# Cwave — personal Android music player (package `com.vibe.player`)

Native Android music player with local library, background playback, playlists, YouTube/yt-dlp
downloads and bundled-FFmpeg conversion. Built for **personal use on your own device** — it is not
intended for distribution (see *Licensing* below).

- Package: `com.vibe.player` (internal id kept; app name is Cwave) · minSdk 26 · targetSdk 36
- Kotlin 2.2.20 · Jetpack Compose (BOM 2025.09.01, Material 3) · Media3 1.8.0 · Room 2.8.1 · WorkManager 2.10.5
- Tested on: Pixel emulator, Android 16 (API 36), x86_64

## Build & install

```bat
gradlew.bat assembleDebug
gradlew.bat installDebug
C:\Android\platform-tools\adb.exe shell am start -n com.vibe.player/.MainActivity
```

APK: `app\build\outputs\apk\debug\app-debug.apk` (≈ 150 MB — see *Why so big*).

Requirements: JDK 17, Android SDK at `C:\Android` (`local.properties` → `sdk.dir=C:/Android`), platform 36, build-tools 36.

## What's implemented

**Library** — MediaStore scan (auto on launch + manual rescan), SAF import of files and whole folders
(referenced by URI, never copied), Songs / Albums / Artists / Folders / Recently added, search by title,
artist, album and filename, sort by title / artist / album / date added / duration, filter by format,
missing-file detection, per-track details sheet. Formats: whatever ExoPlayer decodes on the device
(MP3, FLAC, WAV, M4A/AAC, OGG, Opus verified in code paths; MP3/WAV/FLAC verified on the emulator).

**Playback** — Media3 ExoPlayer inside a `MediaSessionService`: play/pause, next/previous, seek,
shuffle, repeat off/all/one, queue with drag-reorder and remove, play-next / add-to-queue,
background playback with media notification and lock-screen controls, audio-focus handling,
"becoming noisy" pause (headphones unplugged), Bluetooth/headset media buttons via the session,
last queue + position restored on restart, unsupported files skipped with a snackbar.

**Playlists & favorites** — create/rename/delete, add/remove, drag-reorder, favorites, play/shuffle,
counts and durations. Stored in Room; survives restarts.

**Downloads** — multiline URL box with paste, bulk input, URL validation, duplicate detection,
playlist expansion, progress %, ETA, status line, cancel/retry/remove, persisted queue, network-constrained
`WorkManager` foreground worker, auto library refresh, configurable destination folder.
Source adapters (`downloads/SourceAdapter.kt`):
- `YtDlpAdapter` — YouTube, YouTube Music, SoundCloud, Bandcamp, Vimeo via bundled yt-dlp.
  Audio mode: `-x --audio-format mp3 --audio-quality <bitrate>K --embed-metadata --embed-thumbnail` → `Music/Cwave`.
  Video mode ("Video" toggle + quality chips Best/4K/1440p/1080p/720p/480p/360p): `-f bv*[height<=N][ext=mp4]+ba[ext=m4a]/…`
  with `--recode-video mp4` fallback → `Movies/Cwave`; the cap is stored in the queue item's bitrate column;
  videos are not added to the music library but open from the queue's ▶ button.
- `DirectUrlAdapter` — plain `https://…/file.mp3|flac|wav|m4a|ogg|opus` (or any URL whose server reports `audio/*`).
- Anything else is shown as *Unsupported* with the list of supported sources.

yt-dlp self-updates from GitHub at most once per day before a download, and manually from Settings.

**Convert** — pick any audio file *or video* (MP4, MKV, WebM, MOV, AVI, 3GP…; the first audio track is extracted with `-vn -map 0:a:0`), output MP3 (128/192/256/320 kbps), WAV (16-bit PCM) or FLAC;
progress, cancel, history, existing-file dialog (Overwrite / Keep both / Cancel), optional delete-original
(off by default, reports when Android refuses), honest note that lossy → 320 kbps / FLAC does not
restore quality. Encoders are probed at runtime (`ffmpeg -encoders`) and formats without an encoder are disabled.

**Now Playing extras** — drag the sheet down to minimise (follows the finger, springs back or slides off).

**Custom album art** — track menu → *Set album art* → system photo picker → apply to the song or the whole
album. Pictures are stored privately in `files/artwork/`; audio files are never modified.

**Recordings filter** — call recordings, voice memos, WhatsApp audio/voice notes and recorder folders are hidden by default
(`IS_RECORDING` flag + folder/name heuristics); any folder can be hidden from Library → Folders → ⋮ and
unhidden in Settings.

**Backup** — Settings → Backup exports one zip (`manifest.json` first, then `db/vibe.db`, DataStore prefs,
custom artwork, YouTube cookies and every library audio file under `songs/<id>/`). Import validates the
manifest (`app == "cwave"`, format/schema not newer than the app), restores songs through MediaStore into
`Music/Cwave/Restored` when their original URI no longer resolves, remaps those URIs in a staged copy of the
database, swaps the files in and relaunches through `RestartActivity` (runs in its own `:restart` process).

