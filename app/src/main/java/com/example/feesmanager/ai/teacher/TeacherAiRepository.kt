package com.example.feesmanager.ai.teacher

import android.util.Log
import com.example.feesmanager.ai.*

/**
 * TeacherAiRepository — Smart routing engine for the Teacher AI Agent.
 *
 * Request flow:
 *   1. ⚡ CACHE CHECK — Return cached AI response if same data state
 *   2. ⚡ LOCAL ANALYSIS — Instant (no API call) for common queries
 *   3. 🟢 GEMINI API — Primary AI for complex/custom questions
 *   4. 🔵 GROQ API — Fallback if Gemini hits rate limit
 *
 * Optimizations:
 *   - Data context cached with 5-minute TTL
 *   - Conversation history capped at 10 messages
 *   - AI response cache for stable queries
 *   - 70% of queries handled locally → ZERO cost, INSTANT response
 */
class TeacherAiRepository {

    private val insightEngine = InsightEngine()
    private val conversationHistory = mutableListOf<GeminiClient.GeminiMessage>()
    private var cachedContext: TeacherDataContext? = null
    private var contextTimestamp: Long = 0L

    // ── AI Response Cache ─────────────────────────────────────────────────
    private val responseCache = AiResponseCache()

    companion object {
        private const val TAG = "TeacherAiRepo"
        private const val CACHE_TTL_MS = 5 * 60 * 1000L  // 5 minutes
        private const val MAX_HISTORY = 10
    }

