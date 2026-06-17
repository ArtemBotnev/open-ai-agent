package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal class ValidationSubAgent(
    private val support: OpenAiWorkflowSupport,
) {

    fun run(
        context: WorkflowAgentContext,
        artifact: String,
    ): ValidationAgentResult {
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
            appendLine("Артефакт на проверку:")
            appendLine(artifact)
            appendLine()
            appendLine("Последняя история диалога:")
            appendLine(support.formatHistory(context.history))
        }

        val call = support.complete(VALIDATION_SYSTEM_PROMPT, prompt)
        val payload = support.parseJsonPayload<ValidationPayload>(call.raw)
            ?: ValidationPayload(
                reply = "Не удалось завершить валидацию. Нужна повторная доработка.",
                valid = false,
                issues = listOf("Валидатор не вернул структурированный результат"),
                revisionRequest = "Проверь решение ещё раз и верни исправленный вариант.",
            )

        return ValidationAgentResult(
            reply = payload.reply.trim(),
            valid = payload.valid,
            issues = payload.issues.map(String::trim).filter(String::isNotBlank).distinct(),
            revisionRequest = payload.revisionRequest.trim(),
            usage = call.usage,
        )
    }

    @Serializable
    private data class ValidationPayload(
        val reply: String = "",
        val valid: Boolean = false,
        val issues: List<String> = emptyList(),
        val revisionRequest: String = "",
    )

    private companion object {
        val VALIDATION_SYSTEM_PROMPT = """
            Ты subagent валидации. Проверяй решение относительно задачи и плана.
            Отвечай только JSON-объектом:
            {
              "reply": "краткий итог проверки",
              "valid": true,
              "issues": ["список проблем, если есть"],
              "revisionRequest": "что именно надо исправить для повторного запуска execution"
            }
            
            Правила:
            - valid=true только если решение покрывает план и не содержит явных логических проблем.
            - Если valid=false, issues и revisionRequest должны быть предметными и пригодными для повторной генерации.
            - Не добавляй ничего вне JSON.
        """.trimIndent()
    }
}
