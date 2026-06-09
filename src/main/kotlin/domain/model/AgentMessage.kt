package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class AgentMessage(
    val role: AgentRole,
    val content: String
)