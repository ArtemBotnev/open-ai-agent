package com.artembotnev.openai.branching

import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DialogBranchManagerTest {

    private val baseDir = "/tmp/openai-first-branch-manager-test-${System.nanoTime()}".toPath()
    private val fs = FileSystem.SYSTEM

    @AfterTest
    fun cleanup() {
        File(baseDir.toString()).deleteRecursively()
    }

    @Test
    fun `bootstrap main branch copies root dialog state`() {
        fs.createDirectories(baseDir)
        fs.write(baseDir / "history.json") { writeUtf8("history-root") }
        fs.write(baseDir / "facts.json") { writeUtf8("facts-root") }
        fs.write(baseDir / "usage.json") { writeUtf8("usage-root") }

        val manager = DialogBranchManager(baseDir, fs)
        manager.ensureMainBranchBootstrapped()

        val mainDir = manager.branchBaseDir(DialogBranchManager.MAIN_BRANCH_NAME)
        assertTrue(manager.branchExists(DialogBranchManager.MAIN_BRANCH_NAME))
        assertEquals("history-root", fs.read(mainDir / "history.json") { readUtf8() })
        assertEquals("facts-root", fs.read(mainDir / "facts.json") { readUtf8() })
        assertEquals("usage-root", fs.read(mainDir / "usage.json") { readUtf8() })
    }

    @Test
    fun `checkpoint and branch copy state independently`() {
        fs.createDirectories(baseDir)
        val manager = DialogBranchManager(baseDir, fs)
        manager.ensureMainBranchBootstrapped()

        val mainDir = manager.branchBaseDir(DialogBranchManager.MAIN_BRANCH_NAME)
        fs.write(mainDir / "history.json") { writeUtf8("main-history") }
        fs.write(mainDir / "facts.json") { writeUtf8("main-facts") }
        fs.write(mainDir / "usage.json") { writeUtf8("main-usage") }

        manager.createCheckpoint(DialogBranchManager.MAIN_BRANCH_NAME, "cp1")
        manager.createBranch("cp1", "branch-a")
        manager.createBranch("cp1", "branch-b")

        val branchADir = manager.branchBaseDir("branch-a")
        val branchBDir = manager.branchBaseDir("branch-b")
        fs.write(branchADir / "history.json") { writeUtf8("branch-a-history") }
        fs.write(branchADir / "usage.json") { writeUtf8("branch-a-usage") }

        assertEquals(listOf("branch-a", "branch-b", "main"), manager.listBranches())
        assertEquals(listOf("cp1"), manager.listCheckpoints())
        assertEquals("main-history", fs.read(mainDir / "history.json") { readUtf8() })
        assertEquals("branch-a-history", fs.read(branchADir / "history.json") { readUtf8() })
        assertEquals("main-history", fs.read(branchBDir / "history.json") { readUtf8() })
        assertEquals("main-facts", fs.read(branchBDir / "facts.json") { readUtf8() })
        assertEquals("branch-a-usage", fs.read(branchADir / "usage.json") { readUtf8() })
        assertEquals("main-usage", fs.read(branchBDir / "usage.json") { readUtf8() })
    }
}
