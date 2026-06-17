package com.artembotnev.openai.domain.context

internal interface TaskNotesMemoryUpdater {
    fun captureDialogue(userText: String, assistantText: String)
}
