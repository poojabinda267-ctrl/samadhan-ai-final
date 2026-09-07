package com.example.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

enum class VoiceAssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class VoiceLanguage(val code: String, val displayName: String, val locale: Locale) {
    AUTO("hi-IN", "हिंदी / English (Auto)", Locale.forLanguageTag("hi-IN")),
    HINDI("hi-IN", "हिंदी (Hindi)", Locale.forLanguageTag("hi-IN")),
    ENGLISH("en-IN", "English", Locale.forLanguageTag("en-IN"))
}

class VoiceManager(private val context: Context) {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // State flows
    private val _voiceState = MutableStateFlow(VoiceAssistantState.IDLE)
    val voiceState: StateFlow<VoiceAssistantState> = _voiceState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isDictating = MutableStateFlow(false)
    val isDictating: StateFlow<Boolean> = _isDictating.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _liveSpokenText = MutableStateFlow("")
    val liveSpokenText: StateFlow<String> = _liveSpokenText.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedLanguage = MutableStateFlow(VoiceLanguage.AUTO)
    val selectedLanguage: StateFlow<VoiceLanguage> = _selectedLanguage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    val unifiedVoiceService = UnifiedVoiceService(context)
    private var isCurrentlyCancelling = false

    val activeProvider: StateFlow<VoiceProviderType> = unifiedVoiceService.activeProvider
    val activeProviderLabel: StateFlow<String> = unifiedVoiceService.activeProviderLabel
    val lastFallbackReason: StateFlow<String?> = unifiedVoiceService.lastFallbackReason

    private var onDictationCallback: ((text: String, isFinal: Boolean) -> Unit)? = null
    private var onLiveSpeechResultCallback: ((String) -> Unit)? = null
    private var onTtsCompleteCallback: (() -> Unit)? = null

    private val voiceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        // Observe unified voice speaking status to ensure state synchronization across all triggers
        voiceScope.launch {
            unifiedVoiceService.isSpeaking.collect { speaking ->
                _isSpeaking.value = speaking
                if (speaking) {
                    _voiceState.value = VoiceAssistantState.SPEAKING
                } else if (_voiceState.value == VoiceAssistantState.SPEAKING) {
                    _voiceState.value = VoiceAssistantState.IDLE
                }
            }
        }
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun setLanguage(language: VoiceLanguage) {
        _selectedLanguage.value = language
    }

    fun setCallbacks(
        onSpeechResult: (String) -> Unit,
        onTtsComplete: () -> Unit
    ) {
        this.onLiveSpeechResultCallback = onSpeechResult
        this.onTtsCompleteCallback = onTtsComplete
    }

    /**
     * Feature 1: Voice Dictation (Inside Chat Input Field)
     * Captures spoken words and places text directly into the input field.
     * Does NOT auto-send.
     */
    fun startDictation(onResult: (text: String, isFinal: Boolean) -> Unit) {
        this.onDictationCallback = onResult
        _isDictating.value = true
        startListeningInternal(isDictation = true)
    }

    /**
     * Feature 2: Live AI Voice Conversation
     * Captures user speech to directly converse with AI in real-time.
     */
    fun startLiveConversation(onResult: (String) -> Unit) {
        this.onLiveSpeechResultCallback = onResult
        _isDictating.value = false
        startListeningInternal(isDictation = false)
    }

    /**
     * Legacy helper forwarding to live conversation
     */
    fun startListening(onResult: ((String) -> Unit)? = null) {
        if (onResult != null) {
            this.onLiveSpeechResultCallback = onResult
        }
        _isDictating.value = false
        startListeningInternal(isDictation = false)
    }

