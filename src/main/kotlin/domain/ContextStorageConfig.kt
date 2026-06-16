package com.artembotnev.openai.domain

import okio.Path

internal data class ContextStorageConfig(
    val baseDir: Path,
    val userId: String,
) {
    val userDir: Path
        get() = baseDir / "users" / userId

    val userProfilePath: Path
        get() = userDir / "profile.md"

    val taskContextPath: Path
        get() = userDir / "task_context.md"
}
