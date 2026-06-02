package com.example.feesmanager

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * LocaleHelper — applies a saved language locale to any Context.
 * Called in every Activity's attachBaseContext() to apply language app-wide.
 *
 * Supported: "en" (English), "hi" (Hindi)
 */
object LocaleHelper {

    private const val PREF_LANGUAGE = "selected_language"
    private const val DEFAULT_LANG  = "en"

    fun setLocale(context: Context): Context {
        val lang = getSavedLanguage(context)
        return applyLocale(context, lang)
    }

    fun setAndSave(context: Context, lang: String): Context {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().putString(PREF_LANGUAGE, lang).apply()
        return applyLocale(context, lang)
    }

    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString(PREF_LANGUAGE, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    fun isFirstLaunch(context: Context): Boolean {
        return context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .getString(PREF_LANGUAGE, null) == null
    }

    private fun applyLocale(context: Context, lang: String): Context {
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
