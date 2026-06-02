package com.example.feesmanager.data.model

/** Conversation preview item shown in StudentQueriesActivity inbox. */
data class ConversationSummary(
    val studentId: String   = "",
    val studentName: String = "",
    val lastText: String    = "",
    val lastSender: String  = "",   // "student" | "teacher"
    val unreadCount: Int    = 0
)
