package com.example.feesmanager.ui.student

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.repository.FeesRepository
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * StudentDashboardViewModel — Migrated to Supabase and Coroutines.
 * Provides real-time student data and handles automated monthly rollover logic.
 */
class StudentDashboardViewModel : ViewModel() {

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
            studentRepo.getStudent(teacherId, studentId) { result ->
                _student.postValue(result)
            }
        }
    }
}
