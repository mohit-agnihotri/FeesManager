package com.example.feesmanager.ui.payment

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.example.feesmanager.R
import com.example.feesmanager.base.BaseActivity
import com.example.feesmanager.ui.auth.SessionManager
import com.example.feesmanager.ui.fees.FeesEntryActivity
import com.example.feesmanager.ui.student.PendingStudentsActivity

/**
 * PaymentRequestsActivity — Redirects to PendingStudentsActivity
 * which handles both join requests and fee defaulters.
 *
 * The original payment_requests table doesn't exist in the Supabase schema.
 * Instead, this activity serves as a convenient shortcut to the pending/defaulters view.
 */
class PaymentRequestsActivity : BaseActivity() {

    private lateinit var listLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_requests)
        listLayout = findViewById(R.id.requestList)

        val teacherId = SessionManager.getTeacherId(this)
        if (teacherId == null) {
            showMessage("Please login as teacher first")
            return
        }

        // Show informational message and redirect button
        listLayout.removeAllViews()

        val infoText = TextView(this).apply {
            text = "📋 Payment & Student Requests\n\nView pending join requests and fee defaulters in one place."
            textSize = 16f
            setTextColor(0xFFF1F5F9.toInt())
            gravity = Gravity.CENTER
            setPadding(32, 64, 32, 32)
            setLineSpacing(0f, 1.5f)
        }
        listLayout.addView(infoText)

        val btnPending = Button(this).apply {
            text = "📥 View Pending Requests & Defaulters"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.bg_button_gradient)
            stateListAnimator = null
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(32, 16, 32, 16)
            layoutParams = lp
        }
        btnPending.setOnClickListener {
            startActivity(Intent(this, PendingStudentsActivity::class.java))
        }
        listLayout.addView(btnPending)

        val btnFees = Button(this).apply {
            text = "💰 Record Manual Payment"
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundResource(R.drawable.bg_button_gradient)
            stateListAnimator = null
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(32, 8, 32, 16)
            layoutParams = lp
        }
        btnFees.setOnClickListener {
            startActivity(Intent(this, FeesEntryActivity::class.java))
        }
        listLayout.addView(btnFees)
    }

    private fun showMessage(msg: String) {
        listLayout.removeAllViews()
        val tv = TextView(this).apply {
            text = msg; textSize = 16f; setTextColor(0xFF94A3B8.toInt()); gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 100, 0, 0); layoutParams = lp
        }
        listLayout.addView(tv)
    }
}