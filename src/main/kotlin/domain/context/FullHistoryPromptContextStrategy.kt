package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogFacts

internal object FullHistoryPromptContextStrategy : PromptContextStrategy {
    override fun build(facts: DialogFacts, history: List<AgentMessage>): PromptContext {
        val messages = history.filter { it.role != AgentRole.SYSTEM }
        return PromptContext(
            factsBlock = null,
            messages = messages
        )
    }
}
