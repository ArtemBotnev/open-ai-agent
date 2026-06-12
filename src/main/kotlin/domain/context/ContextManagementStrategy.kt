package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage

internal interface ContextManagementStrategy {
    fun apply(history: List<AgentMessage>): List<AgentMessage>
}
