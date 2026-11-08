package com.vibe.player.player

import androidx.media3.session.MediaSession
import com.vibe.player.data.LibraryRepository
import com.vibe.player.settings.SavedPlayback
import com.vibe.player.settings.SettingsRepository

/** Rebuilds the last queue from persisted ids. Shared by the app-side controller and the session's resumption callback. */
class QueueRestorer(private val library: LibraryRepository, private val settings: SettingsRepository) {

    suspend fun load(): Pair<SavedPlayback, List<androidx.media3.common.MediaItem>>? {
        val saved = settings.loadPlayback() ?: return null
        val tracks = library.tracksByIds(saved.trackIds).filter { !it.missing }
        if (tracks.isEmpty()) return null
        return saved to tracks.map { it.toMediaItem() }
    }

    suspend fun restore(): MediaSession.MediaItemsWithStartPosition? {
        val (saved, items) = load() ?: return null
        val index = saved.index.coerceIn(0, items.lastIndex)
        return MediaSession.MediaItemsWithStartPosition(items, index, saved.positionMs)
    }
}
