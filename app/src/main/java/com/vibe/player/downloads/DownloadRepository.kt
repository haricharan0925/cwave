package com.vibe.player.downloads

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.vibe.player.data.LibraryRepository
import com.vibe.player.data.db.DownloadDao
import com.vibe.player.data.db.DownloadItem
import com.vibe.player.data.db.DownloadStatus
import com.vibe.player.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.io.File

data class EnqueueResult(val added: Int, val duplicates: Int, val invalid: Int, val unsupported: Int)

class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao,
    val ytdlp: YtDlpManager,
    private val outputStore: OutputStore,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    val youTubeAuth: YouTubeAuth,
) {
    private val ytAdapter = YtDlpAdapter(ytdlp, youTubeAuth)
    private val directAdapter = DirectUrlAdapter()
    val registry = SourceRegistry(listOf(ytAdapter, directAdapter))

    val all: Flow<List<DownloadItem>> = dao.all()
    val activeCount: Flow<Int> = dao.activeCount()

    /** Validates, de-duplicates and queues raw pasted text. Unsupported URLs are kept visible with an explanation. */
    /** [videoMaxHeight] (0 = best) is stored in the item's bitrate column when [format] is video. */
    suspend fun enqueueText(text: String, format: String = "mp3", videoMaxHeight: Int = 0): EnqueueResult {
        val rawTokens = text.split('\n', '\r', ' ', '\t', ',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        val urls = rawTokens.mapNotNull { UrlValidator.normalize(it) }
        val invalid = rawTokens.size - urls.size
        // Same link may be fetched once as audio and once as video; de-duplicate per (url, format).
        val known = dao.allUrlFormats().map { "${it.url.trimEnd('/')}|${it.audioFormat}" }.toHashSet()
        var added = 0; var duplicates = 0; var unsupported = 0
        val now = System.currentTimeMillis()
        val bitrate = if (format == YtDlpAdapter.VIDEO_FORMAT) videoMaxHeight else settings.current().mp3Bitrate
        for (url in urls.distinct()) {
            val key = "${url.trimEnd('/')}|$format"
            if (key in known) { duplicates++; continue }
            known += key
            val adapter = registry.forUrl(url) ?: if (directAdapter.probe(url)) directAdapter else null
            if (adapter == null) {
                unsupported++
                dao.insert(
                    DownloadItem(
                        url = url, adapterId = "none", title = UrlValidator.host(url), uploader = null, thumbnailUrl = null, durationSec = null,
                        status = DownloadStatus.UNSUPPORTED, createdAt = now, updatedAt = now,
                        error = "Not a supported source. Supported: ${registry.supportedSummary()}.",
                    ),
                )
                continue
            }
            dao.insert(
                DownloadItem(
                    url = url, adapterId = adapter.id, title = null, uploader = null, thumbnailUrl = null, durationSec = null,
                    status = DownloadStatus.PENDING, createdAt = now, updatedAt = now, audioFormat = format, bitrateKbps = bitrate,
                ),
            )
            added++
        }
        if (added > 0) kick()
        return EnqueueResult(added, duplicates, invalid, unsupported)
    }

    fun kick() {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(id: Long) {
        val item = dao.get(id) ?: return
        when (item.status) {
            DownloadStatus.RUNNING -> {
                registry.byId(item.adapterId)?.cancel(processId(id))
                dao.setStatus(id, DownloadStatus.CANCELLED, null, System.currentTimeMillis())
            }
            DownloadStatus.PENDING -> dao.setStatus(id, DownloadStatus.CANCELLED, null, System.currentTimeMillis())
        }
    }

    suspend fun retry(id: Long) {
        val item = dao.get(id) ?: return
        dao.update(item.copy(status = DownloadStatus.PENDING, error = null, progress = 0f, etaSec = null, statusLine = null, updatedAt = System.currentTimeMillis()))
        kick()
    }

    suspend fun remove(id: Long) { cancel(id); dao.delete(id) }
    suspend fun clearFinished() = dao.clearFinished()

    // ---- used by the worker -------------------------------------------------------------

    internal suspend fun nextPending(): DownloadItem? = dao.nextPending()
    internal suspend fun markStaleRunningAsPending() {
        // The worker died mid-download (process killed). Requeue rather than leave ghosts "running".
        dao.requeueRunning(System.currentTimeMillis())
    }

    /** Called on app start: if anything is still queued from a previous session, make sure the worker runs. */
    suspend fun resumePending() {
        // Only the worker may touch RUNNING rows (it re-queues stale ones on start); here we just make sure it runs.
        if (dao.pendingCount() > 0) kick()
    }

    internal suspend fun process(item: DownloadItem, onNotify: (String, Int) -> Unit) {
        val now = { System.currentTimeMillis() }
        val adapter = registry.byId(item.adapterId) ?: run {
            dao.setStatus(item.id, DownloadStatus.UNSUPPORTED, "Unknown source adapter '${item.adapterId}'", now()); return
        }
        // Every later write must derive from `running`, not `item`, or the status silently reverts to PENDING.
        val running = item.copy(status = DownloadStatus.RUNNING, error = null, progress = 0f, statusLine = "Resolving…", updatedAt = now())
        dao.update(running)
        onNotify(item.title ?: item.url, 0)

        // 1. Resolve metadata; expand playlists into their own queue entries.
        val resolved = try {
            adapter.resolve(item.url, processId(item.id))
        } catch (e: Throwable) {
            if (dao.get(item.id)?.status == DownloadStatus.CANCELLED) return
            dao.setStatus(item.id, DownloadStatus.FAILED, e.message ?: "Couldn't read this link", now()); return
        }
        if (dao.get(item.id)?.status == DownloadStatus.CANCELLED) return

        if (resolved.size > 1 || (resolved.size == 1 && resolved[0].url.trimEnd('/') != item.url.trimEnd('/') && adapter is YtDlpAdapter && adapter.isPlaylistUrl(item.url))) {
            val known = dao.allUrls().toHashSet()
            var added = 0
            for (e in resolved) {
                if (e.url in known) continue
                known += e.url
                dao.insert(
                    DownloadItem(
                        url = e.url, adapterId = adapter.id, title = e.title, uploader = e.uploader, thumbnailUrl = e.thumbnailUrl, durationSec = e.durationSec,
                        status = DownloadStatus.PENDING, createdAt = now(), updatedAt = now(), audioFormat = item.audioFormat, bitrateKbps = item.bitrateKbps,
                    ),
                )
                added++
            }
            dao.update(running.copy(status = DownloadStatus.DONE, title = item.title ?: "Playlist", statusLine = "Playlist expanded: $added track(s) queued", progress = 100f, updatedAt = now()))
            return
        }

        val meta = resolved.firstOrNull()
        var current = running.copy(
            title = meta?.title ?: item.title, uploader = meta?.uploader ?: item.uploader, thumbnailUrl = meta?.thumbnailUrl ?: item.thumbnailUrl,
            durationSec = meta?.durationSec ?: item.durationSec, statusLine = "Starting download…", updatedAt = now(),
        )
        dao.update(current)

        // 2. Download into a private work dir, then hand the file to the output store.
        val workDir = File(context.cacheDir, "dl/${item.id}")
        try {
            val output = adapter.download(item.url, item.audioFormat, item.bitrateKbps, workDir, processId(item.id)) { p, eta, line ->
                val pct = p.coerceIn(-1f, 100f)
                onNotify(current.title ?: item.url, pct.toInt().coerceAtLeast(0))
                kotlinx.coroutines.runBlocking { dao.progress(item.id, pct, eta, line?.take(160), now()) }
            }
            if (dao.get(item.id)?.status == DownloadStatus.CANCELLED) return
            dao.progress(item.id, 100f, null, "Saving to library…", now())
            val saved = outputStore.save(output.file, output.displayName, output.mimeType, ConflictPolicy.KEEP_BOTH)
            current = current.copy(status = DownloadStatus.DONE, progress = 100f, etaSec = null, statusLine = "Saved to ${saved.location}", outputUri = saved.uri.toString(), outputName = saved.displayName, updatedAt = now())
            dao.update(current)
        } catch (e: Throwable) {
            val status = if (dao.get(item.id)?.status == DownloadStatus.CANCELLED || e is InterruptedException || e.message?.contains("cancel", true) == true) DownloadStatus.CANCELLED else DownloadStatus.FAILED
            dao.update(current.copy(status = status, error = if (status == DownloadStatus.FAILED) (e.message ?: e.toString()).take(300) else null, updatedAt = now()))
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun processId(id: Long) = "dl-$id"
}
