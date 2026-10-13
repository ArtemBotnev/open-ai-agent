package com.artembotnev.openai

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.SaveHistoryUseCase
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.measureTimedValue

internal class Agent(
    private val model: String,
    private val systemPrompt: String,
    private val client: OpenAIClient = OpenAIOkHttpClient.fromEnv(),
) : KoinComponent {

    val append: AppendMessageUseCase by inject()
    val save: SaveHistoryUseCase by inject()
    val load: LoadHistoryUseCase by inject()
    val clear: ClearHistoryUseCase by inject()

    fun send(userText: String): AgentResult {
        val userMessage = AgentMessage(
            role = AgentRole.USER,
            content = userText
        )

        append(userMessage)

        try {
            val result = requestOpenAi()

            append(
                AgentMessage(
                    role = AgentRole.ASSISTANT,
                    content = result.answer
                )
            )

            return result
        } catch (e: Exception) {
            throw e
        }
    }

    fun clearHistory() {
        clear()
        append(
            AgentMessage(
                role = AgentRole.SYSTEM,
                content = systemPrompt
            )
        )
    }

    fun getHistory(): List<AgentMessage> {
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

        val history = getHistory()
        for (message in history) {
            when (message.role) {
                AgentRole.SYSTEM -> {
                    builder.addSystemMessage(message.content)
                }

                AgentRole.USER -> {
                    builder.addUserMessage(message.content)
                }

                AgentRole.ASSISTANT -> {
                    builder.addAssistantMessage(message.content)
                }
            }
        }

        return builder.build()
    }
}