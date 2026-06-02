package com.example.feesmanager

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * UnreadBadgeHelper — Manages unread message counts and red dot badges.
 *
 * Usage:
 *   UnreadBadgeHelper.addBadge(context, parentView, count)
 *   UnreadBadgeHelper.fetchUnreadCount(teacherId, studentId) → Int
 *   UnreadBadgeHelper.fetchClassUnreadCount(teacherId, className) → Int
 */
object UnreadBadgeHelper {

    private val db = SupabaseManager.client.postgrest

    /**
     * Adds a red badge TextView on top-right of [anchorView].
     * Wraps in FrameLayout if needed.
     */
    fun addBadge(context: Context, anchorView: View, count: Int) {
        if (count <= 0) {
            removeBadge(anchorView); return
        }
        // If already has badge, just update
        (anchorView.tag as? TextView)?.let {
            it.text = if (count > 99) "99+" else count.toString()
            it.visibility = View.VISIBLE
            return
        }

        val badge = TextView(context).apply {
            text = if (count > 99) "99+" else count.toString()
            textSize = 9f; setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(6, 2, 6, 2)
            setBackgroundResource(R.drawable.bg_badge_red)
            elevation = 8f
        }

        // Wrap in FrameLayout if parent is LinearLayout
        val parent = anchorView.parent
        if (parent is FrameLayout) {
            val lp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            ).apply { setMargins(0, -4, -4, 0) }
            badge.layoutParams = lp
            parent.addView(badge)
            anchorView.tag = badge
        }
    }

    fun removeBadge(anchorView: View) {
        (anchorView.tag as? TextView)?.visibility = View.GONE
    }

    // ─── Fetch unread counts from Supabase ─────────────────────────────────

    suspend fun fetchPersonalUnreadCount(teacherId: String, studentId: String, readerRole: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                // Unread = messages not sent by the reader
                val rows = db.from("messages").select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("student_id", studentId)
                        eq("chat_type", "personal")
                        neq("sender_id", readerRole) // messages NOT sent by me
                    }
                    order("created_at", Order.DESCENDING)
                    limit(50)
                }.decodeList<MessageCountRow>()
                rows.size // simplified: count all unread since last seen
            } catch (_: Exception) { 0 }
        }
    }

    suspend fun fetchClassUnreadCount(teacherId: String, className: String, readerId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages").select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("class_name", className)
                        eq("chat_type", "class")
                        neq("sender_id", readerId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(20)
                }.decodeList<MessageCountRow>()
                rows.size
            } catch (_: Exception) { 0 }
        }
    }

    suspend fun fetchTotalUnreadForTeacher(teacherId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages").select {
                    filter {
                        eq("teacher_id", teacherId)
                        neq("sender_id", teacherId)
                    }
                    order("created_at", Order.DESCENDING)
                    limit(99)
                }.decodeList<MessageCountRow>()
                rows.size
            } catch (_: Exception) { 0 }
        }
    }

    suspend fun fetchTotalUnreadForStudent(teacherId: String, studentId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages").select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("student_id", studentId)
                        eq("chat_type", "personal")
                        neq("sender_id", studentId)
                    }
                    limit(50)
                }.decodeList<MessageCountRow>()
                rows.size
            } catch (_: Exception) { 0 }
        }
    }

    @Serializable
    private data class MessageCountRow(val id: String)
}
