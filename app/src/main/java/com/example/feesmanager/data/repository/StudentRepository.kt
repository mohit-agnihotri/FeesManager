package com.example.feesmanager.data.repository

import android.util.Log
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.model.AdvanceBalance
import com.example.feesmanager.data.model.AdvanceEntry
import com.example.feesmanager.data.model.FeeMonth
import com.example.feesmanager.data.model.Student
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import android.content.Context
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * StudentRepository
 *
 * Path: app/src/main/java/com/example/feesmanager/data/repository/StudentRepository.kt
 *
 * FIX 1: isNull("deleted_at") does not exist in Supabase Kotlin SDK v3 postgrest DSL.
 *         Replaced with: filter("deleted_at", "is", "null")
 *         which maps to the PostgREST IS filter and correctly excludes soft-deleted rows.
 *
 * FIX 2: Added loadPendingStudents() — fetches only status="pending" enrollments.
 *         Previously loadStudentsPaginated() (status="approved") was used for both tabs,
 *         so pending join requests were never visible on the teacher side.
 */
class StudentRepository {

    private val db = SupabaseManager.client.postgrest

    // ── Load APPROVED students (main list + defaulters tab) ───────────────────

    suspend fun loadStudentsPaginated(
        teacherId: String,
        limit: Int,
        onResult: (FmResult<List<Student>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes(class_name, fee_amount)"
                )) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                        // ✅ FIX: isNull() doesn't exist in SDK v3 — use raw filter instead
                        exact("deleted_at", null)
                    }
                    limit(limit.toLong())
                }.decodeList<EnrollmentWithProfile>()

            onResult(FmResult.Success(response.map { it.toStudent() }))
        } catch (e: Exception) {
            Log.e("StudentRepo", "loadStudentsPaginated error", e)
            onResult(FmResult.Error(e.message ?: "Failed to load students", e))
        }
    }

    // ── Load PENDING students (Join Requests tab) ─────────────────────────────

    /**
     * ✅ NEW function: Fetches only pending, non-deleted enrollments.
     * This is the root fix for "student requests not visible on teacher side" —
     * the old code used loadStudentsPaginated() which hard-codes status="approved".
     */
    suspend fun loadPendingStudents(
        teacherId: String,
        onResult: (FmResult<List<Student>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes(class_name, fee_amount)"
                )) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "pending")
                        // ✅ FIX: use raw filter for IS NULL check
                        exact("deleted_at", null)
                    }
                }.decodeList<EnrollmentWithProfile>()

            Log.d("StudentRepo", "loadPendingStudents → ${response.size} found")
            onResult(FmResult.Success(response.map { it.toStudent() }))
        } catch (e: Exception) {
            Log.e("StudentRepo", "loadPendingStudents error", e)
            onResult(FmResult.Error(e.message ?: "Failed to load pending students", e))
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    suspend fun searchStudentsByName(
        teacherId: String,
        nameQuery: String,
        limit: Int,
        onResult: (FmResult<List<Student>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes(class_name, fee_amount)"
                )) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                        ilike("profiles.full_name", "%$nameQuery%")
                        exact("deleted_at", null)
                    }
                    limit(limit.toLong())
                }.decodeList<EnrollmentWithProfile>()

            onResult(FmResult.Success(response.map { it.toStudent() }))
        } catch (e: Exception) {
            onResult(FmResult.Error(e.message ?: "Search failed", e))
        }
    }

    // ── Search by Class ──────────────────────────────────────────────────────

    suspend fun searchStudentsByClass(
        teacherId: String,
        classQuery: String,
        limit: Int,
        onResult: (FmResult<List<Student>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes!inner(class_name, fee_amount)"
                )) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                        ilike("teacher_classes.class_name", "%$classQuery%")
                        exact("deleted_at", null)
                    }
                    limit(limit.toLong())
                }.decodeList<EnrollmentWithProfile>()

            onResult(FmResult.Success(response.map { it.toStudent() }))
        } catch (e: Exception) {
            onResult(FmResult.Error(e.message ?: "Class search failed", e))
        }
    }

    // ── Status update (approve / reject) ─────────────────────────────────────

    suspend fun updateStudentStatus(
        teacherId: String,
        studentId: String,
        status: String,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            db.from("enrollments")
                .update(mapOf("status" to status)) {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }
            Log.d("StudentRepo", "Status updated: student=$studentId → $status")
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            Log.e("StudentRepo", "updateStudentStatus error", e)
            onResult(FmResult.Error("Status update failed: ${e.message}", e))
        }
    }

    // ── Soft delete ───────────────────────────────────────────────────────────

    suspend fun deleteStudent(
        teacherId: String,
        studentId: String,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
            db.from("enrollments")
                .update(mapOf("deleted_at" to now)) {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Delete failed: ${e.message}", e))
        }
    }

        // ── Add student — handled by AddStudentActivity via direct RPC call ───────

    suspend fun addStudent(
        teacherId: String,
        name: String,
        className: String,
        whatsapp: String,
        baseFee: Int,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        onResult(FmResult.Error("Use AddStudentActivity which calls add_student_manually RPC"))
    }

    // ── Single student fetch (with fee data) ──────────────────────────────────

    suspend fun getStudent(
        context: Context,
        teacherId: String,
        studentId: String,
        onResult: (FmResult<Student>) -> Unit
    ) {
        val prefs = context.getSharedPreferences("fm_dashboard_cache", Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("student_${teacherId}_${studentId}", null)
        if (cachedJson != null) {
            try {
                val cachedStudent = Gson().fromJson(cachedJson, Student::class.java)
                onResult(FmResult.Success(cachedStudent))
            } catch (_: Exception) {}
        }
        
        try {
            val response = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes(class_name, fee_amount)"
                )) {
                    filter {
                        eq("student_id", studentId)
                        eq("teacher_id", teacherId)
                    }
                }.decodeSingle<EnrollmentWithProfile>()

            // Fetch fee records for this enrollment to populate fees map
            val feeRecords = db.from("fee_records")
                .select {
                    filter { eq("enrollment_id", response.id) }
                }.decodeList<FeeRecordRow>()

            // Fetch all payments for this enrollment to build advance history
            val payments = try {
                db.from("payments")
                    .select {
                        filter { eq("enrollment_id", response.id) }
                    }.decodeList<PaymentHistoryRow>()
            } catch (_: Exception) { emptyList() }

            val feesMap = feeRecords.associate { record ->
                record.month_key to FeeMonth(
                    monthKey = record.month_key,
                    total = record.total_amount.toInt(),
                    paid = record.paid_amount.toInt(),
                    status = record.status
                )
            }

            // Build advance balance — ONLY count payments where advance_amount > 0
            val advanceRemaining = response.advance_balance.toInt()
            val advancePayments  = payments.filter { it.advance_amount > 0 }  // ← only actual excess payments
            val totalPaid        = advancePayments.sumOf { it.advance_amount.toInt() }
            val advanceHistory   = advancePayments.mapIndexed { index, pay ->
                val key = pay.id.ifEmpty { "pay_$index" }
                key to AdvanceEntry(
                    amount  = pay.advance_amount.toInt(),   // ← show only the advance portion, not full payment
                    date    = pay.created_at.take(10),
                    applied = 0
                )
            }.toMap()

            val advanceBalance = AdvanceBalance(
                remaining = advanceRemaining,
                totalPaid = totalPaid,
                lastUpdated = payments.firstOrNull()?.created_at?.take(10) ?: "",
                history = advanceHistory
            )

            val student = Student(
                id = response.student_id,
                name = response.profiles.full_name,
                cls = response.teacher_classes?.class_name ?: "",
                whatsapp = response.whatsapp_number ?: "",
                status = response.status,
                joinedAt = response.joined_at,
                avatarUrl = response.profiles.avatar_url,
                advanceBalance = advanceBalance,
                fees = feesMap
            )

            prefs.edit().putString("student_${teacherId}_${studentId}", Gson().toJson(student)).apply()

            onResult(FmResult.Success(student))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to fetch student: ${e.message}", e))
        }
    }

    // ── Load approved students WITH fee data (for defaulters tab) ────────────

    suspend fun loadStudentsWithFees(
        teacherId: String,
        onResult: (FmResult<List<Student>>) -> Unit
    ) {
        try {
            val enrollments = db.from("enrollments")
                .select(Columns.raw(
                    "*, profiles!inner(id, full_name, email, avatar_url), teacher_classes(class_name, fee_amount)"
                )) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                        exact("deleted_at", null)
                    }
                }.decodeList<EnrollmentWithProfile>()

            val students = enrollments.map { enrollment ->
                val feeRecords = try {
                    db.from("fee_records")
                        .select {
                            filter { eq("enrollment_id", enrollment.id) }
                        }.decodeList<FeeRecordRow>()
                } catch (_: Exception) { emptyList() }

                val feesMap = feeRecords.associate { record ->
                    record.month_key to FeeMonth(
                        monthKey = record.month_key,
                        total = record.total_amount.toInt(),
                        paid = record.paid_amount.toInt(),
                        status = record.status
                    )
                }
                enrollment.toStudent(feesMap)
            }
            onResult(FmResult.Success(students))
        } catch (e: Exception) {
            Log.e("StudentRepo", "loadStudentsWithFees error", e)
            onResult(FmResult.Error(e.message ?: "Failed to load students with fees", e))
        }
    }

    // ── Row decoding models ───────────────────────────────────────────────────

    @Serializable
    private data class ProfileRow(
        val id: String,
        val full_name: String,
        val email: String? = null,
        val avatar_url: String? = null
    )

    @Serializable
    private data class ClassRow(
        val class_name: String? = null,
        val fee_amount: Double? = null
    )

    @Serializable
    private data class EnrollmentWithProfile(
        val id: String = "",
        val student_id: String,
        val teacher_id: String,
        val status: String = "pending",
        val joined_at: String = "",
        val advance_balance: Double = 0.0,
        val whatsapp_number: String? = null,
        val profiles: ProfileRow,
        val teacher_classes: ClassRow? = null
    ) {
        fun toStudent(fees: Map<String, FeeMonth> = emptyMap()) = Student(
            id             = student_id,
            name           = profiles.full_name,
            cls            = teacher_classes?.class_name ?: "",
            whatsapp       = whatsapp_number ?: "",
            status         = status,
            joinedAt       = joined_at,
            avatarUrl      = profiles.avatar_url,
            advanceBalance = AdvanceBalance(remaining = advance_balance.toInt()),
            fees           = fees
        )
    }

    /** Fee record row for building Student.fees map */
    @Serializable
    private data class FeeRecordRow(
        val month_key: String = "",
        val total_amount: Double = 0.0,
        val paid_amount: Double = 0.0,
        val status: String = "pending"
    )

    /** Payment row for building advance balance history */
    @Serializable
    private data class PaymentHistoryRow(
        val id: String = "",
        val amount: Double = 0.0,
        val advance_amount: Double = 0.0,  // ← only the excess that went to advance (0 = full payment covered fees)
        val payment_mode: String = "",
        val created_at: String = ""
    )
}
