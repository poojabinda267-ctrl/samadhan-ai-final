package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale

enum class InputVoiceLanguage(val code: String, val displayName: String, val promptText: String) {
    HINDI("hi-IN", "हिंदी (Hindi)", "बोलें — समाधान AI सुन रहा है..."),
    ENGLISH("en-IN", "English", "Speak — Samadhan AI is listening...")
}

@Composable
fun VoiceInputDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var selectedLanguage by remember { mutableStateOf(InputVoiceLanguage.HINDI) }
    var spokenText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Fallback Intent Launcher for devices without embedded speech recognizer
    val fallbackSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = results?.firstOrNull()
            if (!recognized.isNullOrBlank()) {
                onResult(recognized)
                onDismiss()
                return@rememberLauncherForActivityResult
            }
        }
        isListening = false
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (!isGranted) {
            errorMessage = "माइक्रोफ़ोन अनुमति अस्वीकृत। आवाज़ इनपुट के लिए अनुमति दें।"
            isListening = false
        }
    }

    // Check internet connectivity
    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // Start listening function
    fun startListeningWithRecognizer(recognizer: SpeechRecognizer?, language: InputVoiceLanguage) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        errorMessage = null
        spokenText = ""

        if (!isNetworkAvailable()) {
            errorMessage = "इंटरनेट कनेक्शन उपलब्ध नहीं है। कृपया नेटवर्क चेक करें।"
            isListening = false
            return
        }

        if (recognizer == null) {
            // Use Intent Fallback
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, language.code)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, language.promptText)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                isListening = true
                fallbackSpeechLauncher.launch(intent)
            } catch (e: Exception) {
                errorMessage = "स्पीच रिकग्निशन उपलब्ध नहीं है।"
                isListening = false
            }
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }

        try {
            recognizer.cancel()
            recognizer.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            errorMessage = "आवाज़ रिकॉर्डिंग शुरू नहीं हो सकी: ${e.localizedMessage ?: "पुनः प्रयास करें"}"
            isListening = false
        }
    }

    fun stopListeningSafely() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        isListening = false
        rmsLevel = 0f
    }

    // Initialize SpeechRecognizer
    DisposableEffect(hasPermission) {
        if (hasPermission && SpeechRecognizer.isRecognitionAvailable(context)) {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        errorMessage = null
                    }

                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        rmsLevel = rmsdB.coerceIn(0f, 10f)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        rmsLevel = 0f
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        rmsLevel = 0f
                        errorMessage = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग में समस्या आई।"
                            SpeechRecognizer.ERROR_CLIENT -> "क्लाइंट त्रुटि। कृपया पुनः बोलें।"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफ़ोन अनुमति आवश्यक है।"
                            SpeechRecognizer.ERROR_NETWORK -> "नेटवर्क त्रुटि: इंटरनेट कनेक्शन चेक करें।"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क टाइमआउट: इंटरनेट चेक करें।"
                            SpeechRecognizer.ERROR_NO_MATCH -> "कोई आवाज़ पहचानी नहीं गई। कृपया स्पष्ट बोलें।"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "वॉइस सेवा व्यस्त है। कृपया पुनः प्रयास करें।"
                            SpeechRecognizer.ERROR_SERVER -> "सर्वर त्रुटि। कृपया थोड़ी देर बाद प्रयास करें।"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "समय सीमा समाप्त। कोई आवाज़ नहीं मिली।"
                            else -> "पहचान में समस्या आई (Error $error)। कृपया पुनः प्रयास करें।"
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        rmsLevel = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            spokenText = text
                            errorMessage = null
                        } else if (spokenText.isBlank()) {
                            errorMessage = "कोई आवाज़ नहीं मिली। पुनः प्रयास करें।"
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            spokenText = text
                            errorMessage = null
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
            speechRecognizer = recognizer
        }

        onDispose {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    // Auto-start listening on first launch or permission granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            startListeningWithRecognizer(speechRecognizer, selectedLanguage)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Pulsing animations
    val transition = rememberInfiniteTransition(label = "voice_pulse")
    val idlePulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val soundScale by animateFloatAsState(
        targetValue = if (isListening) (1.0f + (rmsLevel / 10f) * 0.35f) else 1.0f,
        animationSpec = tween(120),
        label = "sound_scale"
    )

    AlertDialog(
        onDismissRequest = {
            stopListeningSafely()
            onDismiss()
        },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isListening) "सुन रहा हूँ... (Listening)" else "वॉइस इनपुट (Voice Input)",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Language Selection Chips (Hindi / English)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InputVoiceLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = {
                                if (selectedLanguage != lang) {
                                    selectedLanguage = lang
                                    if (isListening) {
                                        stopListeningSafely()
                                    }
                                    startListeningWithRecognizer(speechRecognizer, lang)
                                }
                            },
                            label = {
                                Text(
                                    text = lang.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedLanguage == lang) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .testTag("lang_chip_${lang.name.lowercase()}")
                        )
                    }
                }

                // Interactive Animated Microphone Visualizer
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(if (isListening) (idlePulseScale * soundScale) else 1.0f),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer ripple ring when active
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        )
                    }

                    // Main mic circle button
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .clickable {
                                if (isListening) {
                                    stopListeningSafely()
                                } else {
                                    startListeningWithRecognizer(speechRecognizer, selectedLanguage)
                                }
                            }
                            .testTag("voice_dialog_mic_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = if (isListening) "Stop listening" else "Start listening",
                            tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Spoken Transcript / Live Feedback Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (spokenText.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(12.dp)
                        ),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (spokenText.isNotBlank()) {
                            Text(
                                text = spokenText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        } else if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = if (isListening) "कृपया बोलें... (Listening...)" else "माइक्रोफ़ोन पर टैप करके बोलें",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (!hasPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("अनुमति प्रदान करें (Grant Permission)")
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Retry / Listen again button
                if (errorMessage != null || (!isListening && spokenText.isBlank())) {
                    IconButton(
                        onClick = {
                            startListeningWithRecognizer(speechRecognizer, selectedLanguage)
                        },
                        modifier = Modifier.testTag("voice_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry speech recognition",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Stop listening button if active
                if (isListening) {
                    OutlinedButton(
                        onClick = { stopListeningSafely() },
                        modifier = Modifier.testTag("voice_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("रोकें (Stop)")
                    }
                }

                // Use Recognized Text Button
                if (spokenText.isNotBlank()) {
                    Button(
                        onClick = {
                            stopListeningSafely()
                            onResult(spokenText.trim())
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("voice_use_text_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("उपयोग करें (Use)")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    stopListeningSafely()
                    onDismiss()
                },
                modifier = Modifier.testTag("voice_cancel_button")
            ) {
                Text("रद्द करें (Cancel)")
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
