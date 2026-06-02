package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.AnimUtil.withBounce

class RoleSelectActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SecurePrefs.migrateIfNeeded(this, "app")
        SecurePrefs.migrateIfNeeded(this, "student")

        val savedRole   = SessionManager.getRole(this)
        val supabaseSession = SupabaseManager.client.auth.currentSessionOrNull()

        if (savedRole == "student" && supabaseSession != null) { checkStudentAndProceed(); return }
        if (savedRole == "teacher" && supabaseSession != null && SessionManager.isTeacherLoggedIn(this)) {
            startActivity(Intent(this, DashboardActivity::class.java)); finish(); return
        }

        setContentView(R.layout.activity_role_select)

        val btnTeacher = findViewById<View>(R.id.btnTeacher)
        val btnStudent = findViewById<View>(R.id.btnStudent)

        AnimUtil.slideUp(btnTeacher, 200)
        AnimUtil.slideUp(btnStudent, 320)

        btnTeacher.withBounce {
            // ✅ Fix: Clear any existing student session before switching to teacher
            lifecycleScope.launch {
                SessionManager.logoutStudent(this@RoleSelectActivity)
            }
            SessionManager.setRole(this, "teacher")
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        btnStudent.withBounce {
            // ✅ Fix: Clear any existing teacher session before switching to student
            lifecycleScope.launch {
                SessionManager.logoutTeacher(this@RoleSelectActivity)
            }
            SessionManager.setRole(this, "student")
            if (SupabaseManager.client.auth.currentSessionOrNull() == null) {
                startActivity(Intent(this, StudentLoginActivity::class.java))
            } else {
                checkStudentAndProceed()
            }
            finish()
        }
    }

    private fun checkStudentAndProceed() {
        val teacherId  = SessionManager.getStudentTeacherId(this)
        val studentId  = SessionManager.getStudentId(this)
        val currentUid = SupabaseManager.client.auth.currentUserOrNull()?.id

        if (teacherId == null || studentId == null) {
            startActivity(Intent(this, StudentJoinActivity::class.java)); finish(); return
        }
        if (currentUid != studentId) {
            lifecycleScope.launch {
                SessionManager.logoutStudent(this@RoleSelectActivity)
            }
            startActivity(Intent(this, StudentLoginActivity::class.java)); finish(); return
        }

        lifecycleScope.launch {
            try {
                // Query the `enrollments` table for the student's status
                val response = SupabaseManager.client.postgrest.from("enrollments")
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", teacherId)
                        }
                    }.decodeSingleOrNull<EnrollmentStatus>()

                if (response == null) {
                    Toast.makeText(this@RoleSelectActivity, "Join again", Toast.LENGTH_SHORT).show()
                    SessionManager.logoutStudent(this@RoleSelectActivity)
                    startActivity(Intent(this@RoleSelectActivity, StudentJoinActivity::class.java))
                    finish()
                    return@launch
                }

                when (response.status) {
                    "pending"  -> startActivity(Intent(this@RoleSelectActivity, StudentPendingApprovalActivity::class.java))
                    "rejected" -> {
                        Toast.makeText(this@RoleSelectActivity, "Your join request was rejected.", Toast.LENGTH_LONG).show()
                        SessionManager.logoutStudent(this@RoleSelectActivity)
                        startActivity(Intent(this@RoleSelectActivity, StudentJoinActivity::class.java))
                    }
                    else       -> startActivity(Intent(this@RoleSelectActivity, StudentDashboardActivity::class.java))
                }
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@RoleSelectActivity, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class EnrollmentStatus(val status: String)
}
