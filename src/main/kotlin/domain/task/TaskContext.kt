package com.artembotnev.openai.domain.task

import kotlinx.serialization.Serializable

@Serializable
internal enum class AgentState(val value: String) {
    PLANNING("planning"),
    EXECUTION("execution"),
    VALIDATION("validation"),
    DONE("done");

    companion object {
        fun fromValue(value: String): AgentState? {
            return entries.firstOrNull { it.value.equals(value.trim(), ignoreCase = true) }
        }
    }
}

@Serializable
internal data class TaskContext(
    val task: String,
    val state: AgentState,
    val step: Int,
    val total: Int,
    val plan: List<String>,
    val done: List<String>,
    val current: String,
)

internal object TaskStateMachine {
    private const val MAX_WORDS_IN_TASK = 18
    private const val MAX_WORDS_IN_ITEM = 18
    private const val MAX_ITEMS = 12

    val allowedTransitions: Map<AgentState, Set<AgentState>> = mapOf(
        AgentState.PLANNING to setOf(AgentState.EXECUTION),
        AgentState.EXECUTION to setOf(AgentState.VALIDATION, AgentState.PLANNING),
        AgentState.VALIDATION to setOf(AgentState.DONE, AgentState.EXECUTION),
        AgentState.DONE to emptySet(),
    )

    fun start(task: String): TaskContext {
        return normalize(
            TaskContext(
                task = task,
                state = AgentState.PLANNING,
                step = 0,
                total = 0,
                plan = emptyList(),
                done = emptyList(),
                current = "Собрать требования и подготовить план",
            )
        )
    }

    fun restore(
        task: String,
        state: AgentState?,
        step: Int,
        total: Int,
        plan: List<String>,
        done: List<String>,
        current: String,
    ): TaskContext {
        return normalize(
            TaskContext(
                task = task,
                state = state ?: AgentState.PLANNING,
                step = step,
                total = total,
                plan = plan,
                done = done,
                current = current,
            )
        )
    }

    fun isNewTask(current: TaskContext, candidate: String): Boolean {
        return hasTaskChanged(current.task, candidate)
    }

    fun transition(
        current: TaskContext?,
        proposed: TaskContext,
    ): TaskContext {
        return transitionPath(current, proposed).lastOrNull()
            ?: current
            ?: normalize(
                proposed.copy(
                    state = AgentState.PLANNING,
                )
            )
    }

    fun transitionPath(
        current: TaskContext?,
        proposed: TaskContext,
    ): List<TaskContext> {
        if (current == null) {
            return listOf(
                normalize(
                    proposed.copy(
                        state = AgentState.PLANNING,
                    )
                )
            )
        }

        val taskChanged = hasTaskChanged(current.task, proposed.task)
        if (taskChanged) {
            return listOf(
                proposed.copy(
                    state = AgentState.PLANNING,
                    step = 0,
                    done = emptyList(),
                )
            )
        }

        val statePath = resolveStatePath(
            currentState = current.state,
            proposedState = proposed.state,
        )

        if (statePath.size <= 1) {
            return listOf(
                normalize(
                    proposed.copy(
                        task = current.task,
                        state = current.state,
                    )
                )
            )
        }

        return statePath.drop(1).mapIndexed { index, state ->
            val isFinal = index == statePath.lastIndex - 1
            normalize(
                proposed.copy(
                    task = current.task,
                    state = state,
                    current = if (isFinal) proposed.current else "",
                )
            )
        }
    }

    private fun hasTaskChanged(currentTask: String, proposedTask: String): Boolean {
        val current = normalizeText(currentTask)
        val updated = normalizeText(proposedTask)
        if (updated.isBlank()) return false
        if (current.isBlank()) return true
        if (current == updated) return false
        if (current.contains(updated) || updated.contains(current)) return false

        val currentTokens = tokenize(current)
        val updatedTokens = tokenize(updated)
        if (currentTokens.isEmpty() || updatedTokens.isEmpty()) return true

        val overlap = currentTokens.intersect(updatedTokens).size.toDouble()
        val similarity = overlap / maxOf(currentTokens.size, updatedTokens.size).toDouble()

        return similarity < TASK_SIMILARITY_THRESHOLD
    }

    private fun resolveStatePath(
        currentState: AgentState,
        proposedState: AgentState,
    ): List<AgentState> {
        if (proposedState == currentState) return listOf(currentState)
        if (currentState == AgentState.DONE) return listOf(AgentState.DONE)

        return shortestPath(currentState, proposedState) ?: listOf(currentState)
    }

    private fun shortestPath(
        from: AgentState,
        to: AgentState,
    ): List<AgentState>? {
        val queue = ArrayDeque<List<AgentState>>()
        val visited = mutableSetOf<AgentState>()

        queue.addLast(listOf(from))
        visited += from

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val state = path.last()
            if (state == to) return path

            for (next in allowedTransitions.getValue(state)) {
                if (visited.add(next)) {
                    queue.addLast(path + next)
                }
            }
        }

        return null
    }

    private fun normalize(context: TaskContext): TaskContext {
        val task = sanitizeTask(context.task)
        val plan = context.plan
            .map(::sanitizeItem)
            .filter { it.isNotBlank() }
            .distinctBy(::normalizeText)
            .take(MAX_ITEMS)
        val done = context.done
            .map(::sanitizeItem)
            .filter { it.isNotBlank() }
            .distinctBy(::normalizeText)
            .take(MAX_ITEMS)
        val total = when {
            plan.isNotEmpty() -> plan.size
            context.total > 0 -> context.total.coerceAtMost(MAX_ITEMS)
            else -> 0
        }
        val step = when {
            total == 0 -> 0
            context.state == AgentState.DONE -> context.step.coerceIn(1, total)
            context.step <= 0 -> 1
            else -> context.step.coerceIn(1, total)
        }
        val currentPlanStep = plan.getOrNull((step - 1).coerceAtLeast(0)).orEmpty()
        val current = sanitizeItem(context.current).ifBlank {
            when (context.state) {
                AgentState.PLANNING -> currentPlanStep.ifBlank { "Собрать требования и подготовить план" }
                AgentState.EXECUTION -> currentPlanStep.ifBlank { "Выполнить текущий шаг плана" }
                AgentState.VALIDATION -> "Проверить результат по плану"
                AgentState.DONE -> "Зафиксировать итоговый результат"
            }
        }

        return context.copy(
            task = task,
            step = step,
            total = total,
            plan = plan,
            done = done,
            current = current,
        )
    }

    private fun sanitizeTask(value: String): String {
        return sanitizeWords(value, MAX_WORDS_IN_TASK)
    }

    private fun sanitizeItem(value: String): String {
        return sanitizeWords(value, MAX_WORDS_IN_ITEM)
    }

    private fun sanitizeWords(value: String, limit: Int): String {
        val compact = value
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ';', ':', ' ')

        if (compact.isBlank()) return ""

        return compact
            .split(' ')
            .filter { it.isNotBlank() }
            .take(limit)
            .joinToString(" ")
    }

    private fun normalizeText(value: String): String {
        return value.trim().trimEnd('.', ' ').lowercase()
    }

    private fun tokenize(value: String): Set<String> {
        return value.split(Regex("[^\\p{L}\\p{N}]+"))
            .map { it.trim().lowercase() }
            .filter { it.length >= 3 }
            .toSet()
    }

    private const val TASK_SIMILARITY_THRESHOLD = 0.55
}
