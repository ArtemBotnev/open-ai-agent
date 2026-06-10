package com.artembotnev.openai.domain.usecase.dialogusage

import com.artembotnev.openai.domain.repository.DialogUsageRepository

/**
 * Сценарий очистки сохранённого расхода токенов за диалог.
 *
 * @property repo репозиторий расхода токенов, который требуется очистить.
 */
internal class ClearDialogUsageUseCase(private val repo: DialogUsageRepository) {

    /** Удаляет сохранённый расход токенов. */
    operator fun invoke() = repo.clear()
}
