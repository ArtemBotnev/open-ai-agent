package com.artembotnev.openai.domain.context

import kotlinx.serialization.Serializable

@Serializable
internal enum class TaskStage(val value: String) {
    PLANNING("planning"),
    EXECUTION("execution"),
    VALIDATION("validation"),
    DONE("done");

    companion object {
        fun fromValue(value: String): TaskStage? {
            return entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) }
        }
    }
}

@Serializable
internal data class TaskState(
    val stage: TaskStage,
    val currentStep: String,
    val expectedAction: String,
)

internal object TaskStateMachine {
    private const val MAX_WORDS_PER_FIELD = 18

    val allowedTransitions: Map<TaskStage, Set<TaskStage>> = mapOf(
        TaskStage.PLANNING to setOf(TaskStage.PLANNING, TaskStage.EXECUTION),
        TaskStage.EXECUTION to setOf(TaskStage.EXECUTION, TaskStage.VALIDATION),
        TaskStage.VALIDATION to setOf(TaskStage.VALIDATION, TaskStage.EXECUTION, TaskStage.DONE),
        TaskStage.DONE to setOf(TaskStage.DONE),
    )

    fun initial(): TaskState {
        return normalize(
            TaskState(
                stage = TaskStage.PLANNING,
                currentStep = "",
                expectedAction = "",
            )
        )
    }

    fun restore(
        stage: TaskStage?,
        currentStep: String,
        expectedAction: String,
    ): TaskState {
        return normalize(
            TaskState(
                stage = stage ?: TaskStage.PLANNING,
                currentStep = currentStep,
                expectedAction = expectedAction,
            )
        )
    }

    fun deriveLegacyState(
        currentTask: String,
        decisionsAndStatus: List<String>,
    ): TaskState {
        val normalizedBullets = decisionsAndStatus.map { it.lowercase() }
        val stage = when {
            normalizedBullets.any { it.contains("заверш") || it.contains("готово") } -> TaskStage.DONE
            normalizedBullets.any { it.contains("провер") || it.contains("валида") || it.contains("тест") } -> TaskStage.VALIDATION
            normalizedBullets.isNotEmpty() -> TaskStage.EXECUTION
            currentTask.isNotBlank() -> TaskStage.PLANNING
            else -> TaskStage.PLANNING
        }

        return normalize(
            TaskState(
                stage = stage,
                currentStep = decisionsAndStatus.lastOrNull().orEmpty(),
                expectedAction = "",
            )
        )
    }

    fun transition(
        current: TaskState,
        proposed: TaskState,
        taskChanged: Boolean,
    ): TaskState {
        if (taskChanged) {
            return normalize(
                TaskState(
                    stage = TaskStage.PLANNING,
                    currentStep = proposed.currentStep,
                    expectedAction = proposed.expectedAction,
                )
            )
        }

        val nextStage = resolveStage(
            currentStage = current.stage,
            proposedStage = proposed.stage,
        )
        val adjustedProposal = if (nextStage == proposed.stage) {
            proposed
        } else {
            proposed.copy(expectedAction = "")
        }

        return normalize(
            TaskState(
                stage = nextStage,
                currentStep = adjustedProposal.currentStep,
                expectedAction = adjustedProposal.expectedAction,
            )
        )
    }

    private fun resolveStage(
        currentStage: TaskStage,
        proposedStage: TaskStage,
    ): TaskStage {
        if (proposedStage == currentStage) return proposedStage
        if (allowedTransitions.getValue(currentStage).contains(proposedStage)) return proposedStage

        return when (currentStage) {
            TaskStage.PLANNING -> TaskStage.EXECUTION
            TaskStage.EXECUTION -> TaskStage.VALIDATION
            TaskStage.VALIDATION -> TaskStage.EXECUTION
            TaskStage.DONE -> TaskStage.DONE
        }
    }

    private fun normalize(state: TaskState): TaskState {
        val currentStep = sanitizeField(state.currentStep)
        val expectedAction = sanitizeField(state.expectedAction)

        return when (state.stage) {
            TaskStage.PLANNING -> state.copy(
                currentStep = currentStep.ifBlank { "Сформировать план решения" },
                expectedAction = expectedAction.ifBlank { "Перейти к выполнению после плана" },
            )

            TaskStage.EXECUTION -> state.copy(
                currentStep = currentStep.ifBlank { "Выполнить текущий шаг плана" },
                expectedAction = expectedAction.ifBlank { "Подготовить результат к проверке" },
            )

            TaskStage.VALIDATION -> state.copy(
                currentStep = currentStep.ifBlank { "Проверить полученный результат" },
                expectedAction = expectedAction.ifBlank { "Завершить задачу или вернуть в execution" },
            )

            TaskStage.DONE -> state.copy(
                currentStep = currentStep.ifBlank { "Задача завершена" },
                expectedAction = expectedAction.ifBlank { "Нет действий" },
            )
        }
    }

    private fun sanitizeField(value: String): String {
        val compact = value
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ';', ':', ' ')

        if (compact.isBlank()) return ""

        return compact
            .split(' ')
            .filter { it.isNotBlank() }
            .take(MAX_WORDS_PER_FIELD)
            .joinToString(" ")
    }
}
