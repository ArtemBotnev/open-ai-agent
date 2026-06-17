package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskState

internal interface TaskStateDataSource {
    fun load(): TaskState?
    fun save(state: TaskState)
}
