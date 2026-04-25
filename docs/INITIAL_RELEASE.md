# Initial release notes (template for GitHub Releases)

Use the sections below as the **Release title** and **description** when you publish the first APK on GitHub (e.g. tag `v0.1.0-stage1` or `v0.1.0`).

---

## Suggested release title

**Innioasis Y1 Sync Runtime v0.1.0-stage1**

*(Match `versionName` in `app/build.gradle.kts` if you change it before shipping.)*

---

## Short summary (one paragraph)

**Innioasis Y1 Sync Runtime** is an Android app that syncs music from an SMB share to your device, indexes tracks for browsing and playlists, and exposes a full **LAN web console** for management. This initial release focuses on reliable one-way SMB copy, library metadata, playlists with M3U8 export, maintenance tools, and update hooks for both **APK** (GitHub Releases) and **web bundle** (manifest URL) distribution.

---

## What’s included

- **SMB sync** — Copy from a configured share to internal storage or SD card; skip unchanged files; live progress and detailed per-file errors when sync fails.
- **Library** — Sort, search, delete (including bulk), metadata extraction, add-to-playlist from the web UI.
- **Playlists** — CRUD, reorder in the browser, duplicate, M3U8 export.
- **Web console** — Dashboard, profiles, library, playlists, sync view, maintenance, settings, updates (default HTTP port **8081** unless changed in settings).
- **Android UI** — On-device controls for server port, sync, and update checks.
- **Updates** — Compares installed version to the latest GitHub release; optional in-app APK download/install. Web UI can be refreshed separately via manifest — see `docs/updates.md` in the repo.

---

## Install

1. Download the **`.apk`** attached to this release.
2. On Android, allow install from this source if prompted.
3. Open the app, configure Wi‑Fi and permissions, then add a profile and sync.

**Requirements:** Android **4.2.2+** (API 17). Built targeting **API 34** for current Play and signing toolchains.

---

## Known limitations (honest first release)

- **WebDAV** in the profile editor is present as a protocol option; **SMB is the supported path for sync** in this release.
- Storage permission behavior varies by **Android version**; grant all prompts needed for your OS level.
- **Mirror delete** and other advanced sync modes: use defaults unless you understand the risk to local files.

---

## Assets for maintainers

- **Web bundle:** bump `resource_version` and `bundle_url` in `docs/web-bundle/manifest.json` on `main` when you ship a new console zip.
- **Next APK:** bump `versionCode` / `versionName` in `app/build.gradle.kts`, tag the repo, attach the new APK to a new GitHub Release.

---

## Links

- **Repository:** [ophirf15/Y1-Sync-App](https://github.com/ophirf15/Y1-Sync-App)
- **Releases:** [github.com/ophirf15/Y1-Sync-App/releases](https://github.com/ophirf15/Y1-Sync-App/releases)
