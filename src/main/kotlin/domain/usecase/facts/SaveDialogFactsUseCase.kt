package com.artembotnev.openai.domain.usecase.facts

import com.artembotnev.openai.domain.model.DialogFacts
import com.artembotnev.openai.domain.repository.DialogFactsRepository

internal class SaveDialogFactsUseCase(private val repo: DialogFactsRepository) {
    operator fun invoke(facts: DialogFacts) = repo.save(facts)
}
