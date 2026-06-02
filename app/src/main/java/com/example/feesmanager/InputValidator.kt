package com.example.feesmanager

/**
 * InputValidator — sanitizes & validates all user input
 * Prevents injection attacks and bad data in Firebase
 */
object InputValidator {

    // Strip HTML/script injection characters
    fun sanitize(input: String): String {
        return input
            .replace("<", "")
            .replace(">", "")
            .replace("\"", "")
            .replace("'", "")
            .replace(";", "")
            .replace("--", "")
            .replace("/*", "")
            .replace("*/", "")
            .trim()
    }

    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    fun isValidPhone(phone: String): Boolean {
        val digits = phone.filter { it.isDigit() }
        return digits.length in 10..13
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 6
    }

    fun isValidName(name: String): Boolean {
        val cleaned = sanitize(name)
        if (cleaned.isEmpty() || cleaned.length > 100) return false
        // Allow Unicode letters (Hindi, Tamil, Telugu, etc.), digits, spaces, dots, hyphens, and apostrophes
        return cleaned.all { it.isLetterOrDigit() || it in " .-'" }
    }

    fun isValidAmount(amount: String): Boolean {
        val n = amount.toIntOrNull() ?: return false
        return n in 1..10_00_000 // max 10 lakh per transaction
    }

    fun isValidJoinCode(code: String): Boolean {
        return code.length == 6 && code.matches(Regex("[A-Z0-9]+"))
    }

    fun isValidRazorpayKey(key: String): Boolean {
        return key.startsWith("rzp_test_") || key.startsWith("rzp_live_")
    }
}
