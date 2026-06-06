package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.ui.student.StudentJoinActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.utils.InputValidator
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

/**
 * StudentSignupActivity — Supabase Auth signup for students.
 * Handles "user already exists" gracefully by signing in instead.
 */
class StudentSignupActivity : BaseActivity() {

    private val auth = SupabaseManager.client.auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_signup)

        val emailField    = findViewById<EditText>(R.id.etEmail)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val signupBtn     = findViewById<Button>(R.id.btnSignup)

        signupBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            val pass  = passwordField.text.toString().trim()

            if (!InputValidator.isValidEmail(email)) {
                toast("Enter a valid email address"); return@setOnClickListener
            }
            if (!InputValidator.isValidPassword(pass)) {
                toast("Password must be at least 6 characters"); return@setOnClickListener
            }

            signupBtn.isEnabled = false

            lifecycleScope.launch {
                try {
                    // Try signup first
                    var userId: String? = null

                    try {
                        auth.signUpWith(Email) {
                            this.email = email
                            this.password = pass
                        }
                        userId = auth.currentUserOrNull()?.id
                    } catch (e: Exception) {
                        val msg = e.message ?: ""
                        if (msg.contains("already", ignoreCase = true)) {
                            // User exists → sign in instead
                            toast("Account exists, signing in...")
                            auth.signInWith(Email) {
                                this.email = email
                                this.password = pass
                            }
                            userId = auth.currentUserOrNull()?.id
                        } else {
                            throw e
                        }
                    }

                    // If no session (email confirmation needed)
                    if (userId == null) {
                        try {
                            auth.signInWith(Email) {
                                this.email = email
                                this.password = pass
                            }
                            userId = auth.currentUserOrNull()?.id
                        } catch (_: Exception) {}
                    }

                    if (userId != null) {
                        toast("Account Ready ✅")
                        startActivity(
                            Intent(
                                this@StudentSignupActivity,
                                StudentJoinActivity::class.java
                            )
                        )
                        finish()
                    } else {
                        toast("Please check your email to confirm your account, then login.")
                        signupBtn.isEnabled = true
                    }
                } catch (e: Exception) {
                    toast("Signup failed: ${e.message}")
                    signupBtn.isEnabled = true
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}