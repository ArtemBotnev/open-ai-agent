package com.artembotnev.openai.data.facts

import com.artembotnev.openai.domain.model.DialogFacts
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

internal class JsonDialogFactsDataSource(
    private val baseDir: Path,
    private val fileName: String = "facts.json",
    private val fs: FileSystem,
    private val json: Json
) : DialogFactsDataSource {

    private val filePath get() = baseDir / fileName
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val legacyListSerializer = ListSerializer(String.serializer())

    override fun load(): DialogFacts = try {
        if (!fs.exists(filePath)) return emptyMap()
        fs.source(filePath).buffer().use { src ->
            val text = src.readUtf8()
            if (text.isBlank()) return emptyMap()
            return decodeFacts(text)
        }
    } catch (t: Throwable) {
        t.printStackTrace()
        return emptyMap()
    }

    override fun save(facts: DialogFacts) {
        if (!fs.exists(baseDir)) fs.createDirectories(baseDir)
        val text = json.encodeToString(mapSerializer, facts)
        fs.write(filePath) { writeUtf8(text) }
    }

    override fun clear() {
        if (fs.exists(filePath)) fs.delete(filePath)
    }

    private fun decodeFacts(text: String): DialogFacts {
        return runCatching {
            json.decodeFromString(mapSerializer, text)
        }.getOrElse {
            val legacyFacts = json.decodeFromString(legacyListSerializer, text)
            legacyFacts.mapNotNull(::parseLegacyFact)
                .toMap(linkedMapOf())
        }
    }

    private fun parseLegacyFact(fact: String): Pair<String, String>? {
        val separatorIndex = fact.indexOf('=')
            .takeIf { it >= 0 }
            ?: fact.indexOf(':').takeIf { it >= 0 }
            ?: return null

        val key = fact.substring(0, separatorIndex).trim()
        val value = fact.substring(separatorIndex + 1).trim()

        if (key.isEmpty() || value.isEmpty()) {
            return null
        }

        return key to value
    }
}
