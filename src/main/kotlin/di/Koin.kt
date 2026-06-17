package com.artembotnev.openai.di

import com.artembotnev.openai.data.context.MarkdownTaskNotesDataSource
import com.artembotnev.openai.data.context.MarkdownUserProfileContextDataSource
import com.artembotnev.openai.data.context.TaskNotesDataSource
import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.data.history.AgentHistoryDataSource
import com.artembotnev.openai.data.history.AgentHistoryRepositoryJson
import com.artembotnev.openai.data.history.JsonAgentHistoryDataSource
import com.artembotnev.openai.data.task.CurrentTaskDataSource
import com.artembotnev.openai.data.task.JsonCurrentTaskDataSource
import com.artembotnev.openai.domain.ContextStorageConfig
import com.artembotnev.openai.domain.HistoryStorageConfig
import com.artembotnev.openai.domain.context.TaskNotesMemoryUpdater
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.repository.AgentHistoryRepository
import com.artembotnev.openai.domain.task.CurrentTaskMemoryUpdater
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.SaveHistoryUseCase
import com.artembotnev.openai.infra.openai.OpenAiLlm
import com.artembotnev.openai.infra.task.OpenAiCurrentTaskMemoryUpdater
import com.artembotnev.openai.infra.profile.OpenAiUserProfileMemoryUpdater
import com.artembotnev.openai.infra.task.OpenAiTaskNotesMemoryUpdater
import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.dsl.module

fun storageModule(userId: String) = module {
    single { Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false } }
    single { FileSystem.SYSTEM }

    single {
        HistoryStorageConfig(
            baseDir = (System.getProperty("user.home") + "/IdeaProjects/OpenAiFirst/generated").toPath(),
            userId = userId,
        )
    }
    single {
        ContextStorageConfig(
            baseDir = (System.getProperty("user.home") + "/IdeaProjects/OpenAiFirst/context_source").toPath(),
            userId = userId,
        )
    }

    single<AgentHistoryDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonAgentHistoryDataSource(
            baseDir = cfg.userDir,
            fileName = cfg.fileName,
            fs = get(),
            json = get()
        )
    }
    single<AgentHistoryRepository> { AgentHistoryRepositoryJson(get()) }

    single<UserProfileContextDataSource> {
        val cfg = get<ContextStorageConfig>()
        MarkdownUserProfileContextDataSource(
            fs = get(),
            path = cfg.userProfilePath,
            userId = cfg.userId,
        )
    }
    single<TaskNotesDataSource> {
        val cfg = get<ContextStorageConfig>()
        MarkdownTaskNotesDataSource(
            fs = get(),
            path = cfg.taskContextPath,
        )
    }
    single<CurrentTaskDataSource> {
        val cfg = get<HistoryStorageConfig>()
        JsonCurrentTaskDataSource(
            fs = get(),
            path = cfg.currentTaskPath,
            json = get(),
        )
    }

    factory { LoadHistoryUseCase(get()) }
    factory { SaveHistoryUseCase(get()) }
    factory { AppendMessageUseCase(get()) }
    factory { ClearHistoryUseCase(get()) }
}

fun llmModule(model: String) = module {
    single<OpenAIClient> { OpenAIOkHttpClient.fromEnv() }

    single<UserProfileMemoryUpdater> {
        OpenAiUserProfileMemoryUpdater(
            model = model,
            client = get(),
            userProfileDataSource = get(),
        )
    }
    single<TaskNotesMemoryUpdater> {
        OpenAiTaskNotesMemoryUpdater(
            model = model,
            client = get(),
            taskNotesDataSource = get(),
        )
    }
    single<CurrentTaskMemoryUpdater> {
        OpenAiCurrentTaskMemoryUpdater(
            model = model,
            client = get(),
            currentTaskDataSource = get(),
            taskNotesDataSource = get(),
        )
    }

    single<Llm> {
        OpenAiLlm(
            model = model,
            client = get(),
            append = get(),
            load = get(),
            clear = get(),
            userProfileDataSource = get(),
            currentTaskDataSource = get(),
            userProfileMemoryUpdater = get(),
            taskNotesMemoryUpdater = get(),
            currentTaskMemoryUpdater = get(),
        )
    }
}
