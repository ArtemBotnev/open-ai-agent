package com.artembotnev.openai.domain.usecase.facts

import com.artembotnev.openai.domain.model.DialogFacts
import com.artembotnev.openai.domain.repository.DialogFactsRepository

internal class LoadDialogFactsUseCase(private val repo: DialogFactsRepository) {
    operator fun invoke(): DialogFacts = repo.load()
}
