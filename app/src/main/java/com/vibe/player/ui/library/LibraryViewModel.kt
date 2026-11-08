package com.vibe.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vibe.player.AppContainer
import com.vibe.player.data.db.Track
import com.vibe.player.settings.LibrarySort
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SongsUiState(
    val tracks: List<Track> = emptyList(),
    val sort: LibrarySort = LibrarySort.TITLE,
    val desc: Boolean = false,
    val formatFilter: String? = null,
    val formats: List<String> = emptyList(),
    val total: Int = 0,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {
    /** Survive tab switches now that the nav back stack no longer saves destination state. */
    var selectedTab = 0
    var songsFirstVisible = 0
    var songsFirstVisibleOffset = 0
    var shownCount = 100

    private val library = container.library
    private val settings = container.settings

    val songs: StateFlow<SongsUiState> = combine(library.allTracks(), settings.settings, library.formats()) { tracks, s, formats ->
        val filtered = s.formatFilter?.let { f -> tracks.filter { it.format == f } } ?: tracks
        val sorted = when (s.librarySort) {
            LibrarySort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
            LibrarySort.ALBUM -> filtered.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
            LibrarySort.DATE_ADDED -> filtered.sortedByDescending { it.dateAdded }
            LibrarySort.DURATION -> filtered.sortedBy { it.durationMs }
        }.let { if (s.librarySortDesc) it.reversed() else it }
        SongsUiState(sorted, s.librarySort, s.librarySortDesc, s.formatFilter, formats, tracks.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongsUiState())

    fun setSort(sort: LibrarySort) = viewModelScope.launch {
        val cur = songs.value
        settings.setLibrarySort(sort, if (cur.sort == sort) !cur.desc else false)
    }

    fun setFormatFilter(format: String?) = viewModelScope.launch { settings.setFormatFilter(format) }
}
