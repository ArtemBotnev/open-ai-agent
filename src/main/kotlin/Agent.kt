package com.artembotnev.openai

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.ConversationSummary
import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.usecase.history.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.ClearDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.LoadDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.SaveDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.SaveHistoryUseCase
import com.artembotnev.openai.domain.usecase.summary.ClearConversationSummaryUseCase
import com.artembotnev.openai.domain.usecase.summary.LoadConversationSummaryUseCase
import com.artembotnev.openai.domain.usecase.summary.SaveConversationSummaryUseCase
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

    val loadSummary: LoadConversationSummaryUseCase by inject()
    val saveSummary: SaveConversationSummaryUseCase by inject()
    val clearSummary: ClearConversationSummaryUseCase by inject()

    private var dialogUsage = loadUsage()

    /** Текущее сжатое представление ранней части диалога. */
    private var summary = loadSummary()

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

            compressContextIfNeeded()

            return result
        } catch (e: Exception) {
            throw e
        }
    }

    fun clearHistory() {
        clear()
        clearUsage()
        clearSummary()
        dialogUsage = DialogUsage()
        summary = ConversationSummary()
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

    /** Возвращает текущее сжатое представление ранней части диалога. */
    fun getSummary(): ConversationSummary = summary

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

    /**
     * Формирует запрос к модели по принципу управления контекстом:
     * системная инструкция → краткое содержание ранней части диалога (если есть)
     * → последние сообщения «как есть». Сжатые сообщения в истории уже отсутствуют.
     */
    private fun buildParams(): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(model)

        val history = getHistory()
        val systemMessages = history.filter { it.role == AgentRole.SYSTEM }
        val conversation = history.filter { it.role != AgentRole.SYSTEM }

        // Системная инструкция (персона агента) сохраняется всегда.
        if (systemMessages.isEmpty()) {
            builder.addSystemMessage(systemPrompt)
        } else {
            systemMessages.forEach { builder.addSystemMessage(it.content) }
        }

        // Вместо удалённых ранних сообщений подставляем их краткое содержание.
        if (!summary.isEmpty) {
            builder.addSystemMessage("Краткое содержание предыдущей части диалога:\n${summary.text}")
        }

        // В истории остались только несжатые сообщения — передаём их «как есть».
        for (message in conversation) {
            when (message.role) {
                AgentRole.USER -> builder.addUserMessage(message.content)
                AgentRole.ASSISTANT -> builder.addAssistantMessage(message.content)
                AgentRole.SYSTEM -> builder.addSystemMessage(message.content)
            }
        }

        return builder.build()
    }

    /**
     * Сворачивает устаревшие сообщения в [summary], сохраняя последние
     * [KEEP_LAST_MESSAGES] сообщений «как есть». Сжатие выполняется пакетами
     * по [SUMMARIZE_BATCH] сообщений, чтобы не обращаться к модели на каждом шаге.
     *
     * Свёрнутые сообщения физически удаляются из истории — вместо них в контекст
     * запроса подставляется накопленное краткое содержание (см. [buildParams]).
     */
    private fun compressContextIfNeeded() {
        val history = getHistory()
        val systemMessages = history.filter { it.role == AgentRole.SYSTEM }
        val conversation = history.filter { it.role != AgentRole.SYSTEM }

        // Сообщения за пределами последних KEEP_LAST_MESSAGES — кандидаты на сжатие.
        val pending = conversation.size - KEEP_LAST_MESSAGES
        if (pending < SUMMARIZE_BATCH) return

        val block = conversation.subList(0, pending)

        try {
            val newText = summarize(previousSummary = summary.text, block = block)
            if (newText.isBlank()) return

            // Обновляем краткое содержание...
            summary = ConversationSummary(
                text = newText,
                coveredCount = summary.coveredCount + block.size,
            )
            saveSummary(summary)

            // ...и удаляем свёрнутые сообщения из истории, оставляя системную
            // инструкцию и последние KEEP_LAST_MESSAGES сообщений «как есть».
            save(systemMessages + conversation.takeLast(KEEP_LAST_MESSAGES))
        } catch (e: Exception) {
            // Сжатие — вспомогательная операция: её сбой не должен прерывать диалог.
            System.err.println("Не удалось обновить краткое содержание: ${e.message}")
        }
    }

    /**
     * Запрашивает у модели обновлённое краткое содержание: объединяет
     * предыдущий [previousSummary] с новым [block] сообщений.
     *
     * @return текст краткого содержания или пустую строку, если модель не вернула ответ.
     */
    private fun summarize(previousSummary: String, block: List<AgentMessage>): String {
        val prompt = buildString {
            if (previousSummary.isNotBlank()) {
                appendLine("Текущее краткое содержание диалога:")
                appendLine(previousSummary)
                appendLine()
            }
            appendLine("Новые сообщения, которые нужно учесть в кратком содержании:")
            block.forEach { appendLine("${it.role.title()}: ${it.content}") }
        }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(SUMMARIZER_INSTRUCTION)
            .addUserMessage(prompt)
            .build()

        val completion = client.chat().completions().create(params)

        return completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            .orEmpty()
    }

    private fun AgentRole.title(): String = when (this) {
        AgentRole.SYSTEM -> "Система"
        AgentRole.USER -> "Пользователь"
        AgentRole.ASSISTANT -> "Ассистент"
    }

    private companion object {
        /** Количество последних сообщений, которые всегда хранятся «как есть». */
        const val KEEP_LAST_MESSAGES = 3

        /** Размер пакета устаревших сообщений, при накоплении которого выполняется сжатие. */
        const val SUMMARIZE_BATCH = 5

        const val SUMMARIZER_INSTRUCTION =
            "Ты — модуль сжатия контекста диалога. На основе текущего краткого содержания " +
                "и новых сообщений сформируй обновлённое связное краткое содержание на русском языке. " +
                "Сохрани ключевые факты, темы, выводы и договорённости, опусти второстепенные детали. " +
                "Ответь только текстом краткого содержания, без вступлений и комментариев."
    }
}