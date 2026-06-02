package com.example.feesmanager

import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feesmanager.ai.PromptTemplates
import com.example.feesmanager.ai.SuggestedAction
import com.example.feesmanager.ai.adapter.AiChatAdapter
import com.example.feesmanager.ai.teacher.TeacherAiViewModel

/**
 * TeacherAiActivity — Full-screen AI Agent for teachers.
 *
 * Features:
 *   - Real-time data analysis from Supabase
 *   - Pre-built quick action prompts
 *   - Priority-tagged insights
 *   - One-click action execution with confirmation dialog
 *   - Urgent issue detection & highlighting
 *   - Auto data refresh after actions
 */
class TeacherAiActivity : BaseActivity() {

    private val viewModel: TeacherAiViewModel by viewModels()

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: View
    private lateinit var btnRefresh: View
    private lateinit var btnNewChat: View
    private lateinit var btnBack: View
    private lateinit var layoutQuickActions: LinearLayout
    private lateinit var layoutUrgentBanner: LinearLayout
    private lateinit var tvUrgentTitle: TextView
    private lateinit var tvUrgentDetails: TextView

    private lateinit var adapter: AiChatAdapter
    private var teacherId = ""

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_ai)

        teacherId = SessionManager.getTeacherId(this) ?: run {
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Bind views
        rvMessages        = findViewById(R.id.rvMessages)
        etMessage         = findViewById(R.id.etMessage)
        btnSend           = findViewById(R.id.btnSend)
        btnRefresh        = findViewById(R.id.btnRefresh)
        btnNewChat        = findViewById(R.id.btnNewChat)
        btnBack           = findViewById(R.id.btnBack)
        layoutQuickActions = findViewById(R.id.layoutQuickActions)
        layoutUrgentBanner = findViewById(R.id.layoutUrgentBanner)
        tvUrgentTitle      = findViewById(R.id.tvUrgentTitle)
        tvUrgentDetails    = findViewById(R.id.tvUrgentDetails)

        // Setup RecyclerView with action click → show confirmation
        adapter = AiChatAdapter(onActionClick = { action ->
            showConfirmationDialog(action)
        })
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        // Setup quick actions
        setupQuickActions()

        // Observe messages
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages)
            if (messages.isNotEmpty()) {
                rvMessages.scrollToPosition(messages.size - 1)
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            btnSend.isEnabled = !loading
            etMessage.isEnabled = !loading
        }

        // Observe urgent issues
        viewModel.urgentIssues.observe(this) { issues ->
            if (issues.isNotEmpty()) {
                layoutUrgentBanner.visibility = View.VISIBLE
                tvUrgentTitle.text = "⚠️ ${issues.size} Alert${if (issues.size > 1) "s" else ""} Detected"
                tvUrgentDetails.text = issues.joinToString("\n")

                // Animate banner entrance
                AnimUtil.slideUp(layoutUrgentBanner, 0)
            } else {
                layoutUrgentBanner.visibility = View.GONE
            }
        }

        // Observe data context for dynamic subtitle
        viewModel.dataContext.observe(this) { context ->
            if (context != null) {
                val subtitle = "📊 ${context.totalStudents} students • ${context.collectionRate}% collected"
                findViewById<TextView>(R.id.tvSubtitle)?.text = subtitle
            }
        }

        // Observe action results for toast
        viewModel.actionResult.observe(this) { result ->
            if (result != null) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                viewModel.clearActionResult()
            }
        }

        // Button handlers
        btnSend.setOnClickListener { sendMessage() }
        btnBack.setOnClickListener { finish() }
        btnNewChat.setOnClickListener { viewModel.clearChat() }
        btnRefresh.setOnClickListener {
            Toast.makeText(this, "🔄 Refreshing data...", Toast.LENGTH_SHORT).show()
            viewModel.refreshData()
        }

        // Tap urgent banner to analyze
        layoutUrgentBanner.setOnClickListener {
            viewModel.sendMessage("Analyze and provide detailed information about the urgent issues detected. Suggest specific actions to resolve them.")
        }

        // Initialize
        viewModel.initialize(teacherId)

        // Entrance animation
        AnimUtil.slideUp(rvMessages, 0)
    }

    // ── Quick Actions ─────────────────────────────────────────────────────

    private fun setupQuickActions() {
        layoutQuickActions.removeAllViews()

        for (action in PromptTemplates.TEACHER_QUICK_ACTIONS) {
            val chip = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.bg_quick_action_chip)
                setPadding(16, 10, 16, 10)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true

                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 10
                layoutParams = lp
            }

            chip.addView(TextView(this).apply {
                text = action.emoji
                textSize = 16f
                setPadding(0, 0, 8, 0)
            })

            chip.addView(TextView(this).apply {
                text = action.label
                textSize = 13f
                setTextColor(0xFFA5B4FC.toInt())
                maxLines = 1
            })

            chip.setOnClickListener {
                AnimUtil.bounce(it)
                viewModel.executeQuickAction(action)
            }

            layoutQuickActions.addView(chip)
        }
    }

    // ── Send message ──────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return

        etMessage.text.clear()
        viewModel.sendMessage(text)
    }

    // ── Confirmation dialog before action execution ───────────────────────

    private fun showConfirmationDialog(action: SuggestedAction) {
        val typeLabel = when (action.type) {
            SuggestedAction.ActionType.SEND_REMINDER     -> "📨 Send Reminder"
            SuggestedAction.ActionType.POST_ANNOUNCEMENT -> "📢 Post Announcement"
            SuggestedAction.ActionType.NOTIFY_GROUP      -> "🔔 Notify Group"
            SuggestedAction.ActionType.SEND_MESSAGE      -> "✉️ Send Message"
            SuggestedAction.ActionType.GENERATE_REPORT   -> "📋 Generate Report"
            SuggestedAction.ActionType.APPROVE_JOIN      -> "✅ Approve Join Requests"
            SuggestedAction.ActionType.REJECT_JOIN       -> "❌ Reject Join Request"
        }

        AlertDialog.Builder(this)
            .setTitle("Confirm Action")
            .setMessage("$typeLabel\n\n${action.label}\n\n${action.description}\n\nProceed?")
            .setPositiveButton("Execute ▶️") { _, _ ->
                viewModel.executeAction(action)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
