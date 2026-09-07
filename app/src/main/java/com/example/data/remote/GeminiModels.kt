package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    @Json(name = "role") val role: String? = null, // "user" or "model"
    @Json(name = "parts") val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    @Json(name = "text") val text: String? = null,
    @Json(name = "inlineData") val inlineData: GeminiInlineData? = null
)

@JsonClass(generateAdapter = true)
data class GeminiInlineData(
    @Json(name = "mimeType") val mimeType: String,
    @Json(name = "data") val data: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    @Json(name = "temperature") val temperature: Float? = 0.7f,
    @Json(name = "topP") val topP: Float? = 0.95f,
    @Json(name = "topK") val topK: Int? = 40,
    @Json(name = "maxOutputTokens") val maxOutputTokens: Int? = null,
    @Json(name = "responseModalities") val responseModalities: List<String>? = null,
    @Json(name = "imageConfig") val imageConfig: GeminiImageConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiImageConfig(
    @Json(name = "aspectRatio") val aspectRatio: String? = "1:1",
    @Json(name = "imageSize") val imageSize: String? = "1K"
)

@JsonClass(generateAdapter = true)
data class ImagenPredictRequest(
    @Json(name = "instances") val instances: List<ImagenInstance>,
    @Json(name = "parameters") val parameters: ImagenParameters? = null
)

@JsonClass(generateAdapter = true)
data class ImagenInstance(
    @Json(name = "prompt") val prompt: String,
    @Json(name = "image") val image: ImagenInlineImage? = null
)

@JsonClass(generateAdapter = true)
data class ImagenInlineImage(
    @Json(name = "bytesBase64Encoded") val bytesBase64Encoded: String
)

@JsonClass(generateAdapter = true)
data class ImagenParameters(
    @Json(name = "sampleCount") val sampleCount: Int = 1,
    @Json(name = "aspectRatio") val aspectRatio: String = "1:1",
    @Json(name = "outputMimeType") val outputMimeType: String = "image/jpeg"
)

@JsonClass(generateAdapter = true)
data class ImagenPredictResponse(
    @Json(name = "predictions") val predictions: List<ImagenPrediction>? = null,
    @Json(name = "error") val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class ImagenPrediction(
    @Json(name = "bytesBase64Encoded") val bytesBase64Encoded: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<GeminiCandidate>? = null,
    @Json(name = "promptFeedback") val promptFeedback: GeminiPromptFeedback? = null,
    @Json(name = "error") val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    @Json(name = "content") val content: GeminiContent? = null,
    @Json(name = "finishReason") val finishReason: String? = null,
    @Json(name = "safetyRatings") val safetyRatings: List<GeminiSafetyRating>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiSafetyRating(
    @Json(name = "category") val category: String? = null,
    @Json(name = "probability") val probability: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiPromptFeedback(
    @Json(name = "blockReason") val blockReason: String? = null,
    @Json(name = "safetyRatings") val safetyRatings: List<GeminiSafetyRating>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    @Json(name = "code") val code: Int? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "status") val status: String? = null
)
