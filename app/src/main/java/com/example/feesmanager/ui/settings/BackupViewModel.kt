package com.example.feesmanager.ui.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.repository.DashboardRepository
import kotlinx.coroutines.launch

/**
 * BackupViewModel — Migrated to Supabase and Coroutines.
 * Loads the full teacher and student enrollment dataset for CSV/text export.
 */
class BackupViewModel : ViewModel() {

    private val dashboardRepo = DashboardRepository()

    private val _exportData = MutableLiveData<FmResult<List<Map<String, Any>>>>()
    val exportData: LiveData<FmResult<List<Map<String, Any>>>> = _exportData

    /**
     * Loads the complete set of student enrollments and fee status for the teacher.
     */
    fun loadExportData(teacherId: String) {
        _exportData.value = FmResult.Loading
        viewModelScope.launch {
            dashboardRepo.loadFullExportData(teacherId) { result ->
                _exportData.postValue(result)
            }
        }
    }
}