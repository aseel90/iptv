package com.selyro.tv.data

import android.content.Context
import com.selyro.tv.model.PlaylistAccount
import com.selyro.tv.model.SourceType
import com.selyro.tv.player.StreamingProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AppLanguage { ENGLISH, ARABIC }
enum class DisplayMode { LIST, GRID }

data class PlaybackProgress(val positionMs: Long, val durationMs: Long, val updatedAtMs: Long = System.currentTimeMillis()) {
    val fraction: Float get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

class AccountStore(context: Context) {
    private val secrets by lazy(LazyThreadSafetyMode.NONE) { SecretStore() }
    private val prefs = context.getSharedPreferences("selyro", Context.MODE_PRIVATE)

    init {
        migrateAccountStateV3()
    }

    private fun legacyAccountKey(account: PlaylistAccount): String =
        "${account.type}:${account.server.trim()}:${account.username}"

    private fun normalized(account: PlaylistAccount, forceId: String? = null): PlaylistAccount =
        account.copy(id = forceId?.takeIf { it.isNotBlank() } ?: account.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString())

    fun save(account: PlaylistAccount): PlaylistAccount {
        val all = accounts().toMutableList()
        var saved = normalized(account)
        val byId = all.indexOfFirst { it.id == saved.id }
        val index = if (byId >= 0) byId else all.indexOfFirst { legacyAccountKey(it) == legacyAccountKey(saved) }
        if (index >= 0) {
            saved = saved.copy(id = all[index].id)
            all[index] = saved
        } else {
            all += saved
        }
        writeAccounts(all)
        prefs.edit().putString(ACTIVE_ACCOUNT_ID, saved.id).apply()
        writeLegacy(saved)
        return saved
    }

    fun replace(original: PlaylistAccount, updated: PlaylistAccount): PlaylistAccount {
        val all = accounts().toMutableList()
        val byId = all.indexOfFirst { it.id == original.id }
        val index = if (byId >= 0) byId else all.indexOfFirst { legacyAccountKey(it) == legacyAccountKey(original) }
        if (index < 0) return save(updated)

        val persistedId = all[index].id
        val saved = normalized(updated, persistedId)
        all[index] = saved
        writeAccounts(all)

        if (prefs.getString(ACTIVE_ACCOUNT_ID, null) == persistedId) {
            writeLegacy(saved)
        }
        return saved
    }

    fun load(): PlaylistAccount? {
        val all = accounts()
        if (all.isEmpty()) return null
        val activeId = prefs.getString(ACTIVE_ACCOUNT_ID, null)
        return all.firstOrNull { it.id == activeId } ?: all.first().also {
            prefs.edit().putString(ACTIVE_ACCOUNT_ID, it.id).apply()
        }
    }

    fun accounts(): List<PlaylistAccount> = readAccountsV3()

    fun setActive(account: PlaylistAccount) {
        val all = accounts()
        val saved = all.firstOrNull { it.id == account.id }
            ?: all.firstOrNull { legacyAccountKey(it) == legacyAccountKey(account) }
            ?: return
        prefs.edit().putString(ACTIVE_ACCOUNT_ID, saved.id).apply()
        writeLegacy(saved)
    }

    fun remove(account: PlaylistAccount) {
        val all = accounts()
        val byId = all.indexOfFirst { it.id == account.id }
        val removeIndex = if (byId >= 0) byId else all.indexOfFirst { legacyAccountKey(it) == legacyAccountKey(account) }
        if (removeIndex < 0) return

        val removed = all[removeIndex]
        val wasActive = prefs.getString(ACTIVE_ACCOUNT_ID, null) == removed.id
        val remaining = all.toMutableList().also { it.removeAt(removeIndex) }
        clearScopedState(removed.id)
        writeAccounts(remaining)

        if (remaining.isEmpty()) {
            prefs.edit().remove(ACTIVE_ACCOUNT_ID).apply()
            clearLegacy()
            return
        }

        if (wasActive) {
            val next = remaining[removeIndex.coerceAtMost(remaining.lastIndex)]
            prefs.edit().putString(ACTIVE_ACCOUNT_ID, next.id).apply()
            writeLegacy(next)
        }
    }

    private fun readAccountsV3(): List<PlaylistAccount> {
        val raw = prefs.getString(ACCOUNTS_V3, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            var needsRewrite = false
            val result = buildList {
                repeat(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    val server = o.optString("server")
                    if (server.isBlank()) return@repeat
                    val id = o.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also { needsRewrite = true }
                    add(
                        PlaylistAccount(
                            name = o.optString("name", "My IPTV"),
                            server = server,
                            username = decryptOrBlank(o.optString("user")),
                            password = decryptOrBlank(o.optString("pass")),
                            type = runCatching { SourceType.valueOf(o.optString("type", SourceType.XTREAM.name)) }.getOrDefault(SourceType.XTREAM),
                            id = id
                        )
                    )
                }
            }
            if (needsRewrite) writeAccounts(result)
            result
        }.getOrDefault(emptyList())
    }

