package com.example.feesmanager.ui.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.UnreadBadgeHelper
import kotlinx.coroutines.launch

/**
 * ChatHubActivity — Central hub for teacher chat navigation.
 * Shows two cards: Personal Chat and Class Chat, each with unread count badge.
 */
class ChatHubActivity : BaseActivity() {

    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_hub)
        teacherId = SessionManager.getTeacherId(this) ?: run { finish(); return }
        loadBadgesAndSetup()
    }

    override fun onResume() {
        super.onResume()
        loadBadgesAndSetup()
    }

    private fun loadBadgesAndSetup() {
        lifecycleScope.launch {
            // Fetch unread counts in parallel
            val personalUnread = UnreadBadgeHelper.fetchTotalPersonalUnreadForTeacher(this@ChatHubActivity, teacherId)
            val classUnread    = UnreadBadgeHelper.fetchTotalClassUnreadForTeacher(this@ChatHubActivity, teacherId)

            // Personal Chat card
            val cardPersonal = findViewById<FrameLayout>(R.id.cardPersonalChat)
            val tvPersonalBadge = findViewById<TextView>(R.id.tvPersonalBadge)
            tvPersonalBadge.text = if (personalUnread > 0) personalUnread.toString() else ""
            tvPersonalBadge.visibility = if (personalUnread > 0) View.VISIBLE else View.GONE

            // Class Chat card
            val tvClassBadge = findViewById<TextView>(R.id.tvClassBadge)
            tvClassBadge.text = if (classUnread > 0) classUnread.toString() else ""
            tvClassBadge.visibility = if (classUnread > 0) View.VISIBLE else View.GONE

            // Wire click listeners
            cardPersonal.setOnClickListener {
                AnimUtil.bounce(it)
                startActivity(Intent(this@ChatHubActivity, StudentQueriesActivity::class.java))
            }
            val cardClass = findViewById<FrameLayout>(R.id.cardClassChat)
            cardClass.setOnClickListener {
                AnimUtil.bounce(it)
                startActivity(Intent(this@ChatHubActivity, ClassSelectChatActivity::class.java))
            }
        }
    }
}