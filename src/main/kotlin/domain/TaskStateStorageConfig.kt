package com.artembotnev.openai.domain

import okio.Path

internal data class TaskStateStorageConfig(
    val baseDir: Path,
    val userId: String,
    val fileName: String = "current_task.json",
) {
    val userDir: Path
        get() = baseDir / "users" / userId
}
