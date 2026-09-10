package com.selyro.tv.iptv

import android.util.Base64
import com.selyro.tv.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class XtreamClient(private val account: PlaylistAccount) {
    private val base = account.server.trim().trimEnd('/')

    private suspend fun get(action: String? = null, extra: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val query = buildList {
                add("username=${enc(account.username)}")
                add("password=${enc(account.password)}")
                if (!action.isNullOrBlank()) add("action=${enc(action)}")
                extra.forEach { (k, v) -> add("${enc(k)}=${enc(v)}") }
            }.joinToString("&")
            val connection = URL("$base/player_api.php?$query").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 20_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "SelyroTV/1.0")
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                if (code !in 200..299) error("Provider HTTP $code")
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun authenticate(): ProviderInfo? = runCatching {
        val root = JSONObject(get())
        val user = root.optJSONObject("user_info") ?: return@runCatching null
        if (user.optInt("auth", 0) != 1) return@runCatching null
        ProviderInfo(
            username = user.optString("username").takeIf { it.isNotBlank() },
            status = user.optString("status").takeIf { it.isNotBlank() },
            expiresAt = user.optString("exp_date").toLongOrNull()?.times(1000),
            maxConnections = user.optString("max_connections").toIntOrNull(),
            activeConnections = user.optString("active_cons").toIntOrNull()
        )
    }.getOrNull()

    suspend fun live(): List<Channel> {
        val categories = categories("get_live_categories")
        val arr = JSONArray(get("get_live_streams"))
        return List(arr.length()) { index ->
            val o = arr.getJSONObject(index)
            val id = o.optString("stream_id")
            val categoryId = o.optString("category_id")
            Channel(
                id = id,
                name = o.optString("name", "Channel"),
                url = "$base/live/${path(account.username)}/${path(account.password)}/$id.ts",
                logo = o.optString("stream_icon").takeIf { it.isNotBlank() },
                group = categories[categoryId] ?: "Other",
                epgId = o.optString("epg_channel_id").takeIf { it.isNotBlank() },
                number = o.optInt("num", -1).takeIf { it >= 0 }
            )
        }
    }

    suspend fun movies(): List<VodItem> {
        val categories = categories("get_vod_categories")
        val arr = JSONArray(get("get_vod_streams"))
        return List(arr.length()) { index ->
            val o = arr.getJSONObject(index)
            val id = o.optString("stream_id")
            val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
            val categoryId = o.optString("category_id")
            VodItem(
                id = id,
                name = o.optString("name", "Movie"),
                streamUrl = "$base/movie/${path(account.username)}/${path(account.password)}/$id.$ext",
                poster = o.optString("stream_icon").takeIf { it.isNotBlank() },
                category = categories[categoryId] ?: "Other",
                plot = o.optString("plot").takeIf { it.isNotBlank() },
                year = o.optString("year").takeIf { it.isNotBlank() },
                rating = o.optString("rating").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun series(): List<SeriesItem> {
        val categories = categories("get_series_categories")
        val arr = JSONArray(get("get_series"))
        return List(arr.length()) { index ->
            val o = arr.getJSONObject(index)
            val categoryId = o.optString("category_id")
            SeriesItem(
                id = o.optString("series_id"),
                name = o.optString("name", "Series"),
                poster = o.optString("cover").takeIf { it.isNotBlank() },
                category = categories[categoryId] ?: "Other",
                plot = o.optString("plot").takeIf { it.isNotBlank() },
                year = o.optString("releaseDate").take(4).takeIf { it.length == 4 },
                rating = o.optString("rating").takeIf { it.isNotBlank() }
            )
        }
    }

    suspend fun seriesDetails(series: SeriesItem): SeriesDetails {
        val root = JSONObject(get("get_series_info", mapOf("series_id" to series.id)))
        val episodesObject = root.optJSONObject("episodes") ?: JSONObject()
        val episodes = mutableListOf<Episode>()
        val seasonKeys = episodesObject.keys().asSequence().toList().sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        for (seasonKey in seasonKeys) {
            val seasonNumber = seasonKey.toIntOrNull() ?: 0
            val array = episodesObject.optJSONArray(seasonKey) ?: continue
            repeat(array.length()) { index ->
                val o = array.getJSONObject(index)
                val id = o.optString("id")
                val ext = o.optString("container_extension", "mp4").ifBlank { "mp4" }
                val info = o.optJSONObject("info")
                episodes += Episode(
                    id = id,
                    title = o.optString("title").ifBlank { "Episode ${o.optInt("episode_num", index + 1)}" },
                    season = seasonNumber,
                    episode = o.optInt("episode_num", index + 1),
                    streamUrl = "$base/series/${path(account.username)}/${path(account.password)}/$id.$ext",
                    containerExtension = ext,
                    plot = info?.optString("plot")?.takeIf { it.isNotBlank() },
                    duration = info?.optString("duration")?.takeIf { it.isNotBlank() }
                )
            }
        }
        return SeriesDetails(series, episodes)
    }

    suspend fun shortEpg(streamId: String, limit: Int = 4): List<EpgProgram> = runCatching {
        val root = JSONObject(get("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString())))
        val arr = root.optJSONArray("epg_listings") ?: JSONArray()
        List(arr.length()) { index ->
            val o = arr.getJSONObject(index)
            EpgProgram(
                channelId = streamId,
                title = decodeMaybeBase64(o.optString("title")),
                start = o.optLong("start_timestamp", 0L) * 1000L,
                end = o.optLong("stop_timestamp", 0L) * 1000L,
                description = decodeMaybeBase64(o.optString("description")).takeIf { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyList())

    private suspend fun categories(action: String): Map<String, String> = runCatching {
        val arr = JSONArray(get(action))
        buildMap {
            repeat(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                put(o.optString("category_id"), o.optString("category_name", "Other"))
            }
        }
    }.getOrDefault(emptyMap())

    private fun decodeMaybeBase64(value: String): String {
        if (value.isBlank()) return ""
        return runCatching { String(Base64.decode(value, Base64.DEFAULT), Charsets.UTF_8) }
            .getOrDefault(value)
            .ifBlank { value }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun path(value: String) = enc(value).replace("+", "%20")
}
