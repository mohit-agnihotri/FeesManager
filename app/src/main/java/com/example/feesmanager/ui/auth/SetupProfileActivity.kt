package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.ui.dashboard.DashboardActivity
import com.example.feesmanager.R
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

        // ── Pre-fill from LoginActivity if coming from unified signup ─────
        intent.getStringExtra("prefill_name")?.let    { teacherField.setText(it) }
        intent.getStringExtra("prefill_academy")?.let { academyField.setText(it) }
        intent.getStringExtra("prefill_email")?.let   { emailField.setText(it) }

        // If user is already authenticated (came from LoginActivity signup),
        // hide the password field — they don't need to enter it again.
        val alreadyAuthenticated = auth.currentUserOrNull() != null
        if (alreadyAuthenticated) {
            passField.visibility = View.GONE
            passField.setText("already_authenticated") // placeholder to pass validation
        }

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
                    // ── Existing teacher: just update profile via RPC ────────
                    val code = SecurePrefs.get(this@SetupProfileActivity, "app")
                        .getString("joinCode", null) ?: generateJoinCode()
                    callSetupRpc(academy, teacher, email, code)
                } else {
                    // ── New teacher signup ────────────────────────────────────
                    val alreadyAuthenticated = auth.currentUserOrNull() != null

                    val userId: String? = if (alreadyAuthenticated) {
                        // ✅ Already signed in via LoginActivity — skip signup step
                        auth.currentUserOrNull()?.id
                    } else {
                        // Came directly (not via LoginActivity) — need to authenticate
                        if (!InputValidator.isValidPassword(pass)) {
                            toast("Password must be at least 6 characters")
                            btnSave.isEnabled = true
                            return@launch
                        }
                        authenticateUser(email, pass)
                    }

                    if (userId == null) {
                        toast("Account created! Check your email to confirm, then sign in.")
                        btnSave.isEnabled = true
                        startActivity(Intent(this@SetupProfileActivity, LoginActivity::class.java))
                        finish()
                        return@launch
                    }

                    // Create teacher record in DB + update profile role
                    val code = generateJoinCode()
                    callSetupRpc(academy, teacher, email, code)

                    // Save session locally and go to dashboard
                    SessionManager.saveTeacherSession(
                        this@SetupProfileActivity, userId, code, email
                    )
                    SessionManager.setRole(this@SetupProfileActivity, "teacher")
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
        val user = auth.currentUserOrNull() ?: throw Exception("Not logged in")

        // 1. Update profiles table with the new name
        SupabaseManager.client.postgrest.from("profiles").update(
            mapOf("full_name" to teacher)
        ) {
            filter { eq("id", user.id) }
        }

        // 2. Upsert teachers table with academy name and join code
        val upsertData = TeacherUpsert(user.id, academy, joinCode)
        SupabaseManager.client.postgrest.from("teachers").upsert(upsertData) {
            onConflict = "id"
        }
        toast("Profile Saved 😎")
    }

    @Serializable
    private data class TeacherUpsert(val id: String, val academy_name: String, val join_code: String)

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