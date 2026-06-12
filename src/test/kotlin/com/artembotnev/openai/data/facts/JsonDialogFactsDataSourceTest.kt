package com.artembotnev.openai.data.facts

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonDialogFactsDataSourceTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val fs = FileSystem.SYSTEM
    private val baseDir = "/tmp/openai-first-facts-test".toPath()

    @Test
    fun `load migrates legacy fact list into key value facts`() {
        val dataSource = JsonDialogFactsDataSource(
            baseDir = baseDir,
            fs = fs,
            json = json
        )
        fs.createDirectories(baseDir)
        fs.write(baseDir / "facts.json") {
            writeUtf8(
                """["preferences: use EditText","goal=build chat"]"""
            )
        }

        val facts = dataSource.load()

        assertEquals(
            linkedMapOf(
                "preferences" to "use EditText",
                "goal" to "build chat"
            ),
            facts
        )

        if (fs.exists(baseDir / "facts.json")) {
            fs.delete(baseDir / "facts.json")
        }
        if (fs.exists(baseDir)) {
            fs.delete(baseDir)
        }
    }
}
