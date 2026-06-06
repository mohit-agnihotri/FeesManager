package com.example.feesmanager.ui.advances

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.student.StudentDetailViewModel
import com.example.feesmanager.utils.SecurePrefs

class AdvancePaymentActivity : BaseActivity() {

    private val viewModel: StudentDetailViewModel by viewModels()
    private lateinit var container   : LinearLayout
    private lateinit var tvRemaining : TextView
    private lateinit var tvTotalPaid : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advance_payment)

        container   = findViewById(R.id.advanceList)
        tvRemaining = findViewById(R.id.tvAdvanceRemaining)
        tvTotalPaid = findViewById(R.id.tvAdvanceTotalPaid)

        val intentTeacher = intent.getStringExtra("teacherId")
        val intentStudent = intent.getStringExtra("studentId")
        val teacherId: String
        val studentId: String

        if (!intentTeacher.isNullOrEmpty() && !intentStudent.isNullOrEmpty()) {
            teacherId = intentTeacher; studentId = intentStudent
        } else {
            val pref  = SecurePrefs.get(this, "student")
            teacherId = pref.getString("teacherId", null) ?: run { finish(); return }
            studentId = pref.getString("studentId", null) ?: run { finish(); return }
        }

        viewModel.student.observe(this) { result ->
            when (result) {
                is FmResult.Success -> renderAdvance(result.content)
                is FmResult.Error   -> Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show()
                is FmResult.Loading -> {}
            }
        }
        viewModel.observeStudent(teacherId, studentId)
    }

    private fun renderAdvance(student: Student) {
        val adv = student.advanceBalance
        tvTotalPaid.text = "₹${adv.totalPaid}"
        tvRemaining.text = "₹${adv.remaining}"
        runCatching { findViewById<TextView>(R.id.tvAdvanceLastUpdated).text = "Last updated: ${adv.lastUpdated}" }

        container.removeAllViews()
        val history = adv.history.values.toList().reversed()

        if (history.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "No advance payments yet"; textSize = 15f; setTextColor(0xFF94A3B8.toInt())
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 32; layoutParams = lp
            }); return
        }

        for (h in history) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_card_modern)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 8; layoutParams = lp; setPadding(20, 16, 20, 16)
            }
            card.addView(TextView(this).apply {
                text = "💳 ₹${h.amount} advance paid on ${h.date}${if (h.applied > 0) "\n✅ ₹${h.applied} applied to fees" else ""}"
                textSize = 14f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.4f)
            })
            container.addView(card)
        }
    }
}