package com.vibe.player.downloads

import java.io.File
import java.net.URI

/** One entry a URL resolves to. A playlist URL resolves to many. */
data class ResolvedEntry(
    val url: String,
    val title: String?,
    val uploader: String?,
    val thumbnailUrl: String?,
    val durationSec: Long?,
)

data class DownloadOutput(val file: File, val displayName: String, val mimeType: String)

class UnsupportedSourceException(message: String) : Exception(message)

/**
 * Each adapter knows how to resolve and fetch audio from one kind of source.
 * Adding a new source = one new class implementing this and registering it in [SourceRegistry].
 */
interface SourceAdapter {
    val id: String
    val displayName: String
    fun canHandle(url: String): Boolean

    /** Expands a URL into one or more downloadable entries (metadata only, no download). [processId] lets [cancel] stop it. */
    suspend fun resolve(url: String, processId: String? = null): List<ResolvedEntry>

    /** Downloads one entry into [workDir]. [onProgress] gets (0..100 or -1, etaSeconds or null, status line). */
    suspend fun download(
        url: String,
        audioFormat: String,
        bitrateKbps: Int,
        workDir: File,
        processId: String,
        onProgress: (Float, Long?, String?) -> Unit,
    ): DownloadOutput

    fun cancel(processId: String)
}

object UrlValidator {
    fun normalize(raw: String): String? {
        val s = raw.trim().trim('"', '\'', '<', '>')
        if (s.isEmpty()) return null
        val withScheme = if (s.startsWith("http://") || s.startsWith("https://")) s else "https://$s"
        return runCatching {
            val u = URI(withScheme)
            if (u.host.isNullOrBlank() || !u.host.contains('.')) null else withScheme
        }.getOrNull()
    }

    fun host(url: String): String = runCatching { URI(url).host.orEmpty().removePrefix("www.").lowercase() }.getOrDefault("")

    /** Splits pasted text into candidate URLs: newlines, spaces, commas and semicolons all separate entries. */
    fun extractAll(text: String): List<String> =
        text.split('\n', '\r', ' ', '\t', ',', ';').mapNotNull { normalize(it) }.distinct()
}

class SourceRegistry(private val adapters: List<SourceAdapter>) {
    val all: List<SourceAdapter> get() = adapters
    fun forUrl(url: String): SourceAdapter? = adapters.firstOrNull { it.canHandle(url) }
    fun byId(id: String): SourceAdapter? = adapters.firstOrNull { it.id == id }
    fun supportedSummary(): String = adapters.joinToString(" · ") { it.displayName }
}
