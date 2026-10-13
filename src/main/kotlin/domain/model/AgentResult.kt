package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

/**
 * Результат одного запроса к агенту.
 *
 * Содержит сгенерированный ответ модели и метрики выполнения запроса:
 * статистику использования токенов и затраченное время.
 *
 * @property answer текст ответа, сгенерированного моделью.
 * @property promptTokens количество токенов, израсходованных на запрос (промпт);
 *   `null`, если статистика недоступна.
 * @property completionTokens количество токенов в ответе модели;
 *   `null`, если статистика недоступна.
 * @property totalTokens суммарное количество токенов запроса и ответа;
 *   `null`, если статистика недоступна.
 * @property elapsedSec время выполнения запроса в секундах.
 */
@Serializable
internal data class AgentResult(
    val answer: String,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val totalTokens: Long?,
    val elapsedSec: Long
)