package com.artembotnev.openai

import com.artembotnev.openai.di.historyModule
import com.artembotnev.openai.di.llmModule
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.model.AgentResult
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin

//private const val LLM_VERSION = "gpt-5.5"
//private const val LLM_VERSION = "gpt-4.1-nano"
private const val LLM_VERSION = "gpt-4o"

fun main(args: Array<String>) {
    startKoin { modules(historyModule, llmModule(LLM_VERSION)) }

    val llm = GlobalContext.get().get<Llm>()

    printInstruction()

    chat(llm)
}

private fun chat(llm: Llm) {
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
                llm.clearHistory()
                println("История очищена.")
                continue
            }
        }

        try {
            val result = llm.send(input)
            println()
            println("GPT:\n ${result.answer}")

//            printStatistic(result)

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

private fun printStatistic(result: AgentResult) {
    println()
    println("Статистика запроса:")
    println("Время выполнения: ${result.elapsedSec} секунд")
    println("Prompt tokens: ${result.promptTokens ?: "нет данных"}")
    println("Completion tokens: ${result.completionTokens ?: "нет данных"}")
    println("Total tokens: ${result.totalTokens ?: "нет данных"}")
    println()
}
