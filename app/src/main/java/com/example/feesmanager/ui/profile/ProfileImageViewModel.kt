package com.example.feesmanager.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.repository.ProfileImageRepository
import kotlinx.coroutines.launch

/**
 * ProfileImageViewModel
 *
 * Shared ViewModel for avatar operations — used by:
 *   • DashboardActivity        (teacher avatar in header)
 *   • StudentDashboardActivity (student avatar in header)
 *   • nav_drawer_teacher.xml / nav_drawer_student.xml (drawer header)
 *
 * Path: app/src/main/java/com/example/feesmanager/ui/profile/ProfileImageViewModel.kt
 */
class ProfileImageViewModel : ViewModel() {

    private val repo = ProfileImageRepository()

    // ── Upload result ─────────────────────────────────────────────────────────

    private val _uploadResult = MutableLiveData<FmResult<String>>()

    /** Loading → in-progress  |  Success(url) → new avatar URL  |  Error → message */
    val uploadResult: LiveData<FmResult<String>> = _uploadResult

    /**
     * Called immediately after the gallery ActivityResult returns a URI.
     * Emits Loading while uploading, then Success or Error.
     */
    fun uploadAvatar(context: Context, userId: String, imageUri: Uri) {
        _uploadResult.value = FmResult.Loading
        viewModelScope.launch {
            val result = repo.uploadAndSaveAvatar(context, userId, imageUri)
            _uploadResult.postValue(result)
        }
    }

    // ── Load result ───────────────────────────────────────────────────────────

    private val _avatarUrl = MutableLiveData<String?>()

    /** Null = no avatar set (show default), non-null = load with Glide */
    val avatarUrl: LiveData<String?> = _avatarUrl

    fun loadAvatar(userId: String) {
        viewModelScope.launch {
            val result = repo.getAvatarUrl(userId)
            if (result is FmResult.Success) {
                _avatarUrl.postValue(result.content)
            }
            // Silently ignore errors — UI keeps default avatar
        }
    }
}
