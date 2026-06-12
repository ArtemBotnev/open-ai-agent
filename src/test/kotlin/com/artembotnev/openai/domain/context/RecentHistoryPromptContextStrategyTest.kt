package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecentHistoryPromptContextStrategyTest {

    @Test
    fun `build keeps only recent non-system messages and omits facts`() {
        val strategy = RecentHistoryPromptContextStrategy(maxRecentMessages = 3)

        val context = strategy.build(
            facts = mapOf("goal" to "ignored"),
            history = listOf(
                AgentMessage(AgentRole.SYSTEM, "system"),
                AgentMessage(AgentRole.USER, "u1"),
                AgentMessage(AgentRole.ASSISTANT, "a1"),
                AgentMessage(AgentRole.USER, "u2"),
                AgentMessage(AgentRole.ASSISTANT, "a2")
            )
        )

        assertNull(context.factsBlock)
        assertEquals(listOf("a1", "u2", "a2"), context.messages.map { it.content })
    }
}
