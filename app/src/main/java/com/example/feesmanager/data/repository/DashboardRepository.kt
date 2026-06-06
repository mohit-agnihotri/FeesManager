package com.example.feesmanager.data.repository

import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.model.AdvanceStudentSummary
import com.example.feesmanager.data.model.AnalyticsData
import com.example.feesmanager.data.model.DashboardStats
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

/**
 * DashboardRepository — Aligned with deployed Supabase SQL schema.
 * Uses simple select+decode for counts instead of head/count API.
 */
class DashboardRepository {

    private val db = SupabaseManager.client.postgrest

    // ─── Dashboard Stats ──────────────────────────────────────────────────────

    suspend fun getDashboardStats(
        teacherId: String,
        onResult: (FmResult<DashboardStats>) -> Unit
    ) {
        try {
            // 1. All enrollments — single query for both approved count and fee aggregation
            val allEnrollments = db.from("enrollments")
                .select(Columns.raw("id, status")) {
                    filter {
                        eq("teacher_id", teacherId)
                    }
                }.decodeList<EnrollmentStatusRow>()

            val approvedIds = allEnrollments.filter { it.status == "approved" }.map { it.id }
            val studentsCount = approvedIds.size
            val pendingCount = allEnrollments.count { it.status == "pending" }

            // 2. Batch-fetch all fee records for approved enrollments (single query)
            var collected = 0
            var totalDue = 0

            if (approvedIds.isNotEmpty()) {
                val allRecords = db.from("fee_records")
                    .select {
                        filter { isIn("enrollment_id", approvedIds) }
                    }.decodeList<FeeRecordRow>()

                collected = allRecords.sumOf { it.paid_amount.toInt() }
                totalDue = allRecords.sumOf { it.total_amount.toInt() }
            }

            val pending = totalDue - collected

            onResult(FmResult.Success(DashboardStats(
                totalStudents = studentsCount,
                totalCollectedFees = collected,
                totalPendingFees = pending,
                joinPending = pendingCount
            )))
        } catch (e: Exception) {
            onResult(FmResult.Error(e.message ?: "Failed to load dashboard stats", e))
        }
    }

    // ─── Teacher Profile ───────────────────────────────────────────────────────

    suspend fun getTeacherProfile(
        teacherId: String,
        onResult: (FmResult<Triple<String, String, String>>) -> Unit
    ) {
        try {
            val teacher = db.from("teachers")
                .select(Columns.raw("*, profiles!inner(full_name)")) {
                    filter { eq("id", teacherId) }
                }.decodeSingle<TeacherWithProfile>()

            onResult(FmResult.Success(Triple(
                teacher.profiles.full_name,
                teacher.academy_name,
                teacher.join_code ?: ""
            )))
        } catch (e: Exception) {
            onResult(FmResult.Error("Profile load failed: ${e.message}", e))
        }
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    suspend fun loadAnalytics(
        teacherId: String,
        onResult: (FmResult<AnalyticsData>) -> Unit
    ) {
        try {
            // Get all approved enrollment IDs
            val enrollments = db.from("enrollments")
                .select(Columns.raw("id")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                    }
                }.decodeList<IdRow>()

            val enrollmentIds = enrollments.map { it.id }

            // Batch-fetch all fee records in a single query
            val allRecords = if (enrollmentIds.isNotEmpty()) {
                db.from("fee_records")
                    .select {
                        filter { isIn("enrollment_id", enrollmentIds) }
                    }.decodeList<FeeRecordRow>()
            } else {
                emptyList()
            }

            val monthlyMap = allRecords.groupBy { it.month_key }
                .mapValues { (_, entries) -> entries.sumOf { it.paid_amount.toInt() } }

            val totalCollected = allRecords.sumOf { it.paid_amount.toInt() }
            val totalPending = allRecords.sumOf { (it.total_amount - it.paid_amount).toInt() }
            val bestMonth = monthlyMap.maxByOrNull { it.value }?.key ?: "—"

            onResult(FmResult.Success(AnalyticsData(
                totalStudents = enrollments.size,
                totalCollected = totalCollected,
                totalPending = totalPending,
                totalAdvanceCredit = 0,
                bestMonth = bestMonth,
                monthlyCollected = monthlyMap,
                classCollected = emptyMap()
            )))
        } catch (e: Exception) {
            onResult(FmResult.Error("Analytics failed: ${e.message}", e))
        }
    }

