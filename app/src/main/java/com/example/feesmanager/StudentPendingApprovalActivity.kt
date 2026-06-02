package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * StudentPendingApprovalActivity — Migrated to Supabase (Postgres + Realtime).
 * Listens for student enrollment status changes in real-time.
 */
class StudentPendingApprovalActivity : BaseActivity() {

    private lateinit var tvStatus  : TextView
    private lateinit var tvMessage : TextView
    private lateinit var btnLogout : Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_pending_approval)

        tvStatus  = findViewById(R.id.tvApprovalStatus)
        tvMessage = findViewById(R.id.tvApprovalMessage)
        btnLogout = findViewById(R.id.btnCancelJoin)

        btnLogout.setOnClickListener {
            lifecycleScope.launch {
                SessionManager.logoutStudent(this@StudentPendingApprovalActivity)
                startActivity(Intent(this@StudentPendingApprovalActivity, RoleSelectActivity::class.java))
                finish()
            }
        }

        listenForApproval()
    }

    private fun listenForApproval() {
        val uid       = SupabaseManager.client.auth.currentUserOrNull()?.id ?: return
        val teacherId = SessionManager.getStudentTeacherId(this) ?: return

        // 1. Initial status check
        lifecycleScope.launch {
            try {
                val enrollment = SupabaseManager.client.postgrest.from("enrollments")
                    .select {
                        filter {
                            eq("student_id", uid)
                            eq("teacher_id", teacherId)
                        }
                    }.decodeSingleOrNull<EnrollmentStatus>()
                
                enrollment?.let { updateUi(it.status) }
            } catch (e: Exception) {
                // Ignore initial check failure, wait for realtime
            }
        }

        // 2. Real-time status tracking via Supabase Channels
        try {
            val channel = SupabaseManager.client.channel("enrollment_status_$uid")
            channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "enrollments"
            }.onEach { action ->
                val record = action.record
                // Only process updates for our student
                val recordStudentId = record["student_id"]?.toString()?.replace("\"", "") ?: ""
                if (recordStudentId == uid) {
                    val status = record["status"]?.toString()?.replace("\"", "") ?: "pending"
                    runOnUiThread { updateUi(status) }
                }
            }.launchIn(lifecycleScope)

            lifecycleScope.launch {
                channel.subscribe()
            }
        } catch (e: Exception) {
            // Realtime subscription failed — rely on manual polling
        }
    }

    private fun updateUi(status: String) {
        when (status) {
            "approved" -> {
                tvStatus.text  = "✅ Approved!"
                tvMessage.text = "Your request has been approved. Redirecting..."
                tvStatus.postDelayed({
                    startActivity(Intent(this, StudentDashboardActivity::class.java))
                    finish()
                }, 1500)
            }
            "rejected" -> {
                tvStatus.text  = "❌ Request Rejected"
                tvMessage.text = "Your join request was rejected. Please contact the teacher."
            }
            else -> {
                tvStatus.text  = "⏳ Pending Approval"
                tvMessage.text = "Your join request is being reviewed by the teacher."
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class EnrollmentStatus(val status: String)
}
