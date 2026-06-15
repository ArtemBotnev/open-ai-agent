package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskContext

internal interface TaskContextDataSource {
    fun load(): TaskContext
    fun save(context: TaskContext)
    fun loadMarkdown(): String
}
