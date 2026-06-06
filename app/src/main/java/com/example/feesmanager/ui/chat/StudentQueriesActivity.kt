package com.example.feesmanager.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.UnreadBadgeHelper
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class StudentQueriesActivity : BaseActivity() {

    private lateinit var container : LinearLayout
    private lateinit var teacherId : String
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_queries)
        container = findViewById(R.id.queriesList)
        teacherId = SessionManager.getTeacherId(this) ?: run { finish(); return }
        loadPersonalChats()
    }

    override fun onResume() {
        super.onResume()
        loadPersonalChats()
    }

    private fun loadPersonalChats() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                val students = SupabaseManager.client.postgrest.from("enrollments")
                    .select(
                        Columns.Companion.raw(
                        "student_id, profiles!inner(full_name, avatar_url)")) {
                        filter {
                            eq("teacher_id", teacherId)
                            eq("status", "approved")
                            exact("deleted_at", null)
                        }
                        limit(100)
                    }.decodeList<EnrollmentRow>()

                val unique = students.distinctBy { it.student_id }

                container.removeAllViews()
                container.addView(sectionHeader("Personal Messages"))

                if (unique.isEmpty()) {
                    container.addView(emptyState("No approved students yet."))
                    return@launch
                }

                for (s in unique) {
                    val row = buildStudentRow(s.student_id, s.profiles.full_name, s.profiles.avatar_url)
                    container.addView(row)

                    launch {
                        val unread = UnreadBadgeHelper.fetchPersonalUnreadCount(
                            this@StudentQueriesActivity, teacherId, s.student_id, teacherId
                        )
                        if (unread > 0) {
                            val badge = row.findViewWithTag<TextView>("badge_${s.student_id}")
                            badge?.text = if (unread > 99) "99+" else unread.toString()
                            badge?.visibility = View.VISIBLE
                        }
                    }
                }

            } catch (e: Exception) {
                container.addView(emptyState("Could not load students: ${e.message}"))
            }
        }
    }

    private fun buildStudentRow(studentId: String, studentName: String, photoUrl: String?): FrameLayout {
        val frame = FrameLayout(this).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            layoutParams = lp
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.ripple_card)
            isClickable = true; isFocusable = true
            setPadding(20, 18, 20, 18)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        card.addView(TextView(this).apply {
            text = "Student: $studentName"
            textSize = 15f; setTextColor(0xFFE2E8F0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(TextView(this).apply {
            text = ">"; textSize = 18f; setTextColor(0xFF6366F1.toInt())
        })

        card.setOnClickListener {
            startActivity(Intent(this, MessageActivity::class.java).apply {
                putExtra("mode", "teacher")
                putExtra("teacherId", teacherId)
                putExtra("studentId", studentId)
                putExtra("studentName", studentName)
                putExtra("otherPhotoUrl", photoUrl)
            })
        }
        frame.addView(card)

        val badge = TextView(this).apply {
            tag = "badge_$studentId"
            textSize = 10f; setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(8, 2, 8, 2)
            setBackgroundResource(R.drawable.bg_badge_red)
            visibility = View.GONE
        }
        val badgeLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(0, -6, 6, 0) }
        badge.layoutParams = badgeLp
        frame.addView(badge)

        return frame
    }

    private fun sectionHeader(text: String) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(0xFF94A3B8.toInt())
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 8; lp.bottomMargin = 12; layoutParams = lp
    }

    private fun emptyState(msg: String) = TextView(this).apply {
        text = msg; textSize = 14f; setTextColor(0xFF64748B.toInt())
        gravity = Gravity.CENTER; setPadding(0, 40, 0, 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    @Serializable
    data class EnrollmentRow(val student_id: String, val profiles: ProfileRow)
    @Serializable
    data class ProfileRow(val full_name: String, val avatar_url: String? = null)
}