package com.artembotnev.openai.domain.repository

import com.artembotnev.openai.domain.model.AgentMessage

internal interface AgentHistoryRepository {
    fun load(): List<AgentMessage>
    fun save(all: List<AgentMessage>)
    fun append(message: AgentMessage): List<AgentMessage>
    fun clear()
}