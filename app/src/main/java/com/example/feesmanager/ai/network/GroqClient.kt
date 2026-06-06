package com.example.feesmanager.ai.network

import com.example.feesmanager.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * GroqClient — Fallback AI provider using Groq's free API.
 *
 * Groq offers Llama 3.3 70B with generous free tier:
 *   - 30 requests/minute
 *   - 6,000 requests/day
 *   - 6,000 tokens/minute
 *
 * API format: OpenAI-compatible (messages array with roles)
 *
 * Get a FREE API key: https://console.groq.com
 * Add to local.properties: GROQ_API_KEY=gsk_xxxxx
 */
object GroqClient {

    private const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL    = "llama-3.3-70b-versatile"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(this@GroqClient.json) }
        engine {
            connectTimeout = 30_000
            socketTimeout = 60_000
        }
    }

    /**
     * Sends a conversation to Groq's Llama model.
     * Returns Result<String> — same signature as GeminiClient.chat()
     */
    suspend fun chat(
        messages: List<GeminiClient.GeminiMessage>,
        systemPrompt: String,
        temperature: Float = 0.5f
    ): Result<String> {
        val apiKey = BuildConfig.GROQ_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(Exception(
                "Groq API key not configured.\n\n" +
                "To fix: Get a FREE key from console.groq.com\n" +
                "Then add to local.properties:\nGROQ_API_KEY=gsk_xxxxx"
            ))
        }

        return try {
            // Convert Gemini format → OpenAI format
            val openAiMessages = mutableListOf<GroqMessage>()

            // System prompt
            openAiMessages.add(GroqMessage(role = "system", content = systemPrompt))

            // Conversation
            messages.forEach { msg ->
                val role = if (msg.role == "model") "assistant" else msg.role
                openAiMessages.add(GroqMessage(role = role, content = msg.text))
            }

            val request = GroqRequest(
                model = MODEL,
                messages = openAiMessages,
                temperature = temperature,
                maxTokens = 2048
            )

            val response = client.post(BASE_URL) {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status != HttpStatusCode.Companion.OK) {
                val statusCode = response.status.value
                val errorMsg = when (statusCode) {
                    429  -> "Groq rate limit reached. Please wait a moment."
                    401  -> "Invalid Groq API key. Check your GROQ_API_KEY in local.properties."
                    else -> "Groq API error (code $statusCode)."
                }
                return Result.failure(Exception(errorMsg))
            }

            val groqResponse = response.body<GroqResponse>()
            val text = groqResponse.choices?.firstOrNull()?.message?.content

            if (text != null) {
                Result.success(text)
            } else {
                Result.failure(Exception("Groq returned an empty response."))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Groq connection failed: ${e.message}"))
        }
    }

    /**
     * Checks if Groq API key is configured.
     */
    fun isConfigured(): Boolean {
        return try {
            BuildConfig.GROQ_API_KEY.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    // ── Groq API Models (OpenAI-compatible) ────────────────────────────

    @Serializable
    data class GroqRequest(
        val model: String,
        val messages: List<GroqMessage>,
        val temperature: Float = 0.5f,
        @SerialName("max_tokens") val maxTokens: Int = 2048
    )

    @Serializable
    data class GroqMessage(
        val role: String,
        val content: String
    )

    @Serializable
    data class GroqResponse(
        val choices: List<GroqChoice>? = null
    )

    @Serializable
    data class GroqChoice(
        val message: GroqMessage? = null,
        @SerialName("finish_reason") val finishReason: String? = null
    )
}