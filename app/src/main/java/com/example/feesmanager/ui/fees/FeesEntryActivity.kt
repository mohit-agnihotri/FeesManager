package com.example.feesmanager.ui.fees

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.InputValidator
import com.example.feesmanager.utils.SecurePrefs
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * FeesEntryActivity — Record manual (cash/offline) payment.
 * - AutoCompleteTextView with student search
 * - Auto-fetches pending fees when student is selected
 * - Generates receipt after recording
 */
class FeesEntryActivity : BaseActivity() {

    private val viewModel: FeesViewModel by viewModels()

    private lateinit var actvStudent    : AutoCompleteTextView
    private lateinit var modeSpinner    : Spinner
    private lateinit var amountField    : EditText
    private lateinit var recordBtn      : Button
    private lateinit var layoutPending  : View
    private lateinit var tvPendingAmt   : TextView
    private lateinit var btnFillPending : Button

    private val studentList    = ArrayList<Student>()
    private val displayNames   = ArrayList<String>()
    private val studentMap     = LinkedHashMap<String, Student>()  // display -> Student

    private var selectedStudent : Student? = null
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fees_entry)

        teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: run {
            toast("Not logged in"); finish(); return
        }

        actvStudent    = findViewById(R.id.actvStudent)
        modeSpinner    = findViewById(R.id.modeSpinner)
        amountField    = findViewById(R.id.etAmount)
        recordBtn      = findViewById(R.id.btnRecord)
        layoutPending  = findViewById(R.id.layoutPendingHint)
        tvPendingAmt   = findViewById(R.id.tvPendingAmount)
        btnFillPending = findViewById(R.id.btnFillPending)

        modeSpinner.adapter = ArrayAdapter(
            this, R.layout.dropdown_item,
            listOf("Cash 💵", "Online 💳", "UPI 📲", "Cheque 📄")
        )

        // Load all students
        viewModel.students.observe(this) { result ->
            if (result is FmResult.Success) {
                studentList.clear(); displayNames.clear(); studentMap.clear()
                result.content.filter { it.status == "approved" }.forEach { s ->
                    val display = "${s.name} — Class ${s.cls}"
                    studentList.add(s); displayNames.add(display); studentMap[display] = s
                }
                actvStudent.setAdapter(
                    ArrayAdapter(
                        this,
                        android.R.layout.simple_dropdown_item_1line,
                        displayNames
                    )
                )
            }
        }
        viewModel.loadStudents(teacherId)

        // Student selection
        actvStudent.setOnItemClickListener { _, _, position, _ ->
            val display = actvStudent.adapter.getItem(position) as? String ?: return@setOnItemClickListener
            selectedStudent = studentMap[display]
            selectedStudent?.let { fetchPendingFee(it.id) }
        }

        // Also trigger on text change with debounce
        actvStudent.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // If text no longer matches selection, clear pending hint
                if (selectedStudent != null) {
                    val currentText = s.toString()
                    val matchedDisplay = studentMap.keys.firstOrNull { it.contains(currentText, ignoreCase = true) }
                    if (matchedDisplay == null) {
                        selectedStudent = null
                        layoutPending.visibility = View.GONE
                    }
                }
            }
        })

        btnFillPending.setOnClickListener {
            val pending = tvPendingAmt.text.toString().replace("₹", "").trim().toIntOrNull() ?: 0
            if (pending > 0) amountField.setText(pending.toString())
        }

        observePaymentResult()
        recordBtn.setOnClickListener { initiatePayment() }
    }

    private fun fetchPendingFee(studentId: String) {
        lifecycleScope.launch {
            try {
                val res = SupabaseManager.client.postgrest
                    .rpc("get_pending_fee_for_student", mapOf("p_student_id" to studentId))
                    .decodeAs<JsonObject>()
                val success = res["success"]?.jsonPrimitive?.booleanOrNull ?: false
                if (success) {
                    val netPending = res["net_pending"]?.jsonPrimitive?.intOrNull ?: 0
                    layoutPending.visibility = View.VISIBLE
                    tvPendingAmt.text = "₹$netPending"
                    if (amountField.text.isNullOrEmpty()) {
                        amountField.setText(netPending.toString())
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun observePaymentResult() {
        viewModel.paymentResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> recordBtn.isEnabled = false
                is FmResult.Success -> {
                    recordBtn.isEnabled = true
                    val s = result.content
                    amountField.text.clear()
                    layoutPending.visibility = View.GONE
                    selectedStudent = null
                    actvStudent.text.clear()

                    val toastMsg = if (s.advance > 0) "✅ Recorded! ₹${s.advance} saved as advance." else "✅ Payment Recorded!"
                    toast(toastMsg)

                    val academyName = SessionManager.getTeacherName(this) ?: "Academy"
                    val cls = selectedStudent?.cls ?: ""
                    startActivity(Intent(this, ReceiptActivity::class.java).apply {
                        putExtra("name",          s.studentName)
                        putExtra("amount",        s.amount.toString())
                        putExtra("total",         s.total.toString())
                        putExtra("paid",          s.paid.toString())
                        putExtra("remain",        s.remaining.toString())
                        putExtra("mode",          s.mode)
                        putExtra("advance",       s.advance.toString())
                        putExtra("transactionId", s.transactionId)
                        putExtra("academyName",   academyName)
                        putExtra("className",     cls)
                    })
                }
                is FmResult.Error -> {
                    recordBtn.isEnabled = true
                    toast(result.message)
                }
            }
        }
    }

    private fun initiatePayment() {
        val student   = selectedStudent
        val amountStr = amountField.text.toString().trim()

        if (student == null) {
            // Try to find by typed text
            val typed = actvStudent.text.toString().trim()
            val found = studentMap.entries.firstOrNull { it.key.contains(typed, ignoreCase = true) }?.value
            if (found == null) { toast("Select a student from the list"); return }
            selectedStudent = found
        }

        if (!InputValidator.isValidAmount(amountStr)) {
            toast("Enter a valid amount (₹1 – ₹10,00,000)"); return
        }

        val paymentMode = modeSpinner.selectedItem?.toString() ?: "Cash 💵"
        viewModel.recordPayment(teacherId, selectedStudent!!.id, selectedStudent!!.name,
            amountStr.toInt(), paymentMode)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}