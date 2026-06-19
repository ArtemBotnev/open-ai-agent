package com.artembotnev.openai.di

import com.artembotnev.openai.data.context.MarkdownTaskContextDataSource
import com.artembotnev.openai.data.context.MarkdownInvariantDataSource
import com.artembotnev.openai.data.context.MarkdownUserProfileContextDataSource
import com.artembotnev.openai.data.context.InvariantDataSource
import com.artembotnev.openai.data.context.TaskContextDataSource
import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.data.history.AgentHistoryDataSource
import com.artembotnev.openai.data.history.AgentHistoryRepositoryJson
import com.artembotnev.openai.data.history.JsonAgentHistoryDataSource
import com.artembotnev.openai.data.workflow.JsonTaskWorkflowStateDataSource
import com.artembotnev.openai.data.workflow.TaskWorkflowStateDataSource
import com.artembotnev.openai.domain.ContextStorageConfig
import com.artembotnev.openai.domain.HistoryStorageConfig
import com.artembotnev.openai.domain.TaskStateStorageConfig
import com.artembotnev.openai.domain.context.TaskContextMemoryUpdater
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.repository.AgentHistoryRepository
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.artembotnev.openai.domain.usecase.SaveHistoryUseCase
import com.artembotnev.openai.infra.openai.OpenAiLlm
import com.artembotnev.openai.infra.openai.workflow.DoneSubAgent
import com.artembotnev.openai.infra.openai.workflow.ExecutionSubAgent
import com.artembotnev.openai.infra.openai.workflow.OpenAiWorkflowCoordinator
import com.artembotnev.openai.infra.openai.workflow.OpenAiWorkflowSupport
import com.artembotnev.openai.infra.openai.workflow.ArchitecturePlanningSubAgent
import com.artembotnev.openai.infra.openai.workflow.DeveloperPlanningSubAgent
import com.artembotnev.openai.infra.openai.workflow.DesignerPlanningSubAgent
import com.artembotnev.openai.infra.openai.workflow.PlanningSubAgent
import com.artembotnev.openai.infra.openai.workflow.RequirementsPlanningSubAgent
import com.artembotnev.openai.infra.openai.workflow.ValidationSubAgent
import com.artembotnev.openai.infra.profile.OpenAiUserProfileMemoryUpdater
import com.artembotnev.openai.infra.task.OpenAiTaskContextMemoryUpdater
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
    single {
        TaskStateStorageConfig(
            baseDir = (System.getProperty("user.home") + "/IdeaProjects/OpenAiFirst/generated").toPath(),
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
    single<TaskWorkflowStateDataSource> {
        val cfg = get<TaskStateStorageConfig>()
        JsonTaskWorkflowStateDataSource(
            baseDir = cfg.userDir,
            fileName = cfg.fileName,
            fs = get(),
            json = get(),
        )
    }

    single<UserProfileContextDataSource> {
        val cfg = get<ContextStorageConfig>()
        MarkdownUserProfileContextDataSource(
            fs = get(),
            path = cfg.userProfilePath,
            userId = cfg.userId,
        )
    }
    single<TaskContextDataSource> {
        val cfg = get<ContextStorageConfig>()
        MarkdownTaskContextDataSource(
            fs = get(),
            path = cfg.taskContextPath,
        )
    }
    single<InvariantDataSource> {
        val cfg = get<ContextStorageConfig>()
        MarkdownInvariantDataSource(
            fs = get(),
            path = cfg.invariantPath,
        )
    }

    factory { LoadHistoryUseCase(get()) }
    factory { SaveHistoryUseCase(get()) }
    factory { AppendMessageUseCase(get()) }
    factory { ClearHistoryUseCase(get()) }
}

fun llmModule(model: String) = module {
    single<OpenAIClient> { OpenAIOkHttpClient.fromEnv() }
    single { OpenAiWorkflowSupport(model = model, client = get(), json = get()) }
    single { RequirementsPlanningSubAgent(get()) }
    single { ArchitecturePlanningSubAgent(get()) }
    single { DeveloperPlanningSubAgent(get()) }
    single { DesignerPlanningSubAgent(get()) }
    single { PlanningSubAgent(get(), get(), get(), get(), get()) }
    single { ExecutionSubAgent(get()) }
    single { ValidationSubAgent(get()) }
    single { DoneSubAgent(get()) }
    single {
        OpenAiWorkflowCoordinator(
            workflowStateDataSource = get(),
            userProfileDataSource = get(),
            taskContextDataSource = get(),
            invariantDataSource = get(),
            planningSubAgent = get(),
            executionSubAgent = get(),
            validationSubAgent = get(),
            doneSubAgent = get(),
        )
    }

    single<UserProfileMemoryUpdater> {
        OpenAiUserProfileMemoryUpdater(
            model = model,
            client = get(),
            userProfileDataSource = get(),
        )
    }
    single<TaskContextMemoryUpdater> {
        OpenAiTaskContextMemoryUpdater(
            model = model,
            client = get(),
            taskContextDataSource = get(),
        )
    }

    single<Llm> {
        OpenAiLlm(
            append = get(),
            load = get(),
            clear = get(),
            workflowCoordinator = get(),
            userProfileMemoryUpdater = get(),
            taskContextMemoryUpdater = get(),
        )
    }
}
