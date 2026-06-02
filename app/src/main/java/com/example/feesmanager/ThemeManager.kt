package com.example.feesmanager

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {

    fun isDark(context: Context): Boolean {
        return context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE).getBoolean("is_dark", true)
    }

    fun toggle(context: Context) {
        val current = isDark(context)
        context.getSharedPreferences("theme_settings", Context.MODE_PRIVATE).edit().putBoolean("is_dark", !current).apply()
        apply(!current)
    }

    fun apply(isDark: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun applyFromPref(context: Context) {
        apply(isDark(context))
    }
}
