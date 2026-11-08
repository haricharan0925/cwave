package com.vibe.player.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.vibe.player.data.db.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerState(
    val connected: Boolean = false,
    val current: NowPlayingItem? = null,
    val isPlaying: Boolean = false,
    /** User intent (play/pause); unlike isPlaying it doesn't flicker while buffering after a seek. */
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<NowPlayingItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
) {
    val hasQueue get() = queue.isNotEmpty()
}

data class PlayIndicator(val trackId: Long?, val playing: Boolean) {
    fun isCurrent(id: Long) = trackId == id
    fun isPlaying(id: Long) = trackId == id && playing
}

/** App-side facade over the MediaController; the single source of truth for playback UI state. */
class PlayerController(private val context: Context, private val queueRestorer: QueueRestorer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /**
     * What list rows need to highlight the current track. Deliberately excludes position so the
     * half-second ticker doesn't recompose every list on screen.
     */
    val indicator: StateFlow<PlayIndicator> = _state
        .map { PlayIndicator(it.current?.trackId, it.isPlaying) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlayIndicator(null, false))

    fun connect() {
        if (controller != null) return
        scope.launch {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val c = runCatching { MediaController.Builder(context, token).buildAsync().await() }.getOrNull() ?: return@launch
            controller = c
            c.addListener(listener)
            if (c.mediaItemCount == 0) restoreQueue(c)
            publish()
        }
    }

    private suspend fun restoreQueue(c: MediaController) {
        val (saved, items) = runCatching { queueRestorer.load() }.getOrNull() ?: return
        if (c.mediaItemCount > 0) return
        c.setMediaItems(items, saved.index.coerceIn(0, items.lastIndex), saved.positionMs)
        c.shuffleModeEnabled = saved.shuffle
        c.repeatMode = saved.repeatMode
        c.prepare()
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()
        override fun onPlayerError(error: PlaybackException) {
            val name = controller?.currentMediaItem?.mediaMetadata?.title ?: "This file"
            val reason = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "file is missing or not accessible"
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "format isn't supported by this device"
                else -> error.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
            }
            _errors.tryEmit("Can't play \"$name\": $reason. Skipping.")
            controller?.let { c -> if (c.hasNextMediaItem()) { c.seekToNextMediaItem(); c.prepare(); c.play() } }
        }
    }

    private fun publish() {
        val c = controller ?: return
        val queue = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).toNowPlaying() }
        _state.update {
            it.copy(
                connected = true,
                current = c.currentMediaItem?.toNowPlaying(),
                isPlaying = c.isPlaying,
                playWhenReady = c.playWhenReady,
                isBuffering = c.playbackState == Player.STATE_BUFFERING,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.takeIf { d -> d > 0 } ?: (c.currentMediaItem?.toNowPlaying()?.durationMs ?: 0),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                queue = queue,
                currentIndex = c.currentMediaItemIndex,
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem() || c.currentPosition > 3000,
            )
        }
        if (c.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c -> _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) } }
                delay(500)
            }
        }
    }

    // ---- commands -------------------------------------------------------------------------

    fun playTracks(tracks: List<Track>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val items = tracks.map { it.toMediaItem() }
        c.shuffleModeEnabled = shuffle
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playTracks(tracks.shuffled(), 0, shuffle = false)
    }

    fun playNext(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playTracks(tracks)
        c.addMediaItems(c.currentMediaItemIndex + 1, tracks.map { it.toMediaItem() })
    }

    fun addToQueue(tracks: List<Track>) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playTracks(tracks)
        c.addMediaItems(tracks.map { it.toMediaItem() })
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else { if (c.playbackState == Player.STATE_IDLE) c.prepare(); c.play() }
    }

    fun next() { controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() } }
    fun previous() { controller?.let { if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem() } }
    fun seekTo(ms: Long) { controller?.seekTo(ms); _state.update { it.copy(positionMs = ms) } }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }
    fun skipToQueueItem(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.play() } } }
    fun removeQueueItem(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
    fun moveQueueItem(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun clearQueue() { controller?.let { it.stop(); it.clearMediaItems() } }
    fun removeTrack(trackId: Long) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) if (c.getMediaItemAt(i).trackId == trackId) c.removeMediaItem(i)
    }

    fun release() {
        ticker?.cancel()
        controller?.release()
        controller = null
    }
}
