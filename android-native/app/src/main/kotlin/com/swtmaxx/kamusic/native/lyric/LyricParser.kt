package com.swtmaxx.kamusic.native.lyric

import com.swtmaxx.kamusic.native.data.model.LyricLine
import com.swtmaxx.kamusic.native.data.model.LyricWord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/**
 * 歌词解析器（纯函数，可 JVM 单测）。
 *
 * 与 Flutter 版 `lib/services/music_api.dart` 的 `parseLyrics` 逐条对齐：
 * 1. 归一化换行与 BOM
 * 2. 优先解析 KRC（`[起始ms,时长ms]` + `<字偏移ms,字时长ms,?>`），得到逐字时间轴
 * 3. 无 KRC 时回退 LRC，并把**同时间戳的相邻两行**合并为原文 + 翻译
 * 4. 解析 `[language:...]`（Base64 → JSON）里的翻译/音译并按时间或行序合并
 *
 * 注意：服务端 `/lyric?decode=true` 已解密 KRC，客户端只做解析，不做解密。
 */
object LyricParser {

    private val KRC_LINE = Regex("""^\[\s*(-?\d+)\s*,\s*(-?\d+)\s*\](.*)$""")
    private val KRC_WORD = Regex("""<\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*>""")
    private val OFFSET_TAG = Regex("""^\[offset:([+-]?\d+)\]""", RegexOption.MULTILINE)
    private val LRC_TIME = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val LANGUAGE_TAG = Regex("""^\[language:([A-Za-z0-9+/\-_]+=*)\]""", RegexOption.MULTILINE)

    private const val NEAREST_VARIANT_WINDOW_MS = 500L

    fun parse(content: String?): List<LyricLine> {
        if (content.isNullOrBlank()) return emptyList()

        val normalized = content
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")

        val krcLines = parseKrc(normalized)
        val parsed = if (krcLines.isNotEmpty()) {
            krcLines
        } else {
            mergeSameTimeTranslation(parseLrc(normalized))
        }
        if (parsed.isEmpty()) return emptyList()

        val variants = parseLanguageVariants(normalized)
        return mergeVariants(parsed, variants)
    }

    // ===== KRC =====

    private fun parseKrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val offset = extractOffset(content)

        for (rawLine in content.split('\n')) {
            val match = KRC_LINE.find(rawLine.trim()) ?: continue
            val start = match.groupValues[1].toLongOrNull() ?: continue
            val duration = match.groupValues[2].toLongOrNull() ?: continue
            val body = match.groupValues[3]

            val wordMatches = KRC_WORD.findAll(body).toList()
            val words = mutableListOf<LyricWord>()
            wordMatches.forEachIndexed { index, wordMatch ->
                val wordStart = wordMatch.groupValues[1].toLongOrNull() ?: 0L
                val wordDuration = wordMatch.groupValues[2].toLongOrNull() ?: 0L
                val wordEnd = if (index + 1 < wordMatches.size) {
                    wordMatches[index + 1].range.first
                } else {
                    body.length
                }
                if (wordEnd <= wordMatch.range.last + 1) return@forEachIndexed
                val wordText = body.substring(wordMatch.range.last + 1, wordEnd)
                if (wordText.isEmpty()) return@forEachIndexed
                words += LyricWord(
                    timeMs = (start + wordStart + offset).coerceAtLeast(0L),
                    durationMs = wordDuration.coerceAtLeast(0L),
                    text = wordText,
                )
            }

            val displayWords = trimWords(words)
            val text = if (displayWords.isEmpty()) {
                body.replace(KRC_WORD, "").trim()
            } else {
                displayWords.joinToString("") { it.text }
            }
            if (text.isEmpty()) continue

            lines += LyricLine(
                timeMs = (start + offset).coerceAtLeast(0L),
                text = text,
                durationMs = duration.coerceAtLeast(0L),
                words = displayWords,
            )
        }

