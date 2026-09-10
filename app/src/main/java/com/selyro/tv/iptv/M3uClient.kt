package com.selyro.tv.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object M3uClient {
    suspend fun fetch(url: String): M3uPlaylist = withContext(Dispatchers.IO) {
        val connection = URL(url.trim()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 25_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "SelyroTV/1.0")
            val code = connection.responseCode
            if (code !in 200..299) error("Playlist HTTP $code")
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val playlist = M3uParser.parsePlaylist(text)
            if (playlist.channels.isEmpty()) error("Playlist contains no playable channels")
            playlist
        } finally {
            connection.disconnect()
        }
    }
}
