package com.example.feesmanager.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.repository.FeesRepository
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * StudentDashboardViewModel — Migrated to Supabase and Coroutines.
 * Provides real-time student data and handles automated monthly rollover logic.
 */
class StudentDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val studentRepo = StudentRepository()
    private val feesRepo    = FeesRepository()

    private val _student = MutableLiveData<FmResult<Student>>()
    val student: LiveData<FmResult<Student>> = _student

    /**
     * Loads student data from the relational Supabase backend.
     * Uses viewModelScope for lifecycle-aware data fetching.
     */
    fun loadStudentData(teacherId: String, studentId: String) {
        _student.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.getStudent(getApplication(), teacherId, studentId) { result ->
                _student.postValue(result)
            }
        }
    }
}