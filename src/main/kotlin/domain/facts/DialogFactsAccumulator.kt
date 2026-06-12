package com.artembotnev.openai.domain.facts

import com.artembotnev.openai.domain.model.DialogFacts

internal object DialogFactsAccumulator {

    private const val FACT_VALUE_SEPARATOR = "; "

    fun merge(currentFacts: DialogFacts, newFacts: DialogFacts): DialogFacts {
        if (newFacts.isEmpty()) {
            return currentFacts
        }

        val mergedFacts = linkedMapOf<String, String>()
        mergedFacts.putAll(currentFacts)

        for ((key, newValue) in newFacts) {
            val currentValue = mergedFacts[key]
            mergedFacts[key] = mergeValue(currentValue, newValue)
        }

        return mergedFacts
    }

    private fun mergeValue(currentValue: String?, newValue: String): String {
        if (currentValue.isNullOrBlank()) {
            return newValue
        }

        if (currentValue == newValue) {
            return currentValue
        }

        val currentParts = currentValue.split(FACT_VALUE_SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (newValue in currentParts) {
            return currentValue
        }

        return currentValue + FACT_VALUE_SEPARATOR + newValue
    }
}
