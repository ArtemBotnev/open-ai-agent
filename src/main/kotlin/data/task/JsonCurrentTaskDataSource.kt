package com.artembotnev.openai.data.task

import com.artembotnev.openai.domain.task.TaskContext
import com.artembotnev.openai.domain.task.TaskStateMachine
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonCurrentTaskDataSource(
    private val fs: FileSystem,
    private val path: Path,
    private val json: Json,
) : CurrentTaskDataSource {

    override fun load(): TaskContext? = try {
        if (!fs.exists(path)) return null
        fs.source(path).buffer().use { source ->
            val text = source.readUtf8().trim()
            if (text.isBlank()) return null
            json.decodeFromString<TaskContext>(text).normalize()
        }
    } catch (_: Throwable) {
        null
    }

    override fun save(context: TaskContext) {
        val normalized = context.normalize()
        ensureParentExists()
        fs.write(path) {
            writeUtf8(json.encodeToString(TaskContext.serializer(), normalized))
        }
    }

    override fun clear() {
        if (fs.exists(path)) fs.delete(path)
    }

    private fun ensureParentExists() {
        val parent = path.parent
        if (parent != null && !fs.exists(parent)) fs.createDirectories(parent)
    }

    private fun TaskContext.normalize(): TaskContext {
        return TaskStateMachine.restore(
            task = task,
            state = state,
            step = step,
            total = total,
            plan = plan,
            done = done,
            current = current,
        )
    }
}
