package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

/**
 * Сценарий добавления одного сообщения в историю диалога.
 *
 * @property repo репозиторий истории диалога, в который добавляется сообщение.
 */
internal class AppendMessageUseCase(private val repo: AgentHistoryRepository) {

    /**
     * Добавляет сообщение в конец истории.
     *
     * @param message сообщение, которое нужно добавить.
     * @return обновлённый список сообщений с учётом добавленного.
     */
    operator fun invoke(message: AgentMessage): List<AgentMessage> = repo.append(message)
}