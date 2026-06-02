package com.example.feesmanager.ui.fees

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.AppPaymentConfig
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.SupabaseManager
import com.example.feesmanager.data.model.FeeMonth
import com.example.feesmanager.data.model.PaymentSummary
import com.example.feesmanager.data.model.StudentPaymentInfo
import com.example.feesmanager.data.repository.CashfreeOrderData
import com.example.feesmanager.data.repository.CashfreeRepository
import com.example.feesmanager.data.repository.FeesRepository
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PayFeesViewModel : ViewModel() {

    private val feesRepo      = FeesRepository()
    private val cashfreeRepo  = CashfreeRepository()   // NEW — Cashfree path

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

    // ── Razorpay order (KEPT INTACT — for rollback) ────────────────────────────
    private val _orderResult = MutableLiveData<FmResult<RazorpayOrderData>>()
    val orderResult: LiveData<FmResult<RazorpayOrderData>> = _orderResult

    fun createRazorpayOrder(teacherId: String, studentId: String, amountInINR: Int) {
        _orderResult.value = FmResult.Loading
        viewModelScope.launch {
            try {
                val session = SupabaseManager.client.auth.currentSessionOrNull()
                val token = session?.accessToken ?: throw Exception("Not logged in")
                val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

                val client = HttpClient()
                val response = client.post("https://vtpguytfeqbpysxbppyv.supabase.co/functions/v1/create-payment-order") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""
                        {
                            "student_id": "$studentId",
                            "teacher_id": "$teacherId",
                            "amount": $amountInINR,
                            "month_key": "$currentMonth"
                        }
                    """.trimIndent())
                }

                val responseBody = response.bodyAsText()
                val json = JSONObject(responseBody)

                if (response.status.value in 200..299) {
                    val orderId     = json.getString("order_id")
                    val keyId       = json.getString("key_id")
                    val academyName = json.getString("academy_name")
                    _orderResult.postValue(FmResult.Success(RazorpayOrderData(orderId, keyId, academyName, amountInINR)))
                } else {
                    val errorMsg = json.optString("error", "Failed to create order")
                    _orderResult.postValue(FmResult.Error(errorMsg))
                }
            } catch (e: Exception) {
                _orderResult.postValue(FmResult.Error(e.message ?: "Network error"))
            }
        }
    }

    // ── Cashfree order (NEW — active when PAYMENT_PROVIDER = CASHFREE) ─────────
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

    // ── Receipt generation (shared by both providers) ──────────────────────────
    private val _paymentResult = MutableLiveData<FmResult<PaymentSummary>>()
    val paymentResult: LiveData<FmResult<PaymentSummary>> = _paymentResult

    fun generateLocalReceipt(
        studentName:  String,
        payAmount:    Int,
        totalPending: Int,
        transactionId: String,
        provider:     String = if (AppPaymentConfig.isCashfree) "Cashfree" else "Razorpay"
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

// ── Razorpay order data (KEPT INTACT) ─────────────────────────────────────────
data class RazorpayOrderData(
    val orderId:    String,
    val keyId:      String,
    val academyName: String,
    val amountInINR: Int
)
