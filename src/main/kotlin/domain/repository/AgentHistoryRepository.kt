package com.artembotnev.openai.domain.repository

import com.artembotnev.openai.domain.model.AgentMessage

/**
 * Репозиторий истории диалога с агентом.
 *
 * Абстрагирует доменный слой от конкретного механизма хранения сообщений,
 * предоставляя операции чтения, сохранения, дозаписи и очистки истории.
 */
internal interface AgentHistoryRepository {

    /**
     * Загружает всю сохранённую историю диалога.
     *
     * @return список сообщений в порядке их добавления;
     *   пустой список, если история отсутствует.
     */
    fun load(): List<AgentMessage>

    /**
     * Полностью перезаписывает историю диалога переданным набором сообщений.
     *
     * @param all список сообщений, который заменяет текущую историю.
     */
    fun save(all: List<AgentMessage>)

    /**
     * Добавляет одно сообщение в конец истории.
     *
     * @param message сообщение, которое нужно добавить.
     * @return обновлённый список сообщений с учётом добавленного.
     */
    fun append(message: AgentMessage): List<AgentMessage>

    /** Удаляет всю сохранённую историю диалога. */
    fun clear()
}