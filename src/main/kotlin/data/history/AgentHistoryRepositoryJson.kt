package com.artembotnev.openai.data.history

import com.artembotnev.openai.domain.context.ContextManagementStrategy
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.repository.AgentHistoryRepository

internal class AgentHistoryRepositoryJson(
    private val ds: AgentHistoryDataSource,
    private val contextStrategy: ContextManagementStrategy
) : AgentHistoryRepository {

    override fun load(): List<AgentMessage> = persistManaged(ds.load())

    override fun save(all: List<AgentMessage>) {
        ds.save(contextStrategy.apply(all))
    }

    override fun append(message: AgentMessage): List<AgentMessage> {
        val updatedHistory = ds.append(message)
        return persistManaged(updatedHistory)
    }

    override fun clear() = ds.clear()

    private fun persistManaged(history: List<AgentMessage>): List<AgentMessage> {
        val managedHistory = contextStrategy.apply(history)
        if (managedHistory != history) {
            ds.save(managedHistory)
        }

        return managedHistory
    }
}
