package com.artembotnev.openai

import com.artembotnev.openai.domain.context.Invariant
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.infra.openai.workflow.PlanningPromptBuilder
import com.artembotnev.openai.infra.openai.workflow.WorkflowAgentContext
import kotlin.test.Test
import kotlin.test.assertTrue

class PlanningPromptBuilderTest {

    @Test
    fun `system prompt contains invariants and refusal rules`() {
        val context = sampleContext()

        val prompt = PlanningPromptBuilder.buildSystemPrompt(context)

        assertTrue(prompt.contains("Обязательные инварианты пользователя"))
        assertTrue(prompt.contains("Clean Architecture"))
        assertTrue(prompt.contains("Используем Koin"))
        assertTrue(prompt.contains("Не предлагай решения, которые нарушают хотя бы один инвариант"))
        assertTrue(prompt.contains("используй слово \"правило\""))
        assertTrue(prompt.contains("\"consideredInvariants\""))
        assertTrue(prompt.contains("\"invariantConflicts\""))
    }

    @Test
    fun `user prompt includes raw invariants markdown`() {
        val context = sampleContext()

        val prompt = PlanningPromptBuilder.buildUserPrompt(
            context = context,
            formattedHistory = "user: Нужен новый экран",
        )

        assertTrue(prompt.contains("Markdown с правилами пользователя"))
        assertTrue(prompt.contains("# Правила пользователя"))
        assertTrue(prompt.contains("- Clean Architecture"))
        assertTrue(prompt.contains("user: Нужен новый экран"))
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
