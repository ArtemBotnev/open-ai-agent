package com.artembotnev.openai.data.facts

import com.artembotnev.openai.domain.model.DialogFacts

internal interface DialogFactsDataSource {
    fun load(): DialogFacts
    fun save(facts: DialogFacts)
    fun clear()
}
