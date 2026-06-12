package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.DialogFacts

internal interface PromptContextStrategy {
    fun build(facts: DialogFacts, history: List<AgentMessage>): PromptContext
}
