package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.ui.student.StudentDetailViewModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * StudentProfileActivity — Clean profile view.
 * Shows student details. Financial summary removed (use History/Payment for that).
 */
class StudentProfileActivity : BaseActivity() {

    private val viewModel: StudentDetailViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        val tvName      = findViewById<TextView>(R.id.tvProfileName)
        val tvClass     = runCatching { findViewById<TextView>(R.id.tvProfileClass) }.getOrNull()
        val tvPhone     = runCatching { findViewById<TextView>(R.id.tvProfilePhone) }.getOrNull()
        val tvTotal     = runCatching { findViewById<TextView>(R.id.tvProfileTotal) }.getOrNull()
        val tvPaid      = runCatching { findViewById<TextView>(R.id.tvProfilePaid) }.getOrNull()
        val tvRemain    = runCatching { findViewById<TextView>(R.id.tvProfileRemain) }.getOrNull()
        val cardAdvance = runCatching { findViewById<View>(R.id.cardProfileAdvance) }.getOrNull()
        val tvAdvance   = runCatching { findViewById<TextView>(R.id.tvProfileAdvance) }.getOrNull()
        val btnEdit     = runCatching { findViewById<Button>(R.id.btnEditStudent) }.getOrNull()
        val btnHistory  = runCatching { findViewById<Button>(R.id.btnHistory) }.getOrNull()
        val btnReceipt  = runCatching { findViewById<Button>(R.id.btnReceipt) }.getOrNull()
        val btnWhatsApp = runCatching { findViewById<Button>(R.id.btnWhatsAppReminder) }.getOrNull()
        val btnMsg      = runCatching { findViewById<Button>(R.id.btnMessageStudent) }.getOrNull()

        val studentId = intent.getStringExtra("studentId") ?: run { finish(); return }
        val teacherId = SessionManager.getTeacherId(this) ?: run { finish(); return }

        var academyName = "Academy"

        lifecycleScope.launch {
            try {
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select { filter { eq("id", teacherId) } }
                    .decodeSingleOrNull<TeacherAcademyRow>()
                teacher?.let { academyName = it.academy_name }
            } catch (_: Exception) {}
        }

        viewModel.student.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {}
                is FmResult.Error   -> Toast.makeText(this, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                is FmResult.Success -> {
                    val s = result.content
                    val whatsapp = if (s.whatsapp.isNullOrEmpty()) "Not saved" else s.whatsapp

                    tvName.text = s.name
                    tvClass?.text  = "🏫 Class: ${s.cls.ifEmpty { "Not assigned" }}"
                    tvPhone?.text  = "📱 WhatsApp: $whatsapp"

                    // Financial summary — current month only
                    val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                    var thisMonthTotal = 0; var thisMonthPaid = 0; var totalPending = 0

                    for ((month, fee) in s.fees) {
                        totalPending += maxOf(0, fee.total - fee.paid)
                        if (month == currentMonthKey) {
                            thisMonthTotal = fee.total; thisMonthPaid = fee.paid
                        }
                    }
                    val thisMonthRemain = maxOf(0, thisMonthTotal - thisMonthPaid)

                    tvTotal?.text  = "💰 This Month Fee: ₹$thisMonthTotal"
                    tvPaid?.text   = "✅ Paid: ₹$thisMonthPaid"
                    tvRemain?.text = if (totalPending > 0) "❌ Total Pending: ₹$totalPending" else "✅ No pending fees!"
                    tvRemain?.setTextColor(if (totalPending > 0) 0xFFEF4444.toInt() else 0xFF22C55E.toInt())

                    val advRemain = s.advanceBalance.remaining
                    if (advRemain > 0) {
                        cardAdvance?.visibility = View.VISIBLE
                        tvAdvance?.text = "💳 Advance Balance: ₹$advRemain"
                    } else {
                        cardAdvance?.visibility = View.GONE
                    }

                    btnEdit?.setOnClickListener {
                        startActivity(Intent(this, EditStudentActivity::class.java).putExtra("studentId", studentId))
                    }
                    btnHistory?.setOnClickListener {
                        startActivity(Intent(this, HistoryActivity::class.java)
                            .putExtra("studentId", studentId).putExtra("teacherId", teacherId))
                    }
                    btnReceipt?.setOnClickListener {
                        startActivity(Intent(this, ReceiptActivity::class.java).apply {
                            putExtra("name", s.name)
                            putExtra("amount", thisMonthPaid.toString())
                            putExtra("total", thisMonthTotal.toString())
                            putExtra("paid", thisMonthPaid.toString())
                            putExtra("remain", thisMonthRemain.toString())
                            putExtra("mode", "Summary")
                            putExtra("className", s.cls)
                            putExtra("academyName", academyName)
                        })
                    }
                    btnWhatsApp?.setOnClickListener {
                        if (whatsapp == "Not saved") {
                            Toast.makeText(this, "No WhatsApp number ❌", Toast.LENGTH_SHORT).show()
                        } else {
                            WhatsAppHelper.sendFeeReminder(this, s.name, whatsapp, totalPending,
                                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()), academyName)
                        }
                    }
                    btnMsg?.setOnClickListener {
                        startActivity(Intent(this, MessageActivity::class.java).apply {
                            putExtra("mode", "teacher")
                            putExtra("teacherId", teacherId)
                            putExtra("studentId", studentId)
                            putExtra("studentName", s.name)
                        })
                    }
                }
            }
        }
        viewModel.loadStudent(teacherId, studentId)
    }

    @kotlinx.serialization.Serializable
    private data class TeacherAcademyRow(val academy_name: String)
}
