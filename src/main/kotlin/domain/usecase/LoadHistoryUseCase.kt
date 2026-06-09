package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class LoadHistoryUseCase(private val repo: AgentHistoryRepository) {
    operator fun invoke(): List<AgentMessage> = repo.load()
}