package com.artembotnev.openai.branching

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

internal class DialogBranchManager(
    private val generatedBaseDir: Path,
    private val fs: FileSystem = FileSystem.SYSTEM,
) {

    private val branchingDir get() = generatedBaseDir / "branching"
    private val branchesDir get() = branchingDir / "branches"
    private val checkpointsDir get() = branchingDir / "checkpoints"

    fun ensureMainBranchBootstrapped() {
        ensureBranchingDirs()

        val mainDir = branchBaseDir(MAIN_BRANCH_NAME)
        if (fs.exists(mainDir)) {
            return
        }

        copyStateFiles(sourceDir = generatedBaseDir, targetDir = mainDir)
    }

    fun branchBaseDir(branchName: String): Path {
        val normalizedBranch = normalizeName(branchName)
        return branchesDir / normalizedBranch
    }

    fun branchExists(branchName: String): Boolean = fs.exists(branchBaseDir(branchName))

    fun checkpointExists(checkpointName: String): Boolean = fs.exists(checkpointBaseDir(checkpointName))

    fun listBranches(): List<String> {
        if (!fs.exists(branchesDir)) {
            return emptyList()
        }

        return fs.list(branchesDir)
            .map(::pathName)
            .sorted()
    }

    fun listCheckpoints(): List<String> {
        if (!fs.exists(checkpointsDir)) {
            return emptyList()
        }

        return fs.list(checkpointsDir)
            .map(::pathName)
            .sorted()
    }

    fun createCheckpoint(branchName: String, checkpointName: String) {
        ensureBranchingDirs()

        val sourceDir = branchBaseDir(branchName)
        require(fs.exists(sourceDir)) { "Ветка '$branchName' не найдена" }

        val targetDir = checkpointBaseDir(checkpointName)
        require(!fs.exists(targetDir)) { "Checkpoint '$checkpointName' уже существует" }

        copyStateFiles(sourceDir = sourceDir, targetDir = targetDir)
    }

    fun createBranch(checkpointName: String, branchName: String) {
        ensureBranchingDirs()

        val sourceDir = checkpointBaseDir(checkpointName)
        require(fs.exists(sourceDir)) { "Checkpoint '$checkpointName' не найден" }

        val targetDir = branchBaseDir(branchName)
        require(!fs.exists(targetDir)) { "Ветка '$branchName' уже существует" }

        copyStateFiles(sourceDir = sourceDir, targetDir = targetDir)
    }

    private fun checkpointBaseDir(checkpointName: String): Path {
        val normalizedCheckpoint = normalizeName(checkpointName)
        return checkpointsDir / normalizedCheckpoint
    }

    private fun ensureBranchingDirs() {
        if (!fs.exists(branchesDir)) {
            fs.createDirectories(branchesDir)
        }
        if (!fs.exists(checkpointsDir)) {
            fs.createDirectories(checkpointsDir)
        }
    }

    private fun copyStateFiles(sourceDir: Path, targetDir: Path) {
        fs.createDirectories(targetDir)

        for (fileName in STATE_FILE_NAMES) {
            val sourceFile = sourceDir / fileName
            if (!fs.exists(sourceFile)) {
                continue
            }

            val targetFile = targetDir / fileName
            val content = fs.read(sourceFile) { readUtf8() }
            fs.write(targetFile) { writeUtf8(content) }
        }
    }

    private fun normalizeName(name: String): String {
        val normalizedName = name.trim()
        require(normalizedName.matches(NAME_PATTERN)) {
            "Имя '$name' содержит недопустимые символы"
        }
        return normalizedName
    }

    private fun pathName(path: Path): String = path.name

    companion object {
        const val MAIN_BRANCH_NAME = "main"

        private val NAME_PATTERN = Regex("[a-zA-Z0-9._-]+")
        private val STATE_FILE_NAMES = listOf("history.json", "facts.json", "usage.json")

        fun defaultGeneratedBaseDir(): Path =
            (System.getProperty("user.dir") + "/generated").toPath()
    }
}
