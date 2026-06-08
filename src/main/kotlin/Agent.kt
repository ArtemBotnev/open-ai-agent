package com.artembotnev.openai.test

import com.artembotnev.openai.test.entities.AgentMessage
import com.artembotnev.openai.test.entities.AgentResult
import com.artembotnev.openai.test.entities.AgentRole
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.time.measureTimedValue

internal class Agent(
    private val model: String,
    private val systemPrompt: String,
    private val client: OpenAIClient = OpenAIOkHttpClient.fromEnv()
) {
    private val history = mutableListOf<AgentMessage>()

    init {
        clearHistory()
    }

    fun send(userText: String): AgentResult {
        val userMessage = AgentMessage(
            role = AgentRole.USER,
            content = userText
        )

        history.add(userMessage)

        try {
            val result = requestOpenAi()

            history.add(
                AgentMessage(
                    role = AgentRole.ASSISTANT,
                    content = result.answer
                )
            )

            return result
        } catch (e: Exception) {
            history.removeLastOrNull()
            throw e
        }
    }

    fun clearHistory() {
        history.clear()
        history.add(
            AgentMessage(
                role = AgentRole.SYSTEM,
                content = systemPrompt
            )
        )
    }

    fun getHistory(): List<AgentMessage> {
        return history.toList()
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