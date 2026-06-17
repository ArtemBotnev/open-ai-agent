package com.artembotnev.openai.data.workflow

import com.artembotnev.openai.domain.workflow.TaskWorkflowState

internal interface TaskWorkflowStateDataSource {
    fun load(): TaskWorkflowState
    fun save(state: TaskWorkflowState)
    fun clear()
}
