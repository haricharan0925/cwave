package com.vibe.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Albums are grouped by name alone (case/whitespace-insensitive); this is the key used for custom art and selection. */
fun albumKeyOf(album: String): String = album.trim().lowercase()

object TrackSource {
    const val MEDIASTORE = "MEDIASTORE"
    const val IMPORTED = "IMPORTED"
    const val DOWNLOADED = "DOWNLOADED"
}

@Entity(
    tableName = "tracks",
    indices = [Index("uri", unique = true), Index("album"), Index("artist"), Index("folder"), Index("dateAdded")],
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val format: String,
    val folder: String,
    val folderName: String,
    val displayName: String,
    val dateAdded: Long,
    val source: String,
    val artworkUri: String?,
    val year: Int = 0,
    val trackNumber: Int = 0,
    val missing: Boolean = false,
    /** Hidden by the recordings filter or a user-hidden folder; excluded from every list. */
    val hidden: Boolean = false,
    /** Title/artist/album were edited in-app; a rescan must not overwrite them from MediaStore. */
    val userEdited: Boolean = false,
) {
    val albumKey: String get() = albumKeyOf(album)
}

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val trackId: Long,
    val addedAt: Long,
)

@Entity(tableName = "play_history")
data class PlayHistory(
    @PrimaryKey val trackId: Long,
    val lastPlayed: Long,
    val playCount: Int,
)

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index("playlistId"), Index("trackId")],
)
data class PlaylistTrack(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
)

object DownloadStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val UNSUPPORTED = "UNSUPPORTED"
}

@Entity(tableName = "downloads", indices = [Index("status"), Index("createdAt")])
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val adapterId: String,
    val title: String?,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Long?,
    val status: String,
    val progress: Float = 0f,
    val etaSec: Long? = null,
    val statusLine: String? = null,
    val error: String? = null,
    val outputUri: String? = null,
    val outputName: String? = null,
    val audioFormat: String = "mp3",
    val bitrateKbps: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

object ConversionStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
}

@Entity(tableName = "conversions", indices = [Index("createdAt")])
data class ConversionJob(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUri: String,
    val sourceName: String,
    val sourceFormat: String,
    val targetFormat: String,
    val bitrateKbps: Int,
    val status: String,
    val progress: Float = 0f,
    val error: String? = null,
    val outputUri: String? = null,
    val outputName: String? = null,
    val outputBytes: Long = 0,
    val deleteSource: Boolean = false,
    val createdAt: Long,
    val finishedAt: Long? = null,
)
