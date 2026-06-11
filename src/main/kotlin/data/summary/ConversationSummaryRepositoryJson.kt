package com.artembotnev.openai.data.summary

import com.artembotnev.openai.domain.model.ConversationSummary
import com.artembotnev.openai.domain.repository.ConversationSummaryRepository

internal class ConversationSummaryRepositoryJson(
    private val ds: ConversationSummaryDataSource
) : ConversationSummaryRepository {
    override fun load(): ConversationSummary = ds.load()
    override fun save(summary: ConversationSummary) = ds.save(summary)
    override fun clear() = ds.clear()
}
