package com.example.feesmanager

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.ChatMessage
import com.example.feesmanager.ui.chat.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * ClassChatActivity — WhatsApp-like group chat for a class.
 * Shared by teacher (sends as "Teacher") and students (sends as their name).
 */
class ClassChatActivity : BaseActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var container : LinearLayout
    private lateinit var etMessage : EditText
    private lateinit var btnSend   : Button
    private lateinit var scroll    : ScrollView
    private lateinit var tvTitle   : TextView

    private lateinit var teacherId  : String
    private lateinit var className  : String
    private lateinit var senderName : String
    private lateinit var senderId   : String

    private val sdf    = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private var lastDay = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        container  = findViewById(R.id.messageContainer)
        etMessage  = findViewById(R.id.etMessageInput)
        btnSend    = findViewById(R.id.btnSendMessage)
        scroll     = findViewById(R.id.messageScroll)
        tvTitle    = findViewById(R.id.tvMessageTitle)

        runCatching { 
            val tvStatus = findViewById<TextView>(R.id.tvOnlineStatus)
            tvStatus?.text = "Group Chat • Realtime"
        }
        runCatching {
            val tvInitial = findViewById<TextView>(R.id.tvAvatarInitial)
            tvInitial?.text = "G"
        }

        teacherId  = intent.getStringExtra("teacherId") ?: ""
        className  = intent.getStringExtra("className") ?: ""
        val mode   = intent.getStringExtra("mode") ?: "student"
        senderName = intent.getStringExtra("senderName")
            ?: if (mode == "teacher") (SessionManager.getTeacherName(this) ?: "Teacher")
               else (SessionManager.getStudentName(this) ?: "Student")
        senderId   = if (mode == "teacher") teacherId
                     else (intent.getStringExtra("studentId") ?: SessionManager.getStudentId(this) ?: "")

        tvTitle.text = "🏫 Class $className"

        runCatching { 
            findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() } 
        }

        if (teacherId.isEmpty() || className.isEmpty()) { finish(); return }

        viewModel.messages.observe(this) { result ->
            when (result) {
                is FmResult.Success -> renderMessages(result.content)
                is FmResult.Error   -> toast("Chat load failed: ${result.message}")
                else -> {}
            }
        }

        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> btnSend.isEnabled = false
                is FmResult.Success -> { etMessage.text.clear(); btnSend.isEnabled = true }
                is FmResult.Error   -> { btnSend.isEnabled = true; toast("Send failed: ${result.message}") }
            }
        }

        viewModel.loadClassChat(teacherId, className)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
        }
    }

    private fun renderMessages(messages: List<ChatMessage>) {
        container.removeAllViews()
        lastDay = ""
        messages.forEach { msg ->
            val isMine = msg.sender == senderId
            val day = msg.timestamp.take(10)
            if (day != lastDay) {
                lastDay = day
                container.addView(dateSep(day))
            }
            container.addView(buildBubble(msg, isMine))
        }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun dateSep(date: String): TextView = TextView(this).apply {
        text = date; textSize = 11f; setTextColor(0xFF94A3B8.toInt())
        gravity = Gravity.CENTER; setPadding(0, 10, 0, 6)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun buildBubble(msg: ChatMessage, isMine: Boolean): LinearLayout {
        val timeStr = runCatching {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.format(f.parse(msg.timestamp.take(19))!!)
        }.getOrElse { sdf.format(Date()) }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = if (isMine) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 4); layoutParams = lp
        }

        if (!isMine) {
            val av = TextView(this).apply {
                text = msg.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                textSize = 10f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
                setBackgroundResource(R.drawable.bg_avatar_circle)
                val lp = LinearLayout.LayoutParams(30.dp, 30.dp)
                lp.setMargins(0, 4, 8, 0); layoutParams = lp
            }
            wrapper.addView(av)
        }

        val maxW = (resources.displayMetrics.widthPixels * 0.70).toInt()
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (isMine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_other)
            setPadding(14.dp, 10.dp, 14.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        if (!isMine) {
            bubble.addView(TextView(this).apply {
                text = msg.senderName; textSize = 10f; setTextColor(0xFF34D399.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 2; layoutParams = lp
            })
        }

        bubble.addView(TextView(this).apply {
            text = msg.text; textSize = 14f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.3f)
        })
        bubble.addView(TextView(this).apply {
            text = timeStr; textSize = 9f
            setTextColor(if (isMine) 0xFF818CF8.toInt() else 0xFF64748B.toInt())
            gravity = if (isMine) Gravity.END else Gravity.START
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 2; layoutParams = lp
        })

        wrapper.addView(bubble)
        return wrapper
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
