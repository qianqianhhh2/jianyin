package com.qian.jianyin.netease

internal object JsonUtil {
    fun toJson(map: Map<String, Any>): String {
        return toJsonObject(map)
    }

    private fun toJsonObject(map: Map<*, *>): String {
        val sb = StringBuilder()
        sb.append("{")
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val (k, v) = it.next()
            sb.append(jsonQuote(k?.toString()))
            sb.append(":")
            sb.append(toJsonValue(v))
            if (it.hasNext()) sb.append(",")
        }
        sb.append("}")
        return sb.toString()
    }

    fun toJsonValue(v: Any?): String = when (v) {
        null -> "null"
        is String -> jsonQuote(v)
        is Number, is Boolean -> v.toString()
        is Map<*, *> -> toJsonObject(v)
        is List<*> -> v.joinToString(prefix = "[", postfix = "]") { toJsonValue(it) }
        else -> jsonQuote(v.toString())
    }

    fun jsonQuote(s: String?): String {
        if (s == null) return "null"
        val sb = StringBuilder(s.length + 16)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"'  -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch < ' ') {
                        sb.append(String.format("\\u%04x", ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }
}