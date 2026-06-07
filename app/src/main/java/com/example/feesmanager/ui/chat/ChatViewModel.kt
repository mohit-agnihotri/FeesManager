package com.example.feesmanager.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.ChatMessage
import com.example.feesmanager.data.repository.ChatRepository
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import com.example.feesmanager.data.network.SupabaseManager

/**
 * ChatViewModel — Migrated to Supabase (Postgres + Realtime).
 * Handles personal and class-wide messaging using state-driven flows.
 */
class ChatViewModel : ViewModel() {

    private val chatRepo = ChatRepository()

    private val _sendResult = MutableLiveData<FmResult<Unit>>()
    val sendResult: LiveData<FmResult<Unit>> = _sendResult

    // State for driving the real-time message flow
    private val chatParams = MutableStateFlow<ChatContext?>(null)

    /**
     * The real-time message list, automatically updated via Supabase Channels.
     */
    val messages: LiveData<FmResult<List<ChatMessage>>> = chatParams.flatMapLatest { context ->
        if (context == null) {
            MutableStateFlow<FmResult<List<ChatMessage>>>(FmResult.Success(emptyList()))
        } else {
            val flow = if (context.isClassChat) {
                chatRepo.observeClassChat(context.teacherId, context.className!!)
            } else {
                chatRepo.observePersonalChat(context.teacherId, context.studentId!!)
            }
            flow.map<List<ChatMessage>, FmResult<List<ChatMessage>>> { FmResult.Success(it) }
        }
    }.asLiveData()

    // ─── Load chat ────────────────────────────────────────────────────────────

    fun loadPersonalChat(teacherId: String, studentId: String) {
        chatParams.value = ChatContext(teacherId, studentId = studentId, isClassChat = false)
    }

    fun loadClassChat(teacherId: String, className: String) {
        chatParams.value = ChatContext(teacherId, className = className, isClassChat = true)
    }

    private fun refreshChat(context: ChatContext) {
        chatParams.value = context.copy(refreshKey = System.currentTimeMillis())
    }

    private val _avatars = MutableLiveData<Map<String, String>>(emptyMap())
    val avatars: LiveData<Map<String, String>> = _avatars

    fun fetchAvatars(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                val profiles = SupabaseManager.client.postgrest.from("profiles")
                    .select(io.github.jan.supabase.postgrest.query.Columns.raw("id, avatar_url")) {
                        filter { isIn("id", ids) }
                    }.decodeList<ProfileAvatarRow>()
                
                val map = profiles.associate { it.id to (it.avatar_url ?: "") }
                val newMap = _avatars.value.orEmpty().toMutableMap()
                newMap.putAll(map)
                _avatars.postValue(newMap)
            } catch (e: Exception) {}
        }
    }

    fun loadMoreMessages() {
        // Pagination is handled by the initial query limit in ChatRepository
    }

    // ─── Send message ─────────────────────────────────────────────────────────

    fun sendMessage(text: String, sender: String, senderName: String) {
        val context = chatParams.value ?: return
        _sendResult.value = FmResult.Loading
        viewModelScope.launch {
            if (context.isClassChat) {
                chatRepo.sendClassMessage(
                    context.teacherId, context.className!!, text, sender, senderName
                ) { 
                    _sendResult.postValue(it)
                    if (it is FmResult.Success) refreshChat(context)
                }
            } else {
                chatRepo.sendPersonalMessage(
                    context.teacherId, context.studentId!!, text, sender, senderName
                ) { 
                    _sendResult.postValue(it)
                    if (it is FmResult.Success) refreshChat(context)
                }
            }
        }
    }

    private val _uploadingAttachment = MutableLiveData<Boolean>(false)
    val uploadingAttachment: LiveData<Boolean> = _uploadingAttachment

    fun sendAttachments(context: Context, uris: List<Uri>, text: String, sender: String, senderName: String) {
        val ctx = chatParams.value ?: return
        if (uris.isEmpty()) return
        
        _uploadingAttachment.value = true
        
        viewModelScope.launch {
            var first = true
            var anySuccess = false
            for (uri in uris) {
                val uploadResult = chatRepo.uploadAttachment(context, uri)
                if (uploadResult is FmResult.Success) {
                    anySuccess = true
                    val attachmentUrl = uploadResult.content
                    val finalMessageText = if (first && text.isNotBlank()) text else "Attachment"
                    first = false
                    
                    _sendResult.postValue(FmResult.Loading)
                    if (ctx.isClassChat) {
                        chatRepo.sendClassMessage(
                            ctx.teacherId, ctx.className!!, finalMessageText, sender, senderName, attachmentUrl
                        ) { 
                            _sendResult.postValue(it)
                            if (it is FmResult.Success) refreshChat(ctx)
                        }
                    } else {
                        chatRepo.sendPersonalMessage(
                            ctx.teacherId, ctx.studentId!!, finalMessageText, sender, senderName, attachmentUrl
                        ) { 
                            _sendResult.postValue(it)
                            if (it is FmResult.Success) refreshChat(ctx)
                        }
                    }
                } else if (uploadResult is FmResult.Error) {
                    _sendResult.postValue(FmResult.Error("Upload failed: ${uploadResult.message}"))
                }
            }
            _uploadingAttachment.postValue(false)
        }
    }

    // "?"?"? Message Deletion "?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?"?

    fun deleteMessageForEveryone(messageId: String) {
        viewModelScope.launch {
            chatRepo.deleteMessageForEveryone(messageId)
            chatParams.value?.let { refreshChat(it) }
        }
    }

    fun deleteMessageForMe(messageId: String, myUserId: String, currentDeletedBy: List<String>) {
        viewModelScope.launch {
            chatRepo.deleteMessageForMe(messageId, myUserId, currentDeletedBy)
            chatParams.value?.let { refreshChat(it) }
        }
    }

    private data class ChatContext(
        val teacherId: String,
        val studentId: String? = null,
        val className: String? = null,
        val isClassChat: Boolean,
        val refreshKey: Long = 0L
    )

    @kotlinx.serialization.Serializable
    private data class ProfileAvatarRow(val id: String, val avatar_url: String? = null)
}
