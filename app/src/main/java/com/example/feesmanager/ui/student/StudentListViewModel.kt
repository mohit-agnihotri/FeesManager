package com.example.feesmanager.ui.student

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.AdvanceStudentSummary
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.repository.DashboardRepository
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * StudentListViewModel
 *
 * Path: app/src/main/java/com/example/feesmanager/ui/student/StudentListViewModel.kt
 *
 * BUG FIX: loadPendingRequestsAndDefaulters() previously called
 * loadStudentsPaginated() for BOTH pending and approved students, but
 * loadStudentsPaginated() only fetches status="approved" → pending
 * students were never returned, so the teacher's "Join Requests" tab
 * always showed empty.
 *
 * FIXED: Now calls studentRepo.loadPendingStudents() (new dedicated function)
 * for the pending tab, and studentRepo.loadStudentsPaginated() for the
 * defaulters tab separately.
 */
class StudentListViewModel : ViewModel() {

    private val studentRepo   = StudentRepository()
    private val dashboardRepo = DashboardRepository()

    // ── Full approved student list ────────────────────────────────────────────

    private val _students = MutableLiveData<FmResult<List<Student>>>()
    val students: LiveData<FmResult<List<Student>>> = _students

    private var currentLimit    = 50
    private var lastQueryType   = "all"
    private var lastQueryParam  = ""

    fun loadStudentsPaginated(teacherId: String, isLoadMore: Boolean = false) {
        if (!isLoadMore) currentLimit = 50 else currentLimit += 50
        lastQueryType = "all"
        _students.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.loadStudentsPaginated(teacherId, currentLimit) {
                _students.postValue(it)
            }
        }
    }

    fun searchStudentsByName(teacherId: String, name: String, isLoadMore: Boolean = false) {
        if (!isLoadMore) currentLimit = 50 else currentLimit += 50
        lastQueryType  = "search"
        lastQueryParam = name
        _students.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.searchStudentsByName(teacherId, name, currentLimit) {
                _students.postValue(it)
            }
        }
    }

    fun loadMoreStudents(teacherId: String) {
        when (lastQueryType) {
            "all"    -> loadStudentsPaginated(teacherId, true)
            "search" -> searchStudentsByName(teacherId, lastQueryParam, true)
            "class"  -> searchStudentsByClass(teacherId, lastQueryParam, true)
        }
    }

    fun searchStudentsByClass(teacherId: String, className: String, isLoadMore: Boolean = false) {
        if (!isLoadMore) currentLimit = 50 else currentLimit += 50
        lastQueryType  = "class"
        lastQueryParam = className
        _students.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.searchStudentsByClass(teacherId, className, currentLimit) {
                _students.postValue(it)
            }
        }
    }

    // ── Pending + Defaulters (used by PendingStudentsActivity) ───────────────

    private val _pendingRequests = MutableLiveData<FmResult<List<Student>>>()
    val pendingRequests: LiveData<FmResult<List<Student>>> = _pendingRequests

    private val _defaulters = MutableLiveData<FmResult<List<Student>>>()
    val defaulters: LiveData<FmResult<List<Student>>> = _defaulters

    /**
     * ✅ BUG FIX: Now uses two separate repo calls:
     *   1. loadPendingStudents()  → only status="pending" → populates Join Requests tab
     *   2. loadStudentsPaginated() → only status="approved" → populates Defaulters tab
     *
     * Previously both used loadStudentsPaginated() which returned only approved students,
     * so the pending tab was always empty even when students had submitted join requests.
     */
    fun loadPendingRequestsAndDefaulters(teacherId: String) {
        _pendingRequests.value = FmResult.Loading
        _defaulters.value      = FmResult.Loading

        viewModelScope.launch {
            // ── Tab 1: Join Requests ──────────────────────────────────────────
            studentRepo.loadPendingStudents(teacherId) { result ->
                Log.d("StudentListVM", "Pending result: $result")
                _pendingRequests.postValue(result)
            }

            // ── Tab 2: Fee Defaulters (approved students with outstanding fees)
            studentRepo.loadStudentsWithFees(teacherId) { result ->
                _defaulters.postValue(result)
            }
        }
    }

    // ── Status update (approve / reject) ─────────────────────────────────────

    private val _statusResult = MutableLiveData<FmResult<Unit>>()
    val statusResult: LiveData<FmResult<Unit>> = _statusResult

    fun updateStudentStatus(teacherId: String, studentId: String, status: String) {
        viewModelScope.launch {
            studentRepo.updateStudentStatus(teacherId, studentId, status) { result ->
                _statusResult.postValue(result)
                // After approval/rejection, refresh pending list automatically
                if (result is FmResult.Success) {
                    loadPendingRequestsAndDefaulters(teacherId)
                }
            }
        }
    }

    fun deleteStudent(teacherId: String, studentId: String) {
        viewModelScope.launch {
            studentRepo.deleteStudent(teacherId, studentId) { result ->
                _statusResult.postValue(result)
            }
        }
    }

    // ── Advance list ──────────────────────────────────────────────────────────

    private val _advanceStudents = MutableLiveData<FmResult<List<AdvanceStudentSummary>>>()
    val advanceStudents: LiveData<FmResult<List<AdvanceStudentSummary>>> = _advanceStudents

    fun loadAdvanceStudents(teacherId: String) {
        _advanceStudents.value = FmResult.Loading
        viewModelScope.launch {
            dashboardRepo.getAdvanceStudents(teacherId) { result ->
                _advanceStudents.postValue(result)
            }
        }
    }
}
