package com.example.feesmanager

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.feesmanager.ai.student.StudentFaqMatcher

/**
 * StudentFaqActivity — Help Center for students.
 *
 * Provides instant answers to common questions using local keyword matching.
 * No AI API calls — zero cost, works offline after first load.
 *
 * Features:
 *   - Suggested question chips
 *   - Chat-like Q&A interface
 *   - Follow-up suggestions after each answer
 *   - "Contact Teacher" fallback for unknown questions
 */
class StudentFaqActivity : BaseActivity() {

    private lateinit var rvMessages: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: View
    private lateinit var btnBack: View
    private lateinit var layoutSuggestions: LinearLayout

    private val messages = mutableListOf<FaqChatMessage>()
    private lateinit var adapter: FaqChatAdapter

    private var teacherId = ""
    private var studentId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_faq)

        teacherId = SessionManager.getStudentTeacherId(this) ?: ""
        studentId = SessionManager.getStudentId(this) ?: ""

        // Bind views
        rvMessages = findViewById(R.id.rvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)
        layoutSuggestions = findViewById(R.id.layoutSuggestions)

        // Setup RecyclerView
        adapter = FaqChatAdapter()
        rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        rvMessages.adapter = adapter

        // Setup suggested questions
        setupSuggestions()

        // Show welcome message
        addBotMessage("""🤖 **Welcome to Help Center!**

I can answer questions about:
• 💳 Fee payments & deadlines
• 🏫 Joining classes & enrollment
• 📱 How to use the app
• 📜 Payment history & receipts

Tap a suggestion below or type your question!""")

        // Button handlers
        btnSend.setOnClickListener { sendMessage() }
        btnBack.setOnClickListener { finish() }

        // Entrance animation
        AnimUtil.slideUp(rvMessages, 0)
    }

    // ── Suggested Questions ──────────────────────────────────────────────

    private fun setupSuggestions() {
        layoutSuggestions.removeAllViews()

        for (suggestion in StudentFaqMatcher.getSuggestedQuestions()) {
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
                text = suggestion.emoji
                textSize = 16f
                setPadding(0, 0, 8, 0)
            })

            chip.addView(TextView(this).apply {
                text = suggestion.label
                textSize = 13f
                setTextColor(0xFFA5B4FC.toInt())
                maxLines = 1
            })

            chip.setOnClickListener {
                AnimUtil.bounce(it)
                handleQuestion(suggestion.fullQuestion)
            }

            layoutSuggestions.addView(chip)
        }
    }

    // ── Message Handling ─────────────────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return
        etMessage.text.clear()
        handleQuestion(text)
    }

    private fun handleQuestion(question: String) {
        // Add user message
        addUserMessage(question)

        // Try to find FAQ answer
        val faqAnswer = StudentFaqMatcher.findAnswer(question)

        if (faqAnswer != null) {
            // ⚡ Found answer — show instantly
            addBotMessage(faqAnswer.answer)

            // Show follow-up suggestions
            if (faqAnswer.followUpSuggestions.isNotEmpty()) {
                showFollowUps(faqAnswer.followUpSuggestions)
            }
        } else {
            // ❌ No match — offer to contact teacher
            addBotMessage("""🤔 I don't have an answer for that specific question.

**Here's what you can do:**
• Try rephrasing your question
• Check the suggestions above
• Contact your teacher directly for help""")

            // Add contact teacher action
            if (teacherId.isNotEmpty()) {
                addContactTeacherButton()
            }
        }
    }

    private fun addUserMessage(text: String) {
        messages.add(FaqChatMessage(text, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    private fun addBotMessage(text: String) {
        messages.add(FaqChatMessage(text, isUser = false))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    private fun showFollowUps(suggestions: List<String>) {
        messages.add(FaqChatMessage(
            text = "",
            isUser = false,
            isFollowUp = true,
            followUps = suggestions
        ))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    private fun addContactTeacherButton() {
        messages.add(FaqChatMessage(
            text = "",
            isUser = false,
            isContactTeacher = true
        ))
        adapter.notifyItemInserted(messages.size - 1)
        rvMessages.scrollToPosition(messages.size - 1)
    }

    // ── Data Model ───────────────────────────────────────────────────────

    data class FaqChatMessage(
        val text: String,
        val isUser: Boolean,
        val isFollowUp: Boolean = false,
        val isContactTeacher: Boolean = false,
        val followUps: List<String> = emptyList()
    )

    // ── Adapter ─────────────────────────────────────────────────────────

    inner class FaqChatAdapter : RecyclerView.Adapter<FaqChatAdapter.VH>() {

        override fun getItemCount() = messages.size

        override fun getItemViewType(pos: Int): Int {
            val msg = messages[pos]
            return when {
                msg.isUser -> 0
                msg.isFollowUp -> 2
                msg.isContactTeacher -> 3
                else -> 1
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            return when (viewType) {
                0 -> VH(createUserBubble(ctx))
                2 -> VH(createFollowUpView(ctx))
                3 -> VH(createContactTeacherView(ctx))
                else -> VH(createBotBubble(ctx))
            }
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = messages[position]
            when (getItemViewType(position)) {
                0 -> holder.itemView.findViewWithTag<TextView>("text")?.text = msg.text
                1 -> holder.itemView.findViewWithTag<TextView>("text")?.text = formatMarkdown(msg.text)
                2 -> bindFollowUps(holder, msg)
            }
        }

        private fun createUserBubble(context: android.content.Context): View {
            return FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }

                val bubble = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_ai_bubble_user)
                    setPadding(18, 14, 18, 14)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.END
                    ).apply { marginStart = 60 }
                }

                val tvText = TextView(context).apply {
                    tag = "text"
                    textSize = 15f
                    setTextColor(0xFFF1F5F9.toInt())
                }
                bubble.addView(tvText)
                addView(bubble)
            }
        }

        private fun createBotBubble(context: android.content.Context): View {
            return FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16 }

                val wrapper = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.START
                    ).apply { marginEnd = 40 }
                }

                wrapper.addView(TextView(context).apply {
                    text = "❓"
                    textSize = 20f
                    setPadding(0, 4, 10, 0)
                })

                val contentCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.bg_ai_bubble_bot)
                    setPadding(16, 14, 16, 14)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tvText = TextView(context).apply {
                    tag = "text"
                    textSize = 14f
                    setTextColor(0xFFE2E8F0.toInt())
                    setLineSpacing(4f, 1.1f)
                }
                contentCol.addView(tvText)
                wrapper.addView(contentCol)
                addView(wrapper)
            }
        }

        private fun createFollowUpView(context: android.content.Context): View {
            return LinearLayout(context).apply {
                tag = "followupContainer"
                orientation = LinearLayout.HORIZONTAL
                setPadding(44, 4, 16, 12)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
        }

        private fun createContactTeacherView(context: android.content.Context): View {
            return FrameLayout(context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 12 }

                val btn = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundResource(R.drawable.bg_action_card)
                    setPadding(16, 14, 16, 14)
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.START
                    ).apply { marginStart = 44 }
                }

                btn.addView(TextView(context).apply {
                    text = "✉️"
                    textSize = 18f
                    setPadding(0, 0, 12, 0)
                })

                btn.addView(TextView(context).apply {
                    text = "Contact Teacher"
                    textSize = 14f
                    setTextColor(0xFFA5B4FC.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                })

                btn.setOnClickListener {
                    startActivity(
                        Intent(this@StudentFaqActivity, MessageActivity::class.java)
                            .putExtra("mode", "student")
                            .putExtra("teacherId", teacherId)
                            .putExtra("studentId", studentId)
                    )
                }

                addView(btn)
            }
        }

        private fun bindFollowUps(holder: VH, msg: FaqChatMessage) {
            val container = holder.itemView as LinearLayout
            container.removeAllViews()

            for (suggestion in msg.followUps) {
                val chip = TextView(container.context).apply {
                    text = suggestion
                    textSize = 12f
                    setTextColor(0xFFA5B4FC.toInt())
                    setBackgroundResource(R.drawable.bg_quick_action_chip)
                    setPadding(14, 8, 14, 8)
                    isClickable = true
                    isFocusable = true
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = 8
                    layoutParams = lp
                }
                chip.setOnClickListener {
                    handleQuestion(suggestion)
                }
                container.addView(chip)
            }
        }

        // Basic markdown formatter
        private fun formatMarkdown(text: String): CharSequence {
            val builder = SpannableStringBuilder()
            val lines = text.split("\n")

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("## ") -> {
                        val headerText = trimmed.removePrefix("## ")
                        val start = builder.length
                        builder.append(headerText)
                        builder.setSpan(
                            StyleSpan(Typeface.BOLD), start, builder.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        builder.append("\n")
                    }
                    trimmed.contains("**") -> {
                        val parts = trimmed.split("**")
                        for (i in parts.indices) {
                            val start = builder.length
                            builder.append(parts[i])
                            if (i % 2 == 1) {
                                builder.setSpan(
                                    StyleSpan(Typeface.BOLD), start, builder.length,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                        builder.append("\n")
                    }
                    trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*") -> {
                        builder.append("  $trimmed\n")
                    }
                    trimmed.startsWith("💡") || trimmed.startsWith("⚠️") || trimmed.startsWith("✅") -> {
                        val start = builder.length
                        builder.append(trimmed)
                        builder.setSpan(
                            StyleSpan(Typeface.ITALIC), start, builder.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        builder.append("\n")
                    }
                    else -> builder.append("$trimmed\n")
                }
            }

            return builder.trimEnd()
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view)
    }
}
