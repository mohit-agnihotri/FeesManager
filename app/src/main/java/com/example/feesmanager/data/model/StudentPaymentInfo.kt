package com.example.feesmanager.data.model

data class StudentPaymentInfo(
    val isPaymentEnabled : Boolean = false,
    val academyName      : String  = "",
    val studentName      : String  = "",
    val className        : String  = "",
    val whatsapp         : String  = "",
    val email            : String  = "",
    val totalPending     : Int     = 0,
    @Transient internal val _upiId: String = "" // Kept for fallback, but deprecated
)
