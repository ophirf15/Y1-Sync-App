# Stage 1 Architecture

## Runtime Layers
- `app`: minimal Android UI and application entry points.
- `runtime`: orchestration layer (`CoreRuntimeController`).
- `server`: embedded HTTP server, asset serving, API routing.
- `db`: SQLite schema and repositories.
- `sync`: sync orchestration and SMB protocol adapter.
- `scheduler`: AlarmManager scheduling and boot receiver.
- `updates`: downloadable web-bundle lifecycle and fallback.
- `library` / `storage`: media index and playlist writing.

## Asset Serving Strategy
1. Try active downloaded bundle.
2. Fall back to APK bundled assets (`assets/web/bundled`).

## Reliability Constraints
- Small memory footprint and simple class boundaries.
- No heavy DI frameworks.
- Scheduled background work uses old-compatible AlarmManager flow.
