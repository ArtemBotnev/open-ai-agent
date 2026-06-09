package com.artembotnev.openai.domain.data

import com.artembotnev.openai.domain.model.AgentMessage


internal interface AgentHistoryDataSource {
    fun load(): List<AgentMessage>
    fun save(all: List<AgentMessage>)
    fun append(message: AgentMessage): List<AgentMessage>
    fun clear()
}