    private fun startListeningInternal(isDictation: Boolean) {
        stopSpeaking()

        if (!hasRecordPermission()) {
            _errorMessage.value = "माइक्रोफ़ोन अनुमति आवश्यक है (Microphone permission required)."
            _voiceState.value = VoiceAssistantState.ERROR
            _isDictating.value = false
            return
        }

        if (!isNetworkAvailable()) {
            _errorMessage.value = "इंटरनेट कनेक्शन उपलब्ध नहीं है (No internet connection)."
            _voiceState.value = VoiceAssistantState.ERROR
            _isDictating.value = false
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _errorMessage.value = "डिवाइस पर स्पीच रिकग्निशन उपलब्ध नहीं है (Speech recognition unavailable)."
            _voiceState.value = VoiceAssistantState.ERROR
            _isDictating.value = false
            return
        }

        try {
            cleanupSpeechRecognizer()
            isCurrentlyCancelling = false

            _liveSpokenText.value = ""
            _errorMessage.value = null
            _rmsDb.value = 0f
            _voiceState.value = VoiceAssistantState.LISTENING

            val recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        mainHandler.post {
                            _voiceState.value = VoiceAssistantState.LISTENING
                            _errorMessage.value = null
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        mainHandler.post {
                            _voiceState.value = VoiceAssistantState.LISTENING
                        }
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        mainHandler.post {
                            _rmsDb.value = rmsdB.coerceIn(0f, 10f)
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        mainHandler.post {
                            _rmsDb.value = 0f
                        }
                    }

                    override fun onError(error: Int) {
                        mainHandler.post {
                            _rmsDb.value = 0f

                            // Ignore harmless benign errors (client cancellation, silence timeouts, etc.)
                            // Never show false "Voice Notice: Client error" notices!
                            val isBenign = error == SpeechRecognizer.ERROR_CLIENT ||
                                    error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                    isCurrentlyCancelling

                            if (isBenign) {
                                _errorMessage.value = null
                                if (_voiceState.value == VoiceAssistantState.LISTENING) {
                                    _voiceState.value = VoiceAssistantState.IDLE
                                }
                                _isDictating.value = false
                                return@post
                            }

                            // Genuine failures (audio hardware, missing permissions, real network error)
                            val errorDesc = when (error) {
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क त्रुटि: कृपया इंटरनेट कनेक्शन चेक करें"
                                SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग में समस्या आई"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफ़ोन अनुमति आवश्यक है"
                                else -> null
                            }

                            if (errorDesc != null) {
                                _errorMessage.value = errorDesc
                                _voiceState.value = VoiceAssistantState.ERROR
                            } else {
                                _errorMessage.value = null
                                _voiceState.value = VoiceAssistantState.IDLE
                            }
                            _isDictating.value = false
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        mainHandler.post {
                            _rmsDb.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val finalSpeech = matches?.firstOrNull()?.trim()

                            if (!finalSpeech.isNullOrBlank()) {
                                _liveSpokenText.value = finalSpeech
                                _errorMessage.value = null

                                if (_isDictating.value) {
                                    _isDictating.value = false
                                    _voiceState.value = VoiceAssistantState.IDLE
                                    onDictationCallback?.invoke(finalSpeech, true)
                                } else {
                                    _voiceState.value = VoiceAssistantState.THINKING
                                    onLiveSpeechResultCallback?.invoke(finalSpeech)
                                }
                            } else {
                                _errorMessage.value = null
                                _voiceState.value = VoiceAssistantState.IDLE
                                _isDictating.value = false
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        mainHandler.post {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim()
                            if (!partial.isNullOrBlank()) {
                                _liveSpokenText.value = partial
                                if (_isDictating.value) {
                                    onDictationCallback?.invoke(partial, false)
                                }
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            speechRecognizer = recognizer

            val lang = _selectedLanguage.value
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.code)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang.code)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (isDictation) {
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }
            }

            recognizer.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer", e)
            _errorMessage.value = "वॉइस रिकॉर्डिंग शुरू नहीं हो सकी: ${e.localizedMessage ?: "Unknown error"}"
            _voiceState.value = VoiceAssistantState.ERROR
            _isDictating.value = false
        }
    }

    fun stopListening() {
        isCurrentlyCancelling = true
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognizer", e)
        }
        _rmsDb.value = 0f
        _isDictating.value = false
        _errorMessage.value = null
        if (_voiceState.value == VoiceAssistantState.LISTENING) {
            _voiceState.value = VoiceAssistantState.IDLE
        }
    }

    fun cancelListening() {
        isCurrentlyCancelling = true
        cleanupSpeechRecognizer()
        _rmsDb.value = 0f
        _isDictating.value = false
        _errorMessage.value = null
        if (_voiceState.value == VoiceAssistantState.LISTENING) {
            _voiceState.value = VoiceAssistantState.IDLE
        }
    }

    fun setThinkingState() {
        stopListening()
        stopSpeaking()
        _voiceState.value = VoiceAssistantState.THINKING
        _errorMessage.value = null
    }

    fun setIdleState() {
        cancelListening()
        stopSpeaking()
        _voiceState.value = VoiceAssistantState.IDLE
        _isDictating.value = false
        _errorMessage.value = null
    }

    fun setCustomApiKey(key: String?) {
        unifiedVoiceService.setRuntimeApiKey(key)
    }

    /**
     * Delegates to UnifiedVoiceService.speak():
     * 1. First calls ElevenLabs TTS with selected Voice ID (pNInz6obpgDQGcFmaJgB - Adam) and Model (eleven_multilingual_v2).
     * 2. Plays MP3 audio via MediaPlayer.
     * 3. Falls back to Android native TTS ONLY if ElevenLabs request genuinely fails.
     */
    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (onDone != null) {
            this.onTtsCompleteCallback = onDone
        }

        val cleanText = sanitizeForTts(text)
        if (cleanText.isBlank()) {
            _voiceState.value = VoiceAssistantState.IDLE
            _isSpeaking.value = false
            onDone?.invoke()
            return
        }

        unifiedVoiceService.speak(
            text = cleanText,
            onStart = {
                mainHandler.post {
                    _isSpeaking.value = true
                    _voiceState.value = VoiceAssistantState.SPEAKING
                }
            },
            onDone = {
                mainHandler.post {
                    _isSpeaking.value = false
                    if (_voiceState.value == VoiceAssistantState.SPEAKING) {
                        _voiceState.value = VoiceAssistantState.IDLE
                    }
                    onTtsCompleteCallback?.invoke()
                    onDone?.invoke()
                }
            },
            onError = { reason ->
                Log.w("SamadhanVoice", "Voice output notice: $reason")
                mainHandler.post {
                    _isSpeaking.value = false
                    if (_voiceState.value == VoiceAssistantState.SPEAKING) {
                        _voiceState.value = VoiceAssistantState.IDLE
                    }
                }
            }
        )
    }

    fun stopSpeaking() {
        unifiedVoiceService.stopSpeaking()
        _isSpeaking.value = false
        if (_voiceState.value == VoiceAssistantState.SPEAKING) {
            _voiceState.value = VoiceAssistantState.IDLE
        }
    }

    private fun cleanupSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up recognizer", e)
        }
        speechRecognizer = null
    }

