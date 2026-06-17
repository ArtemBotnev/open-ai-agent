package com.artembotnev.openai.domain.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class TaskTransitionGuardTest {

    @Test
    fun `keeps execution when planning is proposed without clarification request`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case"),
            current = "Изменить код",
        )
        val proposed = current.copy(
            state = AgentState.PLANNING,
            current = "Вернуться к плану",
        )

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = "Реализовал основной код и перехожу к следующему шагу.",
        )

        assertEquals(AgentState.EXECUTION, adjusted.state)
    }

    @Test
    fun `allows execution to planning when assistant asks clarification`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case"),
            current = "Изменить код",
        )
        val proposed = current.copy(
            state = AgentState.PLANNING,
            current = "Уточнить требования к сохранению",
        )

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = "Нужно уточнить: сохраняем ли системные сообщения в историю?",
        )

        assertEquals(AgentState.PLANNING, adjusted.state)
    }

    @Test
    fun `moves execution to validation when code artifact is returned without validation result`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case"),
            current = "Изменить код",
        )
        val proposed = current.copy(
            state = AgentState.EXECUTION,
            current = "Изменить код",
        )

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = """
                ```kotlin
                internal class SaveHistoryUseCase(private val repo: AgentHistoryRepository)
                ```
            """.trimIndent(),
        )

        assertEquals(AgentState.VALIDATION, adjusted.state)
    }

    @Test
    fun `keeps planning when implementation is returned during planning`() {
        val current = TaskStateMachine.start("Обновить SaveHistoryUseCase")
        val proposed = current.copy(
            state = AgentState.PLANNING,
            current = "Показать код",
        )

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = """
                ```kotlin
                fun loadWeather() = api.getWeather()
                ```
            """.trimIndent(),
        )

        assertEquals(AgentState.PLANNING, adjusted.state)
    }

    @Test
    fun `promotes execution to done when validation success is stated`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.EXECUTION,
            step = 1,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case"),
            current = "Изменить код",
        )
        val proposed = current.copy(
            state = AgentState.EXECUTION,
            current = "Изменить код",
        )

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = """
                ```kotlin
                internal class SaveHistoryUseCase(private val repo: AgentHistoryRepository)
                ```
                Проверка: соответствует плану, явных ошибок не найдено.
                Итог: результат готов.
            """.trimIndent(),
        )

        assertEquals(AgentState.DONE, adjusted.state)
    }

    @Test
    fun `moves validation back to execution on failure markers`() {
        val current = TaskStateMachine.restore(
            task = "Обновить SaveHistoryUseCase",
            state = AgentState.VALIDATION,
            step = 2,
            total = 3,
            plan = listOf("Проверить use case", "Изменить код", "Проверить результат"),
            done = listOf("Проверить use case", "Изменить код"),
            current = "Проверить результат",
        )
        val proposed = current.copy(state = AgentState.DONE)

        val adjusted = TaskTransitionGuard.adjust(
            current = current,
            proposed = proposed,
            assistantText = "Падает тест на сохранение истории, нужно исправить обработку ошибок.",
        )

        assertEquals(AgentState.EXECUTION, adjusted.state)
    }

    @Test
    fun `detects clarification requests`() {
        assertTrue(TaskTransitionGuard.requestsClarification("Нужно уточнить формат данных?"))
        assertTrue(TaskTransitionGuard.requestsClarification("Какие поля должны попадать в ответ"))
        assertFalse(TaskTransitionGuard.requestsClarification("Реализация завершена, перехожу к проверке"))
    }
}
