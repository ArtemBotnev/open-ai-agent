package com.artembotnev.openai.data.task

import com.artembotnev.openai.domain.task.AgentState
import com.artembotnev.openai.domain.task.TaskContext
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
internal class JsonCurrentTaskDataSourceTest {

    @Test
    fun `returns null when file does not exist`() {
        val dir = createTempDirectory("current-task-empty")
        try {
            val dataSource = JsonCurrentTaskDataSource(
                fs = FileSystem.SYSTEM,
                path = dir.resolve("current_task.json").toString().toPath(),
                json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            )

            assertNull(dataSource.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `saves loads and clears current task`() {
        val dir = createTempDirectory("current-task-save")
        try {
            val dataSource = JsonCurrentTaskDataSource(
                fs = FileSystem.SYSTEM,
                path = dir.resolve("current_task.json").toString().toPath(),
                json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            )
            val context = TaskContext(
                task = "Обновить SaveHistoryUseCase",
                state = AgentState.EXECUTION,
                step = 1,
                total = 3,
                plan = listOf("Проверить use case", "Изменить контракт", "Проверить сохранение"),
                done = listOf("Проверить use case"),
                current = "Изменить контракт",
            )

            dataSource.save(context)
            assertEquals(context, dataSource.load())

            dataSource.clear()
            assertNull(dataSource.load())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `normalizes loaded task progress from persisted json`() {
        val dir = createTempDirectory("current-task-normalize")
        try {
            val path = dir.resolve("current_task.json").toString().toPath()
            val dataSource = JsonCurrentTaskDataSource(
                fs = FileSystem.SYSTEM,
                path = path,
                json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
            )

            FileSystem.SYSTEM.write(path) {
                writeUtf8(
                    """
                    {
                      "task":"Создание медиаплеера для музыки",
                      "state":"PLANNING",
                      "step":0,
                      "total":5,
                      "plan":["Выбор инструментов и библиотек","Создание интерфейса пользователя","Функциональность воспроизведения","Обработка плейлистов","Дополнительные функции"],
                      "done":[],
                      "current":""
                    }
                    """.trimIndent()
                )
            }

            val loaded = dataSource.load()

            assertEquals(1, loaded?.step)
            assertEquals("Выбор инструментов и библиотек", loaded?.current)
        } finally {
            dir.deleteRecursively()
        }
    }
}
