package com.vibe.player.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class AlbumSummary(
    val album: String,
    val artist: String,
    val trackCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val firstTrackUri: String,
    val year: Int,
)

data class ArtistSummary(
    val artist: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkUri: String?,
    val firstTrackUri: String,
)

data class FolderSummary(
    val folder: String,
    val folderName: String,
    val trackCount: Int,
    val artworkUri: String?,
    val firstTrackUri: String,
)

data class UrlFormat(val url: String, val audioFormat: String)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val trackCount: Int,
    val durationMs: Long,
    val artworkUri: String?,
    val firstTrackUri: String?,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 ORDER BY title COLLATE NOCASE")
    fun all(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 ORDER BY dateAdded DESC LIMIT :limit")
    fun recentlyAdded(limit: Int): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Track>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): Track?

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun observe(id: Long): Flow<Track?>

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun byUri(uri: String): Track?

    @Query("SELECT * FROM tracks")
    suspend fun allIncludingMissing(): List<Track>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 AND LOWER(TRIM(album)) = LOWER(TRIM(:album)) ORDER BY artist COLLATE NOCASE, trackNumber, title COLLATE NOCASE")
    fun forAlbum(album: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 AND artist = :artist ORDER BY album COLLATE NOCASE, trackNumber, title COLLATE NOCASE")
    fun forArtist(artist: String): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 AND folder = :folder ORDER BY title COLLATE NOCASE")
    fun forFolder(folder: String): Flow<List<Track>>

    @Query(
        "SELECT * FROM tracks WHERE missing = 0 AND hidden = 0 AND (title LIKE :q OR artist LIKE :q OR album LIKE :q OR displayName LIKE :q) " +
            "ORDER BY title COLLATE NOCASE LIMIT 200",
    )
    fun search(q: String): Flow<List<Track>>

    @Query(
        "SELECT MIN(album) AS album, CASE WHEN COUNT(DISTINCT artist) = 1 THEN MIN(artist) ELSE 'Various artists' END AS artist, " +
            "COUNT(*) AS trackCount, SUM(durationMs) AS durationMs, MAX(artworkUri) AS artworkUri, MIN(uri) AS firstTrackUri, MAX(year) AS year " +
            "FROM tracks WHERE missing = 0 AND hidden = 0 GROUP BY LOWER(TRIM(album)) ORDER BY album COLLATE NOCASE",
    )
    fun albums(): Flow<List<AlbumSummary>>

    @Query(
        "SELECT artist, COUNT(*) AS trackCount, COUNT(DISTINCT album) AS albumCount, MAX(artworkUri) AS artworkUri, MIN(uri) AS firstTrackUri " +
            "FROM tracks WHERE missing = 0 AND hidden = 0 GROUP BY artist ORDER BY artist COLLATE NOCASE",
    )
    fun artists(): Flow<List<ArtistSummary>>

    @Query(
        "SELECT folder, folderName, COUNT(*) AS trackCount, MAX(artworkUri) AS artworkUri, MIN(uri) AS firstTrackUri " +
            "FROM tracks WHERE missing = 0 AND hidden = 0 GROUP BY folder ORDER BY folderName COLLATE NOCASE",
    )
    fun folders(): Flow<List<FolderSummary>>

    @Query("SELECT DISTINCT format FROM tracks WHERE missing = 0 AND hidden = 0 ORDER BY format")
    fun formats(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM tracks WHERE missing = 0 AND hidden = 0")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(track: Track): Long

    @Update
    suspend fun update(track: Track)

    @Query("UPDATE tracks SET missing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("UPDATE tracks SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE tracks SET title = :title, artist = :artist, album = :album, userEdited = 1 WHERE id = :id")
    suspend fun updateTags(id: Long, title: String, artist: String, album: String)

    @Query("UPDATE tracks SET artist = :newName, userEdited = 1 WHERE artist = :oldName")
    suspend fun renameArtist(oldName: String, newName: String): Int

    @Query("UPDATE tracks SET album = :newAlbum, userEdited = 1 WHERE LOWER(TRIM(album)) = LOWER(TRIM(:album))")
    suspend fun renameAlbum(album: String, newAlbum: String): Int

    @Query("UPDATE tracks SET album = :album, userEdited = 1 WHERE id IN (:ids)")
    suspend fun setAlbum(ids: List<Long>, album: String): Int

    @Query("SELECT DISTINCT album FROM tracks WHERE missing = 0 AND hidden = 0 ORDER BY album COLLATE NOCASE")
    fun albumNames(): Flow<List<String>>

    @Query("SELECT DISTINCT artist FROM tracks WHERE missing = 0 AND hidden = 0 ORDER BY artist COLLATE NOCASE")
    fun artistNames(): Flow<List<String>>

    @Query("UPDATE tracks SET hidden = :hidden WHERE folder = :folder")
    suspend fun setFolderHidden(folder: String, hidden: Boolean)

    @Query("SELECT COUNT(*) FROM tracks WHERE hidden = 1 AND missing = 0")
    fun hiddenCount(): Flow<Int>

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :id")
    suspend fun deletePlaylistRefs(id: Long)

    @Query("DELETE FROM favorites WHERE trackId = :id")
    suspend fun deleteFavoriteRef(id: Long)

    @Query("DELETE FROM play_history WHERE trackId = :id")
    suspend fun deleteHistoryRef(id: Long)

    /** Removes a track and every reference to it. */
    @Transaction
    suspend fun purge(id: Long) { deletePlaylistRefs(id); deleteFavoriteRef(id); deleteHistoryRef(id); delete(id) }

    @Transaction
    suspend fun upsert(track: Track): Long {
        val existing = byUri(track.uri)
        return if (existing == null) {
            insertIgnore(track)
        } else {
            update(track.copy(id = existing.id))
            existing.id
        }
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT t.* FROM tracks t INNER JOIN favorites f ON f.trackId = t.id WHERE t.missing = 0 AND hidden = 0 ORDER BY f.addedAt DESC")
    fun favoriteTracks(): Flow<List<Track>>

    @Query("SELECT trackId FROM favorites")
    fun favoriteIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    fun isFavorite(trackId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavoriteNow(trackId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun remove(trackId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT t.* FROM tracks t INNER JOIN play_history h ON h.trackId = t.id WHERE t.missing = 0 AND hidden = 0 ORDER BY h.lastPlayed DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int): Flow<List<Track>>

    @Query("SELECT t.* FROM tracks t INNER JOIN play_history h ON h.trackId = t.id WHERE t.missing = 0 AND hidden = 0 ORDER BY h.playCount DESC, h.lastPlayed DESC LIMIT :limit")
    fun mostPlayed(limit: Int): Flow<List<Track>>

    @Query("SELECT * FROM play_history WHERE trackId = :trackId")
    suspend fun get(trackId: Long): PlayHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(history: PlayHistory)

    @Transaction
    suspend fun recordPlay(trackId: Long, now: Long) {
        val prev = get(trackId)
        put(PlayHistory(trackId, now, (prev?.playCount ?: 0) + 1))
    }
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.createdAt, p.updatedAt, COUNT(t.id) AS trackCount, COALESCE(SUM(t.durationMs), 0) AS durationMs, " +
            "MAX(t.artworkUri) AS artworkUri, MIN(t.uri) AS firstTrackUri FROM playlists p " +
            "LEFT JOIN playlist_tracks pt ON pt.playlistId = p.id LEFT JOIN tracks t ON t.id = pt.trackId AND t.missing = 0 AND hidden = 0 " +
            "GROUP BY p.id ORDER BY p.updatedAt DESC",
    )
    fun summaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): Playlist?

    @Query("SELECT t.* FROM tracks t INNER JOIN playlist_tracks pt ON pt.trackId = t.id WHERE pt.playlistId = :id AND t.missing = 0 AND hidden = 0 ORDER BY pt.position")
    fun tracks(id: Long): Flow<List<Track>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun entries(id: Long): List<PlaylistTrack>

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun contains(playlistId: Long, trackId: Long): Boolean

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Insert
    suspend fun insertEntries(entries: List<PlaylistTrack>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE id = :entryId")
    suspend fun setPosition(entryId: Long, position: Int)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Transaction
    suspend fun addTracks(playlistId: Long, trackIds: List<Long>, now: Long): Int {
        val existing = entries(playlistId)
        val present = existing.map { it.trackId }.toSet()
        var pos = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val fresh = trackIds.filter { it !in present }.distinct().map { PlaylistTrack(playlistId = playlistId, trackId = it, position = pos++) }
        if (fresh.isNotEmpty()) insertEntries(fresh)
        touch(playlistId, now)
        return fresh.size
    }

    @Transaction
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>, now: Long) {
        val entries = entries(playlistId).associateBy { it.trackId }
        orderedTrackIds.forEachIndexed { index, trackId -> entries[trackId]?.let { setPosition(it.id, index) } }
        touch(playlistId, now)
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: Long): DownloadItem?

    @Query("SELECT * FROM downloads WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextPending(): DownloadItem?

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING','RUNNING')")
    fun activeCount(): Flow<Int>

    @Query("SELECT url FROM downloads")
    suspend fun allUrls(): List<String>

    @Query("SELECT url, audioFormat FROM downloads")
    suspend fun allUrlFormats(): List<UrlFormat>

    @Insert
    suspend fun insert(item: DownloadItem): Long

    @Update
    suspend fun update(item: DownloadItem)

    @Query("UPDATE downloads SET progress = :progress, etaSec = :eta, statusLine = :line, updatedAt = :now WHERE id = :id")
    suspend fun progress(id: Long, progress: Float, eta: Long?, line: String?, now: Long)

    @Query("UPDATE downloads SET status = :status, error = :error, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, error: String?, now: Long)

    @Query("UPDATE downloads SET status = 'PENDING', progress = 0, etaSec = NULL, statusLine = NULL, updatedAt = :now WHERE status = 'RUNNING'")
    suspend fun requeueRunning(now: Long)

    @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('PENDING','RUNNING')")
    suspend fun pendingCount(): Int

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status IN ('DONE','FAILED','CANCELLED','UNSUPPORTED')")
    suspend fun clearFinished()
}

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversions ORDER BY createdAt DESC")
    fun all(): Flow<List<ConversionJob>>

    @Query("SELECT * FROM conversions WHERE id = :id")
    suspend fun get(id: Long): ConversionJob?

    @Insert
    suspend fun insert(job: ConversionJob): Long

    @Update
    suspend fun update(job: ConversionJob)

    @Query("UPDATE conversions SET progress = :progress WHERE id = :id")
    suspend fun progress(id: Long, progress: Float)

    @Query("SELECT * FROM conversions WHERE status = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): ConversionJob?

    @Query("UPDATE conversions SET status = 'FAILED', error = 'Interrupted (app was closed)', finishedAt = :now WHERE status = 'RUNNING'")
    suspend fun failStaleRunning(now: Long)

    @Query("DELETE FROM conversions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversions WHERE status IN ('DONE','FAILED','CANCELLED')")
    suspend fun clearFinished()
}
