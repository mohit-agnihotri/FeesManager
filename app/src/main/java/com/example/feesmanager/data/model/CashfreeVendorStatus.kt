package com.example.feesmanager.data.model

/**
 * CashfreeVendorStatus — Holds the current state of a teacher's
 * Cashfree Easy Split vendor registration and KYC.
 *
 * vendor_status values (from Cashfree API):
 *   "not_started"     — Teacher hasn't set up payments yet
 *   "IN_BENE_CREATION" — Vendor created, bank account being verified
 *   "ACTIVE"          — Vendor active, ready to receive payments
 *   "BLOCKED"         — Vendor blocked
 *
 * kyc_status values:
 *   "not_started" — KYC not submitted
 *   "IN_REVIEW"   — PAN submitted, under review
 *   "VERIFIED"    — KYC approved
 *   "REJECTED"    — KYC rejected
 *
 * settlement_status values:
 *   "pending"                      — No settlement yet
 *   "VENDOR_SETTLEMENT_INITIATED"  — Settlement initiated
 *   "VENDOR_SETTLEMENT_SUCCESS"    — Settlement successful
 *   "VENDOR_SETTLEMENT_REVERSED"   — Settlement reversed
 */
data class CashfreeVendorStatus(
    val cashfreeVendorId:  String = "",
    val vendorStatus:      String = "not_started",
    val kycStatus:         String = "not_started",
    val settlementStatus:  String = "pending",
    val settlementUtr:     String = "",
    val bankAccountName:   String = "",
    val bankAccountNumber: String = "",
    val bankIfsc:          String = ""
) {
    val isActive: Boolean     get() = vendorStatus == "ACTIVE"
    val isKycVerified: Boolean get() = kycStatus == "VERIFIED"
    val isSetupComplete: Boolean get() = isActive && isKycVerified
    val isNotStarted: Boolean get() = vendorStatus == "not_started"

    /** Human-readable vendor status for UI display */
    fun vendorStatusDisplay(): String = when (vendorStatus) {
        "not_started"      -> "Not Set Up"
        "IN_BENE_CREATION" -> "Verifying Bank..."
        "ACTIVE"           -> "✅ Active"
        "BLOCKED"          -> "⛔ Blocked"
        else               -> vendorStatus
    }

    /** Human-readable KYC status for UI display */
    fun kycStatusDisplay(): String = when (kycStatus) {
        "not_started" -> "Not Submitted"
        "IN_REVIEW"   -> "🔄 Under Review"
        "VERIFIED"    -> "✅ Verified"
        "REJECTED"    -> "❌ Rejected"
        else          -> kycStatus
    }

    /** Human-readable settlement status for UI display */
    fun settlementStatusDisplay(): String = when (settlementStatus) {
        "pending"                     -> "Pending"
        "VENDOR_SETTLEMENT_INITIATED" -> "🔄 Initiated"
        "VENDOR_SETTLEMENT_SUCCESS"   -> "✅ Settled"
        "VENDOR_SETTLEMENT_REVERSED"  -> "⚠️ Reversed"
        else                          -> settlementStatus
    }
}
