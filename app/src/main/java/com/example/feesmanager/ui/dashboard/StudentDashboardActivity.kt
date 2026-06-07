package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.feesmanager.R
import com.example.feesmanager.ui.announcements.AnnouncementsActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.repository.AuthRepository
import com.example.feesmanager.data.repository.FeesRepository
import com.example.feesmanager.ui.auth.RoleSelectActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.ui.auth.StudentLoginActivity
import com.example.feesmanager.ui.profile.ProfileImageViewModel
import com.example.feesmanager.ui.settings.StudentFaqActivity
import com.example.feesmanager.ui.student.StudentPendingApprovalActivity
import com.example.feesmanager.utils.AnimUtil.withBounce
import com.example.feesmanager.utils.DrawerHelper
import com.example.feesmanager.utils.GlideHelper
import com.example.feesmanager.utils.UnreadBadgeHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class StudentDashboardActivity : BaseActivity() {

    private val viewModel: StudentDashboardViewModel by viewModels()
    private val profileImageViewModel: ProfileImageViewModel by viewModels()

    private lateinit var nameTv: TextView
    private lateinit var classTv: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var ivStudentAvatar: ImageView

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    private var currentName = ""
    private var currentClass = ""
    private var currentTeacherId = ""
    private var currentStudentId = ""

    // Exposed for Fragment
    fun getCurrentName() = currentName
    fun getCurrentClass() = currentClass
    fun getCurrentTeacherId() = currentTeacherId
    fun getCurrentStudentId() = currentStudentId

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                profileImageViewModel.uploadAvatar(this, currentStudentId, it)
            }
        }

    fun launchImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val currentUid = AuthRepository().getCurrentUserId()
        val savedStudentId = SessionManager.getStudentId(this)

        if (currentUid == null || (savedStudentId != null && currentUid != savedStudentId)) {
            Toast.makeText(this, "Session expired.", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { SessionManager.logoutStudent(this@StudentDashboardActivity) }
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish(); return
        }

        nameTv = findViewById(R.id.tvStudentName)
        classTv = findViewById(R.id.tvClass)
        drawerLayout = findViewById(R.id.drawerLayout)
        ivStudentAvatar = findViewById(R.id.ivStudentAvatar)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        currentTeacherId = SessionManager.getStudentTeacherId(this) ?: ""
        currentStudentId = currentUid

        setupProfileImage()
        setupViewPagerAndNav()
        setupHeaderButtons()

        checkApprovalStatus(currentUid)
    }

    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(currentStudentId)

        profileImageViewModel.avatarUrl.observe(this) { url ->
            GlideHelper.loadAvatar(ivStudentAvatar, url)
            findViewById<ImageView>(R.id.ivDrawerProfile)?.let { GlideHelper.loadAvatar(it, url) }
        }

        ivStudentAvatar.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        profileImageViewModel.uploadResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> toast("Uploading photo...")
                is FmResult.Success -> {
                    GlideHelper.loadAvatarFresh(ivStudentAvatar, result.content)
                    toast("Profile photo updated ✅")
                }
                is FmResult.Error -> toast("Upload failed: ${result.message}")
            }
        }
    }

    private fun setupViewPagerAndNav() {
        viewPager.adapter = StudentPagerAdapter(this)
        viewPager.isUserInputEnabled = true // Enable swiping

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                when (position) {
                    0 -> bottomNav.selectedItemId = R.id.navigation_home
                    1 -> bottomNav.selectedItemId = R.id.navigation_fees
                    2 -> bottomNav.selectedItemId = R.id.navigation_chat
                    3 -> bottomNav.selectedItemId = R.id.navigation_settings
                }
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    viewPager.currentItem = 0; true
                }
                R.id.navigation_fees -> {
                    viewPager.currentItem = 1; true
                }
                R.id.navigation_chat -> {
                    viewPager.currentItem = 2; true
                }
                R.id.navigation_settings -> {
                    viewPager.currentItem = 3; true
                }
                else -> false
            }
        }
    }

    private fun setupHeaderButtons() {
        runCatching {
            findViewById<View>(R.id.btnHamburger).withBounce {
                DrawerHelper.openDrawer(drawerLayout)
            }
        }
        runCatching {
            findViewById<View>(R.id.btnAnnouncementsBell).withBounce {
                // Ensure AnnouncementsActivity is correctly imported
                startActivity(Intent(this, com.example.feesmanager.ui.announcements.AnnouncementsActivity::class.java).putExtra("mode", "student"))
            }
        }
        runCatching {
            findViewById<View>(R.id.btnHelp).withBounce {
                startActivity(Intent(this, StudentFaqActivity::class.java))
            }
        }
    }

    private fun checkApprovalStatus(studentId: String) {
        if (currentTeacherId.isEmpty()) {
            loadDashboard(studentId)
            return
        }

        lifecycleScope.launch {
            try {
                val enrollment = SupabaseManager.client.postgrest.from("enrollments")
                    .select {
                        filter {
                            eq("student_id", studentId)
                            eq("teacher_id", currentTeacherId)
                        }
                    }.decodeSingleOrNull<EnrollmentStatusRow>()

                when (enrollment?.status ?: "approved") {
                    "pending" -> {
                        startActivity(Intent(this@StudentDashboardActivity, StudentPendingApprovalActivity::class.java))
                        finish()
                    }
                    "rejected" -> {
                        Toast.makeText(this@StudentDashboardActivity, "Join request rejected.", Toast.LENGTH_LONG).show()
                        SessionManager.logoutStudent(this@StudentDashboardActivity)
                        startActivity(Intent(this@StudentDashboardActivity, RoleSelectActivity::class.java))
                        finish()
                    }
                    else -> loadDashboard(studentId)
                }
            } catch (e: Exception) {
                loadDashboard(studentId)
            }
        }
    }

    private fun loadDashboard(studentId: String) {
        // Auto-trigger monthly rollover to ensure current month fee record exists
        if (currentTeacherId.isNotEmpty()) {
            lifecycleScope.launch {
                try {
                    FeesRepository().performMonthlyRollover(currentTeacherId) { _ -> }
                } catch (_: Exception) {}
            }
        }

        viewModel.student.observe(this) { result ->
            when (result) {
                is FmResult.Success -> {
                    val student = result.content
                    currentName = student.name
                    currentClass = "Class ${student.cls}"
                    nameTv.text = currentName
                    classTv.text = currentClass

                    SessionManager.updateStudentDetails(this, student.name, student.cls)

                    if (!student.avatarUrl.isNullOrBlank()) {
                        GlideHelper.loadAvatar(ivStudentAvatar, student.avatarUrl)
                    }

                    setupDrawer(currentName, currentClass)
                }
                is FmResult.Error -> Toast.makeText(this, "Failed to load data: ${result.message}", Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }

        viewModel.loadStudentData(currentTeacherId, studentId)
    }

    private fun setupDrawer(name: String, cls: String) {
        lifecycleScope.launch {
            try {
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select { filter { eq("id", currentTeacherId) } }
                    .decodeSingleOrNull<AcademyNameRow>()

                val academyName = teacher?.academy_name ?: ""
                val drawerView = runCatching { findViewById<View>(R.id.navDrawerView) }.getOrNull()
                if (drawerView != null) {
                    DrawerHelper.setupStudent(this@StudentDashboardActivity, drawerLayout, drawerView, name, cls, academyName)
                }
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentStudentId.isNotEmpty() && currentTeacherId.isNotEmpty()) {
            loadUnreadBadges()
            viewModel.loadStudentData(currentTeacherId, currentStudentId)
        }
    }

    private fun loadUnreadBadges() {
        lifecycleScope.launch {
            val className = currentClass.removePrefix("Class ").trim().ifEmpty {
                SessionManager.getStudentClassName(this@StudentDashboardActivity) ?: ""
            }
            if (className.isNotEmpty() && currentTeacherId.isNotEmpty()) {
                val hasAnnouncements = UnreadBadgeHelper.hasUnreadAnnouncements(
                    this@StudentDashboardActivity, currentTeacherId, className, currentStudentId
                )
                runCatching {
                    val indicator = findViewById<View>(R.id.indicatorAnnouncements)
                    indicator?.visibility = if (hasAnnouncements) View.VISIBLE else View.GONE
                }

                val personalUnread = UnreadBadgeHelper.fetchTotalUnreadForStudent(this@StudentDashboardActivity, currentTeacherId, currentStudentId)
                val classUnread = UnreadBadgeHelper.fetchClassUnreadForStudent(this@StudentDashboardActivity, currentTeacherId, className, currentStudentId)
                val totalUnread = personalUnread + classUnread
                val badge = bottomNav.getOrCreateBadge(R.id.navigation_chat)
                badge.isVisible = totalUnread > 0
                badge.number = totalUnread
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class EnrollmentStatusRow(val status: String)

    @Serializable
    private data class AcademyNameRow(val academy_name: String)
}