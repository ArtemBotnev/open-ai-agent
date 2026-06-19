package com.artembotnev.openai

import com.artembotnev.openai.domain.context.Invariant
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.infra.openai.workflow.PlanningProposal
import com.artembotnev.openai.infra.openai.workflow.PlanningPromptBuilder
import com.artembotnev.openai.infra.openai.workflow.WorkflowAgentContext
import kotlin.test.Test
import kotlin.test.assertTrue

class PlanningPromptBuilderTest {

    @Test
    fun `system prompt contains invariants and refusal rules`() {
        val context = sampleContext()

        val prompt = PlanningPromptBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("planning-orchestrator"))
        assertTrue(prompt.contains("четырёх внутренних planning worker-ов"))
        assertTrue(prompt.contains("выбирая лучшую часть из каждого уместного предложения"))
        assertTrue(prompt.contains("Обязательные инварианты пользователя"))
        assertTrue(prompt.contains("Clean Architecture"))
        assertTrue(prompt.contains("Используем Koin"))
        assertTrue(prompt.contains("Не предлагай решения, которые нарушают хотя бы один инвариант"))
        assertTrue(prompt.contains("используй слово \"правило\""))
        assertTrue(prompt.contains("\"consideredInvariants\""))
        assertTrue(prompt.contains("\"invariantConflicts\""))
        assertTrue(prompt.contains("Переход в EXECUTION делает только coordinator"))
        assertTrue(prompt.contains("Принимаем ли мы этот план в реализацию?"))
    }

    @Test
    fun `user prompt includes raw invariants markdown and worker proposals`() {
        val context = sampleContext()

        val prompt = PlanningPromptBuilder.buildUserPrompt(
            context = context,
            formattedHistory = "user: Нужен новый экран",
            proposals = listOf(
                PlanningProposal(
                    agentName = "requirements-planner",
                    summary = "Нужно уточнить источник данных",
                    task = "Удаление данных",
                    plan = listOf("Уточнить контракт API", "Подготовить экран"),
                    current = "Уточнить контракт API",
                    questions = listOf("Откуда берём данные?"),
                    consideredInvariants = listOf("Clean Architecture"),
                    invariantConflicts = emptyList(),
                ),
                PlanningProposal(
                    agentName = "developer-planner",
                    summary = "Нужно переиспользовать существующий coordinator и проверить workflow tests",
                    task = "Удаление данных",
                    plan = listOf("Найти существующую точку входа", "Обновить тесты workflow"),
                    current = "Найти существующую точку входа",
                    questions = listOf("Есть ли уже похожий flow в кодовой базе?"),
                    consideredInvariants = listOf("Clean Architecture"),
                    invariantConflicts = emptyList(),
                ),
                PlanningProposal(
                    agentName = "designer-planner",
                    summary = "Нужен явный сценарий подтверждения удаления",
                    task = "Удаление данных",
                    plan = listOf("Уточнить UX подтверждения", "Подготовить тексты"),
                    current = "Уточнить UX подтверждения",
                    questions = listOf("Нужно ли отдельное модальное окно?"),
                    consideredInvariants = emptyList(),
                    invariantConflicts = emptyList(),
                )
            ),
        )

        assertTrue(prompt.contains("Markdown с правилами пользователя"))
        assertTrue(prompt.contains("# Правила пользователя"))
        assertTrue(prompt.contains("- Clean Architecture"))
        assertTrue(prompt.contains("user: Нужен новый экран"))
        assertTrue(prompt.contains("Кандидаты от planning worker-ов"))
        assertTrue(prompt.contains("[requirements-planner]"))
        assertTrue(prompt.contains("[developer-planner]"))
        assertTrue(prompt.contains("[designer-planner]"))
        assertTrue(prompt.contains("questions=[Откуда берём данные?]"))
        assertTrue(prompt.contains("questions=[Есть ли уже похожий flow в кодовой базе?]"))
        assertTrue(prompt.contains("questions=[Нужно ли отдельное модальное окно?]"))
    }

    private fun sampleContext(): WorkflowAgentContext {
        val markdown = """
            # Правила пользователя

            - Clean Architecture
            - Используем Koin
        """.trimIndent()

        return WorkflowAgentContext(
            userText = "Добавь функциональность удаления",
            state = TaskWorkflowState(task = "Удаление данных"),
            userProfileMarkdown = "# Профиль пользователя",
            taskContextMarkdown = "# Контекст задачи",
            invariant = Invariant(
                rules = listOf("Clean Architecture", "Используем Koin"),
            ),
            invariantMarkdown = markdown,
            history = listOf(
                AgentMessage(
                    role = AgentRole.USER,
                    content = "Нужен новый экран",
                )
            ),
        )
    }
}
