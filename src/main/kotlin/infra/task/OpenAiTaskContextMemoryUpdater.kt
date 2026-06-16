package com.artembotnev.openai.infra.task

import com.artembotnev.openai.data.context.TaskContextDataSource
import com.artembotnev.openai.domain.context.TaskContext
import com.artembotnev.openai.domain.context.TaskContextMemoryUpdater
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams

internal class OpenAiTaskContextMemoryUpdater(
    private val model: String,
    private val client: OpenAIClient,
    private val taskContextDataSource: TaskContextDataSource,
) : TaskContextMemoryUpdater {

    override fun captureDialogue(userText: String, assistantText: String) {
        val current = taskContextDataSource.load()
        val updated = generateSnapshot(current, userText, assistantText)

        if (updated == current) return

        taskContextDataSource.save(updated)
    }

    private fun generateSnapshot(
        current: TaskContext,
        userText: String,
        assistantText: String,
    ): TaskContext {
        val prompt = buildString {
            appendLine("Текущий контекст задачи:")
            appendLine("Текущая задача: ${current.currentTask.ifBlank { "нет записи" }}")
            appendLine("Контекст:")
            appendLine(current.context.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine("Решения и статус:")
            appendLine(current.decisionsAndStatus.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine()
            appendLine("Последний диалог:")
            appendLine("Пользователь: $userText")
            appendLine("Агент: $assistantText")
        }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(TASK_CONTEXT_SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .build()

        val raw = client.chat()
            .completions()
            .create(params)
            .choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            ?: return current

        return parseMarkdownSnapshot(raw) ?: current
    }

    private fun parseMarkdownSnapshot(raw: String): TaskContext? {
        val normalized = raw.trim()
        val taskStart = normalized.indexOf(TASK_HEADING)
        val contextStart = normalized.indexOf(CONTEXT_HEADING)
        val statusStart = normalized.indexOf(STATUS_HEADING)
        if (taskStart < 0 || contextStart < 0 || statusStart < 0) return null

        val taskSection = normalized.substring(taskStart + TASK_HEADING.length, contextStart).trim('\n')
        val contextSection = normalized.substring(contextStart + CONTEXT_HEADING.length, statusStart).trim('\n')
        val statusSection = normalized.substring(statusStart + STATUS_HEADING.length).trim('\n')

        val currentTask = sanitizeTask(taskSection.lines().firstOrNull { it.trim().isNotBlank() }.orEmpty())
        val context = extractBullets(contextSection)
        val decisionsAndStatus = extractBullets(statusSection)

        return TaskContext(
            currentTask = currentTask,
            context = context.takeLast(MAX_ITEMS_PER_SECTION),
            decisionsAndStatus = decisionsAndStatus.takeLast(MAX_ITEMS_PER_SECTION),
        )
    }

    private fun extractBullets(section: String): List<String> {
        return section.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { sanitizeBullet(it.removePrefix("- ")) }
            .filter { it.isNotBlank() }
            .distinctBy { normalize(it) }
    }

    private fun sanitizeTask(value: String): String {
        val compact = value
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ';', ':', ' ')

        if (compact.isBlank()) return ""

        return compact
            .split(' ')
            .filter { it.isNotBlank() }
            .take(MAX_WORDS_IN_TASK)
            .joinToString(" ")
    }

    private fun sanitizeBullet(value: String): String {
        val compact = value
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("- ")
            .trimEnd('.', ';', ':', ' ')

        if (compact.isBlank()) return ""

        return compact
            .split(' ')
            .filter { it.isNotBlank() }
            .take(MAX_WORDS_PER_BULLET)
            .joinToString(" ")
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .removePrefix("- ")
            .trimEnd('.', ' ')
            .lowercase()
    }

    private companion object {
        const val TASK_HEADING = "## 1. Текущая задача"
        const val CONTEXT_HEADING = "## 2. Контекст"
        const val STATUS_HEADING = "## 3. Решения и статус"
        const val MAX_ITEMS_PER_SECTION = 10
        const val MAX_WORDS_IN_TASK = 18
        const val MAX_WORDS_PER_BULLET = 14
        val TASK_CONTEXT_SYSTEM_PROMPT = """
            Ты обновляешь markdown-файл рабочего контекста текущего пользователя, например users/<id>/task_context.md.
            Верни только markdown без пояснений вне секций.
            Формат ответа строго такой:
            ## 1. Текущая задача
            одна короткая строка
            ## 2. Контекст
            - ...
            ## 3. Решения и статус
            - ...
            Секция 1 должна описывать только текущую активную задачу пользователя.
            Если задача в диалоге сменилась, перезапиши task context под новую задачу.
            Секция 2 содержит только важный технический контекст по текущему заданию.
            Секция 3 содержит только решения, текущий статус и следующие шаги по текущему заданию.
            Не добавляй ничего, что относится к долгосрочному профилю пользователя.
            Не пересказывай диалог целиком, не пиши общих фраз и одноразового шума.
            Каждый bullet должен быть коротким, предметным и полезным для продолжения этой задачи.
            Если для секции нет пунктов, оставь секцию без bullets.
            Максимум 4 bullets в секции 2 и 4 bullets в секции 3.
        """.trimIndent()
    }
}
