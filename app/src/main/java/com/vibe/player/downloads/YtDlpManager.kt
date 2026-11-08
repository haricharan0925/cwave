package com.vibe.player.downloads

import android.content.Context
import com.vibe.player.settings.SettingsRepository
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class YtDlpStatus(val initialized: Boolean = false, val version: String? = null, val updating: Boolean = false, val lastError: String? = null)

/** Thin, thread-safe wrapper around the bundled yt-dlp runtime. */
class YtDlpManager(private val context: Context, private val settings: SettingsRepository) {

    private val initLock = Mutex()
    private val _status = MutableStateFlow(YtDlpStatus())
    val status: StateFlow<YtDlpStatus> = _status.asStateFlow()

    suspend fun ensureInitialized() = initLock.withLock {
        if (_status.value.initialized) return@withLock
        withContext(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
                val v = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
                _status.value = YtDlpStatus(initialized = true, version = v ?: settings.current().ytdlpVersion)
                if (v != null) settings.setYtdlpVersion(v)
            } catch (e: Throwable) {
                _status.value = YtDlpStatus(initialized = false, lastError = e.message ?: e.toString())
                throw e
            }
        }
    }

    /** Updates yt-dlp from GitHub. Returns a human-readable result. */
    suspend fun update(nightly: Boolean = false): String {
        ensureInitialized()
        _status.value = _status.value.copy(updating = true, lastError = null)
        return withContext(Dispatchers.IO) {
            try {
                val channel = if (nightly) YoutubeDL.UpdateChannel.NIGHTLY else YoutubeDL.UpdateChannel.STABLE
                val result = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
                val v = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
                if (v != null) settings.setYtdlpVersion(v)
                settings.setYtdlpUpdatedAt(System.currentTimeMillis())
                _status.value = _status.value.copy(updating = false, version = v)
                when (result) {
                    YoutubeDL.UpdateStatus.DONE -> "Updated yt-dlp to ${v ?: "latest"}"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is already up to date (${v ?: "?"})"
                    else -> "Update finished: $result"
                }
            } catch (e: Throwable) {
                _status.value = _status.value.copy(updating = false, lastError = e.message)
                "Update failed: ${e.message ?: e.toString()}"
            }
        }
    }

    suspend fun execute(request: YoutubeDLRequest, processId: String?, onProgress: ((Float, Long, String) -> Unit)?): YoutubeDLResponse {
        ensureInitialized()
        return withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().execute(request, processId) { progress, eta, line -> onProgress?.invoke(progress, eta, line) }
        }
    }

    /** Best-effort daily self-update so YouTube-side changes don't require a rebuild. */
    suspend fun updateIfStale(maxAgeMs: Long = 24 * 60 * 60 * 1000L) {
        val last = settings.current().ytdlpUpdatedAt
        if (System.currentTimeMillis() - last > maxAgeMs) runCatching { update() }
    }

    fun cancel(processId: String): Boolean = runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)
}
