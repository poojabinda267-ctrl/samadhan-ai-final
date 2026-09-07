package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.ChatMessageEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class GeminiRepository(private val context: Context) {

    companion object {
        private const val TAG = "GeminiRepository"
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val MODEL_GEMINI_3_5_FLASH = "gemini-3.5-flash"
        const val MODEL_GEMINI_3_1_FLASH_LITE = "gemini-3.1-flash-lite-preview"
        const val MODEL_GEMINI_3_1_PRO = "gemini-3.1-pro-preview"

        val AVAILABLE_MODELS = listOf(
            "gemini-3.5-flash" to "Gemini 3.5 Flash (Fast & Balanced)",
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash Lite (Ultra-Low Latency)",
            "gemini-3.1-pro-preview" to "Gemini 3.1 Pro (Deep Reasoning)"
        )

        val SAMADHAN_SYSTEM_PROMPT = """
            You are Samadhan AI (समाधान AI) — a natural, intelligent, helpful, and solution-oriented conversational AI assistant.
            Your founding motto is: "सिर्फ जवाब नहीं — सच्चा समाधान।" (Not just shallow answers — true, complete solutions).

            Core Guidelines & AI Behavior:
            1. Conversational & Human-like Interaction:
               - Speak and respond naturally like a wise, friendly, and articulate human expert.
               - Support real-time voice and text conversations seamlessly.
               - Understand Hindi, Hinglish, and English intuitively and answer in the user's preferred language.
               - Remember the ongoing conversation context and build upon previous turns effortlessly.

            2. Intent & Solution-Oriented Depth:
               - If a query is direct or conversational, provide a clear, engaging, and comprehensive answer immediately.
               - For complex questions or troubleshooting, offer structured, step-by-step guidance.
               - For code or technical queries, provide clean, fully explained, modern code snippets.

            3. Tone & Style:
               - Warm, respectful, intelligent, articulate, and empathetic.
               - Avoid robotic filler phrases. Speak with confidence and clarity.
               - Format with clean Markdown for easy visual reading and natural speech synthesis.
        """.trimIndent()

        fun isImagePrompt(text: String): Boolean {
            val lower = text.trim().lowercase()
            val imageKeywords = listOf(
                "create image", "generate image", "create an image", "generate an image",
                "draw", "paint", "picture of", "photo of", "wallpaper",
                "छवि बनाएं", "तस्वीर बनाएं", "फोटो बनाओ", "चित्र बनाओ", "तस्वीर दिखाओ",
                "इमेज बनाओ", "तस्वीर खींचो", "चित्र बनाएं", "image banao", "photo banao",
                "tasveer banao", "wallpaper banao", "picture banao"
            )
            return imageKeywords.any { lower.contains(it) }
        }
    }

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val n8nService: N8nAgentService = N8nAgentService(okHttpClient)

    private val apiService: GeminiApiService = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApiService::class.java)

    fun getEffectiveApiKey(customKey: String?): String {
        val trimmedCustom = customKey?.trim()
        if (!trimmedCustom.isNullOrEmpty()) {
            return trimmedCustom
        }
        return try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun convertUriToBase64(uriString: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null

            // Scale down if image is too large for fast transfer
            val maxDimension = 1280
            val ratio = (maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
            val scaledBitmap = if (ratio < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt(),
                    (bitmap.height * ratio).toInt(),
                    true
                )
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            Pair(mimeType, base64Data)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding image to Base64", e)
            null
        }
    }

    /**
     * Core Chat Message Processor:
     * 1. Extracts the latest user message from the input or conversation messages.
     * 2. Sends { "message": "USER_MESSAGE" } via POST to the n8n AI Agent webhook:
     *    https://sukhdav.app.n8n.cloud/webhook/samadhan-ai
     * 3. Robustly parses output, text, message, response, answer, content, etc.
     * 4. Streams the AI response to the SAMADHAN AI chat interface.
     * 5. Handles timeout, network error, unavailable webhook, and empty responses gracefully.
     */
    suspend fun generateStreamResponse(
        messages: List<ChatMessageEntity>,
        latestUserMessage: String,
        attachedImageUri: String?,
        modelName: String = DEFAULT_MODEL,
        customApiKey: String? = null
    ): Flow<String> = flow {
        // 7. Extract the latest user message from input or conversation messages array
        val effectiveUserMessage = if (latestUserMessage.isNotBlank()) {
            latestUserMessage.trim()
        } else {
            messages.lastOrNull { it.role == "user" }?.content?.trim() ?: ""
        }

        if (effectiveUserMessage.isBlank()) {
            emit("कृपया अपना संदेश या प्रश्न लिखें।")
            return@flow
        }

        Log.d(TAG, "Sending latest user message to n8n AI Agent: \"${effectiveUserMessage.take(50)}...\"")

        // 8. Send to n8n AI Agent webhook
        val result = n8nService.sendChatMessage(effectiveUserMessage)

        if (result.isSuccess) {
            val answer = result.getOrThrow()

            // 10. Deliver responsive typewriter streaming to existing chat interface
            val words = answer.split(Regex("(?<=\\s)|(?<=\\n)"))
            if (words.size <= 1) {
                emit(answer)
            } else {
                val sb = StringBuilder()
                for (word in words) {
                    sb.append(word)
                    emit(sb.toString())
                    kotlinx.coroutines.delay(12)
                }
                emit(answer)
            }
        } else {
            val error = result.exceptionOrNull()?.message
                ?: "त्रुटि: AI Agent से संपर्क नहीं हो सका। कृपया पुनः प्रयास करें।"
            Log.e(TAG, "n8n AI Agent call failed: $error")
            emit(error)
        }
    }.flowOn(Dispatchers.IO)

    private fun extractApiErrorMessage(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val code = e.code()
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                try {
                    val adapter = moshi.adapter(GeminiResponse::class.java)
                    val geminiResp = adapter.fromJson(errorBody)
                    if (geminiResp?.error?.message != null) {
                        val status = geminiResp.error.status ?: "HTTP_$code"
                        return "[$status] (Code $code): ${geminiResp.error.message}"
                    }
                } catch (_: Exception) {}
                return "HTTP $code: $errorBody"
            }
            return "HTTP $code: ${e.message()}"
        }
        return e.localizedMessage ?: e.message ?: e.toString()
    }

    private val imageGenerationService: ImageGenerationService = ImageGenerationService(context)

    suspend fun generateImage(
        prompt: String,
        referenceImageUri: String? = null,
        customApiKey: String? = null
    ): Result<Pair<String, String>> {
        return imageGenerationService.generateImage(
            prompt = prompt,
            referenceImageUri = referenceImageUri,
            customApiKey = customApiKey
        )
    }
}
