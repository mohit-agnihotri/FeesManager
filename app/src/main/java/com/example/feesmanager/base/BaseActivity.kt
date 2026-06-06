package com.example.feesmanager.base

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.feesmanager.utils.ThemeManager
import com.example.feesmanager.utils.LocaleHelper

/**
 * BaseActivity — all Activities extend this.
 * Handles:
 *   1. Locale injection (language switching)
 *   2. Theme application from prefs
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        // Apply saved language to every screen automatically
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dark/light theme before super
        ThemeManager.applyFromPref(this)
        super.onCreate(savedInstanceState)
    }
}