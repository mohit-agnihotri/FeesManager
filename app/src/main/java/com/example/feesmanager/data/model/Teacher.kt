package com.example.feesmanager.data.model

/**
 * Teacher profile — stored at:
 *   teachers/{teacherId}/...
 *
 * classFees: Map of "class" → "fee amount as string"
 */
data class Teacher(
    val id: String                      = "",
    val teacherName: String             = "",
    val academyName: String             = "",
    val joinCode: String                = "",
    val upiId: String                   = "",
    val classFees: Map<String, String>  = emptyMap()
)
