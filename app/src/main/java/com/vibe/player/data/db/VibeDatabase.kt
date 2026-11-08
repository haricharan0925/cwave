package com.vibe.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Track::class, Favorite::class, PlayHistory::class, Playlist::class, PlaylistTrack::class, DownloadItem::class, ConversionJob::class],
    version = 3,
    exportSchema = false,
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun favorites(): FavoriteDao
    abstract fun history(): HistoryDao
    abstract fun playlists(): PlaylistDao
    abstract fun downloads(): DownloadDao
    abstract fun conversions(): ConversionDao

    companion object {
        /** v2: per-track `hidden` flag (recordings filter / hidden folders). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3: `userEdited` flag so in-app tag edits survive rescans. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): VibeDatabase =
            Room.databaseBuilder(context, VibeDatabase::class.java, "vibe.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
