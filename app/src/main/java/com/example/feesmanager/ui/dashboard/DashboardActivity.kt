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
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class DashboardActivity : BaseActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private val profileImageViewModel: ProfileImageViewModel by viewModels()

    private lateinit var academyName: TextView
    private lateinit var teacherName: TextView
    private lateinit var joinCodeText: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var ivTeacherAvatar: ImageView
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    private var currentTeacherId = ""
    private var currentAcademyName = ""

    fun getCurrentTeacherId() = currentTeacherId
    fun getCurrentAcademyName() = currentAcademyName

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { profileImageViewModel.uploadAvatar(this, currentTeacherId, it) }
    }

    fun launchImagePicker() {
        pickImageLauncher.launch("image/*")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        val supabaseSession = SupabaseManager.client.auth.currentSessionOrNull()
        val currentUid = supabaseSession?.user?.id
        val savedTeacherId = SessionManager.getTeacherId(this)

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

        academyName = findViewById(R.id.tvAcademyName)
        teacherName = findViewById(R.id.tvTeacherName)
        joinCodeText = findViewById(R.id.tvJoinCode)
        drawerLayout = findViewById(R.id.drawerLayout)
        ivTeacherAvatar = findViewById(R.id.ivTeacherAvatar)
        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        setupViewPagerAndNav()
        setupProfileImage()
        NotificationHelper.saveToken()

        lifecycleScope.launch {
            val count = UnreadBadgeHelper.fetchTotalUnreadForTeacher(this@DashboardActivity, currentTeacherId)
            val badge = bottomNav.getOrCreateBadge(R.id.nav_chat)
            badge.isVisible = count > 0
            badge.number = count
        }

        lifecycleScope.launch {
            try { FeesRepository().performMonthlyRollover(currentTeacherId) {} } catch (_: Exception) {}
        }

        viewModel.profile.observe(this) { result ->
            if (result is FmResult.Success) {
                val (tName, aName, joinCode) = result.content
                currentAcademyName = aName
                academyName.text = aName
                teacherName.text = "By $tName"
                joinCodeText.text = "Join Code: $joinCode"
                SecurePrefs.get(this, "app").edit().putString("teacherName", tName).apply()
                val drawerView = findViewById<View>(R.id.navDrawerView)
                if (drawerView != null) DrawerHelper.setupTeacher(this, drawerLayout, drawerView, tName, aName)
            }
        }
        viewModel.loadDashboard(currentUid)

        findViewById<View>(R.id.btnHamburger).withBounce { DrawerHelper.openDrawer(drawerLayout) }
        
        runCatching {
            val fab = findViewById<View>(R.id.fabAiAssistant)
            fab.withBounce { startActivity(Intent(this, TeacherAiActivity::class.java)) }
            AnimUtil.scaleIn(fab, 600)
        }

        runCatching {
            findViewById<View>(R.id.btnJoinRequests)?.withBounce {
                startActivity(Intent(this, com.example.feesmanager.ui.student.PendingStudentsActivity::class.java)
                    .putExtra("mode", "requests"))
            }
        }
    }

    private fun setupViewPagerAndNav() {
        val adapter = DashboardPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = true // Enable swipe

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.menu.getItem(position).isChecked = true
            }
        })

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> viewPager.currentItem = 0
                R.id.nav_students -> viewPager.currentItem = 1
                R.id.nav_chat -> viewPager.currentItem = 2
                R.id.nav_settings -> viewPager.currentItem = 3
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentTeacherId.isNotEmpty()) {
            viewModel.loadDashboard(currentTeacherId)
            profileImageViewModel.loadAvatar(currentTeacherId)
            lifecycleScope.launch {
                val count = UnreadBadgeHelper.fetchTotalUnreadForTeacher(this@DashboardActivity, currentTeacherId)
                val badge = bottomNav.getOrCreateBadge(R.id.nav_chat)
                badge.isVisible = count > 0
                badge.number = count
            }
        }
    }

    private fun setupProfileImage() {
        profileImageViewModel.loadAvatar(currentTeacherId)
        profileImageViewModel.avatarUrl.observe(this) { url -> 
            GlideHelper.loadAvatar(ivTeacherAvatar, url)
            findViewById<ImageView>(R.id.ivDrawerProfile)?.let { GlideHelper.loadAvatar(it, url) }
        }
        ivTeacherAvatar.setOnClickListener { pickImageLauncher.launch("image/*") }
        profileImageViewModel.uploadResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show()
                is FmResult.Success -> {
                    GlideHelper.loadAvatarFresh(ivTeacherAvatar, result.content)
                    Toast.makeText(this, "Profile photo updated", Toast.LENGTH_SHORT).show()
                }
                is FmResult.Error -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
