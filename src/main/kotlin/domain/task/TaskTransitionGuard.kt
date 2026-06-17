package com.artembotnev.openai.domain.task

internal object TaskTransitionGuard {
    private val clarificationMarkers = listOf(
        "уточн",
        "уточнит",
        "нужны детали",
        "нужно уточнить",
        "подтверд",
        "какой",
        "какие",
        "какая",
        "какое",
        "как именно",
        "верно ли",
        "правильно ли",
        "не хватает",
        "нужна информация",
        "нужны данные",
        "какой именно",
        "какие именно",
        "уточни",
        "подскажи",
    )

    private val validationFailureMarkers = listOf(
        "не проходит",
        "не прошел",
        "падает тест",
        "ошибка",
        "ошибки",
        "нужно исправить",
        "требует исправления",
        "не соответствует",
        "неверно",
        "проблема",
        "сломано",
        "провалилась проверка",
        "провалил валидацию",
    )

    private val validationSuccessMarkers = listOf(
        "проверено",
        "проверил",
        "проверка пройдена",
        "валидация пройдена",
        "тесты прошли",
        "ревью пройдено",
        "ошибок нет",
        "готово",
        "результат готов",
        "всё готово",
        "все готово",
        "соответствует плану",
        "ошибок не найдено",
        "ошибок не нашел",
        "явных ошибок не найдено",
    )

    fun adjust(
        current: TaskContext?,
        proposed: TaskContext,
        assistantText: String,
    ): TaskContext {
        if (current == null) return proposed

        val normalized = assistantText.lowercase()

        return when (current.state) {
            AgentState.PLANNING -> when {
                requestsClarification(normalized) -> proposed.copy(state = AgentState.PLANNING)
                containsImplementationArtifact(assistantText) ->
                    proposed.copy(state = AgentState.PLANNING)
                else -> proposed.copy(state = AgentState.PLANNING)
            }

            AgentState.EXECUTION -> when {
                requestsClarification(normalized) -> proposed.copy(state = AgentState.PLANNING)
                validationFailed(normalized) -> proposed.copy(state = AgentState.EXECUTION)
                validationSucceeded(normalized) -> proposed.copy(state = AgentState.DONE)
                containsImplementationArtifact(assistantText) -> proposed.copy(state = AgentState.VALIDATION)
                proposed.state == AgentState.PLANNING -> proposed.copy(state = AgentState.EXECUTION)
                else -> proposed
            }

            AgentState.VALIDATION -> when {
                validationFailed(normalized) -> proposed.copy(state = AgentState.EXECUTION)
                validationSucceeded(normalized) -> proposed.copy(state = AgentState.DONE)
                containsImplementationArtifact(assistantText) -> proposed.copy(state = AgentState.EXECUTION)
                else -> proposed.copy(state = AgentState.VALIDATION)
            }

            AgentState.DONE -> proposed.copy(state = AgentState.DONE)
        }
    }

    fun requestsClarification(assistantText: String): Boolean {
        val normalized = assistantText.lowercase()
        if (normalized.isBlank()) return false

        val hasMarker = clarificationMarkers.any { normalized.contains(it) }

        return hasMarker
    }

    fun containsImplementationArtifact(assistantText: String): Boolean {
        val text = assistantText.lowercase()
        return assistantText.contains("```") ||
            text.contains("diff --git") ||
            text.contains("[file:") ||
            text.contains("fun ") ||
            text.contains("class ") ||
            text.contains("def ") ||
            text.contains("import ") ||
            text.contains("val ") ||
            text.contains("const val ") ||
            text.contains("operator fun ")
    }

    fun validationFailed(assistantText: String): Boolean {
        val normalized = assistantText.lowercase()
        return validationFailureMarkers.any { normalized.contains(it) }
    }

    fun validationSucceeded(assistantText: String): Boolean {
        val normalized = assistantText.lowercase()
        return validationSuccessMarkers.any { normalized.contains(it) }
    }
}
