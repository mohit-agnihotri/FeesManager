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
import com.example.feesmanager.ui.advances.AdvancePaymentActivity
import com.example.feesmanager.ui.announcements.AnnouncementsActivity
import com.example.feesmanager.ui.chat.ClassChatActivity
import com.example.feesmanager.ui.fees.FeeCalendarActivity
import com.example.feesmanager.ui.fees.HistoryActivity
import com.example.feesmanager.ui.chat.MessageActivity
import com.example.feesmanager.ui.settings.MultiAcademyActivity
import com.example.feesmanager.ui.fees.PayFeesActivity
import com.example.feesmanager.R
import com.example.feesmanager.ui.settings.StudentFaqActivity
import com.example.feesmanager.ui.student.StudentPendingApprovalActivity
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
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.AnimUtil.withBounce
import com.example.feesmanager.utils.DrawerHelper
import com.example.feesmanager.utils.GlideHelper
import com.example.feesmanager.utils.UnreadBadgeHelper
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.iterator

/**
 * StudentDashboardActivity — Student's personal dashboard.
 *
 * Path: app/src/main/java/com/example/feesmanager/StudentDashboardActivity.kt
 *
 * UPDATED: Added profile image support.
 *   - ivStudentAvatar in header → click opens gallery
 *   - ProfileImageViewModel handles upload + load
 *   - GlideHelper renders with circle-crop & fallback avatar
 */
class StudentDashboardActivity : BaseActivity() {

    private val viewModel: StudentDashboardViewModel by viewModels()
    private val profileImageViewModel: ProfileImageViewModel by viewModels()  // ✅ NEW

    private lateinit var nameTv           : TextView
    private lateinit var classTv          : TextView
    private lateinit var totalTv          : TextView
    private lateinit var paidTv           : TextView
    private lateinit var pendingTv        : TextView
    private lateinit var cardAdvance      : View
    private lateinit var tvAdvanceBalance : TextView
    private lateinit var drawerLayout     : DrawerLayout
    private lateinit var ivStudentAvatar  : ImageView   // ✅ NEW

    private var currentName      = ""
    private var currentClass     = ""
    private var currentTeacherId = ""
    private var currentStudentId = ""

    // ── Gallery picker launcher ───────────────────────────────────────────────

