package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class ClearHistoryUseCase(private val repo: AgentHistoryRepository) {
    operator fun invoke() = repo.clear()
}