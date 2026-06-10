package com.artembotnev.openai.data.dialogusage

import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.repository.DialogUsageRepository

internal class DialogUsageRepositoryJson(
    private val ds: DialogUsageDataSource
) : DialogUsageRepository {
    override fun load(): DialogUsage = ds.load()
    override fun save(usage: DialogUsage) = ds.save(usage)
    override fun clear() = ds.clear()
}