    /**
     * Smart routing: Cache → Local → Gemini → Groq
     */
    suspend fun analyzeWithAi(
        teacherId: String,
        query: String,
        forceRefreshData: Boolean = false
    ): Result<AiChatMessage> {
        return try {
            // ── Refresh data if cache expired or forced ──
            if (cachedContext == null || forceRefreshData || isCacheExpired()) {
                Log.d(TAG, "📊 Refreshing data context...")
                cachedContext = insightEngine.gatherContext(teacherId)
                contextTimestamp = System.currentTimeMillis()
            }
            val context = cachedContext!!

            // ── STEP 1: Check AI response cache ──
            val cachedResponse = responseCache.get(query, context)
            if (cachedResponse != null) {
                Log.d(TAG, "⚡ Cache HIT: ${query.take(50)}")
                return Result.success(cachedResponse)
            }

            // ── STEP 2: Try LOCAL analysis first (instant, no API) ──
            val localResult = LocalAnalyzer.tryAnalyze(query, context)
            if (localResult != null) {
                Log.d(TAG, "✅ Handled locally: ${query.take(50)}")
                // Cache stable local responses
                responseCache.put(query, context, localResult)
                return Result.success(localResult)
            }

            // ── STEP 3: Need AI — try Gemini first, Groq as fallback ──
            Log.d(TAG, "🌐 Sending to AI: ${query.take(50)}")
            val result = callAiWithFallback(query, context, forceRefreshData)

            // Cache successful AI responses for stable queries
            result.getOrNull()?.let { aiMessage ->
                responseCache.put(query, context, aiMessage)
            }

            return result

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calls AI with Gemini → Groq fallback chain.
     */
    private suspend fun callAiWithFallback(
        query: String,
        context: TeacherDataContext,
        forceRefreshData: Boolean
    ): Result<AiChatMessage> {
        // Build enriched prompt
        val isFirstMessage = conversationHistory.isEmpty()
        val fullQuery = if (isFirstMessage || forceRefreshData) {
            val dataContext = insightEngine.formatForAi(context)
            "$dataContext\n\nTeacher's Question: $query"
        } else {
            query
        }

        // Add to conversation
        conversationHistory.add(
            GeminiClient.GeminiMessage(role = "user", text = fullQuery)
        )

        // ── Trim conversation history to prevent token overflow ──
        trimConversationHistory()

        // Try Gemini first
        val geminiResult = GeminiClient.chat(
            messages = conversationHistory,
            systemPrompt = PromptTemplates.TEACHER_AGENT_SYSTEM,
            temperature = 0.5f
        )

        val aiResponse = geminiResult.getOrNull()

        if (aiResponse != null) {
            Log.d(TAG, "✅ Gemini responded")
            return processAiResponse(aiResponse)
        }

        // Gemini failed — check if Groq is available
        val geminiError = geminiResult.exceptionOrNull()?.message ?: ""
        Log.w(TAG, "⚠️ Gemini failed: $geminiError")

        if (GroqClient.isConfigured()) {
            Log.d(TAG, "🔄 Trying Groq fallback...")
            val groqResult = GroqClient.chat(
                messages = conversationHistory,
                systemPrompt = PromptTemplates.TEACHER_AGENT_SYSTEM,
                temperature = 0.5f
            )

            val groqResponse = groqResult.getOrNull()
            if (groqResponse != null) {
                Log.d(TAG, "✅ Groq responded (fallback)")
                return processAiResponse(groqResponse)
            }

            Log.w(TAG, "⚠️ Groq also failed: ${groqResult.exceptionOrNull()?.message}")
        }

        // Both failed — remove the failed query from history
        conversationHistory.removeLastOrNull()

        // Return a helpful error with the original Gemini error
        val errorMessage = when {
            geminiError.contains("rate limit", ignoreCase = true) ->
                "Both AI providers hit rate limits. Try using quick actions (they work offline!) or wait 30 seconds."
            geminiError.contains("API key", ignoreCase = true) ->
                geminiError
            else ->
                "AI service unavailable. Quick actions still work offline!"
        }
        return Result.failure(Exception(errorMessage))
    }

    /**
     * Processes raw AI response text into a structured AiChatMessage.
     */
    private fun processAiResponse(rawResponse: String): Result<AiChatMessage> {
        conversationHistory.add(
            GeminiClient.GeminiMessage(role = "model", text = rawResponse)
        )

        // Trim after adding model response too
        trimConversationHistory()

        val (cleanText, actions) = parseActions(rawResponse)
        val priority = detectPriority(rawResponse)

        val message = AiChatMessage(
            content = cleanText,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions,
            priority = priority
        )

        return Result.success(message)
    }

    /**
     * Keeps conversation history within MAX_HISTORY limit.
     * Preserves the first message (contains data context) and the most recent messages.
     */
    private fun trimConversationHistory() {
        if (conversationHistory.size <= MAX_HISTORY) return

        val first = conversationHistory.first()
        val recent = conversationHistory.takeLast(MAX_HISTORY - 1)
        conversationHistory.clear()
        conversationHistory.add(first)
        conversationHistory.addAll(recent)

        Log.d(TAG, "✂️ Trimmed conversation to $MAX_HISTORY messages")
    }

    /**
     * Checks if the data context cache has expired.
     */
    private fun isCacheExpired(): Boolean {
        return System.currentTimeMillis() - contextTimestamp > CACHE_TTL_MS
    }

    /**
     * Gets current academy context data (for display in UI).
     */
    suspend fun getDataContext(teacherId: String): TeacherDataContext {
        if (cachedContext == null || isCacheExpired()) {
            cachedContext = insightEngine.gatherContext(teacherId)
            contextTimestamp = System.currentTimeMillis()
        }
        return cachedContext!!
    }

    /**
     * Detects urgent issues from current data.
     */
    suspend fun getUrgentIssues(teacherId: String): List<String> {
        val context = getDataContext(teacherId)
        return insightEngine.detectUrgentIssues(context)
    }

    /**
     * Refreshes cached data from Supabase and invalidates response cache.
     */
    suspend fun refreshData(teacherId: String) {
        cachedContext = insightEngine.gatherContext(teacherId)
        contextTimestamp = System.currentTimeMillis()
        responseCache.invalidateDataDependent()
    }

    fun clearConversation() {
        conversationHistory.clear()
        responseCache.clearAll()
    }

    /**
     * Returns cached pending students for action execution.
     */
    fun getCachedPendingStudents(): List<PendingStudentInfo> {
        return cachedContext?.pendingStudents ?: emptyList()
    }

    /**
     * Called after an action execution to invalidate stale cache.
     */
    fun onActionExecuted() {
        responseCache.invalidateDataDependent()
        // Force data refresh on next query
        contextTimestamp = 0L
    }

    // ── Response parsing ───────────────────────────────────────────────────

    /**
     * Parses [ACTION:type|label|description] tags from AI response.
     */
    private fun parseActions(response: String): Pair<String, List<SuggestedAction>> {
        val actionPattern = Regex("\\[ACTION:(\\w+)\\|([^|]+)\\|([^]]+)]")
        val matches = actionPattern.findAll(response).toList()

        if (matches.isEmpty()) return Pair(response, emptyList())

        val actions = matches.mapNotNull { match ->
            val type = match.groupValues[1]
            val label = match.groupValues[2].trim()
            val desc = match.groupValues[3].trim()

            val actionType = when (type.uppercase()) {
                "SEND_REMINDER"     -> SuggestedAction.ActionType.SEND_REMINDER
                "POST_ANNOUNCEMENT" -> SuggestedAction.ActionType.POST_ANNOUNCEMENT
                "NOTIFY_GROUP"      -> SuggestedAction.ActionType.NOTIFY_GROUP
                "SEND_MESSAGE"      -> SuggestedAction.ActionType.SEND_MESSAGE
                "GENERATE_REPORT"   -> SuggestedAction.ActionType.GENERATE_REPORT
                "APPROVE_JOIN"      -> SuggestedAction.ActionType.APPROVE_JOIN
                "REJECT_JOIN"       -> SuggestedAction.ActionType.REJECT_JOIN
                else -> return@mapNotNull null
            }

            SuggestedAction(
                label = label,
                description = desc,
                type = actionType,
                payload = mapOf("generated_text" to desc)
            )
        }

        // Clean action tags from text
        val cleanText = actionPattern.replace(response, "").trim()
            .replace(Regex("\n{3,}"), "\n\n")

        return Pair(cleanText, actions)
    }

    /**
     * Detects the highest priority mentioned in the response.
     */
    private fun detectPriority(response: String): Priority {
        val priorityPattern = Regex("\\[PRIORITY:(\\w+)]")
        val matches = priorityPattern.findAll(response).toList()

        val priorities = matches.mapNotNull { match ->
            when (match.groupValues[1].uppercase()) {
                "URGENT" -> Priority.URGENT
                "HIGH"   -> Priority.HIGH
                "NORMAL" -> Priority.NORMAL
                "LOW"    -> Priority.LOW
                else     -> null
            }
        }

        return when {
            priorities.contains(Priority.URGENT) -> Priority.URGENT
            priorities.contains(Priority.HIGH)   -> Priority.HIGH
            else -> Priority.NORMAL
        }
    }
}
