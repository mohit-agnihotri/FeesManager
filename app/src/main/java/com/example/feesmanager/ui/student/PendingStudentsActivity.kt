package com.example.feesmanager.ui.student

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import com.example.feesmanager.ui.chat.MessageActivity
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.utils.SecurePrefs

/**
 * PendingStudentsActivity — Two-tab view: Join Requests + Fee Defaulters.
 *
 * MVVM Refactor:
 *   BEFORE: Activity held a real-time Firebase listener + inline updateStatus().
 *   AFTER:  Observes StudentListViewModel. Status updates via ViewModel.
 */
class PendingStudentsActivity : BaseActivity() {

    private val viewModel: StudentListViewModel by viewModels()

    private lateinit var container     : LinearLayout
    private lateinit var spinnerClass  : Spinner
    private lateinit var spinnerSort   : Spinner
    private lateinit var tabRequests   : TextView
    private lateinit var tabDefaulters : TextView
    private lateinit var layoutFilters : View
    private lateinit var tvHeaderTitle : TextView
    private lateinit var layoutTabs    : LinearLayout

    private var pendingStudents   = listOf<Student>()
    private var defaultersList    = listOf<Student>()
    private var currentTab        = "requests"
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pending_students)

        container     = findViewById(R.id.listPending)
        spinnerClass  = findViewById(R.id.spinnerClass)
        spinnerSort   = findViewById(R.id.spinnerSort)
        tabRequests   = findViewById(R.id.tabJoinRequests)
        tabDefaulters = findViewById(R.id.tabDefaulters)
        layoutFilters = findViewById(R.id.layoutFilters)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        layoutTabs    = findViewById(R.id.layoutTabs)

        teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: run { finish(); return }

        val mode = intent.getStringExtra("mode") ?: "requests"
        if (mode == "requests") {
            tvHeaderTitle.text = "👥 Join Requests"
            layoutTabs.visibility = View.GONE
            currentTab = "requests"
        } else if (mode == "defaulters") {
            tvHeaderTitle.text = "💰 Fee Defaulters"
            layoutTabs.visibility = View.GONE
            currentTab = "defaulters"
        }

        tabRequests.setOnClickListener   { switchTab("requests") }
        tabDefaulters.setOnClickListener { switchTab("defaulters") }

        // ── Observe specific subsets ──────────────────────────────────────────
        viewModel.pendingRequests.observe(this) { result ->
            if (result is FmResult.Success) {
                pendingStudents = result.content
                val pending = pendingStudents.size
                tabRequests.text = "Join Requests${if (pending > 0) " ($pending)" else ""}"
                if (currentTab == "requests") renderRequests()
            }
        }
        viewModel.defaulters.observe(this) { result ->
            if (result is FmResult.Success) {
                defaultersList = result.content
                val classSet = sortedSetOf("All")
                defaultersList.forEach { classSet.add(it.cls) }

                // Only setup spinners once to avoid resetting selection
                if (spinnerClass.adapter == null) {
                    setupSpinners(classSet.toList())
                }

                val defaults = defaultersList.size
                tabDefaulters.text = "Fee Defaulters${if (defaults > 0) " ($defaults)" else ""}"

                // Only render if it's the current tab!
                if (currentTab == "defaulters") renderCurrentTab()
            }
        }

        // ✅ FIX: Single observer for status updates (prevents memory leak)
        viewModel.statusResult.observe(this) { result ->
            when (result) {
                is FmResult.Success -> toast("✅ Action completed!")
                is FmResult.Error   -> toast("❌ Failed: ${result.message}")
                is FmResult.Loading -> { /* loading */ }
            }
        }

        viewModel.loadPendingRequestsAndDefaulters(teacherId)
        styleTab(currentTab)
        if (currentTab == "defaulters") layoutFilters.visibility = View.VISIBLE
    }

    private fun switchTab(tab: String) {
        currentTab = tab; styleTab(tab)
        layoutFilters.visibility = if (tab == "defaulters") View.VISIBLE else View.GONE
        renderCurrentTab()
    }

    private fun styleTab(active: String) {
        tabRequests.setTextColor(if (active == "requests") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        tabRequests.setBackgroundResource(if (active == "requests") R.drawable.bg_btn_indigo else R.drawable.bg_btn_secondary)
        tabDefaulters.setTextColor(if (active == "defaulters") 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
        tabDefaulters.setBackgroundResource(if (active == "defaulters") R.drawable.bg_btn_indigo else R.drawable.bg_btn_secondary)
    }

    private fun renderCurrentTab() {
        container.removeAllViews()
        if (currentTab == "requests") renderRequests()
        else renderDefaulters(spinnerClass.selectedItem?.toString() ?: "All", spinnerSort.selectedItem?.toString() ?: "High to Low")
    }

    private fun renderRequests() {
        val requests = pendingStudents
        if (requests.isEmpty()) { container.addView(emptyText("🎉 No pending join requests!")); return }
        for (s in requests) container.addView(buildRequestCard(s.id, s.name, s.cls, s.joinedAt))
    }

    private fun buildRequestCard(sid: String, name: String, cls: String, joinedAt: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_modern)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 12); layoutParams = lp
            setPadding(20, 16, 20, 16)
        }
        card.addView(TextView(this).apply { text = "👤 $name"; textSize = 16f; setTextColor(0xFFF1F5F9.toInt()); typeface = Typeface.DEFAULT_BOLD })
        card.addView(TextView(this).apply { text = "Class: $cls"; textSize = 13f; setTextColor(0xFF94A3B8.toInt()); val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.topMargin = 2; layoutParams = lp2 })
        if (joinedAt.isNotEmpty()) card.addView(TextView(this).apply { text = "Requested: $joinedAt"; textSize = 12f; setTextColor(0xFF64748B.toInt()); val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.topMargin = 2; layoutParams = lp2 })

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; val lp2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.topMargin = 12; layoutParams = lp2 }
        btnRow.addView(Button(this).apply {
            text = "💬 Chat"; textSize = 12f; setBackgroundResource(R.drawable.bg_btn_secondary); setTextColor(0xFFF1F5F9.toInt()); stateListAnimator = null
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.marginEnd = 8; layoutParams = lp2
            setOnClickListener { startActivity(
                Intent(
                    this@PendingStudentsActivity,
                    MessageActivity::class.java
                ).putExtra("mode","teacher").putExtra("teacherId",teacherId).putExtra("studentId",sid).putExtra("studentName",name)) }
        })
        btnRow.addView(Button(this).apply {
            text = "✅ Approve"; textSize = 12f; setBackgroundResource(R.drawable.bg_card_green); setTextColor(0xFFFFFFFF.toInt()); stateListAnimator = null
            val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.marginEnd = 8; layoutParams = lp2
            setOnClickListener {
                it.isEnabled = false
                viewModel.updateStudentStatus(teacherId, sid, "approved")
                toast("✅ Approving $name...")
            }
        })
        btnRow.addView(Button(this).apply {
            text = "❌ Reject"; textSize = 12f; setBackgroundResource(R.drawable.bg_card_red); setTextColor(0xFFFFFFFF.toInt()); stateListAnimator = null
            setOnClickListener {
                it.isEnabled = false
                viewModel.updateStudentStatus(teacherId, sid, "rejected")
                toast("❌ Rejecting $name...")
            }
        })
        card.addView(btnRow)
        return card
    }

    private fun setupSpinners(classList: List<String>) {
        val classAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, classList)
        classAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClass.adapter = classAdapter
        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                (v as? TextView)?.setTextColor(0xFFFFFFFF.toInt())
                if (currentTab == "defaulters") renderCurrentTab()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        val sorts = listOf("High to Low", "Low to High")
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sorts)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter
        spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                (v as? TextView)?.setTextColor(0xFFFFFFFF.toInt())
                if (currentTab == "defaulters") renderCurrentTab()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun renderDefaulters(selectedClass: String, sortType: String) {
        data class Defaulter(val name: String, val cls: String, val remain: Int)
        var list = defaultersList
            .mapNotNull { s ->
                val remain = s.fees.values.sumOf { maxOf(0, it.total - it.paid) }
                if (remain > 0 && (selectedClass == "All" || s.cls == selectedClass))
                    Defaulter(s.name, s.cls, remain) else null
            }
        list = if (sortType == "High to Low") list.sortedByDescending { it.remain } else list.sortedBy { it.remain }

        if (list.isEmpty()) { container.addView(emptyText("🎉 No pending fees!")); return }
        list.forEach { (name, cls, remain) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.bg_card_modern)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 8); layoutParams = lp
                setPadding(20, 16, 20, 16); gravity = Gravity.CENTER_VERTICAL
            }
            card.addView(TextView(this).apply { text = "👤 $name  (Class $cls)"; textSize = 15f; setTextColor(0xFFF1F5F9.toInt()); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
            card.addView(TextView(this).apply { text = "₹$remain"; textSize = 16f; setTextColor(0xFFEF4444.toInt()); typeface = Typeface.DEFAULT_BOLD })
            container.addView(card)
        }
    }

    private fun emptyText(msg: String) = TextView(this).apply {
        text = msg; textSize = 16f; setTextColor(0xFF22C55E.toInt()); gravity = Gravity.CENTER
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = 40; layoutParams = lp
    }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}