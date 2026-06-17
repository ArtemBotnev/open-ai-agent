package com.artembotnev.openai.infra.task

import com.artembotnev.openai.data.context.TaskContextDataSource
import com.artembotnev.openai.data.context.TaskStateDataSource
import com.artembotnev.openai.domain.context.TaskContext
import com.artembotnev.openai.domain.context.TaskContextMemoryUpdater
import com.artembotnev.openai.domain.context.TaskStage
import com.artembotnev.openai.domain.context.TaskState
import com.artembotnev.openai.domain.context.TaskStateMachine
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams

internal class OpenAiTaskContextMemoryUpdater(
    private val model: String,
    private val client: OpenAIClient,
    private val taskContextDataSource: TaskContextDataSource,
    private val taskStateDataSource: TaskStateDataSource,
) : TaskContextMemoryUpdater {

    override fun captureDialogue(userText: String, assistantText: String) {
        val currentContext = taskContextDataSource.load()
        val currentState = taskStateDataSource.load()
            ?: TaskStateMachine.deriveLegacyState(
                currentTask = currentContext.currentTask,
                decisionsAndStatus = currentContext.decisionsAndStatus,
            )
        val updated = generateSnapshot(
            currentContext = currentContext,
            currentState = currentState,
            userText = userText,
            assistantText = assistantText,
        )

        if (updated.taskContext != currentContext) {
            taskContextDataSource.save(updated.taskContext)
        }

        if (updated.taskState != currentState) {
            taskStateDataSource.save(updated.taskState)
        }
    }

    private fun generateSnapshot(
        currentContext: TaskContext,
        currentState: TaskState,
        userText: String,
        assistantText: String,
    ): Snapshot {
        val prompt = buildString {
            appendLine("Текущий контекст задачи:")
            appendLine("Текущая задача: ${currentContext.currentTask.ifBlank { "нет записи" }}")
            appendLine("Контекст:")
            appendLine(currentContext.context.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine("Решения и статус:")
            appendLine(currentContext.decisionsAndStatus.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine("Состояние задачи:")
            appendLine("stage: ${currentState.stage.value}")
            appendLine("currentStep: ${currentState.currentStep}")
            appendLine("expectedAction: ${currentState.expectedAction}")
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
            ?: return Snapshot(currentContext, currentState)

        return parseMarkdownSnapshot(
            currentContext = currentContext,
            currentState = currentState,
            raw = raw,
        ) ?: Snapshot(currentContext, currentState)
    }

    private fun parseMarkdownSnapshot(
        currentContext: TaskContext,
        currentState: TaskState,
        raw: String,
    ): Snapshot? {
        val normalized = raw.trim()
        val taskStart = normalized.indexOf(TASK_HEADING)
        val contextStart = normalized.indexOf(CONTEXT_HEADING)
        val statusStart = normalized.indexOf(STATUS_HEADING)
        val stateStart = normalized.indexOf(STATE_HEADING)
        if (taskStart < 0 || contextStart < 0 || statusStart < 0 || stateStart < 0) return null

        val taskSection = normalized.substring(taskStart + TASK_HEADING.length, contextStart).trim('\n')
        val contextSection = normalized.substring(contextStart + CONTEXT_HEADING.length, statusStart).trim('\n')
        val statusSection = normalized.substring(statusStart + STATUS_HEADING.length, stateStart).trim('\n')
        val stateSection = normalized.substring(stateStart + STATE_HEADING.length).trim('\n')

        val currentTask = sanitizeTask(
            taskSection.lines().firstOrNull { line ->
                val trimmed = line.trim()
                trimmed.isNotBlank() && trimmed != TASK_PRELUDE
            }.orEmpty()
        )
        val context = extractBullets(contextSection)
        val state = extractTaskState(stateSection)
        val decisionsAndStatus = extractBullets(statusSection)
        val resolvedTask = currentTask.ifBlank { currentContext.currentTask }
        val resolvedState = TaskStateMachine.transition(
            current = currentState,
            proposed = state,
            taskChanged = hasTaskChanged(
                currentTask = currentContext.currentTask,
                resolvedTask = resolvedTask,
            ),
        )

        return Snapshot(
            taskContext = TaskContext(
                currentTask = resolvedTask,
                context = context.takeLast(MAX_ITEMS_PER_SECTION),
                decisionsAndStatus = decisionsAndStatus.takeLast(MAX_ITEMS_PER_SECTION),
            ),
            taskState = resolvedState,
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

    private fun extractTaskState(section: String): TaskState {
        val fields = section.lines()
            .map { it.trim() }
            .filter { it.contains(':') }
            .associate { line ->
                val key = line.substringBefore(':').trim()
                val value = line.substringAfter(':').trim()
                key to value
            }

        return TaskStateMachine.restore(
            stage = TaskStage.fromValue(fields["stage"].orEmpty()),
            currentStep = fields["currentStep"].orEmpty(),
            expectedAction = fields["expectedAction"].orEmpty(),
        )
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

    private fun hasTaskChanged(
        currentTask: String,
        resolvedTask: String,
    ): Boolean {
        val current = normalize(currentTask)
        val updated = normalize(resolvedTask)
        return updated.isNotBlank() && current != updated
    }

    private companion object {
        const val TASK_HEADING = "## 1. Текущая задача"
        const val CONTEXT_HEADING = "## 2. Контекст"
        const val STATUS_HEADING = "## 3. Решения и статус"
        const val STATE_HEADING = "## 4. Состояние задачи"
        const val TASK_PRELUDE = "Этот раздел описывает только активную задачу пользователя в текущем диалоге."
        const val MAX_ITEMS_PER_SECTION = 10
        const val MAX_WORDS_IN_TASK = 18
        const val MAX_WORDS_PER_BULLET = 14
        val TASK_CONTEXT_SYSTEM_PROMPT = """
            Ты обновляешь рабочий контекст текущей задачи и отдельное состояние конечного автомата пользователя.
            Верни только markdown без пояснений вне секций.
            Формат ответа строго такой:
            ## 1. Текущая задача
            одна короткая строка
            ## 2. Контекст
            - ...
            ## 3. Решения и статус
            - ...
            ## 4. Состояние задачи
            stage: planning|execution|validation|done
            currentStep: ...
            expectedAction: ...
            Секция 1 должна описывать только текущую активную задачу пользователя.
            Если задача в диалоге сменилась, перезапиши task context под новую задачу.
            Секция 2 содержит только важный технический контекст по текущему заданию.
            Секция 3 содержит только решения, текущий статус и следующие шаги по текущему заданию.
            Секция 4 содержит только явное состояние конечного автомата задачи.
            stage должен быть одним из planning, execution, validation, done.
            currentStep должен описывать только текущий активный шаг.
            expectedAction должен описывать только ожидаемое следующее действие.
            Не добавляй ничего, что относится к долгосрочному профилю пользователя.
            Не пересказывай диалог целиком, не пиши общих фраз и одноразового шума.
            Каждый bullet должен быть коротким, предметным и полезным для продолжения этой задачи.
            Если для секции нет пунктов, оставь секцию без bullets.
            Максимум 4 bullets в секции 2 и 4 bullets в секции 3.
        """.trimIndent()
    }

    private data class Snapshot(
        val taskContext: TaskContext,
        val taskState: TaskState,
    )
}
