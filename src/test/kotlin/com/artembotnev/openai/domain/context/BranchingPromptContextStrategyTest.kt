package com.artembotnev.openai.domain.context

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BranchingPromptContextStrategyTest {

    @Test
    fun `build returns root history and only messages from active branch`() {
        val history = listOf(
            AgentMessage(role = AgentRole.USER, content = "root-user"),
            AgentMessage(role = AgentRole.ASSISTANT, content = "root-assistant"),
            AgentMessage.checkpointMarker(
                sourceBranch = null,
                firstBranch = "branch-a",
                secondBranch = "branch-b"
            ),
            AgentMessage(role = AgentRole.USER, content = "a-user", branch = "branch-a"),
            AgentMessage(role = AgentRole.ASSISTANT, content = "a-assistant", branch = "branch-a"),
            AgentMessage(role = AgentRole.USER, content = "b-user", branch = "branch-b"),
            AgentMessage(role = AgentRole.ASSISTANT, content = "b-assistant", branch = "branch-b"),
        )

        val branchAContext = BranchingPromptContextStrategy(activeBranch = "branch-a").build(
            facts = emptyMap(),
            history = history,
        )
        val branchBContext = BranchingPromptContextStrategy(activeBranch = "branch-b").build(
            facts = emptyMap(),
            history = history,
        )

        assertNull(branchAContext.factsBlock)
        assertEquals(
            listOf("root-user", "root-assistant", "a-user", "a-assistant"),
            branchAContext.messages.map { it.content }
        )
        assertEquals(
            listOf("root-user", "root-assistant", "b-user", "b-assistant"),
            branchBContext.messages.map { it.content }
        )
    }

    @Test
    fun `build supports nested checkpoints inside active branch`() {
        val history = listOf(
            AgentMessage(role = AgentRole.USER, content = "root-user"),
            AgentMessage.checkpointMarker(
                sourceBranch = null,
                firstBranch = "branch-a",
                secondBranch = "branch-b"
            ),
            AgentMessage(role = AgentRole.USER, content = "a-user", branch = "branch-a"),
            AgentMessage.checkpointMarker(
                sourceBranch = "branch-a",
                firstBranch = "branch-c",
                secondBranch = "branch-d"
            ),
            AgentMessage(role = AgentRole.ASSISTANT, content = "c-assistant", branch = "branch-c"),
            AgentMessage(role = AgentRole.ASSISTANT, content = "b-assistant", branch = "branch-b"),
        )

        val context = BranchingPromptContextStrategy(activeBranch = "branch-c").build(
            facts = emptyMap(),
            history = history,
        )

        assertEquals(
            listOf("root-user", "a-user", "c-assistant"),
            context.messages.map { it.content }
        )
    }
}
