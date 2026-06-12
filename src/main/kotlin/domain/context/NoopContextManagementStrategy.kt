package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage

internal object NoopContextManagementStrategy : ContextManagementStrategy {
    override fun apply(history: List<AgentMessage>): List<AgentMessage> = history
}
