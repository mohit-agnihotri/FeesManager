package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.ui.dashboard.DashboardActivity
import com.example.feesmanager.R
import com.example.feesmanager.ui.dashboard.StudentDashboardActivity
import com.example.feesmanager.ui.student.StudentJoinActivity
import com.example.feesmanager.ui.student.StudentPendingApprovalActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.utils.InputValidator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class LoginActivity : BaseActivity() {

    private val auth = SupabaseManager.client.auth

    // ── Views ─────────────────────────────────────────────────────────────────

    private lateinit var tabSignIn: TextView
    private lateinit var tabSignUp: TextView
    private lateinit var labelName: TextView
    private lateinit var etName: EditText
    private lateinit var labelAcademy: TextView
    private lateinit var etAcademy: EditText
    private lateinit var layoutRoleToggle: View
    private lateinit var rbTeacher: RadioButton
    private lateinit var rbStudent: RadioButton
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnAction: Button
    private lateinit var btnGoogle: Button
    private lateinit var progressBar: ProgressBar

    private var isSignInMode = true

    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        bindViews()
        setupTabSwitcher()
        setupButtons()
    }

    private fun bindViews() {
        tabSignIn        = findViewById(R.id.tabSignIn)
        tabSignUp        = findViewById(R.id.tabSignUp)
        labelName        = findViewById(R.id.labelName)
        etName           = findViewById(R.id.etName)
        labelAcademy     = findViewById(R.id.labelAcademy)
        etAcademy        = findViewById(R.id.etAcademy)
        layoutRoleToggle = findViewById(R.id.layoutRoleToggle)
        rbTeacher        = findViewById(R.id.rbTeacher)
        rbStudent        = findViewById(R.id.rbStudent)
        etEmail          = findViewById(R.id.etEmail)
        etPassword       = findViewById(R.id.etPassword)
        btnAction        = findViewById(R.id.btnAction)
        btnGoogle        = findViewById(R.id.btnGoogle)
        progressBar      = findViewById(R.id.progressBar)
    }

    // ── Tab Switcher ──────────────────────────────────────────────────────────

    private fun setupTabSwitcher() {
        tabSignIn.setOnClickListener { switchToSignIn() }
        tabSignUp.setOnClickListener { switchToSignUp() }
    }

    private fun switchToSignIn() {
        isSignInMode = true
        tabSignIn.setTextColor(getColor(android.R.color.white))
        tabSignIn.setBackgroundResource(R.drawable.bg_tab_selected)
        tabSignUp.setTextColor(0xFF64748B.toInt())
        tabSignUp.setBackgroundColor(0)

        // Hide sign-up-only fields
        listOf(labelName, etName, labelAcademy, etAcademy, layoutRoleToggle)
            .forEach { it.visibility = View.GONE }

        btnAction.text = "Sign In"
    }

    private fun switchToSignUp() {
        isSignInMode = false
        tabSignUp.setTextColor(getColor(android.R.color.white))
        tabSignUp.setBackgroundResource(R.drawable.bg_tab_selected)
        tabSignIn.setTextColor(0xFF64748B.toInt())
        tabSignIn.setBackgroundColor(0)

        // Show sign-up-only fields
        listOf(labelName, etName, labelAcademy, etAcademy, layoutRoleToggle)
            .forEach { it.visibility = View.VISIBLE }

        // Hide academy field initially if student is selected
        updateAcademyFieldVisibility()
        rbTeacher.setOnCheckedChangeListener { _, _ -> updateAcademyFieldVisibility() }
        rbStudent.setOnCheckedChangeListener { _, _ -> updateAcademyFieldVisibility() }

        btnAction.text = "Create Account"
    }

    private fun updateAcademyFieldVisibility() {
        val isTeacher = rbTeacher.isChecked
        labelAcademy.visibility  = if (isTeacher) View.VISIBLE else View.GONE
        etAcademy.visibility     = if (isTeacher) View.VISIBLE else View.GONE
    }

    // ── Buttons ───────────────────────────────────────────────────────────────

    private fun setupButtons() {
        btnAction.setOnClickListener {
            if (isSignInMode) handleSignIn() else handleSignUp()
        }
        btnGoogle.setOnClickListener {
            toast("Google Sign-In coming soon!")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGN IN
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleSignIn() {
        val email = etEmail.text.toString().trim()
        val pass  = etPassword.text.toString().trim()

        if (!InputValidator.isValidEmail(email)) { toast("Enter a valid email"); return }
        if (pass.isEmpty()) { toast("Enter your password"); return }

        setLoading(true)

        lifecycleScope.launch {
            try {
                auth.signInWith(Email) {
                    this.email = email
                    this.password = pass
                }
                val uid = auth.currentUserOrNull()?.id
                if (uid != null) {
                    detectRoleAndRoute(uid)
                } else {
                    toast("Login failed. Try again.")
                    setLoading(false)
                }
            } catch (e: Exception) {
                toast("Invalid email or password ❌")
                setLoading(false)
            }
        }
    }

    /**
     * After sign-in, check the user's role in the `profiles` table, then route appropriately.
     */
    private suspend fun detectRoleAndRoute(uid: String) {
        try {
            // ── 1. Get user profile to determine role ────────────────────────
            val profile = SupabaseManager.client.postgrest.from("profiles")
                .select { filter { eq("id", uid) } }
                .decodeSingleOrNull<ProfileRow>()

            val role = profile?.role ?: "student" // default to student if null

            if (role == "teacher") {
                // ── Teacher Flow ──────────────────────────────
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select { filter { eq("id", uid) } }
                    .decodeSingleOrNull<TeacherRow>()

                if (teacher != null) {
                    val userEmail = auth.currentUserOrNull()?.email ?: ""
                    SessionManager.saveTeacherSession(this, uid, teacher.join_code ?: "", userEmail)
                    startActivity(Intent(this, DashboardActivity::class.java))
                } else {
                    toast("Complete your profile to continue")
                    startActivity(Intent(this, SetupProfileActivity::class.java))
                }
            } else {
                // ── Student Flow ──────────────────────────────
                val enrollments = SupabaseManager.client.postgrest.from("enrollments")
                    .select { filter { eq("student_id", uid) } }
                    .decodeList<EnrollmentRow>()

                SessionManager.setRole(this, "student")
                if (enrollments.isNotEmpty()) {
                    val first = enrollments.first()
                    SessionManager.saveStudentSession(this, first.teacher_id, uid)
                    when (first.status) {
                        "pending"  -> startActivity(
                            Intent(
                                this,
                                StudentPendingApprovalActivity::class.java
                            )
                        )
                        "rejected" -> {
                            toast("Your join request was rejected.")
                            startActivity(Intent(this, StudentJoinActivity::class.java))
                        }
                        else -> startActivity(Intent(this, StudentDashboardActivity::class.java))
                    }
                } else {
                    startActivity(Intent(this, StudentJoinActivity::class.java))
                }
            }
            finish()

        } catch (e: Exception) {
            toast("Network error: ${e.message}")
            setLoading(false)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SIGN UP
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleSignUp() {
        val email  = etEmail.text.toString().trim()
        val pass   = etPassword.text.toString().trim()
        val name   = etName.text.toString().trim()
        val academy = etAcademy.text.toString().trim()
        val isTeacher = rbTeacher.isChecked

        if (name.isEmpty()) { toast("Enter your name"); return }
        if (isTeacher && academy.isEmpty()) { toast("Enter your academy name"); return }
        if (!InputValidator.isValidEmail(email)) { toast("Enter a valid email"); return }
        if (!InputValidator.isValidPassword(pass)) { toast("Password must be at least 6 characters"); return }

        setLoading(true)

        lifecycleScope.launch {
            try {
                // Try sign-up; if account already exists, sign-in instead
                var uid: String? = null
                try {
                    auth.signUpWith(Email) {
                        this.email = email
                        this.password = pass
                    }
                    uid = auth.currentUserOrNull()?.id
                } catch (e: Exception) {
                    if (e.message?.contains("already", ignoreCase = true) == true) {
                        toast("Account exists, signing in...")
                        auth.signInWith(Email) {
                            this.email = email
                            this.password = pass
                        }
                        uid = auth.currentUserOrNull()?.id
                    } else throw e
                }

                if (uid == null) {
                    // Email confirmation required
                    toast("Please check your email to confirm your account, then sign in.")
                    switchToSignIn()
                    setLoading(false)
                    return@launch
                }

                val roleStr = if (isTeacher) "teacher" else "student"

                // ALWAYS update their profile with the chosen role and name
                try {
                    SupabaseManager.client.postgrest.from("profiles").update(mapOf(
                        "full_name" to name,
                        "role" to roleStr
                    )) {
                        filter { eq("id", uid) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (isTeacher) {
                    // Pass name + academy to SetupProfileActivity via intent extras
                    // so it can pre-fill the form and skip re-entry
                    val intent = Intent(this@LoginActivity, SetupProfileActivity::class.java).apply {
                        putExtra("prefill_name",    name)
                        putExtra("prefill_academy", academy)
                        putExtra("prefill_email",   email)
                    }
                    startActivity(intent)
                } else {
                    // Student → join a class (pass name so JoinActivity can pre-fill it)
                    SessionManager.setRole(this@LoginActivity, "student")
                    val intent = Intent(this@LoginActivity, StudentJoinActivity::class.java).apply {
                        putExtra("prefill_name", name)
                    }
                    startActivity(intent)
                }
                finish()

            } catch (e: Exception) {
                toast("Signup error: ${e.message}")
                setLoading(false)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnAction.isEnabled    = !loading
        btnGoogle.isEnabled    = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class ProfileRow(val role: String?)
    @Serializable
    private data class TeacherRow(val id: String, val join_code: String?)
    @Serializable
    private data class EnrollmentRow(val teacher_id: String, val status: String)
}