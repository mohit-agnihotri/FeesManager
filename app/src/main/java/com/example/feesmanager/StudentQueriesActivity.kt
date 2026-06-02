package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.repository.ChatRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * StudentQueriesActivity — Replaced with Class Chat list.
 * Teacher sees all classes and can tap to open each class group chat.
 * Also shows a "Personal Messages" section for individual student chats.
 */
class StudentQueriesActivity : BaseActivity() {

    private lateinit var container : LinearLayout
    private lateinit var teacherId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_queries)

        container = findViewById(R.id.queriesList)

        teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: run {
            finish(); return
        }

        // Update title if present
        // title update skipped - not in layout

        loadClassChats()
    }

    override fun onResume() {
        super.onResume()
        loadClassChats()
    }

    private fun loadClassChats() {
        lifecycleScope.launch {
            container.removeAllViews()

            // Section: Class Group Chats
            container.addView(sectionHeader("📚 Class Group Chats"))

            val chatRepo = ChatRepository()
            val classes = chatRepo.getTeacherClasses(teacherId)

            if (classes.isEmpty()) {
                container.addView(infoRow("No classes set up yet.\nGo to Set Class Fees to add classes."))
            } else {
                for (cls in classes) {
                    container.addView(buildClassRow(cls))
                }
            }

            // Section: Personal Messages (enrolled students)
            container.addView(sectionHeader("👤 Personal Messages"))
            loadPersonalChats()
        }
    }

    private suspend fun loadPersonalChats() {
        try {
            val students = SupabaseManager.client.postgrest.from("enrollments")
                .select(io.github.jan.supabase.postgrest.query.Columns.raw(
                    "student_id, profiles!inner(full_name)")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                        exact("deleted_at", null)
                    }
                    limit(50)
                }.decodeList<EnrollmentRow>()

            if (students.isEmpty()) {
                container.addView(infoRow("No approved students yet."))
            } else {
                for (s in students) {
                    container.addView(buildPersonalRow(s.student_id, s.profiles.full_name))
                }
            }
        } catch (e: Exception) {
            container.addView(infoRow("Could not load students: ${e.message}"))
        }
    }

    private fun buildClassRow(className: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.ripple_card)
            isClickable = true; isFocusable = true
            setPadding(20, 18, 20, 18)
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8; layoutParams = lp
        }
        card.addView(TextView(this).apply {
            text = "📚 Class $className"
            textSize = 16f; setTextColor(0xFFF1F5F9.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(TextView(this).apply {
            text = "→"; textSize = 18f; setTextColor(0xFF6366F1.toInt())
        })
        // Load unread badge
        this@StudentQueriesActivity.lifecycleScope.launch {
            val count = UnreadBadgeHelper.fetchClassUnreadCount(teacherId, className, teacherId)
            if (count > 0) {
                val badge = android.widget.TextView(this@StudentQueriesActivity).apply {
                    text = if (count > 9) "9+" else count.toString()
                    textSize = 9f; setTextColor(0xFFFFFFFF.toInt())
                    gravity = android.view.Gravity.CENTER; setPadding(8, 2, 8, 2)
                    setBackgroundResource(R.drawable.bg_badge_red)
                }
                card.addView(badge)
            }
        }
        card.setOnClickListener {
            startActivity(Intent(this, ClassChatActivity::class.java).apply {
                putExtra("teacherId", teacherId)
                putExtra("className", className)
                putExtra("mode", "teacher")
                putExtra("senderName", "Teacher")
            })
        }
        return card
    }

    private fun buildPersonalRow(studentId: String, studentName: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.ripple_card)
            isClickable = true; isFocusable = true
            setPadding(20, 18, 20, 18)
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8; layoutParams = lp
        }
        card.addView(TextView(this).apply {
            text = "👤 $studentName"
            textSize = 15f; setTextColor(0xFFE2E8F0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(TextView(this).apply {
            text = "→"; textSize = 18f; setTextColor(0xFF6366F1.toInt())
        })
        card.setOnClickListener {
            startActivity(Intent(this, MessageActivity::class.java).apply {
                putExtra("mode", "teacher")
                putExtra("teacherId", teacherId)
                putExtra("studentId", studentId)
                putExtra("studentName", studentName)
            })
        }
        return card
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(0xFF94A3B8.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 16; lp.bottomMargin = 8; layoutParams = lp
    }

    private fun infoRow(text: String) = TextView(this).apply {
        this.text = text; textSize = 14f; setTextColor(0xFF64748B.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = 12; layoutParams = lp
    }

    @Serializable data class EnrollmentRow(val student_id: String, val profiles: ProfileRow)
    @Serializable data class ProfileRow(val full_name: String)
}
