package com.selyro.tv.iptv

import com.selyro.tv.model.Channel
import java.util.concurrent.ConcurrentHashMap

data class M3uPlaylist(
    val channels: List<Channel>,
    val epgUrl: String? = null
)

object M3uParser {
    private val attributeRegexes = ConcurrentHashMap<String, Regex>()

    fun parse(text: String): List<Channel> = parsePlaylist(text).channels

    fun parsePlaylist(text: String): M3uPlaylist {
        val channels = ArrayList<Channel>()
        var pendingInfo: String? = null
        var epgUrl: String? = null
        var fallbackId = 0

        text.lineSequence().forEach { raw ->
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    epgUrl = attribute(line, "x-tvg-url")
                        ?: attribute(line, "url-tvg")
                        ?: epgUrl
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line
                line.startsWith("#") -> Unit
                pendingInfo != null && looksLikeStreamUrl(line) -> {
                    val info = pendingInfo.orEmpty()
                    val tvgId = attribute(info, "tvg-id")
                    val name = info.substringAfterLast(',').trim().ifEmpty {
                        attribute(info, "tvg-name") ?: "Unnamed channel"
                    }
                    val id = tvgId?.takeIf { it.isNotBlank() }
                        ?: "m3u-${fallbackId++}-${stableHash(line)}"
                    channels += Channel(
                        id = id,
                        name = name,
                        url = line,
                        logo = attribute(info, "tvg-logo"),
                        group = attribute(info, "group-title")?.ifBlank { "Other" } ?: "Other",
                        epgId = tvgId,
                        number = attribute(info, "tvg-chno")?.toIntOrNull()
                    )
                    pendingInfo = null
                }
            }
        }

        return M3uPlaylist(channels = channels, epgUrl = epgUrl)
    }

    private fun looksLikeStreamUrl(value: String): Boolean {
        val lower = value.lowercase()
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.startsWith("rtsp://") ||
            lower.startsWith("rtmp://") ||
            lower.startsWith("udp://") ||
            lower.startsWith("rtp://")
    }

    private fun attribute(line: String, key: String): String? {
        val regex = attributeRegexes.getOrPut(key.lowercase()) {
            Regex("(?i)(?:^|\\s)${Regex.escape(key)}\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s]+))")
        }
        val match = regex.find(line) ?: return null
        return (match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun stableHash(value: String): String = value.hashCode().toUInt().toString(16)
}
