# Innioasis Y1 Sync Runtime

Android companion for **Innioasis Y1**-style setups: copy music from an **SMB** share to the device, index it for browsing, manage **playlists**, and control everything from an **embedded web console** on your LAN.

---

## Highlights

- **SMB sync (v1)** — One-way copy from a configured share to internal storage or SD card, with incremental skips (size + remote modified time), progress reporting, and per-file error detail when something fails.
- **Library** — Metadata from audio files (artist, album, title, duration, etc.), sort/search, bulk delete, and add-to-playlist flows from the web UI.
- **Playlists** — Create, edit, reorder (drag-and-drop in the web UI), duplicate, and export **M3U8**.
- **Web console** — Bundled HTML/JS UI served over HTTP (default port **8081**, configurable in app settings). Dashboard, profiles, sync status, maintenance, updates, and settings.
- **Maintenance** — Actions such as rebuilding the sync/library index, cleaning partial (`.part`) files, pruning empty folders, and surfacing failed or incomplete download log hints.
- **Updates** — **APK**: checks the latest [GitHub Release](https://github.com/ophirf15/Y1-Sync-App/releases) and can trigger download + install (where the OS allows). **Web UI bundle**: separate manifest-driven updates so the console can refresh without a full APK bump. See [`docs/updates.md`](docs/updates.md).

---

## Requirements

| Item | Value |
|------|--------|
| **minSdk** | 17 (Android 4.2.2) |
| **targetSdk** | 34 |
| **compileSdk** | 34 |
| **Java** | 8 (source/target compatibility) |

Use **Android Studio** with a recent AGP (this project uses the version catalog in `gradle/libs.versions.toml`).

---

## Build

From the repository root:

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

Release APK (after you configure signing in Android Studio or `signingConfigs`):

```bash
.\gradlew.bat assembleRelease
```

Outputs are under `app/build/outputs/apk/`.

---

## Quick start

1. Install the APK on your phone or head unit (same Wi‑Fi as your SMB server).
2. Open the app and grant **storage** / **install** permissions as prompted (Android version dependent).
3. Create or edit a **sync profile** (host, share, credentials, local root and folder).
4. Start the **HTTP server** and open the shown address in a browser (e.g. `http://<device-ip>:8081/` — confirm the port on the device).
5. Use **Run Sync** from the app or the web console; new files are scanned into the library and the system media index where applicable.

---

## Documentation

| Doc | Purpose |
|-----|--------|
| [`docs/architecture-stage1.md`](docs/architecture-stage1.md) | High-level modules and asset strategy |
| [`docs/updates.md`](docs/updates.md) | Manifest URL vs APK updates on GitHub |
| [`docs/api-stage1.md`](docs/api-stage1.md) | HTTP API overview (stage 1) |
| [`docs/INITIAL_RELEASE.md`](docs/INITIAL_RELEASE.md) | Notes for the first public GitHub Release |

---

## Repository layout (short)

- `app/` — Android application, UI, `assets/web/bundled` web console
- `app/src/main/java/io/innoasis/y1syncer/` — runtime, sync, server, DB, library, updates
- `docs/` — architecture, API, update strategy, release notes

---

## Contributing & support

Issues and PRs are welcome on the canonical GitHub repo. For the first release narrative and feature list, use [`docs/INITIAL_RELEASE.md`](docs/INITIAL_RELEASE.md) as the basis for your GitHub **Release** description.

---

## License

No license file is bundled in this repository yet. Add a `LICENSE` file when you decide how you want to distribute the project.
