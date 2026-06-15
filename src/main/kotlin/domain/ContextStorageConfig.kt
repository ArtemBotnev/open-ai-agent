package com.artembotnev.openai.domain

import okio.Path

internal data class ContextStorageConfig(
    val userProfilePath: Path,
    val taskContextPath: Path,
)