**Library hygiene** — per-song *Delete file from device* (own confirmation dialog; Android's consent sheet for
files owned by other apps), *Edit details* (title/artist/album with existing-name suggestions; survives rescans
via `userEdited`), rename artist/album, custom covers for songs, albums and playlists, lists paged at 100 rows
with *Show more / Show all*, recordings filter, hidden folders.

**YouTube sign-in (optional)** — Settings → YouTube opens an in-app login; cookies are exported to a Netscape
file passed to yt-dlp (`--cookies`) to avoid the "confirm you're not a bot" wall. Video mode uses
`bv*[ext=mp4]+ba[ext=m4a]/…` with `--recode-video mp4` fallback; do **not** override `player_client` — the
android/ios clients only expose ≤360p muxed streams without a PO token (measured: 640×272 vs 2048×872).

**Trim / cut** — song ⋮ → *Trim / cut*: range slider + ±1 s / ±0.1 s nudges, preview plays only the selection
(private ExoPlayer with `ClippingConfiguration`), then *Save as new song* (`<title> (cut).<ext>` via `OutputStore`)
or *Replace original* (own confirmation; overwrites bytes in place so playlists/favourites/art keep working;
Android's write-consent sheet for files owned by other apps). FFmpeg `-c copy` first (lossless, frame-accurate
to ~25 ms); falls back to 320 k MP3 only for *new* files whose container refuses stream copy.

**Multi-select** — long-press any song, playlist or album card to enter selection mode (`SelectionController`
via `LocalSelection`; each list publishes its ids through `LocalSelectionScope` so *Select all* is list-aware).
Songs/albums: Play, Queue, Add to playlist, Remove from playlist (inside a playlist), Delete files.
Playlists: Delete. Batch file deletion bundles every file that needs permission into one MediaStore consent
request (`TrackDeleter.deleteMany`), so the user confirms once.

**Albums** — grouped by album *name only* (`albumKeyOf` = trimmed, lower-cased); an album with several artists
shows "Various artists". Users can build albums themselves: multi-select → *Album* moves songs into an existing or
new album, the album screen has *Add songs* (+), *Remove* (via multi-select), rename and custom cover. Edits set
`userEdited` so rescans keep them.

**Landscape** — tabs move to a `NavigationRail`, the mini-player shrinks, Now Playing becomes two-pane
(art left, controls right); its open state is `rememberSaveable` so rotation doesn't close it. Content is
inset from the display cutout app-wide. Long titles use `basicMarquee` (Now Playing, mini-player).

**Open with Cwave** — `MainActivity` has a VIEW filter for `audio/*` (+ ogg/flac/octet-stream by extension), so file
managers and messengers list Cwave for audio files. The URI goes through `AppContainer.externalOpen`; the UI imports it
if unknown, then plays it with Now Playing open. `singleTask` + `onNewIntent` handle opens while the app is running.
Downloads support multi-select (long-press → Remove; active ones are cancelled) and ▶ on a finished audio download
plays inside Cwave (videos still hand off to a video player).

**Collection sort** — album, artist, folder, playlist and Favorites screens have a Sort chip: Default (track number /
custom playlist order), Name, Date added; choosing the same option again flips direction. Saved per collection *type*
in DataStore (`collectionSorts`, e.g. `playlist=NAME:desc`). Drag-to-reorder is disabled while a playlist is sorted.

**Track-change animation** — `ArtworkStage` (not `AnimatedContent`): the outgoing cover stays painted underneath
while the incoming one slides in from the skip direction with a slight 3D tilt and settles; the blurred backdrop
cross-fades over 600 ms. `AnimatedContent` + `scaleOut` on an `AsyncImage` put the not-yet-decoded image on its own
render layer, which flashed solid green on some GPUs.

**Settings** — theme (system/dark/light), accent colour (presets + custom HSV/hex), hide recordings toggle, hidden folders, rescan, destination folder (SAF tree or default
`Music/Cwave`), default MP3 bitrate, yt-dlp version + update, FFmpeg version + encoder status, licenses.

## Architecture

```
com.vibe.player
├── AppContainer.kt        hand-rolled DI (singletons), VibeApp.kt (Coil + WorkManager config)
├── data/db                Room entities, DAOs, VibeDatabase · data/LibraryRepository.kt
├── library                MediaStoreScanner, FileImporter (SAF)
├── metadata               MetadataExtractor, ArtworkFetcher (Coil: embedded art → MediaStore art)
├── player                 PlaybackService (MediaSessionService), PlayerController (UI facade), QueueRestorer
├── downloads              SourceAdapter + YtDlpAdapter + DirectUrlAdapter, YtDlpManager,
│                          DownloadRepository, DownloadWorker, OutputStore (MediaStore / SAF writes)
├── convert                AudioConverter (FFmpegKit)
├── settings               SettingsRepository (DataStore)
└── ui                     theme/, navigation/, components/, player/, home/, library/, playlists/,
                           downloads/ (+ ConvertViewModel), settings/
```

