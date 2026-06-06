package com.example.feesmanager.ui.fees

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.ui.payment.AppPaymentConfig
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.model.FeeMonth
import com.example.feesmanager.data.model.PaymentSummary
import com.example.feesmanager.data.model.StudentPaymentInfo
import com.example.feesmanager.data.repository.CashfreeOrderData
import com.example.feesmanager.data.repository.CashfreeRepository
import com.example.feesmanager.data.repository.FeesRepository
import kotlinx.coroutines.launch

class PayFeesViewModel : ViewModel() {

    private val feesRepo      = FeesRepository()
    private val cashfreeRepo  = CashfreeRepository()   // Cashfree path

    private val _paymentInfo = MutableLiveData<FmResult<StudentPaymentInfo>>()
    val paymentInfo: LiveData<FmResult<StudentPaymentInfo>> = _paymentInfo

    fun loadStudentInfo(teacherId: String, studentId: String) {
        _paymentInfo.value = FmResult.Loading
        viewModelScope.launch {
            feesRepo.getStudentPaymentInfo(teacherId, studentId) { result ->
                _paymentInfo.postValue(result)
            }
        }
    }

    // ── Cashfree order ─────────────────────────
    private val _cashfreeOrderResult = MutableLiveData<FmResult<CashfreeOrderData>>()
    val cashfreeOrderResult: LiveData<FmResult<CashfreeOrderData>> = _cashfreeOrderResult

    fun createCashfreeOrder(teacherId: String, studentId: String, amountInINR: Int) {
        _cashfreeOrderResult.value = FmResult.Loading
        viewModelScope.launch {
            cashfreeRepo.createOrder(
                teacherId  = teacherId,
                studentId  = studentId,
                amountINR  = amountInINR,
                onResult   = { result -> _cashfreeOrderResult.postValue(result) }
            )
        }
    }

    /**
     * Verifies a Cashfree payment on the server side.
     * This is the PRIMARY path that actually records the payment in the database
     * (payment_orders, payments, fee_records tables).
     * Called from onPaymentVerify after Cashfree SDK confirms success.
     */
    fun verifyCashfreePayment(cashfreeOrderId: String) {
        viewModelScope.launch {
            cashfreeRepo.verifyPayment(cashfreeOrderId) { result ->
                when (result) {
                    is FmResult.Success -> {
                        android.util.Log.d("PayFeesVM", "Payment verified on server: ${result.content}")
                    }
                    is FmResult.Error -> {
                        android.util.Log.e("PayFeesVM", "Payment verification failed: ${result.message}")
                    }
                    else -> {}
                }
            }
        }
    }

    // ── Receipt generation ─────────────────────────
    private val _paymentResult = MutableLiveData<FmResult<PaymentSummary>>()
    val paymentResult: LiveData<FmResult<PaymentSummary>> = _paymentResult

    fun generateLocalReceipt(
        studentName:  String,
        payAmount:    Int,
        totalPending: Int,
        transactionId: String,
        provider:     String = "Cashfree"
    ) {
        val paid      = minOf(payAmount, totalPending)
        val remaining = maxOf(0, totalPending - paid)
        val advance   = maxOf(0, payAmount - totalPending)

        val summary = PaymentSummary(
            studentName   = studentName,
            amount        = payAmount,
            total         = totalPending,
            paid          = paid,
            remaining     = remaining,
            advance       = advance,
            mode          = "Online ($provider)",
            transactionId = transactionId
        )
        _paymentResult.value = FmResult.Success(summary)
    }

    private val _paymentHistory = MutableLiveData<FmResult<List<FeeMonth>>>()
    val paymentHistory: LiveData<FmResult<List<FeeMonth>>> = _paymentHistory

    fun loadHistory(teacherId: String, studentId: String) {
        _paymentHistory.value = FmResult.Loading
        viewModelScope.launch {
            feesRepo.getPaymentHistory(teacherId, studentId) { result ->
                _paymentHistory.postValue(result)
            }
        }
    }
}
