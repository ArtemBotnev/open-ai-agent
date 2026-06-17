package com.artembotnev.openai.infra.openai.workflow

import com.artembotnev.openai.domain.model.AgentMessage
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlinx.serialization.json.Json

internal class OpenAiWorkflowSupport(
    private val model: String,
    private val client: OpenAIClient,
    private val json: Json,
) {

    fun complete(systemPrompt: String, userPrompt: String): WorkflowCallResult {
        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(systemPrompt)
            .addUserMessage(userPrompt)
            .build()

        val completion = client.chat()
            .completions()
            .create(params)

        val raw = completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            .orEmpty()

        val usage = completion.usage().orElse(null)
        return WorkflowCallResult(
            raw = raw,
            usage = WorkflowUsage(
                promptTokens = usage?.promptTokens(),
                completionTokens = usage?.completionTokens(),
                totalTokens = usage?.totalTokens(),
            )
        )
    }

    inline fun <reified T> parseJsonPayload(raw: String): T? {
        val jsonPayload = extractJsonPayload(raw) ?: return null
        return runCatching { json.decodeFromString<T>(jsonPayload) }.getOrNull()
    }

    fun extractArtifactBlock(raw: String): String {
        val fences = CODE_BLOCK_REGEX.findAll(raw).toList()
        return fences
            .firstOrNull { match ->
                match.groupValues[1].trim().lowercase() != "json"
            }
            ?.groupValues
            ?.get(2)
            ?.trim()
            .orEmpty()
    }

    fun formatHistory(history: List<AgentMessage>, limit: Int = 8): String {
        val tail = history.takeLast(limit)
        if (tail.isEmpty()) return "- История пуста"

        return tail.joinToString(separator = "\n") { message ->
            val role = message.role.name.lowercase()
            "$role: ${message.content.trim()}"
        }
    }

    private fun extractJsonPayload(raw: String): String? {
        CODE_BLOCK_REGEX.findAll(raw).forEach { match ->
            if (match.groupValues[1].trim().lowercase() == "json") {
                return match.groupValues[2].trim()
            }
        }

        val start = raw.indexOf('{')
        if (start < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> if (inString) escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> {
                    if (!inString) {
                        depth--
                        if (depth == 0) {
                            return raw.substring(start, index + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private companion object {
        val CODE_BLOCK_REGEX = Regex("```([A-Za-z0-9_+#.-]*)\\n([\\s\\S]*?)```")
    }
}
