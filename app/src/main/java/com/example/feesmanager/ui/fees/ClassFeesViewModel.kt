package com.example.feesmanager.ui.fees

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.repository.TeacherRepository
import kotlinx.coroutines.launch

/**
 * ClassFeesViewModel — Migrated to Supabase and Coroutines.
 * Handles configuration of per-class monthly fee amounts.
 */
class ClassFeesViewModel : ViewModel() {

    private val teacherRepo = TeacherRepository()

    // ─── Load class fees ──────────────────────────────────────────────────────

    private val _classFees = MutableLiveData<FmResult<Map<String, String>>>()
    val classFees: LiveData<FmResult<Map<String, String>>> = _classFees

    fun loadClassFees(teacherId: String) {
        _classFees.value = FmResult.Loading
        viewModelScope.launch {
            teacherRepo.getClassFees(teacherId) { result ->
                _classFees.postValue(result)
            }
        }
    }

    // ─── Save class fees ──────────────────────────────────────────────────────

    private val _saveResult = MutableLiveData<FmResult<Unit>>()
    val saveResult: LiveData<FmResult<Unit>> = _saveResult

    fun saveClassFees(teacherId: String, feesMap: Map<String, String>) {
        if (feesMap.isEmpty()) {
            _saveResult.value = FmResult.Error("No fees entered")
            return
        }
        _saveResult.value = FmResult.Loading
        viewModelScope.launch {
            teacherRepo.saveClassFees(teacherId, feesMap) { result ->
                _saveResult.postValue(result)
            }
        }
    }
}
