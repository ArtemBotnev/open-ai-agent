package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

/**
 * Роль автора сообщения в диалоге с агентом.
 *
 * Соответствует ролям сообщений в OpenAI Chat Completions API и определяет,
 * как сообщение интерпретируется моделью.
 */
@Serializable
internal enum class AgentRole {
    /** Системная инструкция, задающая поведение и контекст агента. */
    SYSTEM,

    /** Сообщение, отправленное пользователем. */
    USER,

    /** Ответ, сгенерированный моделью (агентом). */
    ASSISTANT
}