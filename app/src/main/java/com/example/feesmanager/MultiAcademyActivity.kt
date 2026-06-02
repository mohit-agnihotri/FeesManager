package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

/**
 * MultiAcademyActivity — Migrated to Supabase (Postgres).
 * Displays a student's list of joined academies and status summaries.
 */
class MultiAcademyActivity : BaseActivity() {

    private lateinit var listLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_academy)

        listLayout = findViewById(R.id.academyList)

        findViewById<Button>(R.id.btnJoinNewAcademy).setOnClickListener {
            startActivity(Intent(this, StudentJoinActivity::class.java))
        }

        loadMyAcademies()
    }

    override fun onResume() {
        super.onResume()
        // Simple reload on resume to keep data fresh
        loadMyAcademies()
    }

    private fun loadMyAcademies() {
        val studentId = SessionManager.getStudentId(this) ?: run {
            Toast.makeText(this, "Student session lost ❌", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        lifecycleScope.launch {
            try {
                // Fetch enrollments with teacher details joined
                val enrollments = SupabaseManager.client.postgrest.from("enrollments")
                    .select {
                        filter { eq("student_id", studentId) }
                    }.decodeList<EnrollmentWithTeacher>()

                listLayout.removeAllViews()

                if (enrollments.isEmpty()) {
                    showEmptyState()
                    return@launch
                }

                enrollments.forEach { enrollment ->
                    // Fetch teacher/academy details manually or via join (for simplicity here, manual fetch)
                    loadAcademyCard(enrollment, studentId)
                }
            } catch (e: Exception) {
                showEmptyState("Error loading academies: ${e.message}")
            }
        }
    }

    private suspend fun loadAcademyCard(enrollment: EnrollmentWithTeacher, studentId: String) {
        try {
            val teacher = SupabaseManager.client.postgrest.from("teachers")
                .select {
                    filter { eq("id", enrollment.teacher_id) }
                }.decodeSingleOrNull<TeacherData>()

            if (teacher != null) {
                // Fetch teacher profile name and class
                val teacherProfile = try {
                    SupabaseManager.client.postgrest.from("profiles")
                        .select { filter { eq("id", enrollment.teacher_id) } }
                        .decodeSingleOrNull<ProfileName>()
                } catch (_: Exception) { null }
                val teacherName = teacherProfile?.full_name ?: teacher.academy_name

                // Fetch enrollment details (id + class)
                val enrollmentRow = SupabaseManager.client.postgrest.from("enrollments")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("id, teacher_classes(class_name)")) {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", enrollment.teacher_id)
                        }
                    }.decodeSingleOrNull<EnrollmentWithClass>()
                val className = enrollmentRow?.teacher_classes?.class_name ?: ""

                var totalPaid = 0
                var totalPending = 0

                if (enrollmentRow != null) {
                    val fees = SupabaseManager.client.postgrest.from("fee_records")
                        .select {
                            filter { eq("enrollment_id", enrollmentRow?.id ?: "") }
                        }.decodeList<FeeRecord>()

                    fees.forEach {
                        totalPaid += it.paid_amount.toInt()
                        totalPending += maxOf(0, it.total_amount.toInt() - it.paid_amount.toInt())
                    }
                }

                val card = buildCard(
                    academy = teacher.academy_name,
                    teacher = teacherName,
                    className = className,
                    paid = totalPaid,
                    pending = totalPending,
                    teacherId = enrollment.teacher_id,
                    studentId = studentId,
                    status = enrollment.status
                )
                listLayout.addView(card)
            }
        } catch (e: Exception) {
            // Silently skip broken entries
        }
    }

    private fun buildCard(
        academy: String, teacher: String, className: String = "", paid: Int, pending: Int,
        teacherId: String, studentId: String, status: String
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = ContextCompat.getDrawable(context, R.drawable.bg_card_modern)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.setMargins(0, 0, 0, 16) }
            setPadding(32, 32, 32, 32)
        }

        val tvAcademy = TextView(this).apply {
            val primaryId = SessionManager.getStudentTeacherId(this@MultiAcademyActivity)
            text = "🏫 $academy${if (teacherId == primaryId) " (Current)" else ""}"
            textSize = 18f; setTextColor(0xFFF1F5F9.toInt()); setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val tvStatus = TextView(this).apply {
            text = "Status: ${status.replaceFirstChar { it.uppercase() }}"; textSize = 14f
            setTextColor(when(status) {
                "approved" -> 0xFF22C55E.toInt()
                "pending"  -> 0xFFF59E0B.toInt()
                else       -> 0xFFEF4444.toInt()
            })
            setPadding(0, 8, 0, 0)
        }

        val tvFees = TextView(this).apply {
            text = (if (className.isNotEmpty()) "🏫 Class: $className  |  " else "") + "✅ Paid: ₹$paid  |  🔴 Pending: ₹$pending"
            textSize = 14f; setTextColor(0xFF94A3B8.toInt()); setPadding(0, 16, 0, 16)
        }

        val btn = Button(this).apply {
            text = "Switch Academy"; textSize = 13f; setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.bg_button_gradient)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 120)
        }
        
        btn.setOnClickListener {
            SessionManager.saveStudentSession(this@MultiAcademyActivity, teacherId, studentId)
            when (status) {
                "pending"  -> startActivity(Intent(this@MultiAcademyActivity, StudentPendingApprovalActivity::class.java))
                "rejected" -> Toast.makeText(this@MultiAcademyActivity, "❌ Your request was rejected by $academy", Toast.LENGTH_LONG).show()
                else -> {
                    Toast.makeText(this@MultiAcademyActivity, "Switched to $academy ✅", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MultiAcademyActivity, StudentDashboardActivity::class.java))
                    finish()
                }
            }
        }

        card.addView(tvAcademy); card.addView(tvStatus); card.addView(tvFees); card.addView(btn)
        return card
    }

    private fun showEmptyState(msg: String = "No academies joined yet 😴") {
        listLayout.removeAllViews()
        val tv = TextView(this).apply {
            text = msg; textSize = 16f; setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
            setPadding(0, 100, 0, 0)
        }
        listLayout.addView(tv)
    }

    @kotlinx.serialization.Serializable
    private data class EnrollmentWithTeacher(val teacher_id: String, val status: String)

    @kotlinx.serialization.Serializable
    private data class EnrollmentIdRow(val id: String)

    @kotlinx.serialization.Serializable
    private data class EnrollmentWithClass(val id: String, val teacher_classes: ClassRow? = null)
    
    @kotlinx.serialization.Serializable
    private data class ClassRow(val class_name: String? = null)

    @kotlinx.serialization.Serializable
    private data class ProfileName(val full_name: String = "")

    @kotlinx.serialization.Serializable
    private data class TeacherData(val academy_name: String)

    @kotlinx.serialization.Serializable
    private data class FeeRecord(val total_amount: Double = 0.0, val paid_amount: Double = 0.0)
}
