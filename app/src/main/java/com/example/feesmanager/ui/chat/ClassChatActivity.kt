package com.example.feesmanager.ui.chat

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.ChatMessage
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.FileViewerHelper
import com.example.feesmanager.utils.GlideHelper
import com.example.feesmanager.utils.UnreadBadgeHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * ClassChatActivity — WhatsApp-like group chat for a class.
 * Shared by teacher (sends as "Teacher") and students (sends as their name).
 */
class ClassChatActivity : BaseActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private lateinit var container : LinearLayout
    private lateinit var etMessage : EditText
    private lateinit var btnSend   : Button
    private lateinit var btnAttach : ImageView
    private lateinit var scroll    : ScrollView
    private lateinit var tvTitle   : TextView

    private lateinit var teacherId  : String
    private lateinit var className  : String
    private lateinit var senderName : String
    private lateinit var senderId   : String

    private val sdf    = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private var lastDay = ""

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val text = etMessage.text.toString().trim()
            viewModel.sendAttachments(this, uris, text, senderId, senderName)
            etMessage.text.clear()
        }
    }

    private val documentPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val text = etMessage.text.toString().trim()
            viewModel.sendAttachments(this, uris, text, senderId, senderName)
            etMessage.text.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message)

        container  = findViewById(R.id.messageContainer)
        etMessage  = findViewById(R.id.etMessageInput)
        btnSend    = findViewById(R.id.btnSendMessage)
        btnAttach  = runCatching { findViewById<ImageView>(R.id.btnAttach) }.getOrElse { ImageView(this) }
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
            findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        }

        if (teacherId.isEmpty() || className.isEmpty()) { finish(); return }

        viewModel.messages.observe(this) { result ->
            when (result) {
                is FmResult.Success -> {
                    val msgs = result.content
                    renderMessages(msgs)
                    val unknownIds = msgs.map { it.sender }.distinct()
                        .filter { !viewModel.avatars.value.orEmpty().containsKey(it) }
                    if (unknownIds.isNotEmpty()) {
                        viewModel.fetchAvatars(unknownIds)
                    }
                }
                is FmResult.Error   -> toast("Chat load failed: ${result.message}")
                else -> {}
            }
        }

        viewModel.avatars.observe(this) {
            val result = viewModel.messages.value
            if (result is FmResult.Success) {
                renderMessages(result.content)
            }
        }

        viewModel.sendResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {
                    btnSend.isEnabled = false
                    btnAttach.isEnabled = false
                }
                is FmResult.Success -> {
                    etMessage.text.clear()
                    btnSend.isEnabled = true
                    btnAttach.isEnabled = true
                }
                is FmResult.Error   -> {
                    btnSend.isEnabled = true
                    btnAttach.isEnabled = true
                    toast("Send failed: ${result.message}")
                }
            }
        }

        viewModel.uploadingAttachment.observe(this) { isUploading ->
            if (isUploading) {
                btnSend.isEnabled = false
                btnAttach.isEnabled = false
                toast("Uploading attachment...")
            } else {
                btnSend.isEnabled = true
                btnAttach.isEnabled = true
            }
        }

        viewModel.loadClassChat(teacherId, className)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
        }

        btnAttach.setOnClickListener {
            showAttachmentMenu()
        }
    }

    private fun showAttachmentMenu() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_attachment_menu, null)
        view.findViewById<View>(R.id.btnAttachGallery)?.setOnClickListener {
            galleryPicker.launch("image/*")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btnAttachDocument)?.setOnClickListener {
            documentPicker.launch("*/*")
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun renderMessages(messages: List<ChatMessage>) {
        UnreadBadgeHelper.markAsRead(this, senderId, className)
        container.removeAllViews()
        lastDay = ""
        val visibleMessages = messages.filter { senderId !in it.deletedBy }
        visibleMessages.forEach { msg ->
            val isMine = msg.sender == senderId
            val day = msg.timestamp.take(10)
            if (day != lastDay) {
                lastDay = day
                container.addView(dateSep(day))
            }
            container.addView(createMessageBubble(msg, isMine, false))
        }
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun dateSep(date: String): TextView = TextView(this).apply {
        text = date; textSize = 11f; setTextColor(0xFF94A3B8.toInt())
        gravity = Gravity.CENTER; setPadding(0, 10, 0, 6)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun createMessageBubble(msg: ChatMessage, isMine: Boolean, showDate: Boolean): View {
        val timeStr = runCatching {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.format(f.parse(msg.timestamp.take(19))!!)
        }.getOrElse { sdf.format(Date()) }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4.dp
            lp.bottomMargin = 4.dp
            lp.marginStart = 12.dp
            lp.marginEnd = 12.dp
            layoutParams = lp
            gravity = if (isMine) Gravity.END else Gravity.START
        }

        if (!isMine) {
            val av = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(28.dp, 28.dp).apply { rightMargin = 8.dp }

                val iv = ImageView(this@ClassChatActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = resources.getDrawable(R.drawable.bg_avatar_circle, null)
                    clipToOutline = true
                }

                val tv = TextView(this@ClassChatActivity).apply {
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    text = msg.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    background = resources.getDrawable(R.drawable.bg_avatar_circle, null)
                }

                addView(tv)
                addView(iv)

                val photoUrl = viewModel.avatars.value.orEmpty()[msg.sender]
                if (!photoUrl.isNullOrEmpty()) {
                    iv.visibility = View.VISIBLE
                    tv.visibility = View.GONE
                    GlideHelper.loadAvatarFresh(iv, photoUrl)
                } else {
                    iv.visibility = View.GONE
                    tv.visibility = View.VISIBLE
                }
            }
            wrapper.addView(av)
        }

        val maxW = (resources.displayMetrics.widthPixels * 0.70).toInt()
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (isMine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_other)
            setPadding(14.dp, 10.dp, 14.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnLongClickListener {
                showDeleteOptions(msg, isMine)
                true
            }
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
            autoLinkMask = android.text.util.Linkify.WEB_URLS
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setLinkTextColor(0xFF60A5FA.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            maxWidth = maxW
        })

        if (!msg.attachmentUrl.isNullOrEmpty()) {
            val attachView = createAttachmentView(msg.attachmentUrl, maxW)
            bubble.addView(attachView)
        }

        bubble.addView(TextView(this).apply {
            text = timeStr; textSize = 9f
            setTextColor(if (isMine) 0xFF818CF8.toInt() else 0xFF64748B.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.gravity = if (isMine) Gravity.END else Gravity.START
            lp.topMargin = 2; layoutParams = lp
        })

        wrapper.addView(bubble)
        return wrapper
    }

    private fun createAttachmentView(url: String, maxW: Int): View {
        val ext = url.substringAfterLast('.', "").lowercase()
        return if (ext in listOf("png", "jpg", "jpeg", "gif", "webp")) {
            ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 200.dp
                ).apply {
                    topMargin = 8.dp
                    bottomMargin = 4.dp
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                GlideHelper.loadAvatarFresh(this, url)
                setOnClickListener {
                    lifecycleScope.launch {
                        FileViewerHelper.downloadAndOpenFile(this@ClassChatActivity, url)
                    }
                }
            }
        } else {
            Button(this).apply {
                text = "📄 View Document"
                isAllCaps = false
                textSize = 12f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x44000000)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 8.dp
                    bottomMargin = 4.dp
                }
                setOnClickListener {
                    lifecycleScope.launch {
                        FileViewerHelper.downloadAndOpenFile(this@ClassChatActivity, url)
                    }
                }
            }
        }
    }

    private fun showDeleteOptions(msg: ChatMessage, isMine: Boolean) {
        val options = mutableListOf<String>()
        options.add("Delete for me")
        if (isMine) {
            options.add("Delete for everyone")
        }

        AlertDialog.Builder(this)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Delete for me" -> viewModel.deleteMessageForMe(msg.id, senderId, msg.deletedBy)
                    "Delete for everyone" -> viewModel.deleteMessageForEveryone(msg.id)
                }
            }
            .show()
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}