        lines.sortBy { it.timeMs }
        return lines
    }

    /** 去掉首字左侧空白、末字右侧空白，并丢弃空字。 */
    private fun trimWords(words: List<LyricWord>): List<LyricWord> {
        if (words.isEmpty()) return words
        val result = words.toMutableList()
        while (result.isNotEmpty() && result.first().text.isBlank()) result.removeAt(0)
        while (result.isNotEmpty() && result.last().text.isBlank()) result.removeAt(0)
        if (result.isEmpty()) return result
        result[0] = result[0].copy(text = result[0].text.trimStart())
        val lastIndex = result.size - 1
        result[lastIndex] = result[lastIndex].copy(text = result[lastIndex].text.trimEnd())
        return result.filter { it.text.isNotEmpty() }
    }

    // ===== LRC =====

    private fun parseLrc(content: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val offset = extractOffset(content)

        for (rawLine in content.split('\n')) {
            val matches = LRC_TIME.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = rawLine.replace(LRC_TIME, "").trim()
            if (text.isEmpty()) continue

            for (match in matches) {
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                val seconds = match.groupValues[2].toIntOrNull() ?: 0
                val fraction = match.groupValues[3].ifEmpty { "0" }
                val millis = if (fraction.length == 3) {
                    fraction.toIntOrNull() ?: 0
                } else {
                    fraction.padEnd(3, '0').toIntOrNull() ?: 0
                }
                val timeMs = ((minutes * 60L + seconds) * 1000L + millis + offset)
                    .coerceAtLeast(0L)
                lines += LyricLine(timeMs = timeMs, text = text)
            }
        }

        lines.sortBy { it.timeMs }
        return lines
    }

    /**
     * 合并同一时间戳的相邻两行。
     *
     * LRC 常见的「原文 + 翻译」写法是两行共用同一时间戳，
     * 不合并的话翻译会被当成独立歌词行，导致原文瞬间被跳过。
     */
    private fun mergeSameTimeTranslation(lines: List<LyricLine>): List<LyricLine> {
        if (lines.size < 2) return lines
        val merged = mutableListOf<LyricLine>()
        for (line in lines) {
            val last = merged.lastOrNull()
            if (last != null &&
                last.translation == null &&
                last.romanization == null &&
                last.timeMs == line.timeMs &&
                last.text.trim() != line.text.trim()
            ) {
                merged[merged.lastIndex] = last.copy(translation = line.text)
                continue
            }
            merged += line
        }
        return merged
    }

    private fun extractOffset(content: String): Long =
        OFFSET_TAG.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    // ===== [language:...] 翻译 / 音译 =====

    private data class Variants(
        val translationByTime: Map<Long, String> = emptyMap(),
        val translationByIndex: List<String> = emptyList(),
        val romanizationByTime: Map<Long, String> = emptyMap(),
        val romanizationByIndex: List<String> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = translationByTime.isEmpty() && translationByIndex.isEmpty() &&
                romanizationByTime.isEmpty() && romanizationByIndex.isEmpty()
    }

    private fun parseLanguageVariants(content: String): Variants {
        val encoded = LANGUAGE_TAG.find(content)?.groupValues?.get(1)
        if (encoded.isNullOrEmpty()) return Variants()

        return runCatching {
            var normalized = encoded.replace('-', '+').replace('_', '/')
            val mod = normalized.length % 4
            if (mod > 0) normalized += "=".repeat(4 - mod)
            val decoded = String(Base64.getDecoder().decode(normalized), Charsets.UTF_8)
            val root = com.swtmaxx.kamusic.native.core.KaJson.parseToJsonElement(decoded)

            val translationByTime = mutableMapOf<Long, String>()
            val translationByIndex = mutableListOf<String>()
            val romanizationByTime = mutableMapOf<Long, String>()
            val romanizationByIndex = mutableListOf<String>()

            collectLanguageRows(
                root,
                translationByTime, translationByIndex,
                romanizationByTime, romanizationByIndex,
            )

            Variants(
                translationByTime = translationByTime,
                translationByIndex = translationByIndex,
                romanizationByTime = romanizationByTime,
                romanizationByIndex = romanizationByIndex,
            )
        }.getOrElse { Variants() }
    }

    private fun collectLanguageRows(
        value: JsonElement?,
        translationByTime: MutableMap<Long, String>,
        translationByIndex: MutableList<String>,
        romanizationByTime: MutableMap<Long, String>,
        romanizationByIndex: MutableList<String>,
    ) {
        when (value) {
            is JsonArray -> value.forEach {
                collectLanguageRows(
                    it, translationByTime, translationByIndex,
                    romanizationByTime, romanizationByIndex,
                )
            }

            is JsonObject -> {
                val sectionType = (value["type"] as? JsonPrimitive)?.content?.toIntOrNull()
                val lyricContent = value["lyricContent"]
                if (lyricContent is JsonArray) {
                    val byTime = if (sectionType == 0) romanizationByTime else translationByTime
                    val byIndex = if (sectionType == 0) romanizationByIndex else translationByIndex
                    for (row in lyricContent) {
                        val parsed = parseLanguageRow(row, sectionType) ?: continue
                        if (parsed.first != null) {
                            byTime[parsed.first!!] = parsed.second
                        } else {
                            byIndex += parsed.second
                        }
                    }
                }
                value.values.forEach {
                    collectLanguageRows(
                        it, translationByTime, translationByIndex,
                        romanizationByTime, romanizationByIndex,
                    )
                }
            }

            else -> Unit
        }
    }

    /** 返回 (timeMs?, text)。 */
    private fun parseLanguageRow(row: JsonElement, sectionType: Int?): Pair<Long?, String>? {
        if (row !is JsonArray || row.isEmpty()) return null
        val time = if (row.size > 1) (row[0] as? JsonPrimitive)?.content?.toLongOrNull() else null
        val values = row.mapNotNull { (it as? JsonPrimitive)?.content }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return null
        val text = if (time != null && row.size > 1) {
            (row[1] as? JsonPrimitive)?.content
        } else if (sectionType == 0) {
            values.joinToString("")
        } else {
            values.joinToString(" ").trim()
        }
        if (text.isNullOrEmpty()) return null
        return time to text
    }

    private fun mergeVariants(lines: List<LyricLine>, variants: Variants): List<LyricLine> {
        if (variants.isEmpty) return lines

        return lines.mapIndexed { index, line ->
            val translation = variants.translationByTime[line.timeMs]
                ?: nearestVariant(line.timeMs, variants.translationByTime)
                ?: variants.translationByIndex.getOrNull(index)
            val romanization = variants.romanizationByTime[line.timeMs]
                ?: nearestVariant(line.timeMs, variants.romanizationByTime)
                ?: variants.romanizationByIndex.getOrNull(index)

            line.copy(
                translation = translation?.takeIf { it.isNotEmpty() },
                romanization = romanization?.takeIf { it.isNotEmpty() },
            )
        }
    }

    private fun nearestVariant(timeMs: Long, byTime: Map<Long, String>): String? {
        if (byTime.isEmpty()) return null
        var best: Long? = null
        var bestDelta = Long.MAX_VALUE
        for (key in byTime.keys) {
            val delta = kotlin.math.abs(key - timeMs)
            if (delta < bestDelta) {
                bestDelta = delta
                best = key
            }
        }
        return if (best != null && bestDelta <= NEAREST_VARIANT_WINDOW_MS) byTime[best] else null
    }
}
