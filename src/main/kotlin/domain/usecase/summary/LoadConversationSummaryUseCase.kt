package com.artembotnev.openai.domain.usecase.summary

import com.artembotnev.openai.domain.model.ConversationSummary
import com.artembotnev.openai.domain.repository.ConversationSummaryRepository

/**
 * Сценарий загрузки сохранённого краткого содержания диалога.
 *
 * @property repo репозиторий краткого содержания, из которого читаются данные.
 */
internal class LoadConversationSummaryUseCase(private val repo: ConversationSummaryRepository) {

    /**
     * Загружает сохранённое краткое содержание.
     *
     * @return сохранённый [ConversationSummary]; пустой [ConversationSummary],
     *   если данные отсутствуют.
     */
    operator fun invoke(): ConversationSummary = repo.load()
}
