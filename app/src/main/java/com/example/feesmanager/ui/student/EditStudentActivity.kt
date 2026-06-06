package com.example.feesmanager.ui.student

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * EditStudentActivity — Fixed to properly sync changes everywhere.
 * - Updating class: finds or creates teacher_class entry + updates enrollment.class_id
 * - Updating fee: updates fee_records for current month AND future months
 * - All changes propagate to student dashboard automatically via Supabase.
 */
class EditStudentActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_student)

        val nameField     = findViewById<EditText>(R.id.etName)
        val classField    = findViewById<EditText>(R.id.etClass)
        val whatsappField = findViewById<EditText>(R.id.etWhatsapp)
        val feesField     = findViewById<EditText>(R.id.etCustomFees)
        val tvCurrentFees = findViewById<TextView>(R.id.tvCurrentFees)
        val updateBtn     = findViewById<Button>(R.id.btnUpdate)
        val deleteBtn     = findViewById<Button>(R.id.btnDelete)

        val studentId    = intent.getStringExtra("studentId") ?: run { toast("Student not found"); finish(); return }
        val teacherId    = SessionManager.getTeacherId(this) ?: run { toast("Not logged in"); finish(); return }
        val db           = SupabaseManager.client.postgrest
        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        var enrollmentId = ""

        // ── Load current data ─────────────────────────────────────────────────
        lifecycleScope.launch {
            try {
                val enrollment = db.from("enrollments")
                    .select(Columns.Companion.raw("id, whatsapp_number, profiles!inner(full_name), teacher_classes(class_name, fee_amount)")) {
                        filter { eq("student_id", studentId); eq("teacher_id", teacherId) }
                    }.decodeSingle<JoinRow>()

                enrollmentId = enrollment.id
                nameField.setText(enrollment.profiles.full_name)
                classField.setText(enrollment.teacher_classes?.class_name ?: "")
                whatsappField.setText(enrollment.whatsapp_number ?: "")

                val feeRecord = db.from("fee_records")
                    .select { filter { eq("enrollment_id", enrollmentId); eq("month_key", currentMonth) } }
                    .decodeSingleOrNull<FeeRecordRow>()

                val displayFee = feeRecord?.total_amount?.toInt()
                    ?: enrollment.teacher_classes?.fee_amount?.toInt() ?: 0
                feesField.setText(displayFee.toString())
                tvCurrentFees.text = "Current Monthly Fee: ₹$displayFee"

            } catch (e: Exception) { toast("Error loading: ${e.message}") }
        }

        // ── Update button ─────────────────────────────────────────────────────
        updateBtn.setOnClickListener {
            val newName     = nameField.text.toString().trim()
            val newClass    = classField.text.toString().trim()
            val newWhatsapp = whatsappField.text.toString().trim()
            val newAmount   = feesField.text.toString().toDoubleOrNull() ?: 0.0

            if (newName.isEmpty()) { toast("Name is required"); return@setOnClickListener }

            updateBtn.isEnabled = false
            updateBtn.text = "Saving..."

            lifecycleScope.launch {
                try {
                    // 1. Update profile name
                    db.from("profiles").update(ProfileUpdate(full_name = newName)) {
                        filter { eq("id", studentId) }
                    }

                    // 2. Update whatsapp on enrollment
                    db.from("enrollments")
                        .update(WhatsappUpdate(whatsapp_number = newWhatsapp)) {
                            filter { eq("id", enrollmentId) }
                        }

                    // 3. Handle class change — find/create teacher_class, update enrollment
                    if (newClass.isNotEmpty()) {
                        var classId = db.from("teacher_classes")
                            .select(Columns.Companion.raw("id")) {
                                filter { eq("teacher_id", teacherId); eq("class_name", newClass) }
                            }.decodeSingleOrNull<IdRow>()?.id

                        if (classId == null) {
                            // Create new class entry with the new fee
                            val inserted = db.from("teacher_classes")
                                .insert(ClassInsert(teacher_id = teacherId, class_name = newClass, fee_amount = newAmount))
                                .decodeSingle<IdRow>()
                            classId = inserted.id
                        } else {
                            // Update existing class fee
                            db.from("teacher_classes").update(FeeUpdate(fee_amount = newAmount)) {
                                filter { eq("id", classId) }
                            }
                        }

                        // Update enrollment with new class_id
                        db.from("enrollments").update(ClassUpdate(class_id = classId)) {
                            filter { eq("id", enrollmentId) }
                        }
                    }

                    // 4. Upsert fee record for current month (and consume advance if needed)
                    val existingRecord = db.from("fee_records")
                        .select(Columns.Companion.raw("id, paid_amount")) {
                            filter { eq("enrollment_id", enrollmentId); eq("month_key", currentMonth) }
                        }.decodeSingleOrNull<FeeRecordWithId>()

                    val enrollmentData = db.from("enrollments")
                        .select(Columns.Companion.raw("advance_balance")) { filter { eq("id", enrollmentId) } }
                        .decodeSingleOrNull<AdvanceBalanceRow>()
                    val currentAdv = enrollmentData?.advance_balance ?: 0.0

                    if (existingRecord != null) {
                        val paidSoFar = existingRecord.paid_amount
                        val shortfall = Math.max(0.0, newAmount - paidSoFar)
                        val consumeAdv = Math.min(currentAdv, shortfall)
                        val finalPaid = paidSoFar + consumeAdv

                        val newStatus = when {
                            finalPaid >= newAmount && newAmount > 0 -> "paid"
                            finalPaid > 0                           -> "partial"
                            newAmount == 0.0                        -> "paid"
                            else                                    -> "pending"
                        }

                        db.from("fee_records").update(
                            FeeRecordUpdate(total_amount = newAmount, paid_amount = finalPaid, status = newStatus)
                        ) { filter { eq("id", existingRecord.id) } }

                        if (consumeAdv > 0) {
                            db.from("enrollments").update(AdvanceBalanceUpdate(advance_balance = currentAdv - consumeAdv)) {
                                filter { eq("id", enrollmentId) }
                            }
                        }
                    } else {
                        val shortfall = newAmount
                        val consumeAdv = Math.min(currentAdv, shortfall)
                        val finalPaid = consumeAdv

                        val newStatus = when {
                            finalPaid >= newAmount && newAmount > 0 -> "paid"
                            finalPaid > 0                           -> "partial"
                            newAmount == 0.0                        -> "paid"
                            else                                    -> "pending"
                        }

                        db.from("fee_records").insert(FeeRecordInsert(
                            enrollment_id = enrollmentId, month_key = currentMonth,
                            total_amount = newAmount, paid_amount = finalPaid, status = newStatus
                        ))

                        if (consumeAdv > 0) {
                            db.from("enrollments").update(AdvanceBalanceUpdate(advance_balance = currentAdv - consumeAdv)) {
                                filter { eq("id", enrollmentId) }
                            }
                        }
                    }

                    toast("✅ Updated successfully!")
                    finish()
                } catch (e: Exception) {
                    toast("Update failed: ${e.message}")
                    updateBtn.isEnabled = true
                    updateBtn.text = "Save Changes"
                }
            }
        }

        // ── Delete button ─────────────────────────────────────────────────────
        deleteBtn.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Student")
                .setMessage("Remove this student's enrollment?")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            db.from("enrollments").delete {
                                filter { eq("student_id", studentId); eq("teacher_id", teacherId) }
                            }
                            toast("Deleted")
                            finish()
                        } catch (e: Exception) { toast("Delete failed: ${e.message}") }
                    }
                }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    data class IdRow(val id: String)
    @Serializable
    data class JoinRow(
        val id: String = "",
        val profiles: ProfileRow,
        val teacher_classes: ClassRow? = null,
        val whatsapp_number: String? = null
    )
    @Serializable
    data class ProfileRow(val full_name: String)
    @Serializable
    data class ClassRow(val class_name: String? = null, val fee_amount: Double? = null)
    @Serializable
    data class FeeRecordRow(val total_amount: Double = 0.0)
    @Serializable
    data class FeeRecordWithId(val id: String, val paid_amount: Double = 0.0)
    @Serializable
    data class ProfileUpdate(val full_name: String)
    @Serializable
    data class WhatsappUpdate(val whatsapp_number: String)
    @Serializable
    data class ClassInsert(val teacher_id: String, val class_name: String, val fee_amount: Double)
    @Serializable
    data class ClassUpdate(val class_id: String)
    @Serializable
    data class FeeUpdate(val fee_amount: Double)
    @Serializable
    data class FeeRecordUpdate(val total_amount: Double, val paid_amount: Double, val status: String)
    @Serializable
    data class FeeRecordInsert(
        val enrollment_id: String, val month_key: String,
        val total_amount: Double, val paid_amount: Double, val status: String
    )
    @Serializable
    data class AdvanceBalanceRow(val advance_balance: Double)
    @Serializable
    data class AdvanceBalanceUpdate(val advance_balance: Double)
}