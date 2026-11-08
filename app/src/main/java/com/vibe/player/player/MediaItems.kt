package com.vibe.player.player

import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.vibe.player.data.db.Track

private const val EXTRA_TRACK_ID = "trackId"
private const val EXTRA_FORMAT = "format"
private const val EXTRA_DURATION = "durationMs"
private const val EXTRA_ALBUM_ID = "albumId"

/** What the UI needs to know about the item currently loaded in the player. */
data class NowPlayingItem(
    val trackId: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
    val format: String,
    val durationMs: Long,
) {
    val albumKey: String get() = com.vibe.player.data.db.albumKeyOf(album)
}

fun Track.toMediaItem(): MediaItem {
    val uri = this.uri.toUri()
    return MediaItem.Builder()
        .setMediaId(this.uri)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(bundleOf(EXTRA_TRACK_ID to id, EXTRA_FORMAT to format, EXTRA_DURATION to durationMs, EXTRA_ALBUM_ID to albumId))
                .build(),
        )
        .build()
}

fun MediaItem.toNowPlaying(): NowPlayingItem? {
    val extras = mediaMetadata.extras ?: return null
    val trackId = extras.getLong(EXTRA_TRACK_ID, -1L)
    if (trackId < 0) return null
    return NowPlayingItem(
        trackId = trackId,
        uri = mediaId,
        title = mediaMetadata.title?.toString() ?: "Unknown title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
        album = mediaMetadata.albumTitle?.toString() ?: "",
        artworkUri = mediaMetadata.artworkUri?.toString(),
        format = extras.getString(EXTRA_FORMAT) ?: "",
        durationMs = extras.getLong(EXTRA_DURATION, 0L),
    )
}

val MediaItem.trackId: Long get() = mediaMetadata.extras?.getLong(EXTRA_TRACK_ID, -1L) ?: -1L

/** Rebuilds a playable item from what survives the controller → session IPC (mediaId + request metadata). */
fun MediaItem.withPlayableUri(): MediaItem {
    if (localConfiguration != null) return this
    val uri: Uri = requestMetadata.mediaUri ?: mediaId.toUri()
    return buildUpon().setUri(uri).build()
}
