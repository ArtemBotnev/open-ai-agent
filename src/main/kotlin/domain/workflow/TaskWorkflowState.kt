package com.artembotnev.openai.domain.workflow

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
internal data class TaskWorkflowState(
    val task: String = "",
    val state: WorkflowState = WorkflowState.PLANNING,
    val step: Int = WorkflowState.PLANNING.order,
    val total: Int = TOTAL_WORKFLOW_STEPS,
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val current: String = "",
    val planStep: Int = 0,
    val planTotal: Int = 0,
    val waitingForUserConfirmation: Boolean = false,
    val lastDraft: String = "",
    val lastValidationSummary: String = "",
    val validationIssues: List<String> = emptyList(),
    val pendingRevisionRequest: String = "",
    val validationAttempts: Int = 0,
    val finalReply: String = "",
    val currentStageResult: TaskStageResult? = null,
    val agentHandoffs: List<TaskAgentHandoff> = emptyList(),
    val conversationSteps: List<TaskConversationStep> = emptyList(),
    val transitions: List<TaskAgentTransition> = emptyList(),
) {

    fun normalized(): TaskWorkflowState {
        val sanitizedPlan = plan.map(String::trim).filter(String::isNotBlank)
        val sanitizedDone = done
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .filter { item -> sanitizedPlan.isEmpty() || sanitizedPlan.contains(item) }
        val effectiveCurrent = current
            .takeIf { it.isNotBlank() }
            ?: sanitizedPlan.firstOrNull { it !in sanitizedDone }.orEmpty()
        val effectivePlanTotal = sanitizedPlan.size
        val effectivePlanStep = when {
            effectivePlanTotal == 0 -> 0
            effectiveCurrent.isBlank() -> effectivePlanTotal
            else -> (sanitizedDone.size + 1).coerceAtMost(effectivePlanTotal)
        }

        return copy(
            state = state,
            step = state.order,
            total = TOTAL_WORKFLOW_STEPS,
            plan = sanitizedPlan,
            done = sanitizedDone,
            current = effectiveCurrent,
            planStep = effectivePlanStep,
            planTotal = effectivePlanTotal,
            waitingForUserConfirmation = waitingForUserConfirmation && state == WorkflowState.PLANNING,
            lastDraft = lastDraft.trim(),
            lastValidationSummary = lastValidationSummary.trim(),
            validationIssues = validationIssues.map(String::trim).filter(String::isNotBlank).distinct(),
            pendingRevisionRequest = pendingRevisionRequest.trim(),
            validationAttempts = validationAttempts.coerceAtLeast(0),
            finalReply = finalReply.trim(),
            currentStageResult = currentStageResult?.normalized(),
            agentHandoffs = agentHandoffs
                .map(TaskAgentHandoff::normalized)
                .takeLast(MAX_HANDOFFS),
            conversationSteps = conversationSteps.takeLast(MAX_CONVERSATION_STEPS),
            transitions = transitions.takeLast(MAX_TRANSITIONS),
        )
    }

    fun addConversationStep(
        actor: WorkflowActor,
        message: String,
        stage: WorkflowState = state,
    ): TaskWorkflowState {
        val sanitized = message.trim()
        if (sanitized.isBlank()) return this

        val entry = TaskConversationStep(
            index = (conversationSteps.lastOrNull()?.index ?: 0) + 1,
            actor = actor,
            state = stage,
            message = sanitized,
            timestamp = Instant.now().toString(),
        )

        return copy(conversationSteps = (conversationSteps + entry).takeLast(MAX_CONVERSATION_STEPS))
    }

    fun transitionTo(nextState: WorkflowState, reason: String): TaskWorkflowState {
        if (nextState == state) return normalized()

        val entry = TaskAgentTransition(
            from = state,
            to = nextState,
            reason = reason.trim().ifBlank { "Без указания причины" },
            timestamp = Instant.now().toString(),
        )

        return copy(
            state = nextState,
            step = nextState.order,
            total = TOTAL_WORKFLOW_STEPS,
            transitions = (transitions + entry).takeLast(MAX_TRANSITIONS),
            waitingForUserConfirmation = if (nextState == WorkflowState.PLANNING) waitingForUserConfirmation else false,
            finalReply = if (nextState == WorkflowState.DONE) finalReply else "",
        ).normalized()
    }

    fun applyPlan(taskName: String, newPlan: List<String>, currentItem: String, waitingForConfirmation: Boolean): TaskWorkflowState {
        val sanitizedTask = taskName.trim().ifBlank { task }
        val sanitizedPlan = newPlan.map(String::trim).filter(String::isNotBlank).distinct()
        val keptDone = done.filter { it in sanitizedPlan }
        val effectiveCurrent = currentItem.trim()
            .ifBlank { sanitizedPlan.firstOrNull { it !in keptDone }.orEmpty() }

        return copy(
            task = sanitizedTask,
            plan = sanitizedPlan,
            done = keptDone,
            current = effectiveCurrent,
            planStep = when {
                sanitizedPlan.isEmpty() -> 0
                effectiveCurrent.isBlank() -> sanitizedPlan.size
                else -> (keptDone.size + 1).coerceAtMost(sanitizedPlan.size)
            },
            planTotal = sanitizedPlan.size,
            waitingForUserConfirmation = waitingForConfirmation,
            lastDraft = if (sanitizedPlan != plan) "" else lastDraft,
            lastValidationSummary = if (sanitizedPlan != plan) "" else lastValidationSummary,
            validationIssues = if (sanitizedPlan != plan) emptyList() else validationIssues,
            finalReply = "",
        ).normalized()
    }

    fun applyExecutionResult(
        draft: String,
        completedSteps: List<String>,
        currentItem: String,
    ): TaskWorkflowState {
        val sanitizedCompleted = completedSteps.map(String::trim).filter(String::isNotBlank).distinct()
        val mergedDone = (done + sanitizedCompleted)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .filter { item -> plan.isEmpty() || plan.contains(item) }
        val effectiveCurrent = currentItem.trim()
            .ifBlank { plan.firstOrNull { it !in mergedDone }.orEmpty() }

        return copy(
            done = mergedDone,
            current = effectiveCurrent,
            planStep = when {
                plan.isEmpty() -> 0
                effectiveCurrent.isBlank() -> plan.size
                else -> (mergedDone.size + 1).coerceAtMost(plan.size)
            },
            planTotal = plan.size,
            waitingForUserConfirmation = false,
            lastDraft = draft.trim(),
            finalReply = "",
        ).normalized()
    }

    fun applyValidationResult(summary: String, issues: List<String>): TaskWorkflowState {
        return copy(
            lastValidationSummary = summary.trim(),
            validationIssues = issues.map(String::trim).filter(String::isNotBlank).distinct(),
            finalReply = "",
        ).normalized()
    }

    fun withFinalReply(reply: String): TaskWorkflowState {
        return copy(finalReply = reply.trim()).normalized()
    }

    fun withRuntimeState(
        revisionRequest: String = pendingRevisionRequest,
        attempts: Int = validationAttempts,
    ): TaskWorkflowState {
        return copy(
            pendingRevisionRequest = revisionRequest.trim(),
            validationAttempts = attempts,
        ).normalized()
    }

    fun recordStageResult(
        actor: WorkflowActor,
        stage: WorkflowState = state,
        summary: String,
        details: List<String> = emptyList(),
        artifactPreview: String = "",
    ): TaskWorkflowState {
        val result = TaskStageResult(
            actor = actor,
            state = stage,
            summary = summary,
            details = details,
            artifactPreview = artifactPreview,
            timestamp = Instant.now().toString(),
        )

        return copy(currentStageResult = result).normalized()
    }

    fun addAgentHandoff(
        fromActor: WorkflowActor,
        toActor: WorkflowActor,
        fromState: WorkflowState,
        toState: WorkflowState,
        message: String,
    ): TaskWorkflowState {
        val sanitized = message.trim()
        if (sanitized.isBlank()) return this

        val handoff = TaskAgentHandoff(
            fromActor = fromActor,
            toActor = toActor,
            fromState = fromState,
            toState = toState,
            message = sanitized,
            timestamp = Instant.now().toString(),
        )

        return copy(agentHandoffs = (agentHandoffs + handoff).takeLast(MAX_HANDOFFS)).normalized()
    }

    fun resetForNewCycle(): TaskWorkflowState {
        return TaskWorkflowState(
            agentHandoffs = agentHandoffs,
            conversationSteps = conversationSteps,
            transitions = transitions,
        ).normalized()
    }

    companion object {
        const val TOTAL_WORKFLOW_STEPS = 4
        private const val MAX_HANDOFFS = 40
        private const val MAX_CONVERSATION_STEPS = 80
        private const val MAX_TRANSITIONS = 40
    }
}

