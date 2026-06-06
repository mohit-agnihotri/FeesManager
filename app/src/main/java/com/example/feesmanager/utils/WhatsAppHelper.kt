package com.example.feesmanager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {

    fun sendFeeReminder(
        context: Context,
        studentName: String,
        whatsapp: String,
        amount: Int,
        month: String,
        academyName: String
    ) {
        val message = """
Dear $studentName,

This is a reminder from $academyName.

📅 Month: $month
💰 Fees Due: ₹$amount

Please pay at your earliest convenience.

Thank you! 🙏
Fees Manager Pro
        """.trimIndent()

        val number = whatsapp.replace("+", "").replace(" ", "").replace("-", "")
        val finalNumber = if (number.startsWith("91")) number else "91$number"

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$finalNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendPaymentConfirmation(
        context: Context,
        studentName: String,
        whatsapp: String,
        amount: Int,
        academyName: String
    ) {
        val message = """
Dear $studentName,

✅ Payment Confirmed!

Academy: $academyName
Amount Paid: ₹$amount

Thank you for your payment! 🙏
Fees Manager Pro
        """.trimIndent()

        val number = whatsapp.replace("+", "").replace(" ", "").replace("-", "")
        val finalNumber = if (number.startsWith("91")) number else "91$number"

        try {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$finalNumber&text=${Uri.encode(message)}")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }
}