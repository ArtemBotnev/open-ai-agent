package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class SaveHistoryUseCase(private val repo: AgentHistoryRepository) {
    operator fun invoke(all: List<AgentMessage>) = repo.save(all)
}