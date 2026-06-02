package com.example.feesmanager.data.model

data class ChatMessage(
    val id         : String = "",
    val text       : String = "",
    val sender     : String = "",   // "teacher" | studentId
    val senderName : String = "",
    val timestamp  : String = "",   // ISO timestamp from Supabase
    val isClass    : Boolean = false,
    val time       : Long   = 0L    // kept for backward compat
)
