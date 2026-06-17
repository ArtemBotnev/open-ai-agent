package com.artembotnev.openai

import com.artembotnev.openai.data.workflow.JsonTaskWorkflowStateDataSource
import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.domain.workflow.WorkflowActor
import com.artembotnev.openai.domain.workflow.WorkflowState
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonTaskWorkflowStateDataSourceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `load normalizes legacy current_task json`() {
        val dir = Files.createTempDirectory("workflow-state-test")
        try {
            val dataSource = JsonTaskWorkflowStateDataSource(
                baseDir = dir.toString().toPath(),
                fileName = "current_task.json",
                fs = FileSystem.SYSTEM,
                json = json,
            )
            FileSystem.SYSTEM.write(dir.resolve("current_task.json").toString().toPath()) {
                writeUtf8(
                    """
                    {"task":"demo","state":"PLANNING","step":1,"total":3,"plan":["a","b","c"],"done":["a"],"current":"b"}
                    """.trimIndent()
                )
            }

            val loaded = dataSource.load()

            assertEquals(WorkflowState.PLANNING, loaded.state)
            assertEquals(1, loaded.step)
            assertEquals(4, loaded.total)
            assertEquals(2, loaded.planStep)
            assertEquals(3, loaded.planTotal)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `save and clear persist current task state`() {
        val dir = Files.createTempDirectory("workflow-state-save-test")
        try {
            val dataSource = JsonTaskWorkflowStateDataSource(
                baseDir = dir.toString().toPath(),
                fileName = "current_task.json",
                fs = FileSystem.SYSTEM,
                json = json,
            )
            val state = TaskWorkflowState(
                task = "Implement workflow",
                state = WorkflowState.EXECUTION,
                plan = listOf("planning", "execution"),
                done = listOf("planning"),
                current = "execution",
            ).withRuntimeState(
                revisionRequest = "Добавить retry",
                attempts = 1,
            ).recordStageResult(
                actor = WorkflowActor.EXECUTION_AGENT,
                stage = WorkflowState.EXECUTION,
                summary = "Черновик готов",
                details = listOf("Сделан основной код"),
                artifactPreview = "class Demo",
            ).addAgentHandoff(
                fromActor = WorkflowActor.EXECUTION_AGENT,
                toActor = WorkflowActor.VALIDATION_AGENT,
                fromState = WorkflowState.EXECUTION,
                toState = WorkflowState.VALIDATION,
                message = "Передаю код на проверку",
            )

            dataSource.save(state)
            val loaded = dataSource.load()

            assertEquals("Implement workflow", loaded.task)
            assertEquals(WorkflowState.EXECUTION, loaded.state)
            assertEquals(2, loaded.step)
            assertEquals("Добавить retry", loaded.pendingRevisionRequest)
            assertEquals(1, loaded.validationAttempts)
            assertEquals("Черновик готов", loaded.currentStageResult?.summary)
            assertEquals(1, loaded.agentHandoffs.size)
            assertEquals(WorkflowActor.VALIDATION_AGENT, loaded.agentHandoffs.first().toActor)
            assertTrue(loaded.currentStageResult?.artifactPreview == "class Demo")

            dataSource.clear()

            val exists = FileSystem.SYSTEM.exists(dir.resolve("current_task.json").toString().toPath())
            assertFalse(exists)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
