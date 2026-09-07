package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ElevenLabsTtsService(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsTtsService"
        private const val DIAG_TAG = "SamadhanVoice"
        // ElevenLabs Official Premade Male Voice Configuration (Adam)
        const val VOICE_ID = "pNInz6obpgDQGcFmaJgB"
        private const val MODEL_ID = "eleven_multilingual_v2"
        private const val API_BASE_URL = "https://api.elevenlabs.io/v1/text-to-speech"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private var synthesisJob: Job? = null
    private var runtimeApiKey: String? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentTempAudioFile: File? = null

    fun setRuntimeApiKey(key: String?) {
        runtimeApiKey = key?.trim()?.takeIf { it.isNotBlank() }
    }

    fun isConfigured(): Boolean {
        val key = getApiKey()
        return !key.isNullOrBlank() && !key.startsWith("YOUR_")
    }

    private fun getApiKey(): String? {
        // 1. Runtime / user-provided key if available
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

        // 3. SharedPreferences
        val prefs = context.getSharedPreferences("samadhan_ai_prefs", Context.MODE_PRIVATE)
        val prefKey = prefs.getString("custom_api_key", null)
            ?: prefs.getString("elevenlabs_api_key", null)
        if (!prefKey.isNullOrBlank() && !prefKey.startsWith("YOUR_")) {
            return prefKey
        }

        return null
    }

    fun speak(
        text: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        stop()

        val apiKey = getApiKey()
        if (apiKey == null) {
            val reason = "ElevenLabs API Key is not configured or missing from secrets"
            Log.w(DIAG_TAG, "Fallback reason: $reason")
            onError(reason)
            return
        }

        Log.d(DIAG_TAG, "ElevenLabs request started for Voice ID: $VOICE_ID, model: $MODEL_ID (text length: ${text.length})")

        synthesisJob = scope.launch {
            try {
                val jsonBody = JSONObject().apply {
                    put("text", text)
                    put("model_id", MODEL_ID)
                    put("voice_settings", JSONObject().apply {
                        put("stability", 0.5)
                        put("similarity_boost", 0.75)
                        put("use_speaker_boost", true)
                    })
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val url = "$API_BASE_URL/$VOICE_ID?output_format=mp3_44100_128"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("xi-api-key", apiKey)
                    .addHeader("Accept", "audio/mpeg")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val statusCode = response.code
                Log.d(DIAG_TAG, "ElevenLabs HTTP response status: $statusCode")

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    response.close()
                    val reason = "ElevenLabs request failed with HTTP $statusCode ${if (errorBody.isNotBlank()) "($errorBody)" else ""}"
                    Log.w(DIAG_TAG, "Fallback reason: $reason")
                    withContext(Dispatchers.Main) {
                        onError(reason)
                    }
                    return@launch
                }

                val responseBody = response.body ?: run {
                    val reason = "Empty response body received from ElevenLabs API"
                    Log.w(DIAG_TAG, "Fallback reason: $reason")
                    withContext(Dispatchers.Main) {
                        onError(reason)
                    }
                    return@launch
                }

                // Save audio stream to cache file
                val tempFile = File(context.cacheDir, "elevenlabs_speech_${System.currentTimeMillis()}.mp3")
                FileOutputStream(tempFile).use { output ->
                    responseBody.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val fileSize = tempFile.length()
                if (fileSize <= 0L) {
                    val reason = "Audio file written is 0 bytes"
                    Log.w(DIAG_TAG, "Fallback reason: $reason")
                    withContext(Dispatchers.Main) {
                        onError(reason)
                    }
                    return@launch
                }

                Log.d(DIAG_TAG, "ElevenLabs audio received successfully ($fileSize bytes)")
                currentTempAudioFile = tempFile

                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile, onStart, onDone, onError)
                }

            } catch (e: Exception) {
                val reason = "ElevenLabs synthesis exception: ${e.localizedMessage ?: e.javaClass.simpleName}"
                Log.w(DIAG_TAG, "Fallback reason: $reason")
                withContext(Dispatchers.Main) {
                    onError(reason)
                }
            }
        }
    }

    private fun playAudioFile(
        file: File,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            cleanupMediaPlayer()

            if (!file.exists() || file.length() <= 0L) {
                val reason = "Audio file missing or empty before MediaPlayer playback"
                Log.w(DIAG_TAG, "Fallback reason: $reason")
                onError(reason)
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
                        Log.d(DIAG_TAG, "ElevenLabs audio playback started")
                        onStart()
                    } catch (e: Exception) {
                        val reason = "Error starting MediaPlayer playback: ${e.message}"
                        Log.w(DIAG_TAG, "Fallback reason: $reason")
                        onError(reason)
                    }
                }
                setOnCompletionListener {
                    Log.d(DIAG_TAG, "ElevenLabs audio playback completed")
                    cleanupMediaPlayer()
                    onDone()
                }
                setOnErrorListener { _, what, extra ->
                    val reason = "MediaPlayer error: what=$what, extra=$extra"
                    Log.w(DIAG_TAG, "Fallback reason: $reason")
                    cleanupMediaPlayer()
                    onError(reason)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            val reason = "MediaPlayer preparation failed: ${e.localizedMessage ?: e.javaClass.simpleName}"
            Log.w(DIAG_TAG, "Fallback reason: $reason")
            cleanupMediaPlayer()
            onError(reason)
        }
    }

    fun stop() {
        synthesisJob?.cancel()
        synthesisJob = null
        cleanupMediaPlayer()
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
            // Ignore file deletion error
        } finally {
            currentTempAudioFile = null
        }
    }

    fun release() {
        stop()
    }
}
