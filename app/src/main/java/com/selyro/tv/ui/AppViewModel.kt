package com.selyro.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selyro.tv.data.AccountStore
import com.selyro.tv.data.AppLanguage
import com.selyro.tv.data.DisplayMode
import com.selyro.tv.data.PlaybackProgress
import com.selyro.tv.iptv.M3uClient
import com.selyro.tv.iptv.XtreamClient
import com.selyro.tv.model.*
import com.selyro.tv.player.StreamingProfile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AccountStore(app)

    private val _account = MutableStateFlow(store.load())
    val account = _account.asStateFlow()
    val accounts = MutableStateFlow(store.accounts())

    val channels = MutableStateFlow<List<Channel>>(emptyList())
    val movies = MutableStateFlow<List<VodItem>>(emptyList())
    val series = MutableStateFlow<List<SeriesItem>>(emptyList())
    val providerInfo = MutableStateFlow<ProviderInfo?>(null)
    val epgByChannel = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val selectedSeriesDetails = MutableStateFlow<SeriesDetails?>(null)
    val loading = MutableStateFlow(false)
    val loadingSection = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val favorites = MutableStateFlow(store.favorites())
    val recents = MutableStateFlow(store.recents())
    val playbackProfile = MutableStateFlow(store.playbackProfile())
    val language = MutableStateFlow(store.language())
    val displayMode = MutableStateFlow(store.displayMode())
    val playbackProgress = MutableStateFlow(store.playbackProgress())
    val addingAccount = MutableStateFlow(false)
    val lastLiveId = MutableStateFlow(store.lastLiveId(_account.value))
    val lastLiveGroup = MutableStateFlow(store.lastLiveGroup(_account.value))
    val searchHistory = MutableStateFlow(store.searchHistory())

    private val epgJobs = mutableMapOf<String, Job>()
    private val epgLoadedAt = mutableMapOf<String, Long>()
    private val epgCacheTtlMs = 10 * 60 * 1000L

    init {
        if (_account.value != null) loadLive()
    }

    fun login(account: PlaylistAccount) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                val loadedChannels = when (account.type) {
                    SourceType.XTREAM -> {
                        require(account.username.isNotBlank() && account.password.isNotBlank()) { "Username and password are required" }
                        val client = XtreamClient(account)
                        providerInfo.value = client.authenticate() ?: error("Server login failed")
                        client.live()
                    }
                    SourceType.M3U -> {
                        providerInfo.value = null
                        M3uClient.fetch(account.server).channels
                    }
                }
                require(loadedChannels.isNotEmpty()) { "No live channels were returned" }
                store.save(account)
                accounts.value = store.accounts()
                _account.value = account
                lastLiveId.value = store.lastLiveId(account)
                lastLiveGroup.value = store.lastLiveGroup(account)
                channels.value = loadedChannels
                movies.value = emptyList()
                series.value = emptyList()
                clearEpgCache()
                selectedSeriesDetails.value = null
                addingAccount.value = false
            }.onFailure { error.value = friendlyError(it) }
            loading.value = false
        }
    }

    fun beginAddAccount() {
        addingAccount.value = true
        _account.value = null
        lastLiveId.value = null
        lastLiveGroup.value = null
        error.value = null
    }

    fun cancelAddAccount() {
        addingAccount.value = false
        _account.value = store.load()
        lastLiveId.value = store.lastLiveId(_account.value)
        lastLiveGroup.value = store.lastLiveGroup(_account.value)
        if (_account.value != null && channels.value.isEmpty()) loadLive(force = true)
    }

    fun switchAccount(target: PlaylistAccount) {
        if (_account.value == target) return
        store.setActive(target)
        _account.value = target
        lastLiveId.value = store.lastLiveId(target)
        lastLiveGroup.value = store.lastLiveGroup(target)
        channels.value = emptyList()
        movies.value = emptyList()
        series.value = emptyList()
        providerInfo.value = null
        clearEpgCache()
        selectedSeriesDetails.value = null
        error.value = null
        loadLive(force = true)
    }

    fun removeAccount(target: PlaylistAccount) {
        val wasActive = _account.value == target
        store.remove(target)
        accounts.value = store.accounts()
        if (wasActive) {
            _account.value = store.load()
            lastLiveId.value = store.lastLiveId(_account.value)
            lastLiveGroup.value = store.lastLiveGroup(_account.value)
            channels.value = emptyList()
            movies.value = emptyList()
            series.value = emptyList()
            providerInfo.value = null
            clearEpgCache()
            selectedSeriesDetails.value = null
            if (_account.value != null) loadLive(force = true)
        }
    }

    fun loadLive(force: Boolean = false) {
        val account = _account.value ?: return
        if (!force && channels.value.isNotEmpty()) return
        viewModelScope.launch {
            loadingSection.value = "Live TV"
            error.value = null
            runCatching {
                when (account.type) {
                    SourceType.XTREAM -> {
                        val client = XtreamClient(account)
                        providerInfo.value = client.authenticate()
                        channels.value = client.live()
                    }
                    SourceType.M3U -> channels.value = M3uClient.fetch(account.server).channels
                }
            }.onFailure { error.value = friendlyError(it) }
            loadingSection.value = null
        }
    }

    fun ensureMovies() {
        val account = _account.value ?: return
        if (account.type != SourceType.XTREAM || movies.value.isNotEmpty() || loadingSection.value == "Movies") return
        viewModelScope.launch {
            loadingSection.value = "Movies"
            runCatching { movies.value = XtreamClient(account).movies() }
                .onFailure { error.value = friendlyError(it) }
            loadingSection.value = null
        }
    }

    fun ensureSeries() {
        val account = _account.value ?: return
        if (account.type != SourceType.XTREAM || series.value.isNotEmpty() || loadingSection.value == "Series") return
        viewModelScope.launch {
            loadingSection.value = "Series"
            runCatching { series.value = XtreamClient(account).series() }
                .onFailure { error.value = friendlyError(it) }
            loadingSection.value = null
        }
    }

    fun loadSeriesDetails(seriesItem: SeriesItem) {
        val account = _account.value ?: return
        if (account.type != SourceType.XTREAM) return
        viewModelScope.launch {
            loadingSection.value = "Episodes"
            runCatching { selectedSeriesDetails.value = XtreamClient(account).seriesDetails(seriesItem) }
                .onFailure { error.value = friendlyError(it) }
            loadingSection.value = null
        }
    }

    fun clearSeriesDetails() { selectedSeriesDetails.value = null }

    private fun clearEpgCache() {
        epgJobs.values.forEach { it.cancel() }
        epgJobs.clear()
        epgLoadedAt.clear()
        epgByChannel.value = emptyMap()
    }

    fun loadEpg(channel: Channel, force: Boolean = false) {
        val account = _account.value ?: return
        if (account.type != SourceType.XTREAM) return
        val now = System.currentTimeMillis()
        val fresh = epgByChannel.value.containsKey(channel.id) &&
            now - (epgLoadedAt[channel.id] ?: 0L) < epgCacheTtlMs
        if (!force && fresh) return
        epgJobs[channel.id]?.cancel()
        epgJobs[channel.id] = viewModelScope.launch {
            delay(180)
            val list = XtreamClient(account).shortEpg(channel.id, limit = 8)
            epgByChannel.value = epgByChannel.value + (channel.id to list)
            epgLoadedAt[channel.id] = System.currentTimeMillis()
            epgJobs.remove(channel.id)
        }
    }

    fun rememberLive(channel: Channel) {
        val account = _account.value ?: return
        store.setLastLive(account, channel.id, channel.group)
        lastLiveId.value = channel.id
        lastLiveGroup.value = channel.group
    }

    fun rememberSearch(term: String) {
        store.addSearchTerm(term)
        searchHistory.value = store.searchHistory()
    }

    fun clearSearchHistory() {
        store.clearSearchHistory()
        searchHistory.value = emptyList()
    }

    fun toggleFavorite(kind: String, id: String) {
        val key = "$kind:$id"
        val enabled = key !in favorites.value
        store.setFavorite(key, enabled)
        favorites.value = store.favorites()
    }

    fun isFavorite(kind: String, id: String): Boolean = "$kind:$id" in favorites.value

    fun markWatched(kind: String, id: String) {
        store.addRecent("$kind:$id")
        recents.value = store.recents()
    }

    fun resumePosition(kind: String, id: String): Long =
        if (kind == "live") 0L else playbackProgress.value["$kind:$id"]?.positionMs ?: 0L

    fun progressFor(kind: String, id: String): PlaybackProgress? =
        playbackProgress.value["$kind:$id"]

    fun savePlaybackProgress(kind: String, id: String, positionMs: Long, durationMs: Long) {
        if (kind == "live") return
        store.setPlaybackProgress("$kind:$id", positionMs, durationMs)
        playbackProgress.value = store.playbackProgress()
    }

    fun clearPlaybackProgress(kind: String, id: String) {
        store.clearPlaybackProgress("$kind:$id")
        playbackProgress.value = store.playbackProgress()
    }

    fun setPlaybackProfile(profile: StreamingProfile) {
        store.setPlaybackProfile(profile)
        playbackProfile.value = profile
    }

    fun setLanguage(value: AppLanguage) {
        store.setLanguage(value)
        language.value = value
    }

    fun setDisplayMode(value: DisplayMode) {
        store.setDisplayMode(value)
        displayMode.value = value
    }

    fun logout() {
        store.clearAccount()
        accounts.value = emptyList()
        addingAccount.value = false
        _account.value = null
        channels.value = emptyList()
        movies.value = emptyList()
        series.value = emptyList()
        providerInfo.value = null
        clearEpgCache()
        selectedSeriesDetails.value = null
        lastLiveId.value = null
        lastLiveGroup.value = null
        error.value = null
    }

    fun clearError() { error.value = null }

    private fun friendlyError(t: Throwable): String = when {
        t.message?.contains("HTTP 401") == true || t.message?.contains("HTTP 403") == true -> "Provider rejected the credentials"
        t.message?.contains("Unable to resolve host", ignoreCase = true) == true -> "Server could not be reached"
        t.message?.contains("timeout", ignoreCase = true) == true -> "Server response timed out"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
    }
}
