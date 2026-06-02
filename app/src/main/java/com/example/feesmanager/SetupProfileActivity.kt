package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import com.example.feesmanager.AnimUtil.withBounce

/**
 * SetupProfileActivity — Teacher signup & profile setup.
 *
 * Uses server-side RPC function `setup_teacher_profile` to bypass RLS
 * and safely create/update profile + teacher records in one atomic call.
 */
class SetupProfileActivity : BaseActivity() {

    lateinit var academyField: EditText
    lateinit var teacherField: EditText
    lateinit var emailField: EditText
    lateinit var passField: EditText
    lateinit var btnSave: Button

    private val auth = SupabaseManager.client.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_profile)

        academyField     = findViewById(R.id.etAcademy)
        teacherField     = findViewById(R.id.etTeacher)
        emailField       = findViewById(R.id.etEmail)
        passField        = findViewById(R.id.etPassword)
        btnSave          = findViewById(R.id.btnSaveProfile)

        val existingTeacherId = SessionManager.getTeacherId(this)
        if (existingTeacherId != null) loadExistingProfile(existingTeacherId)

        btnSave.setOnClickListener { saveProfile() }
    }

    private fun loadExistingProfile(teacherId: String) {
        lifecycleScope.launch {
            try {
                val profile = SupabaseManager.client.postgrest.from("profiles")
                    .select {
                        filter { eq("id", teacherId) }
                    }.decodeSingleOrNull<FullProfile>()

                val teacherInfo = SupabaseManager.client.postgrest.from("teachers")
                    .select {
                        filter { eq("id", teacherId) }
                    }.decodeSingleOrNull<TeacherInfo>()

                if (profile != null) {
                    teacherField.setText(profile.full_name)
                    emailField.setText(profile.email)
                }
                if (teacherInfo != null) {
                    academyField.setText(teacherInfo.academy_name)
                }
            } catch (e: Exception) {
                toast("Error loading: ${e.message}")
            }
        }
    }

    private fun saveProfile() {
        val academy     = InputValidator.sanitize(academyField.text.toString().trim())
        val teacher     = InputValidator.sanitize(teacherField.text.toString().trim())
        val email       = emailField.text.toString().trim()
        val pass        = passField.text.toString().trim()

        if (academy.isEmpty() || teacher.isEmpty()) {
            toast("Fill academy & teacher name"); return
        }
        if (!InputValidator.isValidEmail(email)) {
            toast("Enter valid email"); return
        }

        val existingTeacherId = SessionManager.getTeacherId(this)

        lifecycleScope.launch {
            btnSave.isEnabled = false
            try {
                if (existingTeacherId != null) {
                    // ── Existing teacher: just update via RPC ────────────────
                    val code = SecurePrefs.get(this@SetupProfileActivity, "app")
                        .getString("joinCode", null) ?: generateJoinCode()
                    callSetupRpc(academy, teacher, email, code)
                } else {
                    // ── New teacher: signup or sign in first ─────────────────
                    if (!InputValidator.isValidPassword(pass)) {
                        toast("Password must be at least 6 characters")
                        btnSave.isEnabled = true
                        return@launch
                    }

                    // Step 1: Get authenticated (signup or sign-in)
                    val userId = authenticateUser(email, pass)

                    if (userId == null) {
                        toast("Account created! Check your email to confirm, then login.")
                        btnSave.isEnabled = true
                        startActivity(Intent(this@SetupProfileActivity, MainActivity::class.java))
                        finish()
                        return@launch
                    }

                    // Step 2: Call server-side RPC to set up profile + teacher
                    val code = generateJoinCode()
                    callSetupRpc(academy, teacher, email, code)

                    // Step 3: Save session and go to dashboard
                    SessionManager.saveTeacherSession(
                        this@SetupProfileActivity, userId, code, email
                    )
                    startActivity(Intent(this@SetupProfileActivity, DashboardActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                toast("Error: ${e.message}")
                btnSave.isEnabled = true
            }
        }
    }

    /**
     * Tries to authenticate: signup first, if user exists → sign in.
     * Returns userId if successful, null if email confirmation required.
     */
    private suspend fun authenticateUser(email: String, pass: String): String? {
        // Try signup
        try {
            auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            val user = auth.currentUserOrNull()
            if (user != null) return user.id
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("already", ignoreCase = true)) {
                // User already exists → sign in instead
            } else {
                throw e  // Some other signup error
            }
        }

        // Try sign-in (either after failed signup or when session not set)
        try {
            auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            return auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            // If sign-in also fails, email confirmation may be required
            return null
        }
    }

    /**
     * Calls the server-side `setup_teacher_profile` RPC function.
     * This runs with SECURITY DEFINER → bypasses RLS → guaranteed to work.
     */
    private suspend fun callSetupRpc(
        academy: String, teacher: String, email: String,
        joinCode: String
    ) {
        SupabaseManager.client.postgrest.rpc(
            "setup_teacher_profile",
            mapOf(
                "p_teacher_name" to teacher,
                "p_email" to email,
                "p_academy_name" to academy,
                "p_join_code" to joinCode
            )
        )
        toast("Profile Saved 😎")
    }

    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    data class FullProfile(val full_name: String, val email: String)

    @Serializable
    data class TeacherInfo(val academy_name: String, val upi_id: String?)
}
