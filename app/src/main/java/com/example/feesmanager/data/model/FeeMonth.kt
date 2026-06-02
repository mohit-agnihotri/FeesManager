package com.example.feesmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FeeMonth(
    val monthKey: String  = "",            // "2026-03" — not stored in Firebase, injected by parser
    val total: Int        = 0,
    val paid: Int         = 0,
    val status: String    = "pending",     // "pending" | "partial" | "paid"
    val baseFee: Int      = 0,
    val advanceApplied: Int = 0,           // standardised field name
    val history: Map<String, PaymentEntry> = emptyMap()
)