    fun destroy() {
        try {
            voiceScope.cancel()
        } catch (e: Exception) {
            // Ignore
        }
        cleanupSpeechRecognizer()
        unifiedVoiceService.release()
    }

    private fun sanitizeForTts(raw: String): String {
        return raw
            // Replace code blocks with spoken summary
            .replace(Regex("```[\\s\\S]*?```"), " कोड का उदाहरण। ")
            // Strip inline code backticks
            .replace(Regex("`([^`]+)`"), "$1")
            // Strip headers
            .replace(Regex("#{1,6}\\s*"), "")
            // Strip bold and italics
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("__([^_]+)__"), "$1")
            .replace(Regex("_([^_]+)_"), "$1")
            .replace(Regex("~~([^~]+)~~"), "$1")
            // Strip markdown links [label](url) -> label
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            // Strip bullets
            .replace(Regex("^[\\s]*[-*•][\\s]+", RegexOption.MULTILINE), "")
            // Strip number markers
            .replace(Regex("^[\\s]*\\d+\\.[\\s]+", RegexOption.MULTILINE), "")
            // Strip multiple newlines
            .replace(Regex("\\n{2,}"), ". ")
            .replace(Regex("\\n"), " ")
            // Strip extra spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun detectLanguage(text: String): Locale {
        val hindiCharCount = text.count { it in '\u0900'..'\u097F' }
        return if (hindiCharCount > 5 || (text.isNotEmpty() && (hindiCharCount.toDouble() / text.length) > 0.15)) {
            Locale.forLanguageTag("hi-IN")
        } else {
            Locale.forLanguageTag("en-IN")
        }
    }
}

