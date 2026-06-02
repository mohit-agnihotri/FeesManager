package com.example.feesmanager.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.AnalyticsData
import com.example.feesmanager.data.model.DashboardStats
import com.example.feesmanager.data.repository.DashboardRepository
import kotlinx.coroutines.launch

/**
 * DashboardViewModel — Updated for Supabase (Coroutines and Relational logic).
 */
class DashboardViewModel : ViewModel() {

    private val dashboardRepo = DashboardRepository()

    // ─── Dashboard Stats ──────────────────────────────────────────────────────

    private val _stats = MutableLiveData<FmResult<DashboardStats>>()
    val stats: LiveData<FmResult<DashboardStats>> = _stats

    private val _profile = MutableLiveData<FmResult<Triple<String, String, String>>>()
    val profile: LiveData<FmResult<Triple<String, String, String>>> = _profile

    fun loadDashboard(teacherId: String) {
        _stats.value = FmResult.Loading
        _profile.value = FmResult.Loading

        viewModelScope.launch {
            dashboardRepo.getDashboardStats(teacherId) { result ->
                _stats.postValue(result)
            }
            dashboardRepo.getTeacherProfile(teacherId) { result ->
                _profile.postValue(result)
            }
        }
    }

    // ─── Analytics ────────────────────────────────────────────────────────────

    private val _analytics = MutableLiveData<FmResult<AnalyticsData>>()
    val analytics: LiveData<FmResult<AnalyticsData>> = _analytics

    fun loadAnalytics(teacherId: String) {
        _analytics.value = FmResult.Loading
        viewModelScope.launch {
            dashboardRepo.loadAnalytics(teacherId) { result ->
                _analytics.postValue(result)
            }
        }
    }
}
