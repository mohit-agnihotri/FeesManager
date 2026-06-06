package com.example.feesmanager.data.repository

import android.util.Log
import com.example.feesmanager.ui.payment.AppPaymentConfig
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.model.CashfreeVendorStatus
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CashfreeRepository — handles all Cashfree Easy Split interactions.
 *
 * All secret keys stay in Supabase Edge Functions.
 * This class only makes calls via authorized Supabase JWT tokens.
 */
class CashfreeRepository {

    private val db     = SupabaseManager.client.postgrest
    private val client = HttpClient()

    // ─── Vendor Setup ──────────────────────────────────────────────────────────

    /**
     * Creates a Cashfree Easy Split vendor for the teacher.
     * Calls the create-cashfree-vendor Edge Function.
     */
    suspend fun createVendor(
        accountName:   String,
        accountNumber: String,
        ifsc:          String,
        panNumber:     String,
        phone:         String,
        onResult:      (FmResult<CashfreeVendorStatus>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val token   = session?.accessToken ?: throw Exception("Not logged in")
                val email   = session.user?.email ?: ""

                Log.d("TOKEN_TEST", "TOKEN = $token")
                Log.d("TOKEN_TEST", "USER_ID = ${session.user?.id}")
                Log.d("TOKEN_TEST", "EMAIL = $email")

                val body = """
{
    "account_name": "$accountName",
    "account_number": "$accountNumber",
    "ifsc": "${ifsc.uppercase()}",
    "pan_number": "${panNumber.uppercase()}",
    "phone": "$phone",
    "email": "$email"
}
""".trimIndent()

                Log.d("TOKEN_TEST", "REQUEST BODY = $body")
                Log.d("TOKEN_TEST", "Calling create-cashfree-vendor")

                val response = client.post(AppPaymentConfig.FN_CASHFREE_CREATE_VENDOR) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                Log.d("TOKEN_TEST", "HTTP STATUS = ${response.status.value}")

                val responseText = response.bodyAsText()
                Log.d("CashfreeRepo", "createVendor response: $responseText")
                val json = JSONObject(responseText)

                if (response.status.value in 200..299) {
                    val status = CashfreeVendorStatus(
                        cashfreeVendorId  = json.optString("vendor_id", ""),
                        vendorStatus      = json.optString("vendor_status", "IN_BENE_CREATION"),
                        kycStatus         = json.optString("kyc_status",     "IN_REVIEW"),
                        bankAccountName   = accountName,
                        bankAccountNumber = accountNumber,
                        bankIfsc          = ifsc
                    )
                    onResult(FmResult.Success(status))
                } else {
                    val errMsg = json.optString("error", "Vendor creation failed")
                    onResult(FmResult.Error(errMsg))
                }

            } catch (e: Exception) {
                Log.e("CashfreeRepo", "createVendor failed", e)
                onResult(FmResult.Error("Failed: ${e.message}"))
            }
        }
    }

    /**
     * Loads current Cashfree vendor status by syncing with Cashfree.
     */
    suspend fun getVendorStatus(
        teacherId: String,
        onResult:  (FmResult<CashfreeVendorStatus>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val token   = session?.accessToken ?: throw Exception("Not logged in")

                Log.d("CashfreeRepo", "Calling sync-cashfree-vendor")
                val response = client.get(AppPaymentConfig.FN_CASHFREE_SYNC_VENDOR) {
                    header("Authorization", "Bearer $token")
                }

                val responseText = response.bodyAsText()
                Log.d("CashfreeRepo", "sync-cashfree-vendor response: $responseText")
                val json = JSONObject(responseText)

                if (response.status.value in 200..299) {
                    val status = CashfreeVendorStatus(
                        cashfreeVendorId  = json.optString("cashfree_vendor_id", ""),
                        vendorStatus      = json.optString("vendor_status", "not_started"),
                        kycStatus         = json.optString("kyc_status", "not_started"),
                        bankAccountName   = json.optString("bank_account_name", ""),
                        bankAccountNumber = json.optString("bank_account_number", ""),
                        bankIfsc          = json.optString("bank_ifsc", "")
                    )
                    onResult(FmResult.Success(status))
                } else {
                    val errMsg = json.optString("error", "Failed to sync status")
                    onResult(FmResult.Error(errMsg))
                }

            } catch (e: Exception) {
                Log.e("CashfreeRepo", "getVendorStatus failed", e)
                onResult(FmResult.Error("Failed to load status: ${e.message}"))
            }
        }
    }

    /**
     * Gets the latest settlement status for a teacher's payments.
     */
    suspend fun getLatestSettlementStatus(
        teacherId: String,
        onResult:  (FmResult<Pair<String, String>>) -> Unit  // Pair(status, utr)
    ) {
        withContext(Dispatchers.IO) {
            try {
                val row = db.from("payment_orders")
                    .select(Columns.raw("settlement_status, settlement_utr")) {
                        filter {
                            eq("teacher_id",       teacherId)
                            eq("payment_provider", "cashfree")
                            eq("status",           "paid")
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        limit(1)
                    }.decodeSingleOrNull<SettlementRow>()

                val status = row?.settlement_status ?: "pending"
                val utr    = row?.settlement_utr    ?: ""
                onResult(FmResult.Success(Pair(status, utr)))

            } catch (e: Exception) {
                onResult(FmResult.Success(Pair("pending", "")))
            }
        }
    }

    // ─── Payment Order Creation ────────────────────────────────────────────────

    /**
     * Creates a Cashfree payment order via Edge Function.
     * Returns CashfreeOrderData with payment_session_id for SDK checkout.
     */
    suspend fun createOrder(
        teacherId:  String,
        studentId:  String,
        amountINR:  Int,
        onResult:   (FmResult<CashfreeOrderData>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                android.util.Log.d(
                    "AUTH_TEST",
                    "SESSION = ${SupabaseManager.client.auth.currentSessionOrNull()}"
                )
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                android.util.Log.d(
                    "AUTH_TEST",
                    "CURRENT USER = ${SupabaseManager.client.auth.currentUserOrNull()}"
                )
                val token   = session?.accessToken ?: throw Exception("Not logged in")
                Log.d("TOKEN_TEST", token)
                Log.d("TOKEN_TEST", "User = ${session.user?.id}")
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                val body = """
                    {
                        "student_id": "$studentId",
                        "teacher_id": "$teacherId",
                        "amount": $amountINR,
                        "month_key": "$currentMonth"
                    }
                """.trimIndent()
                Log.d("TOKEN_TEST", "Calling create-cashfree-vendor")
                val response = client.post(AppPaymentConfig.FN_CASHFREE_CREATE_ORDER) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                val responseText = response.bodyAsText()
                Log.d("CashfreeRepo", "createOrder response: $responseText")
                val json = JSONObject(responseText)

                if (response.status.value in 200..299) {
                    val data = CashfreeOrderData(
                        cashfreeOrderId  = json.getString("cashfree_order_id"),
                        paymentSessionId = json.getString("payment_session_id"),
                        amountInINR      = json.getInt("amount"),
                        academyName      = json.optString("academy_name", "Academy")
                    )
                    onResult(FmResult.Success(data))
                } else {
                    val errMsg = json.optString("error", "Order creation failed")
                    onResult(FmResult.Error(errMsg))
                }

            } catch (e: Exception) {
                Log.e("CashfreeRepo", "createOrder failed", e)
                onResult(FmResult.Error("Failed: ${e.message}"))
            }
        }
    }

    // ─── Payment Verification (called after SDK confirms success) ────────────

    /**
     * Verifies a Cashfree payment by calling the verify-cashfree-payment Edge Function.
     * This is the PRIMARY path for recording payments in the database.
     * The webhook is just a fallback.
     */
    suspend fun verifyPayment(
        cashfreeOrderId: String,
        onResult: (FmResult<String>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val token   = session?.accessToken ?: throw Exception("Not logged in")

                val response = client.post(AppPaymentConfig.FN_CASHFREE_VERIFY_PAYMENT) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""{"cashfree_order_id": "$cashfreeOrderId"}""")
                }

                val responseText = response.bodyAsText()
                Log.d("CashfreeRepo", "verifyPayment response: $responseText")
                val json = JSONObject(responseText)

                if (response.status.value in 200..299) {
                    val status = json.optString("status", "unknown")
                    onResult(FmResult.Success(status))
                } else {
                    val errMsg = json.optString("error", "Verification failed")
                    onResult(FmResult.Error(errMsg))
                }
            } catch (e: Exception) {
                Log.e("CashfreeRepo", "verifyPayment failed", e)
                onResult(FmResult.Error("Verify failed: ${e.message}"))
            }
        }
    }

    // ─── Inner data classes ───────────────────────────────────────────────────

    @Serializable
    private data class TeacherCashfreeRow(
        val cashfree_vendor_id:  String? = null,
        val vendor_status:       String? = null,
        val kyc_status:          String? = null,
        val bank_account_name:   String? = null,
        val bank_account_number: String? = null,
        val bank_ifsc:           String? = null
    )

    @Serializable
    private data class SettlementRow(
        val settlement_status: String? = null,
        val settlement_utr:    String? = null
    )
}

/** Data returned from createOrder() — passed to Cashfree SDK */
data class CashfreeOrderData(
    val cashfreeOrderId:  String,
    val paymentSessionId: String,
    val amountInINR:      Int,
    val academyName:      String
)
