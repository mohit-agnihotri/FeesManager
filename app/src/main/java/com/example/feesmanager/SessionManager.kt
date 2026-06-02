package com.example.feesmanager

import android.content.Context
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth

/**
 * SessionManager — Migrated to Supabase Auth.
 * Single source of truth for auth state and persistent session preferences.
 */
object SessionManager {

    /**
     * Checks if a teacher is currently logged into Supabase Auth and has a teacherId in prefs.
     */
    fun isTeacherLoggedIn(context: Context): Boolean {
        val user = SupabaseManager.client.auth.currentUserOrNull() ?: return false
        val teacherId = SecurePrefs.get(context, "app").getString("teacherId", null)
        return teacherId != null && teacherId == user.id
    }

    /**
     * Checks if a student is currently logged into Supabase Auth and has a studentId in prefs.
     */
    fun isStudentLoggedIn(context: Context): Boolean {
        val user = SupabaseManager.client.auth.currentUserOrNull() ?: return false
        val studentId = SecurePrefs.get(context, "student").getString("studentId", null)
        return studentId != null && studentId == user.id
    }

    fun saveTeacherSession(context: Context, teacherId: String, joinCode: String, email: String) {
        SecurePrefs.get(context, "app").edit().apply {
            putString("teacherId", teacherId)
            putString("role",      "teacher")
            putString("joinCode",  joinCode)
            putString("email",     email)
            apply()
        }
    }

    fun saveStudentSession(context: Context, teacherId: String, studentId: String,
                            studentName: String = "", email: String = "", className: String = "") {
        SecurePrefs.get(context, "student").edit().apply {
            putString("teacherId", teacherId)
            putString("studentId", studentId)
            if (studentName.isNotEmpty()) putString("studentName", studentName)
            if (email.isNotEmpty()) putString("email", email)
            if (className.isNotEmpty()) putString("className", className)
            apply()
        }
    }

    /** Update just the student details (name/class) without touching IDs */
    fun updateStudentDetails(context: Context, name: String, className: String) {
        SecurePrefs.get(context, "student").edit().apply {
            putString("studentName", name)
            putString("className", className)
            apply()
        }
    }

    fun getTeacherId(context: Context): String? =
        SecurePrefs.get(context, "app").getString("teacherId", null)

    fun getStudentId(context: Context): String? =
        SecurePrefs.get(context, "student").getString("studentId", null)

    fun getStudentTeacherId(context: Context): String? =
        SecurePrefs.get(context, "student").getString("teacherId", null)

    fun getTeacherName(context: Context): String? =
        SecurePrefs.get(context, "app").getString("teacherName", null)

    fun getStudentName(context: Context): String? =
        SecurePrefs.get(context, "student").getString("studentName", null)

    fun getStudentEmail(context: Context): String? =
        SecurePrefs.get(context, "student").getString("email", null)

    /**
     * Logs out the teacher: clears local prefs and signs out of Supabase Auth.
     */
    suspend fun logoutTeacher(context: Context) {
        SecurePrefs.get(context, "app").edit().clear().apply()
        SupabaseManager.client.auth.signOut()
    }

    /**
     * Logs out the student: clears local student prefs and signs out of Supabase Auth.
     */
    suspend fun logoutStudent(context: Context) {
        SecurePrefs.get(context, "student").edit().clear().apply()
        SupabaseManager.client.auth.signOut()
    }

    fun getRole(context: Context): String? =
        SecurePrefs.get(context, "app").getString("role", null)

    fun setRole(context: Context, role: String) {
        SecurePrefs.get(context, "app").edit().putString("role", role).apply()
    }
}
