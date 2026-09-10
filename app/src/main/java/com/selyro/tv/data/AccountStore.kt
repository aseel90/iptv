package com.selyro.tv.data

import android.content.Context
import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
import com.selyro.tv.player.StreamingProfile

class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("selyro", Context.MODE_PRIVATE)

    fun save(account: PlaylistAccount) {
        prefs.edit()
            .putString("name", account.name)
            .putString("server", account.server.trim())
            .putString("user", account.username)
            .putString("pass", account.password)
            .putString("source_type", account.type.name)
            .apply()
    }

    fun load(): PlaylistAccount? {
        val server = prefs.getString("server", null) ?: return null
        val type = runCatching {
            SourceType.valueOf(prefs.getString("source_type", SourceType.XTREAM.name).orEmpty())
        }.getOrDefault(SourceType.XTREAM)
        return PlaylistAccount(
            name = prefs.getString("name", "My IPTV") ?: "My IPTV",
            server = server,
            username = prefs.getString("user", "").orEmpty(),
            password = prefs.getString("pass", "").orEmpty(),
            type = type
        )
    }

    fun clearAccount() {
        prefs.edit()
            .remove("name").remove("server").remove("user").remove("pass").remove("source_type")
            .apply()
    }

    fun setFavorite(key: String, enabled: Boolean) {
        val set = prefs.getStringSet("favorites", emptySet()).orEmpty().toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        prefs.edit().putStringSet("favorites", set).apply()
    }

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun addRecent(key: String) {
        val list = prefs.getString("recent", "").orEmpty()
            .split('|').filter { it.isNotBlank() && it != key }.toMutableList()
        list.add(0, key)
        prefs.edit().putString("recent", list.take(50).joinToString("|")).apply()
    }

    fun recents(): List<String> = prefs.getString("recent", "").orEmpty()
        .split('|').filter { it.isNotBlank() }

    fun playbackProfile(): StreamingProfile = runCatching {
        StreamingProfile.valueOf(prefs.getString("playback_profile", StreamingProfile.BALANCED.name).orEmpty())
    }.getOrDefault(StreamingProfile.BALANCED)

    fun setPlaybackProfile(profile: StreamingProfile) {
        prefs.edit().putString("playback_profile", profile.name).apply()
    }
}
