package us.shandian.giga.get

/** Pure Kotlin (no Android types) so it can be unit tested. Builds a small flat JSON object. */
object SidecarJson {
    @JvmStatic
    fun build(fields: Map<String, String>): String {
        val sb = StringBuilder("{\n")
        var first = true
        for ((k, v) in fields) {
            if (v.isBlank()) continue // only fields that really exist
            if (!first) sb.append(",\n")
            first = false
            sb.append("  \"").append(escape(k)).append("\": \"").append(escape(v)).append('"')
        }
        sb.append("\n}\n")
        return sb.toString()
    }

    @JvmStatic
    fun escape(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun channelIdFromUrl(url: String?): String? =
        url?.let { Regex("/channel/(UC[0-9A-Za-z_-]{20,})").find(it)?.groupValues?.get(1) }
}
