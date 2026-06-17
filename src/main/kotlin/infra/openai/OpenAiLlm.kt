package com.artembotnev.openai.infra.openai

import com.artembotnev.openai.domain.context.TaskContextMemoryUpdater
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.artembotnev.openai.infra.openai.workflow.OpenAiWorkflowCoordinator
import kotlin.time.measureTimedValue

internal class OpenAiLlm(
    private val append: AppendMessageUseCase,
    private val load: LoadHistoryUseCase,
    private val clear: ClearHistoryUseCase,
    private val workflowCoordinator: OpenAiWorkflowCoordinator,
    private val userProfileMemoryUpdater: UserProfileMemoryUpdater,
    private val taskContextMemoryUpdater: TaskContextMemoryUpdater,
) : Llm {

    override fun send(userText: String): AgentResult {
        val userMessage = AgentMessage(
            role = AgentRole.USER,
            content = userText
        )

        append(userMessage)

        val (workflowResponse, duration) = measureTimedValue {
            workflowCoordinator.handle(
                userText = userText,
                history = load(),
            )
        }

        val result = AgentResult(
            answer = workflowResponse.answer,
            promptTokens = workflowResponse.promptTokens,
            completionTokens = workflowResponse.completionTokens,
            totalTokens = workflowResponse.totalTokens,
            elapsedSec = duration.inWholeSeconds,
        )

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
        workflowCoordinator.clear()
    }

    override fun getHistory(): List<AgentMessage> {
        return load()
    }
}
