package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.*
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.ui.student.StudentListViewModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewStudentsActivity — Migrated to Supabase (Postgres).
 * Teacher views all students with search, filter, and delete using relational enrollments.
 */
class ViewStudentsActivity : BaseActivity() {

    private val viewModel: StudentListViewModel by viewModels()

    private lateinit var container : LinearLayout
    private lateinit var search    : EditText
    private lateinit var btnAll    : Button
    private lateinit var btnClass  : Button
    private lateinit var btnPending: Button
    private lateinit var tvCount   : TextView
    private lateinit var studentScroll: ScrollView

    private var teacherAcademyName = ""
    private lateinit var teacherId: String
    
    private var isLoadingMore = false
    private var isInitialLoad = true
    private var lastSize      = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_students)

        container  = findViewById(R.id.listViewStudents)
        search     = findViewById(R.id.searchBox)
        btnAll     = findViewById(R.id.btnAll)
        btnClass   = findViewById(R.id.btnClass)
        btnPending = findViewById(R.id.btnPending)
        tvCount    = findViewById(R.id.tvStudentCount)
        studentScroll = findViewById(R.id.studentScroll)

        teacherId = SessionManager.getTeacherId(this) ?: run {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_LONG.also { finish() }).show(); return
        }

        // ── Observe student list ──────────────────────────────────────────────
        viewModel.students.observe(this) { result ->
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
                is FmResult.Error -> toast("Failed to load students: ${result.message}")
            }
        }
        viewModel.loadStudentsPaginated(teacherId)

        // Load academy name for WhatsApp reminder from Supabase
        lifecycleScope.launch {
            try {
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select { filter { eq("id", teacherId) } }
                    .decodeSingleOrNull<TeacherNameRow>()
                
                teacherAcademyName = teacher?.academy_name ?: ""
            } catch (e: Exception) {
                // Ignore silent profile load failure
            }
        }

        // ── Search + Filters ──────────────────────────────────────────────────
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
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
            if (q.isEmpty()) toast("Enter class name to filter")
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
            val tv = TextView(this).apply {
                text = "No matching students"
                textSize = 15f; setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 48; layoutParams = lp
            }
            container.addView(tv); return
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
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.ripple_card)
            isClickable = true; isFocusable = true
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 0, 0, 8); layoutParams = lp
            setPadding(20, 16, 20, 16)
        }
        card.addView(TextView(this).apply {
            text = display; textSize = 14f; setTextColor(0xFFF1F5F9.toInt()); setLineSpacing(0f, 1.4f)
        })
        card.setOnClickListener {
            AnimUtil.bounce(card)
            card.postDelayed({ startActivity(Intent(this, StudentProfileActivity::class.java).putExtra("studentId", student.id)) }, 90)
        }
        card.setOnLongClickListener { showStudentOptions(student); true }
        return card
    }

    private fun showStudentOptions(student: Student) {
        android.app.AlertDialog.Builder(this)
            .setTitle(student.name)
            .setItems(arrayOf("🗑️ Delete Student")) { _, which ->
                if (which == 0) {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Delete Student")
                        .setMessage("Delete ${student.name}? This will permanently remove their enrollment.")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteStudent(teacherId, student.id)
                            toast("Student record removed")
                        }
                        .setNegativeButton("Cancel", null).show()
                }
            }.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class TeacherNameRow(val academy_name: String)
}
