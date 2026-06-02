package com.example.feesmanager

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.feesmanager.data.SupabaseManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.json.JSONObject

class RazorpayOnboardingActivity : BaseActivity() {

    private lateinit var etAccountName: EditText
    private lateinit var etAccountNumber: EditText
    private lateinit var etIfscCode: EditText
    private lateinit var btnConnect: Button
    private lateinit var tvConnectionStatus: TextView
    private lateinit var btnDisconnect: Button
    private lateinit var tvAccountId: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutConnected: View
    private lateinit var layoutNotConnected: View

    private var teacherId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_razorpay_onboarding)

        teacherId = SessionManager.getTeacherId(this) ?: run {
            finish()
            return
        }

        etAccountName = findViewById(R.id.etAccountName)
        etAccountNumber = findViewById(R.id.etAccountNumber)
        etIfscCode = findViewById(R.id.etIfscCode)
        btnConnect = findViewById(R.id.btnConnectRazorpay)
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)

        progressBar = findViewById(R.id.progressOnboarding)
        layoutConnected = findViewById(R.id.layoutConnected)
        layoutNotConnected = findViewById(R.id.layoutNotConnected)
        tvAccountId = findViewById(R.id.tvConnectedAccountId)
        btnDisconnect = findViewById(R.id.btnDisconnect)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        loadCurrentStatus()
        setupButtons()
    }

    private fun loadCurrentStatus() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val row = SupabaseManager.client.postgrest
                    .from("teachers")
                    .select(Columns.raw("razorpay_account_id, razorpay_onboarding_status")) {
                        filter { eq("id", teacherId) }
                    }.decodeSingleOrNull<RazorpayStatusRow>()

                progressBar.visibility = View.GONE

                val accountId = row?.razorpay_account_id
                if (!accountId.isNullOrEmpty() && row.razorpay_onboarding_status == "activated") {
                    showConnectedState(accountId)
                } else {
                    showNotConnectedState()
                }

            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showNotConnectedState()
            }
        }
    }

    private fun showConnectedState(accountId: String) {
        layoutConnected.visibility = View.VISIBLE
        layoutNotConnected.visibility = View.GONE

        // Mask account ID for privacy
        val masked = if (accountId.length > 8) "acc_..." + accountId.takeLast(4) else accountId
        tvAccountId.text = "Account: $masked"
        tvConnectionStatus.text = "✅ Payments Active"
        tvConnectionStatus.setTextColor(0xFF22C55E.toInt())
    }

    private fun showNotConnectedState() {
        layoutConnected.visibility = View.GONE
        layoutNotConnected.visibility = View.VISIBLE
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener {
            val name = etAccountName.text.toString().trim()
            val number = etAccountNumber.text.toString().trim()
            val ifsc = etIfscCode.text.toString().trim().uppercase()

            if (name.isEmpty() || number.isEmpty() || ifsc.isEmpty()) {
                toast("Please fill all bank details")
                return@setOnClickListener
            }

            if (ifsc.length != 11) {
                toast("IFSC code must be 11 characters")
                return@setOnClickListener
            }

            connectBankAccount(name, number, ifsc)
        }

        btnDisconnect.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Disconnect Account?")
                .setMessage("Students will not be able to pay online until you reconnect. You will need to enter your bank details again.")
                .setPositiveButton("Disconnect") { _, _ ->
                    disconnectAccount()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun connectBankAccount(name: String, number: String, ifsc: String) {
        btnConnect.isEnabled = false
        btnConnect.text = "Connecting..."
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Get session for Edge Function authorization
                val session = SupabaseManager.client.auth.currentSessionOrNull()

                android.util.Log.d("RAZORPAY_DEBUG", "SESSION = ${session != null}")
                android.util.Log.d("RAZORPAY_DEBUG", "TOKEN = ${session?.accessToken}")
                android.util.Log.d("RAZORPAY_DEBUG", "USER = ${session?.user?.id}")

                val token = session?.accessToken ?: throw Exception("Not logged in - access token is null")

                // Call Supabase Edge Function
                val client = HttpClient()
                android.util.Log.d(
                    "RAZORPAY_DEBUG",
                    "AUTH HEADER = Bearer $token"
                )
                val response = client.post("https://vtpguytfeqbpysxbppyv.supabase.co/functions/v1/create-linked-account") {
                    header("Authorization", "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody("""
                        {
                            "account_name": "$name",
                            "account_number": "$number",
                            "ifsc": "$ifsc"
                        }
                    """.trimIndent())
                }

                val responseBody = response.bodyAsText()
                val json = JSONObject(responseBody)

                if (response.status.value in 200..299) {
                    val accountId = json.getString("account_id")
                    toast("✅ Bank Account Connected!")
                    showConnectedState(accountId)
                    setResult(RESULT_OK)
                } else {
                    val errorMsg = json.optString("error", "Unknown error")
                    toast("Connection failed: $errorMsg")
                }

            } catch (e: Exception) {
                toast("Connection failed: ${e.message}")
            } finally {
                btnConnect.isEnabled = true
                btnConnect.text = "🔗 Securely Connect Account"
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun disconnectAccount() {
        lifecycleScope.launch {
            try {
                SupabaseManager.client.postgrest
                    .from("teachers")
                    .update(
                        mapOf(
                            "razorpay_account_id" to null,
                            "razorpay_onboarding_status" to "not_started"
                        )
                    ) {
                        filter { eq("id", teacherId) }
                    }

                toast("Account disconnected")
                showNotConnectedState()
                setResult(RESULT_OK)
            } catch (e: Exception) {
                toast("Failed: ${e.message}")
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    @Serializable
    data class RazorpayStatusRow(
        val razorpay_account_id: String? = null,
        val razorpay_onboarding_status: String? = null
    )
}