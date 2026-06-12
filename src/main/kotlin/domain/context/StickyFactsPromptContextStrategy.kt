package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogFacts

internal class StickyFactsPromptContextStrategy(
    private val maxRecentMessages: Int
) : PromptContextStrategy {

    init {
        require(maxRecentMessages > 0) { "maxRecentMessages must be greater than 0" }
    }

    override fun build(facts: DialogFacts, history: List<AgentMessage>): PromptContext {
        val recentMessages = history
            .filter { it.role != AgentRole.SYSTEM }
            .takeLast(maxRecentMessages)

        val factsBlock = facts.entries
            .sortedBy { it.key }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(
                prefix = "facts:\n",
                separator = "\n"
            ) { (key, value) -> "$key=$value" }

        return PromptContext(
            factsBlock = factsBlock,
            messages = recentMessages
        )
    }
}
