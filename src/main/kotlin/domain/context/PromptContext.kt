package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage

internal data class PromptContext(
    val factsBlock: String?,
    val messages: List<AgentMessage>
)
