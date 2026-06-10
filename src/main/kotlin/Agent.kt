package com.artembotnev.openai

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.usecase.history.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.ClearDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.LoadDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.SaveDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.SaveHistoryUseCase
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

    val loadUsage: LoadDialogUsageUseCase by inject()
    val saveUsage: SaveDialogUsageUseCase by inject()
    val clearUsage: ClearDialogUsageUseCase by inject()

    private var dialogUsage = loadUsage()

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

            dialogUsage = dialogUsage.accumulate(result)
            saveUsage(dialogUsage)

            return result
        } catch (e: Exception) {
            throw e
        }
    }

    fun clearHistory() {
        clear()
        clearUsage()
        dialogUsage = DialogUsage()
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

    /** Возвращает накопленный расход токенов за весь текущий диалог. */
    fun getDialogUsage(): DialogUsage = dialogUsage

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