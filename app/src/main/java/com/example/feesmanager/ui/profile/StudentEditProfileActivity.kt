package com.example.feesmanager.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.utils.GlideHelper
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class StudentEditProfileActivity : BaseActivity() {

    private val profileImageViewModel: ProfileImageViewModel by viewModels()

    private lateinit var btnBack: ImageView
    private lateinit var btnSave: TextView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var btnEditPhoto: ImageView

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvWhatsapp: TextView
    private lateinit var tvClass: TextView

    private lateinit var rowName: LinearLayout
    private lateinit var rowEmail: LinearLayout
    private lateinit var rowWhatsapp: LinearLayout

    private var studentId: String = ""

    // Mutable state for saving
    private var currentName = ""
    private var currentEmail = ""
    private var currentWhatsapp = ""

    private var pendingImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            pendingImageUri = it
            com.bumptech.glide.Glide.with(this).load(it).circleCrop().into(ivProfilePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_edit_profile)

        studentId = SessionManager.getStudentId(this) ?: return finish()

        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnEditPhoto = findViewById(R.id.btnEditPhoto)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvWhatsapp = findViewById(R.id.tvWhatsapp)
        tvClass = findViewById(R.id.tvClass)

        rowName = findViewById(R.id.rowName)
        rowEmail = findViewById(R.id.rowEmail)
        rowWhatsapp = findViewById(R.id.rowWhatsapp)

        btnBack.setOnClickListener { finish() }
        
        val editPhotoClick = View.OnClickListener { pickImageLauncher.launch("image/*") }
        ivProfilePhoto.setOnClickListener(editPhotoClick)
        btnEditPhoto.setOnClickListener(editPhotoClick)

        rowName.setOnClickListener { showEditDialog("Name", currentName) { currentName = it; tvName.text = it } }
        rowEmail.setOnClickListener { showEditDialog("Email", currentEmail) { currentEmail = it; tvEmail.text = it } }
        rowWhatsapp.setOnClickListener { showEditDialog("WhatsApp Number", currentWhatsapp) { currentWhatsapp = it; tvWhatsapp.text = it.ifEmpty { "Not set" } } }

        btnSave.setOnClickListener { saveProfile() }

        setupProfileImage()
        loadProfileData()
    }

    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(studentId)
        profileImageViewModel.avatarUrl.observe(this) { url ->
            GlideHelper.loadAvatar(ivProfilePhoto, url)
        }
        profileImageViewModel.uploadResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show()
                is FmResult.Success -> {
                    GlideHelper.loadAvatarFresh(ivProfilePhoto, result.content)
                    Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
                is FmResult.Error -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadProfileData() {
        lifecycleScope.launch {
            try {
                val db = SupabaseManager.client.postgrest
                val profile = db.from("profiles")
                    .select(Columns.raw("full_name, email")) {
                        filter { eq("id", studentId) }
                    }.decodeSingle<ProfileRow>()

                val enrollment = db.from("enrollments")
                    .select(Columns.raw("whatsapp_number, teacher_classes!inner(class_name)")) {
                        filter { eq("student_id", studentId) }
                    }.decodeList<EnrollmentRow>().firstOrNull()

                currentName = profile.full_name
                currentEmail = profile.email
                currentWhatsapp = enrollment?.whatsapp_number ?: ""

                tvName.text = currentName
                tvEmail.text = currentEmail
                tvWhatsapp.text = currentWhatsapp.ifEmpty { "Not set" }
                tvClass.text = enrollment?.teacher_classes?.class_name ?: "Unknown"

            } catch (e: Exception) {
                Toast.makeText(this@StudentEditProfileActivity, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProfile() {
        if (currentName.isBlank()) {
            Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                if (pendingImageUri != null) {
                    val uploadResult = com.example.feesmanager.data.repository.ProfileImageRepository()
                        .uploadAndSaveAvatar(this@StudentEditProfileActivity, studentId, pendingImageUri!!)
                    if (uploadResult !is FmResult.Success) {
                        Toast.makeText(this@StudentEditProfileActivity, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        btnSave.text = "Save"
                        return@launch
                    }
                }

                val db = SupabaseManager.client.postgrest
                
                // Update Profiles
                val profileUpdate = mutableMapOf<String, String>()
                profileUpdate["full_name"] = currentName
                if (currentEmail.isNotBlank()) {
                    profileUpdate["email"] = currentEmail
                }
                
                db.from("profiles").update(profileUpdate) {
                    filter { eq("id", studentId) }
                }

                // Update enrollments
                val updateData = mutableMapOf<String, String?>()
                updateData["whatsapp_number"] = currentWhatsapp.ifEmpty { null }
                
                db.from("enrollments").update(updateData) {
                    filter { eq("student_id", studentId) }
                }

                Toast.makeText(this@StudentEditProfileActivity, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@StudentEditProfileActivity, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
                btnSave.isEnabled = true
                btnSave.text = "Save"
            }
        }
    }

    private fun showEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        val input = EditText(this)
        input.setText(currentValue)
        input.setSelection(input.text.length)
        
        val padding = (20 * resources.displayMetrics.density).toInt()
        
        val container = LinearLayout(this)
        container.setPadding(padding, padding / 2, padding, 0)
        container.addView(input, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        AlertDialog.Builder(this)
            .setTitle("Edit $title")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                onSave(input.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Serializable
    private data class ProfileRow(val full_name: String, val email: String)

    @Serializable
    private data class TeacherClass(val class_name: String)

    @Serializable
    private data class EnrollmentRow(
        val whatsapp_number: String? = null,
        val teacher_classes: TeacherClass? = null
    )
}
