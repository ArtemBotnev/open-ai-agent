package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

/**
 * Сжатое представление ранней части истории диалога.
 *
 * Используется механизмом управления контекстом: вместо того чтобы
 * передавать модели всю историю переписки, в запрос подставляется это
 * краткое содержание, а «как есть» сохраняются лишь последние сообщения.
 *
 * @property text текст краткого содержания; пустая строка, если сжатие ещё не выполнялось.
 * @property coveredCount количество сообщений диалога (без учёта системной инструкции),
 *   уже учтённых в [text]. Сообщения с этим индексом и далее в историю summary не входят.
 */
@Serializable
internal data class ConversationSummary(
    val text: String = "",
    val coveredCount: Int = 0,
) {

    /** Признак того, что краткое содержание уже сформировано. */
    val isEmpty: Boolean get() = text.isBlank()
}
