package com.artembotnev.openai.data.context

import com.artembotnev.openai.domain.context.TaskStage
import com.artembotnev.openai.domain.context.TaskState
import kotlinx.serialization.json.Json
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import okio.FileSystem
import okio.Path.Companion.toPath

@OptIn(ExperimentalPathApi::class)
internal class JsonTaskStateDataSourceTest {

    @Test
    fun `returns null when file does not exist`() {
        val dir = createTempDirectory("task-state-empty")
        try {
            val dataSource = JsonTaskStateDataSource(
                fs = FileSystem.SYSTEM,
                path = dir.resolve("task_state.json").toString().toPath(),
                json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            )

            assertNull(dataSource.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `saves and loads task state`() {
        val dir = createTempDirectory("task-state-save")
        try {
            val dataSource = JsonTaskStateDataSource(
                fs = FileSystem.SYSTEM,
                path = dir.resolve("task_state.json").toString().toPath(),
                json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            )
            val state = TaskState(
                stage = TaskStage.EXECUTION,
                currentStep = "Обновить отдельное хранилище state",
                expectedAction = "Перейти к validation",
            )

            dataSource.save(state)

            assertEquals(state, dataSource.load())
        } finally {
            dir.deleteRecursively()
        }
    }
}
