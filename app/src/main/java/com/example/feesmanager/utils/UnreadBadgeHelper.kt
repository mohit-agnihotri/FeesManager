package com.example.feesmanager.utils

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.example.feesmanager.R
import com.example.feesmanager.data.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * UnreadBadgeHelper — Manages unread message counts and red dot badges.
 *
 * Badge counts are based on messages NOT sent by the current reader.
 * Call the relevant fetch method and then addBadge() to display.
 */
object UnreadBadgeHelper {

    private val db = SupabaseManager.client.postgrest

    // ── Badge UI ──────────────────────────────────────────────────────────────

    /**
     * Adds a red badge TextView on top-right of [anchorView].
     * Wraps in FrameLayout if needed.
     */
    fun addBadge(context: Context, anchorView: View, count: Int) {
        if (count <= 0) {
            removeBadge(anchorView); return
        }
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

    // ── Local Read State (SharedPreferences) ──────────────────────────────────

    private fun getLastReadTime(context: Context, readerId: String, chatId: String): String {
        return context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .getString("read_${readerId}_${chatId}", "2000-01-01T00:00:00.000Z")!!
    }

    fun markAsRead(context: Context, readerId: String, chatId: String) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
            .edit().putString("read_${readerId}_${chatId}", now).apply()
    }

    private fun filterUnreadLocally(context: Context, readerId: String, rows: List<MessageCountRow>): Int {
        val prefs = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        return rows.count { row ->
            val chatId = if (!row.class_name.isNullOrEmpty()) row.class_name else row.student_id ?: ""
            val lastRead = prefs.getString("read_${readerId}_${chatId}", "2000-01-01T00:00:00.000Z")!!
            (row.created_at ?: "") > lastRead
        }
    }

    // ── Teacher-side unread counts ────────────────────────────────────────────

    /** Total unread for teacher = personal msgs from students + class msgs from students */
    suspend fun fetchTotalUnreadForTeacher(context: Context, teacherId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages")
                    .select(Columns.raw("id, created_at, class_name, student_id")) {
                        filter {
                            eq("teacher_id", teacherId)
                            neq("sender_id", teacherId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(99)
                    }.decodeList<MessageCountRow>()
                filterUnreadLocally(context, teacherId, rows)
            } catch (_: Exception) {
                0
            }
        }
    }

    /** Unread personal messages for a teacher (all students combined) */
    suspend fun fetchTotalPersonalUnreadForTeacher(context: Context, teacherId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages")
                    .select(Columns.raw("id, created_at, class_name, student_id")) {
                        filter {
                            eq("teacher_id", teacherId)
                            eq("chat_type", "personal")
                            neq("sender_id", teacherId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(99)
                    }.decodeList<MessageCountRow>()
                filterUnreadLocally(context, teacherId, rows)
            } catch (_: Exception) {
                0
            }
        }
    }

    /** Unread class messages for a teacher (all classes combined) */
    suspend fun fetchTotalClassUnreadForTeacher(context: Context, teacherId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val rows = db.from("messages")
                    .select(Columns.raw("id, created_at, class_name, student_id")) {
                        filter {
                            eq("teacher_id", teacherId)
                            eq("chat_type", "class")
                            neq("sender_id", teacherId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(99)
                    }.decodeList<MessageCountRow>()
                filterUnreadLocally(context, teacherId, rows)
            } catch (_: Exception) {
                0
            }
        }
    }

    /** Unread personal messages from one specific student to the teacher */
    suspend fun fetchPersonalUnreadCount(context: Context, teacherId: String, studentId: String, readerRole: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val lastRead = getLastReadTime(context, readerRole, studentId)
                val rows = db.from("messages").select(Columns.raw("id")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("student_id", studentId)
                        eq("chat_type", "personal")
                        neq("sender_id", readerRole)
                        gt("created_at", lastRead)
                    }
                    limit(50)
                }.decodeList<MessageCountRow>()
                rows.size
            } catch (_: Exception) {
                0
            }
        }
    }

    /** Unread class messages for one specific class */
    suspend fun fetchClassUnreadCount(context: Context, teacherId: String, className: String, readerId: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val lastRead = getLastReadTime(context, readerId, className)
                val rows = db.from("messages").select(Columns.raw("id")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("class_name", className)
                        eq("chat_type", "class")
                        neq("sender_id", readerId)
                        gt("created_at", lastRead)
                    }
                    limit(20)
                }.decodeList<MessageCountRow>()
                rows.size
            } catch (_: Exception) {
                0
            }
        }
    }

    // ── Student-side unread counts ────────────────────────────────────────────

    /** Unread personal messages for a student (messages teacher sent to them) */
    suspend fun fetchTotalUnreadForStudent(context: Context, teacherId: String, studentId: String): Int {
        return fetchPersonalUnreadCount(context, teacherId, studentId, studentId)
    }

    /** Unread class messages for a student in their class */
    suspend fun fetchClassUnreadForStudent(context: Context, teacherId: String, className: String, studentId: String): Int {
        return fetchClassUnreadCount(context, teacherId, className, studentId)
    }

    @Serializable
    private data class MessageCountRow(
        val id: String,
        val created_at: String? = null,
        val class_name: String? = null,
        val student_id: String? = null
    )
}