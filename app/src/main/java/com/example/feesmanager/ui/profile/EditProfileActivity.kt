package com.example.feesmanager.ui.profile

import android.content.Intent
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

class EditProfileActivity : BaseActivity() {

    private val profileImageViewModel: ProfileImageViewModel by viewModels()

    private lateinit var btnBack: ImageView
    private lateinit var btnSave: TextView
    private lateinit var ivProfilePhoto: ImageView
    private lateinit var btnEditPhoto: ImageView

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvAcademy: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvQuote: TextView
    private lateinit var tvQualifications: TextView

    private lateinit var rowName: LinearLayout
    private lateinit var rowAcademy: LinearLayout
    private lateinit var rowPhone: LinearLayout
    private lateinit var rowQuote: LinearLayout
    private lateinit var rowQualifications: LinearLayout

    private var teacherId: String = ""

    // Mutable state for saving
    private var currentName = ""
    private var currentAcademy = ""
    private var currentPhone = ""
    private var currentQuote = ""
    private var currentQualifications = ""

    private var pendingImageUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { 
            pendingImageUri = it
            com.bumptech.glide.Glide.with(this).load(it).circleCrop().into(ivProfilePhoto)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        teacherId = SessionManager.getTeacherId(this) ?: return finish()

        btnBack = findViewById(R.id.btnBack)
        btnSave = findViewById(R.id.btnSave)
        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnEditPhoto = findViewById(R.id.btnEditPhoto)

        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvAcademy = findViewById(R.id.tvAcademy)
        tvPhone = findViewById(R.id.tvPhone)
        tvQuote = findViewById(R.id.tvQuote)
        tvQualifications = findViewById(R.id.tvQualifications)

        rowName = findViewById(R.id.rowName)
        rowAcademy = findViewById(R.id.rowAcademy)
        rowPhone = findViewById(R.id.rowPhone)
        rowQuote = findViewById(R.id.rowQuote)
        rowQualifications = findViewById(R.id.rowQualifications)

        btnBack.setOnClickListener { finish() }
        
        val editPhotoClick = View.OnClickListener { pickImageLauncher.launch("image/*") }
        ivProfilePhoto.setOnClickListener(editPhotoClick)
        btnEditPhoto.setOnClickListener(editPhotoClick)

        rowName.setOnClickListener { showEditDialog("Name", currentName) { currentName = it; tvName.text = it } }
        rowAcademy.setOnClickListener { showEditDialog("Academy Name", currentAcademy) { currentAcademy = it; tvAcademy.text = it } }
        rowQualifications.setOnClickListener { showEditDialog("Qualifications", currentQualifications) { currentQualifications = it; tvQualifications.text = it.ifEmpty { "Not set" } } }
        rowPhone.setOnClickListener { showEditDialog("Phone Number", currentPhone) { currentPhone = it; tvPhone.text = it.ifEmpty { "Not set" } } }
        rowQuote.setOnClickListener { showEditDialog("Quote / Bio", currentQuote) { currentQuote = it; tvQuote.text = it.ifEmpty { "Not set" } } }

        btnSave.setOnClickListener { saveProfile() }

        setupProfileImage()
        loadProfileData()
    }

    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(teacherId)
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
                val teacher = db.from("teachers")
                    .select(Columns.raw("academy_name, phone, quote, qualifications, profiles!inner(full_name, email)")) {
                        filter { eq("id", teacherId) }
                    }.decodeSingle<TeacherProfileFetch>()

                currentName = teacher.profiles.full_name
                currentAcademy = teacher.academy_name
                currentPhone = teacher.phone ?: ""
                currentQuote = teacher.quote ?: ""
                currentQualifications = teacher.qualifications ?: ""

                tvName.text = currentName
                tvEmail.text = teacher.profiles.email
                tvAcademy.text = currentAcademy
                tvQualifications.text = currentQualifications.ifEmpty { "Not set" }
                tvPhone.text = currentPhone.ifEmpty { "Not set" }
                tvQuote.text = currentQuote.ifEmpty { "Not set" }

            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProfile() {
        if (currentName.isBlank() || currentAcademy.isBlank()) {
            Toast.makeText(this, "Name and Academy Name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        
        btnSave.isEnabled = false
        btnSave.text = "Saving..."

        lifecycleScope.launch {
            try {
                // Upload avatar if a new one was selected
                if (pendingImageUri != null) {
                    val uploadResult = com.example.feesmanager.data.repository.ProfileImageRepository()
                        .uploadAndSaveAvatar(this@EditProfileActivity, teacherId, pendingImageUri!!)
                    if (uploadResult !is FmResult.Success) {
                        Toast.makeText(this@EditProfileActivity, "Failed to upload image", Toast.LENGTH_SHORT).show()
                        btnSave.isEnabled = true
                        btnSave.text = "Save"
                        return@launch
                    }
                }

                val db = SupabaseManager.client.postgrest
                
                // Update Profiles
                db.from("profiles").update(mapOf("full_name" to currentName)) {
                    filter { eq("id", teacherId) }
                }

                // Update Teachers
                val updateData = mutableMapOf<String, String?>()
                updateData["academy_name"] = currentAcademy
                updateData["phone"] = currentPhone.ifEmpty { null }
                updateData["quote"] = currentQuote.ifEmpty { null }
                updateData["qualifications"] = currentQualifications.ifEmpty { null }
                
                db.from("teachers").update(updateData) {
                    filter { eq("id", teacherId) }
                }

                Toast.makeText(this@EditProfileActivity, "Profile saved successfully", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Failed to save profile: ${e.message}", Toast.LENGTH_SHORT).show()
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
    private data class TeacherProfileFetch(
        val academy_name: String,
        val phone: String? = null,
        val quote: String? = null,
        val qualifications: String? = null,
        val profiles: ProfileRow
    )
}
