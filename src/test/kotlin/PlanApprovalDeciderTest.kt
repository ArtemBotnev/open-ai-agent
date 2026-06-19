package com.artembotnev.openai

import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.infra.openai.workflow.PlanApprovalDecider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanApprovalDeciderTest {

    @Test
    fun `detects explicit approval only when confirmation is pending`() {
        val waitingState = TaskWorkflowState(
            plan = listOf("Шаг 1", "Шаг 2"),
            waitingForUserConfirmation = true,
        )
        val notWaitingState = waitingState.copy(waitingForUserConfirmation = false)

        assertTrue(PlanApprovalDecider.isExplicitApproval("Подтверждаю план.", waitingState))
        assertTrue(PlanApprovalDecider.isExplicitApproval("Принимаем.", waitingState))
        assertFalse(PlanApprovalDecider.isExplicitApproval("Подтверждаю план.", notWaitingState))
    }

    @Test
    fun `rejects mixed approval and change request`() {
        val state = TaskWorkflowState(
            plan = listOf("Шаг 1", "Шаг 2"),
            waitingForUserConfirmation = true,
        )

        assertFalse(PlanApprovalDecider.isExplicitApproval("Да, и добавь логирование", state))
        assertFalse(PlanApprovalDecider.isExplicitApproval("Да?", state))
    }
}
