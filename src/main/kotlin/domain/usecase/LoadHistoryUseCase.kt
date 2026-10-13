package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

/**
 * Сценарий загрузки сохранённой истории диалога.
 *
 * @property repo репозиторий истории диалога, из которого читаются сообщения.
 */
internal class LoadHistoryUseCase(private val repo: AgentHistoryRepository) {

    /**
     * Загружает всю сохранённую историю диалога.
     *
     * @return список сообщений в порядке их добавления;
     *   пустой список, если история отсутствует.
     */
    operator fun invoke(): List<AgentMessage> = repo.load()
}