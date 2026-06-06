package com.example.feesmanager.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlin.collections.iterator

/**
 * SecurePrefs — wraps EncryptedSharedPreferences for sensitive data.
 * Falls back to regular SharedPreferences on older devices.
 *
 * Usage:
 *   SecurePrefs.get(context, "student").getString("teacherId", null)
 *   SecurePrefs.get(context, "app").edit().putString("role","teacher").apply()
 */
object SecurePrefs {

    private val cache = mutableMapOf<String, SharedPreferences>()

    fun get(context: Context, name: String): SharedPreferences {
        cache[name]?.let { return it }

        val prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_$name",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback on older/rooted devices
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
        }

        cache[name] = prefs
        return prefs
    }

    /** Migrate existing plaintext prefs into encrypted store (run once on app upgrade) */
    fun migrateIfNeeded(context: Context, name: String) {
        val oldPrefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        if (oldPrefs.all.isEmpty()) return

        val newPrefs = get(context, name)
        if (newPrefs.all.isNotEmpty()) return // already migrated

        val editor = newPrefs.edit()
        for ((key, value) in oldPrefs.all) {
            when (value) {
                is String  -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int     -> editor.putInt(key, value)
                is Long    -> editor.putLong(key, value)
                is Float   -> editor.putFloat(key, value)
                else       -> {}
            }
        }
        editor.apply()
        oldPrefs.edit().clear().apply() // clear old plaintext
    }
}