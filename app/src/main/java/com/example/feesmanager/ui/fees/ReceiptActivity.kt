package com.example.feesmanager.ui.fees

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ReceiptActivity — Generates and displays a payment receipt.
 * Shows proper academy name, teacher name, student details, and transaction info.
 * Supports actual PDF generation, text sharing, and WhatsApp forwarding.
 */
class ReceiptActivity : BaseActivity() {

    private var academyName = ""
    private var teacherName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt)

        val name          = intent.getStringExtra("name") ?: ""
        val amount        = intent.getStringExtra("amount") ?: "0"
        val total         = intent.getStringExtra("total") ?: "0"
        val paid          = intent.getStringExtra("paid") ?: "0"
        val remain        = intent.getStringExtra("remain") ?: "0"
        val mode          = intent.getStringExtra("mode") ?: "Cash"
        val transactionId = intent.getStringExtra("transactionId") ?: ""
        val advance       = intent.getStringExtra("advance") ?: "0"
        val className     = intent.getStringExtra("className") ?: ""
        val intentAcademy = intent.getStringExtra("academyName") ?: ""

        val sdfDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val receiptNo = "RCP-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-${System.currentTimeMillis().toString().takeLast(4)}"

        val tvAcademyName = findViewById<TextView>(R.id.tvAcademyName)
        val tvTeacherName = findViewById<TextView>(R.id.tvTeacherName)

        // Set initial values from intent or session
        academyName = intentAcademy.ifEmpty { SessionManager.getTeacherName(this) ?: "Academy" }
        teacherName = "Official Receipt"

        tvAcademyName.text = academyName
        tvTeacherName.text = teacherName

        // Fetch accurate academy info from Supabase
        val teacherId = SessionManager.getStudentTeacherId(this) ?: SessionManager.getTeacherId(this)

        if (teacherId != null) {
            lifecycleScope.launch {
                try {
                    val teacher = SupabaseManager.client.postgrest.from("teachers")
                        .select {
                            filter { eq("id", teacherId) }
                        }.decodeSingleOrNull<TeacherInfo>()

                    teacher?.let {
                        academyName = it.academy_name
                        tvAcademyName.text = it.academy_name
                    }

                    // Also try to get teacher's actual name
                    val profile = SupabaseManager.client.postgrest.from("profiles")
                        .select {
                            filter { eq("id", teacherId) }
                        }.decodeSingleOrNull<ProfileInfo>()

                    profile?.let {
                        teacherName = "By ${it.full_name}"
                        tvTeacherName.text = teacherName
                    }
                } catch (_: Exception) {
                    // Use defaults from intent/session
                }
            }
        }

        // Student info with class
        val studentInfoText = if (className.isNotEmpty()) {
            "👤 Student: $name  |  🏫 Class: $className"
        } else {
            "👤 Student: $name"
        }
        findViewById<TextView>(R.id.tvStudentName).text = studentInfoText

        // Advance balance
        val tvAdvance = findViewById<TextView>(R.id.tvReceiptAdvance)
        if ((advance.toIntOrNull() ?: 0) > 0) {
            tvAdvance.visibility = View.VISIBLE
            tvAdvance.text = "💜 ₹$advance stored as advance balance"
        } else {
            tvAdvance.visibility = View.GONE
        }

        // Financial details
        findViewById<TextView>(R.id.tvAmountPaid).text  = "₹$amount"
        findViewById<TextView>(R.id.tvTotalPaid).text   = "₹$paid"
        findViewById<TextView>(R.id.tvRemaining).text   = "₹$remain"
        findViewById<TextView>(R.id.tvMode).text        = mode
        findViewById<TextView>(R.id.tvTxnId).text       = if (transactionId.isNotEmpty()) transactionId else "N/A (Offline)"
        findViewById<TextView>(R.id.tvDateTime).text    =
            "📅 ${sdfDate.format(Date())}  🕐 ${sdfTime.format(Date())}  |  Receipt: $receiptNo"

        // Buttons
        findViewById<Button>(R.id.btnSaveReceipt).setOnClickListener {
            savePdf(name, amount, paid, remain, mode, transactionId, className, receiptNo)
        }
        findViewById<Button>(R.id.btnShareReceipt).setOnClickListener {
            shareReceipt(name, amount, paid, remain, mode, transactionId, className, receiptNo)
        }
        findViewById<Button>(R.id.btnWhatsApp).setOnClickListener {
            sendWhatsApp(name, amount, remain, transactionId, className, receiptNo)
        }
    }

    private fun shareReceipt(name: String, amount: String, paid: String, remain: String, mode: String, txnId: String, className: String, receiptNo: String) {
        val text = buildReceiptText(name, amount, paid, remain, mode, txnId, className, receiptNo)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, "Payment Receipt - $name")
        }
        startActivity(Intent.createChooser(share, "Share Receipt"))
    }

    private fun sendWhatsApp(name: String, amount: String, remain: String, txnId: String, className: String, receiptNo: String) {
        val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
        val classInfo = if (className.isNotEmpty()) "\nClass: $className" else ""
        val txnInfo = if (txnId.isNotEmpty()) "\nTransaction ID: $txnId" else ""
        val msg = """
✅ Payment Receipt
━━━━━━━━━━━━━━━━
🏫 $academyName

Student: $name$classInfo
Amount Paid: ₹$amount
Remaining: ₹$remain$txnInfo
Date: ${sdf.format(Date())}
Receipt No: $receiptNo
━━━━━━━━━━━━━━━━
Thank you for your payment! 🙏
— Fees Manager Pro
        """.trimIndent()

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage("com.whatsapp")
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
            }
            startActivity(Intent.createChooser(intent, "Send via"))
        }
    }

    private fun savePdf(name: String, amount: String, paid: String, remain: String, mode: String, txnId: String, className: String, receiptNo: String) {
        try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            drawReceiptOnCanvas(canvas, name, amount, paid, remain, mode, txnId, className, receiptNo)

            document.finishPage(page)

            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Receipts")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Receipt_${name.replace(" ", "_")}_$receiptNo.pdf")
            document.writeTo(FileOutputStream(file))
            document.close()

            Toast.makeText(this, "PDF saved: ${file.name} ✅", Toast.LENGTH_LONG).show()

            // Offer to share the saved PDF
            try {
                val uri = FileProvider.getUriForFile(
                    this, "${packageName}.provider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share PDF Receipt"))
            } catch (_: Exception) {
                // Share plain text as fallback
                shareReceipt(name, amount, paid, remain, mode, txnId, className, receiptNo)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "PDF generation failed: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback to text share
            shareReceipt(name, amount, paid, remain, mode, txnId, className, receiptNo)
        }
    }

    private fun drawReceiptOnCanvas(canvas: Canvas, name: String, amount: String, paid: String, remain: String, mode: String, txnId: String, className: String, receiptNo: String) {
        val sdf = SimpleDateFormat("dd MMM yyyy  hh:mm a", Locale.getDefault())

        val titlePaint = Paint().apply { color = Color.parseColor("#6366F1"); textSize = 24f; isFakeBoldText = true; isAntiAlias = true }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 18f; isFakeBoldText = true; isAntiAlias = true }
        val bodyPaint = Paint().apply { color = Color.DKGRAY; textSize = 14f; isAntiAlias = true }
        val boldPaint = Paint().apply { color = Color.BLACK; textSize = 15f; isFakeBoldText = true; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.LTGRAY; strokeWidth = 1f }

        var y = 60f
        val margin = 40f

        // Academy name
        canvas.drawText(academyName, margin, y, titlePaint); y += 30f
        canvas.drawText(teacherName, margin, y, bodyPaint); y += 30f

        // Divider
        canvas.drawLine(margin, y, 555f, y, linePaint); y += 25f

        // Receipt header
        canvas.drawText("PAYMENT RECEIPT", 220f, y, headerPaint); y += 25f
        canvas.drawText("Receipt No: $receiptNo", margin, y, bodyPaint); y += 20f
        canvas.drawText("Date: ${sdf.format(Date())}", margin, y, bodyPaint); y += 30f

        // Divider
        canvas.drawLine(margin, y, 555f, y, linePaint); y += 25f

        // Student info
        canvas.drawText("Student Name:", margin, y, boldPaint)
        canvas.drawText(name, 200f, y, bodyPaint); y += 22f
        if (className.isNotEmpty()) {
            canvas.drawText("Class:", margin, y, boldPaint)
            canvas.drawText(className, 200f, y, bodyPaint); y += 22f
        }
        y += 10f

        // Payment details
        canvas.drawText("Amount Paid:", margin, y, boldPaint)
        canvas.drawText("₹$amount", 200f, y, boldPaint); y += 22f
        canvas.drawText("Total Paid:", margin, y, boldPaint)
        canvas.drawText("₹$paid", 200f, y, bodyPaint); y += 22f
        canvas.drawText("Remaining:", margin, y, boldPaint)
        canvas.drawText("₹$remain", 200f, y, bodyPaint); y += 22f
        canvas.drawText("Payment Mode:", margin, y, boldPaint)
        canvas.drawText(mode, 200f, y, bodyPaint); y += 22f

        if (txnId.isNotEmpty()) {
            canvas.drawText("Transaction ID:", margin, y, boldPaint)
            canvas.drawText(txnId, 200f, y, bodyPaint); y += 22f
        }
        y += 15f

        // Divider
        canvas.drawLine(margin, y, 555f, y, linePaint); y += 25f

        // Footer
        canvas.drawText("Thank you for your payment!", margin, y, bodyPaint); y += 20f
        canvas.drawText("— Fees Manager Pro", margin, y, bodyPaint)
    }

    private fun buildReceiptText(name: String, amount: String, paid: String, remain: String, mode: String, txnId: String, className: String, receiptNo: String): String {
        val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
        val classInfo = if (className.isNotEmpty()) "\nClass       : $className" else ""
        val txnInfo = if (txnId.isNotEmpty()) "\nTransaction : $txnId" else ""
        return """
PAYMENT RECEIPT
═══════════════════════
🏫 $academyName
$teacherName
───────────────────────
Receipt No  : $receiptNo
Date        : ${sdf.format(Date())}
───────────────────────
Student     : $name$classInfo
───────────────────────
Paid Now    : ₹$amount
Total Paid  : ₹$paid
Remaining   : ₹$remain
Mode        : $mode$txnInfo
═══════════════════════
Thank you for your payment! 🙏
— Fees Manager Pro
        """.trimIndent()
    }

    @Serializable
    private data class TeacherInfo(val academy_name: String)

    @Serializable
    private data class ProfileInfo(val full_name: String)
}