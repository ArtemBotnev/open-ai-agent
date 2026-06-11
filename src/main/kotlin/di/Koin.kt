package com.artembotnev.openai.di

import com.artembotnev.openai.domain.HistoryStorageConfig
import com.artembotnev.openai.data.history.AgentHistoryDataSource
import com.artembotnev.openai.data.history.AgentHistoryRepositoryJson
import com.artembotnev.openai.data.dialogusage.DialogUsageDataSource
import com.artembotnev.openai.data.dialogusage.DialogUsageRepositoryJson
import com.artembotnev.openai.data.history.JsonAgentHistoryDataSource
import com.artembotnev.openai.data.dialogusage.JsonDialogUsageDataSource
import com.artembotnev.openai.data.summary.ConversationSummaryDataSource
import com.artembotnev.openai.data.summary.ConversationSummaryRepositoryJson
import com.artembotnev.openai.data.summary.JsonConversationSummaryDataSource
import com.artembotnev.openai.domain.repository.AgentHistoryRepository
import com.artembotnev.openai.domain.repository.ConversationSummaryRepository
import com.artembotnev.openai.domain.repository.DialogUsageRepository
import com.artembotnev.openai.domain.usecase.history.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.ClearDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.LoadDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.SaveDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.history.SaveHistoryUseCase
import com.artembotnev.openai.domain.usecase.summary.ClearConversationSummaryUseCase
import com.artembotnev.openai.domain.usecase.summary.LoadConversationSummaryUseCase
import com.artembotnev.openai.domain.usecase.summary.SaveConversationSummaryUseCase
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

val historyModule = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false } }
    single { FileSystem.SYSTEM }

    single { HistoryStorageConfig(baseDir = (System.getProperty("user.home") + "/IdeaProjects/OpenAiFirst/generated").toPath()) }

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

    single<DialogUsageDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonDialogUsageDataSource(
            baseDir = cfg.baseDir,
            fs = get(),
            json = get()
        )
    }
    single<DialogUsageRepository> { DialogUsageRepositoryJson(get()) }

    single<ConversationSummaryDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonConversationSummaryDataSource(
            baseDir = cfg.baseDir,
            fs = get(),
            json = get()
        )
    }
    single<ConversationSummaryRepository> { ConversationSummaryRepositoryJson(get()) }

    factory { LoadHistoryUseCase(get()) }
    factory { SaveHistoryUseCase(get()) }
    factory { AppendMessageUseCase(get()) }
    factory { ClearHistoryUseCase(get()) }

    factory { LoadDialogUsageUseCase(get()) }
    factory { SaveDialogUsageUseCase(get()) }
    factory { ClearDialogUsageUseCase(get()) }

    factory { LoadConversationSummaryUseCase(get()) }
    factory { SaveConversationSummaryUseCase(get()) }
    factory { ClearConversationSummaryUseCase(get()) }
}