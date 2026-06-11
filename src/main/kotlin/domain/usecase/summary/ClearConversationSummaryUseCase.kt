package com.artembotnev.openai.domain.usecase.summary

import com.artembotnev.openai.domain.repository.ConversationSummaryRepository

/**
 * Сценарий удаления сохранённого краткого содержания диалога.
 *
 * @property repo репозиторий краткого содержания, данные которого очищаются.
 */
internal class ClearConversationSummaryUseCase(private val repo: ConversationSummaryRepository) {

    /** Удаляет сохранённое краткое содержание диалога. */
    operator fun invoke() = repo.clear()
}
