package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal class PlanningSubAgent(
    private val support: OpenAiWorkflowSupport,
) {

    fun run(context: WorkflowAgentContext): PlanningAgentResult {
        val prompt = buildString {
            appendLine("Профиль пользователя:")
            appendLine(context.userProfileMarkdown.ifBlank { "Профиль пуст" })
            appendLine()
            appendLine("Task context пользователя:")
            appendLine(context.taskContextMarkdown.ifBlank { "Task context пуст" })
            appendLine()
            appendLine("Текущее состояние workflow:")
            appendLine("task=${context.state.task}")
            appendLine("state=${context.state.state}")
            appendLine("plan=${context.state.plan.joinToString(prefix = "[", postfix = "]")}")
            appendLine("done=${context.state.done.joinToString(prefix = "[", postfix = "]")}")
            appendLine("current=${context.state.current}")
            appendLine("waitingForUserConfirmation=${context.state.waitingForUserConfirmation}")
            appendLine()
            appendLine("Последняя история диалога:")
            appendLine(support.formatHistory(context.history))
            appendLine()
            appendLine("Новое сообщение пользователя:")
            appendLine(context.userText)
        }

        val call = support.complete(PLANNING_SYSTEM_PROMPT, prompt)
        val payload = support.parseJsonPayload<PlanningPayload>(call.raw)
            ?: PlanningPayload(
                reply = "Не удалось надёжно распознать планирование. Уточни задачу и ограничения, после этого я пересоберу план.",
                task = context.state.task.ifBlank { context.userText },
                plan = context.state.plan,
                current = context.state.current,
                awaitingConfirmation = false,
                readyForExecution = false,
            )

        return PlanningAgentResult(
            reply = payload.reply.trim(),
            task = payload.task.trim(),
            plan = payload.plan.map(String::trim).filter(String::isNotBlank).distinct(),
            current = payload.current.trim(),
            awaitingConfirmation = payload.awaitingConfirmation,
            readyForExecution = payload.readyForExecution,
            usage = call.usage,
        )
    }

    @Serializable
    private data class PlanningPayload(
        val reply: String = "",
        val task: String = "",
        val plan: List<String> = emptyList(),
        val current: String = "",
        val awaitingConfirmation: Boolean = false,
        val readyForExecution: Boolean = false,
    )

    private companion object {
        val PLANNING_SYSTEM_PROMPT = """
            Ты subagent планирования в state machine с этапами PLANNING -> EXECUTION -> VALIDATION -> DONE.
            Твоя задача:
            - собрать недостающие требования;
            - сформировать или уточнить короткий пошаговый план;
            - дождаться явного подтверждения пользователя перед переходом к EXECUTION.
            
            Отвечай только JSON-объектом без markdown и пояснений вокруг:
            {
              "reply": "сообщение пользователю",
              "task": "краткое название задачи",
              "plan": ["шаг 1", "шаг 2"],
              "current": "текущий ближайший шаг",
              "awaitingConfirmation": true,
              "readyForExecution": false
            }
            
            Правила:
            - Если требований не хватает, задай точечные вопросы в "reply", plan можешь оставить пустым или черновым.
            - Если план уже готов, в "reply" покажи его кратко и попроси пользователя подтвердить.
            - "readyForExecution" ставь true только если пользователь явно подтвердил план или написал, что всё верно.
            - Если пользователь внёс правки, обнови plan и снова запроси подтверждение.
            - Не переходи к кодированию сам, только подготовь данные.
            - План должен быть коротким, конкретным, без воды.
        """.trimIndent()
    }
}
