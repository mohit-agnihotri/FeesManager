package com.example.feesmanager.ai.student

import android.util.Log

/**
 * StudentFaqMatcher — Zero-cost FAQ engine for students.
 *
 * Uses keyword matching to answer common student questions without
 * any AI API calls. Supports English, Hindi, and Hinglish queries.
 *
 * Flow:
 *   Student Question → Normalize → Keyword Match → Template Answer
 *   No AI. No API cost. Instant. Infinite scalability.
 */
object StudentFaqMatcher {

    private const val TAG = "StudentFaq"

    /**
     * Attempts to match a student question against known FAQs.
     * Returns a FaqAnswer if matched, null if no match found.
     */
    fun findAnswer(question: String): FaqAnswer? {
        val q = question.lowercase().trim()

        for (faq in FAQ_DATABASE) {
            val score = calculateMatchScore(q, faq.patterns)
            if (score >= faq.threshold) {
                Log.d(TAG, "✅ FAQ Match (score: $score): ${faq.id}")
                return FaqAnswer(
                    answer = faq.answer,
                    category = faq.category,
                    followUpSuggestions = faq.followUps
                )
            }
        }

        Log.d(TAG, "❌ No FAQ match for: ${q.take(50)}")
        return null
    }

    /**
     * Returns suggested questions to show as chips.
     */
    fun getSuggestedQuestions(): List<SuggestedQuestion> {
        return SUGGESTED_QUESTIONS
    }

    // ── Matching Engine ───────────────────────────────────────────────────

    /**
     * Calculates a match score (0.0 to 1.0) between a query and FAQ patterns.
     * Score is based on how many patterns match the query.
     */
    private fun calculateMatchScore(query: String, patterns: List<String>): Float {
        if (patterns.isEmpty()) return 0f

        var matchCount = 0
        for (pattern in patterns) {
            if (query.contains(pattern, ignoreCase = true)) {
                matchCount++
            }
        }

        // At least one pattern must match
        return if (matchCount > 0) {
            // Score = matched patterns / total patterns, but cap at 1.0
            // We use a lower threshold because even 1 strong match is often enough
            matchCount.toFloat() / patterns.size.coerceAtMost(3).toFloat()
        } else 0f
    }

    // ── FAQ Database ──────────────────────────────────────────────────────

