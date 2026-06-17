package com.artembotnev.openai.infra.openai.workflow

internal class DoneSubAgent(
    private val support: OpenAiWorkflowSupport,
) {

    fun run(
        context: WorkflowAgentContext,
        artifact: String,
        validationSummary: String,
    ): DoneAgentResult {
        val prompt = buildString {
            appendLine("Профиль пользователя:")
            appendLine(context.userProfileMarkdown.ifBlank { "Профиль пуст" })
            appendLine()
            appendLine("Task context пользователя:")
            appendLine(context.taskContextMarkdown.ifBlank { "Task context пуст" })
            appendLine()
            appendLine("Задача:")
            appendLine(context.state.task.ifBlank { context.userText })
            appendLine()
            appendLine("План:")
            if (context.state.plan.isEmpty()) appendLine("- План отсутствует")
            context.state.plan.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Что выполнено:")
            if (context.state.done.isEmpty()) appendLine("- Выполненные шаги не зафиксированы")
            context.state.done.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Итог валидации:")
            appendLine(validationSummary.ifBlank { "Валидация завершена без текста" })
            appendLine()
            appendLine("Итоговый артефакт:")
            appendLine(artifact)
        }

        val call = support.complete(DONE_SYSTEM_PROMPT, prompt)
        return DoneAgentResult(
            reply = call.raw.ifBlank { "Задача завершена, но итоговый ответ пуст." },
            usage = call.usage,
        )
    }

    private companion object {
        val DONE_SYSTEM_PROMPT = """
            Ты финальный subagent. Сформируй ответ пользователю по завершённой задаче.
            Правила:
            - Пиши по-русски.
            - Кратко перечисли, что сделано.
            - Затем отдай итоговый артефакт пользователю.
            - Не упоминай внутренние JSON, служебные состояния или субагентов.
            - Ответ должен быть пригоден для прямой отправки пользователю.
        """.trimIndent()
    }
}
