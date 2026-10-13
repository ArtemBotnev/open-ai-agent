package com.artembotnev.openai.domain

data class HistoryStorageConfig(val baseDir: okio.Path, val fileName: String = "history.json")