    private fun readAccountsV2(): List<PlaylistAccount> {
        val raw = prefs.getString(LEGACY_ACCOUNTS_V2, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    val server = o.optString("server")
                    if (server.isBlank()) return@repeat
                    add(
                        PlaylistAccount(
                            name = o.optString("name", "My IPTV"),
                            server = server,
                            username = decryptOrBlank(o.optString("user")),
                            password = decryptOrBlank(o.optString("pass")),
                            type = runCatching { SourceType.valueOf(o.optString("type", SourceType.XTREAM.name)) }.getOrDefault(SourceType.XTREAM)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAccounts(all: List<PlaylistAccount>) {
        val arr = JSONArray()
        all.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("name", a.name)
                put("server", a.server.trim())
                put("user", secrets.encrypt(a.username))
                put("pass", secrets.encrypt(a.password))
                put("type", a.type.name)
            })
        }
        prefs.edit().putString(ACCOUNTS_V3, arr.toString()).apply()
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

    private fun migrateAccountStateV3() {
        if (prefs.getBoolean(MIGRATED_V3, false)) return

        val existingV3 = readAccountsV3()
        val migratedAccounts = when {
            existingV3.isNotEmpty() -> existingV3
            else -> readAccountsV2().ifEmpty { loadLegacy()?.let(::listOf).orEmpty() }
        }

        if (migratedAccounts.isNotEmpty()) {
            writeAccounts(migratedAccounts)
            val legacyIndex = prefs.getInt(LEGACY_ACTIVE_INDEX, 0).coerceIn(0, migratedAccounts.lastIndex)
            val activeId = prefs.getString(ACTIVE_ACCOUNT_ID, null)
                ?.takeIf { id -> migratedAccounts.any { it.id == id } }
                ?: migratedAccounts[legacyIndex].id
            prefs.edit().putString(ACTIVE_ACCOUNT_ID, activeId).apply()
            migrateLegacyScopedState(activeId)
            migratedAccounts.firstOrNull { it.id == activeId }?.let(::writeLegacy)
        } else {
            prefs.edit()
                .remove(LEGACY_FAVORITES)
                .remove(LEGACY_RECENT)
                .remove(LEGACY_PROGRESS)
                .apply()
        }

        prefs.edit()
            .putBoolean(MIGRATED_V3, true)
            .remove(LEGACY_ACCOUNTS_V2)
            .remove(LEGACY_ACTIVE_INDEX)
            .apply()
    }

    private fun migrateLegacyScopedState(accountId: String) {
        val favoriteTarget = favoritesKey(accountId)
        val recentTarget = recentsKey(accountId)
        val progressTarget = progressKey(accountId)
        val editor = prefs.edit()

        if (!prefs.contains(favoriteTarget)) {
            editor.putStringSet(favoriteTarget, prefs.getStringSet(LEGACY_FAVORITES, emptySet()).orEmpty().toSet())
        }
        if (!prefs.contains(recentTarget)) {
            editor.putString(recentTarget, prefs.getString(LEGACY_RECENT, "").orEmpty())
        }
        if (!prefs.contains(progressTarget)) {
            editor.putString(progressTarget, prefs.getString(LEGACY_PROGRESS, "{}").orEmpty().ifBlank { "{}" })
        }

        editor.remove(LEGACY_FAVORITES).remove(LEGACY_RECENT).remove(LEGACY_PROGRESS).apply()
    }

    private fun decryptOrBlank(value: String): String =
        if (value.isBlank()) "" else runCatching { secrets.decrypt(value) }.getOrDefault("")

    private fun readSecret(encryptedKey: String, legacyKey: String): String {
        val encrypted = prefs.getString(encryptedKey, null)
        val legacy = prefs.getString(legacyKey, "").orEmpty()
        if (encrypted.isNullOrEmpty()) return legacy
        return runCatching { secrets.decrypt(encrypted) }.getOrDefault(legacy)
    }

    fun clearAccount() {
        accounts().forEach { clearScopedState(it.id) }
        prefs.edit()
            .remove(ACCOUNTS_V3)
            .remove(LEGACY_ACCOUNTS_V2)
            .remove(ACTIVE_ACCOUNT_ID)
            .remove(LEGACY_ACTIVE_INDEX)
            .remove(LEGACY_FAVORITES)
            .remove(LEGACY_RECENT)
            .remove(LEGACY_PROGRESS)
            .apply()
        clearLegacy()
    }

    private fun clearLegacy() {
        prefs.edit()
            .remove("name").remove("server").remove("user").remove("pass")
            .remove("user_enc").remove("pass_enc").remove("source_type")
            .apply()
    }

    private fun currentAccountId(): String? =
        prefs.getString(ACTIVE_ACCOUNT_ID, null)?.takeIf { it.isNotBlank() }

    private fun favoritesKey(accountId: String) = "favorites_v2.$accountId"
    private fun recentsKey(accountId: String) = "recent_v2.$accountId"
    private fun progressKey(accountId: String) = "playback_progress_v2.$accountId"

    private fun clearScopedState(accountId: String) {
        if (accountId.isBlank()) return
        prefs.edit()
            .remove(favoritesKey(accountId))
            .remove(recentsKey(accountId))
            .remove(progressKey(accountId))
            .apply()
    }

    fun setFavorite(key: String, enabled: Boolean) {
        val accountId = currentAccountId() ?: return
        val prefKey = favoritesKey(accountId)
        val set = prefs.getStringSet(prefKey, emptySet()).orEmpty().toMutableSet()
        if (enabled) set.add(key) else set.remove(key)
        prefs.edit().putStringSet(prefKey, set).apply()
    }

    fun favorites(): Set<String> {
        val accountId = currentAccountId() ?: return emptySet()
        return prefs.getStringSet(favoritesKey(accountId), emptySet()).orEmpty().toSet()
    }

    fun addRecent(key: String) {
        val accountId = currentAccountId() ?: return
        val prefKey = recentsKey(accountId)
        val list = prefs.getString(prefKey, "").orEmpty()
            .split('|')
            .filter { it.isNotBlank() && it != key }
            .toMutableList()
        list.add(0, key)
        prefs.edit().putString(prefKey, list.take(50).joinToString("|")).apply()
    }

    fun recents(): List<String> {
        val accountId = currentAccountId() ?: return emptyList()
        return prefs.getString(recentsKey(accountId), "").orEmpty().split('|').filter { it.isNotBlank() }
    }

    fun playbackProfile(): StreamingProfile = runCatching {
        StreamingProfile.valueOf(prefs.getString("playback_profile", StreamingProfile.BALANCED.name).orEmpty())
    }.getOrDefault(StreamingProfile.BALANCED)

    fun setPlaybackProfile(profile: StreamingProfile) {
        prefs.edit().putString("playback_profile", profile.name).apply()
    }

    fun language(): AppLanguage =
        runCatching { AppLanguage.valueOf(prefs.getString("language", AppLanguage.ENGLISH.name).orEmpty()) }.getOrDefault(AppLanguage.ENGLISH)

    fun setLanguage(value: AppLanguage) {
        prefs.edit().putString("language", value.name).apply()
    }

    fun displayMode(): DisplayMode =
        runCatching { DisplayMode.valueOf(prefs.getString("display_mode", DisplayMode.LIST.name).orEmpty()) }.getOrDefault(DisplayMode.LIST)

    fun setDisplayMode(value: DisplayMode) {
        prefs.edit().putString("display_mode", value.name).apply()
    }

    fun playbackProgress(): Map<String, PlaybackProgress> {
        val accountId = currentAccountId() ?: return emptyMap()
        val raw = prefs.getString(progressKey(accountId), null) ?: return emptyMap()
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
        val accountId = currentAccountId() ?: return
        val prefKey = progressKey(accountId)
        val safePosition = positionMs.coerceAtLeast(0L)
        val safeDuration = durationMs.coerceAtLeast(0L)
        val root = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
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
        prefs.edit().putString(prefKey, root.toString()).apply()
    }

    fun clearPlaybackProgress(key: String) {
        val accountId = currentAccountId() ?: return
        val prefKey = progressKey(accountId)
        val root = runCatching { JSONObject(prefs.getString(prefKey, "{}") ?: "{}") }.getOrDefault(JSONObject())
        root.remove(key)
        prefs.edit().putString(prefKey, root.toString()).apply()
    }

    private companion object {
        const val ACCOUNTS_V3 = "accounts_v3"
        const val ACTIVE_ACCOUNT_ID = "active_account_id"
        const val MIGRATED_V3 = "account_state_v3_migrated"

        const val LEGACY_ACCOUNTS_V2 = "accounts_v2"
        const val LEGACY_ACTIVE_INDEX = "active_account"
        const val LEGACY_FAVORITES = "favorites"
        const val LEGACY_RECENT = "recent"
        const val LEGACY_PROGRESS = "playback_progress_v1"
    }
}
