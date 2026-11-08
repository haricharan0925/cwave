package com.vibe.player.downloads

import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * yt-dlp backed adapter. Handles YouTube / YouTube Music plus a short allow-list of other
 * audio-centric sites yt-dlp supports well. Audio is extracted and re-encoded by yt-dlp's bundled ffmpeg.
 */
class YtDlpAdapter(private val ytdlp: YtDlpManager, private val auth: YouTubeAuth? = null) : SourceAdapter {

    /** Options shared by resolve + download: optional signed-in cookies, gentle pacing, non-web clients first. */
    private fun YoutubeDLRequest.applyCommon(url: String, useCookies: Boolean) {
        if (UrlValidator.host(url).contains("youtu")) {
            if (useCookies) auth?.cookieArgIfAny()?.let { addOption("--cookies", it) }
            // yt-dlp's default client set gives the fullest format list (the android/ios clients only expose
            // low muxed streams without a PO token). Bot checks are handled by cookies + pacing instead.
        }
        // Bursts are what trip YouTube's rate limiter; a little spacing costs almost nothing.
        addOption("--sleep-requests", "1")
        addOption("--sleep-interval", "2")
        addOption("--max-sleep-interval", "5")
    }

    override val id = "ytdlp"
    override val displayName = "YouTube, YouTube Music, SoundCloud, Bandcamp, Vimeo"

    private val hosts = listOf(
        "youtube.com", "youtu.be", "music.youtube.com", "m.youtube.com", "youtube-nocookie.com",
        "soundcloud.com", "on.soundcloud.com", "bandcamp.com", "vimeo.com",
    )

    override fun canHandle(url: String): Boolean {
        val h = UrlValidator.host(url)
        return hosts.any { h == it || h.endsWith(".$it") }
    }

    fun isPlaylistUrl(url: String): Boolean {
        val h = UrlValidator.host(url)
        val isYt = h.contains("youtu")
        return (isYt && (url.contains("/playlist") || (url.contains("list=") && !url.contains("v=")))) ||
            (h.contains("soundcloud") && url.contains("/sets/")) ||
            (h.contains("bandcamp") && url.contains("/album/"))
    }

    /**
     * Runs [block] with the saved YouTube cookies; if YouTube rejects them as stale, forgets them and runs once more
     * signed out. Dead cookies make every request fail, while an anonymous request usually works.
     */
    private suspend fun <T> withCookieFallback(url: String, block: suspend (useCookies: Boolean) -> T): T {
        val hadCookies = auth?.cookieArgIfAny() != null && UrlValidator.host(url).contains("youtu")
        return try {
            block(true)
        } catch (e: Throwable) {
            if (!hadCookies || !isStaleSession(e)) throw e
            auth?.dropStaleSession()
            block(false)
        }
    }

    private fun isStaleSession(e: Throwable): Boolean {
        if (e.message == STALE_SESSION_MESSAGE) return true
        val raw = "${e.message} ${e.cause?.message}".lowercase()
        return "needs to be reloaded" in raw || "cookies are no longer valid" in raw
    }

    override suspend fun resolve(url: String, processId: String?): List<ResolvedEntry> =
        withCookieFallback(url) { useCookies -> resolveOnce(url, processId, useCookies) }

