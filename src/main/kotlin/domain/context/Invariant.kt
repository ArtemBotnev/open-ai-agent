package com.artembotnev.openai.domain.context

internal data class Invariant(
    val rules: List<String>,
) {
    val isEmpty: Boolean
        get() = rules.isEmpty()
}
