package com.artembotnev.openai.domain.usecase.summary

import com.artembotnev.openai.domain.model.ConversationSummary
import com.artembotnev.openai.domain.repository.ConversationSummaryRepository

/**
 * Сценарий сохранения краткого содержания диалога.
 *
 * @property repo репозиторий краткого содержания, в котором сохраняются данные.
 */
internal class SaveConversationSummaryUseCase(private val repo: ConversationSummaryRepository) {

    /**
     * Полностью перезаписывает сохранённое краткое содержание.
     *
     * @param summary краткое содержание, которое нужно сохранить.
     */
    operator fun invoke(summary: ConversationSummary) = repo.save(summary)
}
