package com.example.feesmanager.ai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.feesmanager.ai.engine.AiActionExecutor
import com.example.feesmanager.ai.engine.TeacherDataContext
import com.example.feesmanager.ai.engine.TrendDetector
import com.example.feesmanager.ai.models.ActionResult
import com.example.feesmanager.ai.models.AiChatMessage
import com.example.feesmanager.ai.models.Priority
import com.example.feesmanager.ai.models.PromptTemplates
import com.example.feesmanager.ai.models.QuickAction
import com.example.feesmanager.ai.models.SuggestedAction
import com.example.feesmanager.ai.teacher.TeacherAiRepository
import kotlinx.coroutines.launch

/**
 * TeacherAiViewModel — MVVM ViewModel for Teacher AI Agent.
 *
 * Manages:
 *   - Chat message list
 *   - Quick action prompts
 *   - Data context (real-time stats)
 *   - Loading & error state
 *   - Urgent issue alerts
 *   - Action execution with confirmation flow
 *   - Proactive daily digest & trend detection
 *
 * UPDATED: Now extends AndroidViewModel for Application context access
 * (needed by TrendDetector for SharedPreferences).
 */
class TeacherAiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TeacherAiRepository()
    private val actionExecutor = AiActionExecutor()
    private val trendDetector = TrendDetector(application)

    private val _messages = MutableLiveData<List<AiChatMessage>>(emptyList())
    val messages: LiveData<List<AiChatMessage>> = _messages

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    private val _dataContext = MutableLiveData<TeacherDataContext?>(null)
    val dataContext: LiveData<TeacherDataContext?> = _dataContext

    private val _urgentIssues = MutableLiveData<List<String>>(emptyList())
    val urgentIssues: LiveData<List<String>> = _urgentIssues

    /** Emits action result after execution — UI observes for toast */
    private val _actionResult = MutableLiveData<ActionResult?>(null)
    val actionResult: LiveData<ActionResult?> = _actionResult

    val quickActions: List<QuickAction> = PromptTemplates.TEACHER_QUICK_ACTIONS

    private var teacherId = ""

    /**
     * Initializes with teacher ID and loads initial data context.
     * Now includes proactive daily digest and trend detection.
     */
    fun initialize(teacherId: String) {
        this.teacherId = teacherId
        viewModelScope.launch {
            // Load data context
            val context = repository.getDataContext(teacherId)
            _dataContext.value = context

            // Take daily snapshot for trend tracking
            trendDetector.takeSnapshot(context)

            // Detect urgent issues
            val issues = repository.getUrgentIssues(teacherId)
            _urgentIssues.value = issues

            // Generate proactive welcome message
            showSmartWelcomeMessage(context, issues)

            // Record this app open
            trendDetector.recordAppOpen()
        }
    }

    /**
     * Sends a teacher query to the AI agent.
     */
    fun sendMessage(text: String) {
        if (text.isBlank() || teacherId.isBlank()) return

        val currentMessages = _messages.value.orEmpty().toMutableList()

        // Add user message
        val userMessage = AiChatMessage(
            content = text,
            role = AiChatMessage.Role.USER
        )
        currentMessages.add(userMessage)

        // Add loading indicator
        val loadingMessage = AiChatMessage(
            content = "",
            role = AiChatMessage.Role.ASSISTANT,
            isLoading = true
        )
        currentMessages.add(loadingMessage)
        _messages.value = currentMessages.toList()
        _isLoading.value = true

        viewModelScope.launch {
            val result = repository.analyzeWithAi(teacherId, text)

            val updated = _messages.value.orEmpty().toMutableList()
            updated.removeAll { it.isLoading }

            result.fold(
                onSuccess = { aiMessage ->
                    updated.add(aiMessage)
                },
                onFailure = { e ->
                    updated.add(
                        AiChatMessage(
                            content = "❌ ${e.message ?: "Something went wrong."}\n\nPlease try again.",
                            role = AiChatMessage.Role.ASSISTANT,
                            priority = Priority.HIGH
                        )
                    )
                    _error.value = e.message
                }
            )

            _messages.value = updated.toList()
            _isLoading.value = false
        }
    }

    /**
     * Executes an AI-suggested action after teacher confirmation.
     * Updates the action's state in messages and auto-refreshes data.
     */
    fun executeAction(action: SuggestedAction) {
        if (teacherId.isBlank()) return

        viewModelScope.launch {
            val result = actionExecutor.execute(teacherId, action)

            // Update the action in messages to show executed state
            val currentMessages = _messages.value.orEmpty().toMutableList()
            val updatedMessages = currentMessages.map { msg ->
                if (msg.actions.any { it.id == action.id }) {
                    msg.copy(actions = msg.actions.map { a ->
                        if (a.id == action.id) {
                            a.copy(
                                executed = true,
                                resultMessage = result.message
                            )
                        } else a
                    })
                } else msg
            }
            _messages.value = updatedMessages

            // Emit result for UI toast
            _actionResult.value = result

            // Invalidate caches after successful action
            if (result.success) {
                repository.onActionExecuted()
                refreshData()
            }
        }
    }

    /**
     * Executes a quick action prompt.
     */
    fun executeQuickAction(action: QuickAction) {
        sendMessage(action.prompt)
    }

    /**
     * Refreshes data context from Supabase.
     */
    fun refreshData() {
        if (teacherId.isBlank()) return
        viewModelScope.launch {
            repository.refreshData(teacherId)
            val context = repository.getDataContext(teacherId)
            _dataContext.value = context
            _urgentIssues.value = repository.getUrgentIssues(teacherId)

            // Update daily snapshot
            trendDetector.takeSnapshot(context)
        }
    }

    /**
     * Clears chat history.
     */
    fun clearChat() {
        repository.clearConversation()
        _messages.value = emptyList()
        viewModelScope.launch {
            val context = _dataContext.value ?: repository.getDataContext(teacherId)
            val issues = _urgentIssues.value.orEmpty()
            showSmartWelcomeMessage(context, issues)
        }
    }

    /**
     * Clears the last action result to prevent re-showing toast.
     */
    fun clearActionResult() {
        _actionResult.value = null
    }

    /**
     * Enhanced welcome message with proactive daily digest and trend insights.
     */
    private fun showSmartWelcomeMessage(context: TeacherDataContext, issues: List<String>) {
        val welcomeText = buildString {
            appendLine("🤖 **AI Agent Ready**\n")

            // ── Daily Digest (changes since last session) ──
            val digest = trendDetector.generateDailyDigest(context)
            if (digest != null) {
                appendLine(digest)
                appendLine()
            }

            // ── Academy Overview ──
            appendLine("📊 **Academy Overview:**")
            appendLine("• Students: ${context.totalStudents}")
            appendLine("• Collected: ₹${context.totalCollected}")
            appendLine("• Pending: ₹${context.totalPending}")
            appendLine("• Collection Rate: ${context.collectionRate}%")

            if (context.pendingJoinRequests > 0) {
                appendLine("• Join Requests: ${context.pendingJoinRequests} pending")
            }

            // ── Urgent Issues ──
            if (issues.isNotEmpty()) {
                appendLine("\n⚠️ **Alerts:**")
                issues.forEach { appendLine("• $it") }
            }

            // ── Weekly Trends (if available) ──
            val trends = trendDetector.detectWeeklyTrends(context)
            if (trends.isNotEmpty()) {
                appendLine("\n📈 **Weekly Trends:**")
                trends.forEach { appendLine("• $it") }
            }

            // ── Time since last open ──
            val timeSince = trendDetector.getTimeSinceLastOpen()
            if (timeSince > 24 * 60 * 60 * 1000L) {
                val days = (timeSince / (24 * 60 * 60 * 1000L)).toInt()
                appendLine("\n⏰ *It's been $days day${if (days > 1) "s" else ""} since your last visit.*")
            }

            appendLine("\n💡 Use the quick actions below or ask me anything. I can analyze data AND execute actions!")
        }

        val priority = when {
            issues.any { it.startsWith("🔴") } -> Priority.URGENT
            issues.any { it.startsWith("🟠") } -> Priority.HIGH
            else -> Priority.NORMAL
        }

        _messages.value = listOf(
            AiChatMessage(
                content = welcomeText,
                role = AiChatMessage.Role.ASSISTANT,
                priority = priority
            )
        )
    }
}