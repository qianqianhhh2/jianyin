package com.qian.jianyin

/**
 * 歌词解析器
 * 支持 YRC（逐字）与 LRC（逐行）两种格式的解析
 */
object LyricParser {

    /** YRC歌词行正则表达式（静态常量，类加载时初始化） */
    private val YRC_LINE_REGEX = Regex("""\[\d+,\s*\d+]\(\d+,""")

    /** 旧版 LRC 时间戳正则：[mm:ss:xx]（冒号分隔毫秒，分钟可为 1 位） */
    private val LEGACY_LRC_TIMESTAMP_REGEX = Regex("""\[(\d{1,2}):(\d{2}):(\d{2,3})]""")

    /** 自动检测并解析歌词：YRC → 逐字, LRC → 逐行 */
    fun parseAuto(content: String): List<LyricEntry> {
        if (content.isBlank()) return emptyList()
        return if (YRC_LINE_REGEX.containsMatchIn(content)) {
            parseYrc(content)
        } else {
            parseLrc(content)
        }
    }

    /** 解析 YRC 逐字歌词 */
    private fun parseYrc(yrc: String): List<LyricEntry> {
        val out = mutableListOf<LyricEntry>()
        val headerRegex = Regex("""\[(\d+),\s*(\d+)]""")
        val segRegex = Regex("""\((\d+),\s*(\d+),\s*[-\d]+\)([^()\n\r]+)""")

        yrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (!line.startsWith("[")) return@forEach

            val header = headerRegex.find(line) ?: return@forEach
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val end = start + dur

            val segs = segRegex.findAll(line).toList()
            if (segs.isEmpty()) {
                val text = line.substringAfter("]").trim()
                if (text.isNotEmpty()) out.add(LyricEntry(start, end, text))
            } else {
                val words = mutableListOf<WordTiming>()
                val sb = StringBuilder()
                for (m in segs) {
                    val ws = m.groupValues[1].toLong()
                    val wd = m.groupValues[2].toLong()
                    val we = ws + wd
                    val t = m.groupValues[3]
                    sb.append(t)
                    words.add(WordTiming(ws, we, t.length))
                }
                out.add(LyricEntry(start, end, sb.toString(), words))
            }
        }
        return out.sortedBy { it.startTimeMs }
    }

    /** 将旧版 LRC 时间戳 [mm:ss:xx] 规范化为 [mm:ss.xx]，分钟补零 */
    private fun normalizeLegacyLrcTimestamps(content: String): String {
        if (content.isEmpty()) return content
        return LEGACY_LRC_TIMESTAMP_REGEX.replace(content) { match ->
            val minutes = match.groupValues[1].padStart(2, '0')
            val seconds = match.groupValues[2]
            val fraction = match.groupValues[3]
            "[$minutes:$seconds.$fraction]"
        }
    }

    /** 解析 LRC 逐行歌词（跳过空行和元数据行） */
    private fun parseLrc(lrc: String): List<LyricEntry> {
        val normalizedLrc = normalizeLegacyLrcTimestamps(lrc)
        val tag = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{2,3}))?]""")
        val timeline = mutableListOf<Pair<Long, String>>()

        normalizedLrc.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            // 跳过 JSON/元数据片段
            if (line.startsWith("{") || line.startsWith("}")) return@forEach
            if (line.startsWith("[ti:") || line.startsWith("[ar:") || line.startsWith("[al:") ||
                line.startsWith("[by:") || line.startsWith("[offset:")) return@forEach

            val m = tag.find(line) ?: return@forEach
            val mm = m.groupValues[1].toInt()
            val ss = m.groupValues[2].toInt()
            val msStr = m.groupValues.getOrNull(3).orEmpty()
            val ms = when (msStr.length) {
                0 -> 0
                2 -> msStr.toInt() * 10
                else -> msStr.toInt()
            }
            val time = mm * 60_000L + ss * 1_000L + ms
            val text = line.substring(m.range.last + 1).trim()
            if (text.isNotEmpty()) {
                timeline.add(time to text)
            }
        }

        timeline.sortBy { it.first }
        val out = mutableListOf<LyricEntry>()
        for (i in timeline.indices) {
            val (start, text) = timeline[i]
            val end = if (i < timeline.lastIndex) timeline[i + 1].first else start + 5_000L
            out.add(LyricEntry(start, end, text))
        }
        return out
    }
}
