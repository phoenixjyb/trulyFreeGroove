package com.trulyfreemusic.opengroove.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trulyfreemusic.opengroove.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeMusicState(
    val configured: Boolean = false,
    val server: String = "",
    val username: String = "",
    val albums: List<HomeAlbum> = emptyList(),
    val songs: List<Track> = emptyList(),
    val selectedAlbum: HomeAlbum? = null,
    val query: String = "",
    val searching: Boolean = false,
    val loading: Boolean = false,
    val canLoadMore: Boolean = false,
    val error: String? = null,
)

class HomeMusicViewModel(application: Application) : AndroidViewModel(application) {
    private val store = HomeMusicStore(application)
    private var client: HomeMusicClient? = null
    private var job: Job? = null
    private var activeQuery = ""
    private var albumOffset = 0
    private var songOffset = 0
    private var albumMore = false
    private var songMore = false
    private val mutableState = MutableStateFlow(HomeMusicState())
    val state = mutableState.asStateFlow()

    init {
        store.load()?.let { config ->
            client = HomeMusicClient(config)
            mutableState.value = HomeMusicState(configured = true, server = config.server, username = config.username)
            browse()
        }
    }

    fun connect(server: String, username: String, password: String) {
        val config = runCatching { HomeMusicConfig(server, username.trim(), password) }.getOrNull()
        if (config == null) {
            mutableState.value = mutableState.value.copy(error = "Enter a private LAN IPv4 server URL, username and password.")
            return
        }
        runRequest {
            val candidate = HomeMusicClient(config)
            val albums = withContext(Dispatchers.IO) { candidate.ping(); candidate.albums() }
            // Commit on the main thread so Disconnect cannot interleave a late credential save.
            store.save(config)
            client = candidate
            albumOffset = albums.size
            mutableState.value = HomeMusicState(configured = true, server = config.server, username = config.username,
                albums = albums, canLoadMore = albums.size == HomeMusicClient.PAGE_SIZE)
        }
    }

    fun disconnect() {
        job?.cancel()
        client = null
        store.clear()
        mutableState.value = HomeMusicState()
    }
    fun setQuery(query: String) { mutableState.value = mutableState.value.copy(query = query) }
    fun browse() {
        val api = client ?: return
        runRequest {
            val albums = withContext(Dispatchers.IO) { api.albums() }
            albumOffset = albums.size
            mutableState.value = mutableState.value.copy(albums = albums, songs = emptyList(), selectedAlbum = null,
                searching = false, query = "", loading = false, canLoadMore = albums.size == HomeMusicClient.PAGE_SIZE)
        }
    }
    fun openAlbum(album: HomeAlbum) {
        val api = client ?: return
        runRequest {
            val songs = withContext(Dispatchers.IO) { api.album(album.id) }
            mutableState.value = mutableState.value.copy(selectedAlbum = album, songs = songs, loading = false, canLoadMore = false)
        }
    }
    fun search() {
        val api = client ?: return
        val query = mutableState.value.query.trim()
        if (query.isEmpty()) { browse(); return }
        runRequest {
            val results = withContext(Dispatchers.IO) { api.search(query) }
            activeQuery = query
            albumOffset = results.albums.size
            songOffset = results.songs.size
            albumMore = albumOffset == HomeMusicClient.PAGE_SIZE
            songMore = songOffset == HomeMusicClient.PAGE_SIZE
            mutableState.value = mutableState.value.copy(albums = results.albums, songs = results.songs, selectedAlbum = null,
                searching = true, query = query, loading = false, canLoadMore = albumMore || songMore)
        }
    }
    fun loadMore() {
        val api = client ?: return
        val previous = mutableState.value
        if (previous.loading || !previous.canLoadMore) return
        runRequest {
            if (previous.searching) {
                val result = withContext(Dispatchers.IO) { api.search(activeQuery, albumOffset, songOffset) }
                val albums = if (albumMore) result.albums else emptyList()
                val songs = if (songMore) result.songs else emptyList()
                albumOffset += albums.size; songOffset += songs.size
                albumMore = albumMore && albums.size == HomeMusicClient.PAGE_SIZE
                songMore = songMore && songs.size == HomeMusicClient.PAGE_SIZE
                mutableState.value = previous.copy(albums = (previous.albums + albums).distinctBy { it.id },
                    songs = (previous.songs + songs).distinctBy { it.id }, loading = false, error = null, canLoadMore = albumMore || songMore)
            } else {
                val albums = withContext(Dispatchers.IO) { api.albums(albumOffset) }
                albumOffset += albums.size
                mutableState.value = previous.copy(albums = (previous.albums + albums).distinctBy { it.id }, loading = false,
                    error = null, canLoadMore = albums.size == HomeMusicClient.PAGE_SIZE)
            }
        }
    }
    private fun runRequest(action: suspend () -> Unit) {
        job?.cancel()
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = (error as? HomeMusicException)?.uiMessage
                    ?: "Home Music is unavailable. Check home Wi-Fi and the server.")
            }
        }
    }
}
