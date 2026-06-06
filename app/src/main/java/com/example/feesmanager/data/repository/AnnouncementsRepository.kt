package com.example.feesmanager.data.repository

import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.model.Announcement
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable

class AnnouncementsRepository {

    private val db = SupabaseManager.client.postgrest

    suspend fun getAnnouncements(teacherId: String, onResult: (FmResult<List<Announcement>>) -> Unit) {
        try {
            val response = db.from("announcements")
                .select {
                    filter { eq("teacher_id", teacherId) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<AnnouncementRow>()
            onResult(FmResult.Success(response.map { it.toAnnouncement() }))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed: ${e.message}", e))
        }
    }

    suspend fun postAnnouncement(
        teacherId: String, title: String, body: String,
        targetClass: String = "all",
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            db.from("announcements").insert(AnnouncementInsert(
                teacher_id = teacherId, title = title, body = body, target_class = targetClass
            ))
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed: ${e.message}", e))
        }
    }

    suspend fun deleteAnnouncement(teacherId: String, announcementId: String, onResult: (FmResult<Unit>) -> Unit) {
        try {
            db.from("announcements").delete {
                filter { eq("id", announcementId); eq("teacher_id", teacherId) }
            }
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Failed: ${e.message}", e))
        }
    }

    @Serializable
    private data class AnnouncementRow(
        val id: String, val teacher_id: String, val title: String, val body: String,
        val created_at: String, val target_class: String? = "all"
    ) {
        fun toAnnouncement() = Announcement(
            id = id, title = title, body = body, timestamp = 0,
            teacherId = teacher_id, createdAt = created_at, targetClass = target_class ?: "all"
        )
    }

    @Serializable
    private data class AnnouncementInsert(
        val teacher_id: String, val title: String, val body: String, val target_class: String
    )
}
