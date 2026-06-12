package com.artembotnev.openai.data.history

import com.artembotnev.openai.domain.context.SlidingWindowContextManagementStrategy
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentHistoryRepositoryJsonTest {

    @Test
    fun `save keeps only last ten messages`() {
        val dataSource = InMemoryAgentHistoryDataSource()
        val repository = AgentHistoryRepositoryJson(
            ds = dataSource,
            contextStrategy = SlidingWindowContextManagementStrategy(maxMessages = 10)
        )

        repository.save(messages(count = 12))

        assertEquals((3..12).toList(), dataSource.load().map { it.content.toInt() })
    }

    @Test
    fun `append discards oldest messages after reaching window size`() {
        val initialMessages = messages(count = 10)
        val dataSource = InMemoryAgentHistoryDataSource(initialMessages)
        val repository = AgentHistoryRepositoryJson(
            ds = dataSource,
            contextStrategy = SlidingWindowContextManagementStrategy(maxMessages = 10)
        )

        val result = repository.append(message(11))

        assertEquals((2..11).toList(), result.map { it.content.toInt() })
        assertEquals((2..11).toList(), dataSource.load().map { it.content.toInt() })
    }

    @Test
    fun `load trims oversized persisted history`() {
        val dataSource = InMemoryAgentHistoryDataSource(messages(count = 13))
        val repository = AgentHistoryRepositoryJson(
            ds = dataSource,
            contextStrategy = SlidingWindowContextManagementStrategy(maxMessages = 10)
        )

        val result = repository.load()

        assertEquals((4..13).toList(), result.map { it.content.toInt() })
        assertEquals((4..13).toList(), dataSource.load().map { it.content.toInt() })
    }

    private fun messages(count: Int): List<AgentMessage> = (1..count).map(::message)

    private fun message(index: Int): AgentMessage = AgentMessage(
        role = AgentRole.USER,
        content = index.toString()
    )

    private class InMemoryAgentHistoryDataSource(
        initialHistory: List<AgentMessage> = emptyList()
    ) : AgentHistoryDataSource {

        private var history: List<AgentMessage> = initialHistory

        override fun load(): List<AgentMessage> = history

        override fun save(all: List<AgentMessage>) {
            history = all
        }

        override fun append(message: AgentMessage): List<AgentMessage> {
            history = history + message
            return history
        }

        override fun clear() {
            history = emptyList()
        }
    }
}
