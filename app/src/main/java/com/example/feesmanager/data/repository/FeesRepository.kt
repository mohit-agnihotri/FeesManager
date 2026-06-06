package com.example.feesmanager.data.repository

import android.util.Log
import com.example.feesmanager.ui.payment.AppPaymentConfig
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.model.FeeMonth
import com.example.feesmanager.data.model.PaymentEntry
import com.example.feesmanager.data.model.PaymentSummary
import com.example.feesmanager.data.model.StudentPaymentInfo
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * FeesRepository — Aligned with deployed Supabase SQL schema.
 *
 * Key schema:
 * - fee_records: enrollment_id, month_key, total_amount, paid_amount, status
 * - payments: enrollment_id, student_id, teacher_id, amount, payment_mode, transaction_id, month_key
 * - enrollments: student_id, teacher_id, class_id, advance_balance, whatsapp_number
 */
class FeesRepository {

    private val db         = SupabaseManager.client.postgrest
    private val httpClient = HttpClient()

    // ─── Helper: Get enrollment ID ───────────────────────────────────────────

    private suspend fun getEnrollmentId(studentId: String, teacherId: String): String? {
        return db.from("enrollments")
            .select(Columns.raw("id")) {
                filter {
                    eq("student_id", studentId)
                    eq("teacher_id", teacherId)
                }
            }.decodeSingleOrNull<IdRow>()?.id
    }

    // ─── Payment Recording ────────────────────────────────────────────────────

    suspend fun recordPayment(
        teacherId: String,
        studentId: String,
        studentName: String,
        payAmount: Int,
        paymentMode: String,
        transactionId: String? = null,
        onResult: (FmResult<PaymentSummary>) -> Unit
    ) {
        try {
            val enrollmentId = getEnrollmentId(studentId, teacherId)
                ?: run { onResult(FmResult.Error("Enrollment not found")); return }

            val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

            // 1. Insert payment record
            db.from("payments").insert(PaymentInsert(
                enrollment_id = enrollmentId,
                student_id = studentId,
                teacher_id = teacherId,
                amount = payAmount.toDouble(),
                payment_mode = paymentMode,
                transaction_id = transactionId,
                month_key = currentMonth
            ))

            // 2. Get current month's fee record
            val feeRecord = db.from("fee_records")
                .select {
                    filter {
                        eq("enrollment_id", enrollmentId)
                        eq("month_key", currentMonth)
                    }
                }.decodeSingleOrNull<FeeRecordRow>()

            if (feeRecord == null) {
                onResult(FmResult.Error("No fee record found for current month."))
                return
            }

            // 3. Calculate new amounts
            val currentPaid = feeRecord.paid_amount.toInt()
            val totalDue = feeRecord.total_amount.toInt()
            val newTotal = currentPaid + payAmount
            val regularPayment = minOf(newTotal, totalDue)
            val excess = maxOf(0, newTotal - totalDue)

            // 4. Update fee record
            val newStatus = when {
                regularPayment >= totalDue -> "paid"
                regularPayment > 0 -> "partial"
                else -> "pending"
            }

            db.from("fee_records")
                .update(FeeRecordUpdate(
                    paid_amount = regularPayment.toDouble(),
                    status = newStatus
                )) {
                    filter { eq("id", feeRecord.id) }
                }

            // 5. Update advance balance if excess
            if (excess > 0) {
                val enrollment = db.from("enrollments")
                    .select(Columns.raw("advance_balance")) {
                        filter { eq("id", enrollmentId) }
                    }.decodeSingle<AdvanceRow>()

                val newAdvance = enrollment.advance_balance.toInt() + excess
                db.from("enrollments")
                    .update(AdvanceUpdate(advance_balance = newAdvance.toDouble())) {
                        filter { eq("id", enrollmentId) }
                    }
            }

            // 6. Return summary
            val summary = PaymentSummary(
                studentName = studentName,
                amount = payAmount,
                total = totalDue,
                paid = regularPayment,
                remaining = maxOf(0, totalDue - regularPayment),
                advance = excess,
                mode = paymentMode,
                transactionId = transactionId ?: ""
            )
            onResult(FmResult.Success(summary))
        } catch (e: Exception) {
            onResult(FmResult.Error("Payment failed: ${e.message}", e))
        }
    }

