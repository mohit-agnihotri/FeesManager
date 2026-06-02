package com.example.feesmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AdvanceBalance(
    val remaining: Int    = 0,
    val totalPaid: Int    = 0,
    val lastUpdated: String = "",
    val history: Map<String, AdvanceEntry> = emptyMap()
)
