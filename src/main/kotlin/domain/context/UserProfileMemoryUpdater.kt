package com.artembotnev.openai.domain.context

internal interface UserProfileMemoryUpdater {
    fun captureDialogue(userText: String, assistantText: String)
}
