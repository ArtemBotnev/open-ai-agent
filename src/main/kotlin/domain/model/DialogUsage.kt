package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

/**
 * Накопительный расход токенов за весь диалог.
 *
 * Суммирует статистику использования токенов по всем запросам, выполненным
 * в рамках текущей сессии. Сбрасывается при очистке истории диалога.
 *
 * @property promptTokens суммарное количество токенов, израсходованных на запросы (промпты).
 * @property completionTokens суммарное количество токенов в ответах модели.
 * @property totalTokens суммарное количество токенов запросов и ответов.
 */
@Serializable
internal data class DialogUsage(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
) {

    /**
     * Прибавляет статистику одного запроса к накопленному итогу.
     *
     * Отсутствующие значения ([AgentResult.promptTokens] и др. равны `null`)
     * трактуются как нулевые.
     *
     * @param result результат одного запроса к агенту.
     * @return новый [DialogUsage] с учётом токенов переданного запроса.
     */
    fun accumulate(result: AgentResult): DialogUsage = DialogUsage(
        promptTokens = promptTokens + (result.promptTokens ?: 0),
        completionTokens = completionTokens + (result.completionTokens ?: 0),
        totalTokens = totalTokens + (result.totalTokens ?: 0),
    )
}
