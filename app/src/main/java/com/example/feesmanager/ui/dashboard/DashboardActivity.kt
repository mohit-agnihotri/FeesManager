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
import com.example.feesmanager.ui.announcements.AnnouncementsActivity
import com.example.feesmanager.ui.attendance.AttendanceActivity
import com.example.feesmanager.ui.advances.AdvanceStudentsActivity
import com.example.feesmanager.ui.analytics.AnalyticsActivity
import com.example.feesmanager.ui.student.AddStudentActivity
import com.example.feesmanager.ui.settings.BackupActivity
import com.example.feesmanager.ui.payment.CashfreeOnboardingActivity
import com.example.feesmanager.ui.chat.ChatHubActivity
import com.example.feesmanager.ui.fees.FeesEntryActivity
import com.example.feesmanager.ui.student.PendingStudentsActivity
import com.example.feesmanager.R
import com.example.feesmanager.ui.fees.SetClassFeesActivity
import com.example.feesmanager.ui.student.ViewStudentsActivity
import com.example.feesmanager.ai.ui.TeacherAiActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.repository.FeesRepository
import com.example.feesmanager.ui.auth.MainActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.ui.profile.ProfileImageViewModel
import com.example.feesmanager.utils.AnimUtil
import com.example.feesmanager.utils.AnimUtil.withBounce
import com.example.feesmanager.utils.DrawerHelper
import com.example.feesmanager.utils.GlideHelper
import com.example.feesmanager.utils.NotificationHelper
import com.example.feesmanager.utils.SecurePrefs
import com.example.feesmanager.utils.UnreadBadgeHelper
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

