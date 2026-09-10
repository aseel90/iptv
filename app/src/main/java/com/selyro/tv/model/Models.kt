package com.selyro.tv.model

enum class SourceType { XTREAM, M3U }

data class PlaylistAccount(
    val name: String,
    val server: String,
    val username: String = "",
    val password: String = "",
    val type: SourceType = SourceType.XTREAM
)

data class Channel(
    val id: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = "Other",
    val epgId: String? = null,
    val number: Int? = null
)

data class VodItem(
    val id: String,
    val name: String,
    val streamUrl: String,
    val poster: String? = null,
    val category: String = "Other",
    val plot: String? = null,
    val year: String? = null,
    val rating: String? = null
)

data class SeriesItem(
    val id: String,
    val name: String,
    val poster: String? = null,
    val category: String = "Other",
    val plot: String? = null,
    val year: String? = null,
    val rating: String? = null
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val episode: Int,
    val streamUrl: String,
    val containerExtension: String = "mp4",
    val plot: String? = null,
    val duration: String? = null
)

data class SeriesDetails(
    val series: SeriesItem,
    val episodes: List<Episode>
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val start: Long,
    val end: Long,
    val description: String? = null
)

data class ProviderInfo(
    val username: String? = null,
    val status: String? = null,
    val expiresAt: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int? = null
)
