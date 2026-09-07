package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Image Generation Service for Samadhan AI utilizing Hugging Face Inference Providers.
 * Model: black-forest-labs/FLUX.1-schnell
 * Reads the secure server-side secret HUGGINGFACE_API_KEY from BuildConfig.
 *
 * NOTE: AI Image Mode will be enabled later.
 */
class ImageGenerationService(private val context: Context) {

    companion object {
        private const val TAG = "ImageGenerationService"
        const val MODEL_ID = "black-forest-labs/FLUX.1-schnell"
        private const val PRIMARY_ROUTER_URL = "https://router.huggingface.co/hf-inference/models/$MODEL_ID"
        private val FALLBACK_ROUTER_URLS = listOf(
            "https://router.huggingface.co/models/$MODEL_ID",
            "https://router.huggingface.co/together/models/$MODEL_ID",
            "https://router.huggingface.co/fal-ai/models/$MODEL_ID"
        )
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Retrieves the active Hugging Face API key securely from BuildConfig.HUGGINGFACE_API_KEY or custom override.
     * Never logs or exposes the key.
     */
    fun getApiKey(customKey: String? = null): String {
        val trimmed = customKey?.trim()
        if (!trimmed.isNullOrEmpty()) {
            return trimmed
        }
        return try {
            val key = BuildConfig.HUGGINGFACE_API_KEY
            if (key.isNotBlank() && key != "YOUR_HUGGINGFACE_API_KEY") key else ""
        } catch (e: Throwable) {
            try {
                val field = BuildConfig::class.java.getField("HUGGINGFACE_API_KEY")
                val key = field.get(null) as? String ?: ""
                if (key.isNotBlank() && key != "YOUR_HUGGINGFACE_API_KEY") key else ""
            } catch (_: Throwable) {
                ""
            }
        }
    }

    /**
     * Generates an image using Hugging Face Inference Providers (FLUX.1-schnell).
     * Endpoint: POST https://router.huggingface.co/hf-inference/models/black-forest-labs/FLUX.1-schnell
     * Header: Authorization: Bearer <HUGGINGFACE_API_KEY>
     * Body: {"inputs": "<prompt>"}
     *
     * Handles status codes & errors:
     * - Missing Key: "Image generation is not configured yet."
     * - 200: Saves and returns image URI
     * - 401/403: "Image generation authentication failed."
     * - 402/429: "Image generation limit reached."
     * - 5xx / Network / Other: "Image service is temporarily unavailable."
     */
    suspend fun generateImage(
        prompt: String,
        referenceImageUri: String? = null,
        customApiKey: String? = null
    ): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(customApiKey)
        if (apiKey.isBlank()) {
            Log.w(TAG, "HUGGINGFACE_API_KEY is missing or unconfigured.")
            return@withContext Result.failure(Exception("Image generation is not configured yet."))
        }

        val cleanPrompt = prompt.trim()
        val escapedPrompt = cleanPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
        val jsonPayload = "{\"inputs\":\"$escapedPrompt\"}"
        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

        val candidateUrls = listOf(PRIMARY_ROUTER_URL) + FALLBACK_ROUTER_URLS

        var lastExceptionMessage = "Image service is temporarily unavailable."

        for (url in candidateUrls) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            try {
                val response = okHttpClient.newCall(request).execute()
                val code = response.code
                val contentType = response.header("Content-Type") ?: ""
                val body = response.body

                Log.d(TAG, "Inference provider response code: $code")

                when (code) {
                    200 -> {
                        val bytes = body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val extension = if (contentType.contains("png", ignoreCase = true)) "png" else "jpg"
                            val imageUri = saveImageBytes(bytes, extension)
                            Log.d(TAG, "Image successfully generated and saved")
                            return@withContext Result.success(Pair(imageUri, "छवि सफलतापूर्वक तैयार की गई।"))
                        } else {
                            Log.e(TAG, "Response body was empty for 200 OK")
                            lastExceptionMessage = "Image service is temporarily unavailable."
                        }
                    }
                    401, 403 -> {
                        Log.e(TAG, "Hugging Face authentication failed ($code)")
                        return@withContext Result.failure(Exception("Image generation authentication failed."))
                    }
                    402, 429 -> {
                        Log.e(TAG, "Hugging Face quota or rate limit reached ($code)")
                        return@withContext Result.failure(Exception("Image generation limit reached."))
                    }
                    in 500..599 -> {
                        Log.e(TAG, "Hugging Face server error ($code)")
                        lastExceptionMessage = "Image service is temporarily unavailable."
                    }
                    else -> {
                        Log.w(TAG, "Hugging Face provider error ($code)")
                        lastExceptionMessage = "Image service is temporarily unavailable."
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Network exception during image generation", e)
                lastExceptionMessage = "Image service is temporarily unavailable."
            }
        }

        return@withContext Result.failure(Exception(lastExceptionMessage))
    }

    private fun saveImageBytes(bytes: ByteArray, extension: String): String {
        val imagesDir = File(context.filesDir, "generated_images").apply { mkdirs() }
        val imageFile = File(imagesDir, "gen_${System.currentTimeMillis()}.$extension")
        imageFile.writeBytes(bytes)
        return Uri.fromFile(imageFile).toString()
    }
}

