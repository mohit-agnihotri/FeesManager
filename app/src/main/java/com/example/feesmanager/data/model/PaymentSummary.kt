package com.example.feesmanager.data.model

/**
 * Summary returned by FeesRepository.recordPayment() to the ViewModel,
 * then used by FeesEntryActivity / PayFeesActivity to populate ReceiptActivity intent extras.
 */
data class PaymentSummary(
    val studentName: String = "",
    val amount: Int         = 0,
    val total: Int          = 0,
    val paid: Int           = 0,
    val remaining: Int      = 0,
    val advance: Int        = 0,
    val mode: String        = "",
    val transactionId: String = ""
)
