package com.artembotnev.openai.domain.usecase.facts

import com.artembotnev.openai.domain.repository.DialogFactsRepository

internal class ClearDialogFactsUseCase(private val repo: DialogFactsRepository) {
    operator fun invoke() = repo.clear()
}
