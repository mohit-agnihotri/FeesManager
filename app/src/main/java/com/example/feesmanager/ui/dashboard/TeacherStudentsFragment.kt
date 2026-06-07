package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.feesmanager.R
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.ui.student.StudentListViewModel
import com.example.feesmanager.ui.student.StudentProfileActivity
import com.example.feesmanager.utils.AnimUtil

class TeacherStudentsFragment : Fragment(R.layout.fragment_teacher_students) {

    private val viewModel: StudentListViewModel by lazy {
        ViewModelProvider(requireActivity())[StudentListViewModel::class.java]
    }

    private lateinit var container: LinearLayout
    private lateinit var search: EditText
    private lateinit var btnAll: Button
    private lateinit var btnClass: Button
    private lateinit var btnPending: Button
    private lateinit var tvCount: TextView

    private var teacherId: String = ""

    private var isLoadingMore = false
    private var isInitialLoad = true
    private var lastSize = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val parentActivity = requireActivity() as DashboardActivity
        teacherId = parentActivity.getCurrentTeacherId()

        container = view.findViewById(R.id.listViewStudents)
        search = view.findViewById(R.id.searchBox)
        btnAll = view.findViewById(R.id.btnAll)
        btnClass = view.findViewById(R.id.btnClass)
        btnPending = view.findViewById(R.id.btnPending)
        tvCount = view.findViewById(R.id.tvStudentCount)

        setupObservers()
        setupListeners()

        if (teacherId.isNotEmpty()) {
            viewModel.loadStudentsPaginated(teacherId)
        }
    }

    private fun setupObservers() {
        viewModel.students.observe(viewLifecycleOwner) { result ->
            when (result) {
                is FmResult.Loading -> { /* optional spinner */ }
                is FmResult.Success -> {
                    val students = result.content
                    if (isLoadingMore && students.size == lastSize) isLoadingMore = false

                    tvCount.text = "${students.size} students"
                    renderList(students)

                    lastSize = students.size
                    if (isInitialLoad) isInitialLoad = false
                    else if (isLoadingMore) isLoadingMore = false
                }
                is FmResult.Error -> Toast.makeText(requireContext(), "Failed to load students: ${result.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        val handler = Handler(Looper.getMainLooper())
        var runnable: Runnable? = null
        
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                runnable?.let { handler.removeCallbacks(it) }
                runnable = Runnable {
                    val q = text.toString().trim()
                    isInitialLoad = true
                    if (q.isEmpty()) viewModel.loadStudentsPaginated(teacherId)
                    else viewModel.searchStudentsByName(teacherId, q)
                }
                handler.postDelayed(runnable!!, 500)
            }
        })

        btnAll.setOnClickListener {
            search.text.clear()
            isInitialLoad = true
            viewModel.loadStudentsPaginated(teacherId)
        }
        btnClass.setOnClickListener {
            val q = search.text.toString().trim()
            if (q.isEmpty()) Toast.makeText(requireContext(), "Enter class name in search to filter", Toast.LENGTH_SHORT).show()
            else {
                isInitialLoad = true
                viewModel.searchStudentsByClass(teacherId, q)
            }
        }
        btnPending.setOnClickListener {
            search.text.clear()
            isInitialLoad = true
            viewModel.loadPendingRequestsAndDefaulters(teacherId)
        }
    }

    private fun renderList(items: List<Student>) {
        container.removeAllViews()
        if (items.isEmpty()) {
            val tv = TextView(requireContext()).apply {
                text = "No matching students"
                textSize = 15f
                setTextColor(0xFF94A3B8.toInt())
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 48
                layoutParams = lp
            }
            container.addView(tv)
            return
        }
        items.forEach { student ->
            val icon = when(student.status) {
                "pending"  -> "⏳"
                "rejected" -> "❌"
                else       -> "👤"
            }
            val statusLabel = if (student.status == "pending") "  (Pending Approval)" else ""
            val extraInfo = if (student.whatsapp.isNotEmpty()) "📱 ${student.whatsapp}" else student.joinedAt.take(10).ifEmpty { "" }
            val clsDisplay = if (student.cls.isNotEmpty()) "Class ${student.cls}" else "Class N/A"
            val display = "$icon ${student.name} ($clsDisplay)$statusLabel" + if (extraInfo.isNotEmpty()) "\n$extraInfo" else ""
            container.addView(buildStudentCard(student, display))
        }
    }

    private fun buildStudentCard(student: Student, display: String): LinearLayout {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.ripple_card)
            isClickable = true
            isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 16)
            layoutParams = lp
            setPadding(40, 32, 40, 32)
        }
        card.addView(TextView(requireContext()).apply {
            text = display
            textSize = 15f
            setTextColor(0xFFF1F5F9.toInt())
            setLineSpacing(0f, 1.4f)
        })
        card.setOnClickListener {
            AnimUtil.bounce(card)
            card.postDelayed({ startActivity(Intent(requireContext(), StudentProfileActivity::class.java).putExtra("studentId", student.id)) }, 90)
        }
        card.setOnLongClickListener { showStudentOptions(student); true }
        return card
    }

    private fun showStudentOptions(student: Student) {
        AlertDialog.Builder(requireContext())
            .setTitle(student.name)
            .setItems(arrayOf("🗑️ Delete Student")) { _, which ->
                if (which == 0) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Delete Student")
                        .setMessage("Delete ${student.name}? This will permanently remove their enrollment.")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteStudent(teacherId, student.id)
                            Toast.makeText(requireContext(), "Student record removed", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }
}
