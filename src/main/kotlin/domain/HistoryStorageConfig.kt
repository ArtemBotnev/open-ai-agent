package com.artembotnev.openai.domain

import okio.Path

internal data class HistoryStorageConfig(
    val baseDir: Path,
    val userId: String,
    val fileName: String = "history.json",
) {
    val userDir: Path
        get() = baseDir / "users" / userId
}
