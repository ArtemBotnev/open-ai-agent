package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal enum class AgentRole {
    SYSTEM,
    USER,
    ASSISTANT
}