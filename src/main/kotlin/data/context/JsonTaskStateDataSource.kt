package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskState
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonTaskStateDataSource(
    private val fs: FileSystem,
    private val path: Path,
    private val json: Json,
) : TaskStateDataSource {

    override fun load(): TaskState? = try {
        if (!fs.exists(path)) return null
        fs.source(path).buffer().use { source ->
            val text = source.readUtf8().trim()
            if (text.isBlank()) return null
            json.decodeFromString<TaskState>(text)
        }
    } catch (_: Throwable) {
        null
    }

    override fun save(state: TaskState) {
        ensureParentExists()
        fs.write(path) {
            writeUtf8(json.encodeToString(TaskState.serializer(), state))
        }
    }

    private fun ensureParentExists() {
        val parent = path.parent
        if (parent != null && !fs.exists(parent)) fs.createDirectories(parent)
    }
}
