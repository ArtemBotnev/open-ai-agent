package com.artembotnev.openai.data.facts

import com.artembotnev.openai.domain.model.DialogFacts
import com.artembotnev.openai.domain.repository.DialogFactsRepository

internal class DialogFactsRepositoryJson(
    private val ds: DialogFactsDataSource
) : DialogFactsRepository {
    override fun load(): DialogFacts = ds.load()
    override fun save(facts: DialogFacts) = ds.save(facts)
    override fun clear() = ds.clear()
}
