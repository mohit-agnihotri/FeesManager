package com.example.feesmanager.ui.student

import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.GlideHelper
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class AboutTeacherActivity : BaseActivity() {

    private lateinit var btnBack: ImageView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvAcademy: TextView
    private lateinit var tvQualifications: TextView
    private lateinit var tvQuote: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvEmail: TextView

    private var currentAvatarUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_teacher)

        btnBack = findViewById(R.id.btnBack)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        tvName = findViewById(R.id.tvName)
        tvAcademy = findViewById(R.id.tvAcademy)
        tvQualifications = findViewById(R.id.tvQualifications)
        tvQuote = findViewById(R.id.tvQuote)
        tvPhone = findViewById(R.id.tvPhone)
        tvEmail = findViewById(R.id.tvEmail)

        btnBack.setOnClickListener { finish() }

        ivProfilePhoto.setOnClickListener {
            showFullImage()
        }

        loadTeacherInfo()
    }

    private fun loadTeacherInfo() {
        val studentId = SessionManager.getStudentId(this)
        if (studentId == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val db = SupabaseManager.client.postgrest

                // First find the teacher ID the student is enrolled with
                val enrollment = db.from("enrollments")
                    .select(Columns.raw("teacher_id")) {
                        filter { eq("student_id", studentId) }
                    }.decodeList<EnrollmentFetch>().firstOrNull()

                if (enrollment == null) {
                    Toast.makeText(this@AboutTeacherActivity, "No assigned teacher found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val teacherId = enrollment.teacher_id

                // Fetch teacher info
                val teacher = db.from("teachers")
                    .select(Columns.raw("academy_name, phone, quote, qualifications, profiles!inner(full_name, email, avatar_url)")) {
                        filter { eq("id", teacherId) }
                    }.decodeSingle<TeacherInfoFetch>()

                tvName.text = teacher.profiles.full_name
                tvAcademy.text = teacher.academy_name
                tvQualifications.text = teacher.qualifications?.ifEmpty { "Not set" } ?: "Not set"
                tvQuote.text = teacher.quote?.ifEmpty { "Not set" } ?: "Not set"
                tvPhone.text = teacher.phone?.ifEmpty { "Not set" } ?: "Not set"
                tvEmail.text = teacher.profiles.email

                currentAvatarUrl = teacher.profiles.avatar_url
                if (currentAvatarUrl != null) {
                    GlideHelper.loadAvatar(ivProfilePhoto, currentAvatarUrl)
                }

            } catch (e: Exception) {
                Toast.makeText(this@AboutTeacherActivity, "Failed to load teacher info: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showFullImage() {
        if (currentAvatarUrl.isNullOrEmpty()) return

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        GlideHelper.loadAvatarFresh(imageView, currentAvatarUrl!!)
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    @Serializable
    private data class EnrollmentFetch(val teacher_id: String)

    @Serializable
    private data class ProfileRow(val full_name: String, val email: String, val avatar_url: String? = null)

    @Serializable
    private data class TeacherInfoFetch(
        val academy_name: String,
        val phone: String? = null,
        val quote: String? = null,
        val qualifications: String? = null,
        val profiles: ProfileRow
    )
}
