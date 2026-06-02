package com.example.feesmanager

import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.AdvanceStudentSummary
import com.example.feesmanager.ui.student.StudentListViewModel

/**
 * AdvanceStudentsActivity — Teacher views all students with advance credit.
 *
 * MVVM Refactor:
 *   BEFORE: Activity had inline ValueEventListener and raw Firebase parsing.
 *   AFTER:  Observes StudentListViewModel.advanceStudents.
 */
class AdvanceStudentsActivity : BaseActivity() {

    private val viewModel: StudentListViewModel by viewModels()

    private lateinit var container : LinearLayout
    private lateinit var tvSummary : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advance_students)

        container = findViewById(R.id.advanceStudentsList)
        tvSummary = findViewById(R.id.tvAdvanceSummary)

        val teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: run { finish(); return }

        viewModel.advanceStudents.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> { /* optional */ }
                is FmResult.Success -> renderList(result.content)
                is FmResult.Error   -> Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show()
            }
        }
        viewModel.loadAdvanceStudents(teacherId)
    }

    private fun renderList(list: List<AdvanceStudentSummary>) {
        container.removeAllViews()
        if (list.isEmpty()) {
            tvSummary.text = "No advance payments recorded yet"
            container.addView(TextView(this).apply {
                text = "No students have advance balance 😴"
                textSize = 15f; setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 48; layoutParams = lp
            })
            return
        }
        val totalAdvance = list.sumOf { it.remaining }
        tvSummary.text = "${list.size} students • Total advance remaining: ₹$totalAdvance"
        list.forEach { container.addView(buildCard(it)) }
    }

    private fun buildCard(item: AdvanceStudentSummary): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_modern)
            setPadding(20, 16, 20, 16)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10; layoutParams = lp
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        row.addView(TextView(this).apply {
            text = "👤 ${item.name}"; textSize = 16f; setTextColor(0xFFF1F5F9.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            text = "₹${item.remaining}"; textSize = 18f; setTextColor(0xFF6366F1.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        card.addView(row)
        card.addView(TextView(this).apply {
            text = "Total advance paid: ₹${item.totalPaid}  •  Balance: ₹${item.remaining}\nLast updated: ${item.lastUpdated}"
            textSize = 12f; setTextColor(0xFF94A3B8.toInt()); setLineSpacing(0f, 1.4f)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = 4; layoutParams = lp
        })
        card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (item.totalPaid > 0) item.totalPaid else 1; progress = item.remaining
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF6366F1.toInt())
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 8); lp.topMargin = 8; layoutParams = lp
        })
        return card
    }
}
