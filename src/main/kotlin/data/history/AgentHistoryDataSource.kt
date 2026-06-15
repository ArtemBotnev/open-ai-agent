package com.artembotnev.openai.data.history

import com.artembotnev.openai.domain.model.AgentMessage

/**
 * Источник данных для истории диалога с агентом.
 *
 * Отвечает за низкоуровневое хранение сообщений (например, в файле или БД)
 * и используется реализациями [com.artembotnev.openai.domain.repository.AgentHistoryRepository]
 * как конкретный механизм персистентности.
 */
internal interface AgentHistoryDataSource {

    /**
     * Считывает всю сохранённую историю диалога из хранилища.
     *
     * @return список сообщений в порядке их добавления;
     *   пустой список, если история отсутствует.
     */
    fun load(): List<AgentMessage>

    /**
     * Полностью перезаписывает содержимое хранилища переданным набором сообщений.
     *
     * @param all список сообщений, который заменяет текущую историю.
     */
    fun save(all: List<AgentMessage>)

    /**
     * Дозаписывает одно сообщение в хранилище.
     *
     * @param message сообщение, которое нужно добавить.
     * @return обновлённый список сообщений с учётом добавленного.
     */
    fun append(message: AgentMessage): List<AgentMessage>

    /** Очищает хранилище, удаляя всю сохранённую историю диалога. */
    fun clear()
}
