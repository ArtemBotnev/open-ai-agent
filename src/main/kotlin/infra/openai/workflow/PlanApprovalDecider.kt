package com.artembotnev.openai.infra.openai.workflow

import com.artembotnev.openai.domain.workflow.TaskWorkflowState

internal object PlanApprovalDecider {

    fun isExplicitApproval(userText: String, state: TaskWorkflowState): Boolean {
        if (!state.waitingForUserConfirmation || state.plan.isEmpty()) return false

        val normalized = userText
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()

        if (normalized.isBlank()) return false

        return APPROVAL_PATTERNS.any { pattern -> pattern.matches(normalized) }
    }

    private val APPROVAL_PATTERNS = listOf(
        Regex("^да[.!]*$"),
        Regex("^принимаем[.!]*$"),
        Regex("^принимаем план[.!]*$"),
        Regex("^да,? запускай[.!]*$"),
        Regex("^да,? можно (выполнять|приступать|запускать)[.!]*$"),
        Regex("^да,? приступай[.!]*$"),
        Regex("^подтверждаю( план)?[.!]*$"),
        Regex("^утверждаю( план)?[.!]*$"),
        Regex("^план (подтверждаю|утверждаю)[.!]*$"),
        Regex("^согласен( с планом)?[.!]*$"),
        Regex("^всё верно[.!]*$"),
        Regex("^все верно[.!]*$"),
        Regex("^looks good[.!]*$"),
        Regex("^approved[.!]*$"),
    )
}
