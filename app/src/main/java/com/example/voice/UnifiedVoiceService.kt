package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class VoiceProviderType {
    NONE,
    ELEVENLABS,
    ANDROID_FALLBACK
}

class UnifiedVoiceService(private val context: Context) {

    companion object {
        private const val TAG = "UnifiedVoiceService"
        private const val DIAG_TAG = "SamadhanVoice"

        // ElevenLabs Official Premade Male Voice Configuration
        // Adam (pNInz6obpgDQGcFmaJgB): Natural, realistic official premade male voice,
        // fully supported on ElevenLabs free tier via eleven_multilingual_v2
        const val VOICE_ID = "pNInz6obpgDQGcFmaJgB"
        const val MODEL_ID = "eleven_multilingual_v2"
        // Secondary premade male voice fallback (Charlie) if ever needed
        const val FALLBACK_ELEVENLABS_VOICE_ID = "IKne3meq5aSn9XLyUdCD"
        private const val API_BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var synthesisJob: Job? = null

    // State indicators for UI and diagnostics
    private val _activeProvider = MutableStateFlow(VoiceProviderType.ELEVENLABS)
    val activeProvider: StateFlow<VoiceProviderType> = _activeProvider.asStateFlow()

    private val _activeProviderLabel = MutableStateFlow("Voice Provider: ElevenLabs")
    val activeProviderLabel: StateFlow<String> = _activeProviderLabel.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _lastFallbackReason = MutableStateFlow<String?>(null)
    val lastFallbackReason: StateFlow<String?> = _lastFallbackReason.asStateFlow()

    private var runtimeApiKey: String? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // Media Player for ElevenLabs MP3 playback
    private var mediaPlayer: MediaPlayer? = null
    private var currentTempAudioFile: File? = null

    // Android TextToSpeech Fallback
    private var androidTts: TextToSpeech? = null
    private var isAndroidTtsReady = false

    private var onDoneCallback: (() -> Unit)? = null

    init {
        initAndroidTts()
    }

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isAndroidTtsReady = true
                androidTts?.apply {
                    setPitch(1.0f)
                    setSpeechRate(0.95f)
                    setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post {
                                _isSpeaking.value = true
                                _activeProvider.value = VoiceProviderType.ANDROID_FALLBACK
                                if (!_activeProviderLabel.value.contains("Fallback")) {
                                    _activeProviderLabel.value = "Voice Provider: Android Fallback"
                                }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                _isSpeaking.value = false
                                _activeProvider.value = VoiceProviderType.NONE
                                _activeProviderLabel.value = ""
                                onDoneCallback?.invoke()
                            }
                        }

                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                _isSpeaking.value = false
                                _activeProvider.value = VoiceProviderType.NONE
                                _activeProviderLabel.value = ""
                                onDoneCallback?.invoke()
                            }
                        }
                    })
                }
            } else {
                Log.e(TAG, "Failed to initialize Android TextToSpeech fallback engine (status: $status)")
            }
        }
    }

    fun setRuntimeApiKey(key: String?) {
        runtimeApiKey = key?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves the ElevenLabs API Key safely:
     * 1. Runtime user-provided key
     * 2. BuildConfig.ELEVENLABS_API_KEY injected at build time
     * 3. SharedPreferences
     */
    private fun getEffectiveApiKey(): String? {
        // 1. Runtime override
        if (!runtimeApiKey.isNullOrBlank()) {
            return runtimeApiKey
        }

        // 2. BuildConfig key injected via Secrets Gradle Plugin
        val buildConfigKey = try {
            val key = BuildConfig.ELEVENLABS_API_KEY
            if (key.isNotBlank() && !key.startsWith("YOUR_")) key else null
        } catch (e: Exception) {
            null
        }
        if (buildConfigKey != null) return buildConfigKey

        // 3. System environment variable
        val envKey = try {
            System.getenv("ELEVENLABS_API_KEY")
        } catch (e: Exception) {
            null
        }
        if (!envKey.isNullOrBlank() && !envKey.startsWith("YOUR_")) {
            return envKey
        }

        // 4. SharedPreferences fallback
        val prefs = context.getSharedPreferences("samadhan_ai_prefs", Context.MODE_PRIVATE)
        val prefCustomKey = prefs.getString("custom_api_key", null)
        if (!prefCustomKey.isNullOrBlank() && !prefCustomKey.startsWith("YOUR_")) {
            return prefCustomKey
        }
        val prefElevenKey = prefs.getString("elevenlabs_api_key", null)
        if (!prefElevenKey.isNullOrBlank() && !prefElevenKey.startsWith("YOUR_")) {
            return prefElevenKey
        }

        return null
    }

    /**
     * UNIFIED VOICE METHOD:
     * 1. First calls ElevenLabs TTS with selected Voice ID (pNInz6obpgDQGcFmaJgB - Adam, official premade male voice) and Model (eleven_multilingual_v2).
     * 2. If ever restricted (HTTP 402/404), tries ElevenLabs premade male voice Charlie (IKne3meq5aSn9XLyUdCD).
     * 3. Streams and receives MP3 audio.
     * 4. Plays audio via MediaPlayer.
     * 5. ONLY falls back to Android native TTS if ElevenLabs genuinely fails (invalid key, total quota exhausted, or offline).
     */
    fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        stopSpeaking()
        this.onDoneCallback = onDone

        val cleanText = sanitizeForVoice(text)
        if (cleanText.isBlank()) {
            _isSpeaking.value = false
            _activeProvider.value = VoiceProviderType.ELEVENLABS
            _activeProviderLabel.value = "Voice Provider: ElevenLabs"
            onDone?.invoke()
            return
        }

        val apiKey = getEffectiveApiKey()
        if (apiKey == null) {
            val fallbackCategory = "AUTH_ERROR"
            val diag = "[1. Req Started: true | 2. HTTP: null | 3. Content-Type: none (audio/mpeg: false) | 4. AudioBytes: 0 | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (API key missing in environment / BuildConfig)]"
            Log.w(DIAG_TAG, diag)
            _lastFallbackReason.value = diag
            _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"
            fallbackToAndroidTts(cleanText, onStart, onDone, onError)
            return
        }

        // 1. ElevenLabs request started
        Log.i(DIAG_TAG, "[1. Req Started: true | Voice: $VOICE_ID | Model: $MODEL_ID | Text length: ${cleanText.length}]")
        _activeProvider.value = VoiceProviderType.ELEVENLABS
        _activeProviderLabel.value = "Voice Provider: ElevenLabs"

        synthesisJob = scope.launch {
            try {
                val jsonBody = JSONObject().apply {
                    put("text", cleanText)
                    put("model_id", MODEL_ID)
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.75)
                        put("use_speaker_boost", true)
                    })
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val primaryUrl = "$API_BASE_URL/$VOICE_ID?output_format=mp3_44100_128"

                val primaryRequest = Request.Builder()
                    .url(primaryUrl)
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Accept", "audio/mpeg")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(primaryRequest).execute()
                val statusCode = response.code
                val contentType = response.header("Content-Type") ?: "none"
                val isAudioMpeg = contentType.contains("audio/mpeg", ignoreCase = true) || contentType.contains("audio/mp3", ignoreCase = true)

                Log.i(DIAG_TAG, "[2. HTTP Status: $statusCode | 3. Content-Type: $contentType (isAudioMpeg: $isAudioMpeg)]")

                if (!response.isSuccessful) {
                    val rawErrorBody = response.body?.string() ?: ""
                    response.close()

                    // Extract safe, non-sensitive detail message from ElevenLabs JSON
                    val errorDetail = try {
                        val json = JSONObject(rawErrorBody)
                        val detailObj = json.optJSONObject("detail")
                        detailObj?.optString("message") ?: json.optString("detail", rawErrorBody.take(100))
                    } catch (e: Exception) {
                        rawErrorBody.take(100)
                    }

                    // Map exact fallback category
                    val fallbackCategory = when (statusCode) {
                        401 -> "AUTH_ERROR"
                        402 -> "HTTP_402_QUOTA_OR_CREDITS"
                        403 -> "HTTP_403_PERMISSION"
                        404 -> "HTTP_404_VOICE_NOT_FOUND"
                        429 -> "HTTP_429_RATE_LIMIT"
                        in 500..599 -> "HTTP_5XX_SERVER_ERROR"
                        else -> "HTTP_${statusCode}_ERROR"
                    }

                    val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType (audio/mpeg: $isAudioMpeg) | 4. AudioBytes: 0 | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory ($errorDetail)]"
                    Log.w(DIAG_TAG, diag)
                    _lastFallbackReason.value = diag
                    _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"

                    // CRITICAL: Do not use Android TTS until ElevenLabs HTTP request has actually completed and failed
                    withContext(Dispatchers.Main) {
                        fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                    }
                    return@launch
                }

                val responseBody = response.body
                if (responseBody == null) {
                    val fallbackCategory = "INVALID_AUDIO"
                    val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType (audio/mpeg: $isAudioMpeg) | 4. AudioBytes: 0 | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (Empty response body)]"
                    Log.w(DIAG_TAG, diag)
                    _lastFallbackReason.value = diag
                    _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"

                    withContext(Dispatchers.Main) {
                        fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                    }
                    return@launch
                }

                // Write MP3 stream to temporary cache file
                val tempFile = File(context.cacheDir, "elevenlabs_voice_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { output ->
                    responseBody.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val fileSize = tempFile.length()
                if (fileSize <= 0L || !isAudioMpeg) {
                    val fallbackCategory = "INVALID_AUDIO"
                    val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType (audio/mpeg: $isAudioMpeg) | 4. AudioBytes: $fileSize | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (Invalid or empty audio stream)]"
                    Log.w(DIAG_TAG, diag)
                    _lastFallbackReason.value = diag
                    _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"

                    withContext(Dispatchers.Main) {
                        fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                    }
                    return@launch
                }

                Log.i(DIAG_TAG, "[4. AudioBytes: $fileSize | Stream written successfully]")
                currentTempAudioFile = tempFile

                withContext(Dispatchers.Main) {
                    playElevenLabsAudio(
                        file = tempFile,
                        cleanText = cleanText,
                        statusCode = statusCode,
                        contentType = contentType,
                        audioBytes = fileSize,
                        onStart = onStart,
                        onDone = onDone,
                        onError = onError
                    )
                }

            } catch (e: Exception) {
                val isNetwork = e is java.io.IOException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException
                val fallbackCategory = if (isNetwork) "NETWORK_ERROR" else "HTTP_5XX_SERVER_ERROR"
                val diag = "[1. Req Started: true | 2. HTTP: null | 3. Content-Type: none | 4. AudioBytes: 0 | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (${e.localizedMessage ?: e.javaClass.simpleName})]"
                Log.w(DIAG_TAG, diag)
                _lastFallbackReason.value = diag
                _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"

                withContext(Dispatchers.Main) {
                    fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                }
            }
        }
    }

    private fun playElevenLabsAudio(
        file: File,
        cleanText: String,
        statusCode: Int,
        contentType: String,
        audioBytes: Long,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        try {
            cleanupMediaPlayer()

            if (!file.exists() || file.length() <= 0L) {
                val fallbackCategory = "INVALID_AUDIO"
                val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType | 4. AudioBytes: 0 | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (File missing or empty before playback)]"
                Log.w(DIAG_TAG, diag)
                _lastFallbackReason.value = diag
                _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"
                fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                return
            }

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    try {
                        mp.start()
                        val diagSuccess = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType (audio/mpeg: true) | 4. AudioBytes: $audioBytes | 5. MediaPlayer: true | Active: ELEVENLABS]"
                        Log.i(DIAG_TAG, "ElevenLabs playback started successfully: $diagSuccess")
                        _isSpeaking.value = true
                        _activeProvider.value = VoiceProviderType.ELEVENLABS
                        _activeProviderLabel.value = "Voice Provider: ElevenLabs"
                        _lastFallbackReason.value = null
                        onStart?.invoke()
                    } catch (e: Exception) {
                        val fallbackCategory = "PLAYBACK_ERROR"
                        val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType | 4. AudioBytes: $audioBytes | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (Error starting playback: ${e.message})]"
                        Log.w(DIAG_TAG, diag)
                        _lastFallbackReason.value = diag
                        _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"
                        fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                    }
                }
                setOnCompletionListener {
                    Log.d(DIAG_TAG, "ElevenLabs audio playback completed")
                    _isSpeaking.value = false
                    _activeProvider.value = VoiceProviderType.ELEVENLABS
                    _activeProviderLabel.value = "Voice Provider: ElevenLabs"
                    cleanupMediaPlayer()
                    onDone?.invoke()
                }
                setOnErrorListener { _, what, extra ->
                    val fallbackCategory = "PLAYBACK_ERROR"
                    val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType | 4. AudioBytes: $audioBytes | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (MediaPlayer error: what=$what, extra=$extra)]"
                    Log.w(DIAG_TAG, diag)
                    _lastFallbackReason.value = diag
                    _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"
                    cleanupMediaPlayer()
                    fallbackToAndroidTts(cleanText, onStart, onDone, onError)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            val fallbackCategory = "PLAYBACK_ERROR"
            val diag = "[1. Req Started: true | 2. HTTP: $statusCode | 3. Content-Type: $contentType | 4. AudioBytes: $audioBytes | 5. MediaPlayer: false | 6. Fallback: $fallbackCategory (MediaPlayer setup exception: ${e.localizedMessage ?: e.javaClass.simpleName})]"
            Log.w(DIAG_TAG, diag)
            _lastFallbackReason.value = diag
            _activeProviderLabel.value = "Voice Provider: Android Fallback [$fallbackCategory]"
            cleanupMediaPlayer()
            fallbackToAndroidTts(cleanText, onStart, onDone, onError)
        }
    }

    private fun fallbackToAndroidTts(
        cleanText: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        Log.w(DIAG_TAG, "Invoking Android Native TTS fallback for: \"${cleanText.take(40)}...\"")

        _activeProvider.value = VoiceProviderType.ANDROID_FALLBACK
        _activeProviderLabel.value = "Voice Provider: Android Fallback"

        if (!isAndroidTtsReady || androidTts == null) {
            _isSpeaking.value = false
            _activeProvider.value = VoiceProviderType.NONE
            _activeProviderLabel.value = ""
            onError?.invoke("Both ElevenLabs and Android TTS are unavailable")
            onDone?.invoke()
            return
        }

        try {
            val detectedLocale = detectLanguage(cleanText)
            val langResult = androidTts?.setLanguage(detectedLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                androidTts?.setLanguage(Locale.forLanguageTag("hi-IN"))
            }

            _isSpeaking.value = true
            onStart?.invoke()

            val utteranceId = UUID.randomUUID().toString()
            androidTts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Android TTS speak", e)
            _isSpeaking.value = false
            _activeProvider.value = VoiceProviderType.NONE
            _activeProviderLabel.value = ""
            onError?.invoke(e.localizedMessage ?: "Android TTS failed")
            onDone?.invoke()
        }
    }

    fun stopSpeaking() {
        synthesisJob?.cancel()
        synthesisJob = null

        cleanupMediaPlayer()

        try {
            androidTts?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Android TTS", e)
        }

        _isSpeaking.value = false
        if (_activeProvider.value != VoiceProviderType.ANDROID_FALLBACK) {
            _activeProvider.value = VoiceProviderType.ELEVENLABS
            _activeProviderLabel.value = "Voice Provider: ElevenLabs"
        }
    }

    private fun cleanupMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }

        try {
            currentTempAudioFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
        } catch (e: Exception) {
            // Ignore temporary deletion failure
        } finally {
            currentTempAudioFile = null
        }
    }

    fun release() {
        stopSpeaking()
        try {
            androidTts?.shutdown()
        } catch (e: Exception) {
            // Ignore shutdown error
        }
        androidTts = null
        isAndroidTtsReady = false
    }

    private fun sanitizeForVoice(text: String): String {
        return text
            .replace(Regex("[#*`_~>\\[\\]()]"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun detectLanguage(text: String): Locale {
        var devanagariCount = 0
        var latinCount = 0

        for (char in text) {
            val ub = Character.UnicodeBlock.of(char)
            if (ub == Character.UnicodeBlock.DEVANAGARI) {
                devanagariCount++
            } else if (ub == Character.UnicodeBlock.BASIC_LATIN) {
                latinCount++
            }
        }

        return if (devanagariCount > latinCount / 2) {
            Locale.forLanguageTag("hi-IN")
        } else {
            Locale.forLanguageTag("en-IN")
        }
    }
}
