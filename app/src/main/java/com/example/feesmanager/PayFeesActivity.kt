package com.example.feesmanager

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.CFSession.Environment
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.exception.CFException
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.ui.api.CFDropCheckoutPayment
import com.cashfree.pg.ui.api.CFPaymentComponent
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.StudentPaymentInfo
import com.example.feesmanager.data.repository.AuthRepository
import com.example.feesmanager.data.repository.CashfreeOrderData
import com.example.feesmanager.ui.fees.PayFeesViewModel
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject

/**
 * PayFeesActivity — Student fee payment screen.
 *
 * Supports BOTH Razorpay and Cashfree via AppPaymentConfig.PAYMENT_PROVIDER flag.
 * Switching providers: change AppPaymentConfig.PAYMENT_PROVIDER to "RAZORPAY" or "CASHFREE".
 * Neither implementation is deleted — both paths remain for instant rollback.
 */
class PayFeesActivity : BaseActivity(), PaymentResultWithDataListener, CFCheckoutResponseCallback {
    private val viewModel: PayFeesViewModel by viewModels()

    private lateinit var amountEt        : EditText
    private lateinit var payBtn          : Button
    private lateinit var tvPendingAmount : TextView
    private lateinit var tvStudentInfo   : TextView
    private lateinit var progressBar     : ProgressBar
    private lateinit var tvProviderNote  : TextView

