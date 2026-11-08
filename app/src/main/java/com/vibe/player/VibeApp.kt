package com.vibe.player

import android.app.Application
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.vibe.player.metadata.ArtworkFetcher

class VibeApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (getProcessName().endsWith(":restart")) return // trampoline process: nothing to set up
        container = AppContainer(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ArtworkFetcher.Factory(context, container.artworkStore))
                add(ArtworkFetcher.ArtworkKeyer)
            }
            .crossfade(true)
            .build()
}
