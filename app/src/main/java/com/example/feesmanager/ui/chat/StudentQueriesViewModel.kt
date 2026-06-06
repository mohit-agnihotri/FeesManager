package com.example.feesmanager.ui.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.ConversationSummary
import com.example.feesmanager.data.repository.DashboardRepository
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * StudentQueriesViewModel — Migrated to Supabase and Coroutines.
 * ViewModel for the teacher's message inbox.
 */
class StudentQueriesViewModel : ViewModel() {

    private val dashboardRepo = DashboardRepository()
    private val studentRepo   = StudentRepository()

    private val _conversations = MutableLiveData<FmResult<List<ConversationSummary>>>()
    val conversations: LiveData<FmResult<List<ConversationSummary>>> = _conversations

    /**
     * Loads the teacher's message inbox by joining relational enrollment data with latest messages.
     */
    fun loadInbox(teacherId: String) {
        _conversations.value = FmResult.Loading

        viewModelScope.launch {
            // Step 1: Load student name map (optimized relational fetch)
            studentRepo.loadStudentsPaginated(teacherId, 1000) { studentResult ->
                if (studentResult is FmResult.Success) {
                    val namesMap = studentResult.content.associate { it.id to it.name }

                    // Step 2: Fetch inbox summaries
                    viewModelScope.launch {
                        dashboardRepo.getTeacherInbox(teacherId, namesMap) { inboxResult ->
                            when (inboxResult) {
                                is FmResult.Success -> {
                                    val convs = inboxResult.content.map { (sid, info) ->
                                        ConversationSummary(
                                            studentId   = sid,
                                            studentName = info.first,
                                            lastText    = info.second,
                                            unreadCount = info.third
                                        )
                                    }
                                    _conversations.postValue(FmResult.Success(convs))
                                }
                                is FmResult.Error -> _conversations.postValue(FmResult.Error(inboxResult.message))
                                is FmResult.Loading -> {}
                            }
                        }
                    }
                } else if (studentResult is FmResult.Error) {
                    _conversations.postValue(FmResult.Error(studentResult.message))
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