Navigation: the five tabs are **UI state** (`tab` + `SaveableStateHolder`) inside one `main` NavHost
destination; only sub-screens (album, artist, playlist, trim, search, licenses, sign-in) are back-stack entries.
Earlier versions pushed tabs onto the back stack with `saveState/restoreState` and the NavHost's
AnimatedContent could get stuck mid-transition (blank screen, Home tap ignored) — don't go back to that.

Output files are written through MediaStore (`Music/Cwave`, `IS_PENDING` protocol) or the user's SAF
folder — never via raw filesystem paths, no `MANAGE_EXTERNAL_STORAGE`.

## Bundled engines, versions and licenses

| Component | Artifact | Version | License |
|---|---|---|---|
| FFmpeg (conversion) | `dev.ffmpegkit-maintained:ffmpeg-kit-audio` | 8.1.9 → FFmpeg **n8.1.3** | **LGPL-3.0** (not the GPL variant) |
| yt-dlp runtime | `io.github.junkfood02.youtubedl-android:library` + `:ffmpeg` | 0.18.1 (bundled Python + yt-dlp, ships QuickJS) | **GPL-3.0** (library), yt-dlp itself Unlicense |
| yt-dlp | self-updated at runtime | 2026.08.19 at time of testing | Unlicense |
| Media3, Room, Compose, WorkManager, Coil, Reorderable | — | see `app/build.gradle.kts` | Apache-2.0 |

FFmpegKit audio build encoders verified at runtime on the device:
`libmp3lame=true pcm_s16le=true flac=true` (also contains opus, libvorbis, shine, twolame, soxr, speex, amr).
All third-party notice files shipped in the AAR (`res/raw/license_*.txt`) are viewable in
*Settings → Open-source licenses*.

### Licensing — read this if you ever share the APK
- The conversion engine is the LGPL FFmpeg build; notices are preserved and shown in-app.
- `youtubedl-android` is **GPL-3.0**. For personal use on your own device this is fine. Distributing
  the APK would require GPL compliance for the whole app.
- Downloading from YouTube violates YouTube's Terms of Service. This app exists for your personal use
  by your explicit request; nothing here bypasses DRM.

## Why so big (≈ 150 MB)
Two ABIs (`x86_64` for the emulator, `arm64-v8a` for phones) × (Python runtime ≈ 15 MB + full FFmpeg
for yt-dlp ≈ 45 MB + FFmpegKit audio ≈ 10 MB). Remove `arm64-v8a` from `ndk.abiFilters` in
`app/build.gradle.kts` for an emulator-only build about half the size.

## Test results (Pixel emulator, Android 16, 2026-10-18)

| Area | Result |
|---|---|
| Build | `assembleDebug` succeeds, no Kotlin errors |
| Launch / permissions | No crashes; media + notification permission flow works |
| Library scan | 2 seeded files found; downloaded and converted files picked up automatically |
| Playback | `dumpsys media_session` → `PLAYING`; media notification present; auto-advance; queue restored after restart |
| Screen off | Playback continued with `mWakefulness=Asleep` |
| Now Playing | Blurred-artwork backdrop, seek, shuffle/repeat, favorite, queue sheet with drag-reorder |
| YouTube download | `youtu.be/jNQXAC9IVRw` → 483 KB MP3 192 kbps with embedded thumbnail, in library; a 3:35 music video (5.2 MB) also downloaded fine |
| Conversion | MP3→FLAC (`fLaC`, 1.2 MB), MP3→WAV (`RIFF`, 3.5 MB), MP3→MP3 320 kbps (`ID3`, 744 KB); conflict dialog → `Second track (1).flac` |
| Playlists | Create, add from track menu, favorites, drag-reorder persisted |
| Settings | Theme switch live, yt-dlp update, encoder status, licenses render from bundled texts |

Not tested here: real Bluetooth headset buttons, Android Auto, very large libraries (10k+ tracks),
SAF destination on removable SD cards.

## Known limitations
- YouTube changes break yt-dlp periodically. The app auto-updates daily; if a download fails with a
  bot-check message, tap *Update* in Settings and retry. Heavy use from one IP can trigger
  "Sign in to confirm you're not a bot" — cookie import is not implemented.
- Media notification artwork uses MediaStore album art only; files whose cover is embedded (most
  downloads) show their art in-app but not on the lock screen.
- WAV output cannot carry cover art; MP3/FLAC keep it when the source has one.
- "Delete original" cannot delete files owned by other apps on Android 11+ (Android restriction); the
  app reports this instead of failing the conversion.
- Playlist reorder uses a drag handle; swipe gestures are not implemented.
