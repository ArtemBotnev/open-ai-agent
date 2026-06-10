package com.artembotnev.openai.data.dialogusage

import com.artembotnev.openai.domain.model.DialogUsage

/**
 * Источник данных для накопленного расхода токенов за диалог.
 *
 * Отвечает за низкоуровневое хранение [DialogUsage] (например, в файле)
 * и используется реализациями [com.artembotnev.openai.domain.repository.DialogUsageRepository]
 * как конкретный механизм персистентности.
 */
internal interface DialogUsageDataSource {

    /**
     * Считывает накопленный расход токенов из хранилища.
     *
     * @return сохранённый [DialogUsage]; [DialogUsage] с нулевыми значениями,
     *   если данные отсутствуют.
     */
    fun load(): DialogUsage

    /**
     * Полностью перезаписывает хранилище переданным расходом токенов.
     *
     * @param usage накопленный расход токенов, который нужно сохранить.
     */
    fun save(usage: DialogUsage)

    /** Очищает хранилище, удаляя сохранённый расход токенов. */
    fun clear()
}
