package com.example.feesmanager.data.model

import kotlinx.serialization.Serializable

/**
 * Student data model.
 *
 * Path: app/src/main/java/com/example/feesmanager/data/model/Student.kt
 *
 * UPDATED: Added [avatarUrl] field to carry the profile image URL
 * from profiles.avatar_url through to the UI layer.
 */
@Serializable
data class Student(
    val id            : String                  = "",
    val name          : String                  = "",
    val cls           : String                  = "",
    val whatsapp      : String                  = "",
    val status        : String                  = "approved",
    val joinedAt      : String                  = "",
    val avatarUrl     : String?                 = null,          // ✅ NEW
    val fees          : Map<String, FeeMonth>   = emptyMap(),
    val advanceBalance: AdvanceBalance          = AdvanceBalance()
)
