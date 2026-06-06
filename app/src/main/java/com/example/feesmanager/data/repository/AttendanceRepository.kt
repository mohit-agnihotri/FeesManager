package com.example.feesmanager.data.repository

import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

/**
 * AttendanceRepository — Aligned with deployed Supabase SQL schema.
 *
 * Schema: attendance(enrollment_id, attendance_date, status TEXT 'present'|'absent'|'late')
 * Uses enrollment_id FK, not student_id/teacher_id.
 */
class AttendanceRepository {

    private val db = SupabaseManager.client.postgrest

    /**
     * Loads student names — returns map of enrollment_id → "name (Class X)"
     */
    suspend fun loadStudents(
        teacherId: String,
        onResult: (FmResult<Map<String, String>>) -> Unit
    ) {
        try {
            val response = db.from("enrollments")
                .select(Columns.raw("id, student_id, profiles!inner(full_name), teacher_classes(class_name)")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                    }
                }.decodeList<EnrollmentWithProfile>()

            // Map by student_id for the activity UI
            val map = response.associate {
                it.student_id to "${it.profiles.full_name} (Class ${it.teacher_classes?.class_name ?: "—"})"
            }
            onResult(FmResult.Success(map))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to load students: ${e.message}", e))
        }
    }

    /**
     * Loads attendance for a specific date.
     * Returns map of student_id → present/absent status.
     */
    suspend fun loadAttendance(
        teacherId: String,
        dateKey: String,
        onResult: (FmResult<Map<String, Boolean>>) -> Unit
    ) {
        try {
            // First get enrollment IDs for this teacher
            val enrollments = db.from("enrollments")
                .select(Columns.raw("id, student_id")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                    }
                }.decodeList<EnrollmentIdWithStudent>()

            val enrollmentMap = enrollments.associate { it.id to it.student_id }

            // Now get attendance records for these enrollments on this date
            val attendanceMap = mutableMapOf<String, Boolean>()
            for (enrollment in enrollments) {
                val record = db.from("attendance")
                    .select {
                        filter {
                            eq("enrollment_id", enrollment.id)
                            eq("attendance_date", dateKey)
                        }
                    }.decodeSingleOrNull<AttendanceRow>()

                if (record != null) {
                    attendanceMap[enrollment.student_id] = (record.status == "present")
                }
            }

            onResult(FmResult.Success(attendanceMap))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to load attendance: ${e.message}", e))
        }
    }

    /** Saves attendance for a specific date using bulk upsert. */
    suspend fun saveAttendance(
        teacherId: String,
        dateKey: String,
        attendanceMap: Map<String, Boolean>,  // student_id → present/absent
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            // Get enrollment IDs for the students
            val enrollments = db.from("enrollments")
                .select(Columns.raw("id, student_id")) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "approved")
                    }
                }.decodeList<EnrollmentIdWithStudent>()

            val studentToEnrollment = enrollments.associate { it.student_id to it.id }

            val rows = attendanceMap.mapNotNull { (studentId, isPresent) ->
                val enrollmentId = studentToEnrollment[studentId] ?: return@mapNotNull null
                mapOf(
                    "enrollment_id" to enrollmentId,
                    "attendance_date" to dateKey,
                    "status" to if (isPresent) "present" else "absent"
                )
            }

            if (rows.isNotEmpty()) {
                db.from("attendance").upsert(rows)
            }
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to save attendance: ${e.message}", e))
        }
    }

    // ─── Row Models ──────────────────────────────────────────────────────────

    @Serializable
    private data class ProfileRow(val full_name: String)

    @Serializable
    private data class ClassRow(val class_name: String? = null)

    @Serializable
    private data class EnrollmentWithProfile(
        val id: String,
        val student_id: String,
        val profiles: ProfileRow,
        val teacher_classes: ClassRow? = null
    )

    @Serializable
    private data class EnrollmentIdWithStudent(
        val id: String,
        val student_id: String
    )

    @Serializable
    private data class AttendanceRow(
        val status: String  // "present", "absent", "late"
    )
}
