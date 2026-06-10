package com.artembotnev.openai.data.dialogusage

import com.artembotnev.openai.domain.model.DialogUsage
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonDialogUsageDataSource(
    private val baseDir: Path,
    private val fileName: String = "usage.json",
    private val fs: FileSystem,
    private val json: Json
) : DialogUsageDataSource {

    private val filePath get() = baseDir / fileName
    private val ser = DialogUsage.serializer()

    override fun load(): DialogUsage = try {
        if (!fs.exists(filePath)) return DialogUsage()
        fs.source(filePath).buffer().use { src ->
            val text = src.readUtf8()
            if (text.isBlank()) return DialogUsage()
            return json.decodeFromString(deserializer = ser, string = text)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return DialogUsage()
    }

    override fun save(usage: DialogUsage) {
        if (!fs.exists(baseDir)) fs.createDirectories(baseDir)
        val text = json.encodeToString(ser, usage)
        fs.write(filePath) { writeUtf8(text) }
    }

    override fun clear() {
        if (fs.exists(filePath)) fs.delete(filePath)
    }
}
