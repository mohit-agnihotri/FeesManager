package com.example.feesmanager.ai.teacher

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TrendDetector — Tracks key academy metrics over time and detects patterns.
 *
 * Stores daily snapshots of metrics using SharedPreferences.
 * After 2+ snapshots, it can detect:
 *   - Collection rate trends (improving/declining)
 *   - Student count changes (growing/shrinking)
 *   - Defaulter trends (rising/falling)
 *   - New changes since last session
 *
 * ALL computation is local — zero API cost.
 */
class TrendDetector(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "ai_trend_data", Context.MODE_PRIVATE
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        private const val TAG = "TrendDetector"
        private const val KEY_SNAPSHOTS = "daily_snapshots"
        private const val KEY_LAST_OPEN = "last_open_timestamp"
        private const val KEY_LAST_CONTEXT_HASH = "last_context_hash"
        private const val MAX_SNAPSHOTS = 30  // Keep 30 days of history
    }

    /**
     * Takes a daily snapshot of the current data context.
     * Only stores one snapshot per day (keyed by date string).
     */
    fun takeSnapshot(context: TeacherDataContext) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

        val snapshots = getSnapshots().toMutableList()

        // Only one snapshot per day
        if (snapshots.any { it.date == today }) {
            // Update today's snapshot with latest data
            val idx = snapshots.indexOfFirst { it.date == today }
            snapshots[idx] = DailySnapshot(
                date = today,
                totalStudents = context.totalStudents,
                totalCollected = context.totalCollected,
                totalPending = context.totalPending,
                collectionRate = context.collectionRate,
                defaulterCount = context.defaulters.size,
                pendingJoinRequests = context.pendingJoinRequests
            )
        } else {
            snapshots.add(DailySnapshot(
                date = today,
                totalStudents = context.totalStudents,
                totalCollected = context.totalCollected,
                totalPending = context.totalPending,
                collectionRate = context.collectionRate,
                defaulterCount = context.defaulters.size,
                pendingJoinRequests = context.pendingJoinRequests
            ))
        }

        // Keep only last MAX_SNAPSHOTS days
        val trimmed = snapshots.takeLast(MAX_SNAPSHOTS)

        try {
            val encoded = json.encodeToString(trimmed)
            prefs.edit().putString(KEY_SNAPSHOTS, encoded).apply()
            Log.d(TAG, "📸 Snapshot saved for $today (${trimmed.size} total)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save snapshot: ${e.message}")
        }
    }

    /**
     * Generates a daily digest by comparing current data with the last session.
     * Returns a formatted string for the AI welcome message.
     */
    fun generateDailyDigest(currentContext: TeacherDataContext): String? {
        val snapshots = getSnapshots()
        if (snapshots.size < 2) return null  // Need at least 2 data points

        val currentHash = contextHash(currentContext)
        val lastHash = prefs.getString(KEY_LAST_CONTEXT_HASH, "") ?: ""

        // Save current hash for next comparison
        prefs.edit().putString(KEY_LAST_CONTEXT_HASH, currentHash).apply()

        // If data hasn't changed, no digest needed
        if (currentHash == lastHash) return null

        val previous = snapshots[snapshots.size - 2]  // Yesterday's data
        val changes = mutableListOf<String>()

        // Collection rate change
        val rateChange = currentContext.collectionRate - previous.collectionRate
        if (rateChange != 0) {
            val arrow = if (rateChange > 0) "↑" else "↓"
            val emoji = if (rateChange > 0) "📈" else "📉"
            changes.add("$emoji Collection rate: ${previous.collectionRate}% → ${currentContext.collectionRate}% $arrow")
        }

        // New payments
        val newCollected = currentContext.totalCollected - previous.totalCollected
        if (newCollected > 0) {
            changes.add("💰 New payments received: ₹$newCollected")
        }

        // Student count change
        val studentDiff = currentContext.totalStudents - previous.totalStudents
        if (studentDiff > 0) {
            changes.add("👥 $studentDiff new student${if (studentDiff > 1) "s" else ""} added")
        } else if (studentDiff < 0) {
            changes.add("👥 ${-studentDiff} student${if (-studentDiff > 1) "s" else ""} removed")
        }

        // Defaulter change
        val defaulterDiff = currentContext.defaulters.size - previous.defaulterCount
        if (defaulterDiff > 0) {
            changes.add("⚠️ $defaulterDiff new defaulter${if (defaulterDiff > 1) "s" else ""}")
        } else if (defaulterDiff < 0) {
            changes.add("✅ ${-defaulterDiff} student${if (-defaulterDiff > 1) "s" else ""} cleared their dues")
        }

        // Pending join requests
        val joinDiff = currentContext.pendingJoinRequests - previous.pendingJoinRequests
        if (joinDiff > 0) {
            changes.add("🆕 $joinDiff new join request${if (joinDiff > 1) "s" else ""}")
        }

        if (changes.isEmpty()) return null

        return buildString {
            appendLine("📊 **Changes since last session:**")
            changes.forEach { appendLine("• $it") }
        }
    }

    /**
     * Detects weekly trends from snapshot history.
     * Returns a list of trend insights.
     */
    fun detectWeeklyTrends(currentContext: TeacherDataContext): List<String> {
        val snapshots = getSnapshots()
        if (snapshots.size < 7) return emptyList()  // Need at least 7 days

        val trends = mutableListOf<String>()
        val lastWeek = snapshots.takeLast(7)

        // Collection rate trend
        val avgRateLastWeek = lastWeek.map { it.collectionRate }.average()
        val rateDirection = currentContext.collectionRate - avgRateLastWeek
        if (rateDirection > 5) {
            trends.add("📈 Collection rate is **improving** (+${rateDirection.toInt()}% vs last week avg)")
        } else if (rateDirection < -5) {
            trends.add("📉 Collection rate is **declining** (${rateDirection.toInt()}% vs last week avg)")
        }

        // Defaulter trend
        val avgDefaultersLastWeek = lastWeek.map { it.defaulterCount }.average()
        val defaulterDirection = currentContext.defaulters.size - avgDefaultersLastWeek
        if (defaulterDirection > 2) {
            trends.add("⚠️ Defaulter count is **rising** (+${defaulterDirection.toInt()} vs last week avg)")
        } else if (defaulterDirection < -2) {
            trends.add("✅ Defaulter count is **dropping** (${defaulterDirection.toInt()} vs last week avg)")
        }

        // Student growth
        val firstDayStudents = lastWeek.first().totalStudents
        val studentGrowth = currentContext.totalStudents - firstDayStudents
        if (studentGrowth > 0) {
            trends.add("👥 Student base grew by **$studentGrowth** this week")
        }

        return trends
    }

    /**
     * Records the current app open timestamp.
     */
    fun recordAppOpen() {
        prefs.edit().putLong(KEY_LAST_OPEN, System.currentTimeMillis()).apply()
    }

    /**
     * Returns time since last app open in milliseconds.
     */
    fun getTimeSinceLastOpen(): Long {
        val lastOpen = prefs.getLong(KEY_LAST_OPEN, 0L)
        return if (lastOpen > 0) System.currentTimeMillis() - lastOpen else 0L
    }

    // ── Private Helpers ───────────────────────────────────────────────────

    private fun getSnapshots(): List<DailySnapshot> {
        return try {
            val raw = prefs.getString(KEY_SNAPSHOTS, null) ?: return emptyList()
            json.decodeFromString<List<DailySnapshot>>(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read snapshots: ${e.message}")
            emptyList()
        }
    }

    private fun contextHash(context: TeacherDataContext): String {
        return "${context.totalStudents}|${context.totalCollected}|${context.totalPending}|${context.defaulters.size}|${context.pendingJoinRequests}"
    }

    // ── Data Models ───────────────────────────────────────────────────────

    @Serializable
    data class DailySnapshot(
        val date: String,
        val totalStudents: Int = 0,
        val totalCollected: Int = 0,
        val totalPending: Int = 0,
        val collectionRate: Int = 0,
        val defaulterCount: Int = 0,
        val pendingJoinRequests: Int = 0
    )
}
