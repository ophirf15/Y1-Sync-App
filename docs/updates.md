# Web bundle and APK updates

- **Manifest (default URL):** `docs/web-bundle/manifest.json` on the `main` branch of [Y1-Sync-App](https://github.com/ophirf15/Y1-Sync-App), served as raw content. The app defaults to that URL for “Check updates” / “Download/Apply”.
- **Bump the bundle:** increase `resource_version` in `manifest.json` and set `bundle_url` to a downloadable zip (recommended: a GitHub Release asset, e.g. `https://github.com/ophirf15/Y1-Sync-App/releases/download/<tag>/web-bundle.zip`). Optionally set `checksum_sha256` for integrity.
- **APK:** publish `.apk` files on [GitHub Releases](https://github.com/ophirf15/Y1-Sync-App/releases). The in-app status page compares the latest release tag to the installed app version and links to the release / APK download.
