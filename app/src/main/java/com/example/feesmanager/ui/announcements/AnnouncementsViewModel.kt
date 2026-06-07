package com.example.feesmanager.ui.announcements

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.Announcement
import com.example.feesmanager.data.repository.AnnouncementsRepository
import kotlinx.coroutines.launch

class AnnouncementsViewModel : ViewModel() {

    private val announcementsRepo = AnnouncementsRepository()

    private val _announcements = MutableLiveData<FmResult<List<Announcement>>>()
    val announcements: LiveData<FmResult<List<Announcement>>> = _announcements

    private val _postResult = MutableLiveData<FmResult<Unit>>()
    val postResult: LiveData<FmResult<Unit>> = _postResult

    fun loadAnnouncements(teacherId: String, studentClass: String? = null) {
        _announcements.value = FmResult.Loading
        viewModelScope.launch {
            announcementsRepo.getAnnouncements(teacherId, studentClass) { _announcements.postValue(it) }
        }
    }

    fun postAnnouncement(teacherId: String, body: String, targetClass: String = "all") {
        _postResult.value = FmResult.Loading
        viewModelScope.launch {
            announcementsRepo.postAnnouncement(teacherId, body, body, targetClass) { _postResult.postValue(it) }
        }
    }

    fun deleteAnnouncement(teacherId: String, announcementId: String) {
        viewModelScope.launch {
            announcementsRepo.deleteAnnouncement(teacherId, announcementId) { _postResult.postValue(it) }
        }
    }
}
