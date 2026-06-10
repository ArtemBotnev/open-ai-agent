package com.artembotnev.openai

import com.artembotnev.openai.di.historyModule
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.DialogUsage
import org.koin.core.context.GlobalContext.startKoin

//private const val LLM_VERSION = "gpt-5.5"
private const val LLM_VERSION = "gpt-4.1-nano"
//private const val LLM_VERSION = "gpt-4o"

private const val SYSTEM_INSTRUCTION = "Ты полезный ассистент. Отвечай понятно и по делу. Кратко"


fun main(args: Array<String>) {
    startKoin { modules(historyModule) }

    val agent = Agent(
        model = LLM_VERSION,
        systemPrompt = SYSTEM_INSTRUCTION,
    )

    printInstruction()

    chat(agent)
}

private fun chat(agent: Agent) {
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

private fun printInstruction() {
    println("Чат с OpenAI API")
    println("Модель: $LLM_VERSION")
    println()
    println("Команды:")
    println("/exit  — выйти")
    println("/clear — очистить историю")
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