    // ─── Advance Students List ────────────────────────────────────────────────

    suspend fun getAdvanceStudents(
        teacherId: String,
        onResult: (FmResult<List<AdvanceStudentSummary>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw("*, profiles!inner(full_name)")) {
                    filter {
                        eq("teacher_id", teacherId)
                        gt("advance_balance", 0)
                        eq("status", "approved")
                    }
                    order("advance_balance", Order.DESCENDING)
                    limit(100)
                }.decodeList<EnrollmentWithProfile>()

            val list = response.map {
                AdvanceStudentSummary(
                    studentId = it.student_id,
                    name = it.profiles.full_name,
                    totalPaid = 0,
                    remaining = it.advance_balance.toInt(),
                    lastUpdated = it.joined_at
                )
            }
            onResult(FmResult.Success(list))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to fetch advance list: ${e.message}", e))
        }
    }

    // ─── Export Data (for BackupViewModel) ─────────────────────────────────────

    suspend fun loadFullExportData(
        teacherId: String,
        onResult: (FmResult<List<Map<String, Any>>>) -> Unit
    ) {
        try {
            val enrollments = db.from("enrollments")
                .select(Columns.raw("*, profiles!inner(full_name, email), teacher_classes(class_name)")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                    }
                }.decodeList<ExportEnrollment>()

            val exportData = enrollments.map { e ->
                mapOf<String, Any>(
                    "student_id" to e.student_id,
                    "name" to e.profiles.full_name,
                    "email" to (e.profiles.email ?: ""),
                    "class" to (e.teacher_classes?.class_name ?: ""),
                    "status" to e.status,
                    "advance_balance" to e.advance_balance.toInt()
                )
            }
            onResult(FmResult.Success(exportData))
        } catch (e: Exception) {
            onResult(FmResult.Error("Export failed: ${e.message}", e))
        }
    }

    // ─── Teacher Inbox (for StudentQueriesViewModel) ──────────────────────────

    suspend fun getTeacherInbox(
        teacherId: String,
        namesMap: Map<String, String>,
        onResult: (FmResult<Map<String, Triple<String, String, Int>>>) -> Unit
    ) {
        try {
            // Since there's no messages table in the schema,
            // return the student list as conversation stubs
            val result = namesMap.mapValues { (_, name) ->
                Triple(name, "No messages yet", 0)
            }
            onResult(FmResult.Success(result))
        } catch (e: Exception) {
            onResult(FmResult.Error("Inbox load failed: ${e.message}", e))
        }
    }

    // ─── Row Models ──────────────────────────────────────────────────────────

    @Serializable
    private data class IdRow(val id: String)

    @Serializable
    private data class EnrollmentStatusRow(val id: String, val status: String)

    @Serializable
    private data class ProfileRow(val full_name: String)

    @Serializable
    private data class TeacherWithProfile(
        val academy_name: String,
        val join_code: String? = null,
        val profiles: ProfileRow
    )

    @Serializable
    private data class FeeRecordRow(
        val month_key: String = "",
        val total_amount: Double = 0.0,
        val paid_amount: Double = 0.0
    )

    @Serializable
    private data class EnrollmentWithProfile(
        val student_id: String,
        val advance_balance: Double = 0.0,
        val joined_at: String = "",
        val profiles: ProfileRow
    )

    @Serializable
    private data class ExportProfileRow(val full_name: String, val email: String? = null)

    @Serializable
    private data class ExportClassRow(val class_name: String? = null)

    @Serializable
    private data class ExportEnrollment(
        val student_id: String,
        val status: String = "",
        val advance_balance: Double = 0.0,
        val profiles: ExportProfileRow,
        val teacher_classes: ExportClassRow? = null
    )
}
