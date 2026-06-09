package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class AgentResult(
    val answer: String,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val totalTokens: Long?,
    val elapsedSec: Long
)