    private suspend fun resolveOnce(url: String, processId: String?, useCookies: Boolean): List<ResolvedEntry> {
        val req = YoutubeDLRequest(url).apply {
            applyCommon(url, useCookies)
            addOption("--socket-timeout", "20")
            addOption("--extractor-retries", "2")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--ignore-errors")
            addOption("--print", "%(webpage_url)s\t%(title)s\t%(uploader,channel,artist)s\t%(thumbnail)s\t%(duration)s")
            if (isPlaylistUrl(url)) addOption("--flat-playlist") else addOption("--no-playlist")
        }
        // A throttled session can leave yt-dlp retrying for many minutes; kill it rather than sit on "Resolving…" forever.
        val pid = processId ?: "resolve-${System.nanoTime()}"
        var timedOut = false
        val resp = try {
            coroutineScope {
                val watchdog = launch { delay(RESOLVE_TIMEOUT_MS); timedOut = true; ytdlp.cancel(pid) }
                try { ytdlp.execute(req, pid, null) } finally { watchdog.cancel() }
            }
        } catch (e: Throwable) {
            if (timedOut) throw IllegalStateException("YouTube didn't answer within ${RESOLVE_TIMEOUT_MS / 1000} s (often a sign it's throttling this connection). Retry in a while, or switch networks.", e)
            throw IllegalStateException(friendlyError(e.message ?: e.toString()), e)
        }
        val entries = resp.out.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2 || parts[0].isBlank()) return@mapNotNull null
            ResolvedEntry(
                url = parts[0].trim(),
                title = parts.getOrNull(1)?.takeUnless { it == "NA" || it.isBlank() },
                uploader = parts.getOrNull(2)?.takeUnless { it == "NA" || it.isBlank() },
                thumbnailUrl = parts.getOrNull(3)?.takeUnless { it == "NA" || it.isBlank() },
                durationSec = parts.getOrNull(4)?.toDoubleOrNull()?.toLong(),
            )
        }.toList()
        if (entries.isEmpty()) throw IllegalStateException(friendlyError(resp.err.ifBlank { resp.out }))
        return entries
    }

    override suspend fun download(
        url: String,
        audioFormat: String,
        bitrateKbps: Int,
        workDir: File,
        processId: String,
        onProgress: (Float, Long?, String?) -> Unit,
    ): DownloadOutput = withCookieFallback(url) { useCookies ->
        if (!useCookies) onProgress(-1f, null, "YouTube sign-in expired; continuing signed out…")
        downloadOnce(url, audioFormat, bitrateKbps, workDir, processId, useCookies, onProgress)
    }

    private suspend fun downloadOnce(
        url: String,
        audioFormat: String,
        bitrateKbps: Int,
        workDir: File,
        processId: String,
        useCookies: Boolean,
        onProgress: (Float, Long?, String?) -> Unit,
    ): DownloadOutput {
        workDir.mkdirs()
        val outTemplate = File(workDir, "%(title).150B [%(id)s].%(ext)s").absolutePath
        val video = audioFormat == VIDEO_FORMAT

        fun request(withThumb: Boolean) = YoutubeDLRequest(url).apply {
            applyCommon(url, useCookies)
            if (video) {
                // Highest resolution up to the chosen cap (bitrateKbps carries the max height for video; 0 = no cap), but
                // prefer streams that mux into MP4 without re-encoding (H.264/AV1 video + AAC audio). VP9+Opus → MP4 fails
                // on many videos, so those are only a last resort; if nothing fits the cap at all, fall back to anything.
                val h = if (bitrateKbps > 0) "[height<=$bitrateKbps]" else ""
                // HLS-only streams (e.g. YouTube's "Premium" 1080p) come as hundreds of tiny segments and crawl on mobile,
                // so plain HTTPS (DASH) streams are tried first at every step.
                val https = "[protocol^=http][protocol!*=m3u8]"
                addOption(
                    "-f",
                    "bv*$h[ext=mp4]$https+ba[ext=m4a]$https/bv*$h[ext=mp4]+ba[ext=m4a]/b$h[ext=mp4]/" +
                        "bv*$h[vcodec!*=vp9]$https+ba$https/bv*$h[vcodec!*=vp9]+ba/bv*$h+ba/b$h/bv*+ba/b",
                )
                addOption("--concurrent-fragments", "4")
                addOption("--merge-output-format", "mp4")
                addOption("--recode-video", "mp4")
                addOption("--postprocessor-args", "VideoConvertor:-c:v copy -c:a aac -b:a 192k -movflags +faststart")
            } else {
                addOption("-x")
                addOption("--audio-format", audioFormat)
                if (audioFormat == "mp3" && bitrateKbps > 0) addOption("--audio-quality", "${bitrateKbps}K")
            }
            addOption("--embed-metadata")
            // Thumbnail embedding into MP4 needs AtomicParsley, which the bundled ffmpeg build doesn't ship; audio only.
            if (withThumb && !video) { addOption("--embed-thumbnail"); addOption("--convert-thumbnails", "jpg") }
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--no-warnings")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--socket-timeout", "30")
            addOption("-o", outTemplate)
        }

        val response = try {
            ytdlp.execute(request(withThumb = true), processId) { p, eta, line -> onProgress(p, eta.takeIf { it >= 0 }, line) }
        } catch (first: Throwable) {
            if (first.message?.contains("thumbnail", ignoreCase = true) == true || first.message?.contains("EmbedThumbnail", ignoreCase = true) == true) {
                onProgress(-1f, null, "Retrying without cover art…")
                ytdlp.execute(request(withThumb = false), processId) { p, eta, line -> onProgress(p, eta.takeIf { it >= 0 }, line) }
            } else throw IllegalStateException(friendlyError(first.message ?: first.toString()), first)
        }

        val ext = if (video) "mp4" else audioFormat
        val produced = workDir.listFiles { f -> f.isFile && f.extension.equals(ext, ignoreCase = true) }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException(friendlyError(response.err.ifBlank { "yt-dlp finished but produced no .$ext file" }))
        val cleanName = produced.name.replace(Regex("\\s*\\[[A-Za-z0-9_-]{6,}]\\.$ext$"), ".$ext")
        return DownloadOutput(produced, cleanName, if (video) "video/mp4" else OutputStore.mimeFor(audioFormat))
    }

    companion object {
        const val VIDEO_FORMAT = "mp4-video"
        const val RESOLVE_TIMEOUT_MS = 90_000L
        const val STALE_SESSION_MESSAGE = "YouTube rejected the saved sign-in (it expired). Sign in again from Settings, or retry to continue signed out."
        const val BOT_CHECK_MESSAGE = "YouTube is asking for a bot check. Sign in to YouTube from Settings to stop this, or try again later."
    }

    override fun cancel(processId: String) { ytdlp.cancel(processId) }

    private fun friendlyError(raw: String): String {
        val r = raw.lowercase()
        return when {
            "sign in to confirm" in r || "not a bot" in r ->
                if (auth?.account?.value?.signedIn == true) "YouTube bot check even while signed in. Wait a while, or sign out and back in from Settings."
                else BOT_CHECK_MESSAGE
            // yt-dlp words YouTube's per-IP rate limit as "Video unavailable. This content isn't available, try again later".
            "try again later" in r || "rate-limited" in r || "captcha" in r ->
                "YouTube is temporarily blocking this connection (rate limit, up to ~1 hour). The video itself is fine: wait, or switch Wi-Fi ↔ mobile data, then retry. Signing in from Settings helps too."
            "needs to be reloaded" in r || "cookies are no longer valid" in r -> STALE_SESSION_MESSAGE
            "video unavailable" in r -> "Video unavailable (private, removed, or region-locked)."
            "private video" in r -> "This video is private."
            "age" in r && "confirm" in r -> "Age-restricted video; can't download without a signed-in session."
            "unsupported url" in r -> "yt-dlp doesn't support this URL."
            "http error 429" in r || "too many requests" in r -> "YouTube is rate-limiting this connection (HTTP 429). Wait an hour or switch networks (e.g. mobile data), then retry. Signing in from Settings helps too."
            "network" in r || "unable to download" in r || "timed out" in r || "resolve host" in r -> "Network error while downloading. Check your connection and retry."
            "ffmpeg" in r && "not found" in r -> "Bundled ffmpeg failed to initialise."
            "requested format is not available" in r -> "YouTube offered no stream matching the requested quality. Try again, or update yt-dlp in Settings."
            "postprocessing" in r || "conversion failed" in r || "ffmpeg exited" in r -> "Downloaded, but converting/merging the file failed: " + raw.lines().lastOrNull { "error" in it.lowercase() }?.trim()?.take(160).orEmpty()
            else -> raw.lines().lastOrNull { it.isNotBlank() }?.removePrefix("ERROR:")?.trim()?.take(240) ?: "Download failed"
        }
    }
}
