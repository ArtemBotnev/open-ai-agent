package com.artembotnev.openai

import com.artembotnev.openai.data.context.MarkdownInvariantDataSource
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownInvariantDataSourceTest {

    @Test
    fun `load parses invariant rules from markdown list items`() {
        val dir = Files.createTempDirectory("invariant-test")
        try {
            val path = dir.resolve("invariant.md").toString().toPath()
            FileSystem.SYSTEM.write(path) {
                writeUtf8(
                    """
                    # Правила

                    - Clean Architecture
                    * Используем Koin
                    1. Не использовать RxJava
                    2. Нельзя удалять пользовательские данные без подтверждения
                    """.trimIndent()
                )
            }

            val dataSource = MarkdownInvariantDataSource(
                fs = FileSystem.SYSTEM,
                path = path,
            )

            val invariant = dataSource.load()

            assertEquals(
                listOf(
                    "Clean Architecture",
                    "Используем Koin",
                    "Не использовать RxJava",
                    "Нельзя удалять пользовательские данные без подтверждения",
                ),
                invariant.rules,
            )
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `loadMarkdown returns empty string when file is missing`() {
        val dir = Files.createTempDirectory("invariant-missing-test")
        try {
            val path = dir.resolve("invariant.md").toString().toPath()
            val dataSource = MarkdownInvariantDataSource(
                fs = FileSystem.SYSTEM,
                path = path,
            )

            assertEquals("", dataSource.loadMarkdown())
            assertTrue(dataSource.load().isEmpty)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
