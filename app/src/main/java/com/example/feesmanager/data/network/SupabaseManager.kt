package com.example.feesmanager.data.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

/**
 * SupabaseManager — Centralized Supabase client.
 * UPDATED: Added Storage plugin for avatar image uploads.
 *
 * FIX: Removed conflicting import com.google.android.gms.auth.api.signin.internal.Storage
 *      which was auto-added by Android Studio and caused "ambiguous import" build error.
 *
 * Path: app/src/main/java/com/example/feesmanager/data/SupabaseManager.kt
 */
object SupabaseManager {

    private const val SUPABASE_URL      = "https://vtpguytfeqbpysxbppyv.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ0cGd1eXRmZXFicHlzeGJwcHl2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ4MTUyOTgsImV4cCI6MjA5MDM5MTI5OH0.nB5StGVR5j1W6nuh-D-RXCEmEYeNypnq9CTTunyPqcA"

    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest.Companion)
        install(Auth.Companion)
        install(Realtime.Companion)
        install(Storage.Companion)   // ✅ Supabase Storage — required for avatar uploads
    }
}