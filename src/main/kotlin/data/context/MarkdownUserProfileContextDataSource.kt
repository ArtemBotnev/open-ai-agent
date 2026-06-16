package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.UserProfileContext
import okio.FileSystem
import okio.Path

internal class MarkdownUserProfileContextDataSource(
    private val fs: FileSystem,
    private val path: Path,
    private val userId: String,
) : UserProfileContextDataSource {

    override fun load(): UserProfileContext {
        val text = loadMarkdown()

        val sectionOne = sectionBody(text, PROFILE_HEADING, KNOWLEDGE_HEADING)
        val sectionTwo = sectionBody(text, KNOWLEDGE_HEADING, CONSTRAINTS_HEADING)
        val sectionThree = sectionBody(text, CONSTRAINTS_HEADING, DECISIONS_HEADING)
        val sectionFour = sectionBody(text, DECISIONS_HEADING, null)

        return UserProfileContext(
            userId = parseUserId(sectionOne),
            role = parseRole(sectionOne),
            stack = parseStack(sectionOne),
            knowledge = parseBulletSection(sectionTwo),
            constraints = parseBulletSection(sectionThree),
            decisions = parseBulletSection(sectionFour),
        )
    }

    override fun save(context: UserProfileContext) {
        ensureParentExists()
        val text = buildString {
            appendLine(TITLE)
            appendLine()
            appendLine(PROFILE_HEADING)
            appendLine()
            appendLine(PROFILE_NOTE)
            appendLine()
            appendLine("- User ID: `${context.userId}`")
            appendLine("- Роль пользователя: ${context.role}")
            appendLine("- Основной стек:")
            context.stack.forEach { item ->
                appendLine("  - $item")
            }
            appendLine()
            appendLine(KNOWLEDGE_HEADING)
            appendLine()
            appendLine(KNOWLEDGE_PRELUDE)
            appendLine()
            context.knowledge.forEach { item ->
                appendLine("- $item")
            }
            appendLine()
            appendLine(CONSTRAINTS_HEADING)
            appendLine()
            appendLine(CONSTRAINTS_PRELUDE)
            appendLine()
            context.constraints.forEach { item ->
                appendLine("- $item")
            }
            appendLine()
            appendLine(DECISIONS_HEADING)
            appendLine()
            appendLine(DECISIONS_PRELUDE)
            appendLine()
            context.decisions.forEach { item ->
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

    private fun parseUserId(section: String): String {
        val raw = section.lines().firstOrNull { it.trim().startsWith("- User ID:") }
            ?.substringAfter(":")
            ?.replace("`", "")
            ?.trim()
        return raw.orEmpty()
    }

    private fun parseRole(section: String): String {
        return section.lines().firstOrNull { it.trim().startsWith("- Роль пользователя:") }
            ?.substringAfter(":")
            ?.trim()
            .orEmpty()
    }

    private fun parseStack(section: String): List<String> {
        val lines = section.lines()
        val stackHeaderIndex = lines.indexOfFirst { it.trim().startsWith("- Основной стек:") }
        if (stackHeaderIndex < 0) return emptyList()

        val headerLine = lines[stackHeaderIndex].trim()
        val inlineValue = headerLine.substringAfter(":", "").trim()
        if (inlineValue.isNotBlank()) {
            return inlineValue.split(',').map { it.trim() }.filter { it.isNotBlank() }
        }

        val result = mutableListOf<String>()
        for (index in stackHeaderIndex + 1 until lines.size) {
            val line = lines[index]
            val trimmed = line.trim()
            if (!line.startsWith("  - ") && !line.startsWith("\t- ")) break
            val item = trimmed.removePrefix("- ").trim()
            if (item.isNotBlank()) result += item
        }
        return result
    }

    private fun parseBulletSection(section: String): List<String> {
        return section.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { it.removePrefix("- ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun defaultContext(): UserProfileContext {
        return UserProfileContext(
            userId = userId,
            role = "Android-разработчик, senior",
            stack = listOf("Kotlin", "Jetpack Compose"),
            knowledge = listOf(
                "Пользователь работает с Android-разработкой на Kotlin",
                "В UI использует Jetpack Compose",
            ),
            constraints = emptyList(),
            decisions = emptyList(),
        )
    }

    private companion object {
        const val TITLE = "# Профиль пользователя"
        const val PROFILE_HEADING = "## 1. Текущий профиль пользователя"
        const val KNOWLEDGE_HEADING = "## 2. Знания пользователя"
        const val CONSTRAINTS_HEADING = "## 3. Ограничения пользователя"
        const val DECISIONS_HEADING = "## 4. Решения пользователя и агента"
        const val PROFILE_NOTE = "> Этот раздел является основным и не редактируется агентами."
        const val KNOWLEDGE_PRELUDE = "Краткая выжимка о знаниях, стиле работы и поведении пользователя по диалогам."
        const val CONSTRAINTS_PRELUDE = "Краткая выжимка ограничений, запретов и предпочтительных рамок, которые агент должен учитывать."
        const val DECISIONS_PRELUDE = "Краткая выжимка принятых решений, выбранных подходов и договорённостей."
    }
}
