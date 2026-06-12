package com.artembotnev.openai

import com.artembotnev.openai.branching.DialogBranchManager
import com.artembotnev.openai.di.historyModule
import com.artembotnev.openai.domain.context.ContextStrategyType
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.DialogUsage
import okio.Path
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
    var currentBranch: String? = null
    var agent = createAgent(currentStrategy, currentBranch)

    printInstruction(currentStrategy, currentBranch)

    while (true) {
        println()
        print("Вы: ")

        val input = readlnOrNull()?.trim()

        if (input.isNullOrBlank()) {
            continue
        }

        val command = input.substringBefore(' ').lowercase()

        when (command) {
            "/exit" -> {
                println("Выход.")
                break
            }

            "/clear" -> {
                agent.clearHistory()
                currentBranch = null
                println("История очищена.")
                continue
            }

            "/strategy" -> {
                currentStrategy = selectStrategy(currentStrategy)
                agent = createAgent(currentStrategy, currentBranch)
                printInstruction(currentStrategy, currentBranch)
                continue
            }

            "/checkpoint", "/switch", "/branches" -> {
                val updatedBranch = handleBranchingCommand(
                    input = input,
                    currentStrategy = currentStrategy,
                    currentBranch = currentBranch,
                    agent = agent,
                ) ?: continue

                currentBranch = updatedBranch
                agent = createAgent(currentStrategy, currentBranch)
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

private fun createAgent(
    strategyType: ContextStrategyType,
    currentBranch: String?,
): Agent {
    val storageBaseDir = resolveStorageBaseDir(strategyType)
    configureDependencies(strategyType, storageBaseDir, currentBranch)
    return Agent(
        model = LLM_VERSION,
        systemPrompt = SYSTEM_INSTRUCTION,
        contextStrategyType = strategyType,
        activeBranch = if (strategyType == ContextStrategyType.BRANCHING) currentBranch else null,
    )
}

private fun resolveStorageBaseDir(strategyType: ContextStrategyType): Path {
    val generatedBaseDir = DialogBranchManager.defaultGeneratedBaseDir()
    return if (strategyType == ContextStrategyType.BRANCHING) {
        generatedBaseDir / "branching"
    } else {
        generatedBaseDir
    }
}

private fun configureDependencies(
    strategyType: ContextStrategyType,
    storageBaseDir: Path,
    activeBranch: String?,
) {
    if (GlobalContext.getOrNull() != null) {
        stopKoin()
    }

    startKoin { modules(historyModule(strategyType, storageBaseDir, activeBranch)) }
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
        println("Доступные значения: sliding-window, sticky-facts, branching")
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

        println("Неверный выбор. Доступно: 1, 2, 3, sliding-window, sticky-facts, branching")
    }
}

private fun handleBranchingCommand(
    input: String,
    currentStrategy: ContextStrategyType,
    currentBranch: String?,
    agent: Agent,
): String? {
    if (currentStrategy != ContextStrategyType.BRANCHING) {
        println("Эти команды доступны только в стратегии Branching.")
        return null
    }

    val parts = input.split(Regex("\\s+")).filter { it.isNotBlank() }
    val command = parts.first().lowercase()

    return try {
        when (command) {
            "/checkpoint" -> {
                require(parts.size == 3) { "Использование: /checkpoint <branch-1> <branch-2>" }
                val firstBranch = parts[1]
                val secondBranch = parts[2]
                val selectedBranch = agent.createCheckpoint(firstBranch, secondBranch)
                println(
                    "Checkpoint сохранён. Созданы ветки '$firstBranch' и '$secondBranch'. " +
                        "Активна ветка '$selectedBranch'."
                )
                selectedBranch
            }

            "/switch" -> {
                require(parts.size == 2) { "Использование: /switch <branch>" }
                val branchName = parts[1]
                require(branchName in agent.getAvailableBranches()) { "Ветка '$branchName' не найдена" }
                println("Переключено на ветку '$branchName'.")
                branchName
            }

            "/branches" -> {
                val branches = agent.getAvailableBranches()
                println("Ветки: ${branches.ifEmpty { listOf("нет веток") }.joinToString()}")
                currentBranch
            }

            else -> currentBranch
        }
    } catch (e: IllegalArgumentException) {
        println(e.message)
        null
    }
}

private fun printInstruction(strategyType: ContextStrategyType, currentBranch: String?) {
    println("Чат с OpenAI API")
    println("Модель: $LLM_VERSION")
    println("Стратегия: ${strategyType.displayName}")
    if (strategyType == ContextStrategyType.BRANCHING && currentBranch != null) {
        println("Текущая ветка: $currentBranch")
    }
    println()
    println("Команды:")
    println("/exit        — выйти")
    println("/clear       — очистить историю")
    println("/strategy    — сменить стратегию")
    if (strategyType == ContextStrategyType.BRANCHING) {
        println("/checkpoint  — создать две ветки и выбрать первую")
        println("/switch      — переключить ветку")
        println("/branches    — список веток")
    }
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
