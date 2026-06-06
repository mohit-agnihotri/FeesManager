package com.example.feesmanager.ai.engine

import com.example.feesmanager.ai.models.SuggestedAction
import com.example.feesmanager.ai.models.ActionResult
import com.example.feesmanager.data.network.FmResult
import com.example.feesmanager.data.network.SupabaseManager
import com.example.feesmanager.data.repository.AnnouncementsRepository
import io.github.jan.supabase.postgrest.postgrest

/**
 * AiActionExecutor — Executes AI agent's suggested actions on Supabase.
 *
 * ALL actions require teacher confirmation before execution (enforced by UI).
 *
 * Supports:
 *   - SEND_REMINDER    → Posts as announcement
 *   - POST_ANNOUNCEMENT → Creates a new announcement
 *   - NOTIFY_GROUP     → Posts targeted notification
 *   - APPROVE_JOIN     → Updates enrollment status to "approved"
 *   - REJECT_JOIN      → Updates enrollment status to "rejected"
 *   - GENERATE_REPORT  → Returns formatted report text
 */
class AiActionExecutor {

    private val announcementsRepo = AnnouncementsRepository()
    private val db = SupabaseManager.client.postgrest

    /**
     * Executes a suggested action and returns the result.
     * Called ONLY after teacher confirms via dialog.
     */
    suspend fun execute(
        teacherId: String,
        action: SuggestedAction
    ): ActionResult {
        return try {
            when (action.type) {
                SuggestedAction.ActionType.SEND_REMINDER -> {
                    sendReminder(teacherId, action)
                }
                SuggestedAction.ActionType.POST_ANNOUNCEMENT -> {
                    postAnnouncement(teacherId, action)
                }
                SuggestedAction.ActionType.NOTIFY_GROUP -> {
                    notifyGroup(teacherId, action)
                }
                SuggestedAction.ActionType.SEND_MESSAGE -> {
                    ActionResult(
                        success = true,
                        message = "✅ Message prepared. You can send it from the Chat section.",
                        affectedCount = 0
                    )
                }
                SuggestedAction.ActionType.GENERATE_REPORT -> {
                    ActionResult(
                        success = true,
                        message = "✅ Report generated and displayed above.",
                        affectedCount = 0
                    )
                }
                SuggestedAction.ActionType.APPROVE_JOIN -> {
                    approveJoinRequests(teacherId)
                }
                SuggestedAction.ActionType.REJECT_JOIN -> {
                    rejectJoinRequest(teacherId, action)
                }
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "❌ Action failed: ${e.message}"
            )
        }
    }

    /**
     * Posts a fee reminder as an announcement.
     */
    private suspend fun sendReminder(
        teacherId: String,
        action: SuggestedAction
    ): ActionResult {
        var result: ActionResult? = null

        announcementsRepo.postAnnouncement(
            teacherId = teacherId,
            title = "💰 ${action.label}",
            body = action.description
        ) { fmResult ->
            result = when (fmResult) {
                is FmResult.Success -> ActionResult(
                    success = true,
                    message = "✅ Reminder sent as announcement to all students!",
                    affectedCount = 1
                )
                is FmResult.Error -> ActionResult(
                    success = false,
                    message = "❌ Failed: ${fmResult.message}"
                )
                is FmResult.Loading -> ActionResult(
                    success = false,
                    message = "Processing..."
                )
            }
        }

        return result ?: ActionResult(success = false, message = "Unknown error")
    }

    /**
     * Creates an announcement from AI-generated content.
     */
    private suspend fun postAnnouncement(
        teacherId: String,
        action: SuggestedAction
    ): ActionResult {
        var result: ActionResult? = null

        announcementsRepo.postAnnouncement(
            teacherId = teacherId,
            title = "📢 ${action.label}",
            body = action.description
        ) { fmResult ->
            result = when (fmResult) {
                is FmResult.Success -> ActionResult(
                    success = true,
                    message = "✅ Announcement posted successfully!",
                    affectedCount = 1
                )
                is FmResult.Error -> ActionResult(
                    success = false,
                    message = "❌ Failed: ${fmResult.message}"
                )
                is FmResult.Loading -> ActionResult(
                    success = false,
                    message = "Processing..."
                )
            }
        }

        return result ?: ActionResult(success = false, message = "Unknown error")
    }

    /**
     * Sends a notification to a group (posts as announcement).
     */
    private suspend fun notifyGroup(
        teacherId: String,
        action: SuggestedAction
    ): ActionResult {
        return postAnnouncement(teacherId, action)
    }

    /**
     * Approves ALL pending join requests for this teacher.
     */
    private suspend fun approveJoinRequests(teacherId: String): ActionResult {
        return try {
            db.from("enrollments").update({
                set("status", "approved")
            }) {
                filter {
                    eq("teacher_id", teacherId)
                    eq("status", "pending")
                }
            }

            ActionResult(
                success = true,
                message = "✅ All pending join requests have been approved!",
                affectedCount = 1
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "❌ Failed to approve: ${e.message}"
            )
        }
    }

    /**
     * Rejects a pending join request.
     * Uses the enrollment_id from the action payload if available,
     * otherwise rejects all pending.
     */
    private suspend fun rejectJoinRequest(
        teacherId: String,
        action: SuggestedAction
    ): ActionResult {
        return try {
            val enrollmentId = action.payload["enrollment_id"]

            if (enrollmentId != null) {
                // Reject specific enrollment
                db.from("enrollments").update({
                    set("status", "rejected")
                }) {
                    filter {
                        eq("id", enrollmentId)
                        eq("teacher_id", teacherId)
                    }
                }
                ActionResult(
                    success = true,
                    message = "✅ Join request rejected.",
                    affectedCount = 1
                )
            } else {
                // No specific ID — reject all pending
                db.from("enrollments").update({
                    set("status", "rejected")
                }) {
                    filter {
                        eq("teacher_id", teacherId)
                        eq("status", "pending")
                    }
                }
                ActionResult(
                    success = true,
                    message = "✅ All pending join requests rejected.",
                    affectedCount = 1
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "❌ Failed to reject: ${e.message}"
            )
        }
    }
}