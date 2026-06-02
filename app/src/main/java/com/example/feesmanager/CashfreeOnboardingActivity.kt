package com.example.feesmanager

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import com.example.feesmanager.data.FmResult
import com.example.feesmanager.data.model.CashfreeVendorStatus
import com.example.feesmanager.ui.payment.CashfreeSetupViewModel

/**
 * CashfreeOnboardingActivity — Teacher payment setup screen for Cashfree Easy Split.
 *
 * Exists ALONGSIDE RazorpayOnboardingActivity (which is NOT touched).
 * DashboardActivity routes to this screen when AppPaymentConfig.isCashfree == true.
 *
 * Flow:
 *   1. Teacher enters: Account Holder Name, Account Number, IFSC, PAN Number, Phone
 *   2. Calls create-cashfree-vendor Edge Function via CashfreeRepository
 *   3. Shows: Vendor ID, Vendor Status, KYC Status, Settlement Status
 */
class CashfreeOnboardingActivity : BaseActivity() {

    private val viewModel: CashfreeSetupViewModel by viewModels()

    // ── Connected state views ─────────────────────────────────────────────────
    private lateinit var layoutConnected:    View
    private lateinit var tvVendorId:         TextView
    private lateinit var tvVendorStatus:     TextView
    private lateinit var tvKycStatus:        TextView
    private lateinit var tvSettlementStatus: TextView
    private lateinit var btnDisconnect:      Button

    // ── Not-connected state views ─────────────────────────────────────────────
    private lateinit var layoutNotConnected: View
    private lateinit var etAccountName:      EditText
    private lateinit var etAccountNumber:    EditText
    private lateinit var etIfscCode:         EditText
    private lateinit var etPanNumber:        EditText
    private lateinit var etPhone:            EditText
    private lateinit var btnSetup:           Button

    // ── Shared ────────────────────────────────────────────────────────────────
    private lateinit var progressBar:        ProgressBar

