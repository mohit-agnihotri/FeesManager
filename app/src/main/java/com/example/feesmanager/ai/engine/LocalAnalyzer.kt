package com.example.feesmanager.ai.engine

import com.example.feesmanager.ai.models.AiChatMessage
import com.example.feesmanager.ai.models.Priority
import com.example.feesmanager.ai.models.SuggestedAction

/**
 * LocalAnalyzer — Generates instant responses for common queries
 * WITHOUT calling any external API.
 *
 * Strategy:
 *   - Quick actions (fee summary, defaulters, reports) → LOCAL templates
 *   - Complex/custom questions → falls through to AI API
 *
 * Benefits:
 *   - ⚡ INSTANT response (no network latency)
 *   - 💰 ZERO API cost
 *   - 🔒 No rate limits
 *   - 📶 Works even with slow internet
 */
object LocalAnalyzer {

    /**
     * Tries to handle the query locally. Returns null if AI is needed.
     */
    fun tryAnalyze(query: String, context: TeacherDataContext): AiChatMessage? {
        val q = query.lowercase().trim()

        return when {
            // Fee Summary
            matchesAny(q, "fee summary", "fee status", "summarize", "fee data", "collection",
                "kitna fees", "fees summary", "total fees", "fee overview") ->
                generateFeeSummary(context)

            // Defaulter Analysis
            matchesAny(q, "defaulter", "overdue", "pending fees", "who hasn't paid",
                "kisne nahi diya", "defaulters", "bachche jinke paise") ->
                generateDefaulterReport(context)

            // Payment Report
            matchesAny(q, "payment report", "full report", "class-wise", "classwise",
                "report generate", "detailed report", "payment details") ->
                generatePaymentReport(context)

            // Detect Issues
            matchesAny(q, "detect issue", "problems", "issues", "kya problem",
                "urgent", "alerts", "kya galat", "check karo") ->
                generateIssueReport(context)

            // Auto-Fix
            matchesAny(q, "auto-fix", "auto fix", "fix issues", "fix all",
                "sab fix karo", "resolve", "action suggest") ->
                generateAutoFix(context)

            // Pending Join Requests
            matchesAny(q, "pending join", "join request", "approval", "approve",
                "new students", "naye students", "join pending") ->
                generateJoinStatus(context)

            // Otherwise → needs AI
            else -> null
        }
    }

    // ── Fee Summary ──────────────────────────────────────────────────────

