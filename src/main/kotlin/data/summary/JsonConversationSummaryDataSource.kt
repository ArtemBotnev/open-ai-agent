package com.artembotnev.openai.data.summary

import com.artembotnev.openai.domain.model.ConversationSummary
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonConversationSummaryDataSource(
    private val baseDir: Path,
    private val fileName: String = "summary.json",
    private val fs: FileSystem,
    private val json: Json
) : ConversationSummaryDataSource {

    private val filePath get() = baseDir / fileName
    private val ser = ConversationSummary.serializer()

    override fun load(): ConversationSummary = try {
        if (!fs.exists(filePath)) return ConversationSummary()
        fs.source(filePath).buffer().use { src ->
            val text = src.readUtf8()
            if (text.isBlank()) return ConversationSummary()
            return json.decodeFromString(deserializer = ser, string = text)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return ConversationSummary()
    }

    override fun save(summary: ConversationSummary) {
        if (!fs.exists(baseDir)) fs.createDirectories(baseDir)
        val text = json.encodeToString(ser, summary)
        fs.write(filePath) { writeUtf8(text) }
    }

    override fun clear() {
        if (fs.exists(filePath)) fs.delete(filePath)
    }
}
