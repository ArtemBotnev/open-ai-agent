package com.artembotnev.openai.domain.usecase.history

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

/**
 * Сценарий сохранения истории диалога.
 *
 * @property repo репозиторий истории диалога, в котором сохраняются сообщения.
 */
internal class SaveHistoryUseCase(private val repo: AgentHistoryRepository) {

    /**
     * Полностью перезаписывает историю диалога переданным набором сообщений.
     *
     * @param all список сообщений, который заменяет текущую историю.
     */
    operator fun invoke(all: List<AgentMessage>) = repo.save(all)
}