package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/**
 * StudentJoinActivity — Student enters join code to request access to a class.
 *
 * Uses the server-side `join_academy()` RPC function which:
 * 1. Validates join code
 * 2. Updates student profile
 * 3. Creates enrollment (pending)
 * 4. Creates initial fee record
 * All in one atomic, RLS-bypassing call.
 */
class StudentJoinActivity : BaseActivity() {

    private lateinit var nameField     : EditText
    private lateinit var classField    : EditText
    private lateinit var whatsappField : EditText
    private lateinit var codeField     : EditText
    private lateinit var joinBtn       : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_join)

        nameField     = findViewById(R.id.etName)
        classField    = findViewById(R.id.etClass)
        whatsappField = findViewById(R.id.etWhatsapp)
        codeField     = findViewById(R.id.etCode)
        joinBtn       = findViewById(R.id.btnJoin)

        joinBtn.setOnClickListener {
            joinBtn.isEnabled = false
            joinAcademy()
        }
    }

    private fun joinAcademy() {
        val name     = InputValidator.sanitize(nameField.text.toString().trim())
        val cls      = InputValidator.sanitize(classField.text.toString().trim())
        val whatsapp = whatsappField.text.toString().trim().filter { it.isDigit() || it == '+' }
        val joinCode = codeField.text.toString().trim().uppercase()

        if (name.isEmpty() || !InputValidator.isValidName(name)) {
            toast("Enter valid name"); joinBtn.isEnabled = true; return
        }
        if (cls.isEmpty()) {
            toast("Enter your class"); joinBtn.isEnabled = true; return
        }
        if (!InputValidator.isValidPhone(whatsapp)) {
            toast("Enter valid WhatsApp number (10 digits)"); joinBtn.isEnabled = true; return
        }
        if (!InputValidator.isValidJoinCode(joinCode)) {
            toast("Join code must be 6 characters"); joinBtn.isEnabled = true; return
        }

        val uid = AuthRepository().getCurrentUserId()
        if (uid == null) {
            toast("Please login first ❌")
            startActivity(Intent(this, StudentLoginActivity::class.java)); finish(); return
        }

        lifecycleScope.launch {
            try {
                // Call server-side RPC — does everything in one call
                val response = SupabaseManager.client.postgrest.rpc(
                    "join_academy",
                    mapOf(
                        "p_name" to name,
                        "p_class" to cls,
                        "p_whatsapp" to whatsapp,
                        "p_join_code" to joinCode
                    )
                ).decodeAs<JsonObject>()

                val success = response["success"]?.jsonPrimitive?.booleanOrNull ?: false
                val error = response["error"]?.jsonPrimitive?.contentOrNull

                if (!success) {
                    toast(error ?: "Join failed ❌")
                    joinBtn.isEnabled = true
                    return@launch
                }

                val teacherId = response["teacher_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val status = response["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                val alreadyEnrolled = response["already_enrolled"]?.jsonPrimitive?.booleanOrNull ?: false

                // Save session with student details
                val email = SupabaseManager.client.auth.currentUserOrNull()?.email ?: ""
                SessionManager.saveStudentSession(this@StudentJoinActivity, teacherId, uid,
                    studentName = name, email = email, className = cls)

                if (alreadyEnrolled) {
                    // Route based on existing status
                    when (status) {
                        "approved" -> {
                            toast("Welcome back! 🎉")
                            startActivity(Intent(this@StudentJoinActivity, StudentDashboardActivity::class.java))
                        }
                        "rejected" -> {
                            toast("Your request was previously rejected. Contact your teacher.")
                            joinBtn.isEnabled = true
                            return@launch
                        }
                        else -> {
                            toast("Request already pending ⏳")
                            startActivity(Intent(this@StudentJoinActivity, StudentPendingApprovalActivity::class.java))
                        }
                    }
                } else {
                    toast("Join request sent! ⏳ Waiting for teacher approval.")
                    startActivity(Intent(this@StudentJoinActivity, StudentPendingApprovalActivity::class.java))
                }
                finish()

            } catch (e: Exception) {
                toast("Error: ${e.message}")
                joinBtn.isEnabled = true
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
