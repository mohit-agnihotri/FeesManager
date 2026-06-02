package com.example.feesmanager.ui.attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.repository.AttendanceRepository
import kotlinx.coroutines.launch

/**
 * AttendanceViewModel — Migrated to Supabase and Coroutines.
 * Manages all student list and attendance record fetching via asynchronous scopes.
 */
class AttendanceViewModel : ViewModel() {

    private val attendanceRepo = AttendanceRepository()

    // ─── Student names ────────────────────────────────────────────────────────

    private val _studentNames = MutableLiveData<FmResult<Map<String, String>>>()
    val studentNames: LiveData<FmResult<Map<String, String>>> = _studentNames

    fun loadStudents(teacherId: String) {
        _studentNames.value = FmResult.Loading
        viewModelScope.launch {
            attendanceRepo.loadStudents(teacherId) { result ->
                _studentNames.postValue(result)
            }
        }
    }

    // ─── Attendance for date ──────────────────────────────────────────────────

    private val _attendance = MutableLiveData<FmResult<Map<String, Boolean>>>()
    val attendance: LiveData<FmResult<Map<String, Boolean>>> = _attendance

    fun loadAttendance(teacherId: String, dateKey: String) {
        viewModelScope.launch {
            attendanceRepo.loadAttendance(teacherId, dateKey) { result ->
                _attendance.postValue(result)
            }
        }
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private val _saveResult = MutableLiveData<FmResult<Unit>>()
    val saveResult: LiveData<FmResult<Unit>> = _saveResult

    fun saveAttendance(teacherId: String, dateKey: String, attendanceMap: Map<String, Boolean>) {
        viewModelScope.launch {
            attendanceRepo.saveAttendance(teacherId, dateKey, attendanceMap) { result ->
                _saveResult.postValue(result)
            }
        }
    }
}
