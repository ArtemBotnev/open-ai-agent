package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage

internal class SlidingWindowContextManagementStrategy(
    private val maxMessages: Int
) : ContextManagementStrategy {

    init {
        require(maxMessages > 0) { "maxMessages must be greater than 0" }
    }

    override fun apply(history: List<AgentMessage>): List<AgentMessage> {
        if (history.size <= maxMessages) {
            return history
        }

        return history.takeLast(maxMessages)
    }
}
