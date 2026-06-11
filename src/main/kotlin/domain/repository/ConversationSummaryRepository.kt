package com.artembotnev.openai.domain.repository

import com.artembotnev.openai.domain.model.ConversationSummary

/**
 * Репозиторий краткого содержания диалога.
 *
 * Абстрагирует доменный слой от конкретного механизма хранения,
 * предоставляя операции чтения, сохранения и очистки [ConversationSummary].
 */
internal interface ConversationSummaryRepository {

    /**
     * Загружает сохранённое краткое содержание диалога.
     *
     * @return сохранённый [ConversationSummary]; пустой [ConversationSummary],
     *   если данные отсутствуют.
     */
    fun load(): ConversationSummary

    /**
     * Полностью перезаписывает сохранённое краткое содержание.
     *
     * @param summary краткое содержание, которое нужно сохранить.
     */
    fun save(summary: ConversationSummary)

    /** Удаляет сохранённое краткое содержание диалога. */
    fun clear()
}
