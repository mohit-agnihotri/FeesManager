package com.example.feesmanager.ai.adapter

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import com.example.feesmanager.R
import com.example.feesmanager.ai.AiChatMessage
import com.example.feesmanager.ai.Priority
import com.example.feesmanager.ai.SuggestedAction

/**
 * AiChatAdapter — RecyclerView adapter for Teacher AI Agent chat messages.
 *
 * Handles multiple view types:
 *   - User messages (right-aligned, indigo)
 *   - AI messages (left-aligned, dark card, action buttons)
 *   - Loading indicator (typing dots animation)
 */
class AiChatAdapter(
    private val onActionClick: ((SuggestedAction) -> Unit)? = null
) : RecyclerView.Adapter<AiChatAdapter.ChatViewHolder>() {

    private var messages = listOf<AiChatMessage>()

    companion object {
        private const val VIEW_TYPE_USER    = 0
        private const val VIEW_TYPE_AI      = 1
        private const val VIEW_TYPE_LOADING = 2
    }

    fun submitList(newMessages: List<AiChatMessage>) {
        val oldSize = messages.size
        messages = newMessages
        val newSize = messages.size

        when {
            oldSize == 0 -> notifyDataSetChanged()
            newSize > oldSize -> {
                if (oldSize > 0) notifyItemChanged(oldSize - 1)
                notifyItemRangeInserted(oldSize, newSize - oldSize)
            }
            newSize < oldSize -> {
                notifyDataSetChanged()
            }
            else -> {
                notifyDataSetChanged()
            }
        }
    }

    override fun getItemCount() = messages.size

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        return when {
            msg.isLoading                      -> VIEW_TYPE_LOADING
            msg.role == AiChatMessage.Role.USER -> VIEW_TYPE_USER
            else                               -> VIEW_TYPE_AI
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val context = parent.context
        return when (viewType) {
            VIEW_TYPE_USER    -> ChatViewHolder(createUserBubble(context))
            VIEW_TYPE_LOADING -> ChatViewHolder(createLoadingView(context))
            else              -> ChatViewHolder(createAiBubble(context))
        }
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        when (getItemViewType(position)) {
            VIEW_TYPE_USER  -> bindUserMessage(holder, msg)
            VIEW_TYPE_AI    -> bindAiMessage(holder, msg)
        }
    }

    // ── User Message ───────────────────────────────────────────────────────

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

    private fun bindUserMessage(holder: ChatViewHolder, msg: AiChatMessage) {
        holder.itemView.findViewWithTag<TextView>("text")?.text = msg.content
    }

    // ── AI Message ─────────────────────────────────────────────────────────

    private fun createAiBubble(context: android.content.Context): View {
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

            // AI avatar
            val avatar = TextView(context).apply {
                text = "🤖"
                textSize = 20f
                setPadding(0, 4, 10, 0)
            }
            wrapper.addView(avatar)

            // Content column
            val contentCol = LinearLayout(context).apply {
                tag = "contentCol"
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

            // Actions container
            val actionsContainer = LinearLayout(context).apply {
                tag = "actions"
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
            }
            contentCol.addView(actionsContainer)

            wrapper.addView(contentCol)
            addView(wrapper)
        }
    }

    private fun bindAiMessage(holder: ChatViewHolder, msg: AiChatMessage) {
        val view = holder.itemView

        // Set text with basic markdown formatting
        view.findViewWithTag<TextView>("text")?.text = formatMarkdown(msg.content)

        // Priority border
        val contentCol = view.findViewWithTag<LinearLayout>("contentCol")
        if (msg.priority == Priority.URGENT) {
            contentCol?.setBackgroundResource(R.drawable.bg_priority_urgent)
        } else {
            contentCol?.setBackgroundResource(R.drawable.bg_ai_bubble_bot)
        }

        // Actions
        val actionsContainer = view.findViewWithTag<LinearLayout>("actions")
        actionsContainer?.removeAllViews()
        if (msg.actions.isNotEmpty()) {
            actionsContainer?.visibility = View.VISIBLE
            msg.actions.forEach { action ->
                addActionButton(actionsContainer!!, action)
            }
        } else {
            actionsContainer?.visibility = View.GONE
        }
    }

    private fun addActionButton(container: LinearLayout, action: SuggestedAction) {
        val ctx = container.context

        val btn = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_action_card)
            setPadding(16, 12, 16, 12)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 10
            layoutParams = lp
        }

        // Action type icon
        val icon = TextView(ctx).apply {
            text = when (action.type) {
                SuggestedAction.ActionType.SEND_REMINDER     -> "📨"
                SuggestedAction.ActionType.POST_ANNOUNCEMENT -> "📢"
                SuggestedAction.ActionType.NOTIFY_GROUP      -> "🔔"
                SuggestedAction.ActionType.SEND_MESSAGE      -> "✉️"
                SuggestedAction.ActionType.GENERATE_REPORT   -> "📋"
                SuggestedAction.ActionType.APPROVE_JOIN      -> "✅"
                SuggestedAction.ActionType.REJECT_JOIN       -> "❌"
            }
            textSize = 18f
            setPadding(0, 0, 12, 0)
        }
        btn.addView(icon)

        // Text column
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textCol.addView(TextView(ctx).apply {
            text = action.label
            textSize = 14f
            setTextColor(0xFFF1F5F9.toInt())
            typeface = Typeface.DEFAULT_BOLD
        })

        if (action.description.isNotBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = action.description
                textSize = 11f
                setTextColor(0xFF94A3B8.toInt())
                maxLines = 2
            })
        }

        // Show result message after execution
        if (action.executed && action.resultMessage.isNotBlank()) {
            textCol.addView(TextView(ctx).apply {
                text = action.resultMessage
                textSize = 11f
                setTextColor(0xFF86EFAC.toInt())  // Green for success
                setPadding(0, 4, 0, 0)
            })
        }

        btn.addView(textCol)

        // Execute/done indicator
        val executeIcon = TextView(ctx).apply {
            text = if (action.executed) "✅" else "▶️"
            textSize = 16f
        }
        btn.addView(executeIcon)

        // Only clickable if not yet executed
        if (!action.executed) {
            btn.setOnClickListener {
                executeIcon.text = "⏳"
                onActionClick?.invoke(action)
            }
        } else {
            // Dim the card for executed actions
            btn.alpha = 0.7f
        }

        container.addView(btn)
    }

    // ── Loading View ───────────────────────────────────────────────────────

    private fun createLoadingView(context: android.content.Context): View {
        return FrameLayout(context).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }

            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.START
                )
            }

            wrapper.addView(TextView(context).apply {
                text = "🤖"
                textSize = 20f
                setPadding(0, 0, 10, 0)
            })

            val dotsFrame = LinearLayout(context).apply {
                setBackgroundResource(R.drawable.bg_ai_bubble_bot)
                setPadding(20, 14, 20, 14)
            }

            val dots = TextView(context).apply {
                text = "Analyzing..."
                textSize = 13f
                setTextColor(0xFF818CF8.toInt())
                setTypeface(null, Typeface.ITALIC)
            }
            dotsFrame.addView(dots)
            wrapper.addView(dotsFrame)
            addView(wrapper)

            // Dots animation
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var dotCount = 0
            val runnable = object : Runnable {
                override fun run() {
                    dotCount = (dotCount + 1) % 4
                    dots.text = "Analyzing" + ".".repeat(dotCount)
                    handler.postDelayed(this, 500)
                }
            }
            handler.post(runnable)
            this.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}
                override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacks(runnable) }
            })
        }
    }

    // ── Markdown Formatting ────────────────────────────────────────────────

    private fun formatMarkdown(text: String): CharSequence {
        val builder = SpannableStringBuilder()
        val lines = text.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            when {
                // Headers (## Header)
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
                // Bold (**text**)
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
                // Bullet points
                trimmed.startsWith("• ") || trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    builder.append("  $trimmed\n")
                }
                // Remove [PRIORITY:...] tags from display
                trimmed.contains("[PRIORITY:") -> {
                    val cleaned = trimmed.replace(Regex("\\[PRIORITY:\\w+]"), "").trim()
                    if (cleaned.isNotBlank()) {
                        builder.append("$cleaned\n")
                    }
                }
                else -> {
                    builder.append("$trimmed\n")
                }
            }
        }

        return builder.trimEnd()
    }

    // ── ViewHolder ─────────────────────────────────────────────────────────

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
