package com.example.feesmanager.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.ui.dashboard.DashboardActivity
import com.example.feesmanager.R
import com.example.feesmanager.ui.dashboard.StudentDashboardActivity
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.utils.LocaleHelper
import com.example.feesmanager.utils.ThemeManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * SplashActivity — App entry point.
 *
 * Auto-login routing (no more role picker for returning users):
 *   1. First launch ever → LanguageSelectActivity
 *   2. Active session + role = "teacher" + teacherId saved → DashboardActivity
 *   3. Active session + role = "student" + studentId saved → StudentDashboardActivity
 *   4. No session or incomplete session → LoginActivity
 */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyFromPref(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            routeUser()
        }, 1500)
    }

    private fun routeUser() {
        // ── First launch: show language picker ────────────────────────────────
        if (LocaleHelper.isFirstLaunch(this)) {
            startActivity(Intent(this, LanguageSelectActivity::class.java))
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                // Wait for Supabase to restore the session from local storage
                SupabaseManager.client.auth.awaitInitialization()
            } catch (e: Exception) {
                // ignore
            }

            val session    = SupabaseManager.client.auth.currentSessionOrNull()
            val savedRole  = SessionManager.getRole(this@SplashActivity)

        // ── Returning teacher ─────────────────────────────────────────────────
        if (session != null && savedRole == "teacher") {
            val teacherId = SessionManager.getTeacherId(this@SplashActivity)
            if (!teacherId.isNullOrEmpty() && session.user?.id == teacherId) {
                startActivity(Intent(this@SplashActivity, DashboardActivity::class.java))
                finish()
                return@launch
            }
        }

        // ── Returning student ─────────────────────────────────────────────────
        if (session != null && savedRole == "student") {
            val studentId = SessionManager.getStudentId(this@SplashActivity)
            val teacherId = SessionManager.getStudentTeacherId(this@SplashActivity)
            if (!studentId.isNullOrEmpty() && !teacherId.isNullOrEmpty()
                && session.user?.id == studentId) {
                // Student is logged in — go to their last known state
                startActivity(Intent(this@SplashActivity, StudentDashboardActivity::class.java))
                finish()
                return@launch
            }
        }

        // ── No valid session — go to unified login ────────────────────────────
        startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
        finish()
        }
    }
}