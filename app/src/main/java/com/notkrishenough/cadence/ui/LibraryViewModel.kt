package com.notkrishenough.cadence.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notkrishenough.cadence.data.CadenceStore
import com.notkrishenough.cadence.data.MusicRepository
import com.notkrishenough.cadence.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MusicRepository(app)
    private val store = CadenceStore(app)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks = _tracks.asStateFlow()
    private val _favorites = MutableStateFlow<Set<Long>>(emptySet())
    val favorites = _favorites.asStateFlow()
    private val _playlists = MutableStateFlow<Map<String, List<Long>>>(emptyMap())
    val playlists = _playlists.asStateFlow()

    init {
        _favorites.value = store.favorites()
        _playlists.value = store.playlists()
        refresh()
    }

    fun refresh() = viewModelScope.launch(Dispatchers.IO) { _tracks.value = repo.loadTracks() }

    fun toggleFavorite(id: Long) { _favorites.value = store.toggleFavorite(id) }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        val next = _playlists.value.toMutableMap()
        next.putIfAbsent(name.trim(), emptyList())
        _playlists.value = next
        store.savePlaylists(next)
    }

    fun addToPlaylist(name: String, id: Long) {
        val next = _playlists.value.toMutableMap()
        next[name] = (next[name].orEmpty() + id).distinct()
        _playlists.value = next
        store.savePlaylists(next)
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val next = _playlists.value.toMutableMap()
        next[name] = next[name].orEmpty().filterNot { it == id }
        _playlists.value = next
        store.savePlaylists(next)
    }
}
