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

class ClassSelectChatActivity : BaseActivity() {

    private lateinit var container : LinearLayout
    private lateinit var teacherId : String
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_queries)
        container  = findViewById(R.id.queriesList)
        teacherId  = SessionManager.getTeacherId(this) ?: run { finish(); return }
        loadClasses()
    }

    override fun onResume() {
        super.onResume()
        loadClasses()
    }

    private fun loadClasses() {
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                // Fetch classes from 'teacher_classes' view
                val classRows = SupabaseManager.client.postgrest.from("teacher_classes")
                    .select(Columns.Companion.raw("id, class_name")) {
                        filter {
                            eq("teacher_id", teacherId)
                        }
                    }.decodeList<ClassRow>()

                val uniqueClasses = classRows.distinctBy { it.class_name }

                container.removeAllViews()
                container.addView(sectionHeader("Class Group Chats"))

                if (uniqueClasses.isEmpty()) {
                    container.addView(emptyState("No classes set up yet.\nGo to Set Class Fees to add classes."))
                    return@launch
                }

                val teacherName = SessionManager.getTeacherName(this@ClassSelectChatActivity) ?: "Teacher"

                for (cls in uniqueClasses.sortedBy { it.class_name }) {
                    val row = buildClassRow(cls.class_name, teacherName)
                    container.addView(row)

                    // Load per-class unread badge asynchronously
                    launch {
                        val unread = UnreadBadgeHelper.fetchClassUnreadCount(this@ClassSelectChatActivity, teacherId, cls.class_name, teacherId)
                        if (unread > 0) {
                            val badge = row.findViewWithTag<TextView>("badge_${cls.class_name}")
                            badge?.text = if (unread > 99) "99+" else unread.toString()
                            badge?.visibility = View.VISIBLE
                        }
                    }
                }

            } catch (e: Exception) {
                container.addView(emptyState("Failed to load classes: ${e.message}"))
            }
        }
    }

    private fun buildClassRow(className: String, teacherName: String): FrameLayout {
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
            text = "Class $className"
            textSize = 16f; setTextColor(0xFFF1F5F9.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        card.addView(TextView(this).apply {
            text = ">"; textSize = 18f; setTextColor(0xFF6366F1.toInt())
        })

        card.setOnClickListener {
            startActivity(Intent(this, ClassChatActivity::class.java).apply {
                putExtra("teacherId", teacherId)
                putExtra("className", className)
                putExtra("mode", "teacher")
                putExtra("senderName", teacherName)
            })
        }
        frame.addView(card)

        // Badge overlay
        val badge = TextView(this).apply {
            tag = "badge_$className"
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
    data class ClassRow(val class_name: String)
}