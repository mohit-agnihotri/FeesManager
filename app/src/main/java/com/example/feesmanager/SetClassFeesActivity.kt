package com.example.feesmanager

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.ui.fees.ClassFeesViewModel

/**
 * SetClassFeesActivity — Teacher sets per-class monthly fee amounts.
 *
 * MVVM Refactor:
 *   BEFORE: Activity directly called FirebaseDatabase.getInstance() in both
 *           saveFees() and loadOldFees(), with no state management.
 *   AFTER:  Activity observes ClassFeesViewModel LiveData for all states.
 *           All Firebase work is in TeacherRepository via ClassFeesViewModel.
 */
class SetClassFeesActivity : BaseActivity() {

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private val viewModel: ClassFeesViewModel by viewModels()

    private lateinit var btnSave: Button
    private val classMap = HashMap<String, EditText>()
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_set_class_fees)

        // ── Session guard ─────────────────────────────────────────────────────
        teacherId = SessionManager.getTeacherId(this) ?: run {
            toast("Not logged in"); finish(); return
        }

        // ── View binding ──────────────────────────────────────────────────────
        btnSave = findViewById(R.id.btnSaveFees)

        classMap["1"]  = findViewById(R.id.c1)
        classMap["2"]  = findViewById(R.id.c2)
        classMap["3"]  = findViewById(R.id.c3)
        classMap["4"]  = findViewById(R.id.c4)
        classMap["5"]  = findViewById(R.id.c5)
        classMap["6"]  = findViewById(R.id.c6)
        classMap["7"]  = findViewById(R.id.c7)
        classMap["8"]  = findViewById(R.id.c8)
        classMap["9"]  = findViewById(R.id.c9)
        classMap["10"] = findViewById(R.id.c10)
        classMap["11"] = findViewById(R.id.c11)
        classMap["12"] = findViewById(R.id.c12)

        // ── Observe LiveData ──────────────────────────────────────────────────
        observeClassFees()
        observeSaveResult()

        // ── Trigger data load + actions ───────────────────────────────────────
        viewModel.loadClassFees(teacherId)
        btnSave.setOnClickListener { saveClassFees() }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private fun observeClassFees() {
        viewModel.classFees.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> { /* optionally disable inputs */ }
                is FmResult.Success -> {
                    for ((cls, fee) in result.content) {
                        classMap[cls]?.setText(fee)
                    }
                }
                is FmResult.Error -> {
                    toast("Failed to load fees: ${result.message}")
                }
            }
        }
    }

    private fun observeSaveResult() {
        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> btnSave.isEnabled = false
                is FmResult.Success -> {
                    btnSave.isEnabled = true
                    toast("Fees Saved Successfully ✅")
                }
                is FmResult.Error -> {
                    btnSave.isEnabled = true
                    toast("Failed to save fees: ${result.message}")
                }
            }
        }
    }

    // ─── UI Interaction ───────────────────────────────────────────────────────

    private fun saveClassFees() {
        val feesMap = HashMap<String, String>()
        for ((cls, editText) in classMap) {
            val value = editText.text.toString().trim()
            if (value.isNotEmpty()) feesMap[cls] = value
        }
        viewModel.saveClassFees(teacherId, feesMap)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
