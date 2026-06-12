package com.artembotnev.openai.domain.repository

import com.artembotnev.openai.domain.model.DialogFacts

internal interface DialogFactsRepository {
    fun load(): DialogFacts
    fun save(facts: DialogFacts)
    fun clear()
}
