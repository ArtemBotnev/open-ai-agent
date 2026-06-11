package com.artembotnev.openai.data.summary

import com.artembotnev.openai.domain.model.ConversationSummary

/**
 * Источник данных для краткого содержания диалога.
 *
 * Отвечает за низкоуровневое хранение [ConversationSummary] (например, в файле)
 * и используется реализациями [com.artembotnev.openai.domain.repository.ConversationSummaryRepository]
 * как конкретный механизм персистентности.
 */
internal interface ConversationSummaryDataSource {

    /**
     * Считывает сохранённое краткое содержание из хранилища.
     *
     * @return сохранённый [ConversationSummary]; пустой [ConversationSummary],
     *   если данные отсутствуют.
     */
    fun load(): ConversationSummary

    /**
     * Полностью перезаписывает хранилище переданным кратким содержанием.
     *
     * @param summary краткое содержание, которое нужно сохранить.
     */
    fun save(summary: ConversationSummary)

    /** Очищает хранилище, удаляя сохранённое краткое содержание. */
    fun clear()
}
