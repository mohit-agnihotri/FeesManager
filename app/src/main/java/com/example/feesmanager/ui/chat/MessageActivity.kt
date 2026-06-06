package com.example.feesmanager.ui.chat

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
    private lateinit var ivAvatar      : ImageView
    private lateinit var btnAttach     : ImageView

    private lateinit var mode       : String
    private lateinit var senderName : String
    private lateinit var teacherId  : String
    private lateinit var studentId  : String
    private lateinit var senderId   : String
    private var otherPhotoUrl: String? = null

    private val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val sdfDay = SimpleDateFormat("dd MMM", Locale.getDefault())
    private var lastMessageDate = ""

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
        scroll     = findViewById(R.id.messageScroll)
        tvTitle    = findViewById(R.id.tvMessageTitle)
        tvStatus   = runCatching { findViewById<TextView>(R.id.tvOnlineStatus) }.getOrElse {
            TextView(
                this
            )
        }
        tvInitial  = runCatching { findViewById<TextView>(R.id.tvAvatarInitial) }.getOrElse {
            TextView(
                this
            )
        }
        ivAvatar   = runCatching { findViewById<ImageView>(R.id.ivChatAvatar) }.getOrElse { ImageView(this) }
        btnAttach  = runCatching { findViewById<ImageView>(R.id.btnAttach) }.getOrElse { ImageView(this) }

        mode           = intent.getStringExtra("mode") ?: "student"
        teacherId      = intent.getStringExtra("teacherId") ?: ""
        studentId      = intent.getStringExtra("studentId") ?: ""
        val otherName  = intent.getStringExtra("studentName") ?: "Chat"
        otherPhotoUrl  = intent.getStringExtra("otherPhotoUrl")

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
            ivAvatar.visibility = View.GONE
        }

        runCatching {
            findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        }

        // Observe messages
        viewModel.messages.observe(this) { result ->
            when (result) {
                is FmResult.Success -> {
                    renderMessages(result.content)
                    if (otherPhotoUrl.isNullOrEmpty()) {
                        viewModel.fetchAvatars(listOf(if (mode == "teacher") studentId else teacherId))
                    }
                }
                is FmResult.Error   -> toast("Failed: ${result.message}")
                else -> {}
            }
        }

        viewModel.avatars.observe(this) {
            val otherId = if (mode == "teacher") studentId else teacherId
            val fetchedUrl = it[otherId]
            if (!fetchedUrl.isNullOrEmpty() && (otherPhotoUrl == null || otherPhotoUrl!!.isEmpty())) {
                otherPhotoUrl = fetchedUrl
                ivAvatar.visibility = View.VISIBLE
                tvInitial.visibility = View.GONE
                GlideHelper.loadAvatarFresh(ivAvatar, otherPhotoUrl!!)
                val result = viewModel.messages.value
                if (result is FmResult.Success) {
                    renderMessages(result.content)
                }
            }
        }

        // Observe send result
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

        viewModel.loadPersonalChat(teacherId, studentId)

        btnSend.setOnClickListener {
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
        }

        btnAttach.setOnClickListener {
            showAttachmentMenu()
        }

        // Enter key sends
        etMessage.setOnEditorActionListener { _, _, _ ->
            val text = etMessage.text.toString().trim()
            if (text.isNotEmpty()) viewModel.sendMessage(text, senderId, senderName)
            true
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
        UnreadBadgeHelper.markAsRead(this, senderId, studentId)
        container.removeAllViews()
        lastMessageDate = ""
        val visibleMessages = messages.filter { senderId !in it.deletedBy }
        visibleMessages.forEach { msg ->
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
            val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.format(inFmt.parse(msg.timestamp.take(19))!!)
        }.getOrElse { sdf.format(Date()) }

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = 4.dp
            lp.bottomMargin = 4.dp
            lp.marginStart = 12.dp
            lp.marginEnd = 12.dp
            layoutParams = lp
            gravity = if (isMine) Gravity.END else Gravity.START
        }

        // Avatar for other person
        if (!isMine) {
            val lp = LinearLayout.LayoutParams(32.dp, 32.dp).apply { setMargins(0, 4, 8, 0) }
            val photoToUse = otherPhotoUrl ?: viewModel.avatars.value.orEmpty()[msg.sender]
            if (photoToUse != null && photoToUse.isNotEmpty()) {
                val iv = ImageView(this).apply {
                    layoutParams = lp
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = resources.getDrawable(R.drawable.bg_avatar_circle, null)
                    clipToOutline = true
                }
                wrapper.addView(iv)
                GlideHelper.loadAvatarFresh(iv, photoToUse)
            } else {
                val av = TextView(this).apply {
                    text = msg.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                    textSize = 11f; setTextColor(0xFFFFFFFF.toInt())
                    gravity = Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_avatar_circle)
                    layoutParams = lp
                }
                wrapper.addView(av)
            }
        }

        val maxW = (resources.displayMetrics.widthPixels * 0.72).toInt()
        // Bubble card
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(if (isMine) R.drawable.bg_bubble_mine else R.drawable.bg_bubble_other)
            setPadding(16.dp, 10.dp, 16.dp, 8.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnLongClickListener {
                showDeleteOptions(msg, isMine)
                true
            }
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
            text = msg.text; textSize = 14f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.3f)
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

        // Time
        bubble.addView(TextView(this).apply {
            text = timeStr; textSize = 9f
            setTextColor(if (isMine) 0xFF818CF8.toInt() else 0xFF64748B.toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
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
                        FileViewerHelper.downloadAndOpenFile(this@MessageActivity, url)
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