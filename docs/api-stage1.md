# Stage 1 Local API

Base URL: `http://<local-ip>:8080`

## Implemented in Stage 1 Skeleton
- `GET /api/status`
- `GET /api/device-info`
- `POST /api/sync/now`
- `POST /api/updates/check`
- `POST /api/updates/revert-bundled`
- `GET /api/logs`

## Planned Endpoint Families (scaffold target)
- Profiles: `/api/profiles`
- Sync status/runs: `/api/sync/status`, `/api/sync/runs`
- Library: `/api/library/*`
- Playlists: `/api/playlists/*`
- Maintenance: `/api/maintenance/*`
