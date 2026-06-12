package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StickyFactsPromptContextStrategyTest {

    @Test
    fun `build returns facts block and only recent non-system messages`() {
        val strategy = StickyFactsPromptContextStrategy(maxRecentMessages = 3)

        val context = strategy.build(
            facts = linkedMapOf(
                "goal" to "build context memory",
                "constraint" to "keep prompt small"
            ),
            history = listOf(
                AgentMessage(AgentRole.SYSTEM, "system"),
                AgentMessage(AgentRole.USER, "u1"),
                AgentMessage(AgentRole.ASSISTANT, "a1"),
                AgentMessage(AgentRole.USER, "u2"),
                AgentMessage(AgentRole.ASSISTANT, "a2")
            )
        )

        assertEquals(
            """facts:
constraint=keep prompt small
goal=build context memory""",
            context.factsBlock
        )
        assertEquals(listOf("a1", "u2", "a2"), context.messages.map { it.content })
    }

    @Test
    fun `build omits facts block when facts are empty`() {
        val strategy = StickyFactsPromptContextStrategy(maxRecentMessages = 2)

        val context = strategy.build(
            facts = emptyMap(),
            history = listOf(
                AgentMessage(AgentRole.USER, "u1"),
                AgentMessage(AgentRole.ASSISTANT, "a1")
            )
        )

        assertNull(context.factsBlock)
        assertEquals(listOf("u1", "a1"), context.messages.map { it.content })
    }
}
