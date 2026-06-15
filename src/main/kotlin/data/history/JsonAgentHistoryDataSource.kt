package com.artembotnev.openai.data.history

import com.artembotnev.openai.domain.model.AgentMessage
import com.artembotnev.openai.domain.model.AgentRole
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonAgentHistoryDataSource(
    private val baseDir: Path,
    private val fileName: String = "history.json",
    private val fs: FileSystem,
    private val json: Json
) : AgentHistoryDataSource {

    private val filePath get() = baseDir / fileName
    private val listSer = ListSerializer(AgentMessage.serializer())

    override fun load(): List<AgentMessage> = try {
        if (!fs.exists(filePath)) return emptyList()
        fs.source(filePath).buffer().use { src ->
            val text = src.readUtf8()
            if (text.isBlank()) return emptyList()
            return json.decodeFromString(deserializer = listSer, string = text)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return emptyList()
    }

    override fun save(all: List<AgentMessage>) {
        if (!fs.exists(baseDir)) fs.createDirectories(baseDir)
        val trimmed = trimToLastRequests(all)
        val text = json.encodeToString(listSer, trimmed)
        fs.write(filePath) { writeUtf8(text) }
    }

    override fun append(message: AgentMessage): List<AgentMessage> {
        val all = trimToLastRequests(load() + message)
        save(all)

        return all
    }

    override fun clear() {
        if (fs.exists(filePath)) fs.delete(filePath)
    }

    private fun trimToLastRequests(history: List<AgentMessage>): List<AgentMessage> {
        val systemMessages = history.filter { it.role == AgentRole.SYSTEM }
        val conversationMessages = history.filter { it.role != AgentRole.SYSTEM }
        val userIndices = conversationMessages.mapIndexedNotNull { index, message ->
            index.takeIf { message.role == AgentRole.USER }
        }

        if (userIndices.size <= MAX_REQUESTS) {
            return systemMessages + conversationMessages
        }

        val startIndex = userIndices[userIndices.size - MAX_REQUESTS]
        return systemMessages + conversationMessages.drop(startIndex)
    }

    private companion object {
        const val MAX_REQUESTS = 20
    }
}
