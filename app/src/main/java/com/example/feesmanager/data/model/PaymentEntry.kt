package com.example.feesmanager.data.model

import kotlinx.serialization.Serializable

/** A single cash/online payment recorded in a fee month's history. */
@Serializable
data class PaymentEntry(
    val id: String          = "",
    val amount: String      = "0",
    val date: String        = "",
    val time: String        = "",
    val mode: String        = "",
    val transactionId: String = "",
    val monthKey: String    = ""
)
