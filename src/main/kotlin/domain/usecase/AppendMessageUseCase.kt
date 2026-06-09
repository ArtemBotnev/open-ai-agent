package com.artembotnev.openai.domain.usecase

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class AppendMessageUseCase(private val repo: AgentHistoryRepository) {
    operator fun invoke(message: AgentMessage): List<AgentMessage> = repo.append(message)
}