package com.example.feesmanager.ui.payment

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.CashfreeVendorStatus
import com.example.feesmanager.data.repository.CashfreeRepository
import kotlinx.coroutines.launch

/**
 * CashfreeSetupViewModel — ViewModel for CashfreeOnboardingActivity.
 * Follows the same MVVM pattern used throughout the project.
 */
class CashfreeSetupViewModel : ViewModel() {

    private val repo = CashfreeRepository()

    // ── Vendor Status (loaded on screen open) ─────────────────────────────────
    private val _vendorStatus = MutableLiveData<FmResult<CashfreeVendorStatus>>()
    val vendorStatus: LiveData<FmResult<CashfreeVendorStatus>> = _vendorStatus

    fun loadVendorStatus(teacherId: String) {
        _vendorStatus.value = FmResult.Loading
        viewModelScope.launch {
            repo.getVendorStatus(teacherId) { result ->
                _vendorStatus.postValue(result)
            }
        }
    }

    // ── Vendor Creation ────────────────────────────────────────────────────────
    private val _createResult = MutableLiveData<FmResult<CashfreeVendorStatus>>()
    val createResult: LiveData<FmResult<CashfreeVendorStatus>> = _createResult

    fun createVendor(
        accountName:   String,
        accountNumber: String,
        ifsc:          String,
        panNumber:     String,
        phone:         String
    ) {
        _createResult.value = FmResult.Loading
        viewModelScope.launch {
            repo.createVendor(
                accountName   = accountName,
                accountNumber = accountNumber,
                ifsc          = ifsc,
                panNumber     = panNumber,
                phone         = phone,
                onResult      = { result ->
                    _createResult.postValue(result)
                    // Refresh status after creation
                    if (result is FmResult.Success) {
                        _vendorStatus.postValue(FmResult.Success(result.content))
                    }
                }
            )
        }
    }

    // ── Settlement Status ─────────────────────────────────────────────────────
    private val _settlementStatus = MutableLiveData<Pair<String, String>>()
    val settlementStatus: LiveData<Pair<String, String>> = _settlementStatus

    fun loadSettlementStatus(teacherId: String) {
        viewModelScope.launch {
            repo.getLatestSettlementStatus(teacherId) { result ->
                if (result is FmResult.Success) {
                    _settlementStatus.postValue(result.content)
                }
            }
        }
    }
}
