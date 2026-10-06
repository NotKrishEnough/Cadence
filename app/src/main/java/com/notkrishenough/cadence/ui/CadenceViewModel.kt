package com.notkrishenough.cadence.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notkrishenough.cadence.data.MusicRepository
import com.notkrishenough.cadence.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CadenceViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = MusicRepository(app)
    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _tracks.value = repository.loadTracks() }
    }

    fun setQuery(value: String) { _query.value = value }

    fun filteredTracks(): List<Track> {
        val q = _query.value.trim()
        if (q.isEmpty()) return _tracks.value
        return _tracks.value.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
}
