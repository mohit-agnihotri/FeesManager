package com.example.feesmanager.ai.teacher

import android.util.Log
import com.example.feesmanager.ai.models.AiChatMessage
import com.example.feesmanager.ai.engine.TeacherDataContext
import java.security.MessageDigest

/**
 * AiResponseCache — Lightweight in-memory cache for AI responses.
 *
 * Uses smart cache keys that factor in both the question AND the current
 * data state, so the same question with different data produces a cache miss.
 *
 * Cache strategy:
 *   - Stable queries (how-to, help) → cached for 24 hours
 *   - Data-dependent queries → cached for 5 minutes (matches data TTL)
 *   - Error responses → never cached
 *   - Max 100 entries to prevent memory bloat
 *
 * No Room dependency — pure in-memory with LRU eviction.
 */
class AiResponseCache {

    private val cache = LinkedHashMap<String, CacheEntry>(100, 0.75f, true)

    companion object {
        private const val TAG = "AiCache"
        private const val MAX_ENTRIES = 100
        private const val STABLE_TTL_MS = 24 * 60 * 60 * 1000L  // 24 hours
        private const val DATA_TTL_MS = 5 * 60 * 1000L           // 5 minutes
    }

    /**
     * Retrieves a cached response if it exists and is not expired.
     *
     * @param query The user's question
     * @param context Current data context (used for cache key)
     * @return Cached AiChatMessage, or null if cache miss
     */
    fun get(query: String, context: TeacherDataContext): AiChatMessage? {
        val key = generateKey(query, context)
        val entry = cache[key] ?: return null

        // Check TTL
        if (System.currentTimeMillis() - entry.timestamp > entry.ttlMs) {
            cache.remove(key)
            return null
        }

        entry.hitCount++
        Log.d(TAG, "⚡ Cache HIT (hits: ${entry.hitCount}): ${query.take(40)}")
        return entry.response
    }

    /**
     * Stores a response in the cache.
     *
     * @param query The user's question
     * @param context Current data context (used for cache key)
     * @param response The AI response to cache
     */
    fun put(query: String, context: TeacherDataContext, response: AiChatMessage) {
        // Don't cache error or loading messages
        if (response.isLoading || response.content.startsWith("❌")) return

        val key = generateKey(query, context)
        val isStable = isStableQuery(query)
        val ttl = if (isStable) STABLE_TTL_MS else DATA_TTL_MS

        // Evict oldest if full
        if (cache.size >= MAX_ENTRIES) {
            val oldestKey = cache.keys.first()
            cache.remove(oldestKey)
        }

        cache[key] = CacheEntry(
            response = response,
            timestamp = System.currentTimeMillis(),
            ttlMs = ttl,
            isStable = isStable
        )

        Log.d(TAG, "💾 Cached (${if (isStable) "stable 24h" else "data 5m"}): ${query.take(40)}")
    }

    /**
     * Invalidates all data-dependent cache entries.
     * Called after data changes (action executed, manual refresh).
     */
    fun invalidateDataDependent() {
        val keysToRemove = cache.entries
            .filter { !it.value.isStable }
            .map { it.key }
        keysToRemove.forEach { cache.remove(it) }
        Log.d(TAG, "🗑️ Invalidated ${keysToRemove.size} data-dependent entries")
    }

    /**
     * Clears the entire cache.
     */
    fun clearAll() {
        val size = cache.size
        cache.clear()
        Log.d(TAG, "🗑️ Cleared all $size cache entries")
    }

    /**
     * Returns cache statistics for debugging.
     */
    fun getStats(): CacheStats {
        val totalHits = cache.values.sumOf { it.hitCount }
        val stableCount = cache.values.count { it.isStable }
        return CacheStats(
            totalEntries = cache.size,
            stableEntries = stableCount,
            dataEntries = cache.size - stableCount,
            totalHits = totalHits
        )
    }

    // ── Key Generation ────────────────────────────────────────────────────

    /**
     * Generates a cache key from the normalized query + data context hash.
     *
     * Two identical questions with different data produce different keys.
     * Two identical questions with same data produce the same key.
     */
    private fun generateKey(query: String, context: TeacherDataContext): String {
        val normalizedQuery = normalizeQuery(query)
        val dataHash = generateDataHash(context)
        return md5("$normalizedQuery|$dataHash")
    }

    /**
     * Normalizes query: lowercase, trim, remove extra spaces, remove punctuation.
     */
    private fun normalizeQuery(query: String): String {
        return query.lowercase()
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^a-z0-9\\s]"), "")
    }

    /**
     * Generates a hash of the data context.
     * Changes when student count, fees collected, or pending amount changes.
     */
    private fun generateDataHash(context: TeacherDataContext): String {
        return "${context.totalStudents}|${context.totalCollected}|${context.totalPending}|${context.defaulters.size}|${context.pendingJoinRequests}"
    }

    /**
     * Determines if a query is "stable" (answer doesn't depend on live data).
     */
    private fun isStableQuery(query: String): Boolean {
        val q = query.lowercase()
        val stablePatterns = listOf(
            "how to", "kaise", "how do i", "what is", "kya hai",
            "help", "madad", "explain", "guide", "steps",
            "tutorial", "meaning", "definition"
        )
        return stablePatterns.any { q.contains(it) }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ── Data Classes ──────────────────────────────────────────────────────

    private data class CacheEntry(
        val response: AiChatMessage,
        val timestamp: Long,
        val ttlMs: Long,
        val isStable: Boolean,
        var hitCount: Int = 0
    )

    data class CacheStats(
        val totalEntries: Int,
        val stableEntries: Int,
        val dataEntries: Int,
        val totalHits: Int
    )
}
