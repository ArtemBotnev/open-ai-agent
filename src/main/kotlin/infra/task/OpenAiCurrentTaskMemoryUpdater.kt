package com.artembotnev.openai.infra.task

import com.artembotnev.openai.data.context.TaskNotesDataSource
import com.artembotnev.openai.data.task.CurrentTaskDataSource
import com.artembotnev.openai.domain.task.AgentState
import com.artembotnev.openai.domain.task.CurrentTaskMemoryUpdater
import com.artembotnev.openai.domain.task.TaskContext
import com.artembotnev.openai.domain.task.TaskTransitionGuard
import com.artembotnev.openai.domain.task.TaskStateMachine
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams

internal class OpenAiCurrentTaskMemoryUpdater(
    private val model: String,
    private val client: OpenAIClient,
    private val currentTaskDataSource: CurrentTaskDataSource,
    private val taskNotesDataSource: TaskNotesDataSource,
) : CurrentTaskMemoryUpdater {

    override fun captureDialogue(userText: String, assistantText: String) {
        val currentTask = currentTaskDataSource.load()
        val updated = generateSnapshot(
            currentTask = currentTask,
            userText = userText,
            assistantText = assistantText,
        )

        if (updated.path.isEmpty()) return

        var previousState = currentTask?.state
        updated.path.forEach { snapshot ->
            if (previousState != snapshot.state) {
                println("Task state changed: ${previousState?.value ?: "none"} -> ${snapshot.state.value}")
            }
            previousState = snapshot.state
        }

        currentTaskDataSource.save(updated.path.last())
    }

    private fun generateSnapshot(
        currentTask: TaskContext?,
        userText: String,
        assistantText: String,
    ): SnapshotUpdate {
        val taskNotes = taskNotesDataSource.load()
        val prompt = buildString {
            appendLine("Текущая рабочая задача агента:")
            appendLine(currentTask?.let(::renderTaskContext) ?: "нет активной задачи")
            appendLine()
            appendLine("Текущий task context:")
            appendLine("Задача: ${taskNotes.currentTask.ifBlank { "нет записи" }}")
            appendLine("Контекст:")
            appendLine(taskNotes.context.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine("Решения и статус:")
            appendLine(taskNotes.decisionsAndStatus.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine()
            appendLine("Последний диалог:")
            appendLine("Пользователь: $userText")
            appendLine("Агент: $assistantText")
        }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(CURRENT_TASK_SYSTEM_PROMPT)
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
            ?: return SnapshotUpdate(
                path = listOf(currentTask ?: TaskStateMachine.start(userText))
            )

        val proposed = parseJson(raw) ?: return SnapshotUpdate(
            path = listOf(currentTask ?: TaskStateMachine.start(userText))
        )
        val adjusted = TaskTransitionGuard.adjust(
            current = currentTask,
            proposed = proposed,
            assistantText = assistantText,
        )
        return SnapshotUpdate(
            path = TaskStateMachine.transitionPath(currentTask, adjusted)
        )
    }

    private fun parseJson(raw: String): TaskContext? {
        val task = matchString(raw, "task") ?: return null
        val state = AgentState.fromValue(matchString(raw, "state").orEmpty())
        val step = matchInt(raw, "step") ?: 0
        val total = matchInt(raw, "total") ?: 0
        val plan = matchArray(raw, "plan")
        val done = matchArray(raw, "done")
        val current = matchString(raw, "current").orEmpty()

        return TaskStateMachine.restore(
            task = task,
            state = state,
            step = step,
            total = total,
            plan = plan,
            done = done,
            current = current,
        )
    }

    private fun matchString(raw: String, key: String): String? {
        val match = Regex(""""$key"\s*:\s*"((?:[^"\\\\]|\\\\.)*)"""").find(raw) ?: return null
        return match.groupValues[1]
            .replace("""\"""", "\"")
            .replace("""\\n""", " ")
            .replace("""\\\""", "\"")
            .trim()
    }

    private fun matchInt(raw: String, key: String): Int? {
        return Regex(""""$key"\s*:\s*(\d+)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun matchArray(raw: String, key: String): List<String> {
        val body = Regex(""""$key"\s*:\s*\[(.*?)]""", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(raw)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: return emptyList()

        if (body.isBlank()) return emptyList()

        return Regex(""""((?:[^"\\\\]|\\\\.)*)"""")
            .findAll(body)
            .map { match ->
                match.groupValues[1]
                    .replace("""\"""", "\"")
                    .replace("""\\n""", " ")
                    .replace("""\\\""", "\"")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun renderTaskContext(context: TaskContext): String {
        return buildString {
            appendLine("task: ${context.task}")
            appendLine("state: ${context.state.value}")
            appendLine("step: ${context.step}")
            appendLine("total: ${context.total}")
            appendLine("plan:")
            context.plan.forEach { appendLine("- $it") }
            appendLine("done:")
            context.done.forEach { appendLine("- $it") }
            append("current: ${context.current}")
        }
    }

    private companion object {
        val CURRENT_TASK_SYSTEM_PROMPT = """
            Ты обновляешь persisted-состояние текущей рабочей задачи агента.
            Верни только JSON без markdown и пояснений в формате:
            {
              "task": "краткая формулировка задачи",
              "state": "planning|execution|validation|done",
              "step": 0,
              "total": 0,
              "plan": ["..."],
              "done": ["..."],
              "current": "..."
            }
            state означает этап агентной задачи:
            planning — собираем требования, уточняем и показываем план пользователю;
            execution — пишем код и создаем артефакты;
            validation — тесты, ревью и сверка с планом;
            done — задача завершена и результат зафиксирован.
            step — номер текущего реального шага задачи в последовательном плане, а не номер варианта.
            Если план уже сформирован, step должен быть в диапазоне 1..total даже на стадии planning.
            0 используй только если плана еще нет.
            total — общее число шагов, должно совпадать с размером plan.
            plan — список реальных последовательных шагов задачи, а не варианты и не альтернативы.
            done — список реально завершенных шагов из plan.
            current — текущий активный реальный шаг или краткое текущее действие агента.
            Если задача сменилась, опиши новую задачу и установи state=planning.
            Допустимые переходы строго такие:
            planning -> execution
            execution -> validation или planning
            validation -> done или execution
            done -> никуда
            Жесткие правила стадий:
            1. planning — только сбор требований и план, без кода и без артефактов реализации.
            2. execution — агент пишет код или создает конкретный артефакт только после явного запроса пользователя на реализацию.
            3. после execution должна быть validation.
            4. если validation успешна, итоговый этап — done с кратким отчетом.
            planning -> execution используй только если план уже готов и пользователь явно попросил реализовать решение, написать код, запрос, класс, функцию, diff или patch.
            execution -> planning используй только когда агент сам явно просит уточнение или дополнительную информацию.
            Если по содержанию ответа кажется, что задача уже на более позднем этапе, чем текущий state, не перепрыгивай через стадии.
            Вместо этого верни ближайший следующий логический этап.
            Если задача еще не согласована или идет уточнение требований, оставляй planning.
            Если агент только уточняет требования или показывает план, оставляй planning.
            Если агент выдал код на стадии planning, считай это нарушением правил стадии и не продвигай задачу дальше planning.
            Если агент пишет код или создает артефакты, используй execution.
            Если агент уже выдал реализацию и затем делает самопроверку, используй validation.
            Если агент делает тесты, ревью или сверяет результат с планом, переводи в validation.
            Если агент проверяет, тестирует или сверяет результат, используй validation.
            Если агент в одном ответе сначала реализовал решение, а потом явно написал краткую проверку и итог успешности, используй done.
            Если задача явно завершена, используй done.
        """.trimIndent()
    }

    private data class SnapshotUpdate(
        val path: List<TaskContext>,
    )
}
