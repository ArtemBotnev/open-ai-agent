package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import com.artembotnev.openai.domain.model.DialogFacts

internal class BranchingPromptContextStrategy(
    private val activeBranch: String?,
) : PromptContextStrategy {

    override fun build(facts: DialogFacts, history: List<AgentMessage>): PromptContext {
        val messages = visibleHistory(
            history = history,
            branch = activeBranch,
            endExclusive = history.size
        ).filter { message ->
            message.checkpoint == null && message.role != AgentRole.SYSTEM
        }

        return PromptContext(
            factsBlock = null,
            messages = messages
        )
    }

    companion object {
        fun listBranches(history: List<AgentMessage>): List<String> {
            return history.asSequence()
                .mapNotNull { it.checkpoint?.branches }
                .flatten()
                .distinct()
                .sorted()
                .toList()
        }

        private fun visibleHistory(
            history: List<AgentMessage>,
            branch: String?,
            endExclusive: Int,
        ): List<AgentMessage> {
            if (branch == null) {
                return history.take(endExclusive)
                    .filter { message ->
                        message.checkpoint == null && message.branch == null
                    }
            }

            val checkpointIndex = history.take(endExclusive)
                .indexOfLast { message -> branch in (message.checkpoint?.branches ?: emptyList()) }

            if (checkpointIndex < 0) {
                return history.take(endExclusive)
                    .filter { message ->
                        message.checkpoint == null && message.branch == branch
                    }
            }

            val checkpointMessage = history[checkpointIndex]
            val baseHistory = visibleHistory(
                history = history,
                branch = checkpointMessage.checkpoint?.sourceBranch,
                endExclusive = checkpointIndex
            )
            val branchHistory = history.subList(checkpointIndex + 1, endExclusive)
                .filter { message ->
                    message.checkpoint == null && message.branch == branch
                }

            return baseHistory + branchHistory
        }
    }
}
