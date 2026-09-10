package com.selyro.tv.data

import android.content.Context
import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
import com.selyro.tv.player.StreamingProfile
import org.json.JSONArray
import org.json.JSONObject

enum class AppLanguage { ENGLISH, ARABIC }
enum class DisplayMode { LIST, GRID }

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

    fun playbackProfile(): StreamingProfile = runCatching {
        StreamingProfile.valueOf(prefs.getString("playback_profile", StreamingProfile.BALANCED.name).orEmpty())
    }.getOrDefault(StreamingProfile.BALANCED)
    fun setPlaybackProfile(profile: StreamingProfile) { prefs.edit().putString("playback_profile", profile.name).apply() }

    fun language(): AppLanguage = runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.ENGLISH.name).orEmpty()) }.getOrDefault(AppLanguage.ENGLISH)
    fun setLanguage(value: AppLanguage) { prefs.edit().putString("language", value.name).apply() }

    fun displayMode(): DisplayMode = runCatching { DisplayMode.valueOf(prefs.getString("display_mode", DisplayMode.LIST.name).orEmpty()) }.getOrDefault(DisplayMode.LIST)
    fun setDisplayMode(value: DisplayMode) { prefs.edit().putString("display_mode", value.name).apply() }
}