    private var teacherId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cashfree_onboarding)

        teacherId = SessionManager.getTeacherId(this) ?: run { finish(); return }

        // Connected state
        layoutConnected    = findViewById(R.id.cfLayoutConnected)
        tvVendorId         = findViewById(R.id.cfTvVendorId)
        tvVendorStatus     = findViewById(R.id.cfTvVendorStatus)
        tvKycStatus        = findViewById(R.id.cfTvKycStatus)
        tvSettlementStatus = findViewById(R.id.cfTvSettlementStatus)
        btnDisconnect      = findViewById(R.id.cfBtnDisconnect)

        // Not connected state
        layoutNotConnected = findViewById(R.id.cfLayoutNotConnected)
        etAccountName      = findViewById(R.id.cfEtAccountName)
        etAccountNumber    = findViewById(R.id.cfEtAccountNumber)
        etIfscCode         = findViewById(R.id.cfEtIfscCode)
        etPanNumber        = findViewById(R.id.cfEtPanNumber)
        etPhone            = findViewById(R.id.cfEtPhone)
        btnSetup           = findViewById(R.id.cfBtnSetup)

        progressBar        = findViewById(R.id.cfProgressBar)

        findViewById<View>(R.id.cfBtnBack).setOnClickListener { finish() }

        observeViewModel()
        setupButtons()

        // Load current status
        viewModel.loadVendorStatus(teacherId)
        viewModel.loadSettlementStatus(teacherId)
    }

    private fun observeViewModel() {
        viewModel.vendorStatus.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    layoutConnected.visibility    = View.GONE
                    layoutNotConnected.visibility = View.GONE
                }
                is FmResult.Success -> {
                    progressBar.visibility = View.GONE
                    val status = result.content
                    if (status.isNotStarted) {
                        showNotConnectedState()
                    } else {
                        showConnectedState(status)
                    }
                }
                is FmResult.Error -> {
                    progressBar.visibility = View.GONE
                    showNotConnectedState()
                    toast("Could not load status: ${result.message}")
                }
            }
        }

        viewModel.createResult.observe(this) { result ->
            when (result) {
                is FmResult.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    btnSetup.isEnabled = false
                    btnSetup.text = "Setting up…"
                }
                is FmResult.Success -> {
                    progressBar.visibility = View.GONE
                    btnSetup.isEnabled = true
                    btnSetup.text = "✅ Setup Complete"
                    toast("Payment setup successful! KYC is under review.")
                    showConnectedState(result.content)
                    setResult(RESULT_OK)
                }
                is FmResult.Error -> {
                    progressBar.visibility = View.GONE
                    btnSetup.isEnabled = true
                    btnSetup.text = "🔗 Submit & Activate Payments"
                    toast("Setup failed: ${result.message}")
                }
            }
        }

        viewModel.settlementStatus.observe(this) { (status, utr) ->
            val display = when (status) {
                "pending"                     -> "Pending"
                "VENDOR_SETTLEMENT_INITIATED" -> "🔄 Transfer Initiated"
                "VENDOR_SETTLEMENT_SUCCESS"   -> "✅ Settled"
                "VENDOR_SETTLEMENT_REVERSED"  -> "⚠️ Reversed"
                else                          -> status
            }
            tvSettlementStatus.text = "Settlement: $display${if (utr.isNotEmpty()) "  (UTR: $utr)" else ""}"
        }
    }

    private fun setupButtons() {
        btnSetup.setOnClickListener {
            val name   = etAccountName.text.toString().trim()
            val number = etAccountNumber.text.toString().trim()
            val ifsc   = etIfscCode.text.toString().trim().uppercase()
            val pan    = etPanNumber.text.toString().trim().uppercase()
            val phone  = etPhone.text.toString().trim()

            when {
                name.isEmpty()          -> { toast("Enter account holder name"); return@setOnClickListener }
                number.isEmpty()        -> { toast("Enter account number"); return@setOnClickListener }
                ifsc.length != 11       -> { toast("IFSC must be 11 characters"); return@setOnClickListener }
                pan.length != 10        -> { toast("PAN must be 10 characters (e.g. ABCDE1234F)"); return@setOnClickListener }
                !pan.matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]")) -> {
                    toast("Invalid PAN format (e.g. ABCDE1234F)")
                    return@setOnClickListener
                }
                phone.length < 10       -> { toast("Enter valid 10-digit phone number"); return@setOnClickListener }
                else -> viewModel.createVendor(name, number, ifsc, pan, phone)
            }
        }

        btnDisconnect.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Disconnect Payment Setup?")
                .setMessage("Students will not be able to pay online until you reconnect. You will need to re-enter your details.")
                .setPositiveButton("Disconnect") { _, _ -> showNotConnectedState() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showConnectedState(status: CashfreeVendorStatus) {
        layoutConnected.visibility    = View.VISIBLE
        layoutNotConnected.visibility = View.GONE

        tvVendorId.text     = "Vendor ID: ${status.cashfreeVendorId.takeLast(12)}"
        tvVendorStatus.text = "Vendor: ${status.vendorStatusDisplay()}"
        tvKycStatus.text    = "KYC: ${status.kycStatusDisplay()}"

        // Color coding
        tvVendorStatus.setTextColor(
            if (status.isActive) 0xFF22C55E.toInt() else 0xFFFFB800.toInt()
        )
        tvKycStatus.setTextColor(
            when (status.kycStatus) {
                "VERIFIED" -> 0xFF22C55E.toInt()
                "REJECTED" -> 0xFFEF4444.toInt()
                else       -> 0xFFFFB800.toInt()
            }
        )
    }

    private fun showNotConnectedState() {
        layoutConnected.visibility    = View.GONE
        layoutNotConnected.visibility = View.VISIBLE
        btnSetup.isEnabled = true
        btnSetup.text = "🔗 Submit & Activate Payments"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
