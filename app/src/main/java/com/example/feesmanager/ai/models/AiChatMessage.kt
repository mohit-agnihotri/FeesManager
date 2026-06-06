package com.example.feesmanager.ai.models

import java.util.UUID

/**
 * AiChatMessage — Data model for AI chat conversations.
 * Used by the Teacher AI Agent.
 */
data class AiChatMessage(
    val id: String         = UUID.randomUUID().toString(),
    val content: String    = "",
    val role: Role         = Role.ASSISTANT,
    val actions: List<SuggestedAction>  = emptyList(),
    val timestamp: Long    = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val priority: Priority = Priority.NORMAL
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/**
 * SuggestedAction — An action the AI suggests the teacher can execute.
 * Always requires teacher confirmation before execution.
 */
data class SuggestedAction(
    val id: String            = UUID.randomUUID().toString(),
    val label: String         = "",
    val description: String   = "",
    val type: ActionType      = ActionType.SEND_REMINDER,
    val priority: Priority    = Priority.NORMAL,
    val payload: Map<String, String> = emptyMap(),
    val executed: Boolean     = false,
    val resultMessage: String = ""    // Shows after execution
) {
    enum class ActionType {
        SEND_REMINDER,
        POST_ANNOUNCEMENT,
        NOTIFY_GROUP,
        SEND_MESSAGE,
        GENERATE_REPORT,
        APPROVE_JOIN,
        REJECT_JOIN
    }
}

/**
 * Priority — Used for both AI insights and suggested actions.
 */
enum class Priority {
    URGENT,    // 🔴 Red highlight — immediate attention
    HIGH,      // 🟠 Orange — important
    NORMAL,    // 🟡 Default
    LOW        // 🟢 Informational
}
