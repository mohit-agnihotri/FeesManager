package com.example.feesmanager.ai

import com.example.feesmanager.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GeminiClient — Singleton REST client for Google Gemini API.
 *
 * Uses Ktor (already in the project) to make direct REST calls.
 * Avoids adding the heavyweight Google AI SDK — zero new dependency conflicts.
 *
 * Usage:
 *   val response = GeminiClient.chat(messages, systemPrompt)
 */
object GeminiClient {

    private const val BASE_URL      = "https://generativelanguage.googleapis.com/v1beta"
    private const val MODEL_PRIMARY  = "gemini-2.0-flash"
    private const val MODEL_FALLBACK = "gemini-2.0-flash-lite"  // Higher rate limits on free tier

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(this@GeminiClient.json) }
        engine {
            connectTimeout  = 30_000
            socketTimeout   = 60_000
        }
    }

    /**
     * Sends a multi-turn conversation to Gemini and returns the AI response text.
     *
     * @param messages  Conversation history (role: "user" or "model")
     * @param systemPrompt  System instruction for the AI's behavior
     * @param temperature   Creativity level (0.0–1.0)
     * @return Result<String> — success with response text, or failure with error
     */
    suspend fun chat(
        messages: List<GeminiMessage>,
        systemPrompt: String,
        temperature: Float = 0.7f
    ): Result<String> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Gemini API key not configured. Add GEMINI_API_KEY to local.properties"))
        }

        val maxRetries = 3
        var lastError: Exception? = null
        var useModel = MODEL_PRIMARY

        for (attempt in 0..maxRetries) {
            try {
                val request = GeminiRequest(
                    contents = messages.map { msg ->
                        GeminiContent(
                            role = msg.role,
                            parts = listOf(GeminiPart(text = msg.text))
                        )
                    },
                    systemInstruction = GeminiContent(
                        parts = listOf(GeminiPart(text = systemPrompt))
                    ),
                    generationConfig = GenerationConfig(
                        temperature = temperature,
                        maxOutputTokens = 2048
                    )
                )

                val response = client.post("$BASE_URL/models/$useModel:generateContent") {
                    parameter("key", apiKey)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                if (response.status != HttpStatusCode.OK) {
                    val statusCode = response.status.value
                    val friendlyMessage = parseApiError(statusCode, response)

                    // Retry on 429 (rate limit) with exponential backoff
                    if (statusCode == 429 && attempt < maxRetries) {
                        val delayMs = (3000L * (attempt + 1))  // 3s, 6s, 9s
                        kotlinx.coroutines.delay(delayMs)
                        // Switch to fallback model on 2nd retry
                        if (attempt >= 1) useModel = MODEL_FALLBACK
                        lastError = Exception(friendlyMessage)
                        continue
                    }

                    return Result.failure(Exception(friendlyMessage))
                }

                val geminiResponse = response.body<GeminiResponse>()

                val text = geminiResponse.candidates
                    ?.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                return if (text != null) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("AI returned an empty response. Please try again."))
                }
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    kotlinx.coroutines.delay(1000L * (attempt + 1))
                    continue
                }
            }
        }

        return Result.failure(Exception(
            "Unable to reach AI service. Please check your internet connection and try again."
        ))
    }

    /**
     * Parses Gemini API error responses into user-friendly messages.
     * Avoids dumping raw JSON into the chat.
     */
    private suspend fun parseApiError(statusCode: Int, response: io.ktor.client.statement.HttpResponse): String {
        return when (statusCode) {
            429 -> "⏳ AI is temporarily busy (rate limit reached). Please wait a moment and try again."
            400 -> "The request was invalid. Please try rephrasing your question."
            401, 403 -> "API key issue. Please check your Gemini API key configuration."
            404 -> "AI model not found. The model may have been updated."
            500, 502, 503 -> "AI service is temporarily unavailable. Please try again in a few seconds."
            else -> {
                // Try to extract a clean message from the error JSON
                try {
                    val errorBody = response.body<String>()
                    val messageMatch = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(errorBody)
                    messageMatch?.groupValues?.get(1)?.let { msg ->
                        // Truncate overly long messages
                        if (msg.length > 150) msg.take(150) + "..." else msg
                    } ?: "AI service error (code $statusCode). Please try again."
                } catch (_: Exception) {
                    "AI service error (code $statusCode). Please try again."
                }
            }
        }
    }

    /**
     * Single-turn convenience method — no conversation history needed.
     */
    suspend fun ask(
        question: String,
        systemPrompt: String,
        temperature: Float = 0.7f
    ): Result<String> {
        return chat(
            messages = listOf(GeminiMessage(role = "user", text = question)),
            systemPrompt = systemPrompt,
            temperature = temperature
        )
    }

    // ── API Models ─────────────────────────────────────────────────────────

    data class GeminiMessage(
        val role: String,   // "user" or "model"
        val text: String
    )
}

// ── Serializable request/response models for Gemini REST API ──────────────

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GenerationConfig(
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 4096
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)
