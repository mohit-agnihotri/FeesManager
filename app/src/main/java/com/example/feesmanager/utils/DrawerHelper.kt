package com.example.feesmanager.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.ui.advances.AdvancePaymentActivity
import com.example.feesmanager.ui.analytics.AnalyticsActivity
import com.example.feesmanager.ui.announcements.AnnouncementsActivity
import com.example.feesmanager.ui.chat.ClassSelectChatActivity
import com.example.feesmanager.ui.fees.FeeCalendarActivity
import com.example.feesmanager.ui.fees.FeesEntryActivity
import com.example.feesmanager.ui.fees.HistoryActivity
import com.example.feesmanager.ui.chat.MessageActivity
import com.example.feesmanager.ui.settings.MultiAcademyActivity
import com.example.feesmanager.R
import com.example.feesmanager.ui.settings.SettingsActivity
import com.example.feesmanager.ui.auth.SetupProfileActivity
import com.example.feesmanager.ai.ui.TeacherAiActivity
import com.example.feesmanager.ui.student.ViewStudentsActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.ui.auth.SplashActivity
import kotlinx.coroutines.launch

object DrawerHelper {

    fun setupTeacher(
        activity: Activity,
        drawer: DrawerLayout,
        drawerView: View,
        teacherName: String,
        academyName: String
    ) {
        runCatching { drawerView.findViewById<TextView>(R.id.drawerTeacherName).text = teacherName }
        runCatching { drawerView.findViewById<TextView>(R.id.drawerAcademyName).text = academyName }

        fun nav(id: Int, block: () -> Unit) {
            runCatching {
                drawerView.findViewById<View>(id).setOnClickListener {
                    drawer.closeDrawer(Gravity.START)
                    it.postDelayed({ block() }, 250)
                }
            }
        }

        nav(R.id.navProfile)       { activity.startActivity(
            Intent(
                activity,
                SetupProfileActivity::class.java
            )
        ) }
        nav(R.id.navStudents)      { activity.startActivity(
            Intent(
                activity,
                ViewStudentsActivity::class.java
            )
        ) }
        nav(R.id.navPayments)      { activity.startActivity(
            Intent(
                activity,
                FeesEntryActivity::class.java
            )
        ) }
        nav(R.id.navAnalytics)     { activity.startActivity(
            Intent(
                activity,
                AnalyticsActivity::class.java
            )
        ) }
        nav(R.id.navNotifications) { activity.startActivity(
            Intent(
                activity,
                AnnouncementsActivity::class.java
            ).putExtra("mode","teacher")) }
        nav(R.id.navSettings)      { activity.startActivity(
            Intent(
                activity,
                SettingsActivity::class.java
            )
        ) }
        nav(R.id.navSupport)       { activity.startActivity(
            Intent(
                activity,
                ClassSelectChatActivity::class.java
            )
        ) }
        nav(R.id.navAiAssistant)   { activity.startActivity(
            Intent(
                activity,
                TeacherAiActivity::class.java
            )
        ) }
        nav(R.id.navLogout) {
            if (activity is AppCompatActivity) {
                activity.lifecycleScope.launch {
                    SessionManager.logoutTeacher(activity)
                    activity.startActivity(
                        Intent(activity, SplashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    activity.finish()
                }
            }
        }

        // Language switch in drawer (reuse navAbout slot if exists, else navSettings long press)
        runCatching {
            drawerView.findViewById<View>(R.id.navLanguage).setOnClickListener {
                drawer.closeDrawer(Gravity.START)
                it.postDelayed({ showLanguagePicker(activity) }, 250)
            }
        }

        setNavItem(drawerView, R.id.navProfile,       "👤", activity.getString(R.string.nav_profile))
        setNavItem(drawerView, R.id.navStudents,      "👥", activity.getString(R.string.students))
        setNavItem(drawerView, R.id.navPayments,      "💰", activity.getString(R.string.nav_payments_dashboard))
        setNavItem(drawerView, R.id.navAnalytics,     "📊", activity.getString(R.string.analytics))
        setNavItem(drawerView, R.id.navNotifications, "🔔", activity.getString(R.string.notifications))
        setNavItem(drawerView, R.id.navSettings,      "⚙️", activity.getString(R.string.settings))
        setNavItem(drawerView, R.id.navSupport,       "💬", "Chat / Messages")
        setNavItem(drawerView, R.id.navAiAssistant,  "🤖", "AI Assistant")
        runCatching { setNavItem(drawerView, R.id.navLanguage, "🌐", "Language / भाषा") }
    }

    fun setupStudent(
        activity: Activity,
        drawer: DrawerLayout,
        drawerView: View,
        studentName: String,
        studentClass: String,
        academyName: String = ""
    ) {
        runCatching { drawerView.findViewById<TextView>(R.id.drawerStudentName).text = studentName }
        runCatching { drawerView.findViewById<TextView>(R.id.drawerStudentClass).text = studentClass }
        if (academyName.isNotEmpty()) {
            runCatching { drawerView.findViewById<TextView>(R.id.drawerAcademyName).text = academyName }
        }

        val teacherId = SessionManager.getStudentTeacherId(activity) ?: ""
        val studentId = SessionManager.getStudentId(activity) ?: ""

        fun nav(id: Int, block: () -> Unit) {
            runCatching {
                drawerView.findViewById<View>(id).setOnClickListener {
                    drawer.closeDrawer(Gravity.START)
                    it.postDelayed({ block() }, 250)
                }
            }
        }

        nav(R.id.navProfile) {
            AlertDialog.Builder(activity)
                .setTitle("My Profile")
                .setMessage("Name: $studentName\nClass: $studentClass\n\nFor changes, contact your teacher.")
                .setPositiveButton("OK", null).show()
        }
        nav(R.id.navAcademies)      { activity.startActivity(
            Intent(
                activity,
                MultiAcademyActivity::class.java
            )
        ) }
        nav(R.id.navPayHistory)     { activity.startActivity(
            Intent(
                activity,
                HistoryActivity::class.java
            )
        ) }
        nav(R.id.navPendingFees)    { activity.startActivity(
            Intent(
                activity,
                FeeCalendarActivity::class.java
            )
        ) }
        nav(R.id.navAdvanceBalance) { activity.startActivity(
            Intent(
                activity,
                AdvancePaymentActivity::class.java
            )
        ) }
        nav(R.id.navNotifications)  { activity.startActivity(
            Intent(
                activity,
                AnnouncementsActivity::class.java
            ).putExtra("mode","student")) }
        nav(R.id.navSettings)       { activity.startActivity(
            Intent(
                activity,
                SettingsActivity::class.java
            )
        ) }


        // Language switch
        runCatching {
            drawerView.findViewById<View>(R.id.navLanguage).setOnClickListener {
                drawer.closeDrawer(Gravity.START)
                it.postDelayed({ showLanguagePicker(activity) }, 250)
            }
        }

        // Contact Teacher (personal chat)
        nav(R.id.navAbout) {
            if (teacherId.isNotEmpty() && studentId.isNotEmpty()) {
                activity.startActivity(
                    Intent(activity, MessageActivity::class.java)
                    .putExtra("mode",        "student")
                    .putExtra("teacherId",   teacherId)
                    .putExtra("studentId",   studentId)
                    .putExtra("studentName", studentName))
            }
        }
        nav(R.id.navLogout) {
            if (activity is AppCompatActivity) {
                activity.lifecycleScope.launch {
                    SessionManager.logoutStudent(activity)
                    activity.startActivity(
                        Intent(activity, SplashActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
                    activity.finish()
                }
            }
        }

        setNavItem(drawerView, R.id.navProfile,        "👤", activity.getString(R.string.nav_profile))
        setNavItem(drawerView, R.id.navAcademies,      "🏫", activity.getString(R.string.my_academies))
        setNavItem(drawerView, R.id.navPayHistory,     "📜", activity.getString(R.string.nav_payment_history))
        setNavItem(drawerView, R.id.navPendingFees,    "⏳", activity.getString(R.string.nav_pending_fees))
        setNavItem(drawerView, R.id.navAdvanceBalance, "💳", activity.getString(R.string.nav_advance_balance))
        setNavItem(drawerView, R.id.navNotifications,  "🔔", activity.getString(R.string.notifications))
        setNavItem(drawerView, R.id.navSettings,       "⚙️", activity.getString(R.string.settings))

        setNavItem(drawerView, R.id.navAbout,          "💬", "Contact Teacher")
        runCatching { setNavItem(drawerView, R.id.navLanguage, "🌐", "Language / भाषा") }
    }

    fun openDrawer(drawer: DrawerLayout) = drawer.openDrawer(Gravity.START)

    /** Inline language picker — no new activity needed */
    fun showLanguagePicker(activity: Activity) {
        val current = LocaleHelper.getSavedLanguage(activity)
        val options = arrayOf("🇬🇧  English", "🇮🇳  हिंदी (Hindi)")
        val checked = if (current == "hi") 1 else 0
        AlertDialog.Builder(activity)
            .setTitle("Language / भाषा")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                val newLang = if (which == 0) "en" else "hi"
                if (newLang != current) {
                    dialog.dismiss()
                    LocaleHelper.setAndSave(activity, newLang)
                    // Restart to apply locale
                    val intent = Intent(activity, SplashActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    activity.finish()
                } else {
                    dialog.dismiss()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun setNavItem(parent: View, id: Int, icon: String, label: String) {
        runCatching {
            val item = parent.findViewById<View>(id) ?: return
            item.findViewById<TextView>(R.id.navItemIcon).text  = icon
            item.findViewById<TextView>(R.id.navItemLabel).text = label
        }
    }
}