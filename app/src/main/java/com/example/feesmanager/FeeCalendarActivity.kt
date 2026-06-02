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
 * FeeCalendarActivity — Student views their monthly fee calendar.
 *
 * MVVM Refactor:
 *   BEFORE: Activity had direct Firebase.get() and raw parsing.
 *   AFTER:  Observes PayFeesViewModel.paymentHistory (reusing the same ViewModel).
 */
class FeeCalendarActivity : BaseActivity() {

    private val viewModel: PayFeesViewModel by viewModels()

    private lateinit var listLayout    : LinearLayout
    private lateinit var tvTitle       : TextView
    private lateinit var tvTotalPaid   : TextView
    private lateinit var tvTotalPending: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fee_calendar)

        listLayout     = findViewById(R.id.calendarList)
        tvTitle        = findViewById(R.id.tvCalendarTitle)
        tvTotalPaid    = findViewById(R.id.tvCalTotalPaid)
        tvTotalPending = findViewById(R.id.tvCalTotalPending)

        val pref      = SecurePrefs.get(this, "student")
        val teacherId = pref.getString("teacherId", null) ?: return
        val studentId = pref.getString("studentId", null) ?: return

        // Get student name from prefs for title
        val name = pref.getString("studentName", "Student") ?: "Student"
        tvTitle.text = "📅 Fee Calendar — $name"

        viewModel.paymentHistory.observe(this) { result ->
            if (result is FmResult.Success) renderCalendar(result.content)
        }
        viewModel.loadHistory(teacherId, studentId)
    }

    private fun renderCalendar(months: List<FeeMonth>) {
        listLayout.removeAllViews()
        val sorted = months.filter { it.monthKey.matches(Regex("\\d{4}-\\d{2}")) }
            .sortedByDescending { it.monthKey }
        var totalPaid = 0; var totalPending = 0
        for (m in sorted) {
            totalPaid    += m.paid
            totalPending += maxOf(0, m.total - m.paid)
            listLayout.addView(buildMonthCard(m))
        }
        tvTotalPaid.text    = "Total Paid: ₹$totalPaid"
        tvTotalPending.text = "Total Pending: ₹$totalPending"
    }

    private fun buildMonthCard(m: FeeMonth): LinearLayout {
        val pending   = maxOf(0, m.total - m.paid)
        val emoji     = when (m.status) { "paid" -> "✅"; "advance" -> "💳"; "partial" -> "⚠️"; else -> "🔴" }
        val statusColor = when (m.status) { "paid" -> 0xFF22C55E.toInt(); "advance" -> 0xFF6366F1.toInt(); "partial" -> 0xFFF97316.toInt(); else -> 0xFFEF4444.toInt() }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E293B.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12); layoutParams = lp; setPadding(24, 24, 24, 24)
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(TextView(this).apply {
            text = "$emoji  ${formatMonth(m.monthKey)}"; textSize = 17f; setTextColor(0xFFF1F5F9.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply { text = m.status.uppercase(); textSize = 12f; setTextColor(statusColor); gravity = Gravity.END })
        card.addView(row)

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = m.total; progress = m.paid; progressTintList = android.content.res.ColorStateList.valueOf(statusColor)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 16); lp.topMargin = 10; lp.bottomMargin = 10; layoutParams = lp
        }
        card.addView(progress)

        var details = "Total: ₹${m.total}   Paid: ₹${m.paid}   Pending: ₹$pending"
        if (m.advanceApplied > 0) details += "\n💜 ₹${m.advanceApplied} advance applied this month"
        card.addView(TextView(this).apply { text = details; textSize = 13f; setTextColor(0xFF94A3B8.toInt()) })
        return card
    }

    private fun formatMonth(key: String) = try {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)!!)
    } catch (e: Exception) { key }
}
