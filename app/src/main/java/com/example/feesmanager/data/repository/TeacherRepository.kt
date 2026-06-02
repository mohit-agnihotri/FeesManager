package com.example.feesmanager.data.repository

import android.util.Log
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.model.Teacher
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable

/**
 * TeacherRepository
 *
 * Path: app/src/main/java/com/example/feesmanager/data/repository/TeacherRepository.kt
 *
 * BUG FIX: saveClassFees() was using Map<String, Any> for upsert which
 * crashes with "Serializer for class 'Any' is not found".
 * FIXED by using a @Serializable data class ClassFeeUpsert.
 */
class TeacherRepository {

    private val db = SupabaseManager.client.postgrest

    // ── Profile ───────────────────────────────────────────────────────────────

    suspend fun getTeacher(teacherId: String, onResult: (FmResult<Teacher>) -> Unit) {
        try {
            val response = db.from("teachers")
                .select(Columns.raw("*, profiles!inner(full_name)")) {
                    filter { eq("id", teacherId) }
                }.decodeSingle<TeacherWithProfile>()

            onResult(FmResult.Success(response.toTeacher()))
        } catch (e: Exception) {
            onResult(FmResult.Error("Teacher not found: ${e.message}", e))
        }
    }

    // ── Class Fees ────────────────────────────────────────────────────────────

    suspend fun getClassFees(teacherId: String, onResult: (FmResult<Map<String, String>>) -> Unit) {
        try {
            val response = db.from("teacher_classes")
                .select {
                    filter { eq("teacher_id", teacherId) }
                }.decodeList<ClassFeeRow>()

            val fees = response.associate { it.class_name to it.fee_amount.toInt().toString() }
            onResult(FmResult.Success(fees))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed to load class fees: ${e.message}", e))
        }
    }

    suspend fun fetchClassFeeSync(teacherId: String, className: String): FmResult<Int> {
        return try {
            val response = db.from("teacher_classes")
                .select {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("class_name", className)
                    }
                }.decodeSingleOrNull<ClassFeeRow>()

            FmResult.Success(response?.fee_amount?.toInt() ?: 0)
        } catch (e: Exception) {
            FmResult.Error("Failed to fetch class fee: ${e.message}", e)
        }
    }

    /**
     * ✅ BUG FIX: Previously used Map<String, Any> which crashes at runtime
     * with "Serializer for class 'Any' is not found".
     *
     * Now uses @Serializable data class ClassFeeUpsert so kotlinx.serialization
     * can properly encode every row before sending to Supabase.
     *
     * Also added:
     *   - toDoubleOrNull() guard → skips blank/invalid entries with a log warning
     *   - try-catch with Log.e for better crash diagnostics
     */
    suspend fun saveClassFees(
        teacherId: String,
        feesMap: Map<String, String>,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            // Build a type-safe, serializable list of rows
            val rows = feesMap.mapNotNull { (className, feeStr) ->
                val amount = feeStr.trim().toDoubleOrNull()
                if (amount == null) {
                    Log.w("TeacherRepo", "Skipping class '$className' — invalid fee: '$feeStr'")
                    null
                } else {
                    ClassFeeUpsert(
                        teacher_id = teacherId,
                        class_name = className,
                        fee_amount = amount
                    )
                }
            }

            if (rows.isEmpty()) {
                onResult(FmResult.Error("No valid fees entered"))
                return
            }

            // Upsert: inserts new rows, updates fee_amount on conflict (teacher_id, class_name)
            db.from("teacher_classes").upsert(rows) {
                onConflict = "teacher_id,class_name"
            }
            Log.d("TeacherRepo", "Saved ${rows.size} class fees for teacher $teacherId")
            onResult(FmResult.Success(Unit))

        } catch (e: Exception) {
            Log.e("TeacherRepo", "saveClassFees failed", e)
            onResult(FmResult.Error("Failed to save class fees: ${e.message}", e))
        }
    }

    // ── Serializable Row Models ───────────────────────────────────────────────

    @Serializable
    private data class ProfileRow(val full_name: String)

    @Serializable
    private data class TeacherWithProfile(
        val id: String,
        val academy_name: String,
        val join_code: String? = null,
        val upi_id: String? = null,
        val profiles: ProfileRow
    ) {
        fun toTeacher() = Teacher(
            id          = id,
            teacherName = profiles.full_name,
            academyName = academy_name,
            joinCode    = join_code ?: "",
            upiId       = upi_id ?: "",
            classFees   = emptyMap()
        )
    }

    /** Used for reading class fee rows back from DB */
    @Serializable
    private data class ClassFeeRow(
        val class_name: String,
        val fee_amount: Double = 0.0
    )

    /**
     * ✅ KEY FIX: This @Serializable class replaces Map<String, Any>.
     * kotlinx.serialization can encode this to JSON without reflection errors.
     */
    @Serializable
    private data class ClassFeeUpsert(
        val teacher_id: String,
        val class_name: String,
        val fee_amount: Double
    )
}
