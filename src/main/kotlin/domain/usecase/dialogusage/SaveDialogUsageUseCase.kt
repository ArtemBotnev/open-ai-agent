package com.artembotnev.openai.domain.usecase.dialogusage

import com.artembotnev.openai.domain.model.DialogUsage
import com.artembotnev.openai.domain.repository.DialogUsageRepository

/**
 * Сценарий сохранения накопленного расхода токенов за диалог.
 *
 * @property repo репозиторий расхода токенов, в котором сохраняются данные.
 */
internal class SaveDialogUsageUseCase(private val repo: DialogUsageRepository) {

    /**
     * Полностью перезаписывает сохранённый расход токенов.
     *
     * @param usage накопленный расход токенов, который нужно сохранить.
     */
    operator fun invoke(usage: DialogUsage) = repo.save(usage)
}
