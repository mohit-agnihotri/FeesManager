package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.ui.backup.BackupViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * BackupActivity — Generates CSV and Text reports from student enrollment data.
 * Fixed to work with List<Map<String, Any>> data format from DashboardRepository.
 */
class BackupActivity : BaseActivity() {

    private val viewModel: BackupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup)

        val btnCsv  = findViewById<Button>(R.id.btnExportCsv)
        val btnText = findViewById<Button>(R.id.btnExportText)

        val teacherId = SessionManager.getTeacherId(this) ?: return
        val academyName = SessionManager.getTeacherName(this) ?: "Academy"

        btnCsv.setOnClickListener {
            btnCsv.isEnabled = false
            toast("Generating CSV…")
            viewModel.loadExportData(teacherId)
        }

        btnText.setOnClickListener {
            btnText.isEnabled = false
            toast("Generating Report…")
            viewModel.loadExportData(teacherId)
        }

        viewModel.exportData.observe(this) { result ->
            btnCsv.isEnabled = true
            btnText.isEnabled = true

            when (result) {
                is FmResult.Loading -> { /* already showing toast */ }
                is FmResult.Error -> toast("Export failed: ${result.message}")
                is FmResult.Success -> {
                    val data = result.content
                    if (data.isEmpty()) {
                        toast("No student data to export")
                        return@observe
                    }

                    // Determine which export was triggered based on which button was last clicked
                    // Since both buttons trigger the same data load, generate both formats
                    val csvStr = buildCsv(data)
                    val textStr = buildTextReport(data, academyName)

                    // Show a chooser
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Export Ready ✅")
                        .setItems(arrayOf("📄 Share as CSV", "📋 Share as Text Report")) { _, which ->
                            if (which == 0) {
                                shareText(csvStr, "FeesManager_Export.csv", "text/csv")
                            } else {
                                shareText(textStr, "FeesManager_Report.txt", "text/plain")
                            }
                        }
                        .show()
                }
            }
        }
    }

    private fun buildCsv(data: List<Map<String, Any>>): String {
        val sb = StringBuilder()
        sb.appendLine("Name,Class,Email,Status,Advance Balance")
        for (student in data) {
            val name = student["name"]?.toString() ?: "Unknown"
            val cls = student["class"]?.toString() ?: "—"
            val email = student["email"]?.toString() ?: ""
            val status = student["status"]?.toString() ?: ""
            val advance = student["advance_balance"]?.toString() ?: "0"
            sb.appendLine("$name,$cls,$email,$status,₹$advance")
        }
        return sb.toString()
    }

    private fun buildTextReport(data: List<Map<String, Any>>, academyName: String): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine("🏫 $academyName")
        sb.appendLine("📅 Generated: ${sdf.format(Date())}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine()

        var count = 0
        for (student in data) {
            count++
            val name = student["name"]?.toString() ?: "Unknown"
            val cls = student["class"]?.toString() ?: "—"
            val email = student["email"]?.toString() ?: ""
            val status = student["status"]?.toString() ?: ""
            val advance = student["advance_balance"]?.toString()?.toIntOrNull() ?: 0

            sb.appendLine("👤 $name  |  Class: $cls")
            if (email.isNotEmpty()) sb.appendLine("   📧 $email")
            sb.appendLine("   Status: $status  |  Advance: ₹$advance")
            sb.appendLine()
        }

        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Total Students: $count")
        sb.appendLine()
        sb.appendLine("— Fees Manager Pro")
        return sb.toString()
    }

    private fun shareText(content: String, filename: String, mimeType: String) {
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, content)
                putExtra(Intent.EXTRA_SUBJECT, filename)
            },
            "Export via"
        ))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
