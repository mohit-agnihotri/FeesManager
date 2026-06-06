package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.utils.BiometricHelper
import com.example.feesmanager.R
import com.example.feesmanager.ui.dashboard.StudentDashboardActivity
import com.example.feesmanager.ui.student.StudentJoinActivity
import com.example.feesmanager.ui.student.StudentPendingApprovalActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.utils.InputValidator
import com.example.feesmanager.utils.SecurePrefs
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * StudentLoginActivity — Migrated to Supabase Auth and Relational Postgres.
 */
class StudentLoginActivity : BaseActivity() {

    private val auth = SupabaseManager.client.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_login)

        val emailField    = findViewById<EditText>(R.id.etEmail)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val loginBtn      = findViewById<Button>(R.id.btnLogin)
        val signupTv      = findViewById<TextView>(R.id.tvSignup)
        val googleBtn     = findViewById<Button>(R.id.btnGoogleLogin)

        // Session recovery / Biometric login
        val studentId = SessionManager.getStudentId(this)
        if (studentId != null && BiometricHelper.isAvailable(this) &&
            SecurePrefs.get(this, "student").getBoolean("biometric_enabled", false)) {
            BiometricHelper.authenticate(
                activity = this,
                title = "Student Login",
                subtitle = "Verify to continue",
                onSuccess = {
                    val user = auth.currentUserOrNull()
                    if (user != null) proceedAfterLogin(user.id)
                    else toast("Session expired. Please log in again.")
                },
                onFailed = {},
                onError = {}
            )
        }

        loginBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            val pass  = passwordField.text.toString().trim()

            if (!InputValidator.isValidEmail(email)) {
                toast("Enter valid email"); return@setOnClickListener
            }
            if (pass.isEmpty()) {
                toast("Enter password"); return@setOnClickListener
            }

            loginBtn.isEnabled = false
            lifecycleScope.launch {
                try {
                    auth.signInWith(Email) {
                        this.email = email
                        this.password = pass
                    }
                    val user = auth.currentUserOrNull()
                    if (user != null) {
                        proceedAfterLogin(user.id)
                    } else {
                        toast("Login failed")
                        loginBtn.isEnabled = true
                    }
                } catch (e: Exception) {
                    toast("Invalid email or password ❌")
                    loginBtn.isEnabled = true
                }
            }
        }

        googleBtn.setOnClickListener {
            toast("Google Login is coming soon with Supabase!")
        }

        signupTv.setOnClickListener {
            startActivity(Intent(this, StudentSignupActivity::class.java))
        }
    }

    private fun proceedAfterLogin(uid: String) {
        val savedTeacherId = SessionManager.getStudentTeacherId(this)

        lifecycleScope.launch {
            try {
                // 1. Try to find enrollment with the previously saved teacher
                if (savedTeacherId != null) {
                    val enrollment = SupabaseManager.client.postgrest.from("enrollments")
                        .select {
                            filter {
                                eq("student_id", uid)
                                eq("teacher_id", savedTeacherId)
                            }
                        }.decodeSingleOrNull<EnrollmentResult>()

                    if (enrollment != null) {
                        SessionManager.saveStudentSession(this@StudentLoginActivity, savedTeacherId, uid)
                        routeByStatus(enrollment.status)
                        finish()
                        return@launch
                    }
                }

                // 2. If no saved teacher or enrollment not found, find ANY enrollment for this student
                val enrollments = SupabaseManager.client.postgrest.from("enrollments")
                    .select {
                        filter { eq("student_id", uid) }
                    }.decodeList<EnrollmentResult>()

                if (enrollments.isNotEmpty()) {
                    val first = enrollments.first()
                    SessionManager.saveStudentSession(this@StudentLoginActivity, first.teacher_id, uid)
                    routeByStatus(first.status)
                } else {
                    // 3. No enrollments found — redirect to Join Academy
                    startActivity(
                        Intent(
                            this@StudentLoginActivity,
                            StudentJoinActivity::class.java
                        )
                    )
                }
                finish()
            } catch (e: Exception) {
                toast("Database error: ${e.message}")
                findViewById<Button>(R.id.btnLogin).isEnabled = true
            }
        }
    }

    private fun routeByStatus(status: String) {
        when (status) {
            "pending"  -> startActivity(Intent(this, StudentPendingApprovalActivity::class.java))
            "rejected" -> {
                toast("Your join request was rejected.")
                startActivity(Intent(this, StudentJoinActivity::class.java))
            }
            else       -> startActivity(Intent(this, StudentDashboardActivity::class.java))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class EnrollmentResult(val teacher_id: String, val status: String)
}