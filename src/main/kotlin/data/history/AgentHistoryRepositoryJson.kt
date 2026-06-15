package com.artembotnev.openai.data.history

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class AgentHistoryRepositoryJson(
    private val ds: AgentHistoryDataSource
) : AgentHistoryRepository {
    override fun load(): List<AgentMessage> = ds.load()
    override fun save(all: List<AgentMessage>) = ds.save(all)
    override fun append(message: AgentMessage): List<AgentMessage> = ds.append(message)
    override fun clear() = ds.clear()
}
