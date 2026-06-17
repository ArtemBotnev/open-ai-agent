package com.artembotnev.openai.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals

internal class TaskStateMachineTest {

    @Test
    fun `forces lifecycle to pass through execution before validation or done`() {
        val current = TaskStateMachine.initial()

        val afterInvalidDone = TaskStateMachine.transition(
            current = current,
            proposed = TaskState(
                stage = TaskStage.DONE,
                currentStep = "Сразу завершить задачу",
                expectedAction = "Нет действий",
            ),
            taskChanged = false,
        )

        assertEquals(TaskStage.EXECUTION, afterInvalidDone.stage)
        assertEquals("Сразу завершить задачу", afterInvalidDone.currentStep)
        assertEquals("Подготовить результат к проверке", afterInvalidDone.expectedAction)
    }

    @Test
    fun `resets stage to planning when task changes`() {
        val current = TaskStateMachine.restore(
            stage = TaskStage.VALIDATION,
            currentStep = "Проверить ответ",
            expectedAction = "Закрыть задачу",
        )

        val next = TaskStateMachine.transition(
            current = current,
            proposed = TaskState(
                stage = TaskStage.DONE,
                currentStep = "Определить требования новой задачи",
                expectedAction = "Собрать план",
            ),
            taskChanged = true,
        )

        assertEquals(TaskStage.PLANNING, next.stage)
        assertEquals("Определить требования новой задачи", next.currentStep)
        assertEquals("Собрать план", next.expectedAction)
    }

    @Test
    fun `derives explicit state from legacy status bullets`() {
        val state = TaskStateMachine.deriveLegacyState(
            currentTask = "Сделать рефакторинг",
            decisionsAndStatus = listOf("Проверить итоговый результат перед сдачей"),
        )

        assertEquals(TaskStage.VALIDATION, state.stage)
        assertEquals("Проверить итоговый результат перед сдачей", state.currentStep)
        assertEquals("Завершить задачу или вернуть в execution", state.expectedAction)
    }
}
