package com.example.feesmanager.data.model

/**
 * Summary stats for the teacher's main dashboard card.
 * Fetched globally in O(1) from stats/{teacherId} root.
 */
data class DashboardStats(
    val totalStudents: Int      = 0,
    val totalCollectedFees: Int = 0,
    val totalPendingFees: Int   = 0,
    val joinPending: Int        = 0   // students awaiting approval
) {
    val totalFees: Int get() = totalCollectedFees + totalPendingFees
}
