package com.vibe.player.metadata

import android.content.Context
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import okio.FileSystem

/**
 * Coil model for a track's artwork. Priority: user-set track art → user-set album art → picture embedded
 * in the file → MediaStore album art (last, because MediaStore shares one image across untagged folders).
 * [version] comes from [ArtworkStore.version] so cached images roll over when the user changes art.
 */
data class Artwork(val trackUri: String, val artworkUri: String?, val albumKey: String? = null, val version: Int = 0)

class ArtworkFetcher(private val context: Context, private val store: ArtworkStore, private val data: Artwork) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val custom = data.trackUri.takeIf { it.isNotBlank() }?.let { store.trackFile(it).takeIf { f -> f.exists() } }
            ?: data.albumKey?.let { store.albumFile(it).takeIf { f -> f.exists() } }
        // Coil only caches successes, so remember which files have no embedded picture; otherwise every
        // art-less row re-opens MediaMetadataRetriever each time it scrolls into view.
        val embedded = data.trackUri.takeIf { it.isNotBlank() && !store.knownNoEmbeddedArt(it) }?.let { uri ->
            MetadataExtractor.embeddedArt(context, uri.toUri()).also { if (it == null) store.rememberNoEmbeddedArt(uri) }
        }
        val bytes = custom?.readBytes()
            ?: embedded
            ?: data.artworkUri?.let { art -> runCatching { context.contentResolver.openInputStream(art.toUri())?.use { it.readBytes() } }.getOrNull() }
            ?: return null
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context, private val store: ArtworkStore) : Fetcher.Factory<Artwork> {
        override fun create(data: Artwork, options: Options, imageLoader: ImageLoader): Fetcher = ArtworkFetcher(context, store, data)
    }

    object ArtworkKeyer : Keyer<Artwork> {
        override fun key(data: Artwork, options: Options): String =
            "art:${data.trackUri.ifBlank { data.artworkUri.orEmpty() }}:${data.albumKey.orEmpty()}:v${data.version}"
    }
}