@Serializable
internal enum class WorkflowState(val order: Int) {
    PLANNING(1),
    EXECUTION(2),
    VALIDATION(3),
    DONE(4),
}

@Serializable
internal enum class WorkflowActor {
    USER,
    PLANNING_AGENT,
    EXECUTION_AGENT,
    VALIDATION_AGENT,
    DONE_AGENT,
    SYSTEM,
}

@Serializable
internal data class TaskConversationStep(
    val index: Int,
    val actor: WorkflowActor,
    val state: WorkflowState,
    val message: String,
    val timestamp: String,
)

@Serializable
internal data class TaskAgentTransition(
    val from: WorkflowState,
    val to: WorkflowState,
    val reason: String,
    val timestamp: String,
)

@Serializable
internal data class TaskStageResult(
    val actor: WorkflowActor,
    val state: WorkflowState,
    val summary: String,
    val details: List<String> = emptyList(),
    val artifactPreview: String = "",
    val timestamp: String,
) {
    fun normalized(): TaskStageResult {
        return copy(
            summary = summary.trim(),
            details = details.map(String::trim).filter(String::isNotBlank).distinct(),
            artifactPreview = artifactPreview.trim().take(MAX_ARTIFACT_PREVIEW_CHARS),
        )
    }

    private companion object {
        const val MAX_ARTIFACT_PREVIEW_CHARS = 1200
    }
}

@Serializable
internal data class TaskAgentHandoff(
    val fromActor: WorkflowActor,
    val toActor: WorkflowActor,
    val fromState: WorkflowState,
    val toState: WorkflowState,
    val message: String,
    val timestamp: String,
) {
    fun normalized(): TaskAgentHandoff {
        return copy(message = message.trim())
    }
}
