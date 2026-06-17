package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal class ExecutionSubAgent(
    private val support: OpenAiWorkflowSupport,
) {

    fun run(
        context: WorkflowAgentContext,
        revisionRequest: String,
    ): ExecutionAgentResult {
        val prompt = buildString {
            appendLine("Профиль пользователя:")
            appendLine(context.userProfileMarkdown.ifBlank { "Профиль пуст" })
            appendLine()
            appendLine("Task context пользователя:")
            appendLine(context.taskContextMarkdown.ifBlank { "Task context пуст" })
            appendLine()
            appendLine("Подтверждённая задача:")
            appendLine(context.state.task.ifBlank { context.userText })
            appendLine()
            appendLine("Подтверждённый план:")
            if (context.state.plan.isEmpty()) appendLine("- План отсутствует")
            context.state.plan.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Уже выполненные шаги:")
            if (context.state.done.isEmpty()) appendLine("- Пока нет")
            context.state.done.forEach { appendLine("- $it") }
            appendLine()
            if (context.state.lastDraft.isNotBlank()) {
                appendLine("Предыдущий черновик:")
                appendLine(context.state.lastDraft)
                appendLine()
            }
            if (revisionRequest.isNotBlank()) {
                appendLine("Замечания после валидации, которые нужно исправить:")
                appendLine(revisionRequest)
                appendLine()
            }
            appendLine("Последняя история диалога:")
            appendLine(support.formatHistory(context.history))
            appendLine()
            appendLine("Последнее сообщение пользователя:")
            appendLine(context.userText)
        }

        val call = support.complete(EXECUTION_SYSTEM_PROMPT, prompt)
        val payload = support.parseJsonPayload<ExecutionPayload>(call.raw)
            ?: ExecutionPayload(
                reply = "Не удалось надёжно сформировать код. Нужны уточнения по задаче.",
                needsClarification = true,
                completedSteps = emptyList(),
                current = context.state.current,
            )
        val artifact = if (payload.needsClarification) "" else support.extractArtifactBlock(call.raw)

        return ExecutionAgentResult(
            reply = payload.reply.trim(),
            needsClarification = payload.needsClarification || artifact.isBlank(),
            completedSteps = payload.completedSteps.map(String::trim).filter(String::isNotBlank).distinct(),
            current = payload.current.trim(),
            artifact = artifact,
            usage = call.usage,
        )
    }

    @Serializable
    private data class ExecutionPayload(
        val reply: String = "",
        val needsClarification: Boolean = false,
        val completedSteps: List<String> = emptyList(),
        val current: String = "",
    )

    private companion object {
        val EXECUTION_SYSTEM_PROMPT = """
            Ты subagent исполнения. Ты получаешь подтверждённый план и пишешь решение по нему.
            Если данных достаточно, верни ответ строго в двух блоках:
            
            ```json
            {
              "reply": "краткая внутренняя сводка",
              "needsClarification": false,
              "completedSteps": ["что выполнено"],
              "current": "что осталось, либо пустая строка"
            }
            ```
            ```text
            здесь сам итоговый код или артефакт
            ```
            
            Если требований не хватает или есть противоречие, верни только JSON:
            {
              "reply": "какое уточнение нужно у пользователя",
              "needsClarification": true,
              "completedSteps": [],
              "current": "что нужно уточнить"
            }
            
            Правила:
            - Не добавляй ничего вне указанного формата.
            - completedSteps должны ссылаться на шаги плана.
            - Если получил замечания от VALIDATION, исправь код с их учётом.
            - Если код готов, needsClarification должен быть false и второй блок обязателен.
        """.trimIndent()
    }
}