    private val FAQ_DATABASE = listOf(
        // ── Fee Payment ──
        FaqEntry(
            id = "pay_fees",
            patterns = listOf("how to pay", "pay fees", "fees kaise", "payment kaise", "paisa kaise de", "fee bharna"),
            answer = """## 💳 How to Pay Fees

1. Open your **Dashboard**
2. Tap the **"Pay Fees Now"** button
3. Select the month you want to pay for
4. Enter the amount
5. Choose your payment method
6. Confirm the payment

✅ Your payment will be recorded instantly and your teacher will be notified.

💡 *You can also pay advance fees from the Advance Balance section.*""",
            category = FaqCategory.FEES,
            followUps = listOf("What is my pending fee?", "Can I pay in installments?"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "pending_fees",
            patterns = listOf("pending", "kitna bacha", "how much", "remaining", "due", "baaki", "kitna dena"),
            answer = """## 💰 Check Your Pending Fees

Your pending fee amount is shown right on your **Dashboard**:
• **Red card** = Pending amount (current + previous months)
• **Green card** = Amount already paid this month

For a detailed month-by-month breakdown:
1. Tap **📜 History** on your dashboard
2. Or tap **📅 Calendar** to see a visual view

💡 *If you see any discrepancy, tap **Contact Teacher** to clarify.*""",
            category = FaqCategory.FEES,
            followUps = listOf("How to pay fees?", "Contact teacher"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "fee_receipt",
            patterns = listOf("receipt", "raseed", "proof", "payment proof", "receipt download"),
            answer = """## 🧾 Fee Receipt

Your fee receipts are available in the **Payment History** section:

1. Tap **📜 History** on your dashboard
2. Find the payment you need a receipt for
3. Your receipt will show all payment details

💡 *You can show this to your teacher as proof of payment.*""",
            category = FaqCategory.FEES,
            followUps = listOf("View payment history", "Contact teacher"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "installments",
            patterns = listOf("installment", "partial", "kist", "thoda thoda", "half payment", "part payment"),
            answer = """## 📊 Partial & Installment Payments

Yes! You can pay your fees in installments:

1. Go to **Pay Fees**
2. Enter the partial amount you want to pay
3. The remaining balance will show as pending

Your teacher can see all partial payments. The system tracks your total paid vs total due automatically.

💡 *Contact your teacher if you need a special payment arrangement.*""",
            category = FaqCategory.FEES,
            followUps = listOf("How to pay fees?", "Check pending fees"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "advance_payment",
            patterns = listOf("advance", "advance balance", "extra payment", "pehle se pay", "advance fees"),
            answer = """## ⬆️ Advance Balance

If you pay more than your monthly fee, the extra amount is stored as **Advance Balance**.

This advance will automatically be applied to future months' fees.

To check your advance balance:
• Look at the **Advance Balance card** on your Dashboard

To add advance payment:
• Tap the Advance Balance card → Add advance""",
            category = FaqCategory.FEES,
            followUps = listOf("How to pay fees?", "Check pending fees"),
            threshold = 0.33f
        ),

        // ── Enrollment & Class ──
        FaqEntry(
            id = "join_class",
            patterns = listOf("join class", "class join", "how to join", "enroll", "kaise join", "class code", "admission"),
            answer = """## 🏫 How to Join a Class

1. Ask your teacher for the **Class Code**
2. Go to **Join Class** screen
3. Enter the class code
4. Submit your join request
5. Wait for your teacher to approve

⏳ Your teacher will review and approve your request. You'll see your dashboard once approved.

💡 *If your request is pending for too long, contact your teacher directly.*""",
            category = FaqCategory.ENROLLMENT,
            followUps = listOf("My request is pending", "Contact teacher"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "pending_approval",
            patterns = listOf("pending approval", "waiting", "request pending", "not approved", "kab approve", "intezaar"),
            answer = """## ⏳ Pending Approval

Your join request is waiting for your teacher's approval. This is normal!

**What to do:**
• Be patient — teachers may take 1-2 days to approve
• If it's been more than 3 days, contact your teacher
• Make sure you joined the **correct class code**

💡 *You can tap **Contact Teacher** from the waiting screen to send a reminder.*""",
            category = FaqCategory.ENROLLMENT,
            followUps = listOf("Contact teacher", "Re-submit join request"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "switch_academy",
            patterns = listOf("switch academy", "change class", "multiple academy", "doosra class", "academy change"),
            answer = """## 🏫 Multiple Academies

You can be enrolled in **multiple academies** at the same time!

• Tap **🏫 My Academies** on your dashboard
• Switch between different academy dashboards
• Each academy has separate fee tracking

💡 *To join a new academy, you'll need the class code from that teacher.*""",
            category = FaqCategory.ENROLLMENT,
            followUps = listOf("How to join a class?", "Leave an academy"),
            threshold = 0.33f
        ),

        // ── App Help ──
        FaqEntry(
            id = "contact_teacher",
            patterns = listOf("contact teacher", "message teacher", "teacher se baat", "teacher ko message", "help teacher"),
            answer = """## ✉️ Contact Your Teacher

1. Tap **Contact Teacher** on your dashboard
2. Type your message
3. Send it directly to your teacher

Your teacher will see your message and can respond from their dashboard.

💡 *For urgent matters, you can also use the **Class Chat** to reach out.*""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Class chat", "Announcements"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "class_chat",
            patterns = listOf("class chat", "group chat", "sabse baat", "class group", "chat kaise"),
            answer = """## 👥 Class Chat

Class Chat lets you communicate with everyone in your class:

1. Tap **👥 Class Chat** on your dashboard
2. Send messages visible to all classmates and your teacher

⚠️ Keep it respectful and on-topic!

💡 *For private queries, use **Contact Teacher** instead.*""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Contact teacher", "Announcements"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "announcements",
            patterns = listOf("announcement", "notice", "update", "suchna", "teacher ki update", "news"),
            answer = """## 📢 Announcements

Your teacher posts important updates in Announcements:

1. Tap **📢 Announcements** on your dashboard
2. See all notices (fee reminders, schedule changes, etc.)

These are one-way messages from your teacher — you can read but not reply.

💡 *Check announcements regularly for fee deadlines!*""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Contact teacher", "Pay fees"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "profile_photo",
            patterns = listOf("profile photo", "photo change", "avatar", "picture", "dp change", "photo badlo"),
            answer = """## 📸 Change Profile Photo

1. Tap your **profile picture** in the top-left of your dashboard
2. Select a photo from your gallery
3. It will be uploaded automatically

✅ Your new photo will be visible to your teacher and classmates.

💡 *Use a clear, appropriate photo for your academic profile.*""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Dashboard help", "Settings"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "payment_history",
            patterns = listOf("history", "past payments", "purane payment", "payment record", "itihaas"),
            answer = """## 📜 Payment History

View all your past payments:

1. Tap **📜 History** on your dashboard
2. See month-by-month payment records
3. Each entry shows: amount, date, and status

💡 *Use the **📅 Calendar** view for a visual overview of your payment timeline.*""",
            category = FaqCategory.FEES,
            followUps = listOf("Fee receipt", "Pay fees"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "fee_deadline",
            patterns = listOf("deadline", "last date", "due date", "kab tak", "time limit", "akhri tarikh"),
            answer = """## ⏰ Fee Deadline

Fee deadlines are set by your teacher and communicated through:
• **📢 Announcements** — check for deadline notices
• **Direct messages** — your teacher may send reminders

💡 *There's no fixed deadline in the app — it depends on your teacher's policy. Check Announcements regularly!*""",
            category = FaqCategory.FEES,
            followUps = listOf("Announcements", "Contact teacher"),
            threshold = 0.33f
        ),

        // ── Account ──
        FaqEntry(
            id = "logout",
            patterns = listOf("logout", "sign out", "log out", "account switch", "bahar niklo", "switch account"),
            answer = """## 🔄 Logout / Switch Account

1. Tap the **🔄 Switch** button in the top-right of your dashboard
2. You'll be logged out and taken to the role selection screen
3. You can log back in with a different account

⚠️ Your data is safe — logging out doesn't delete anything.""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Join a new class", "Login help"),
            threshold = 0.33f
        ),

        FaqEntry(
            id = "forgot_password",
            patterns = listOf("forgot password", "password reset", "password bhool", "login problem", "cannot login"),
            answer = """## 🔑 Account & Login Issues

If you're having trouble logging in:

1. Make sure you're using the **correct email/phone**
2. Try the **"Forgot Password"** option on the login screen
3. Check your email for a password reset link

💡 *If you're still stuck, ask your teacher to verify your enrollment status.*""",
            category = FaqCategory.APP_HELP,
            followUps = listOf("Contact teacher", "Join a class"),
            threshold = 0.33f
        )
    )

    private val SUGGESTED_QUESTIONS = listOf(
        SuggestedQuestion("💳", "How to pay fees?", "How to pay fees?"),
        SuggestedQuestion("💰", "My pending fees", "What is my pending fee amount?"),
        SuggestedQuestion("📜", "Payment history", "How to check payment history?"),
        SuggestedQuestion("🏫", "Join a class", "How to join a class?"),
        SuggestedQuestion("✉️", "Contact teacher", "How to contact my teacher?"),
        SuggestedQuestion("📢", "Announcements", "How to check announcements?")
    )

    // ── Data Classes ──────────────────────────────────────────────────────

    data class FaqEntry(
        val id: String,
        val patterns: List<String>,
        val answer: String,
        val category: FaqCategory,
        val followUps: List<String> = emptyList(),
        val threshold: Float = 0.33f
    )

    data class FaqAnswer(
        val answer: String,
        val category: FaqCategory,
        val followUpSuggestions: List<String> = emptyList()
    )

    data class SuggestedQuestion(
        val emoji: String,
        val label: String,
        val fullQuestion: String
    )

    enum class FaqCategory {
        FEES, ENROLLMENT, APP_HELP, GENERAL
    }
}