    // ─── Payment History ──────────────────────────────────────────────────────

    suspend fun getPaymentHistory(
        teacherId: String,
        studentId: String,
        onResult: (FmResult<List<FeeMonth>>) -> Unit
    ) {
        try {
            val enrollmentId = getEnrollmentId(studentId, teacherId)
                ?: run { onResult(FmResult.Success(emptyList())); return }

            // 1. Fetch all fee records for this enrollment
            val feeRecords = db.from("fee_records")
                .select {
                    filter { eq("enrollment_id", enrollmentId) }
                    order("month_key", Order.DESCENDING)
                }.decodeList<FeeRecordRow>()

            // 2. Fetch all actual payments from the payments table
            val payments = db.from("payments")
                .select {
                    filter { eq("enrollment_id", enrollmentId) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<PaymentRow>()

            // 3. Group payments by month_key
            val paymentsByMonth = payments.groupBy { it.month_key ?: "unknown" }

            // 4. Build FeeMonth objects with actual payment history
            val months = feeRecords.map { record ->
                val monthPayments = paymentsByMonth[record.month_key] ?: emptyList()
                val historyMap = monthPayments.mapIndexed { index, pay ->
                    val key = pay.id.ifEmpty { "pay_$index" }
                    key to PaymentEntry(
                        id = pay.id,
                        amount = pay.amount.toInt().toString(),
                        date = pay.created_at.take(10),  // "2026-04-25"
                        time = pay.created_at.drop(11).take(5), // "14:30"
                        mode = pay.payment_mode,
                        transactionId = pay.transaction_id ?: "",
                        monthKey = pay.month_key ?: ""
                    )
                }.toMap()

                FeeMonth(
                    monthKey = record.month_key,
                    total = record.total_amount.toInt(),
                    paid = record.paid_amount.toInt(),
                    status = record.status,
                    history = historyMap
                )
            }
            onResult(FmResult.Success(months))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to load history: ${e.message}", e))
        }
    }

    // ─── Student Payment Info ──────────────────────────────────────────────────

    suspend fun getStudentPaymentInfo(
        teacherId: String,
        studentId: String,
        onResult: (FmResult<StudentPaymentInfo>) -> Unit
    ) {
        try {
            val teacher = db.from("teachers")
                .select(Columns.raw("academy_name, upi_id, cashfree_vendor_id, vendor_status")) { filter { eq("id", teacherId) } }
                .decodeSingle<TeacherRow>()

            val enrollment = db.from("enrollments")
                .select(Columns.raw("*, profiles!inner(full_name), teacher_classes(class_name)")) {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }.decodeSingle<EnrollmentWithDetails>()

            // Calculate pending from fee_records
            val enrollmentId = db.from("enrollments")
                .select(Columns.raw("id")) {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }.decodeSingle<IdRow>().id

            val records = db.from("fee_records")
                .select {
                    filter { eq("enrollment_id", enrollmentId) }
                }.decodeList<FeeRecordRow>()

            val grossPending = records.sumOf { maxOf(0.0, it.total_amount - it.paid_amount) }.toInt()
            // Fetch advance balance and subtract
            val advRow = try {
                db.from("enrollments").select(Columns.raw("advance_balance")) {
                    filter { eq("id", enrollmentId) }
                }.decodeSingle<AdvanceRow>()
            } catch (_: Exception) { AdvanceRow() }
            val netPending = maxOf(0, grossPending - advRow.advance_balance.toInt())

            val isCashfreeActive = teacher.vendor_status == "ACTIVE"

            // Feature flag: check active provider
            val isPaymentEnabled = isCashfreeActive

            onResult(FmResult.Success(StudentPaymentInfo(
                isPaymentEnabled = isPaymentEnabled,
                _upiId = teacher.upi_id ?: "",
                academyName = teacher.academy_name,
                studentName = enrollment.profiles.full_name,
                className = enrollment.teacher_classes?.class_name ?: "",
                whatsapp = enrollment.whatsapp_number ?: "",
                totalPending = netPending
            )))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to fetch info: ${e.message}", e))
        }
    }

    // ─── Monthly Rollover (Server-Side) ───────────────────────────────────────
    //
    // Calls the monthly-rollover Edge Function which:
    //   1. Creates fee_records for the current month for ALL approved students
    //   2. Auto-applies advance_balance to reduce pending dues
    //   3. Deducts used advance from enrollments.advance_balance
    // This is server-side so it always uses the SERVICE_ROLE_KEY (bypasses RLS).

    suspend fun performMonthlyRollover(
        teacherId: String,
        onResult: (FmResult<Int>) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val token   = session?.accessToken ?: run {
                    onResult(FmResult.Error("Not logged in"))
                    return@withContext
                }

                val body = """{"teacher_id": "$teacherId"}"""

                val response = httpClient.post(AppPaymentConfig.FN_MONTHLY_ROLLOVER) {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }

                val text = response.bodyAsText()
                Log.d("FeesRepo", "monthly-rollover response: $text")

                if (response.status.value in 200..299) {
                    // Parse created count from response
                    val created = try {
                        org.json.JSONObject(text).optInt("created", 0)
                    } catch (_: Exception) { 0 }
                    onResult(FmResult.Success(created))
                } else {
                    val errMsg = try {
                        org.json.JSONObject(text).optString("error", "Rollover failed")
                    } catch (_: Exception) { "Rollover failed" }
                    onResult(FmResult.Error(errMsg))
                }
            } catch (e: Exception) {
                Log.e("FeesRepo", "performMonthlyRollover failed", e)
                onResult(FmResult.Error("Rollover failed: ${e.message}", e))
            }
        }
    }

