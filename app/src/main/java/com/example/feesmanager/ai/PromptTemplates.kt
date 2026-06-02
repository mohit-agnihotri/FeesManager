package com.example.feesmanager.ai

/**
 * PromptTemplates — System prompts and quick-action templates
 * for the Teacher AI Agent.
 */
object PromptTemplates {

    // ── Teacher AI Agent System Prompt ────────────────────────────────────

    const val TEACHER_AGENT_SYSTEM = """You are an AI Agent for academy/coaching institute teachers managing student fees, enrollments, and communications.

You are NOT just a chatbot — you are a SMART AGENT that analyzes data AND suggests executable actions.

RULES:
1. Analyze the provided data carefully — fee records, student counts, payment patterns, join requests.
2. Detect language automatically (Hindi/English/Hinglish). Match the teacher's language.
3. Provide structured, clear insights with:
   - 📊 Numbers and percentages
   - 🔴 URGENT issues (highlight with [PRIORITY:URGENT])
   - 🟠 HIGH priority items (highlight with [PRIORITY:HIGH])
   - 🟡 Normal observations (highlight with [PRIORITY:NORMAL])
4. When suggesting EXECUTABLE actions, use this EXACT format:
   [ACTION:type|label|description]
   
   Available action types:
   - SEND_REMINDER    → Post a fee reminder to all students
   - POST_ANNOUNCEMENT → Create a new announcement
   - NOTIFY_GROUP     → Notify a specific class/group
   - APPROVE_JOIN     → Approve pending student join requests
   - REJECT_JOIN      → Reject a pending join request
   - GENERATE_REPORT  → Generate a formatted report
   
   Examples:
   [ACTION:SEND_REMINDER|Send Fee Reminder|Remind 12 students with pending fees for April]
   [ACTION:POST_ANNOUNCEMENT|Monthly Update|Post monthly fee collection summary to all students]
   [ACTION:APPROVE_JOIN|Approve 3 Students|Approve all 3 pending join requests]

5. IMPORTANT: Actions will require teacher confirmation before execution. Suggest confidently.
6. Group similar problems together with counts.
7. Prioritize issues by urgency and number of affected students.
8. Suggest specific, actionable steps — not vague advice.
9. Use tables when comparing data across multiple categories.
10. Be professional but approachable. Use emojis sparingly for clarity.
11. For reports, use clean formatting with headers, bullet points, and tables.
12. When analyzing trends, compare current data with historical averages.

Always provide at least one actionable suggestion with [ACTION:...] tags."""

    // ── Teacher Quick Action Prompts ────────────────────────────────────────

    val TEACHER_QUICK_ACTIONS = listOf(
        QuickAction(
            id = "summarize_fees",
            emoji = "📊",
            label = "Fee Summary",
            prompt = "Analyze the current fee data and provide a complete summary: total students, paid vs pending counts, total collected amount, total pending amount, collection rate by class, and highlight any urgent payment issues. Suggest relevant actions."
        ),
        QuickAction(
            id = "detect_issues",
            emoji = "🔍",
            label = "Detect Issues",
            prompt = "Identify all problems: students with long-overdue fees, pending join requests, unusual patterns, low collection rates. Prioritize by urgency and suggest specific actions to resolve each issue."
        ),
        QuickAction(
            id = "generate_reminder",
            emoji = "📢",
            label = "Send Reminder",
            prompt = "Generate a fee payment reminder for students with pending fees. Make it polite but firm. Include the deadline. Provide it as a ready-to-send announcement with an [ACTION:SEND_REMINDER] tag."
        ),
        QuickAction(
            id = "payment_report",
            emoji = "📋",
            label = "Full Report",
            prompt = "Create a detailed payment report: class-wise collection, top defaulters, overall collection percentage, and pending join requests. Use tables where possible. Add a [ACTION:GENERATE_REPORT] tag."
        ),
        QuickAction(
            id = "auto_fix",
            emoji = "⚡",
            label = "Auto-Fix Issues",
            prompt = "Based on the current data, identify ALL fixable issues and suggest ONE ACTION for each:\n- Pending join requests → suggest APPROVE_JOIN\n- Overdue fees → suggest SEND_REMINDER\n- Low collection classes → suggest NOTIFY_GROUP\nProvide all actions with proper [ACTION:...] tags so I can execute them with one click each."
        ),
        QuickAction(
            id = "defaulter_analysis",
            emoji = "⚠️",
            label = "Defaulter Analysis",
            prompt = "List all fee defaulters grouped by class. Show each student's pending amount, how many months overdue, and suggest targeted actions: reminders for mild cases, direct messaging for severe cases."
        )
    )
}

/**
 * QuickAction — A pre-built prompt chip shown in the Teacher AI interface.
 */
data class QuickAction(
    val id: String,
    val emoji: String,
    val label: String,
    val prompt: String
)
