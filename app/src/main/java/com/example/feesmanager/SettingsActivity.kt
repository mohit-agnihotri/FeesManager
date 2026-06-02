package com.example.feesmanager

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/**
 * SettingsActivity — Common to both Teacher and Student roles.
 * Migrated to Supabase Auth.
 * Features: Language switch, Dark/Light mode, Notifications, Change password, Logout all.
 */
class SettingsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val rowLanguage       = findViewById<android.view.View>(R.id.rowLanguage)
        val tvCurrentLang     = findViewById<TextView>(R.id.tvCurrentLang)
        val switchDarkMode    = findViewById<Switch>(R.id.switchDarkMode)
        val switchNotif       = findViewById<Switch>(R.id.switchNotifications)
        val switchSound       = findViewById<Switch>(R.id.switchSound)
        val rowChangePassword = findViewById<android.view.View>(R.id.rowChangePassword)
        val rowLogoutAll      = findViewById<android.view.View>(R.id.rowLogoutAll)

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // ── Current language display ─────────────────────────────
        val lang = LocaleHelper.getSavedLanguage(this)
        tvCurrentLang.text = if (lang == "hi") "हिंदी" else "English"

        // ── Language picker ──────────────────────────────────────
        rowLanguage.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_language))
                .setItems(arrayOf("English", "हिंदी (Hindi)")) { _, which ->
                    val newLang = if (which == 0) "en" else "hi"
                    LocaleHelper.setAndSave(this, newLang)
                    // Recreate entire app stack to apply language
                    val intent = Intent(this, SplashActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    finish()
                }.show()
        }

        // ── Dark mode ────────────────────────────────────────────
        switchDarkMode.isChecked = ThemeManager.isDark(this)
        switchDarkMode.setOnCheckedChangeListener { _, checked ->
            ThemeManager.toggle(this)
            recreate()
        }

        // ── Notification toggles ─────────────────────────────────
        switchNotif.isChecked = prefs.getBoolean("notif_enabled", true)
        switchNotif.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_enabled", checked).apply()
            toast(if (checked) "Notifications enabled" else "Notifications disabled")
        }

        switchSound.isChecked = prefs.getBoolean("notif_sound", true)
        switchSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_sound", checked).apply()
        }

        // ── Change password (Supabase Reset Password) ────────────
        rowChangePassword.setOnClickListener {
            val email = SupabaseManager.client.auth.currentUserOrNull()?.email
            if (email != null) {
                lifecycleScope.launch {
                    try {
                        SupabaseManager.client.auth.resetPasswordForEmail(email)
                        toast("Password reset link sent to $email ✅")
                    } catch (e: Exception) {
                        toast("Failed to send reset email: ${e.message}")
                    }
                }
            } else {
                toast("Not logged in")
            }
        }

        // ── Logout all devices ───────────────────────────────────
        rowLogoutAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout from all devices")
                .setMessage("This will sign you out everywhere. Continue?")
                .setPositiveButton("Logout") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            // Supabase signOut() with Scope.GLOBAL will sign out of all sessions
                            SupabaseManager.client.auth.signOut()
                            SessionManager.logoutTeacher(this@SettingsActivity)
                            SessionManager.logoutStudent(this@SettingsActivity)
                            
                            startActivity(Intent(this@SettingsActivity, SplashActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                            finish()
                        } catch (e: Exception) {
                            toast("Logout failed: ${e.message}")
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
