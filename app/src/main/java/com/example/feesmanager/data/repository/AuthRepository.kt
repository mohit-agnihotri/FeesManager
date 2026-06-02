package com.example.feesmanager.data.repository

import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * AuthRepository — Centralized authentication and profile management.
 * Aligned with Supabase 3.x API and the deployed SQL schema.
 */
class AuthRepository {

    private val auth = SupabaseManager.client.auth
    private val db = SupabaseManager.client.postgrest

    /**
     * Returns the current authenticated user's ID, or null if not logged in.
     */
    fun getCurrentUserId(): String? =
        auth.currentUserOrNull()?.id

    suspend fun login(email: String, pass: String) =
        auth.signInWith(Email) {
            this.email = email
            this.password = pass
        }

    /**
     * Signs up a new user. The DB trigger `handle_new_user` auto-creates the profile row.
     * We then update the profile with full_name and role, and create teacher record if needed.
     */
    suspend fun signUp(email: String, pass: String, fullName: String, role: String) {
        try {
            // 1. Sign up user in Supabase Auth (triggers profile creation)
            auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }

            // 2. Update the auto-created profile with name and role
            val user = auth.currentUserOrNull() ?: throw Exception("Auth session not found")
            db.from("profiles").update(mapOf(
                "full_name" to fullName,
                "role" to role
            )) {
                filter { eq("id", user.id) }
            }

            // 3. Create entry in `teachers` table if role == teacher
            if (role == "teacher") {
                val code = generateUniqueJoinCode()
                db.from("teachers").insert(mapOf(
                    "id" to user.id,
                    "academy_name" to "New Academy",
                    "join_code" to code
                ))
            }
        } catch (e: Exception) {
            try {
                auth.signOut()
            } catch (_: Exception) {}
            throw e
        }
    }

    private suspend fun generateUniqueJoinCode(): String {
        var attempts = 0
        while (attempts < 5) {
            val code = (100000..999999).random().toString()

            val existing = db.from("teachers")
                .select(Columns.raw("join_code")) {
                    filter { eq("join_code", code) }
                }.decodeSingleOrNull<JoinCodeRow>()

            if (existing == null) return code
            attempts++
        }

        return UUID.randomUUID().toString().substring(0, 8).uppercase()
    }

    suspend fun signOut() = auth.signOut()

    fun currentUserSession() = auth.currentSessionOrNull()

    fun isUserLoggedIn() = currentUserSession() != null

    @Serializable
    private data class JoinCodeRow(val join_code: String)
}
