package com.artembotnev.openai.di

import com.artembotnev.openai.domain.HistoryStorageConfig
import com.artembotnev.openai.domain.data.AgentHistoryDataSource
import com.artembotnev.openai.domain.data.AgentHistoryRepositoryJson
import com.artembotnev.openai.domain.data.JsonAgentHistoryDataSource
import com.artembotnev.openai.domain.repository.AgentHistoryRepository
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.SaveHistoryUseCase
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

val historyModule = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false } }
    single { FileSystem.SYSTEM }

    single { HistoryStorageConfig(baseDir = (System.getProperty("user.home") + "/IdeaProjects/OpenAiFirst").toPath()) }

    single<AgentHistoryDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonAgentHistoryDataSource(
            baseDir = cfg.baseDir,
            fileName = cfg.fileName,
            fs = get(),
            json = get()
        )
    }
    single<AgentHistoryRepository> { AgentHistoryRepositoryJson(get()) }

    factory { LoadHistoryUseCase(get()) }
    factory { SaveHistoryUseCase(get()) }
    factory { AppendMessageUseCase(get()) }
    factory { ClearHistoryUseCase(get()) }
}