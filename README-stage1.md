# Innioasis Y1 Stage 1 Runtime

This repository contains the Stage 1 runtime shell for the Innioasis Y1 NAS Sync + Library Manager.

## Stage 1 Includes
- Minimal on-device control screen for server, sync, and update actions.
- Embedded local HTTP server (`NanoHTTPD`) on port `8080`.
- Bundled fallback web console in `app/src/main/assets/web/bundled`.
- SQLite schema and repositories for profiles, sync state, updates, media index, playlists, and logs.
- SMB-first sync engine skeleton with WebDAV scaffold.
- AlarmManager-based scheduling with boot-time re-registration.
- Web bundle update manager scaffold with manual manifest URL and bundled rollback.

## Build
- Android `minSdk=17` (Android 4.2.2)
- Java 8 source/target compatibility

## Runtime Notes
- Reliability-first defaults: no delete sync mode, fallback bundled assets always available.
- Designed so browser web assets can be replaced independently of APK updates.
