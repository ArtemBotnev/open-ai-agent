package com.artembotnev.openai.infra.openai.workflow

import com.artembotnev.openai.domain.context.Invariant
import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.workflow.TaskWorkflowState

internal data class WorkflowAgentContext(
    val userText: String,
    val state: TaskWorkflowState,
    val userProfileMarkdown: String,
    val taskContextMarkdown: String,
    val invariant: Invariant,
    val invariantMarkdown: String,
    val history: List<AgentMessage>,
)

internal data class WorkflowUsage(
    val promptTokens: Long? = null,
    val completionTokens: Long? = null,
    val totalTokens: Long? = null,
)

internal data class WorkflowCallResult(
    val raw: String,
    val usage: WorkflowUsage,
)

internal data class PlanningAgentResult(
    val reply: String,
    val task: String,
    val plan: List<String>,
    val current: String,
    val awaitingConfirmation: Boolean,
    val consideredInvariants: List<String>,
    val invariantConflicts: List<String>,
    val candidateSummaries: List<String>,
    val usage: WorkflowUsage,
)

internal data class PlanningProposal(
    val agentName: String,
    val summary: String,
    val task: String,
    val plan: List<String>,
    val current: String,
    val questions: List<String>,
    val consideredInvariants: List<String>,
    val invariantConflicts: List<String>,
)

internal data class PlanningProposalResult(
    val proposal: PlanningProposal,
    val usage: WorkflowUsage,
)

internal data class ExecutionAgentResult(
    val reply: String,
    val needsClarification: Boolean,
    val completedSteps: List<String>,
    val current: String,
    val artifact: String,
    val usage: WorkflowUsage,
)

internal data class ValidationAgentResult(
    val reply: String,
    val valid: Boolean,
    val issues: List<String>,
    val revisionRequest: String,
    val usage: WorkflowUsage,
)

internal data class DoneAgentResult(
    val reply: String,
    val usage: WorkflowUsage,
)

internal data class WorkflowResponse(
    val answer: String,
    val promptTokens: Long?,
    val completionTokens: Long?,
    val totalTokens: Long?,
)
