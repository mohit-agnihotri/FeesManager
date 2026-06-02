package com.example.feesmanager.data.repository

import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.model.ChatMessage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable

class ChatRepository {

    private val db = SupabaseManager.client.postgrest
    private val rt = SupabaseManager.client.realtime

    // ─── Personal Chat ───────────────────────────────────────────────────────

    fun observePersonalChat(teacherId: String, studentId: String): Flow<List<ChatMessage>> {
        val channel = rt.channel("personal_${teacherId}_${studentId}")
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }
        return changeFlow
            .map { fetchPersonalMessages(teacherId, studentId) }
            .onStart {
                emit(fetchPersonalMessages(teacherId, studentId))
                channel.subscribe()
            }
    }

    private suspend fun fetchPersonalMessages(teacherId: String, studentId: String): List<ChatMessage> {
        return try {
            db.from("messages").select {
                filter {
                    eq("teacher_id", teacherId)
                    eq("student_id", studentId)
                    eq("chat_type", "personal")
                }
                order("created_at", Order.ASCENDING)
                limit(100)
            }.decodeList<MessageRow>().map { it.toChatMessage() }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun sendPersonalMessage(
        teacherId: String, studentId: String,
        text: String, sender: String, senderName: String,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            db.from("messages").insert(
                MessageInsert(teacher_id = teacherId, student_id = studentId,
                    chat_type = "personal", class_name = null,
                    sender_id = sender, sender_name = senderName, text = text)
            )
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Send failed: ${e.message}", e))
        }
    }

    // ─── Class Chat ──────────────────────────────────────────────────────────

    fun observeClassChat(teacherId: String, className: String): Flow<List<ChatMessage>> {
        val channel = rt.channel("class_${teacherId}_${className.replace(" ", "_")}")
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
        }
        return changeFlow
            .map { fetchClassMessages(teacherId, className) }
            .onStart {
                emit(fetchClassMessages(teacherId, className))
                channel.subscribe()
            }
    }

    private suspend fun fetchClassMessages(teacherId: String, className: String): List<ChatMessage> {
        return try {
            db.from("messages").select {
                filter {
                    eq("teacher_id", teacherId)
                    eq("class_name", className)
                    eq("chat_type", "class")
                }
                order("created_at", Order.ASCENDING)
                limit(100)
            }.decodeList<MessageRow>().map { it.toChatMessage() }
        } catch (e: Exception) { emptyList() }
    }

    suspend fun sendClassMessage(
        teacherId: String, className: String,
        text: String, sender: String, senderName: String,
        onResult: (FmResult<Unit>) -> Unit
    ) {
        try {
            db.from("messages").insert(
                MessageInsert(teacher_id = teacherId, student_id = null,
                    chat_type = "class", class_name = className,
                    sender_id = sender, sender_name = senderName, text = text)
            )
            onResult(FmResult.Success(Unit))
        } catch (e: Exception) {
            onResult(FmResult.Error("Send failed: ${e.message}", e))
        }
    }

    // ─── Teacher's class list ────────────────────────────────────────────────

    suspend fun getTeacherClasses(teacherId: String): List<String> {
        return try {
            db.from("teacher_classes")
                .select { filter { eq("teacher_id", teacherId) } }
                .decodeList<ClassNameRow>()
                .map { it.class_name }
                .sortedWith(compareBy { it.toIntOrNull() ?: 999 })
        } catch (e: Exception) { emptyList() }
    }

    // ─── Models ───────────────────────────────────────────────────────────────

    @Serializable
    private data class MessageRow(
        val id: String = "", val teacher_id: String = "",
        val student_id: String? = null, val class_name: String? = null,
        val chat_type: String = "personal",
        val sender_id: String = "", val sender_name: String = "",
        val text: String = "", val created_at: String = ""
    ) {
        fun toChatMessage() = ChatMessage(
            id = id, sender = sender_id, senderName = sender_name,
            text = text, timestamp = created_at, isClass = chat_type == "class"
        )
    }

    @Serializable
    private data class MessageInsert(
        val teacher_id: String, val student_id: String?,
        val chat_type: String, val class_name: String?,
        val sender_id: String, val sender_name: String, val text: String
    )

    @Serializable
    private data class ClassNameRow(val class_name: String)
}
