package com.example.feesmanager.ui.announcements

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.Announcement
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.auth.SessionManager
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * AnnouncementsActivity — Supports class-targeted announcements.
 * Teacher can send to: ALL, or a specific class.
 */
class AnnouncementsActivity : BaseActivity() {

    private val viewModel: AnnouncementsViewModel by viewModels()

    private lateinit var listLayout  : LinearLayout
    private lateinit var inputLayout : LinearLayout
    private lateinit var etMessage   : EditText
    private lateinit var btnPost     : Button
    private lateinit var tvTitle     : TextView

    private var isTeacherMode = false
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_announcements)

        listLayout  = findViewById(R.id.listLayout)
        inputLayout = findViewById(R.id.inputLayout)
        etMessage   = findViewById(R.id.etMessage)
        btnPost     = findViewById(R.id.btnPost)
        tvTitle     = findViewById(R.id.tvTitle)

        val mode = intent.getStringExtra("mode") ?: "student"
        isTeacherMode = (mode == "teacher")

        if (isTeacherMode) {
            tvTitle.text = "📢 Post Announcement"
            inputLayout.visibility = View.VISIBLE
            teacherId = SessionManager.getTeacherId(this) ?: run { finish(); return }
        } else {
            tvTitle.text = "📢 Announcements"
            inputLayout.visibility = View.GONE
            teacherId = SessionManager.getStudentTeacherId(this) ?: run {
                toast("Not linked to any academy"); finish(); return
            }
        }

        viewModel.announcements.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {}
                is FmResult.Success -> renderAnnouncements(result.content)
                is FmResult.Error   -> toast("Failed to load announcements")
            }
        }
        viewModel.loadAnnouncements(teacherId)

        btnPost.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isEmpty()) { toast("Write a message first"); return@setOnClickListener }
            showTargetDialog(text)
        }
    }

    private fun showTargetDialog(text: String) {
        // Build class list for targeting
        val options = arrayOf("📢 All Students", "📚 Specific Class...")
        AlertDialog.Builder(this)
            .setTitle("Send to whom?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> postAnnouncement(text, "all")
                    1 -> showClassPicker(text)
                }
            }.show()
    }

    private fun showClassPicker(text: String) {
        val input = EditText(this).apply {
            hint = "Class name (e.g. 10, 11, 12)"
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Target Class")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val cls = input.text.toString().trim()
                if (cls.isNotEmpty()) postAnnouncement(text, cls)
                else toast("Enter a class name")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun postAnnouncement(text: String, targetClass: String) {
        btnPost.isEnabled = false
        viewModel.postAnnouncement(teacherId, text, targetClass)
        viewModel.postResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {}
                is FmResult.Success -> {
                    btnPost.isEnabled = true
                    etMessage.text.clear()
                    val targetLabel = if (targetClass == "all") "all students" else "Class $targetClass"
                    toast("Announcement sent to $targetLabel ✅")
                    viewModel.loadAnnouncements(teacherId)
                }
                is FmResult.Error -> {
                    btnPost.isEnabled = true
                    toast("Failed to post: ${result.message}")
                }
            }
        }
    }

    private fun renderAnnouncements(list: List<Announcement>) {
        listLayout.removeAllViews()
        if (list.isEmpty()) {
            listLayout.addView(TextView(this).apply {
                text = "No announcements yet."; textSize = 14f
                setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 32; layoutParams = lp
            })
            return
        }
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        list.forEach { ann ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_modern)
                setPadding(20, 16, 20, 16)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 10; layoutParams = lp
            }
            // Target badge
            val targetBadge = ann.targetClass.let { tc ->
                if (tc == "all" || tc.isNullOrEmpty()) "📢 All Students" else "📚 Class $tc"
            }
            card.addView(TextView(this).apply {
                text = targetBadge; textSize = 11f
                setTextColor(0xFF6366F1.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 6; layoutParams = lp
            })
            card.addView(TextView(this).apply {
                text = ann.body; textSize = 15f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.4f)
            })
            val time = runCatching { sdf.format(
                SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ssXXX",
                    Locale.getDefault()
                ).parse(ann.createdAt)!!) }.getOrElse { ann.createdAt.take(10) }
            card.addView(TextView(this).apply {
                text = time; textSize = 11f; setTextColor(0xFF64748B.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 6; layoutParams = lp
            })
            listLayout.addView(card)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}