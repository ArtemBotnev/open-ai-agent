package com.artembotnev.openai.data.context

import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import okio.FileSystem
import okio.Path.Companion.toPath

@OptIn(ExperimentalPathApi::class)
internal class MarkdownTaskNotesDataSourceTest {

    @Test
    fun `loads legacy markdown without explicit task state`() {
        val dir = createTempDirectory("task-context-legacy")
        try {
            val file = dir.resolve("task_context.md")
            file.toFile().writeText(
                """
                # Контекст текущей задачи

                ## 1. Текущая задача

                Этот раздел описывает только активную задачу пользователя в текущем диалоге.

                Подготовить рефакторинг state machine

                ## 2. Контекст

                Краткая выжимка важного технического контекста по текущей задаче. Раздел может обновляться агентом только в рамках текущего задания.

                - Есть текущий task context

                ## 3. Решения и статус

                Краткая выжимка принятых решений, текущего состояния и следующих шагов по задаче. Раздел может обновляться агентом только в рамках текущего задания.

                - Реализовать переходы и затем проверить итоговый результат
                """.trimIndent()
            )

            val dataSource = MarkdownTaskNotesDataSource(
                fs = FileSystem.SYSTEM,
                path = file.toString().toPath(),
            )

            val loaded = dataSource.load()

            assertEquals("Подготовить рефакторинг state machine", loaded.currentTask)
            assertEquals(listOf("Есть текущий task context"), loaded.context)
            assertEquals(listOf("Реализовать переходы и затем проверить итоговый результат"), loaded.decisionsAndStatus)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `saves original task context format without state section`() {
        val dir = createTempDirectory("task-context-save")
        try {
            val file = dir.resolve("task_context.md")
            val dataSource = MarkdownTaskNotesDataSource(
                fs = FileSystem.SYSTEM,
                path = file.toString().toPath(),
            )

            val original = com.artembotnev.openai.domain.context.TaskNotesContext(
                currentTask = "Внедрить конечный автомат",
                context = listOf("Есть legacy формат task_context.md"),
                decisionsAndStatus = listOf("Парсер должен остаться обратно совместимым"),
            )

            dataSource.save(original)
            val markdown = dataSource.loadMarkdown()
            val loaded = dataSource.load()

            assertEquals(false, markdown.contains("## 4. Состояние задачи"))
            assertEquals(original, loaded)
        } finally {
            dir.deleteRecursively()
        }
    }
}
