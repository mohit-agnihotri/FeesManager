package com.example.feesmanager.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.ChatMessage
import com.example.feesmanager.data.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
                ) { _sendResult.postValue(it) }
            } else {
                chatRepo.sendPersonalMessage(
                    context.teacherId, context.studentId!!, text, sender, senderName
                ) { _sendResult.postValue(it) }
            }
        }
    }

    private data class ChatContext(
        val teacherId: String,
        val studentId: String? = null,
        val className: String? = null,
        val isClassChat: Boolean
    )
}
