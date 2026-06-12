package com.artembotnev.openai

import com.artembotnev.openai.domain.context.BranchingPromptContextStrategy
import com.artembotnev.openai.domain.context.ContextStrategyType
import com.artembotnev.openai.domain.context.PromptContextStrategy
import com.artembotnev.openai.domain.facts.DialogFactsAccumulator
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogFacts
import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.usecase.dialogusage.ClearDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.LoadDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.SaveDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.facts.ClearDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.facts.LoadDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.facts.SaveDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.history.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.history.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.history.LoadHistoryUseCase
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.measureTimedValue

private val FACTS_UPDATE_SYSTEM_PROMPT = """
    Ты поддерживаешь key-value память о диалоге.
    Верни только JSON-объект вида {"key":"value"} без markdown и пояснений.
    Это должны быть только новые или уточнённые факты, которые нужно добавить к уже сохранённому набору.
    Не повторяй без изменений факты, которые уже есть в памяти.
    Храни только полезные и устойчивые факты из категорий: goal, constraints, preferences, decisions, agreements.
    Если пользователь уточнил или изменил факт, верни тот же ключ с новым значением.
    Ключи делай короткими, в snake_case. Значения делай краткими.
""".trimIndent()

internal class Agent(
    private val model: String,
    private val systemPrompt: String,
    private val contextStrategyType: ContextStrategyType,
    private val activeBranch: String? = null,
    private val client: OpenAIClient = OpenAIOkHttpClient.fromEnv(),
) : KoinComponent {

    val append: AppendMessageUseCase by inject()
    val load: LoadHistoryUseCase by inject()
    val clear: ClearHistoryUseCase by inject()

    val loadFacts: LoadDialogFactsUseCase by inject()
    val saveFacts: SaveDialogFactsUseCase by inject()
    val clearFacts: ClearDialogFactsUseCase by inject()

    val loadUsage: LoadDialogUsageUseCase by inject()
    val saveUsage: SaveDialogUsageUseCase by inject()
    val clearUsage: ClearDialogUsageUseCase by inject()

    val promptContextStrategy: PromptContextStrategy by inject()
    val json: Json by inject()

    private val factsSerializer = MapSerializer(String.serializer(), String.serializer())
    private var dialogUsage = loadUsage()

    fun send(userText: String): AgentResult {
        append(createDialogMessage(role = AgentRole.USER, content = userText))

        try {
            val factsUpdateResult = if (contextStrategyType.usesFactsMemory) {
                runCatching { updateFacts() }
                    .getOrElse { FactsUpdateResult.empty() }
            } else {
                FactsUpdateResult.empty()
            }

            val result = requestOpenAi().merge(factsUpdateResult)

            append(createDialogMessage(role = AgentRole.ASSISTANT, content = result.answer))

            dialogUsage = dialogUsage.accumulate(result)
            saveUsage(dialogUsage)

            return result
        } catch (e: Exception) {
            throw e
        }
    }

    fun createCheckpoint(firstBranch: String, secondBranch: String): String {
        require(contextStrategyType == ContextStrategyType.BRANCHING) {
            "Checkpoint доступен только в стратегии Branching."
        }

        val normalizedFirstBranch = normalizeBranchName(firstBranch)
        val normalizedSecondBranch = normalizeBranchName(secondBranch)

        require(normalizedFirstBranch != normalizedSecondBranch) {
            "Названия веток должны отличаться"
        }

        val existingBranches = getAvailableBranches().toSet()
        require(normalizedFirstBranch !in existingBranches) {
            "Ветка '$normalizedFirstBranch' уже существует"
        }
        require(normalizedSecondBranch !in existingBranches) {
            "Ветка '$normalizedSecondBranch' уже существует"
        }

        append(
            AgentMessage.checkpointMarker(
                sourceBranch = activeBranch,
                firstBranch = normalizedFirstBranch,
                secondBranch = normalizedSecondBranch,
            )
        )

        return normalizedFirstBranch
    }

    fun getAvailableBranches(): List<String> =
        BranchingPromptContextStrategy.listBranches(getHistory())

    fun clearHistory() {
        clear()
        clearFacts()
        clearUsage()
        dialogUsage = DialogUsage()
    }

    fun getHistory(): List<AgentMessage> {
        return load()
    }

    fun getFacts(): DialogFacts = loadFacts()

    fun getDialogUsage(): DialogUsage = dialogUsage

    private fun updateFacts(): FactsUpdateResult {
        val currentFacts = loadFacts()
        val currentHistory = getHistory()
        val params = buildFactsUpdateParams(currentFacts, currentHistory)

        val (completion, duration) = measureTimedValue {
            client.chat()
                .completions()
                .create(params)
        }

        val rawFacts = completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            .orEmpty()

        val newFacts = parseFacts(rawFacts, emptyMap())
        val mergedFacts = mergeFacts(currentFacts, newFacts)
        saveFacts(mergedFacts)

        val usage = completion.usage().orElse(null)

        return FactsUpdateResult(
            promptTokens = usage?.promptTokens(),
            completionTokens = usage?.completionTokens(),
            totalTokens = usage?.totalTokens(),
            elapsedSec = duration.inWholeSeconds
        )
    }

    private fun requestOpenAi(): AgentResult {
        val params = buildConversationParams()

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

    private fun buildConversationParams(): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(model)

        builder.addSystemMessage(systemPrompt)

        val promptContext = promptContextStrategy.build(
            facts = if (contextStrategyType.usesFactsMemory) loadFacts() else emptyMap(),
            history = getHistory()
        )

        promptContext.factsBlock?.let(builder::addSystemMessage)

        for (message in promptContext.messages) {
            when (message.role) {
                AgentRole.USER -> builder.addUserMessage(message.content)
                AgentRole.ASSISTANT -> builder.addAssistantMessage(message.content)
                AgentRole.SYSTEM -> Unit
            }
        }

        return builder.build()
    }

    private fun buildFactsUpdateParams(
        currentFacts: DialogFacts,
        history: List<AgentMessage>
    ): ChatCompletionCreateParams {
        val currentFactsJson = json.encodeToString(factsSerializer, currentFacts)
        val historyText = history
            .filter { it.role != AgentRole.SYSTEM }
            .joinToString(separator = "\n") { message ->
                val role = when (message.role) {
                    AgentRole.USER -> "user"
                    AgentRole.ASSISTANT -> "assistant"
                    AgentRole.SYSTEM -> "system"
                }
                "$role: ${message.content}"
            }

        return ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(FACTS_UPDATE_SYSTEM_PROMPT)
            .addUserMessage(
                buildString {
                    appendLine("Current facts JSON:")
                    appendLine(currentFactsJson)
                    appendLine()
                    appendLine("Dialog history:")
                    append(historyText)
                }
            )
            .build()
    }

    private fun parseFacts(rawFacts: String, fallbackFacts: DialogFacts): DialogFacts {
        if (rawFacts.isBlank()) {
            return fallbackFacts
        }

        return runCatching {
            val cleanedFacts = stripCodeFences(rawFacts)
            val parsedFacts = json.decodeFromString(factsSerializer, cleanedFacts)
            normalizeFacts(parsedFacts)
        }.getOrElse { fallbackFacts }
    }

    private fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith("```")) {
            return trimmed
        }

        return trimmed
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun normalizeFacts(facts: DialogFacts): DialogFacts {
        val normalizedFacts = linkedMapOf<String, String>()

        for ((key, value) in facts) {
            val normalizedKey = key
                .trim()
                .lowercase()
                .replace(FACT_KEY_PATTERN, "_")
                .trim('_')
            val normalizedValue = value.trim()

            if (normalizedKey.isNotEmpty() && normalizedValue.isNotEmpty()) {
                normalizedFacts[normalizedKey] = normalizedValue
            }
        }

        return normalizedFacts
    }

    private fun mergeFacts(currentFacts: DialogFacts, newFacts: DialogFacts): DialogFacts =
        DialogFactsAccumulator.merge(currentFacts, newFacts)

    private fun createDialogMessage(role: AgentRole, content: String): AgentMessage = AgentMessage(
        role = role,
        content = content,
        branch = if (contextStrategyType == ContextStrategyType.BRANCHING) activeBranch else null
    )

    private fun normalizeBranchName(branchName: String): String {
        val normalizedName = branchName.trim()
        require(normalizedName.matches(BRANCH_NAME_PATTERN)) {
            "Имя '$branchName' содержит недопустимые символы"
        }
        return normalizedName
    }

    private fun AgentResult.merge(factsUpdateResult: FactsUpdateResult): AgentResult = copy(
        promptTokens = sumUsage(promptTokens, factsUpdateResult.promptTokens),
        completionTokens = sumUsage(completionTokens, factsUpdateResult.completionTokens),
        totalTokens = sumUsage(totalTokens, factsUpdateResult.totalTokens),
        elapsedSec = elapsedSec + factsUpdateResult.elapsedSec
    )

    private fun sumUsage(first: Long?, second: Long?): Long? {
        if (first == null && second == null) {
            return null
        }

        return (first ?: 0) + (second ?: 0)
    }

    private data class FactsUpdateResult(
        val promptTokens: Long?,
        val completionTokens: Long?,
        val totalTokens: Long?,
        val elapsedSec: Long
    ) {
        companion object {
            fun empty() = FactsUpdateResult(
                promptTokens = null,
                completionTokens = null,
                totalTokens = null,
                elapsedSec = 0
            )
        }
    }

    private companion object {
        val FACT_KEY_PATTERN = Regex("[^a-z0-9]+")
        val BRANCH_NAME_PATTERN = Regex("[a-zA-Z0-9._-]+")
    }
}
