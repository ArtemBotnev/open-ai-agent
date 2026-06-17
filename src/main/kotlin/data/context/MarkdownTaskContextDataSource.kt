package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskContext
import okio.FileSystem
import okio.Path

internal class MarkdownTaskContextDataSource(
    private val fs: FileSystem,
    private val path: Path,
) : TaskContextDataSource {

    override fun load(): TaskContext {
        val text = loadMarkdown()
        val hasStateSection = text.contains(STATE_HEADING)
        val statusHeading = if (hasStateSection) STATUS_HEADING else LEGACY_STATUS_HEADING

        val taskSection = sectionBody(text, TASK_HEADING, CONTEXT_HEADING)
        val contextSection = sectionBody(
            text = text,
            startHeading = CONTEXT_HEADING,
            endHeading = if (hasStateSection) STATE_HEADING else statusHeading,
        )
        val statusSection = sectionBody(text, statusHeading, null)

        return TaskContext(
            currentTask = parseCurrentTask(taskSection),
            context = parseBulletSection(contextSection),
            decisionsAndStatus = parseBulletSection(statusSection),
        )
    }

    override fun save(context: TaskContext) {
        ensureParentExists()
        val text = buildString {
            appendLine(TITLE)
            appendLine()
            appendLine(TASK_HEADING)
            appendLine()
            appendLine(TASK_PRELUDE)
            appendLine()
            appendLine(context.currentTask.ifBlank { TASK_PLACEHOLDER })
            appendLine()
            appendLine(CONTEXT_HEADING)
            appendLine()
            appendLine(CONTEXT_PRELUDE)
            appendLine()
            context.context.forEach { item ->
                appendLine("- $item")
            }
            appendLine()
            appendLine(LEGACY_STATUS_HEADING)
            appendLine()
            appendLine(STATUS_PRELUDE)
            appendLine()
            context.decisionsAndStatus.forEach { item ->
                appendLine("- $item")
            }
        }.trimEnd() + "\n"

        fs.write(path) { writeUtf8(text) }
    }

    override fun loadMarkdown(): String {
        ensureFileExists()
        return fs.read(path) { readUtf8() }
    }

    private fun ensureFileExists() {
        if (fs.exists(path)) return
        save(defaultContext())
    }

    private fun ensureParentExists() {
        val parent = path.parent
        if (parent != null && !fs.exists(parent)) fs.createDirectories(parent)
    }

    private fun sectionBody(text: String, startHeading: String, endHeading: String?): String {
        val startIndex = text.indexOf(startHeading)
        require(startIndex >= 0) { "В markdown отсутствует секция $startHeading" }
        val contentStart = startIndex + startHeading.length
        val endIndex = endHeading?.let { heading ->
            val found = text.indexOf(heading, startIndex = contentStart)
            require(found >= 0) { "В markdown отсутствует секция $heading" }
            found
        } ?: text.length
        return text.substring(contentStart, endIndex).trim('\n')
    }

    private fun parseCurrentTask(section: String): String {
        return section.lines()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != TASK_PLACEHOLDER && it != TASK_PRELUDE }
            .orEmpty()
    }

    private fun parseBulletSection(section: String): List<String> {
        return section.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun defaultContext(): TaskContext {
        return TaskContext(
            currentTask = "",
            context = emptyList(),
            decisionsAndStatus = emptyList(),
        )
    }

    private companion object {
        const val TITLE = "# Контекст текущей задачи"
        const val TASK_HEADING = "## 1. Текущая задача"
        const val CONTEXT_HEADING = "## 2. Контекст"
        const val STATE_HEADING = "## 3. Состояние задачи"
        const val STATUS_HEADING = "## 4. Решения и статус"
        const val LEGACY_STATUS_HEADING = "## 3. Решения и статус"
        const val TASK_PRELUDE = "Этот раздел описывает только активную задачу пользователя в текущем диалоге."
        const val TASK_PLACEHOLDER = "Краткое описание активной задачи пользователя."
        const val CONTEXT_PRELUDE = "Краткая выжимка важного технического контекста по текущей задаче. Раздел может обновляться агентом только в рамках текущего задания."
        const val STATUS_PRELUDE = "Краткая выжимка принятых решений, текущего состояния и следующих шагов по задаче. Раздел может обновляться агентом только в рамках текущего задания."
    }
}