    private var teacherId   = ""
    private var studentId   = ""
    private var payAmount   = 0
    private var currentInfo : StudentPaymentInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pay_fees)

        // Pre-warm Razorpay SDK only if using Razorpay (kept for rollback)
        if (AppPaymentConfig.isRazorpay) {
            Checkout.preload(applicationContext)
        }

        // Register Cashfree callback
        try {
            CFPaymentGatewayService.getInstance().setCheckoutCallback(this)
        } catch (_: Exception) {}

        val currentUid     = AuthRepository().getCurrentUserId()
        val savedStudentId = SessionManager.getStudentId(this)

        if (currentUid == null || (savedStudentId != null && currentUid != savedStudentId)) {
            Toast.makeText(this, "Session expired", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        amountEt        = findViewById(R.id.etAmount)
        payBtn          = findViewById(R.id.btnPayRazorpay)   // reuse existing button ID
        tvPendingAmount = findViewById(R.id.tvPendingAmount)
        tvStudentInfo   = findViewById(R.id.tvStudentInfo)
        progressBar     = findViewById(R.id.payProgress)
        tvProviderNote  = findViewById(R.id.tvPaymentProviderNote)

        // Update provider note label
        tvProviderNote.text = if (AppPaymentConfig.isCashfree)
            "🔒 Payments secured by Cashfree\nSupports UPI, Cards, Net Banking"
        else
            "🔒 Payments secured by Razorpay\nSupports UPI, Cards, Net Banking"

        findViewById<View>(R.id.btnPayRequest).visibility = View.GONE

        teacherId = SessionManager.getStudentTeacherId(this) ?: return
        studentId = SessionManager.getStudentId(this)        ?: return

        observeViewModel()
        viewModel.loadStudentInfo(teacherId, studentId)

        payBtn.setOnClickListener { startPaymentProcess() }
    }

    private fun observeViewModel() {
        viewModel.paymentInfo.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    payBtn.isEnabled = false
                }
                is FmResult.Success -> {
                    progressBar.visibility = View.GONE
                    currentInfo = result.content
                    val info = result.content

                    tvStudentInfo.text   = "👤 ${info.studentName} | Class ${info.className}"
                    tvPendingAmount.text = "Total Pending: ₹${info.totalPending}"
                    if (info.totalPending > 0) amountEt.setText(info.totalPending.toString())

                    if (!info.isPaymentEnabled) {
                        payBtn.text      = "Online Payment Not Available"
                        payBtn.isEnabled = false
                    } else {
                        payBtn.text      = "💳  Pay Online (UPI/Card)"
                        payBtn.isEnabled = true
                    }
                }
                is FmResult.Error -> {
                    progressBar.visibility = View.GONE
                    toast("Failed to load info: ${result.message}")
                }
            }
        }

        // ── Razorpay order result (KEPT INTACT) ───────────────────────────────
        viewModel.orderResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> { showLoading(true) }
                is FmResult.Success -> {
                    showLoading(false)
                    launchRazorpayCheckout(
                        result.content.orderId,
                        result.content.keyId,
                        result.content.academyName,
                        result.content.amountInINR
                    )
                }
                is FmResult.Error -> {
                    showLoading(false)
                    toast(result.message)
                }
            }
        }

        // ── Cashfree order result (NEW) ────────────────────────────────────────
        viewModel.cashfreeOrderResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> { showLoading(true) }
                is FmResult.Success -> {
                    showLoading(false)
                    launchCashfreeCheckout(result.content)
                }
                is FmResult.Error -> {
                    showLoading(false)
                    toast(result.message)
                }
            }
        }

        // ── Receipt result (shared) ────────────────────────────────────────────
        viewModel.paymentResult.observe(this) { result ->
            if (result is FmResult.Success) {
                val s = result.content
                if (s.advance > 0) toast("Payment Successful! 🎉 (₹${s.advance} saved as advance)")
                else               toast("Payment Successful! 🎉")

                startActivity(Intent(this, ReceiptActivity::class.java).apply {
                    putExtra("name",          s.studentName)
                    putExtra("amount",        s.amount.toString())
                    putExtra("total",         s.total.toString())
                    putExtra("paid",          s.paid.toString())
                    putExtra("remain",        s.remaining.toString())
                    putExtra("advance",       s.advance.toString())
                    putExtra("mode",          s.mode)
                    putExtra("transactionId", s.transactionId)
                    putExtra("academyName",   currentInfo?.academyName ?: "")
                    putExtra("className",     currentInfo?.className   ?: "")
                })
                finish()
            }
        }
    }

    private fun startPaymentProcess() {
        val info      = currentInfo ?: return
        val amountStr = amountEt.text.toString().trim()
        if (!InputValidator.isValidAmount(amountStr)) {
            toast("Enter valid amount (₹1 – ₹10,00,000)"); return
        }
        payAmount = amountStr.toInt()

        if (!info.isPaymentEnabled) {
            toast("Teacher has not enabled online payments."); return
        }

        // Feature flag: route to correct provider
        if (AppPaymentConfig.isCashfree) {
            viewModel.createCashfreeOrder(teacherId, studentId, payAmount)
        } else {
            viewModel.createRazorpayOrder(teacherId, studentId, payAmount)
        }
    }

    // ── Cashfree Checkout (NEW) ────────────────────────────────────────────────

    private fun launchCashfreeCheckout(orderData: CashfreeOrderData) {
        try {
            val cfEnv = if (AppPaymentConfig.CASHFREE_ENV == "PROD") Environment.PRODUCTION else Environment.SANDBOX

            val cfSession = CFSession.CFSessionBuilder()
                .setEnvironment(cfEnv)
                .setPaymentSessionID(orderData.paymentSessionId)
                .setOrderId(orderData.cashfreeOrderId)
                .build()

            val cfPayment = CFDropCheckoutPayment.CFDropCheckoutPaymentBuilder()
                .setSession(cfSession)
                .setCFUIPaymentModes(
                    CFPaymentComponent.CFPaymentComponentBuilder()
                        .add(CFPaymentComponent.CFPaymentModes.UPI)
                        .add(CFPaymentComponent.CFPaymentModes.CARD)
                        .add(CFPaymentComponent.CFPaymentModes.NB)
                        .add(CFPaymentComponent.CFPaymentModes.WALLET)
                        .build()
                )
                .build()

            CFPaymentGatewayService.getInstance().doPayment(this, cfPayment)

        } catch (e: CFException) {
            toast("Checkout error: ${e.message}")
        } catch (e: Exception) {
            toast("Could not open payment: ${e.message}")
        }
    }

    // ── Cashfree Callbacks ─────────────────────────────────────────────────────

    override fun onPaymentVerify(orderId: String) {
        // Called by Cashfree SDK on payment success
        val info = currentInfo ?: return
        viewModel.generateLocalReceipt(
            studentName   = info.studentName,
            payAmount     = payAmount,
            totalPending  = info.totalPending,
            transactionId = orderId,
            provider      = "Cashfree"
        )
    }

    override fun onPaymentFailure(cfErrorResponse: CFErrorResponse, orderId: String) {
        toast("Payment failed: ${cfErrorResponse.message}")
    }

    // ── Razorpay Callbacks (KEPT INTACT — for rollback) ───────────────────────

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val info      = currentInfo ?: return
        val paymentId = razorpayPaymentId ?: "TXN_${System.currentTimeMillis()}"
        viewModel.generateLocalReceipt(
            studentName   = info.studentName,
            payAmount     = payAmount,
            totalPending  = info.totalPending,
            transactionId = paymentId,
            provider      = "Razorpay"
        )
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        toast("Payment failed: $response")
    }

    // ── Razorpay Checkout (KEPT INTACT — for rollback) ────────────────────────

    private fun launchRazorpayCheckout(orderId: String, keyId: String, academyName: String, amountInINR: Int) {
        val checkout = Checkout()
        checkout.setKeyID(keyId)
        try {
            val options = JSONObject()
            options.put("name",        academyName)
            options.put("description", "Fees Payment")
            options.put("currency",    "INR")
            options.put("amount",      amountInINR * 100)  // Razorpay expects paise
            options.put("order_id",    orderId)

            val prefill = JSONObject()
            prefill.put("email",   currentInfo?.email   ?: "")
            prefill.put("contact", currentInfo?.whatsapp ?: "")
            options.put("prefill", prefill)

            checkout.open(this, options)
        } catch (e: Exception) {
            toast("Error launching payment: ${e.message}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        payBtn.isEnabled       = !loading
        payBtn.text            = if (loading) "Processing..." else "💳  Pay Online (UPI/Card)"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
