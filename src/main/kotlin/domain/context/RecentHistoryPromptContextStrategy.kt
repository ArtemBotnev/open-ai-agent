package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogFacts

internal class RecentHistoryPromptContextStrategy(
    private val maxRecentMessages: Int
) : PromptContextStrategy {

    init {
        require(maxRecentMessages > 0) { "maxRecentMessages must be greater than 0" }
    }

    override fun build(facts: DialogFacts, history: List<AgentMessage>): PromptContext {
        val recentMessages = history
            .filter { it.role != AgentRole.SYSTEM }
            .takeLast(maxRecentMessages)

        return PromptContext(
            factsBlock = null,
            messages = recentMessages
        )
    }
}
