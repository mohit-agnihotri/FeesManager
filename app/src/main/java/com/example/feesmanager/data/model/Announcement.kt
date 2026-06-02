package com.example.feesmanager.data.model

data class Announcement(
    val id          : String = "",
    val title       : String = "",
    val body        : String = "",
    val timestamp   : Long   = 0L,
    val teacherId   : String = "",
    val createdAt   : String = "",
    val targetClass : String? = "all"  // "all" or specific class name
)
