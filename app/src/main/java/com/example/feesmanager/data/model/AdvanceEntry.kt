package com.example.feesmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AdvanceEntry(
    val amount: Int  = 0,
    val date: String = "",
    val applied: Int = 0   // amount applied to a later month's fees
)
