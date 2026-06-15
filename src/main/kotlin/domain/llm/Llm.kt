package com.artembotnev.openai.domain.llm

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult

internal interface Llm {
    fun send(userText: String): AgentResult
    fun clearHistory()
    fun getHistory(): List<AgentMessage>
}
