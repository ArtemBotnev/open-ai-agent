package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal class PlanningSubAgent(
    private val support: OpenAiWorkflowSupport,
) {

    fun run(context: WorkflowAgentContext): PlanningAgentResult {
        val prompt = PlanningPromptBuilder.buildUserPrompt(
            context = context,
            formattedHistory = support.formatHistory(context.history),
        )
        val systemPrompt = PlanningPromptBuilder.buildSystemPrompt(context)

        val call = support.complete(systemPrompt, prompt)
        val payload = support.parseJsonPayload<PlanningPayload>(call.raw)
            ?: PlanningPayload(
                reply = "Не удалось надёжно распознать планирование. Уточни задачу и ограничения, после этого я пересоберу план.",
                task = context.state.task.ifBlank { context.userText },
                plan = context.state.plan,
                current = context.state.current,
                awaitingConfirmation = false,
                readyForExecution = false,
                consideredInvariants = emptyList(),
                invariantConflicts = emptyList(),
            )

        return PlanningAgentResult(
            reply = payload.reply.trim(),
            task = payload.task.trim(),
            plan = payload.plan.map(String::trim).filter(String::isNotBlank).distinct(),
            current = payload.current.trim(),
            awaitingConfirmation = payload.awaitingConfirmation,
            readyForExecution = payload.readyForExecution,
            consideredInvariants = payload.consideredInvariants.map(String::trim).filter(String::isNotBlank).distinct(),
            invariantConflicts = payload.invariantConflicts.map(String::trim).filter(String::isNotBlank).distinct(),
            usage = call.usage,
        )
    }

    @Serializable
    private data class PlanningPayload(
        val reply: String = "",
        val task: String = "",
        val plan: List<String> = emptyList(),
        val current: String = "",
        val awaitingConfirmation: Boolean = false,
        val readyForExecution: Boolean = false,
        val consideredInvariants: List<String> = emptyList(),
        val invariantConflicts: List<String> = emptyList(),
    )
}
