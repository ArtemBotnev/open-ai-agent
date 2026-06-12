package com.artembotnev.openai.domain.context

internal enum class ContextStrategyType(
    val cliValue: String,
    val displayName: String,
    val description: String,
    val usesFactsMemory: Boolean,
    val recentMessagesCount: Int,
) {
    SLIDING_WINDOW(
        cliValue = "sliding-window",
        displayName = "Sliding Window",
        description = "только последние 10 сообщений",
        usesFactsMemory = false,
        recentMessagesCount = 10,
    ),
    STICKY_FACTS(
        cliValue = "sticky-facts",
        displayName = "Sticky Facts / Key-Value Memory",
        description = "facts + последние 6 сообщений",
        usesFactsMemory = true,
        recentMessagesCount = 6,
    );

    companion object {
        fun fromInput(input: String): ContextStrategyType? {
            return when (input.trim().lowercase()) {
                "1", "sliding-window", "sliding_window", "slidingwindow", "sliding" -> SLIDING_WINDOW
                "2", "sticky-facts", "sticky_facts", "stickyfacts", "sticky" -> STICKY_FACTS
                else -> null
            }
        }
    }
}
