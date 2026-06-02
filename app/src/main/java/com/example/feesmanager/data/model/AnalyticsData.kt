package com.example.feesmanager.data.model

/** Aggregated analytics data computed from all student fee records. */
data class AnalyticsData(
    val totalStudents: Int                    = 0,
    val totalCollected: Int                   = 0,
    val totalPending: Int                     = 0,
    val totalAdvanceCredit: Int               = 0,
    val bestMonth: String                     = "",
    val monthlyCollected: Map<String, Int>    = emptyMap(),
    val classCollected: Map<String, Int>      = emptyMap()
)
