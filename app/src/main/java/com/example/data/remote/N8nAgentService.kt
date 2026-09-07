package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Service to communicate with the SAMADHAN AI n8n AI Agent Webhook.
 *
 * Webhook URL: https://sukhdav.app.n8n.cloud/webhook/samadhan-ai
 * Request format:
 * POST
 * Content-Type: application/json
 * Body: { "message": "USER_MESSAGE" }
 */
class N8nAgentService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "N8nAgentService"
        const val WEBHOOK_URL = "https://sukhdav.app.n8n.cloud/webhook/samadhan-ai"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Candidate response field keys in priority order
        private val CANDIDATE_KEYS = listOf(
            "output", "text", "message", "response", "answer", "content", "result", "reply", "data"
        )
    }

    /**
     * Sends the latest user message to the n8n AI Agent webhook and returns the parsed response.
     *
     * @param message The user's latest text prompt.
     * @return Result containing the extracted AI response string or an error.
     */
    suspend fun sendChatMessage(message: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Message cannot be empty."))
        }

        try {
            // Build strictly required JSON body: { "message": "USER_MESSAGE" }
            val jsonPayload = JSONObject().apply {
                put("message", trimmedMessage)
            }.toString()

            val requestBody = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(WEBHOOK_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json, text/plain, */*")
                .addHeader("User-Agent", "SAMADHAN-AI-Android/1.0")
                .build()

            Log.d(TAG, "Dispatching message to n8n webhook: ${request.url}")

            client.newCall(request).execute().use { response ->
                val statusCode = response.code
                val rawBody = response.body?.string()

                if (!response.isSuccessful) {
                    Log.e(TAG, "n8n webhook error HTTP $statusCode: $rawBody")
                    return@withContext Result.failure(
                        IOException("AI Agent is currently unavailable (HTTP $statusCode). Please verify the n8n workflow or try again.")
                    )
                }

                if (rawBody.isNullOrBlank()) {
                    Log.e(TAG, "n8n webhook returned an empty response body")
                    return@withContext Result.failure(
                        IOException("The AI Agent returned an empty response. Please try again.")
                    )
                }

                // Robustly parse the response for output, text, message, response, answer, content, etc.
                val parsedText = parseN8nResponse(rawBody)

                if (parsedText.isNullOrBlank()) {
                    Log.e(TAG, "Unable to extract message from n8n response: $rawBody")
                    return@withContext Result.failure(
                        IOException("The AI Agent returned an invalid response format.")
                    )
                }

                Log.d(TAG, "Successfully received response from n8n Agent (${parsedText.length} chars)")
                Result.success(parsedText)
            }
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Timeout contacting n8n webhook", e)
            Result.failure(IOException("Request timed out. The AI Agent took too long to respond. Please try again."))
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Network unreachable for n8n webhook", e)
            Result.failure(IOException("Network error. Unable to reach the AI Agent. Please check your internet connection."))
        } catch (e: IOException) {
            Log.e(TAG, "I/O error communicating with n8n webhook", e)
            Result.failure(IOException("Network error: ${e.localizedMessage ?: "Unable to connect to AI Agent."}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error communicating with n8n webhook", e)
            Result.failure(IOException("Error: ${e.localizedMessage ?: "Unknown error occurred while contacting AI Agent."}"))
        }
    }

    /**
     * Recursively traverses and parses the n8n response to extract the text answer.
     * Supports:
     * - Direct JSON object with output/text/message/response/answer/content fields
     * - Nested JSON objects
     * - JSON arrays containing node outputs (e.g. `[{"output": "..."}]` or `[{"json": {"text": "..."}}]`)
     * - Fallback plain text string
     */
    fun parseN8nResponse(rawBody: String): String? {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return null

        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
            (trimmed.startsWith("[") && trimmed.endsWith("]"))
        ) {
            try {
                fun extract(element: Any?): String? {
                    if (element == null) return null

                    if (element is String) {
                        val s = element.trim()
                        if (s.isNotEmpty()) return s
                    }

                    if (element is JSONArray) {
                        for (i in 0 until element.length()) {
                            val candidate = extract(element.opt(i))
                            if (!candidate.isNullOrBlank()) return candidate
                        }
                    }

                    if (element is JSONObject) {
                        // 1. Check known candidate keys first
                        for (key in CANDIDATE_KEYS) {
                            if (element.has(key)) {
                                val value = element.opt(key)
                                if (value is String && value.isNotBlank()) {
                                    return value.trim()
                                } else if (value is JSONObject || value is JSONArray) {
                                    val candidate = extract(value)
                                    if (!candidate.isNullOrBlank()) return candidate
                                }
                            }
                        }

                        // 2. Check inner 'json' key common in n8n nodes
                        if (element.has("json")) {
                            val jsonVal = element.opt("json")
                            val candidate = extract(jsonVal)
                            if (!candidate.isNullOrBlank()) return candidate
                        }

                        // 3. Fallback: inspect any key with a string or nested object
                        val keys = element.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = element.opt(k)
                            if (v is JSONObject || v is JSONArray) {
                                val candidate = extract(v)
                                if (!candidate.isNullOrBlank()) return candidate
                            } else if (v is String && v.isNotBlank()) {
                                return v.trim()
                            }
                        }
                    }

                    return null
                }

                val parsedRoot: Any = if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    JSONObject(trimmed)
                }

                val extracted = extract(parsedRoot)
                if (!extracted.isNullOrBlank()) {
                    return extracted
                }
            } catch (e: Exception) {
                Log.w(TAG, "JSON parsing error, falling back to raw text: ${e.message}")
            }
        }

        // If not JSON or no field extracted, return the raw text if non-empty
        return trimmed.ifEmpty { null }
    }
}
