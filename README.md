# Cwave

A personal-use Android music player built with **Kotlin + Jetpack Compose**.  
It plays local audio, downloads tracks from YouTube via yt-dlp, and handles conversion / trimming with FFmpeg — all in a clean Material 3 UI.

---

## Features

| Category | Details |
|---|---|
| **Playback** | ExoPlayer / Media3 with a persistent foreground service and lock-screen / notification controls |
| **Library** | MediaStore scanner + file importer; browse by tracks, albums, artists, and playlists |
| **Downloads** | YouTube & direct-URL downloads powered by yt-dlp (bundled Python runtime) |
| **Conversion** | Audio format conversion and trimming via FFmpeg Kit (audio-only, LGPL build) |
| **Playlists** | Create, reorder (drag-and-drop), and manage playlists stored in Room |
| **Settings** | Accent colour picker, YouTube sign-in, theme options |
| **Open with** | Registered as an audio file handler — tap any `mp3 / flac / m4a / wav / ogg / opus` from a file manager or browser to open it directly in Cwave |
| **Backup** | Library backup / restore |

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| UI | Jetpack Compose · Material 3 · Navigation Compose |
| Playback | Media3 ExoPlayer + MediaSession |
| Downloads | youtubedl-android (yt-dlp + bundled Python + FFmpeg) |
| Conversion | FFmpeg Kit Audio (LGPL) |
| Database | Room (entities, DAOs, migrations) |
| Preferences | Jetpack DataStore |
| Background work | WorkManager (foreground `dataSync` service) |
| Images | Coil 3 |
| Reorderable lists | reorderable |
| DI / wiring | Manual `AppContainer` (no Hilt/Koin) |
| Build | Gradle (Kotlin DSL) · KSP · Kotlin 2.x |

---

## Requirements

- **Android 8.0+** (API 26 — `minSdk`)
- **Android Studio Meerkat** (or newer) with the Android SDK for API 36
- NDK with `x86_64` and `arm64-v8a` ABI support (needed by yt-dlp's native libs)

---

## Building

```bash
# Clone
git clone <repo-url>
cd cwave

# Build a debug APK
./gradlew assembleDebug

# Install directly to a connected device / emulator
./gradlew installDebug
```

> The release build type is configured to sign with the **debug keystore** for personal use,  
> so `assembleRelease` also installs over a debug build without key management.

---

## Project Structure

```
cwave/
├── app/src/main/java/com/vibe/player/
│   ├── backup/          # Backup / restore logic
│   ├── convert/         # AudioConverter, AudioTrimmer (FFmpeg Kit)
│   ├── data/            # Room DB (Entities, DAOs, VibeDatabase), LibraryRepository
│   ├── downloads/       # yt-dlp adapter, WorkManager worker, YouTube auth
│   ├── library/         # MediaStore scanner, file importer
│   ├── metadata/        # Tag reading / writing
│   ├── player/          # PlaybackService (Media3 MediaSessionService)
│   ├── settings/        # DataStore-backed settings repository
│   └── ui/
│       ├── components/  # Shared composables (dialogs, menus, selection)
│       ├── downloads/   # Downloads & convert screens + ViewModels
│       ├── home/        # Home screen
│       ├── library/     # Library & detail screens, LibraryViewModel
│       ├── navigation/  # Route definitions
│       ├── player/      # Now Playing screen, Mini-player, Artwork stage
│       ├── playlists/   # Playlists screen, add-songs sheet
│       ├── settings/    # Settings screen, accent picker, YouTube sign-in
│       ├── theme/       # Material 3 theme
│       └── trim/        # Trim screen
├── app/build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | YouTube downloads |
| `WAKE_LOCK` | Keep CPU awake during playback |
| `FOREGROUND_SERVICE` / `_MEDIA_PLAYBACK` / `_DATA_SYNC` | Playback & download foreground services |
| `POST_NOTIFICATIONS` | Media notification |
| `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤ API 32) | Scan local audio files |

---

## License

Personal-use project. No licence granted for redistribution.
