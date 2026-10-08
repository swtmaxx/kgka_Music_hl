package com.swtmaxx.kamusic.compose.data.model

/** 单个字/词及其时间轴。 */
data class LyricWord(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
)

/** 一行歌词。`words` 非空即支持逐字高亮。 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val durationMs: Long? = null,
    val translation: String? = null,
    val romanization: String? = null,
    val words: List<LyricWord> = emptyList(),
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()

    /** 返回 positionMs 时刻已唱到的字下标；未开始返回 -1。 */
    fun activeWordIndex(positionMs: Long): Int {
        if (words.isEmpty()) return -1
        var active = -1
        for (index in words.indices) {
            if (positionMs >= words[index].timeMs) active = index else break
        }
        return active
    }

    /**
     * 已唱部分占整行的比例（0f..1f），用于逐字渐变。
     * 无逐字时间轴时按行内线性插值估算。
     */
    fun progressFraction(positionMs: Long): Float {
        if (words.isEmpty()) {
            val end = timeMs + (durationMs ?: 0L)
            if (end <= timeMs) return if (positionMs >= timeMs) 1f else 0f
            return ((positionMs - timeMs).toFloat() / (end - timeMs).toFloat()).coerceIn(0f, 1f)
        }
        val total = words.sumOf { it.durationMs.coerceAtLeast(0L) }.coerceAtLeast(1L)
        var elapsed = 0L
        for (word in words) {
            val wordEnd = word.timeMs + word.durationMs
            elapsed += when {
                positionMs >= wordEnd -> word.durationMs
                positionMs <= word.timeMs -> 0L
                else -> positionMs - word.timeMs
            }
        }
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