    private fun generateFeeSummary(ctx: TeacherDataContext): AiChatMessage {
        val total = ctx.totalCollected + ctx.totalPending
        val paidStudents = ctx.totalStudents - ctx.defaulters.size

        val text = buildString {
            appendLine("## 📊 Fee Status Summary\n")
            appendLine("**Academy Overview:**")
            appendLine("• Total Students: **${ctx.totalStudents}**")
            appendLine("• Total Classes: **${ctx.totalClasses}**")
            appendLine("• Collection Rate: **${ctx.collectionRate}%**\n")

            appendLine("**Financial Summary:**")
            appendLine("• Total Fees Due: **₹$total**")
            appendLine("• Collected: **₹${ctx.totalCollected}** ✅")
            appendLine("• Pending: **₹${ctx.totalPending}** ⏳\n")

            appendLine("**Student Breakdown:**")
            appendLine("• Fully Paid: **$paidStudents** students ✅")
            appendLine("• With Pending Fees: **${ctx.defaulters.size}** students ⚠️")

            if (ctx.pendingJoinRequests > 0) {
                appendLine("• Pending Join Requests: **${ctx.pendingJoinRequests}** 🟡")
            }

            if (ctx.classStats.isNotEmpty()) {
                appendLine("\n**Class-wise Breakdown:**")
                ctx.classStats.forEach { (cls, data) ->
                    val count = ctx.classStudentCounts[cls] ?: 0
                    val classRate = if (data.first + data.second > 0)
                        (data.first * 100 / (data.first + data.second)) else 100
                    appendLine("• $cls: $count students, ₹${data.first} collected, ₹${data.second} pending ($classRate%)")
                }
            }

            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        val actions = mutableListOf<SuggestedAction>()
        if (ctx.defaulters.isNotEmpty()) {
            actions.add(
                SuggestedAction(
                    label = "Send Fee Reminder",
                    description = "Remind ${ctx.defaulters.size} students with pending fees",
                    type = SuggestedAction.ActionType.SEND_REMINDER
                )
            )
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions,
            priority = if (ctx.collectionRate < 50) Priority.URGENT
            else if (ctx.collectionRate < 75) Priority.HIGH
            else Priority.NORMAL
        )
    }

    // ── Defaulter Report ─────────────────────────────────────────────────

    private fun generateDefaulterReport(ctx: TeacherDataContext): AiChatMessage {
        val text = buildString {
            if (ctx.defaulters.isEmpty()) {
                appendLine("## ✅ No Defaulters!\n")
                appendLine("All students have paid their fees. Great job! 🎉")
                return@buildString
            }

            appendLine("## ⚠️ Defaulter Analysis\n")
            appendLine("**${ctx.defaulters.size} students** have pending fees totaling **₹${ctx.totalPending}**\n")

            // Group by severity
            val critical = ctx.defaulters.filter { it.overdueMonths >= 3 }
            val moderate = ctx.defaulters.filter { it.overdueMonths in 1..2 }
            val mild = ctx.defaulters.filter { it.overdueMonths == 0 }

            if (critical.isNotEmpty()) {
                appendLine("**🔴 CRITICAL (3+ months overdue): ${critical.size} students**")
                critical.take(10).forEach { d ->
                    appendLine("• ${d.studentName} — ₹${d.pendingAmount} (${d.overdueMonths} months)")
                }
                appendLine()
            }
            if (moderate.isNotEmpty()) {
                appendLine("**🟠 MODERATE (1-2 months overdue): ${moderate.size} students**")
                moderate.take(10).forEach { d ->
                    appendLine("• ${d.studentName} — ₹${d.pendingAmount} (${d.overdueMonths} months)")
                }
                appendLine()
            }
            if (mild.isNotEmpty()) {
                appendLine("**🟡 RECENT (current month): ${mild.size} students**")
                mild.take(10).forEach { d ->
                    appendLine("• ${d.studentName} — ₹${d.pendingAmount}")
                }
                appendLine()
            }

            // Top 5 by amount
            appendLine("**💰 Top 5 by Amount:**")
            ctx.defaulters.take(5).forEachIndexed { i, d ->
                appendLine("${i+1}. ${d.studentName} — ₹${d.pendingAmount}")
            }

            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        val actions = mutableListOf<SuggestedAction>()
        val critical = ctx.defaulters.filter { it.overdueMonths >= 3 }

        if (critical.isNotEmpty()) {
            actions.add(
                SuggestedAction(
                    label = "Urgent Reminder",
                    description = "Send urgent reminder to ${critical.size} students with 3+ months overdue",
                    type = SuggestedAction.ActionType.SEND_REMINDER,
                    priority = Priority.URGENT
                )
            )
        }
        if (ctx.defaulters.isNotEmpty()) {
            actions.add(
                SuggestedAction(
                    label = "Fee Reminder to All",
                    description = "Send general fee reminder to all ${ctx.defaulters.size} students with pending fees",
                    type = SuggestedAction.ActionType.SEND_REMINDER
                )
            )
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions,
            priority = if (critical.isNotEmpty()) Priority.URGENT else Priority.NORMAL
        )
    }

    // ── Payment Report ───────────────────────────────────────────────────

    private fun generatePaymentReport(ctx: TeacherDataContext): AiChatMessage {
        val total = ctx.totalCollected + ctx.totalPending
        val text = buildString {
            appendLine("## 📋 Detailed Payment Report\n")
            appendLine("**Overall:**")
            appendLine("• Total Revenue Expected: ₹$total")
            appendLine("• Collected: ₹${ctx.totalCollected} (${ctx.collectionRate}%)")
            appendLine("• Pending: ₹${ctx.totalPending} (${100 - ctx.collectionRate}%)")
            appendLine("• Students: ${ctx.totalStudents} (${ctx.defaulters.size} with dues)\n")

            if (ctx.classStats.isNotEmpty()) {
                appendLine("**Class-wise Performance:**")
                appendLine("| Class | Students | Collected | Pending | Rate |")
                appendLine("|-------|----------|-----------|---------|------|")
                ctx.classStats.forEach { (cls, data) ->
                    val count = ctx.classStudentCounts[cls] ?: 0
                    val classTotal = data.first + data.second
                    val rate = if (classTotal > 0) (data.first * 100 / classTotal) else 100
                    appendLine("| $cls | $count | ₹${data.first} | ₹${data.second} | $rate% |")
                }
                appendLine()
            }

            if (ctx.defaulters.isNotEmpty()) {
                appendLine("**Top Defaulters:**")
                ctx.defaulters.take(10).forEachIndexed { i, d ->
                    appendLine("${i+1}. ${d.studentName} — ₹${d.pendingAmount} (${d.overdueMonths} months overdue)")
                }
            }

            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = listOf(
                SuggestedAction(
                    label = "Export Report",
                    description = "Generate and export this report for sharing",
                    type = SuggestedAction.ActionType.GENERATE_REPORT
                )
            )
        )
    }

    // ── Issue Detection ──────────────────────────────────────────────────

    private fun generateIssueReport(ctx: TeacherDataContext): AiChatMessage {
        val issues = mutableListOf<String>()
        val actions = mutableListOf<SuggestedAction>()

        // Check collection rate
        if (ctx.collectionRate < 50) {
            issues.add("🔴 **CRITICAL**: Collection rate is only ${ctx.collectionRate}% — well below healthy 75%+")
        } else if (ctx.collectionRate < 75) {
            issues.add("🟠 **HIGH**: Collection rate is ${ctx.collectionRate}% — below healthy 75%+")
        }

        // Check overdue students
        val critical = ctx.defaulters.filter { it.overdueMonths >= 3 }
        if (critical.isNotEmpty()) {
            issues.add("🔴 **CRITICAL**: ${critical.size} students have fees overdue for 3+ months (₹${critical.sumOf { it.pendingAmount }} total)")
            actions.add(
                SuggestedAction(
                    label = "Send Urgent Reminder",
                    description = "Remind ${critical.size} critically overdue students",
                    type = SuggestedAction.ActionType.SEND_REMINDER,
                    priority = Priority.URGENT
                )
            )
        }

        // Check pending join requests
        if (ctx.pendingJoinRequests > 0) {
            issues.add("🟡 ${ctx.pendingJoinRequests} students awaiting join approval")
            val names = ctx.pendingStudents.take(5).joinToString(", ") { it.studentName }
            actions.add(
                SuggestedAction(
                    label = "Approve All (${ctx.pendingJoinRequests})",
                    description = "Approve pending requests from: $names",
                    type = SuggestedAction.ActionType.APPROVE_JOIN
                )
            )
        }

        // Check if more than half have pending fees
        if (ctx.defaulters.size > ctx.totalStudents / 2 && ctx.totalStudents > 0) {
            issues.add("🟠 **HIGH**: More than half the students (${ctx.defaulters.size}/${ctx.totalStudents}) have pending fees")
            actions.add(
                SuggestedAction(
                    label = "Mass Fee Reminder",
                    description = "Send reminder to all ${ctx.defaulters.size} students with pending fees",
                    type = SuggestedAction.ActionType.SEND_REMINDER
                )
            )
        }

        // Low-class performance
        ctx.classStats.forEach { (cls, data) ->
            val classTotal = data.first + data.second
            val rate = if (classTotal > 0) (data.first * 100 / classTotal) else 100
            if (rate < 50 && classTotal > 0) {
                issues.add("🟠 Class **$cls** has only $rate% collection rate")
            }
        }

        val text = buildString {
            if (issues.isEmpty()) {
                appendLine("## ✅ No Issues Detected!\n")
                appendLine("Everything looks good:")
                appendLine("• Collection rate: ${ctx.collectionRate}%")
                appendLine("• No overdue students")
                appendLine("• No pending join requests")
                appendLine("\nKeep up the great work! 🎉")
            } else {
                appendLine("## 🔍 ${issues.size} Issue${if (issues.size > 1) "s" else ""} Detected\n")
                issues.forEach { appendLine("• $it") }
            }
            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        val priority = when {
            issues.any { it.startsWith("🔴") } -> Priority.URGENT
            issues.any { it.startsWith("🟠") } -> Priority.HIGH
            else -> Priority.NORMAL
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions,
            priority = priority
        )
    }

    // ── Auto-Fix ─────────────────────────────────────────────────────────

    private fun generateAutoFix(ctx: TeacherDataContext): AiChatMessage {
        val actions = mutableListOf<SuggestedAction>()
        val fixes = mutableListOf<String>()

        // Fix 1: Approve pending joins
        if (ctx.pendingJoinRequests > 0) {
            fixes.add("✅ Approve ${ctx.pendingJoinRequests} pending join requests")
            actions.add(
                SuggestedAction(
                    label = "Approve All Joins",
                    description = "Approve ${ctx.pendingJoinRequests} pending student join requests",
                    type = SuggestedAction.ActionType.APPROVE_JOIN
                )
            )
        }

        // Fix 2: Send reminder to critical defaulters
        val critical = ctx.defaulters.filter { it.overdueMonths >= 3 }
        if (critical.isNotEmpty()) {
            fixes.add("📨 Send urgent reminder to ${critical.size} students with 3+ months overdue")
            actions.add(
                SuggestedAction(
                    label = "Urgent Fee Reminder",
                    description = "Urgent: ${critical.size} students have fees overdue 3+ months. Total pending: ₹${critical.sumOf { it.pendingAmount }}",
                    type = SuggestedAction.ActionType.SEND_REMINDER,
                    priority = Priority.URGENT
                )
            )
        }

        // Fix 3: General reminder if many defaulters
        val nonCritical = ctx.defaulters.filter { it.overdueMonths < 3 }
        if (nonCritical.isNotEmpty()) {
            fixes.add("📢 Send general reminder to ${nonCritical.size} students with current pending fees")
            actions.add(
                SuggestedAction(
                    label = "General Fee Reminder",
                    description = "Reminder for ${nonCritical.size} students with recent pending fees. Total: ₹${nonCritical.sumOf { it.pendingAmount }}",
                    type = SuggestedAction.ActionType.SEND_REMINDER
                )
            )
        }

        // Fix 4: Post announcement for low collection
        if (ctx.collectionRate < 60) {
            fixes.add("📢 Post fee deadline announcement (collection at ${ctx.collectionRate}%)")
            actions.add(
                SuggestedAction(
                    label = "Fee Deadline Announcement",
                    description = "Attention: Fee collection is at ${ctx.collectionRate}%. Please complete your pending payments by the end of this week.",
                    type = SuggestedAction.ActionType.POST_ANNOUNCEMENT
                )
            )
        }

        val text = buildString {
            if (fixes.isEmpty()) {
                appendLine("## ✅ Nothing to Fix!\n")
                appendLine("Your academy is running smoothly. No actions needed right now. 🎉")
            } else {
                appendLine("## ⚡ Auto-Fix: ${fixes.size} Actions Ready\n")
                appendLine("Click any action below to execute with one tap:\n")
                fixes.forEachIndexed { i, fix ->
                    appendLine("${i+1}. $fix")
                }
                appendLine("\n⚠️ *Each action requires your confirmation before executing.*")
            }
            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions,
            priority = if (critical.isNotEmpty()) Priority.URGENT else Priority.NORMAL
        )
    }

    // ── Join Status ──────────────────────────────────────────────────────

    private fun generateJoinStatus(ctx: TeacherDataContext): AiChatMessage {
        val text = buildString {
            if (ctx.pendingStudents.isEmpty()) {
                appendLine("## ✅ No Pending Join Requests\n")
                appendLine("All students have been processed. No new requests. 👍")
            } else {
                appendLine("## 🟡 ${ctx.pendingJoinRequests} Pending Join Requests\n")
                ctx.pendingStudents.forEachIndexed { i, s ->
                    appendLine("${i+1}. **${s.studentName}**")
                }
                appendLine("\nYou can approve all requests with one click below.")
            }
            appendLine("\n💡 *Generated locally — instant analysis*")
        }

        val actions = mutableListOf<SuggestedAction>()
        if (ctx.pendingStudents.isNotEmpty()) {
            actions.add(
                SuggestedAction(
                    label = "Approve All (${ctx.pendingJoinRequests})",
                    description = "Approve all ${ctx.pendingJoinRequests} pending join requests",
                    type = SuggestedAction.ActionType.APPROVE_JOIN
                )
            )
        }

        return AiChatMessage(
            content = text,
            role = AiChatMessage.Role.ASSISTANT,
            actions = actions
        )
    }

    // ── Helper ───────────────────────────────────────────────────────────

    private fun matchesAny(query: String, vararg keywords: String): Boolean {
        return keywords.any { keyword ->
            query.contains(keyword, ignoreCase = true)
        }
    }
}