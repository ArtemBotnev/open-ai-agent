package com.artembotnev.openai.domain.repository

import com.artembotnev.openai.domain.model.DialogUsage

/**
 * Репозиторий накопленного расхода токенов за диалог.
 *
 * Абстрагирует доменный слой от конкретного механизма хранения,
 * предоставляя операции чтения, сохранения и очистки [DialogUsage].
 */
internal interface DialogUsageRepository {

    /**
     * Загружает сохранённый расход токенов.
     *
     * @return сохранённый [DialogUsage]; [DialogUsage] с нулевыми значениями,
     *   если данные отсутствуют.
     */
    fun load(): DialogUsage

    /**
     * Полностью перезаписывает сохранённый расход токенов.
     *
     * @param usage накопленный расход токенов, который нужно сохранить.
     */
    fun save(usage: DialogUsage)

    /** Удаляет сохранённый расход токенов. */
    fun clear()
}
