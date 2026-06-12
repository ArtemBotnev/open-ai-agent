package com.artembotnev.openai.domain.facts

import kotlin.test.Test
import kotlin.test.assertEquals

class DialogFactsAccumulatorTest {

    @Test
    fun `merge appends value when key already exists`() {
        val merged = DialogFactsAccumulator.merge(
            currentFacts = linkedMapOf(
                "decisions" to "use weather api"
            ),
            newFacts = linkedMapOf(
                "decisions" to "cache responses"
            )
        )

        assertEquals(
            linkedMapOf(
                "decisions" to "use weather api; cache responses"
            ),
            merged
        )
    }

    @Test
    fun `merge keeps other keys and avoids duplicate appended value`() {
        val merged = DialogFactsAccumulator.merge(
            currentFacts = linkedMapOf(
                "decisions" to "use weather api; cache responses",
                "preferences" to "edittext"
            ),
            newFacts = linkedMapOf(
                "decisions" to "cache responses",
                "constraints" to "android only"
            )
        )

        assertEquals(
            linkedMapOf(
                "decisions" to "use weather api; cache responses",
                "preferences" to "edittext",
                "constraints" to "android only"
            ),
            merged
        )
    }
}
