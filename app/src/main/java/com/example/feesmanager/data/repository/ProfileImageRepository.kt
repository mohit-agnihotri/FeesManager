package com.example.feesmanager.data.repository

import android.content.Context
import android.net.Uri
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.upload
import kotlinx.serialization.Serializable

/**
 * ProfileImageRepository — handles all profile image operations.
 *
 * FIX: Corrected storage.from("avatars").upload() call for Supabase Kotlin SDK v3.x.
 *      Old (wrong) signature:  upload(path = ..., data = ..., upsert = true)
 *      New (correct) v3 API:   upload(path, data) { upsert = true }
 *
 * Path: app/src/main/java/com/example/feesmanager/data/repository/ProfileImageRepository.kt
 *
 * Supabase Setup (one-time):
 *   Dashboard → Storage → New Bucket → name: "avatars" → Public: ON
 *   Then run: sql/supabase_avatar_setup.sql
 */
class ProfileImageRepository {

    private val db      = SupabaseManager.client.postgrest
    private val storage = SupabaseManager.client.storage

    // ── Upload + save ─────────────────────────────────────────────────────────

    suspend fun uploadAndSaveAvatar(
        context: Context,
        userId: String,
        imageUri: Uri
    ): FmResult<String> {
        return try {
            // 1. Read bytes from URI
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return FmResult.Error("Cannot open image – try again")
            val bytes = inputStream.readBytes()
            inputStream.close()

            if (bytes.isEmpty()) return FmResult.Error("Selected image is empty")

            // 2. Determine extension from MIME type
            val mimeType  = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/png"  -> "png"
                "image/webp" -> "webp"
                else         -> "jpg"
            }

            // 3. One file per user: <uuid>.<ext>  e.g. abc-123.jpg
            val storagePath = "$userId.$extension"

            // 4. ✅ FIXED: Correct Supabase Kotlin SDK v3.x upload API
            //    Old broken call: storage.from("avatars").upload(path=storagePath, data=bytes, upsert=true)
            //    Correct v3 call: upload(path, bytes) { upsert = true }
            storage.from("avatars").upload(storagePath, bytes) {
                upsert = true   // overwrite if file already exists
            }

            // 5. Build public URL with timestamp to bust Glide cache
            val baseUrl = storage.from("avatars").publicUrl(storagePath)
            val publicUrl = "$baseUrl?t=${System.currentTimeMillis()}"

            // 6. Persist URL in profiles table
            db.from("profiles").update(mapOf("avatar_url" to publicUrl)) {
                filter { eq("id", userId) }
            }

            android.util.Log.d("ProfileImageRepo", "Avatar uploaded → $publicUrl")
            FmResult.Success(publicUrl)

        } catch (e: Exception) {
            android.util.Log.e("ProfileImageRepo", "Upload failed", e)
            FmResult.Error("Upload failed: ${e.message}", e)
        }
    }

    // ── Fetch current avatar URL ──────────────────────────────────────────────

    suspend fun getAvatarUrl(userId: String): FmResult<String?> {
        return try {
            val row = db.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<AvatarRow>()
            FmResult.Success(row?.avatar_url)
        } catch (e: Exception) {
            FmResult.Error("Failed to fetch avatar: ${e.message}", e)
        }
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    @Serializable
    private data class AvatarRow(val avatar_url: String? = null)
}
