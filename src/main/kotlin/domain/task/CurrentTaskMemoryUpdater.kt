package com.artembotnev.openai.domain.task

internal interface CurrentTaskMemoryUpdater {
    fun captureDialogue(userText: String, assistantText: String)
}
