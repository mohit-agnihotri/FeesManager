package com.example.feesmanager

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.ChatMessage
import com.example.feesmanager.ui.chat.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * MessageActivity — Professional personal chat between teacher ↔ student.
 * WhatsApp-like UI with avatar initial, timestamps, and realtime updates.
 */
class MessageActivity : BaseActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var container     : LinearLayout
    private lateinit var etMessage     : EditText
    private lateinit var btnSend       : Button
    private lateinit var scroll        : ScrollView
    private lateinit var tvTitle       : TextView
    private lateinit var tvStatus      : TextView
    private lateinit var tvInitial     : TextView
    private lateinit var ivAvatar      : android.widget.ImageView

    private lateinit var mode       : String
    private lateinit var senderName : String
    private lateinit var teacherId  : String
    private lateinit var studentId  : String
    private lateinit var senderId   : String

    private val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val sdfDay = SimpleDateFormat("dd MMM", Locale.getDefault())
    private var lastMessageDate = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        container  = findViewById(R.id.messageContainer)
        etMessage  = findViewById(R.id.etMessageInput)
        btnSend    = findViewById(R.id.btnSendMessage)
        scroll     = findViewById(R.id.messageScroll)
        tvTitle    = findViewById(R.id.tvMessageTitle)
        tvStatus   = runCatching { findViewById<TextView>(R.id.tvOnlineStatus) }.getOrElse { TextView(this) }
        tvInitial  = runCatching { findViewById<TextView>(R.id.tvAvatarInitial) }.getOrElse { TextView(this) }
        ivAvatar   = runCatching { findViewById<android.widget.ImageView>(R.id.ivChatAvatar) }.getOrElse { android.widget.ImageView(this) }

        mode           = intent.getStringExtra("mode") ?: "student"
        teacherId      = intent.getStringExtra("teacherId") ?: ""
        studentId      = intent.getStringExtra("studentId") ?: ""
        val otherName  = intent.getStringExtra("studentName") ?: "Chat"

        if (teacherId.isEmpty() || studentId.isEmpty()) { toast("Invalid session"); finish(); return }

        senderName = if (mode == "teacher")
            SessionManager.getTeacherName(this) ?: "Teacher"
        else
            SessionManager.getStudentName(this) ?: otherName
        senderId = if (mode == "teacher") teacherId else studentId

        tvTitle.text = if (mode == "teacher") "💬 $otherName" else "💬 Your Teacher"
        tvStatus.text = "Supabase Realtime"
        tvInitial.text = otherName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        runCatching {
            GlideHelper.loadAvatar(ivAvatar, null)
            ivAvatar.visibility = android.view.View.GONE
        }

        runCatching { 
            findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() } 
        }

        // Observe messages
        viewModel.messages.observe(this) { result ->
            when (result) {
                is FmResult.Success -> renderMessages(result.content)
                is FmResult.Error   -> toast("Failed: ${result.message}")
                else -> {}
            }
        }

        // Observe send result
        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> btnSend.isEnabled = false
                is FmResult.Success -> {
                    etMessage.text.clear()
                    btnSend.isEnabled = true
                }
                is FmResult.Error   -> {
                    btnSend.isEnabled = true
                    toast("Send failed: ${result.message}")
                }
            }
        }

        viewModel.loadPersonalChat(teacherId, studentId)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
        }

        // Enter key sends
        etMessage.setOnEditorActionListener { _, _, _ ->
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
            true
        }
    }

    private fun renderMessages(messages: List<ChatMessage>) {
        container.removeAllViews()
        lastMessageDate = ""
        messages.forEach { msg ->
            val isMine = msg.sender == senderId
            // Date separator
            val msgDay = runCatching {
                val ts = msg.timestamp.take(10)
                if (ts != lastMessageDate) {
                    lastMessageDate = ts
                    addDateSeparator(if (ts == sdfDay.format(Date())) "Today" else ts)
                }
            }
            container.addView(buildBubble(msg, isMine))
        }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun addDateSeparator(label: String) {
        val tv = TextView(this).apply {
            text = label; textSize = 11f; setTextColor(0xFF94A3B8.toInt())
            gravity = Gravity.CENTER; setPadding(0, 12, 0, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(tv)
    }

    private fun buildBubble(msg: ChatMessage, isMine: Boolean): LinearLayout {
        val timeStr = runCatching {
            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.format(inFmt.parse(msg.timestamp.take(19))!!)
        }.getOrElse { sdf.format(Date()) }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = if (isMine) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, 0, 4)
            layoutParams = lp
        }

        // Avatar for other person
        if (!isMine) {
            val av = TextView(this).apply {
                text = msg.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_avatar_circle)
                val lp = LinearLayout.LayoutParams(32.dp, 32.dp)
                lp.setMargins(0, 4, 8, 0); layoutParams = lp
            }
            wrapper.addView(av)
        }

        // Bubble card
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (isMine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_other)
            setPadding(16.dp, 10.dp, 16.dp, 8.dp)
            val maxW = (resources.displayMetrics.widthPixels * 0.72).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Sender name (for group / other side only)
        if (!isMine) {
            bubble.addView(TextView(this).apply {
                text = msg.senderName; textSize = 10f
                setTextColor(0xFF818CF8.toInt())
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 2; layoutParams = lp
            })
        }

        // Message text
        bubble.addView(TextView(this).apply {
            text = msg.text; textSize = 15f
            setTextColor(0xFFF1F5F9.toInt())
            setLineSpacing(0f, 1.35f)
        })

        // Time
        bubble.addView(TextView(this).apply {
            text = timeStr; textSize = 9f
            setTextColor(if (isMine) 0xFF818CF8.toInt() else 0xFF64748B.toInt())
            gravity = if (isMine) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 2; layoutParams = lp
        })

        wrapper.addView(bubble)
        return wrapper
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
