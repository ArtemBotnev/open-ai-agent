package com.artembotnev.openai.infra.profile

import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.domain.context.UserProfileContext
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams

internal class OpenAiUserProfileMemoryUpdater(
    private val model: String,
    private val client: OpenAIClient,
    private val userProfileDataSource: UserProfileContextDataSource,
) : UserProfileMemoryUpdater {

    override fun captureDialogue(userText: String, assistantText: String) {
        val context = userProfileDataSource.load()
        val delta = generateDelta(context, userText, assistantText)
        val mergedKnowledge = mergeBullets(context.knowledge, delta.knowledge)
        val mergedDecisions = mergeBullets(context.decisions, delta.decisions)

        if (mergedKnowledge == context.knowledge && mergedDecisions == context.decisions) {
            return
        }

        userProfileDataSource.save(
            context.copy(
                knowledge = mergedKnowledge,
                decisions = mergedDecisions,
            )
        )
    }

    private fun generateDelta(
        context: UserProfileContext,
        userText: String,
        assistantText: String,
    ): ProfileDelta {
        val prompt = buildString {
            appendLine("Текущий профиль пользователя:")
            appendLine("- User ID: ${context.userId}")
            appendLine("- Роль: ${context.role}")
            appendLine("- Стек: ${context.stack.joinToString()}")
            appendLine()
            appendLine("Текущие знания пользователя:")
            appendLine(context.knowledge.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine()
            appendLine("Текущие решения пользователя и агента:")
            appendLine(context.decisions.joinToString(separator = "\n") { "- $it" }.ifBlank { "- нет записей" })
            appendLine()
            appendLine("Последний диалог:")
            appendLine("Пользователь: $userText")
            appendLine("Агент: $assistantText")
        }

        val params = ChatCompletionCreateParams.builder()
            .model(model)
            .addSystemMessage(PROFILE_UPDATE_SYSTEM_PROMPT)
            .addUserMessage(prompt)
            .build()

        val raw = client.chat()
            .completions()
            .create(params)
            .choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            ?: return ProfileDelta()

        return parseMarkdownDelta(raw)
    }

    private fun parseMarkdownDelta(raw: String): ProfileDelta {
        val normalized = raw.trim()
        val knowledgeStart = normalized.indexOf(KNOWLEDGE_HEADING)
        val decisionsStart = normalized.indexOf(DECISIONS_HEADING)
        if (knowledgeStart < 0 || decisionsStart < 0) return ProfileDelta()

        val knowledge = extractBullets(
            normalized.substring(knowledgeStart + KNOWLEDGE_HEADING.length, decisionsStart)
        )
        val decisions = extractBullets(
            normalized.substring(decisionsStart + DECISIONS_HEADING.length)
        )

        return ProfileDelta(knowledge = knowledge, decisions = decisions)
    }

    private fun extractBullets(section: String): List<String> {
        return section.lines()
            .map { it.trim() }
            .filter { it.startsWith("- ") }
            .map { sanitizeBullet(it.removePrefix("- ")) }
            .filter { it.isNotBlank() }
    }

    private fun mergeBullets(existing: List<String>, incoming: List<String>): List<String> {
        if (incoming.isEmpty()) return existing

        val merged = existing.toMutableList()
        val seen = existing.associateBy { normalize(it) }.toMutableMap()

        for (item in incoming) {
            val sanitized = sanitizeBullet(item)
            val normalized = normalize(sanitized)
            if (normalized.isBlank() || seen.containsKey(normalized)) continue
            seen[normalized] = sanitized
            merged += sanitized
        }

        return merged.takeLast(MAX_ITEMS_PER_SECTION)
    }

    private fun sanitizeBullet(value: String): String {
        val compact = value
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .removePrefix("- ")
            .trimEnd('.', ';', ':', ' ')

        if (compact.isBlank()) return ""

        return compact
            .split(' ')
            .filter { it.isNotBlank() }
            .take(MAX_WORDS_PER_BULLET)
            .joinToString(" ")
    }

    private fun normalize(value: String): String {
        return value
            .trim()
            .removePrefix("- ")
            .trimEnd('.', ' ')
            .lowercase()
    }

    private data class ProfileDelta(
        val knowledge: List<String> = emptyList(),
        val decisions: List<String> = emptyList(),
    )

    private companion object {
        const val KNOWLEDGE_HEADING = "## 2. Знания пользователя"
        const val DECISIONS_HEADING = "## 3. Решения пользователя и агента"
        const val MAX_ITEMS_PER_SECTION = 12
        const val MAX_WORDS_PER_BULLET = 12
        val PROFILE_UPDATE_SYSTEM_PROMPT = """
            Обновляй долговременный профиль пользователя на основе устойчивых, повторяющихся наблюдений о его поведении, знаниях и предпочтениях в целом (не только за текущую сессию). Возвращай результат только в виде markdown в следующем формате.
            ## 2. Знания пользователя
            - [Краткое, предметное наблюдение — максимум 12 слов]
            - [Второе при необходимости — максимум 12 слов]
            
            ## 3. Решения пользователя и агента
            - [Краткое, предметное решение или договорённость — максимум 12 слов]
            - [Второе при необходимости — максимум 12 слов]
            
            **Требования:**
            - В knowledge используй только устойчивые выжимки об уровне, стиле, поведении и предпочтениях пользователя, которые проявляются на протяжении нескольких диалогов или зафиксированы как характерные особенности.
            - В decisions отражай принципы и решения, которые пользователь принимает регулярно или в которых проявляется стабильный подход/договорённость.
            - Не добавляй одноразовые детали, конкретику текущего вопроса или пересказ намерений за одну сессию.
            - Каждый пункт — самостоятельная краткая мысль, не длиннее 12 слов.
            - Не пиши пояснений вне указанных секций, используй только указанный markdown.
            
            **Пример:**
            
            ## 2. Знания пользователя
            - Предпочитает подробные технические объяснения
            - Часто работает с большими данными
            
            ## 3. Решения пользователя и агента
            - Обычно выбирает автоматизацию рутинных задач
            - Согласен на гибкий подход к дедлайнам
            
            **(В реальных примерах пункты содержат только долговременные характеристики и решения, без одноразовых деталей.)**
            
            ---
            
            НАПОМИНАНИЕ:  
            Обновляй долговременный профиль, оценивая поведение и предпочтения пользователя в целом, а не только по текущей сессии. Выход только в формате markdown-секций, максимум по 2 коротких пункта на каждую.
        """.trimIndent()
    }
}
