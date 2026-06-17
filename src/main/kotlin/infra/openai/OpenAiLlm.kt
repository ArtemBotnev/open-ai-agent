package com.artembotnev.openai.infra.openai

import com.artembotnev.openai.data.context.TaskContextDataSource
import com.artembotnev.openai.data.context.TaskStateDataSource
import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.domain.context.TaskContextMemoryUpdater
import com.artembotnev.openai.domain.context.TaskState
import com.artembotnev.openai.domain.context.TaskStateMachine
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.time.measureTimedValue

internal class OpenAiLlm(
    private val model: String,
    private val client: OpenAIClient,
    private val append: AppendMessageUseCase,
    private val load: LoadHistoryUseCase,
    private val clear: ClearHistoryUseCase,
    private val userProfileDataSource: UserProfileContextDataSource,
    private val taskContextDataSource: TaskContextDataSource,
    private val taskStateDataSource: TaskStateDataSource,
    private val userProfileMemoryUpdater: UserProfileMemoryUpdater,
    private val taskContextMemoryUpdater: TaskContextMemoryUpdater,
) : Llm {

    override fun send(userText: String): AgentResult {
        val userMessage = AgentMessage(
            role = AgentRole.USER,
            content = userText
        )

        append(userMessage)

        val result = requestOpenAi()

        append(
            AgentMessage(
                role = AgentRole.ASSISTANT,
                content = result.answer
            )
        )

        runCatching {
            userProfileMemoryUpdater.captureDialogue(
                userText = userText,
                assistantText = result.answer,
            )
        }
        runCatching {
            taskContextMemoryUpdater.captureDialogue(
                userText = userText,
                assistantText = result.answer,
            )
        }

        return result
    }

    override fun clearHistory() {
        clear()
    }

    override fun getHistory(): List<AgentMessage> {
        return load()
    }

    private fun requestOpenAi(): AgentResult {
        val params = buildParams()

        val (completion, duration) = measureTimedValue {
            client.chat()
                .completions()
                .create(params)
        }

        val answer = completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            ?: error("Пустой ответ от модели")

        val usage = completion.usage().orElse(null)

        return AgentResult(
            answer = answer,
            promptTokens = usage?.promptTokens(),
            completionTokens = usage?.completionTokens(),
            totalTokens = usage?.totalTokens(),
            elapsedSec = duration.inWholeSeconds
        )
    }

    private fun buildParams(): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(model)

        builder.addSystemMessage(userProfileDataSource.loadMarkdown().trim())

        val taskContext = taskContextDataSource.load()
        val taskContextMarkdown = taskContextDataSource.loadMarkdown().trim()
        val taskState = taskStateDataSource.load()
            ?: TaskStateMachine.deriveLegacyState(
                currentTask = taskContext.currentTask,
                decisionsAndStatus = taskContext.decisionsAndStatus,
            )
        val history = getHistory().filter { it.role != AgentRole.SYSTEM }
        for (message in history) {
            when (message.role) {
                AgentRole.USER -> builder.addUserMessage(
                    userMessageWithTaskContext(
                        userText = message.content,
                        taskContextMarkdown = taskContextMarkdown,
                        taskState = taskState,
                    )
                )
                AgentRole.ASSISTANT -> builder.addAssistantMessage(message.content)
                AgentRole.SYSTEM -> Unit
            }
        }

        return builder.build()
    }

    private fun userMessageWithTaskContext(
        userText: String,
        taskContextMarkdown: String,
        taskState: TaskState,
    ): String {
        if (taskContextMarkdown.isBlank()) return userText

        return buildString {
            appendLine(taskContextMarkdown)
            appendLine()
            appendLine("Состояние задачи:")
            appendLine("stage: ${taskState.stage.value}")
            appendLine("currentStep: ${taskState.currentStep}")
            appendLine("expectedAction: ${taskState.expectedAction}")
            appendLine()
            appendLine("Сообщение пользователя:")
            append(userText)
        }
    }
}
