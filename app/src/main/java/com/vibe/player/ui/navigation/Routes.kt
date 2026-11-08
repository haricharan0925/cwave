package com.vibe.player.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Switch the visible tab from inside a screen (e.g. Home's "See all" → Library). */
val LocalSwitchTab = androidx.compose.runtime.compositionLocalOf<(String) -> Unit> { {} }

object Routes {
    /** Root destination: hosts the five tabs. Tab choice is UI state, never a back-stack entry. */
    const val MAIN = "main"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PLAYLISTS = "playlists"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"

    const val SEARCH = "search"
    const val LICENSES = "licenses"
    const val YT_SIGNIN = "yt_signin"
    const val TRIM = "trim/{id}"
    fun trim(id: Long) = "trim/$id"
    const val ALBUM = "album/{key}"
    const val ARTIST = "artist/{name}"
    const val FOLDER = "folder/{path}"
    const val PLAYLIST = "playlist/{id}"

    /** key = album display name; the screen groups by albumKeyOf(name). */
    fun album(key: String) = "album/${enc(key)}"
    fun artist(name: String) = "artist/${enc(name)}"
    fun folder(path: String) = "folder/${enc(path)}"
    fun playlist(id: Long) = "playlist/$id"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    fun dec(s: String): String = java.net.URLDecoder.decode(s, "UTF-8")
}

data class TopLevelDestination(
    val route: String,
    val label: String,
    val selected: ImageVector,
    val unselected: ImageVector,
)

val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    TopLevelDestination(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    TopLevelDestination(Routes.PLAYLISTS, "Playlists", Icons.Filled.QueueMusic, Icons.Outlined.QueueMusic),
    TopLevelDestination(Routes.DOWNLOADS, "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)
