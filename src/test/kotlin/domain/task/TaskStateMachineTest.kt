package com.artembotnev.openai.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals

internal class TaskStateMachineTest {

    @Test
    fun `starts new task in planning`() {
        val context = TaskStateMachine.start("Обновить SaveHistoryUseCase")

        assertEquals(AgentState.PLANNING, context.state)
        assertEquals(0, context.step)
        assertEquals(0, context.total)
    }

    @Test
    fun `normalizes planned task step as first real step`() {
        val context = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.PLANNING,
            step = 0,
            total = 3,
            plan = listOf("Собрать требования", "Изменить код", "Проверить результат"),
            done = emptyList(),
            current = "",
        )

        assertEquals(1, context.step)
        assertEquals("Собрать требования", context.current)
    }

    @Test
    fun `allows only declared transitions`() {
        val planning = TaskStateMachine.start("Обновить SaveHistoryUseCase").copy(
            plan = listOf("Проверить текущую реализацию", "Изменить код", "Проверить результат"),
            total = 3,
            current = "Проверить текущую реализацию",
        )

        val execution = TaskStateMachine.transition(
            current = planning,
            proposed = planning.copy(
                state = AgentState.EXECUTION,
                step = 1,
                current = "Проверить текущую реализацию",
            ),
        )
        val validation = TaskStateMachine.transition(
            current = execution,
            proposed = execution.copy(
                state = AgentState.VALIDATION,
                step = 2,
                done = listOf("Проверить текущую реализацию", "Изменить код"),
                current = "Проверить результат",
            ),
        )
        val done = TaskStateMachine.transition(
            current = validation,
            proposed = validation.copy(
                state = AgentState.DONE,
                step = 3,
                done = listOf("Проверить текущую реализацию", "Изменить код", "Проверить результат"),
                current = "Зафиксировать итоговый результат",
            ),
        )

        assertEquals(AgentState.EXECUTION, execution.state)
        assertEquals(AgentState.VALIDATION, validation.state)
        assertEquals(AgentState.DONE, done.state)
    }

    @Test
    fun `reaches done through internal path when done is proposed from planning`() {
        val planning = TaskStateMachine.start("Обновить SaveHistoryUseCase")
        val path = TaskStateMachine.transitionPath(
            current = planning,
            proposed = planning.copy(
                state = AgentState.DONE,
                current = "Сразу завершить задачу",
            ),
        )

        assertEquals(
            listOf(AgentState.EXECUTION, AgentState.VALIDATION, AgentState.DONE),
            path.map { it.state }
        )
        assertEquals("Сразу завершить задачу", path.last().current)
    }

    @Test
    fun `resets new task to planning`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 2,
            plan = listOf("Изменить use case", "Проверить результат"),
            done = emptyList(),
            current = "Изменить use case",
        )

        val next = TaskStateMachine.transition(
            current = current,
            proposed = TaskContext(
                task = "Сделать ревью SaveHistoryUseCase",
                state = AgentState.VALIDATION,
                step = 1,
                total = 1,
                plan = listOf("Проверить замечания"),
                done = listOf("Старый шаг"),
                current = "Проверить замечания",
            ),
        )

        assertEquals(AgentState.PLANNING, next.state)
        assertEquals(0, next.step)
        assertEquals(emptyList(), next.done)
    }

    @Test
    fun `does not treat paraphrased task as a new task`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase для полного сохранения истории диалога",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить контракт", "Добавить проверку"),
            done = listOf("Проверить use case"),
            current = "Изменить контракт",
        )

        val next = TaskStateMachine.transition(
            current = current,
            proposed = current.copy(
                task = "Сделать доработку SaveHistoryUseCase для сохранения полной истории",
                state = AgentState.VALIDATION,
                step = 2,
                done = listOf("Проверить use case", "Изменить контракт"),
                current = "Добавить проверку",
            ),
        )

        assertEquals(AgentState.VALIDATION, next.state)
        assertEquals(2, next.step)
    }

    @Test
    fun `moves from execution to nearest allowed stage when done is proposed`() {
        val execution = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case"),
            current = "Изменить код",
        )

        val path = TaskStateMachine.transitionPath(
            current = execution,
            proposed = execution.copy(
                state = AgentState.DONE,
                step = 3,
                done = listOf("Проверить use case", "Изменить код", "Проверить результат"),
                current = "Зафиксировать итоговый результат",
            ),
        )

        assertEquals(
            listOf(AgentState.VALIDATION, AgentState.DONE),
            path.map { it.state }
        )
    }
}
