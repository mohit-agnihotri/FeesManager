package com.example.feesmanager.ui.student

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * StudentDetailViewModel — Migrated to Supabase and Coroutines.
 * Handles one-time and real-time student data for profile/advance/history views.
 * Binds lifecycle-aware UI state to the relational PostgreSQL backend.
 */
class StudentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val studentRepo = StudentRepository()

    private val _student = MutableLiveData<FmResult<Student>>()
    val student: LiveData<FmResult<Student>> = _student

    private val _updateResult = MutableLiveData<FmResult<Unit>>()
    val updateResult: LiveData<FmResult<Unit>> = _updateResult

    /**
     * Loads student profile and enrollment data using a one-time fetch.
     */
    fun loadStudent(teacherId: String, studentId: String) {
        _student.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.getStudent(getApplication(), teacherId, studentId) { result ->
                _student.postValue(result)
            }
        }
    }

    /**
     * Soft-deletes a student enrollment in the relational schema.
     */
    fun deleteStudent(teacherId: String, studentId: String) {
        _updateResult.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.deleteStudent(teacherId, studentId) { result ->
                _updateResult.postValue(result)
            }
        }
    }

    /**
     * Future Improvement: observeStudent() can be implemented using Supabase Realtime
     * via studentRepo.observeEnrollment(studentId).
     */
    fun observeStudent(teacherId: String, studentId: String) {
        // For now, redirect to loadStudent to fix build
        loadStudent(teacherId, studentId)
    }

    override fun onCleared() {
        super.onCleared()
        // No listener removal needed for standard Postgrest queries
    }
}
