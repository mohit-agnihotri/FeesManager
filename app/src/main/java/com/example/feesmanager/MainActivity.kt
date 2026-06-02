package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import com.example.feesmanager.AnimUtil.withBounce

class MainActivity : BaseActivity() {

    private val auth = SupabaseManager.client.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val teacherId   = SessionManager.getTeacherId(this)
        val supabaseSession = auth.currentSessionOrNull()

        if (supabaseSession != null && !teacherId.isNullOrEmpty() && supabaseSession.user?.id == teacherId) {
            if (BiometricHelper.isAvailable(this) &&
                SecurePrefs.get(this, "app").getBoolean("biometric_enabled", false)) {
                BiometricHelper.authenticate(
                    activity = this, title = "Teacher Login", subtitle = "Verify to access dashboard",
                    onSuccess = { startActivity(Intent(this, DashboardActivity::class.java)); finish() },
                    onFailed  = { Toast.makeText(this, "Biometric failed", Toast.LENGTH_SHORT).show() },
                    onError   = { _ -> startActivity(Intent(this, DashboardActivity::class.java)); finish() }
                )
            } else {
                startActivity(Intent(this, DashboardActivity::class.java)); finish()
            }
            return
        }

        setContentView(R.layout.activity_main)

        val emailField    = findViewById<EditText>(R.id.username)
        val passwordField = findViewById<EditText>(R.id.password)
        val loginBtn      = findViewById<Button>(R.id.loginBtn)
        val createTv      = findViewById<TextView>(R.id.tvCreate)
        val googleBtn     = findViewById<Button>(R.id.btnGoogleSignIn)

        // Entrance animations
        AnimUtil.slideUp(emailField,    100)
        AnimUtil.slideUp(passwordField, 180)
        AnimUtil.slideUp(loginBtn,      260)
        AnimUtil.slideUp(googleBtn,     320)
        AnimUtil.slideUp(createTv,      380)

        loginBtn.withBounce {
            val email = emailField.text.toString().trim()
            val pass  = passwordField.text.toString().trim()
            if (!InputValidator.isValidEmail(email)) {
                Toast.makeText(this, "Enter valid email", Toast.LENGTH_SHORT).show(); return@withBounce
            }
            if (pass.isEmpty()) {
                Toast.makeText(this, "Enter password", Toast.LENGTH_SHORT).show(); return@withBounce
            }
            
            loginBtn.isEnabled = false
            lifecycleScope.launch {
                try {
                    auth.signInWith(Email) {
                        this.email = email
                        this.password = pass
                    }

                    // DEBUG: Check if Supabase session exists
                    val session = auth.currentSessionOrNull()

                    Toast.makeText(
                        this@MainActivity,
                        "Session = ${session != null}",
                        Toast.LENGTH_LONG
                    ).show()

                    val user = auth.currentUserOrNull()

                    if (user != null) {
                        loadTeacherAndProceed(user.id)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Login failed (user is null)",
                            Toast.LENGTH_SHORT
                        ).show()
                        loginBtn.isEnabled = true
                    }

                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Login Error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    loginBtn.isEnabled = true
                }
            }
        }
 
        googleBtn.withBounce {
            Toast.makeText(this, "Google Sign-In is coming soon with Supabase!", Toast.LENGTH_SHORT).show()
        }

        createTv.setOnClickListener { startActivity(Intent(this, SetupProfileActivity::class.java)) }
    }

    private fun loadTeacherAndProceed(uid: String) {
        lifecycleScope.launch {
            try {
                // Fetch teacher profile from `teachers` table
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select {
                        filter { eq("id", uid) }
                    }.decodeSingleOrNull<TeacherProfile>()

                val userEmail = auth.currentUserOrNull()?.email ?: ""

                if (teacher == null) {
                    // Start SetupProfileActivity if no teacher record exists
                    startActivity(Intent(this@MainActivity, SetupProfileActivity::class.java))
                } else {
                    // Sync session and move to dashboard
                    val joinCode = teacher.join_code ?: ""
                    SessionManager.saveTeacherSession(this@MainActivity, uid, joinCode, userEmail)
                    startActivity(Intent(this@MainActivity, DashboardActivity::class.java))
                }
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Database error: ${e.message}", Toast.LENGTH_SHORT).show()
                findViewById<Button>(R.id.loginBtn).isEnabled = true
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class TeacherProfile(val id: String, val join_code: String?)
}
