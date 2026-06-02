package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * ClassSelectChatActivity — Migrated to Supabase (Postgres).
 * Teacher selects a class for group chat or proceeds to personal queries.
 */
class ClassSelectChatActivity : BaseActivity() {

    private lateinit var container : LinearLayout
    private lateinit var teacherId : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_queries) // Reusing existing list layout

        container  = findViewById(R.id.queriesList)
        teacherId  = SessionManager.getTeacherId(this) ?: run { finish(); return }

        loadClassesFromSupabase()
    }

    private fun loadClassesFromSupabase() {
        lifecycleScope.launch {
            try {
                // Fetch distinct classes from teacher_classes table
                val classes = SupabaseManager.client.postgrest.from("teacher_classes")
                    .select {
                        filter { eq("teacher_id", teacherId) }
                    }.decodeList<ClassRow>()

                container.removeAllViews()

                if (classes.isEmpty()) {
                    container.addView(TextView(this@ClassSelectChatActivity).apply {
                        text = "No classes set up yet.\nGo to Set Class Fees first."; textSize = 14f
                        setTextColor(0xFF94A3B8.toInt()); setPadding(0, 40, 0, 0)
                        gravity = android.view.Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    })
                    return@launch
                }

                // Personal chats section
                addSectionHeader("Personal Chats")
                container.addView(buildRow("💬 Student Queries / Personal Messages", "1-to-1 chat with individual students") {
                    startActivity(Intent(this@ClassSelectChatActivity, StudentQueriesActivity::class.java))
                })

                // Group chats section
                addSectionHeader("Class Group Chats")
                for (cls in classes.sortedBy { it.class_name }) {
                    container.addView(buildRow("🏫 Class ${cls.class_name}", "Group chat for Class ${cls.class_name}") {
                        startActivity(Intent(this@ClassSelectChatActivity, ClassChatActivity::class.java)
                            .putExtra("teacherId",  teacherId)
                            .putExtra("className",  cls.class_name)
                            .putExtra("mode",       "teacher")
                            .putExtra("senderName", SessionManager.getTeacherName(this@ClassSelectChatActivity) ?: "Teacher"))
                    })
                }

            } catch (e: Exception) {
                Toast.makeText(this@ClassSelectChatActivity, "Failed to load classes", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title; textSize = 12f; setTextColor(0xFF6366F1.toInt())
            setPadding(0, 20, 0, 8)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun buildRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.ripple_card)
            setPadding(20, 16, 20, 16)
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 8; layoutParams = lp
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply {
            text = title; textSize = 15f; setTextColor(0xFFF1F5F9.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        row.addView(TextView(this).apply {
            text = subtitle; textSize = 12f; setTextColor(0xFF94A3B8.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 3; layoutParams = lp
        })
        return row
    }

    @Serializable
    private data class ClassRow(val class_name: String)
}
