package com.example.feesmanager

import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.repository.ChatRepository
import com.example.feesmanager.ui.attendance.AttendanceViewModel
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AttendanceActivity : BaseActivity() {

    private val viewModel: AttendanceViewModel by viewModels()

    private lateinit var listLayout     : LinearLayout
    private lateinit var tvDate         : TextView
    private lateinit var btnPrev        : Button
    private lateinit var btnNext        : Button
    private lateinit var btnSave        : Button
    private lateinit var classSpinner   : Spinner

    private val calendar      = Calendar.getInstance()
    private val sdfDate       = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val sdfKey        = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val attendanceMap = HashMap<String, Boolean>()

    /** enrollmentId -> studentName */
    private var allStudents : Map<String, String> = emptyMap()
    /** class -> list of enrollmentId */
    private var classBuckets: Map<String, List<String>> = emptyMap()

    private var selectedClass = "All"
    private lateinit var teacherId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        listLayout   = findViewById(R.id.attendanceList)
        tvDate       = findViewById(R.id.tvAttendanceDate)
        btnPrev      = findViewById(R.id.btnPrevDay)
        btnNext      = findViewById(R.id.btnNextDay)
        btnSave      = findViewById(R.id.btnSaveAttendance)
        classSpinner = runCatching { findViewById<Spinner>(R.id.spinnerClassFilter) }.getOrElse {
            Spinner(this) // fallback stub
        }

        teacherId = SecurePrefs.get(this, "app").getString("teacherId", null) ?: run { finish(); return }

        // Load classes for spinner
        lifecycleScope.launch {
            val classes = ChatRepository().getTeacherClasses(teacherId)
            val options = listOf("All") + classes
            classSpinner.adapter = ArrayAdapter(this@AttendanceActivity, R.layout.dropdown_item, options)
            classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                    selectedClass = options[pos]
                    buildAttendanceUI()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }

        viewModel.studentNames.observe(this) { result ->
            if (result is FmResult.Success) {
                allStudents = result.content
                // We need class info per enrollment — fetch class buckets
                loadClassBuckets()
            }
        }

        viewModel.attendance.observe(this) { result ->
            if (result is FmResult.Success) {
                attendanceMap.clear()
                for ((id, _) in allStudents) {
                    attendanceMap[id] = result.content[id] ?: true
                }
                buildAttendanceUI()
            }
        }

        viewModel.saveResult.observe(this) { result ->
            when (result) {
                is FmResult.Success -> toast("Attendance Saved ✅")
                is FmResult.Error   -> toast("Save failed: ${result.message}")
                else -> {}
            }
        }

        updateDateDisplay()
        viewModel.loadStudents(teacherId)

        btnPrev.setOnClickListener { calendar.add(Calendar.DAY_OF_MONTH, -1); reloadDay() }
        btnNext.setOnClickListener { calendar.add(Calendar.DAY_OF_MONTH, +1); reloadDay() }
        btnSave.setOnClickListener { viewModel.saveAttendance(teacherId, sdfKey.format(calendar.time), attendanceMap) }
    }

    private fun loadClassBuckets() {
        lifecycleScope.launch {
            try {
                // Fetch enrollments with class info
                val rows = SupabaseManager.client.postgrest.from("enrollments")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw(
                        "id, student_id, teacher_classes(class_name)")) {
                        filter {
                            eq("teacher_id", teacherId)
                            eq("status", "approved")
                            exact("deleted_at", null)
                        }
                    }.decodeList<EnrollmentClassRow>()

                // Map enrollmentId -> className
                val buckets = LinkedHashMap<String, MutableList<String>>()
                rows.forEach { row ->
                    val cls = row.teacher_classes?.class_name ?: "Unknown"
                    buckets.getOrPut(cls) { mutableListOf() }.add(row.id)
                }
                classBuckets = buckets

                // Now load attendance
                viewModel.loadAttendance(teacherId, sdfKey.format(calendar.time))
            } catch (e: Exception) {
                viewModel.loadAttendance(teacherId, sdfKey.format(calendar.time))
            }
        }
    }

    private fun reloadDay() {
        updateDateDisplay()
        viewModel.loadAttendance(teacherId, sdfKey.format(calendar.time))
    }

    private fun updateDateDisplay() { tvDate.text = sdfDate.format(calendar.time) }

    private fun buildAttendanceUI() {
        listLayout.removeAllViews()
        val displayStudents = if (selectedClass == "All") {
            allStudents
        } else {
            val idsForClass = classBuckets[selectedClass] ?: emptyList()
            allStudents.filter { it.key in idsForClass }
        }

        if (displayStudents.isEmpty()) {
            listLayout.addView(TextView(this).apply {
                text = "No students in ${if (selectedClass == "All") "this academy" else "Class $selectedClass"}"
                textSize = 14f; setTextColor(0xFF94A3B8.toInt())
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.topMargin = 24; layoutParams = lp
            })
            return
        }

        for ((id, name) in displayStudents) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setPadding(16, 16, 16, 16)
                setBackgroundResource(R.drawable.bg_card_modern)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = 8; layoutParams = lp
            }
            val tv = TextView(this).apply {
                text = "👤 $name"; textSize = 15f; setTextColor(0xFFF1F5F9.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val toggle = Switch(this).apply {
                isChecked = attendanceMap[id] ?: true
                textOn = "Present ✅"; textOff = "Absent ❌"
                text = if (isChecked) "Present ✅" else "Absent ❌"
                setTextColor(if (isChecked) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())
                setOnCheckedChangeListener { _, checked ->
                    attendanceMap[id] = checked
                    text = if (checked) "Present ✅" else "Absent ❌"
                    setTextColor(if (checked) 0xFF22C55E.toInt() else 0xFFEF4444.toInt())
                }
            }
            row.addView(tv); row.addView(toggle)
            listLayout.addView(row)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @kotlinx.serialization.Serializable
    private data class EnrollmentClassRow(
        val id: String,
        val student_id: String,
        val teacher_classes: ClassNameRow? = null
    )
    @kotlinx.serialization.Serializable
    private data class ClassNameRow(val class_name: String? = null)
}
