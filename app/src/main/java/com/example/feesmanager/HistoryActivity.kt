package com.example.feesmanager

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.FeeMonth
import com.example.feesmanager.ui.fees.PayFeesViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * HistoryActivity — Migrated to Supabase (Postgres).
 * Shows historical payment records for a student across all months.
 */
class HistoryActivity : BaseActivity() {

    private val viewModel: PayFeesViewModel by viewModels()
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        container = findViewById(R.id.listHistory)

        val intentTeacherId = intent.getStringExtra("teacherId")
        val intentStudentId = intent.getStringExtra("studentId")
        val teacherId: String?
        val studentId: String?

        if (!intentTeacherId.isNullOrEmpty() && !intentStudentId.isNullOrEmpty()) {
            teacherId = intentTeacherId; studentId = intentStudentId
        } else {
            teacherId = SessionManager.getStudentTeacherId(this)
            studentId = SessionManager.getStudentId(this)
        }

        if (teacherId == null || studentId == null) { showEmpty("Student not found"); return }

        viewModel.paymentHistory.observe(this) { result ->
            when (result) {
                is FmResult.Success -> renderHistory(result.content)
                is FmResult.Error   -> showEmpty("Error loading history: ${result.message}")
                is FmResult.Loading -> { /* optional spinner */ }
            }
        }
        viewModel.loadHistory(teacherId, studentId)
    }

    private fun renderHistory(months: List<FeeMonth>) {
        container.removeAllViews()
        val tempList = ArrayList<Triple<Long, String, String>>()

        for (feeMonth in months) {
            for (pay in feeMonth.history.values) {
                // In Supabase, we might have actual ISO timestamps. Assuming existing display logic for now.
                val display = "💰 ₹${pay.amount}  ${pay.mode}\n📅 ${pay.date}  ⏰ ${pay.time}" +
                        if (pay.transactionId.isNotEmpty()) "\nTxn: ${pay.transactionId}" else ""
                
                tempList.add(Triple(0L, feeMonth.monthKey, display))
            }
        }
        
        if (tempList.isEmpty()) { showEmpty("No payment history yet 😴"); return }

        var lastMonth = ""
        for ((_, monthKey, display) in tempList) {
            if (monthKey != lastMonth) { addMonthHeader(formatMonth(monthKey)); lastMonth = monthKey }
            addPaymentCard(display)
        }
    }

    private fun addMonthHeader(month: String) {
        container.addView(TextView(this).apply {
            text = month; textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF818CF8.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 16, 0, 8); layoutParams = lp
        })
    }

    private fun addPaymentCard(text: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_modern)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 8); layoutParams = lp; setPadding(24, 20, 24, 20)
        }
        card.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.4f) })
        container.addView(card)
    }

    private fun showEmpty(msg: String) {
        container.addView(TextView(this).apply {
            text = msg; textSize = 15f; setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 48, 0, 0); layoutParams = lp
        })
    }

    private fun formatMonth(key: String) = try {
        "📅  ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)!!)}"
    } catch (e: Exception) { key }
}
