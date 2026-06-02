package com.example.feesmanager

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.ui.dashboard.DashboardViewModel
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import java.text.SimpleDateFormat
import java.util.*

/**
 * AnalyticsActivity — Shows collection stats, bar chart, pie chart.
 */
class AnalyticsActivity : BaseActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    private lateinit var barChart          : BarChart
    private lateinit var pieChart          : PieChart
    private lateinit var tvTotalCollected  : TextView
    private lateinit var tvTotalPending    : TextView
    private lateinit var tvTotalStudents   : TextView
    private lateinit var tvBestMonth       : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analytics)

        barChart         = findViewById(R.id.barChart)
        pieChart         = findViewById(R.id.pieChart)
        tvTotalCollected = findViewById(R.id.tvTotalCollected)
        tvTotalPending   = findViewById(R.id.tvTotalPending)
        tvTotalStudents  = findViewById(R.id.tvTotalStudents)
        tvBestMonth      = findViewById(R.id.tvBestMonth)

        val teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: return

        viewModel.analytics.observe(this) { result ->
            if (result is FmResult.Success) {
                val a = result.content
                tvTotalStudents.text  = "${a.totalStudents}"
                tvTotalCollected.text = "₹${a.totalCollected}"

                if (a.totalAdvanceCredit > 0 && a.totalPending == 0) {
                    tvTotalPending.text      = "₹0 (₹${a.totalAdvanceCredit} credit)"
                    tvTotalPending.setTextColor(Color.parseColor("#22C55E"))
                } else {
                    tvTotalPending.text = "₹${a.totalPending}"
                    tvTotalPending.setTextColor(Color.parseColor("#EF4444"))
                }

                tvBestMonth.text = if (a.bestMonth.isNotEmpty()) formatMonth(a.bestMonth) else "-"
                setupBarChart(TreeMap(a.monthlyCollected))
                setupPieChart(HashMap(a.classCollected))
            }
        }

        viewModel.loadAnalytics(teacherId)
    }

    private fun setupBarChart(monthlyData: TreeMap<String, Int>) {
        val entries = ArrayList<BarEntry>()
        val labels  = ArrayList<String>()
        monthlyData.entries.toList().takeLast(6).forEachIndexed { i, entry ->
            entries.add(BarEntry(i.toFloat(), entry.value.toFloat()))
            labels.add(formatMonthShort(entry.key))
        }
        val dataSet = BarDataSet(entries, "Monthly Collection (₹)")
        dataSet.colors = listOf(Color.parseColor("#6366F1"), Color.parseColor("#22C55E"),
            Color.parseColor("#F97316"), Color.parseColor("#EAB308"),
            Color.parseColor("#06B6D4"), Color.parseColor("#EC4899"))
        dataSet.valueTextColor = Color.WHITE; dataSet.valueTextSize = 12f
        barChart.data = BarData(dataSet)
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.xAxis.position       = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.textColor      = Color.WHITE
        barChart.axisLeft.textColor   = Color.WHITE
        barChart.axisRight.isEnabled  = false
        barChart.legend.textColor     = Color.WHITE
        barChart.description.isEnabled = false
        barChart.setBackgroundColor(Color.parseColor("#1E293B"))
        barChart.animateY(1000); barChart.invalidate()
    }

    private fun setupPieChart(classData: HashMap<String, Int>) {
        val entries = ArrayList<PieEntry>()
        classData.forEach { (cls, amount) -> if (amount > 0) entries.add(PieEntry(amount.toFloat(), "Class $cls")) }
        val dataSet = PieDataSet(entries, "Class-wise Collection")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextColor = Color.WHITE; dataSet.valueTextSize = 12f
        pieChart.data = PieData(dataSet)
        pieChart.description.isEnabled = false; pieChart.legend.textColor = Color.WHITE
        pieChart.setEntryLabelColor(Color.WHITE)
        pieChart.setBackgroundColor(Color.parseColor("#1E293B"))
        pieChart.holeRadius = 40f; pieChart.setHoleColor(Color.parseColor("#1E293B"))
        pieChart.setCenterText("Classes"); pieChart.setCenterTextColor(Color.WHITE)
        pieChart.animateY(1000); pieChart.invalidate()
    }

    private fun formatMonth(key: String) = try { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)!!) } catch (e: Exception) { key }
    private fun formatMonthShort(key: String) = try { SimpleDateFormat("MMM", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(key)!!) } catch (e: Exception) { key }
}
