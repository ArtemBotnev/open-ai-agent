package com.artembotnev.openai

import com.artembotnev.openai.di.historyModule
import com.artembotnev.openai.domain.context.ContextStrategyType
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.DialogUsage
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

//private const val LLM_VERSION = "gpt-5.5"
private const val LLM_VERSION = "gpt-4.1-nano"
//private const val LLM_VERSION = "gpt-4o"

private const val SYSTEM_INSTRUCTION = "Ты полезный ассистент. Отвечай понятно и по делу. Кратко"

fun main(args: Array<String>) {
    val initialStrategy = resolveInitialStrategy(args)
    chat(initialStrategy)
}

private fun chat(initialStrategy: ContextStrategyType) {
    var currentStrategy = initialStrategy
    var agent = createAgent(currentStrategy)

    printInstruction(currentStrategy)

    while (true) {
        println()
        print("Вы: ")

        val input = readlnOrNull()?.trim()

        if (input.isNullOrBlank()) {
            continue
        }

        when (input.lowercase()) {
            "/exit" -> {
                println("Выход.")
                break
            }

            "/clear" -> {
                agent.clearHistory()
                println("История очищена.")
                continue
            }

            "/strategy" -> {
                currentStrategy = selectStrategy(currentStrategy)
                agent = createAgent(currentStrategy)
                println("Стратегия переключена: ${currentStrategy.displayName}")
                continue
            }
        }

        try {
            val result = agent.send(input)
            println()
            println("GPT:\n ${result.answer}")
            printStatistic(result, agent.getDialogUsage())
        } catch (e: Exception) {
            println("Ошибка: ${e.message}")
        }
    }
}

private fun createAgent(strategyType: ContextStrategyType): Agent {
    configureDependencies(strategyType)
    return Agent(
        model = LLM_VERSION,
        systemPrompt = SYSTEM_INSTRUCTION,
        contextStrategyType = strategyType,
    )
}

private fun configureDependencies(strategyType: ContextStrategyType) {
    if (GlobalContext.getOrNull() != null) {
        stopKoin()
    }

    startKoin { modules(historyModule(strategyType)) }
}

private fun resolveInitialStrategy(args: Array<String>): ContextStrategyType {
    parseStrategyArg(args)?.let { return it }
    return selectStrategy()
}

private fun parseStrategyArg(args: Array<String>): ContextStrategyType? {
    val inlineValue = args.firstOrNull { it.startsWith("--strategy=") }
        ?.substringAfter('=', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }

    val separateValue = args.indices
        .firstOrNull { index -> args[index] == "--strategy" && index + 1 < args.size }
        ?.let { args[it + 1] }

    val rawValue = inlineValue ?: separateValue ?: return null
    val strategyType = ContextStrategyType.fromInput(rawValue)

    if (strategyType == null) {
        println("Неизвестная стратегия: $rawValue")
        println("Доступные значения: sliding-window, sticky-facts")
    }

    return strategyType
}

private fun selectStrategy(currentStrategy: ContextStrategyType? = null): ContextStrategyType {
    println()
    println("Выберите стратегию управления контекстом:")
    ContextStrategyType.entries.forEachIndexed { index, strategyType ->
        val currentMarker = if (strategyType == currentStrategy) " [текущая]" else ""
        println("${index + 1}. ${strategyType.displayName} — ${strategyType.description}$currentMarker")
    }

    while (true) {
        print("Введите номер или имя стратегии: ")
        val input = readlnOrNull()?.trim().orEmpty()

        ContextStrategyType.fromInput(input)?.let { return it }

        println("Неверный выбор. Доступно: 1, 2, sliding-window, sticky-facts")
    }
}

private fun printInstruction(strategyType: ContextStrategyType) {
    println("Чат с OpenAI API")
    println("Модель: $LLM_VERSION")
    println("Стратегия: ${strategyType.displayName}")
    println()
    println("Команды:")
    println("/exit     — выйти")
    println("/clear    — очистить историю")
    println("/strategy — сменить стратегию")
    println()
}

private fun printStatistic(result: AgentResult, dialogUsage: DialogUsage) {
    println()
    println("Статистика запроса:")
    println("Время выполнения: ${result.elapsedSec} секунд")
    println("Prompt tokens: ${result.promptTokens ?: "нет данных"}")
    println("Completion tokens: ${result.completionTokens ?: "нет данных"}")
    println("Total tokens: ${result.totalTokens ?: "нет данных"}")
    println()
    println("Расход токенов за весь диалог:")
    println("Prompt tokens: ${dialogUsage.promptTokens}")
    println("Completion tokens: ${dialogUsage.completionTokens}")
    println("Total tokens: ${dialogUsage.totalTokens}")
    println()
}
