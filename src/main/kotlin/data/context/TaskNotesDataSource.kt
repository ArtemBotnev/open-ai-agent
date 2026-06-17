package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskNotesContext

internal interface TaskNotesDataSource {
    fun load(): TaskNotesContext
    fun save(context: TaskNotesContext)
    fun loadMarkdown(): String
}
