package com.artembotnev.openai.test.entities

internal data class AgentMessage(
    val role: AgentRole,
    val content: String
)