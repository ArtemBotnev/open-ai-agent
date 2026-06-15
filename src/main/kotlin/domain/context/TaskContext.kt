package com.artembotnev.openai.domain.context

internal data class TaskContext(
    val currentTask: String,
    val context: List<String>,
    val decisionsAndStatus: List<String>,
)
