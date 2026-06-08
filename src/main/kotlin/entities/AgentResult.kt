package com.artembotnev.openai.test.entities

internal data class AgentResult(
    val answer: String,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val totalTokens: Long?,
    val elapsedSec: Long
)