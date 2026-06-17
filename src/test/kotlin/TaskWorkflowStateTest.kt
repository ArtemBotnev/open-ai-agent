package com.artembotnev.openai

import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.domain.workflow.WorkflowActor
import com.artembotnev.openai.domain.workflow.WorkflowState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskWorkflowStateTest {

    @Test
    fun `normalized recalculates workflow and plan counters`() {
        val state = TaskWorkflowState(
            task = "State machine",
            state = WorkflowState.VALIDATION,
            step = 99,
            total = 99,
            plan = listOf("planning", "execution", "validation"),
            done = listOf("planning"),
            current = "execution",
            waitingForUserConfirmation = true,
        )

        val normalized = state.normalized()

        assertEquals(WorkflowState.VALIDATION, normalized.state)
        assertEquals(3, normalized.step)
        assertEquals(4, normalized.total)
        assertEquals(2, normalized.planStep)
        assertEquals(3, normalized.planTotal)
        assertFalse(normalized.waitingForUserConfirmation)
    }

    @Test
    fun `transitionTo records transition and updates step`() {
        val state = TaskWorkflowState(state = WorkflowState.PLANNING)

        val transitioned = state.transitionTo(WorkflowState.EXECUTION, "Пользователь подтвердил план")

        assertEquals(WorkflowState.EXECUTION, transitioned.state)
        assertEquals(2, transitioned.step)
        assertEquals(4, transitioned.total)
        assertEquals(1, transitioned.transitions.size)
        assertEquals(WorkflowState.PLANNING, transitioned.transitions.first().from)
        assertEquals(WorkflowState.EXECUTION, transitioned.transitions.first().to)
    }

    @Test
    fun `addConversationStep appends indexed log entry`() {
        val state = TaskWorkflowState()

        val updated = state
            .addConversationStep(WorkflowActor.USER, "Нужна реализация")
            .addConversationStep(WorkflowActor.PLANNING_AGENT, "Вот план")

        assertEquals(2, updated.conversationSteps.size)
        assertEquals(1, updated.conversationSteps[0].index)
        assertEquals(2, updated.conversationSteps[1].index)
        assertTrue(updated.conversationSteps[1].message.contains("Вот план"))
    }

    @Test
    fun `recordStageResult and handoff persist runtime resume data`() {
        val state = TaskWorkflowState(state = WorkflowState.VALIDATION)

        val updated = state
            .withRuntimeState(revisionRequest = "Исправить null handling", attempts = 1)
            .recordStageResult(
                actor = WorkflowActor.VALIDATION_AGENT,
                stage = WorkflowState.VALIDATION,
                summary = "Найдены замечания",
                details = listOf("Проверить null handling", "Добавить guard"),
                artifactPreview = "fun test() {}\n".repeat(300),
            )
            .addAgentHandoff(
                fromActor = WorkflowActor.VALIDATION_AGENT,
                toActor = WorkflowActor.EXECUTION_AGENT,
                fromState = WorkflowState.VALIDATION,
                toState = WorkflowState.EXECUTION,
                message = "Исправить null handling",
            )

        assertEquals("Исправить null handling", updated.pendingRevisionRequest)
        assertEquals(1, updated.validationAttempts)
        assertEquals(WorkflowActor.VALIDATION_AGENT, updated.currentStageResult?.actor)
        assertEquals(2, updated.currentStageResult?.details?.size)
        assertTrue((updated.currentStageResult?.artifactPreview?.length ?: 0) <= 1200)
        assertEquals(1, updated.agentHandoffs.size)
        assertEquals(WorkflowActor.EXECUTION_AGENT, updated.agentHandoffs.first().toActor)
    }
}