    /** ✅ NEW */
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                profileImageViewModel.uploadAvatar(this, currentStudentId, it)
            }
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        val currentUid     = AuthRepository().getCurrentUserId()
        val savedStudentId = SessionManager.getStudentId(this)

        if (currentUid == null || (savedStudentId != null && currentUid != savedStudentId)) {
            Toast.makeText(this, "Session expired.", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { SessionManager.logoutStudent(this@StudentDashboardActivity) }
            startActivity(Intent(this, StudentLoginActivity::class.java))
            finish(); return
        }

        nameTv           = findViewById(R.id.tvStudentName)
        classTv          = findViewById(R.id.tvClass)
        totalTv          = findViewById(R.id.tvTotalFees)
        paidTv           = findViewById(R.id.tvPaid)
        pendingTv        = findViewById(R.id.tvPending)
        cardAdvance      = findViewById(R.id.cardAdvanceBalance)
        tvAdvanceBalance = findViewById(R.id.tvAdvanceBalance)
        drawerLayout     = findViewById(R.id.drawerLayout)
        ivStudentAvatar  = findViewById(R.id.ivStudentAvatar)  // ✅ NEW

        currentTeacherId = SessionManager.getStudentTeacherId(this) ?: ""
        currentStudentId = currentUid

        setupProfileImage()     // ✅ NEW
        checkApprovalStatus(currentUid)
    }

    // ── Profile image ─────────────────────────────────────────────────────────

    /** ✅ NEW */
    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(currentStudentId)

        profileImageViewModel.avatarUrl.observe(this) { url ->
            GlideHelper.loadAvatar(ivStudentAvatar, url)
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

    // ── Approval status check ─────────────────────────────────────────────────

    private fun checkApprovalStatus(studentId: String) {
        if (currentTeacherId.isEmpty()) { loadDashboard(studentId); return }

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
                    "pending"  -> {
                        startActivity(
                            Intent(
                                this@StudentDashboardActivity,
                                StudentPendingApprovalActivity::class.java
                            )
                        )
                        finish()
                    }
                    "rejected" -> {
                        Toast.makeText(this@StudentDashboardActivity,
                            "Join request rejected.", Toast.LENGTH_LONG).show()
                        SessionManager.logoutStudent(this@StudentDashboardActivity)
                        startActivity(
                            Intent(
                                this@StudentDashboardActivity,
                                RoleSelectActivity::class.java
                            )
                        )
                        finish()
                    }
                    else -> loadDashboard(studentId)
                }
            } catch (e: Exception) {
                loadDashboard(studentId)
            }
        }
    }

    // ── Dashboard data load ───────────────────────────────────────────────────

    private fun loadDashboard(studentId: String) {
        setupButtons(studentId)
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
                is FmResult.Loading -> { /* optional progress */ }
                is FmResult.Success -> renderStudent(result.content)
                is FmResult.Error   -> Toast.makeText(
                    this, "Failed to load data: ${result.message}", Toast.LENGTH_SHORT
                ).show()
            }
        }

        viewModel.loadStudentData(currentTeacherId, studentId)
        animateEntrance()
    }

    // ── Render student data ───────────────────────────────────────────────────

    private fun renderStudent(student: Student) {
        currentName  = student.name
        currentClass = "Class ${student.cls}"
        nameTv.text  = currentName
        classTv.text = currentClass

        // Cache student details to session for use in other screens
        SessionManager.updateStudentDetails(this, student.name, student.cls)

        // Update avatar if the student model carries a URL (from DB)
        if (!student.avatarUrl.isNullOrBlank()) {
            GlideHelper.loadAvatar(ivStudentAvatar, student.avatarUrl)
        }

        lifecycleScope.launch {
            try {
                val teacher = SupabaseManager.client.postgrest.from("teachers")
                    .select { filter { eq("id", currentTeacherId) } }
                    .decodeSingleOrNull<AcademyNameRow>()

                val academyName = teacher?.academy_name ?: ""
                val drawerView = runCatching { findViewById<View>(R.id.navDrawerView) }.getOrNull()
                if (drawerView != null)
                    DrawerHelper.setupStudent(
                        this@StudentDashboardActivity,
                        drawerLayout, drawerView,
                        currentName, currentClass, academyName
                    )
            } catch (_: Exception) {}
        }

        val advRemaining = student.advanceBalance.remaining
        cardAdvance.visibility = View.VISIBLE
        tvAdvanceBalance.text  = if (advRemaining > 0) "₹$advRemaining" else "₹0 (No advance)"

        val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        var currentTotal = 0; var currentPaid = 0; var totalPending = 0

        for ((key, feeMonth) in student.fees) {
            if (!key.matches(Regex("\\d{4}-\\d{2}"))) continue
            if (key == currentMonth) { currentTotal = feeMonth.total; currentPaid = feeMonth.paid }
            val pend = feeMonth.total - feeMonth.paid
            if (pend > 0) totalPending += pend
        }

        totalTv.text   = "₹$currentTotal"
        paidTv.text    = "₹$currentPaid"
        pendingTv.text = "₹${maxOf(0, totalPending)}"
    }

    // ── Button wiring ─────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (currentStudentId.isNotEmpty() && currentTeacherId.isNotEmpty()) {
            loadUnreadBadges()
            viewModel.loadStudentData(currentTeacherId, currentStudentId)
        }
    }

    private fun loadUnreadBadges() {
        lifecycleScope.launch {
            // Personal chat badge (teacher -> student messages)
            val personalUnread = UnreadBadgeHelper.fetchTotalUnreadForStudent(this@StudentDashboardActivity, currentTeacherId, currentStudentId)
            runCatching {
                val contactBtn = findViewById<View>(R.id.btnContactTeacher)
                contactBtn?.let { UnreadBadgeHelper.addBadge(this@StudentDashboardActivity, it, personalUnread) }
            }

            // Class chat badge (class messages not sent by student)
            val className = currentClass.removePrefix("Class ").trim().ifEmpty {
                SessionManager.getStudentClassName(this@StudentDashboardActivity) ?: ""
            }
            if (className.isNotEmpty() && currentTeacherId.isNotEmpty()) {
                val classUnread = UnreadBadgeHelper.fetchClassUnreadForStudent(
                    this@StudentDashboardActivity, currentTeacherId, className, currentStudentId
                )
                runCatching {
                    val classChatBtn = findViewById<View>(R.id.btnClassChat)
                    classChatBtn?.let { UnreadBadgeHelper.addBadge(this@StudentDashboardActivity, it, classUnread) }
                }

                // Announcements badge
                val hasAnnouncements = UnreadBadgeHelper.hasUnreadAnnouncements(
                    this@StudentDashboardActivity, currentTeacherId, className, currentStudentId
                )
                runCatching {
                    val indicator = findViewById<View>(R.id.indicatorAnnouncements)
                    indicator?.visibility = if (hasAnnouncements) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun setupButtons(studentId: String) {
        runCatching {
            findViewById<View>(R.id.btnHamburger).withBounce {
                DrawerHelper.openDrawer(drawerLayout)
            }
        }
        runCatching {
            findViewById<View>(R.id.btnPay).withBounce {
                startActivity(Intent(this, PayFeesActivity::class.java))
            }
        }
        runCatching {
            findViewById<View>(R.id.btnHistory).withBounce {
                startActivity(Intent(this, HistoryActivity::class.java))
            }
        }
        runCatching {
            findViewById<View>(R.id.btnCalendar).withBounce {
                startActivity(Intent(this, FeeCalendarActivity::class.java))
            }
        }

        runCatching {
            findViewById<View>(R.id.btnAnnouncementsBell).withBounce {
                startActivity(Intent(this, AnnouncementsActivity::class.java).putExtra("mode", "student"))
            }
        }
        runCatching {
            findViewById<View>(R.id.btnMyAcademies).withBounce {
                startActivity(Intent(this, MultiAcademyActivity::class.java))
            }
        }
        cardAdvance.setOnClickListener {
            startActivity(Intent(this, AdvancePaymentActivity::class.java))
        }
        runCatching {
            findViewById<View>(R.id.btnContactTeacher).withBounce {
                if (currentTeacherId.isEmpty()) {
                    Toast.makeText(this, "Not linked to academy", Toast.LENGTH_SHORT).show()
                    return@withBounce
                }
                startActivity(
                    Intent(this, MessageActivity::class.java)
                        .putExtra("mode",      "student")
                        .putExtra("teacherId", currentTeacherId)
                        .putExtra("studentId", studentId)
                )
            }
        }
        runCatching {
            findViewById<View>(R.id.btnClassChat).withBounce {
                if (currentTeacherId.isEmpty() || currentClass.isEmpty()) {
                    Toast.makeText(this, "Class not assigned yet", Toast.LENGTH_SHORT).show()
                    return@withBounce
                }
                startActivity(
                    Intent(this, ClassChatActivity::class.java)
                        .putExtra("teacherId",  currentTeacherId)
                        .putExtra("className",  currentClass.removePrefix("Class ").trim())
                        .putExtra("mode",       "student")
                        .putExtra("studentId",  studentId)
                        .putExtra("senderName", currentName)
                )
            }
        }
        // ✅ NEW: Help/FAQ button
        runCatching {
            findViewById<View>(R.id.btnHelp).withBounce {
                startActivity(Intent(this, StudentFaqActivity::class.java))
            }
        }



    }

    // ── Entrance animation ────────────────────────────────────────────────────

    private fun animateEntrance() {
        AnimUtil.slideUp(nameTv, 0); AnimUtil.slideUp(classTv, 60)
        val statCards = listOf(
            totalTv.parent as? View,
            paidTv.parent as? View,
            pendingTv.parent as? View
        ).filterNotNull()
        statCards.forEachIndexed { i, v -> AnimUtil.scaleIn(v, (180 + i * 70).toLong()) }
        runCatching { AnimUtil.slideUp(findViewById(R.id.btnPay), 420) }
        val tiles = listOf(
            R.id.btnHistory, R.id.btnCalendar,
            R.id.btnMyAcademies, R.id.btnContactTeacher
        ).mapNotNull { runCatching { findViewById<View>(it) }.getOrNull() }
        AnimUtil.staggerIn(tiles, 60)
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    @Serializable
    private data class EnrollmentStatusRow(val status: String)
    @Serializable
    private data class AcademyNameRow(val academy_name: String)
}