class DashboardActivity : BaseActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val profileImageViewModel: ProfileImageViewModel by viewModels()

    private lateinit var totalStudents  : TextView
    private lateinit var totalFees      : TextView
    private lateinit var collected      : TextView
    private lateinit var pending        : TextView
    private lateinit var academyName    : TextView
    private lateinit var teacherName    : TextView
    private lateinit var joinCodeText   : TextView
    private lateinit var drawerLayout   : DrawerLayout
    private lateinit var ivTeacherAvatar: ImageView

    private var currentTeacherId = ""

    @Serializable
    private data class CashfreeStatusRow(
        val vendor_status: String? = null,
        val kyc_status:    String? = null
    )

    // Cashfree payment setup launcher
    private val cashfreeSetupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                runCatching {
                    val tvStatus = findViewById<TextView>(R.id.tvPaymentStatus)
                    refreshPaymentStatus(tvStatus)
                }
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { profileImageViewModel.uploadAvatar(this, currentTeacherId, it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val supabaseSession = SupabaseManager.client.auth.currentSessionOrNull()
        val currentUid      = supabaseSession?.user?.id
        val savedTeacherId  = SessionManager.getTeacherId(this)

        if (currentUid == null || currentUid != savedTeacherId) {
            Toast.makeText(this, "Session expired. Login again.", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                SessionManager.logoutTeacher(this@DashboardActivity)
                startActivity(Intent(this@DashboardActivity, MainActivity::class.java))
                finish()
            }
            return
        }

        currentTeacherId = currentUid

        totalStudents   = findViewById(R.id.tvTotalStudents)
        totalFees       = findViewById(R.id.tvTotalFees)
        collected       = findViewById(R.id.tvCollected)
        pending         = findViewById(R.id.tvPending)
        academyName     = findViewById(R.id.tvAcademyName)
        teacherName     = findViewById(R.id.tvTeacherName)
        joinCodeText    = findViewById(R.id.tvJoinCode)
        drawerLayout    = findViewById(R.id.drawerLayout)
        ivTeacherAvatar = findViewById(R.id.ivTeacherAvatar)

        NotificationHelper.saveToken()
        setupButtons()

        // Load unread badge on Chat button (refreshed every time)
        lifecycleScope.launch {
            val count = UnreadBadgeHelper.fetchTotalUnreadForTeacher(this@DashboardActivity, currentTeacherId)
            runCatching {
                val chatBtn = findViewById<View>(R.id.btnStudentQueries)
                if (chatBtn != null) UnreadBadgeHelper.addBadge(this@DashboardActivity, chatBtn, count)
            }
        }

        // Auto-trigger monthly rollover on dashboard load
        lifecycleScope.launch {
            try { FeesRepository().performMonthlyRollover(currentTeacherId) { _ -> }
            } catch (_: Exception) {}
        }

        setupProfileImage()

        viewModel.stats.observe(this) { result ->
            when (result) {
                is FmResult.Success -> {
                    val s = result.content
                    totalStudents.text = "${s.totalStudents}"
                    totalFees.text     = "₹${s.totalCollectedFees + s.totalPendingFees}"
                    collected.text     = "₹${s.totalCollectedFees}"
                    pending.text       = "₹${s.totalPendingFees}"

                    // Update join requests badge
                    val badge = findViewById<TextView>(R.id.tvJoinRequestsBadge)
                    if (badge != null) {
                        if (s.joinPending > 0) {
                            badge.visibility = View.VISIBLE
                            badge.text = if (s.joinPending > 9) "9+" else "${s.joinPending}"
                        } else {
                            badge.visibility = View.GONE
                        }
                    }
                }
                is FmResult.Error   -> toast("Failed to load stats: ${result.message}")
                is FmResult.Loading -> { /* no-op */ }
            }
        }

        viewModel.profile.observe(this) { result ->
            if (result is FmResult.Success) {
                val (tName, aName, joinCode) = result.content
                academyName.text  = aName
                teacherName.text  = "By $tName"
                joinCodeText.text = "Join Code: $joinCode"
                SecurePrefs.get(this, "app").edit().putString("teacherName", tName).apply()
                val drawerView = runCatching { findViewById<View>(R.id.navDrawerView) }.getOrNull()
                if (drawerView != null)
                    DrawerHelper.setupTeacher(this, drawerLayout, drawerView, tName, aName)
            }
        }
        // Load dashboard stats initially
        viewModel.loadDashboard(currentUid)
    }

    override fun onResume() {
        super.onResume()
        if (currentTeacherId.isNotEmpty()) {
            viewModel.loadDashboard(currentTeacherId)

            // Refresh payment status badge
            val tvStatus = findViewById<TextView>(R.id.tvPaymentStatus)
            if (tvStatus != null) refreshPaymentStatus(tvStatus)

            // Refresh unread badge on chat button
            lifecycleScope.launch {
                val count = UnreadBadgeHelper.fetchTotalUnreadForTeacher(this@DashboardActivity, currentTeacherId)
                runCatching {
                    val chatBtn = findViewById<View>(R.id.btnStudentQueries)
                    if (chatBtn != null) UnreadBadgeHelper.addBadge(this@DashboardActivity, chatBtn, count)
                }
            }
        }
    }
    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(currentTeacherId)
        profileImageViewModel.avatarUrl.observe(this) { url ->
            GlideHelper.loadAvatar(ivTeacherAvatar, url)
        }
        ivTeacherAvatar.setOnClickListener { pickImageLauncher.launch("image/*") }
        profileImageViewModel.uploadResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> toast("Uploading photo...")
                is FmResult.Success -> {
                    GlideHelper.loadAvatarFresh(ivTeacherAvatar, result.content)
                    toast("Profile photo updated ✅.")
                }
                is FmResult.Error -> toast("Upload failed: ${result.message}")
            }
        }
    }

    private fun setupButtons() {
        fun go(id: Int, cls: Class<*>) = runCatching {
            findViewById<View>(id).withBounce { startActivity(Intent(this, cls)) }
        }

        go(R.id.addStudentBtn,      AddStudentActivity::class.java)
        go(R.id.viewStudentBtn,     ViewStudentsActivity::class.java)
        go(R.id.feesBtn,            FeesEntryActivity::class.java)
        go(R.id.btnSetFees,         SetClassFeesActivity::class.java)
        go(R.id.btnAnalytics,       AnalyticsActivity::class.java)
        go(R.id.btnAttendance,      AttendanceActivity::class.java)
        go(R.id.btnBackup,          BackupActivity::class.java)
        go(R.id.btnAdvanceStudents, AdvanceStudentsActivity::class.java)

        // Chat button — opens ChatHubActivity
        runCatching {
            findViewById<View>(R.id.btnStudentQueries).withBounce {
                startActivity(Intent(this, ChatHubActivity::class.java))
            }
        }

        // Payment Connect button
        runCatching {
            val btnPayment = findViewById<View>(R.id.btnPaymentConnect)
            val tvStatus   = findViewById<TextView>(R.id.tvPaymentStatus)

            refreshPaymentStatus(tvStatus)

            btnPayment?.setOnClickListener {
                cashfreeSetupLauncher.launch(
                    Intent(this, CashfreeOnboardingActivity::class.java)
                )
            }
        }

        runCatching {
            findViewById<View>(R.id.btnHamburger).withBounce {
                DrawerHelper.openDrawer(drawerLayout)
            }
        }
        runCatching {
            findViewById<View>(R.id.btnAnnouncements).withBounce {
                startActivity(Intent(this, AnnouncementsActivity::class.java).putExtra("mode", "teacher"))
            }
        }
        pending.setOnClickListener {
            AnimUtil.bounce(it)
            startActivity(Intent(this, PendingStudentsActivity::class.java).putExtra("mode", "defaulters"))
        }
        runCatching {
            findViewById<View>(R.id.btnJoinRequests).withBounce {
                startActivity(Intent(this, PendingStudentsActivity::class.java).putExtra("mode", "requests"))
            }
        }
        runCatching {
            val fab = findViewById<View>(R.id.fabAiAssistant)
            fab.withBounce { startActivity(Intent(this, TeacherAiActivity::class.java)) }
            AnimUtil.scaleIn(fab, 600)
        }
    }

    /** Cashfree vendor status */
    private fun refreshPaymentStatus(tvStatus: TextView?) {
        lifecycleScope.launch {
            try {
                val row = SupabaseManager.client.postgrest
                    .from("teachers")
                    .select(Columns.Companion.raw("vendor_status, kyc_status")) {
                        filter { eq("id", currentTeacherId) }
                    }.decodeSingleOrNull<CashfreeStatusRow>()
                val isActive = row?.vendor_status == "ACTIVE"
                val label    = when {
                    isActive                    -> "Payments ✅ Active"
                    row?.vendor_status == "IN_BENE_CREATION" -> "Verifying Bank..."
                    row?.vendor_status == "not_started" || row?.vendor_status == null -> "Setup Payments"
                    else                        -> "Setup Payments"
                }
                tvStatus?.text = label
                tvStatus?.setTextColor(if (isActive) 0xFF22C55E.toInt() else 0xFF94A3B8.toInt())
            } catch (_: Exception) {}
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}