package com.artembotnev.openai.infra.openai.workflow

import com.artembotnev.openai.data.context.TaskContextDataSource
import com.artembotnev.openai.data.context.InvariantDataSource
import com.artembotnev.openai.data.context.UserProfileContextDataSource
import com.artembotnev.openai.data.workflow.TaskWorkflowStateDataSource
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.workflow.TaskWorkflowState
import com.artembotnev.openai.domain.workflow.WorkflowActor
import com.artembotnev.openai.domain.workflow.WorkflowState

internal class OpenAiWorkflowCoordinator(
    private val workflowStateDataSource: TaskWorkflowStateDataSource,
    private val userProfileDataSource: UserProfileContextDataSource,
    private val taskContextDataSource: TaskContextDataSource,
    private val invariantDataSource: InvariantDataSource,
    private val planningSubAgent: PlanningSubAgent,
    private val executionSubAgent: ExecutionSubAgent,
    private val validationSubAgent: ValidationSubAgent,
    private val doneSubAgent: DoneSubAgent,
) {

    fun handle(userText: String, history: List<AgentMessage>): WorkflowResponse {
        val baseContext = baseContext(userText, history)
        var state = workflowStateDataSource.load().normalized()
        state = prepareStateForNewMessage(state, userText)
        state = state.addConversationStep(WorkflowActor.USER, userText)
        state = persist(state)
        logStep("USER", state.state, userText)

        var totalPromptTokens = 0L
        var totalCompletionTokens = 0L
        var totalTokens = 0L
        var hasUsage = false

        fun accumulate(usage: WorkflowUsage) {
            val sumPrompt = usage.promptTokens
            val sumCompletion = usage.completionTokens
            val sumTotal = usage.totalTokens
            if (sumPrompt != null) {
                totalPromptTokens += sumPrompt
                hasUsage = true
            }
            if (sumCompletion != null) {
                totalCompletionTokens += sumCompletion
                hasUsage = true
            }
            if (sumTotal != null) {
                totalTokens += sumTotal
                hasUsage = true
            }
        }

        var revisionRequest = state.pendingRevisionRequest
        var validationAttempts = state.validationAttempts
        var finalAnswer = ""

        repeat(MAX_WORKFLOW_ITERATIONS) {
            val context = baseContext.copy(state = state)
            when (state.state) {
                WorkflowState.PLANNING -> {
                    if (PlanApprovalDecider.isExplicitApproval(userText, state)) {
                        state = persist(
                            transition(
                                state.withRuntimeState(revisionRequest = "", attempts = 0),
                                WorkflowState.EXECUTION,
                                "Пользователь явно утвердил предложенный план",
                            ).addAgentHandoff(
                                fromActor = WorkflowActor.PLANNING_AGENT,
                                toActor = WorkflowActor.EXECUTION_AGENT,
                                fromState = WorkflowState.PLANNING,
                                toState = WorkflowState.EXECUTION,
                                message = "Пользователь явно утвердил предложенный план.",
                            )
                        )
                        return@repeat
                    }

                    val result = planningSubAgent.run(context)
                    accumulate(result.usage)
                    revisionRequest = ""
                    validationAttempts = 0
                    val planningReply = ensurePlanApprovalQuestion(
                        reply = result.reply,
                        awaitingConfirmation = result.awaitingConfirmation,
                        plan = result.plan,
                    )
                    state = persist(
                        state
                            .applyPlan(
                                taskName = result.task.ifBlank { guessTaskTitle(userText, state) },
                                newPlan = result.plan,
                                currentItem = result.current,
                                waitingForConfirmation = result.awaitingConfirmation,
                            )
                            .withRuntimeState(
                                revisionRequest = revisionRequest,
                                attempts = validationAttempts,
                            )
                            .recordStageResult(
                                actor = WorkflowActor.PLANNING_AGENT,
                                stage = WorkflowState.PLANNING,
                                summary = planningReply,
                                details = planningDetails(result),
                            )
                            .addConversationStep(WorkflowActor.PLANNING_AGENT, planningReply, WorkflowState.PLANNING)
                    )
                    logStep("PLANNING", WorkflowState.PLANNING, planningReply)

                    if (result.invariantConflicts.isNotEmpty()) {
                        finalAnswer = planningReply.ifBlank {
                            buildString {
                                appendLine("Запрос конфликтует с правилами пользователя.")
                                result.invariantConflicts.forEach { appendLine("- $it") }
                            }.trim()
                        }
                        return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
                    }

                    finalAnswer = planningReply
                    return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
                }

                WorkflowState.EXECUTION -> {
                    val result = executionSubAgent.run(context, revisionRequest)
                    accumulate(result.usage)
                    state = persist(
                        state
                            .recordStageResult(
                                actor = WorkflowActor.EXECUTION_AGENT,
                                stage = WorkflowState.EXECUTION,
                                summary = result.reply.ifBlank { "Execution выполнил генерацию артефакта." },
                                details = result.completedSteps,
                                artifactPreview = result.artifact,
                            )
                            .addConversationStep(
                                WorkflowActor.EXECUTION_AGENT,
                                result.reply.ifBlank { "Execution выполнил генерацию артефакта." },
                                WorkflowState.EXECUTION,
                            )
                    )
                    logStep("EXECUTION", WorkflowState.EXECUTION, result.reply.ifBlank { "Артефакт сгенерирован" })

                    if (result.needsClarification) {
                        revisionRequest = ""
                        validationAttempts = 0
                        state = persist(
                            transition(
                                state
                                    .applyExecutionResult(
                                        draft = "",
                                        completedSteps = emptyList(),
                                        currentItem = result.current,
                                    )
                                    .withRuntimeState(
                                        revisionRequest = revisionRequest,
                                        attempts = validationAttempts,
                                    ),
                                WorkflowState.PLANNING,
                                "Execution запросил дополнительные уточнения",
                            ).addAgentHandoff(
                                fromActor = WorkflowActor.EXECUTION_AGENT,
                                toActor = WorkflowActor.PLANNING_AGENT,
                                fromState = WorkflowState.EXECUTION,
                                toState = WorkflowState.PLANNING,
                                message = result.reply.ifBlank { "Нужны дополнительные уточнения от пользователя." },
                            )
                        )
                        finalAnswer = result.reply.ifBlank { "Нужны уточнения по задаче перед реализацией." }
                        return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
                    }

                    revisionRequest = ""
                    state = persist(
                        state
                            .applyExecutionResult(
                                draft = result.artifact,
                                completedSteps = result.completedSteps,
                                currentItem = result.current,
                            )
                            .withRuntimeState(
                                revisionRequest = revisionRequest,
                                attempts = validationAttempts,
                            )
                    )
                    state = persist(
                        transition(state, WorkflowState.VALIDATION, "Код подготовлен и отправлен на валидацию")
                            .addAgentHandoff(
                                fromActor = WorkflowActor.EXECUTION_AGENT,
                                toActor = WorkflowActor.VALIDATION_AGENT,
                                fromState = WorkflowState.EXECUTION,
                                toState = WorkflowState.VALIDATION,
                                message = result.reply.ifBlank { "Артефакт передан на валидацию." },
                            )
                    )
                }

                WorkflowState.VALIDATION -> {
                    val result = validationSubAgent.run(context, state.lastDraft)
                    accumulate(result.usage)
                    state = persist(
                        state
                            .applyValidationResult(result.reply, result.issues)
                            .recordStageResult(
                                actor = WorkflowActor.VALIDATION_AGENT,
                                stage = WorkflowState.VALIDATION,
                                summary = result.reply,
                                details = result.issues,
                            )
                            .addConversationStep(WorkflowActor.VALIDATION_AGENT, result.reply, WorkflowState.VALIDATION)
                    )
                    logStep("VALIDATION", WorkflowState.VALIDATION, result.reply)

                    if (!result.valid) {
                        validationAttempts++
                        revisionRequest = result.revisionRequest.ifBlank {
                            result.issues.joinToString(separator = "; ")
                        }
                        state = persist(
                            transition(
                                state.withRuntimeState(
                                    revisionRequest = revisionRequest,
                                    attempts = validationAttempts,
                                ),
                                WorkflowState.EXECUTION,
                                "Валидация вернула замечания",
                            ).addAgentHandoff(
                                fromActor = WorkflowActor.VALIDATION_AGENT,
                                toActor = WorkflowActor.EXECUTION_AGENT,
                                fromState = WorkflowState.VALIDATION,
                                toState = WorkflowState.EXECUTION,
                                message = revisionRequest,
                            )
                        )

                        if (validationAttempts >= MAX_VALIDATION_RETRIES) {
                            finalAnswer = buildString {
                                appendLine("Решение не прошло внутреннюю валидацию.")
                                appendLine(result.reply)
                                if (result.issues.isNotEmpty()) {
                                    appendLine()
                                    appendLine("Что нужно исправить:")
                                    result.issues.forEach { appendLine("- $it") }
                                }
                            }.trim()
                            return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
                        }
                        return@repeat
                    }

                    revisionRequest = ""
                    validationAttempts = 0
                    state = persist(
                        transition(
                            state.withRuntimeState(
                                revisionRequest = revisionRequest,
                                attempts = validationAttempts,
                            ),
                            WorkflowState.DONE,
                            "Решение прошло валидацию",
                        ).addAgentHandoff(
                            fromActor = WorkflowActor.VALIDATION_AGENT,
                            toActor = WorkflowActor.DONE_AGENT,
                            fromState = WorkflowState.VALIDATION,
                            toState = WorkflowState.DONE,
                            message = result.reply,
                        )
                    )
                }

                WorkflowState.DONE -> {
                    val result = doneSubAgent.run(context, state.lastDraft, state.lastValidationSummary)
                    accumulate(result.usage)
                    state = persist(
                        state
                            .withRuntimeState(revisionRequest = "", attempts = 0)
                            .withFinalReply(result.reply)
                            .recordStageResult(
                                actor = WorkflowActor.DONE_AGENT,
                                stage = WorkflowState.DONE,
                                summary = result.reply,
                            )
                            .addConversationStep(WorkflowActor.DONE_AGENT, result.reply, WorkflowState.DONE)
                    )
                    logStep("DONE", WorkflowState.DONE, result.reply)
                    finalAnswer = result.reply
                    return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
                }
            }
        }

        workflowStateDataSource.save(state)
        finalAnswer = "Workflow остановлен по лимиту внутренних переходов. Уточни задачу или разбей её на меньшие шаги."
        return responseWithUsage(finalAnswer, hasUsage, totalPromptTokens, totalCompletionTokens, totalTokens)
    }

    fun clear() {
        workflowStateDataSource.clear()
    }

    private fun persist(state: TaskWorkflowState): TaskWorkflowState {
        workflowStateDataSource.save(state)
        return state
    }

    private fun baseContext(userText: String, history: List<AgentMessage>): WorkflowAgentContext {
        return WorkflowAgentContext(
            userText = userText,
            state = TaskWorkflowState(),
            userProfileMarkdown = userProfileDataSource.loadMarkdown().trim(),
            taskContextMarkdown = taskContextDataSource.loadMarkdown().trim(),
            invariant = invariantDataSource.load(),
            invariantMarkdown = invariantDataSource.loadMarkdown().trim(),
            history = history,
        )
    }

    private fun planningDetails(result: PlanningAgentResult): List<String> {
        return buildList {
            addAll(result.plan)
            addAll(result.candidateSummaries.map { "Кандидат: $it" })
            addAll(result.consideredInvariants.map { "Учтён инвариант: $it" })
            addAll(result.invariantConflicts.map { "Конфликт с инвариантом: $it" })
        }
    }

    private fun ensurePlanApprovalQuestion(
        reply: String,
        awaitingConfirmation: Boolean,
        plan: List<String>,
    ): String {
        val sanitized = reply.trim()
        if (!awaitingConfirmation || plan.isEmpty()) return sanitized

        val approvalQuestion = "Принимаем ли мы этот план в реализацию?"
        if (sanitized.contains(approvalQuestion)) return sanitized
        if (sanitized.isBlank()) return approvalQuestion

        return "$sanitized\n\n$approvalQuestion"
    }

    private fun prepareStateForNewMessage(state: TaskWorkflowState, userText: String): TaskWorkflowState {
        if (state.state != WorkflowState.DONE) return state

        val reset = transition(
            state,
            WorkflowState.PLANNING,
            "Поступило новое сообщение пользователя после завершения задачи",
        ).resetForNewCycle()
        return reset
            .applyPlan(
                taskName = guessTaskTitle(userText, reset),
                newPlan = emptyList(),
                currentItem = "",
                waitingForConfirmation = false,
            )
    }

    private fun guessTaskTitle(userText: String, state: TaskWorkflowState): String {
        return state.task.ifBlank {
            userText.lines()
                .firstOrNull { it.trim().isNotBlank() }
                ?.trim()
                ?.take(120)
                .orEmpty()
        }
    }

    private fun transition(
        state: TaskWorkflowState,
        nextState: WorkflowState,
        reason: String,
    ): TaskWorkflowState {
        val updated = state.transitionTo(nextState, reason)
        logTransition(state.state, nextState, reason, updated.step, updated.total)
        return updated
    }

    private fun responseWithUsage(
        answer: String,
        hasUsage: Boolean,
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
    ): WorkflowResponse {
        return WorkflowResponse(
            answer = answer,
            promptTokens = promptTokens.takeIf { hasUsage },
            completionTokens = completionTokens.takeIf { hasUsage },
            totalTokens = totalTokens.takeIf { hasUsage },
        )
    }

    private fun logStep(agent: String, state: WorkflowState, message: String) {
        val compact = message.replace(Regex("\\s+"), " ").trim().take(200)
        println("[workflow] [$state] $agent: $compact")
    }

    private fun logTransition(from: WorkflowState, to: WorkflowState, reason: String, step: Int, total: Int) {
        println("[workflow] transition $from -> $to | step $step/$total | $reason")
    }

    private companion object {
        const val MAX_WORKFLOW_ITERATIONS = 8
        const val MAX_VALIDATION_RETRIES = 2
    }
}
