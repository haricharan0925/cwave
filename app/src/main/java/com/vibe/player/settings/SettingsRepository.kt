package com.vibe.player.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibe.player.ui.theme.DEFAULT_ACCENT_ARGB
import com.vibe.player.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class LibrarySort(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), DATE_ADDED("Date added"), DURATION("Duration")
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val accentArgb: Int = DEFAULT_ACCENT_ARGB,
    val destinationTreeUri: String? = null,
    val mp3Bitrate: Int = 320,
    val librarySort: LibrarySort = LibrarySort.TITLE,
    val librarySortDesc: Boolean = false,
    val formatFilter: String? = null,
    val ytdlpVersion: String? = null,
    val ytdlpUpdatedAt: Long = 0,
    val lastScanAt: Long = 0,
    /** Per-collection song order, keyed by "album" / "playlist" / "artist" / "folder"; value "NAME", "DATE_ADDED" or "DEFAULT", suffixed ":desc" when reversed. */
    val collectionSorts: Map<String, String> = emptyMap(),
    val onboardingDone: Boolean = false,
    val hideRecordings: Boolean = true,
    val hiddenFolders: Set<String> = emptySet(),
)

data class SavedPlayback(
    val trackIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val THEME = stringPreferencesKey("theme")
        val ACCENT = intPreferencesKey("accent_argb")
        val DEST = stringPreferencesKey("destination_tree")
        val MP3_BITRATE = intPreferencesKey("mp3_bitrate")
        val SORT = stringPreferencesKey("library_sort")
        val SORT_DESC = booleanPreferencesKey("library_sort_desc")
        val FORMAT_FILTER = stringPreferencesKey("format_filter")
        val YTDLP_VERSION = stringPreferencesKey("ytdlp_version")
        val YTDLP_UPDATED_AT = longPreferencesKey("ytdlp_updated_at")
        val LAST_SCAN = longPreferencesKey("last_scan")
        val COLLECTION_SORTS = stringSetPreferencesKey("collection_sorts")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val HIDE_RECORDINGS = booleanPreferencesKey("hide_recordings")
        val HIDDEN_FOLDERS = stringSetPreferencesKey("hidden_folders")

        val Q_IDS = stringPreferencesKey("queue_ids")
        val Q_INDEX = intPreferencesKey("queue_index")
        val Q_POS = longPreferencesKey("queue_pos")
        val Q_SHUFFLE = booleanPreferencesKey("queue_shuffle")
        val Q_REPEAT = intPreferencesKey("queue_repeat")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeMode = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            accentArgb = p[K.ACCENT] ?: DEFAULT_ACCENT_ARGB,
            destinationTreeUri = p[K.DEST],
            mp3Bitrate = p[K.MP3_BITRATE] ?: 320,
            librarySort = p[K.SORT]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.TITLE,
            librarySortDesc = p[K.SORT_DESC] ?: false,
            formatFilter = p[K.FORMAT_FILTER],
            ytdlpVersion = p[K.YTDLP_VERSION],
            ytdlpUpdatedAt = p[K.YTDLP_UPDATED_AT] ?: 0,
            lastScanAt = p[K.LAST_SCAN] ?: 0,
            collectionSorts = (p[K.COLLECTION_SORTS] ?: emptySet()).mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap(),
            onboardingDone = p[K.ONBOARDED] ?: false,
            hideRecordings = p[K.HIDE_RECORDINGS] ?: true,
            hiddenFolders = p[K.HIDDEN_FOLDERS] ?: emptySet(),
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[K.THEME] = mode.name }
    suspend fun setAccent(argb: Int) = context.dataStore.edit { it[K.ACCENT] = argb }
    suspend fun setDestination(uri: String?) = context.dataStore.edit { if (uri == null) it.remove(K.DEST) else it[K.DEST] = uri }
    suspend fun setMp3Bitrate(kbps: Int) = context.dataStore.edit { it[K.MP3_BITRATE] = kbps }
    suspend fun setLibrarySort(sort: LibrarySort, desc: Boolean) = context.dataStore.edit { it[K.SORT] = sort.name; it[K.SORT_DESC] = desc }
    suspend fun setFormatFilter(format: String?) = context.dataStore.edit { if (format == null) it.remove(K.FORMAT_FILTER) else it[K.FORMAT_FILTER] = format }
    suspend fun setYtdlpVersion(v: String?) = context.dataStore.edit { if (v == null) it.remove(K.YTDLP_VERSION) else it[K.YTDLP_VERSION] = v }
    suspend fun setYtdlpUpdatedAt(at: Long) = context.dataStore.edit { it[K.YTDLP_UPDATED_AT] = at }
    suspend fun setLastScan(at: Long) = context.dataStore.edit { it[K.LAST_SCAN] = at }
    suspend fun setCollectionSort(kind: String, value: String) = context.dataStore.edit { p ->
        val others = (p[K.COLLECTION_SORTS] ?: emptySet()).filterNot { it.startsWith("$kind=") }
        p[K.COLLECTION_SORTS] = (others + "$kind=$value").toSet()
    }
    suspend fun setOnboardingDone() = context.dataStore.edit { it[K.ONBOARDED] = true }
    suspend fun setHideRecordings(hide: Boolean) = context.dataStore.edit { it[K.HIDE_RECORDINGS] = hide }
    suspend fun setHiddenFolders(folders: Set<String>) = context.dataStore.edit { it[K.HIDDEN_FOLDERS] = folders }

    suspend fun savePlayback(state: SavedPlayback) = context.dataStore.edit {
        it[K.Q_IDS] = state.trackIds.joinToString(",")
        it[K.Q_INDEX] = state.index
        it[K.Q_POS] = state.positionMs
        it[K.Q_SHUFFLE] = state.shuffle
        it[K.Q_REPEAT] = state.repeatMode
    }

    suspend fun savePosition(index: Int, positionMs: Long) = context.dataStore.edit {
        it[K.Q_INDEX] = index
        it[K.Q_POS] = positionMs
    }

    suspend fun loadPlayback(): SavedPlayback? {
        val p = context.dataStore.data.first()
        val ids = p[K.Q_IDS]?.split(',')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.isNotEmpty() } ?: return null
        return SavedPlayback(ids, p[K.Q_INDEX] ?: 0, p[K.Q_POS] ?: 0L, p[K.Q_SHUFFLE] ?: false, p[K.Q_REPEAT] ?: 0)
    }
}
