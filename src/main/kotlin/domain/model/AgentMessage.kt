package com.artembotnev.openai.domain.model

import kotlinx.serialization.Serializable

@Serializable
internal data class AgentMessage(
    val role: AgentRole,
    val content: String,
    val branch: String? = null,
    val checkpoint: BranchCheckpoint? = null,
) {
    companion object {
        fun checkpointMarker(
            sourceBranch: String?,
            firstBranch: String,
            secondBranch: String,
        ): AgentMessage = AgentMessage(
            role = AgentRole.SYSTEM,
            content = "[checkpoint]",
            checkpoint = BranchCheckpoint(
                sourceBranch = sourceBranch,
                branches = listOf(firstBranch, secondBranch)
            )
        )
    }
}

@Serializable
internal data class BranchCheckpoint(
    val sourceBranch: String? = null,
    val branches: List<String> = emptyList(),
)
