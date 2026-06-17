package com.artembotnev.openai.infra.openai

import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.data.task.CurrentTaskDataSource
import com.artembotnev.openai.domain.context.TaskNotesMemoryUpdater
import com.artembotnev.openai.domain.context.UserProfileMemoryUpdater
import com.artembotnev.openai.domain.context.UserProfileContext
import com.artembotnev.openai.domain.llm.Llm
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentResult
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.task.CurrentTaskMemoryUpdater
import com.artembotnev.openai.domain.task.TaskContext
import com.artembotnev.openai.domain.task.TaskStateMachine
import com.artembotnev.openai.domain.usecase.AppendMessageUseCase
import com.artembotnev.openai.domain.usecase.ClearHistoryUseCase
import com.artembotnev.openai.domain.usecase.LoadHistoryUseCase
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import kotlin.time.measureTimedValue

internal class OpenAiLlm(
    private val model: String,
    private val client: OpenAIClient,
    private val append: AppendMessageUseCase,
    private val load: LoadHistoryUseCase,
    private val clear: ClearHistoryUseCase,
    private val userProfileDataSource: UserProfileContextDataSource,
    private val currentTaskDataSource: CurrentTaskDataSource,
    private val userProfileMemoryUpdater: UserProfileMemoryUpdater,
    private val taskNotesMemoryUpdater: TaskNotesMemoryUpdater,
    private val currentTaskMemoryUpdater: CurrentTaskMemoryUpdater,
) : Llm {

    override fun send(userText: String): AgentResult {
        val currentTask = prepareCurrentTaskForTurn(userText)

        val userMessage = AgentMessage(
            role = AgentRole.USER,
            content = userText
        )

        append(userMessage)

        val params = buildParams(
            userText = userText,
            currentTask = currentTask,
        )
        val result = requestOpenAi(params)

        append(
            AgentMessage(
                role = AgentRole.ASSISTANT,
                content = result.answer
            )
        )

        runCatching {
            userProfileMemoryUpdater.captureDialogue(
                userText = userText,
                assistantText = result.answer,
            )
        }
        runCatching {
            taskNotesMemoryUpdater.captureDialogue(
                userText = userText,
                assistantText = result.answer,
            )
        }
        runCatching {
            currentTaskMemoryUpdater.captureDialogue(
                userText = userText,
                assistantText = result.answer,
            )
        }

        return result
    }

    override fun clearHistory() {
        clear()
        currentTaskDataSource.clear()
    }

    override fun getHistory(): List<AgentMessage> {
        return load()
    }

    private fun requestOpenAi(params: ChatCompletionCreateParams): AgentResult {
        val (completion, duration) = measureTimedValue {
            client.chat()
                .completions()
                .create(params)
        }

        val answer = completion.choices()
            .firstOrNull()
            ?.message()
            ?.content()
            ?.orElse(null)
            ?.trim()
            ?: error("Пустой ответ от модели")

        val usage = completion.usage().orElse(null)

        return AgentResult(
            answer = answer,
            promptTokens = usage?.promptTokens(),
            completionTokens = usage?.completionTokens(),
            totalTokens = usage?.totalTokens(),
            elapsedSec = duration.inWholeSeconds
        )
    }

    private fun buildParams(
        userText: String,
        currentTask: TaskContext,
    ): ChatCompletionCreateParams {
        val builder = ChatCompletionCreateParams.builder()
            .model(model)

        val profile = userProfileDataSource.load()
        builder.addSystemMessage(RESPONSE_SYSTEM_PROMPT)
        builder.addSystemMessage(buildStageInstruction(currentTask))
        builder.addSystemMessage(
            buildPrompt(
                query = userText,
                ctx = currentTask,
                profile = profile,
            )
        )

        val history = getHistory().filter { it.role != AgentRole.SYSTEM }
        for (message in history) {
            when (message.role) {
                AgentRole.USER -> builder.addUserMessage(message.content)
                AgentRole.ASSISTANT -> builder.addAssistantMessage(message.content)
                AgentRole.SYSTEM -> Unit
            }
        }

        return builder.build()
    }

    private fun prepareCurrentTaskForTurn(userText: String): TaskContext {
        val current = currentTaskDataSource.load()

        if (current == null) {
            return TaskStateMachine.start(userText).also(currentTaskDataSource::save)
        }

        if (startsExplicitNewTask(userText)) {
            return TaskStateMachine.start("Уточнить новую задачу").also(currentTaskDataSource::save)
        }

        if (current.state == com.artembotnev.openai.domain.task.AgentState.DONE &&
            isStandaloneRequest(userText) &&
            TaskStateMachine.isNewTask(current, userText)
        ) {
            return TaskStateMachine.start(userText).also(currentTaskDataSource::save)
        }

        if (current.state == com.artembotnev.openai.domain.task.AgentState.PLANNING &&
            planReady(current) &&
            userRequestedImplementation(userText)
        ) {
            return current.copy(
                state = com.artembotnev.openai.domain.task.AgentState.EXECUTION,
            ).also(currentTaskDataSource::save)
        }

        return current
    }

    private fun buildPrompt(
        query: String,
        ctx: TaskContext,
        profile: UserProfileContext,
    ): String {
        return """
            Internal agent context. Do not expose these fields to the user unless they explicitly ask about internal progress or plan state.
            step/total means progress through the real task plan, not options or alternatives.
            PLAN is the committed sequence of real work steps required to finish the task.
            CURRENT is the active real step right now.
            The current stage defines what kind of answer is allowed.
            PLAN_READY ${planReady(ctx)}
            IMPLEMENTATION_REQUESTED ${userRequestedImplementation(query)}
            [STATE] ${ctx.state.value}, step ${ctx.step}/${ctx.total}
            [CURRENT] ${ctx.current}
            [PLAN] ${ctx.plan.joinToString()}
            [DONE] ${ctx.done.joinToString()}
            [PROFILE] ${formatProfile(profile)}
            [QUERY] $query
        """.trimIndent()
    }

    private fun buildStageInstruction(ctx: TaskContext): String {
        return when (ctx.state) {
            com.artembotnev.openai.domain.task.AgentState.PLANNING -> """
                Current internal stage: planning.
                The plan is a sequence of real task steps, not a menu of options.
                step/total is the current real step within that plan.
                Hard rule: in planning you must not write code, diffs, patches, file contents, or implementation artifacts.
                Hard rule: do not write code until the user explicitly asks for implementation and the task has moved to execution.
                Your job in this reply is only to clarify requirements and produce or refine that sequential plan.
                Allowed output in this stage: clarification questions, constraints, assumptions, and the ordered plan.
                Forbidden output in this stage: code blocks, pseudo-code, implementation snippets, file diffs, or final solution text.
                If information is missing, ask only the blocking clarification questions.
                If information is sufficient, present or refine the real step-by-step plan and ask for confirmation to start implementation.
                If the user asks for code while the task is still in planning, do not write code. Briefly say that implementation starts only after planning is complete, then continue with requirements or plan only.
                Stop after requirements and plan. Do not start implementation in the same reply.
                Do not mention the words planning, execution, validation, or done unless the user explicitly asks about internal stages.
            """.trimIndent()
            com.artembotnev.openai.domain.task.AgentState.EXECUTION -> """
                Current internal stage: execution.
                The plan items are real sequential task steps, not alternatives.
                Hard rule: in execution you must produce the concrete implementation artifact for the current step.
                Execution is allowed only because the user explicitly requested implementation.
                Implement the current step and produce code, diff, patch, file content, or another concrete artifact the user asked for.
                Only go back to planning if you are truly blocked by missing requirements.
                After implementation, immediately perform validation in the same reply.
                Validation must be a distinct short block that starts with "Проверка:".
                If validation finds a problem, explicitly state the defect and what must be fixed.
                If validation passes, finish with a short block that starts with "Итог:" and gives a concise completion report.
                Do not ask the user to perform validation for you.
                Do not stop after code only. Execution reply must include implementation and then validation.
                Do not mention the internal stage names unless the user explicitly asks.
            """.trimIndent()
            com.artembotnev.openai.domain.task.AgentState.VALIDATION -> """
                Current internal stage: validation.
                The plan items are real sequential task steps, not alternatives.
                Hard rule: validation is separate from implementation.
                Do not produce new code unless validation reveals a concrete defect that must be fixed.
                Validate the existing result against the plan, tests, and constraints.
                The validation block must start with "Проверка:".
                If validation fails, clearly state what failed and what must be corrected.
                If validation passes, finish with a short line starting with "Итог:" describing the completed result.
                If validation passes, the task should be considered complete.
                Do not mention the internal stage names unless the user explicitly asks.
            """.trimIndent()
            com.artembotnev.openai.domain.task.AgentState.DONE -> """
                Current internal stage: done.
                The task is already completed.
                Hard rule: done contains no new planning and no new implementation.
                Give a concise completion summary, highlight the delivered result, and mention residual caveats only if they matter.
                The answer should read like a short completion report.
                Do not reopen planning or implementation unless the user asks for a new change.
                Do not mention the internal stage names unless the user explicitly asks.
            """.trimIndent()
        }
    }

    private fun startsExplicitNewTask(userText: String): Boolean {
        val normalized = userText.lowercase()
        return listOf(
            "новая задача",
            "другая задача",
            "следующая задача",
            "теперь новая задача",
            "у нас новая задача",
        ).any { normalized.contains(it) }
    }

    private fun isStandaloneRequest(userText: String): Boolean {
        val normalized = userText.trim().lowercase()
        if (normalized.length < 12) return false

        return listOf(
            "продолж",
            "дальше",
            "покажи",
            "объясни",
            "исправь",
            "добавь",
            "еще",
            "ещё",
            "давай",
            "ок",
            "хорошо",
            "да",
            "нет",
        ).none { normalized.startsWith(it) }
    }

    private fun planReady(task: TaskContext): Boolean {
        return task.plan.isNotEmpty() && task.total > 0
    }

    private fun userRequestedImplementation(userText: String): Boolean {
        val normalized = userText.lowercase()
        return implementationRequestMarkers.any { normalized.contains(it) }
    }

    private fun formatProfile(profile: UserProfileContext): String {
        return buildString {
            append("userId=${profile.userId}; ")
            append("role=${profile.role}; ")
            append("stack=${profile.stack.joinToString()}; ")
            append("knowledge=${profile.knowledge.joinToString()}; ")
            append("constraints=${profile.constraints.joinToString()}; ")
            append("decisions=${profile.decisions.joinToString()}")
        }
    }

    private companion object {
        const val RESPONSE_SYSTEM_PROMPT = """
            You are an agent working with an internal task state machine.
            The current stage is authoritative and must determine how you answer.
            The task stages, plan, done items, and current step are internal execution metadata for the agent.
            The plan always describes real sequential task steps, never options, alternatives, or brainstorming ideas.
            Non-negotiable stage rules:
            1. planning: gather requirements and refine the plan; no code is allowed.
            2. execution: write the code or concrete artifact for the current step only after the user explicitly requested implementation.
            3. after execution, validation must happen.
            4. if validation passes, finish with done semantics and a short completion report.
            5. if the current stage is planning, do not write code even if the user asks for it in that same reply.
            6. user requests for code or implementation are a trigger to move from planning to execution only when the plan is ready.
            Do not narrate or expose internal state-machine stages to the user unless the user explicitly asks for them.
            Follow the current stage behavior strictly and answer naturally inside those constraints.
        """

        val implementationRequestMarkers = listOf(
            "напиши код",
            "покажи код",
            "реализуй",
            "реализацию",
            "реализацию",
            "сделай реализацию",
            "выполни реализацию",
            "внеси изменения в код",
            "исправь код",
            "создай файл",
            "создай класс",
            "создай функцию",
            "напиши функцию",
            "напиши класс",
            "напиши запрос",
            "write code",
            "show code",
            "implement",
            "implementation",
            "create file",
            "create class",
            "create function",
            "write request",
            "write api request",
        )
    }
}
