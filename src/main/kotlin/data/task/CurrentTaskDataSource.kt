package com.artembotnev.openai.data.task

import com.artembotnev.openai.domain.task.TaskContext

internal interface CurrentTaskDataSource {
    fun load(): TaskContext?
    fun save(context: TaskContext)
    fun clear()
}
