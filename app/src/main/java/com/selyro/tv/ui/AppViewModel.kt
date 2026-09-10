package com.selyro.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.selyro.tv.data.AccountStore
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

    private var epgJob: Job? = null

    init {
        if (_account.value != null) loadLive()
    }

    fun login(account: PlaylistAccount) {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            runCatching {
                when (account.type) {
                    SourceType.XTREAM -> {
                        require(account.username.isNotBlank() && account.password.isNotBlank()) { "Username and password are required" }
                        val client = XtreamClient(account)
                        providerInfo.value = client.authenticate() ?: error("Server login failed")
                        channels.value = client.live()
                    }
                    SourceType.M3U -> {
                        channels.value = M3uClient.fetch(account.server).channels
                        providerInfo.value = null
                    }
                }
                require(channels.value.isNotEmpty()) { "No live channels were returned" }
                store.save(account)
                _account.value = account
                movies.value = emptyList()
                series.value = emptyList()
                epgByChannel.value = emptyMap()
            }.onFailure { error.value = friendlyError(it) }
            loading.value = false
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

    fun loadEpg(channel: Channel) {
        val account = _account.value ?: return
        if (account.type != SourceType.XTREAM || epgByChannel.value.containsKey(channel.id)) return
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            delay(300)
            val list = XtreamClient(account).shortEpg(channel.id)
            epgByChannel.value = epgByChannel.value + (channel.id to list)
        }
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

    fun setPlaybackProfile(profile: StreamingProfile) {
        store.setPlaybackProfile(profile)
        playbackProfile.value = profile
    }

    fun logout() {
        store.clearAccount()
        _account.value = null
        channels.value = emptyList()
        movies.value = emptyList()
        series.value = emptyList()
        providerInfo.value = null
        epgByChannel.value = emptyMap()
        selectedSeriesDetails.value = null
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