    // ─── Decoding Models ──────────────────────────────────────────────────────

    @Serializable
    private data class IdRow(val id: String)

    @Serializable
    private data class PaymentInsert(
        val enrollment_id: String,
        val student_id: String,
        val teacher_id: String,
        val amount: Double,
        val payment_mode: String,
        val transaction_id: String? = null,
        val month_key: String? = null
    )

    @Serializable
    private data class FeeRecordRow(
        val id: String,
        val month_key: String = "",
        val total_amount: Double = 0.0,
        val paid_amount: Double = 0.0,
        val status: String = "pending"
    ) {
        fun toFeeMonth() = FeeMonth(
            monthKey = month_key,
            total = total_amount.toInt(),
            paid = paid_amount.toInt(),
            status = status
        )
    }

    @Serializable
    private data class TeacherRow(
        val academy_name: String,
        val upi_id: String? = null,
        // Cashfree fields (nullable — columns added by migration v4)
        val cashfree_vendor_id: String? = null,
        val vendor_status: String? = null
    )

    @Serializable
    private data class ProfileForInfo(val full_name: String)

    @Serializable
    private data class ClassForInfo(val class_name: String? = null)

    @Serializable
    private data class EnrollmentWithDetails(
        val whatsapp_number: String? = null,
        val profiles: ProfileForInfo,
        val teacher_classes: ClassForInfo? = null
    )

    @Serializable
    private data class AdvanceRow(val advance_balance: Double = 0.0)

    @Serializable
    private data class ClassFeeRow(val fee_amount: Double = 0.0)

    @Serializable
    private data class EnrollmentForRollover(
        val id: String,
        val class_id: String? = null,
        val teacher_classes: ClassFeeRow? = null
    )

    @Serializable
    private data class FeeRecordUpdate(
        val paid_amount: Double,
        val status: String
    )

    @Serializable
    private data class AdvanceUpdate(
        val advance_balance: Double
    )

    @Serializable
    private data class FeeRecordInsert(
        val enrollment_id: String,
        val month_key: String,
        val total_amount: Double,
        val paid_amount: Double,
        val status: String
    )

    /** Row model for the 'payments' table — individual payment entries */
    @Serializable
    private data class PaymentRow(
        val id: String = "",
        val enrollment_id: String = "",
        val amount: Double = 0.0,
        val payment_mode: String = "",
        val transaction_id: String? = null,
        val month_key: String? = null,
        val created_at: String = ""
    )
}
