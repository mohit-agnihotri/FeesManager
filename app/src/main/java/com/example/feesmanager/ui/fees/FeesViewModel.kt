package com.example.feesmanager.ui.fees

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.PaymentSummary
import com.example.feesmanager.data.model.Student
import com.example.feesmanager.data.repository.FeesRepository
import com.example.feesmanager.data.repository.StudentRepository
import kotlinx.coroutines.launch

/**
 * FeesViewModel — Migrated to Supabase and Coroutines.
 * ViewModel for FeesEntryActivity (teacher records student payment).
 * Binds lifecycle-aware UI state to the relational PostgreSQL backend.
 */
class FeesViewModel : ViewModel() {

    private val studentRepo = StudentRepository()
    private val feesRepo    = FeesRepository()

    // ─── Student list (approved students only for record payment spinner) ──────

    private val _students = MutableLiveData<FmResult<List<Student>>>()
    val students: LiveData<FmResult<List<Student>>> = _students

    /**
     * Loads a teacher's approved students to populate the payment spinner.
     */
    fun loadStudents(teacherId: String) {
        _students.value = FmResult.Loading
        viewModelScope.launch {
            studentRepo.loadStudentsPaginated(teacherId, 100) { result ->
                _students.postValue(result)
            }
        }
    }

    // ─── Payment Recording ────────────────────────────────────────────────────

    private val _paymentResult = MutableLiveData<FmResult<PaymentSummary>>()
    val paymentResult: LiveData<FmResult<PaymentSummary>> = _paymentResult

    /**
     * Records a manual payment (Cash/Online/etc.) in the relational database.
     */
    fun recordPayment(
        teacherId: String,
        studentId: String,
        studentName: String,
        payAmount: Int,
        paymentMode: String
    ) {
        _paymentResult.value = FmResult.Loading
        viewModelScope.launch {
            feesRepo.recordPayment(teacherId, studentId, studentName, payAmount, paymentMode) { result ->
                _paymentResult.postValue(result)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // No listener removal needed for standard Postgrest queries
    }
}
