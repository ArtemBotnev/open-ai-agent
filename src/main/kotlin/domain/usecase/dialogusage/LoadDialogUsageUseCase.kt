package com.artembotnev.openai.domain.usecase.dialogusage

import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.repository.DialogUsageRepository

/**
 * Сценарий загрузки сохранённого расхода токенов за диалог.
 *
 * @property repo репозиторий расхода токенов, из которого читаются данные.
 */
internal class LoadDialogUsageUseCase(private val repo: DialogUsageRepository) {

    /**
     * Загружает сохранённый расход токенов.
     *
     * @return сохранённый [DialogUsage]; [DialogUsage] с нулевыми значениями,
     *   если данные отсутствуют.
     */
    operator fun invoke(): DialogUsage = repo.load()
}
