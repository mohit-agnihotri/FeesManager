package com.example.feesmanager.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.R
import com.example.feesmanager.ui.auth.RoleSelectActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.ui.fees.SetClassFeesActivity
import com.example.feesmanager.ui.settings.BackupActivity
import com.example.feesmanager.utils.AnimUtil.withBounce
import android.widget.Switch
import android.widget.TextView
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.ui.auth.SplashActivity
import com.example.feesmanager.utils.LocaleHelper
import com.example.feesmanager.utils.ThemeManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import android.content.Context

class TeacherSettingsFragment : Fragment(R.layout.fragment_teacher_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnEditProfile).withBounce {
            startActivity(Intent(requireContext(), com.example.feesmanager.ui.profile.EditProfileActivity::class.java))
        }

        view.findViewById<View>(R.id.btnSetFees).withBounce {
            startActivity(Intent(requireContext(), SetClassFeesActivity::class.java))
        }

        view.findViewById<View>(R.id.btnBackup).withBounce {
            startActivity(Intent(requireContext(), BackupActivity::class.java))
        }

        view.findViewById<View>(R.id.btnLogout).withBounce {
            AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Logout") { _, _ ->
                    lifecycleScope.launch {
                        SessionManager.logoutTeacher(requireContext())
                        startActivity(Intent(requireContext(), RoleSelectActivity::class.java))
                        requireActivity().finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        setupSettings(view)
    }

    private fun setupSettings(view: View) {
        val rowLanguage = view.findViewById<View>(R.id.rowLanguage)
        val tvCurrentLang = view.findViewById<TextView>(R.id.tvCurrentLang)
        val switchDarkMode = view.findViewById<Switch>(R.id.switchDarkMode)
        val switchNotif = view.findViewById<Switch>(R.id.switchNotifications)
        val switchSound = view.findViewById<Switch>(R.id.switchSound)
        val rowChangePassword = view.findViewById<View>(R.id.rowChangePassword)
        val rowLogoutAll = view.findViewById<View>(R.id.rowLogoutAll)

        val prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        val lang = LocaleHelper.getSavedLanguage(requireContext())
        tvCurrentLang.text = if (lang == "hi") "हिंदी" else "English"

        rowLanguage.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.choose_language))
                .setItems(arrayOf("English", "हिंदी (Hindi)")) { _, which ->
                    val newLang = if (which == 0) "en" else "hi"
                    LocaleHelper.setAndSave(requireContext(), newLang)
                    val intent = Intent(requireContext(), SplashActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    requireActivity().finish()
                }.show()
        }

        switchDarkMode.isChecked = ThemeManager.isDark(requireContext())
        switchDarkMode.setOnCheckedChangeListener { _, checked ->
            ThemeManager.toggle(requireContext())
            requireActivity().recreate()
        }

        switchNotif.isChecked = prefs.getBoolean("notif_enabled", true)
        switchNotif.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_enabled", checked).apply()
            Toast.makeText(requireContext(), if (checked) "Notifications enabled" else "Notifications disabled", Toast.LENGTH_SHORT).show()
        }

        switchSound.isChecked = prefs.getBoolean("notif_sound", true)
        switchSound.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("notif_sound", checked).apply()
        }

        rowChangePassword.setOnClickListener {
            val email = SupabaseManager.client.auth.currentUserOrNull()?.email
            if (email != null) {
                lifecycleScope.launch {
                    try {
                        SupabaseManager.client.auth.resetPasswordForEmail(email)
                        Toast.makeText(requireContext(), "Password reset link sent to $email ✅", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Failed to send reset email: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Not logged in", Toast.LENGTH_SHORT).show()
            }
        }

        rowLogoutAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout from all devices")
                .setMessage("This will sign you out everywhere. Continue?")
                .setPositiveButton("Logout") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            SupabaseManager.client.auth.signOut()
                            SessionManager.logoutTeacher(requireContext())
                            SessionManager.logoutStudent(requireContext())
                            startActivity(Intent(requireContext(), SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                            requireActivity().finish()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }
}
