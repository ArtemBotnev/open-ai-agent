package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.Invariant
import okio.FileSystem
import okio.Path

internal class MarkdownInvariantDataSource(
    private val fs: FileSystem,
    private val path: Path,
) : InvariantDataSource {

    override fun load(): Invariant {
        return Invariant(
            rules = parseRules(loadMarkdown()),
        )
    }

    override fun loadMarkdown(): String {
        if (!fs.exists(path)) return ""
        return fs.read(path) { readUtf8() }
    }

    private fun parseRules(markdown: String): List<String> {
        return markdown.lines()
            .map { it.trim() }
            .mapNotNull(::extractListItem)
            .filter { it.isNotBlank() }
    }

    private fun extractListItem(line: String): String? {
        return when {
            line.startsWith("- ") -> line.removePrefix("- ").trim()
            line.startsWith("* ") -> line.removePrefix("* ").trim()
            ORDERED_LIST_REGEX.matches(line) -> line.substringAfter(".").trim()
            else -> null
        }
    }

    private companion object {
        val ORDERED_LIST_REGEX = Regex("""\d+\.\s+.+""")
    }
}
