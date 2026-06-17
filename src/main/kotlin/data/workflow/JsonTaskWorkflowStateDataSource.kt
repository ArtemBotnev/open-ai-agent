package com.artembotnev.openai.data.workflow

import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonTaskWorkflowStateDataSource(
    private val baseDir: Path,
    private val fileName: String,
    private val fs: FileSystem,
    private val json: Json,
) : TaskWorkflowStateDataSource {

    private val filePath: Path
        get() = baseDir / fileName

    override fun load(): TaskWorkflowState = try {
        if (!fs.exists(filePath)) return TaskWorkflowState()
        fs.source(filePath).buffer().use { source ->
            val text = source.readUtf8()
            if (text.isBlank()) return TaskWorkflowState()
            json.decodeFromString(TaskWorkflowState.serializer(), text).normalized()
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        TaskWorkflowState()
    }

    override fun save(state: TaskWorkflowState) {
        if (!fs.exists(baseDir)) fs.createDirectories(baseDir)
        val normalized = state.normalized()
        val text = json.encodeToString(TaskWorkflowState.serializer(), normalized)
        fs.write(filePath) { writeUtf8(text) }
    }

    override fun clear() {
        if (fs.exists(filePath)) fs.delete(filePath)
    }
}
