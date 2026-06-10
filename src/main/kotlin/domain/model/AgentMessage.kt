package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

/**
 * Одно сообщение в истории диалога с агентом.
 *
 * Из последовательности таких сообщений формируется контекст запроса к модели,
 * а также сохраняется и загружается история переписки.
 *
 * @property role роль автора сообщения (система, пользователь или ассистент).
 * @property content текстовое содержимое сообщения.
 */
@Serializable
internal data class AgentMessage(
    val role: AgentRole,
    val content: String
)