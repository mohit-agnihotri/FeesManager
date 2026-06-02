package com.example.feesmanager.data.model

/** Summary for one student's advance balance — used in AdvanceStudentsActivity list. */
data class AdvanceStudentSummary(
    val studentId: String   = "",
    val name: String        = "",
    val totalPaid: Int      = 0,
    val remaining: Int      = 0,
    val lastUpdated: String = ""
)
