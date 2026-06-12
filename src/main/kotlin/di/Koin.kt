package com.artembotnev.openai.di

import com.artembotnev.openai.data.dialogusage.DialogUsageDataSource
import com.artembotnev.openai.data.dialogusage.DialogUsageRepositoryJson
import com.artembotnev.openai.data.dialogusage.JsonDialogUsageDataSource
import com.artembotnev.openai.data.facts.DialogFactsDataSource
import com.artembotnev.openai.data.facts.DialogFactsRepositoryJson
import com.artembotnev.openai.data.facts.JsonDialogFactsDataSource
import com.artembotnev.openai.data.history.AgentHistoryDataSource
import com.artembotnev.openai.data.history.AgentHistoryRepositoryJson
import com.artembotnev.openai.data.history.JsonAgentHistoryDataSource
import com.artembotnev.openai.domain.HistoryStorageConfig
import com.artembotnev.openai.domain.context.BranchingPromptContextStrategy
import com.artembotnev.openai.domain.context.ContextManagementStrategy
import com.artembotnev.openai.domain.context.ContextStrategyType
import com.artembotnev.openai.domain.context.NoopContextManagementStrategy
import com.artembotnev.openai.domain.context.PromptContextStrategy
import com.artembotnev.openai.domain.context.RecentHistoryPromptContextStrategy
import com.artembotnev.openai.domain.context.SlidingWindowContextManagementStrategy
import com.artembotnev.openai.domain.context.StickyFactsPromptContextStrategy
import com.artembotnev.openai.domain.repository.AgentHistoryRepository
import com.artembotnev.openai.domain.repository.DialogFactsRepository
import com.artembotnev.openai.domain.repository.DialogUsageRepository
import com.artembotnev.openai.domain.usecase.dialogusage.ClearDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.LoadDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.dialogusage.SaveDialogUsageUseCase
import com.artembotnev.openai.domain.usecase.facts.ClearDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.facts.LoadDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.facts.SaveDialogFactsUseCase
import com.artembotnev.openai.domain.usecase.history.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.history.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.history.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.history.SaveHistoryUseCase
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import org.koin.dsl.module

internal fun historyModule(
    strategyType: ContextStrategyType,
    storageBaseDir: Path,
    activeBranch: String? = null,
) = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false } }
    single { FileSystem.SYSTEM }
    single<ContextManagementStrategy> {
        when (strategyType) {
            ContextStrategyType.BRANCHING -> NoopContextManagementStrategy
            else -> SlidingWindowContextManagementStrategy(maxMessages = strategyType.recentMessagesCount)
        }
    }
    single<PromptContextStrategy> {
        when (strategyType) {
            ContextStrategyType.SLIDING_WINDOW -> {
                RecentHistoryPromptContextStrategy(maxRecentMessages = strategyType.recentMessagesCount)
            }

            ContextStrategyType.STICKY_FACTS -> {
                StickyFactsPromptContextStrategy(maxRecentMessages = strategyType.recentMessagesCount)
            }

            ContextStrategyType.BRANCHING -> BranchingPromptContextStrategy(activeBranch = activeBranch)
        }
    }

    single { HistoryStorageConfig(baseDir = storageBaseDir) }

    single<AgentHistoryDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonAgentHistoryDataSource(
            baseDir = cfg.baseDir,
            fileName = cfg.fileName,
            fs = get(),
            json = get()
        )
    }
    single<AgentHistoryRepository> { AgentHistoryRepositoryJson(get(), get()) }

    single<DialogFactsDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonDialogFactsDataSource(
            baseDir = cfg.baseDir,
            fs = get(),
            json = get()
        )
    }
    single<DialogFactsRepository> { DialogFactsRepositoryJson(get()) }

    single<DialogUsageDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonDialogUsageDataSource(
            baseDir = cfg.baseDir,
            fs = get(),
            json = get()
        )
    }
    single<DialogUsageRepository> { DialogUsageRepositoryJson(get()) }

    factory { LoadHistoryUseCase(get()) }
    factory { SaveHistoryUseCase(get()) }
    factory { AppendMessageUseCase(get()) }
    factory { ClearHistoryUseCase(get()) }

    factory { LoadDialogFactsUseCase(get()) }
    factory { SaveDialogFactsUseCase(get()) }
    factory { ClearDialogFactsUseCase(get()) }

    factory { LoadDialogUsageUseCase(get()) }
    factory { SaveDialogUsageUseCase(get()) }
    factory { ClearDialogUsageUseCase(get()) }
}
