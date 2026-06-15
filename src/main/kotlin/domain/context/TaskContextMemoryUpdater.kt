package com.artembotnev.openai.domain.context

internal interface TaskContextMemoryUpdater {
    fun captureDialogue(userText: String, assistantText: String)
}
