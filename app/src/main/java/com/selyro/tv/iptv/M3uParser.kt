package com.selyro.tv.iptv

data class IptvChannel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val tvgId: String? = null
)

object M3uParser {
    fun parse(text: String): List<IptvChannel> {
        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        val channels = ArrayList<IptvChannel>()
        var pendingInfo: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line
                line.startsWith("#") -> Unit
                pendingInfo != null -> {
                    val info = pendingInfo!!
                    channels += IptvChannel(
                        name = info.substringAfterLast(',').trim().ifEmpty { "Unnamed channel" },
                        url = line,
                        group = attribute(info, "group-title"),
                        logo = attribute(info, "tvg-logo"),
                        tvgId = attribute(info, "tvg-id")
                    )
                    pendingInfo = null
                }
            }
        }
        return channels
    }

    private fun attribute(line: String, key: String): String? {
        val marker = "$key=\""
        val start = line.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val valueStart = start + marker.length
        val end = line.indexOf('"', valueStart)
        return if (end > valueStart) line.substring(valueStart, end) else null
    }
}
