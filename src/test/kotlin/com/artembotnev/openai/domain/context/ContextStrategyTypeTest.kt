package com.artembotnev.openai.domain.context

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextStrategyTypeTest {

    @Test
    fun `from input parses aliases for sliding window`() {
        assertEquals(ContextStrategyType.SLIDING_WINDOW, ContextStrategyType.fromInput("1"))
        assertEquals(ContextStrategyType.SLIDING_WINDOW, ContextStrategyType.fromInput("sliding-window"))
        assertEquals(ContextStrategyType.SLIDING_WINDOW, ContextStrategyType.fromInput("Sliding"))
    }

    @Test
    fun `from input parses aliases for sticky facts`() {
        assertEquals(ContextStrategyType.STICKY_FACTS, ContextStrategyType.fromInput("2"))
        assertEquals(ContextStrategyType.STICKY_FACTS, ContextStrategyType.fromInput("sticky-facts"))
        assertEquals(ContextStrategyType.STICKY_FACTS, ContextStrategyType.fromInput("sticky"))
    }

    @Test
    fun `uses facts memory is enabled only for sticky facts`() {
        assertFalse(ContextStrategyType.SLIDING_WINDOW.usesFactsMemory)
        assertTrue(ContextStrategyType.STICKY_FACTS.usesFactsMemory)
    }

    @Test
    fun `recent message count is stored in strategy type`() {
        assertEquals(10, ContextStrategyType.SLIDING_WINDOW.recentMessagesCount)
        assertEquals(6, ContextStrategyType.STICKY_FACTS.recentMessagesCount)
    }
}
