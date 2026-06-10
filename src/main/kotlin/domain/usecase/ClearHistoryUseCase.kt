package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.repository.AgentHistoryRepository

/**
 * Сценарий очистки всей истории диалога.
 *
 * @property repo репозиторий истории диалога, который требуется очистить.
 */
internal class ClearHistoryUseCase(private val repo: AgentHistoryRepository) {

    /** Удаляет всю сохранённую историю диалога. */
    operator fun invoke() = repo.clear()
}