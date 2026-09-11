package com.selyro.tv.data

import android.content.Context
import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
import com.selyro.tv.player.StreamingProfile
import org.json.JSONArray
import org.json.JSONObject

enum class AppLanguage { ENGLISH, ARABIC }
enum class DisplayMode { LIST, GRID }

data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val updatedAtMs: Long = System.currentTimeMillis()) {
    val fraction: Float get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

class AccountStore(context: Context) {
    private val secrets by lazy(LazyThreadSafetyMode.NONE) { SecretStore() }
    private val prefs = context.getSharedPreferences("selyro", Context.MODE_PRIVATE)

    fun save(account: PlaylistAccount) {
        val all = accounts().toMutableList()
        val key: (PlaylistAccount) -> String = { "${it.type}:${it.server.trim()}:${it.username}" }
        val index = all.indexOfFirst { key(it) == key(account) }
        if (index >= 0) all[index] = account else all += account
        writeAccounts(all)
        prefs.edit().putInt("active_account", all.indexOfFirst { key(it) == key(account) }).apply()
        writeLegacy(account)
    }

    fun load(): PlaylistAccount? {
        val all = accounts()
        if (all.isNotEmpty()) return all.getOrNull(prefs.getInt("active_account", 0)) ?: all.first()
        return loadLegacy()?.also { save(it) }
    }

    fun accounts(): List<PlaylistAccount> {
        val raw = prefs.getString("accounts_v2", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    add(PlaylistAccount(
                        name = o.optString("name", "My IPTV"),
                        server = o.optString("server"),
                        username = decryptOrBlank(o.optString("user")),
                        password = decryptOrBlank(o.optString("pass")),
                        type = runCatching { SourceType.valueOf(o.optString("type", SourceType.XTREAM.name)) }.getOrDefault(SourceType.XTREAM)
                    ))
                }
            }.filter { it.server.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun setActive(account: PlaylistAccount) {
        val all = accounts()
        val index = all.indexOfFirst { it.type == account.type && it.server.trim() == account.server.trim() && it.username == account.username }
        if (index >= 0) prefs.edit().putInt("active_account", index).apply()
        writeLegacy(account)
    }

    fun remove(account: PlaylistAccount) {
        val all = accounts().filterNot { it.type == account.type && it.server.trim() == account.server.trim() && it.username == account.username }
        writeAccounts(all)
        prefs.edit().putInt("active_account", 0).apply()
        if (all.isEmpty()) clearLegacy() else writeLegacy(all.first())
    }

    private fun writeAccounts(all: List<PlaylistAccount>) {
        val arr = JSONArray()
        all.forEach { a ->
            arr.put(JSONObject().apply {
                put("name", a.name)
                put("server", a.server.trim())
                put("user", secrets.encrypt(a.username))
                put("pass", secrets.encrypt(a.password))
                put("type", a.type.name)
            })
        }
        prefs.edit().putString("accounts_v2", arr.toString()).apply()
    }

    private fun writeLegacy(account: PlaylistAccount) {
        prefs.edit()
            .putString("name", account.name)
            .putString("server", account.server.trim())
            .putString("user_enc", secrets.encrypt(account.username))
            .putString("pass_enc", secrets.encrypt(account.password))
            .remove("user").remove("pass")
            .putString("source_type", account.type.name)
            .apply()
    }

    private fun loadLegacy(): PlaylistAccount? {
        val server = prefs.getString("server", null) ?: return null
        val type = runCatching { SourceType.valueOf(prefs.getString("source_type", SourceType.XTREAM.name).orEmpty()) }.getOrDefault(SourceType.XTREAM)
        return PlaylistAccount(
            name = prefs.getString("name", "My IPTV") ?: "My IPTV",
            server = server,
            username = readSecret("user_enc", "user"),
            password = readSecret("pass_enc", "pass"),
            type = type
        )
    }

    private fun decryptOrBlank(value: String): String = if (value.isBlank()) "" else runCatching { secrets.decrypt(value) }.getOrDefault("")

    private fun readSecret(encryptedKey: String, legacyKey: String): String {
        val encrypted = prefs.getString(encryptedKey, null)
        val legacy = prefs.getString(legacyKey, "").orEmpty()
        if (encrypted.isNullOrEmpty()) return legacy
        return runCatching { secrets.decrypt(encrypted) }.getOrDefault(legacy)
    }

    fun clearAccount() {
        prefs.edit().remove("accounts_v2").remove("active_account").apply()
        clearLegacy()
    }

    private fun clearLegacy() {
        prefs.edit().remove("name").remove("server").remove("user").remove("pass").remove("user_enc").remove("pass_enc").remove("source_type").apply()
    }

    fun setFavorite(key: String, enabled: Boolean) {
        val set = prefs.getStringSet("favorites", emptySet()).orEmpty().toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        prefs.edit().putStringSet("favorites", set).apply()
    }
    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun addRecent(key: String) {
        val list = prefs.getString("recent", "").orEmpty().split('|').filter { it.isNotBlank() && it != key }.toMutableList()
        list.add(0, key)
        prefs.edit().putString("recent", list.take(50).joinToString("|")).apply()
    }
    fun recents(): List<String> = prefs.getString("recent", "").orEmpty().split('|').filter { it.isNotBlank() }

    private fun accountSuffix(account: PlaylistAccount): String =
        "${account.type}:${account.server.trim()}:${account.username}".hashCode().toString()

    fun setLastLive(account: PlaylistAccount, channelId: String, group: String) {
        if (channelId.isBlank()) return
        val suffix = accountSuffix(account)
        prefs.edit()
            .putString("last_live_id_$suffix", channelId)
            .putString("last_live_group_$suffix", group)
            .apply()
    }

    fun lastLiveId(account: PlaylistAccount?): String? {
        account ?: return null
        return prefs.getString("last_live_id_${accountSuffix(account)}", null)?.takeIf { it.isNotBlank() }
    }

    fun lastLiveGroup(account: PlaylistAccount?): String? {
        account ?: return null
        return prefs.getString("last_live_group_${accountSuffix(account)}", null)?.takeIf { it.isNotBlank() }
    }

    fun addSearchTerm(term: String) {
        val clean = term.trim()
        if (clean.length < 2) return
        val list = searchHistory().filterNot { it.equals(clean, ignoreCase = true) }.toMutableList()
        list.add(0, clean)
        prefs.edit().putString("search_history_v1", list.take(10).joinToString("|")).apply()
    }

    fun searchHistory(): List<String> = prefs.getString("search_history_v1", "").orEmpty()
        .split('|').map { it.trim() }.filter { it.isNotBlank() }.take(10)

    fun clearSearchHistory() { prefs.edit().remove("search_history_v1").apply() }

    fun playbackProfile(): StreamingProfile = runCatching {
        StreamingProfile.valueOf(prefs.getString("playback_profile", StreamingProfile.BALANCED.name).orEmpty())
    }.getOrDefault(StreamingProfile.BALANCED)
    fun setPlaybackProfile(profile: StreamingProfile) { prefs.edit().putString("playback_profile", profile.name).apply() }

    fun language(): AppLanguage = runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.ENGLISH.name).orEmpty()) }.getOrDefault(AppLanguage.ENGLISH)
    fun setLanguage(value: AppLanguage) { prefs.edit().putString("language", value.name).apply() }

    fun displayMode(): DisplayMode = runCatching { DisplayMode.valueOf(prefs.getString("display_mode", DisplayMode.LIST.name).orEmpty()) }.getOrDefault(DisplayMode.LIST)
    fun setDisplayMode(value: DisplayMode) { prefs.edit().putString("display_mode", value.name).apply() }

    fun playbackProgress(): Map<String, PlaybackProgress> {
        val raw = prefs.getString("playback_progress_v1", null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                val keys = root.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = root.optJSONObject(key) ?: continue
                    val position = item.optLong("position", 0L).coerceAtLeast(0L)
                    val duration = item.optLong("duration", 0L).coerceAtLeast(0L)
                    val updated = item.optLong("updated", 0L)
                    if (position > 0L && duration > 0L) put(key, PlaybackProgress(position, duration, updated))
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun setPlaybackProgress(key: String, positionMs: Long, durationMs: Long) {
        if (key.isBlank()) return
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val root = runCatching { JSONObject(prefs.getString("playback_progress_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val completed = safeDuration > 0L && (safePosition >= safeDuration - 60_000L || safePosition.toDouble() / safeDuration >= 0.95)
        if (safeDuration <= 0L || safePosition < 10_000L || completed) {
            root.remove(key)
        } else {
            root.put(key, JSONObject().apply {
                put("position", safePosition)
                put("duration", safeDuration)
                put("updated", System.currentTimeMillis())
            })
        }

        if (root.length() > 200) {
            val ordered = mutableListOf<Pair<String, Long>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                ordered += k to (root.optJSONObject(k)?.optLong("updated", 0L) ?: 0L)
            }
            ordered.sortedByDescending { it.second }.drop(200).forEach { root.remove(it.first) }
        }
        prefs.edit().putString("playback_progress_v1", root.toString()).apply()
    }

    fun clearPlaybackProgress(key: String) {
        val root = runCatching { JSONObject(prefs.getString("playback_progress_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())
        root.remove(key)
        prefs.edit().putString("playback_progress_v1", root.toString()).apply()
    }
}
