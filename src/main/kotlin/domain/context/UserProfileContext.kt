package com.artembotnev.openai.domain.context

internal data class UserProfileContext(
    val userId: String,
    val role: String,
    val stack: List<String>,
    val knowledge: List<String>,
    val decisions: List<String>,
)
