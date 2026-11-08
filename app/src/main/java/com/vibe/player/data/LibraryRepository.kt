package com.vibe.player.data

import android.net.Uri
import com.vibe.player.data.db.AlbumSummary
import com.vibe.player.data.db.ArtistSummary
import com.vibe.player.data.db.Favorite
import com.vibe.player.data.db.FolderSummary
import com.vibe.player.data.db.Playlist
import com.vibe.player.data.db.PlaylistSummary
import com.vibe.player.data.db.Track
import com.vibe.player.data.db.VibeDatabase
import com.vibe.player.library.FileImporter
import com.vibe.player.library.ImportResult
import com.vibe.player.library.MediaStoreScanner
import com.vibe.player.library.ScanOptions
import com.vibe.player.library.ScanResult
import com.vibe.player.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LibraryRepository(
    private val db: VibeDatabase,
    private val scanner: MediaStoreScanner,
    private val importer: FileImporter,
    private val settings: SettingsRepository,
) {
    private val tracks = db.tracks()
    private val favorites = db.favorites()
    private val history = db.history()
    private val playlists = db.playlists()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    fun hasMediaPermission() = scanner.hasPermission()

    suspend fun rescan(): ScanResult {
        _scanning.value = true
        try {
            val s = settings.current()
            return scanner.scan(ScanOptions(s.hideRecordings, s.hiddenFolders)).also { settings.setLastScan(System.currentTimeMillis()) }
        } finally {
            _scanning.value = false
        }
    }

    fun hiddenCount(): Flow<Int> = tracks.hiddenCount()

    suspend fun hideFolder(folder: String) {
        settings.setHiddenFolders(settings.current().hiddenFolders + folder)
        tracks.setFolderHidden(folder, true)
    }

    suspend fun unhideFolder(folder: String) {
        settings.setHiddenFolders(settings.current().hiddenFolders - folder)
        tracks.setFolderHidden(folder, false)
        rescan() // re-applies the recordings filter to that folder
    }

    suspend fun setHideRecordings(hide: Boolean) {
        settings.setHideRecordings(hide)
        rescan()
    }

    suspend fun importFiles(uris: List<Uri>): ImportResult = importer.importFiles(uris)
    suspend fun importTree(uri: Uri): ImportResult = importer.importTree(uri)

    fun allTracks(): Flow<List<Track>> = tracks.all()
    suspend fun allTracksNow(): List<Track> = tracks.all().first()
    fun recentlyAdded(limit: Int = 20): Flow<List<Track>> = tracks.recentlyAdded(limit)
    fun recentlyPlayed(limit: Int = 20): Flow<List<Track>> = history.recentlyPlayed(limit)
    fun mostPlayed(limit: Int = 20): Flow<List<Track>> = history.mostPlayed(limit)
    fun trackCount(): Flow<Int> = tracks.count()
    fun formats(): Flow<List<String>> = tracks.formats()
    fun albums(): Flow<List<AlbumSummary>> = tracks.albums()
    fun artists(): Flow<List<ArtistSummary>> = tracks.artists()
    fun folders(): Flow<List<FolderSummary>> = tracks.folders()
    fun albumTracks(album: String): Flow<List<Track>> = tracks.forAlbum(album)
    fun artistTracks(artist: String): Flow<List<Track>> = tracks.forArtist(artist)
    fun folderTracks(folder: String): Flow<List<Track>> = tracks.forFolder(folder)
    fun search(query: String): Flow<List<Track>> = tracks.search("%${query.trim()}%")
    fun observeTrack(id: Long): Flow<Track?> = tracks.observe(id)
    suspend fun track(id: Long): Track? = tracks.byId(id)
    suspend fun trackByUri(uri: String): Track? = tracks.byUri(uri)
    suspend fun tracksByIds(ids: List<Long>): List<Track> {
        val map = tracks.byIds(ids).associateBy { it.id }
        return ids.mapNotNull { map[it] }
    }
    suspend fun removeTrack(id: Long) = tracks.delete(id)

    fun albumNames(): Flow<List<String>> = tracks.albumNames()
    fun artistNames(): Flow<List<String>> = tracks.artistNames()
    suspend fun updateTags(id: Long, title: String, artist: String, album: String) =
        tracks.updateTags(id, title.trim().ifBlank { "Unknown title" }, artist.trim().ifBlank { "Unknown artist" }, album.trim().ifBlank { "Unknown album" })
    suspend fun renameArtist(old: String, new: String): Int = if (new.trim().isBlank() || new.trim() == old) 0 else tracks.renameArtist(old, new.trim())
    suspend fun renameAlbum(album: String, new: String): Int = if (new.trim().isBlank() || new.trim() == album) 0 else tracks.renameAlbum(album, new.trim())
    /** Moves songs into an album (existing or brand new); "" / blank puts them back under "Unknown album". */
    suspend fun setAlbumForTracks(ids: List<Long>, album: String): Int = if (ids.isEmpty()) 0 else tracks.setAlbum(ids, album.trim().ifBlank { "Unknown album" })

    fun favoriteTracks(): Flow<List<Track>> = favorites.favoriteTracks()
    fun favoriteIds(): Flow<List<Long>> = favorites.favoriteIds()
    fun isFavorite(id: Long): Flow<Boolean> = favorites.isFavorite(id)
    suspend fun toggleFavorite(id: Long): Boolean {
        return if (favorites.isFavoriteNow(id)) { favorites.remove(id); false } else { favorites.add(Favorite(id, System.currentTimeMillis())); true }
    }

    suspend fun recordPlay(trackId: Long) = history.recordPlay(trackId, System.currentTimeMillis())

    fun playlistSummaries(): Flow<List<PlaylistSummary>> = playlists.summaries()
    fun playlist(id: Long): Flow<Playlist?> = playlists.observe(id)
    fun playlistTracks(id: Long): Flow<List<Track>> = playlists.tracks(id)
    suspend fun createPlaylist(name: String): Long {
        val now = System.currentTimeMillis()
        return playlists.insert(Playlist(name = name.trim().ifBlank { "New playlist" }, createdAt = now, updatedAt = now))
    }
    suspend fun renamePlaylist(id: Long, name: String) {
        playlists.get(id)?.let { playlists.update(it.copy(name = name.trim().ifBlank { it.name }, updatedAt = System.currentTimeMillis())) }
    }
    suspend fun deletePlaylist(id: Long) {
        playlists.get(id)?.let { playlists.clearEntries(id); playlists.delete(it) }
    }
    suspend fun addToPlaylist(playlistId: Long, trackIds: List<Long>): Int = playlists.addTracks(playlistId, trackIds, System.currentTimeMillis())
    suspend fun removeFromPlaylist(playlistId: Long, trackId: Long) {
        playlists.removeTrack(playlistId, trackId); playlists.touch(playlistId, System.currentTimeMillis())
    }
    suspend fun reorderPlaylist(playlistId: Long, orderedTrackIds: List<Long>) = playlists.reorder(playlistId, orderedTrackIds, System.currentTimeMillis())
}
