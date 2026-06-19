package com.artembotnev.openai.infra.openai.workflow

import kotlinx.serialization.Serializable

internal class PlanningSubAgent(
    private val support: OpenAiWorkflowSupport,
    private val requirementsPlanningSubAgent: RequirementsPlanningSubAgent,
    private val architecturePlanningSubAgent: ArchitecturePlanningSubAgent,
    private val developerPlanningSubAgent: DeveloperPlanningSubAgent,
    private val designerPlanningSubAgent: DesignerPlanningSubAgent,
) {

    fun run(context: WorkflowAgentContext): PlanningAgentResult {
        val proposals = listOf(
            requirementsPlanningSubAgent.propose(context),
            architecturePlanningSubAgent.propose(context),
            developerPlanningSubAgent.propose(context),
            designerPlanningSubAgent.propose(context),
        )
        val prompt = PlanningPromptBuilder.buildUserPrompt(
            context = context,
            formattedHistory = support.formatHistory(context.history),
            proposals = proposals.map(PlanningProposalResult::proposal),
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
                consideredInvariants = emptyList(),
                invariantConflicts = emptyList(),
            )

        val allUsage = proposals.fold(call.usage) { acc, proposal ->
            acc.plus(proposal.usage)
        }

        return PlanningAgentResult(
            reply = payload.reply.trim(),
            task = payload.task.trim(),
            plan = payload.plan.map(String::trim).filter(String::isNotBlank).distinct(),
            current = payload.current.trim(),
            awaitingConfirmation = payload.awaitingConfirmation,
            consideredInvariants = payload.consideredInvariants.map(String::trim).filter(String::isNotBlank).distinct(),
            invariantConflicts = payload.invariantConflicts.map(String::trim).filter(String::isNotBlank).distinct(),
            candidateSummaries = proposals.map { proposal ->
                val summary = proposal.proposal.summary.ifBlank { "Сводка не получена" }
                "${proposal.proposal.agentName}: $summary"
            },
            usage = allUsage,
        )
    }

    @Serializable
    private data class PlanningPayload(
        val reply: String = "",
        val task: String = "",
        val plan: List<String> = emptyList(),
        val current: String = "",
        val awaitingConfirmation: Boolean = false,
        val consideredInvariants: List<String> = emptyList(),
        val invariantConflicts: List<String> = emptyList(),
    )

    private fun WorkflowUsage.plus(other: WorkflowUsage): WorkflowUsage {
        fun sum(left: Long?, right: Long?): Long? {
            return when {
                left == null && right == null -> null
                else -> (left ?: 0L) + (right ?: 0L)
            }
        }

        return WorkflowUsage(
            promptTokens = sum(promptTokens, other.promptTokens),
            completionTokens = sum(completionTokens, other.completionTokens),
            totalTokens = sum(totalTokens, other.totalTokens),
        )
    }
}
