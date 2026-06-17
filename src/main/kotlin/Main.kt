package com.artembotnev.openai

import com.artembotnev.openai.di.llmModule
import com.artembotnev.openai.di.storageModule
import com.artembotnev.openai.data.context.TaskStateDataSource
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.model.AgentResult
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin

//private const val LLM_VERSION = "gpt-5.5"
//private const val LLM_VERSION = "gpt-4.1-nano"
private const val LLM_VERSION = "gpt-4o"
private const val DEFAULT_USER_ID = "0"
private val USER_ID_REGEX = Regex("[A-Za-z0-9_-]+")

fun main(args: Array<String>) {
    val userId = readUserId()

    startKoin { modules(storageModule(userId), llmModule(LLM_VERSION)) }

    val llm = GlobalContext.get().get<Llm>()
    val taskStateDataSource = GlobalContext.get().get<TaskStateDataSource>()

    printInstruction(userId)

    chat(llm, taskStateDataSource)
}

private fun chat(
    llm: Llm,
    taskStateDataSource: TaskStateDataSource,
) {
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
            printTaskState(taskStateDataSource)

//            printStatistic(result)

        } catch (e: Exception) {
            println("Ошибка: ${e.message}")
        }
    }
}

private fun printTaskState(taskStateDataSource: TaskStateDataSource) {
    val state = taskStateDataSource.load() ?: return
    println("State: ${state.stage.value} | step: ${state.currentStep} | next: ${state.expectedAction}")
}

private fun printInstruction(userId: String) {
    println("Чат с OpenAI API")
    println("Модель: $LLM_VERSION")
    println("User ID: $userId")
    println()
    println("Команды:")
    println("/exit  — выйти")
    println("/clear — очистить историю")
    println()
}

private fun readUserId(): String {
    while (true) {
        print("Введите user id [$DEFAULT_USER_ID]: ")
        val raw = readlnOrNull()?.trim().orEmpty()
        val userId = raw.ifBlank { DEFAULT_USER_ID }
        if (USER_ID_REGEX.matches(userId)) return userId
        println("Некорректный user id. Разрешены только буквы, цифры, '_' и '-'.")
    }
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
