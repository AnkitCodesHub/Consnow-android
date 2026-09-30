# Consnow Android

Kotlin + Jetpack Compose step-sharing app.

## Architecture

```
app/
  core/
    database/     — Room DB, DAOs, entities
    network/      — Retrofit API service, DTOs, auth interceptor
    datastore/    — DataStore token manager
    common/       — Result wrapper
  features/
    auth/         — Login/register flow
    connections/  — Peer connection via 6-char code
    steps/        — Step counting, local storage, sync, peer viewing
  navigation/     — Compose NavHost
  ui/theme/       — Material 3 theme
```

## Data Flow

```
TYPE_STEP_COUNTER sensor
  → StepCounterService (foreground)
  → Room (StepRecordEntity, upsert on user_id+date)
  → UI observes Flow from Room
  → SyncWorker (periodic 15min via WorkManager)
  → Backend PUT /steps/sync (total count, idempotent)
```

## Idempotency Guarantees

- Room uses `ON CONFLICT REPLACE` on the (user_id, date) unique index
- Step count is monotonic per day — only increases are stored
- Sync sends TOTAL step count, not deltas — safe to retry
- SyncWorker is enqueued with `ExistingPeriodicWorkPolicy.KEEP` — no duplicates

## Setup

1. Open in Android Studio
2. Set `API_BASE_URL` in `app/build.gradle.kts` to your backend
3. Build and run on a device with a step counter sensor

## Permissions

- `ACTIVITY_RECOGNITION` — required for TYPE_STEP_COUNTER
- `INTERNET` / `ACCESS_NETWORK_STATE` — API communication
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_HEALTH` — background step counting
- `RECEIVE_BOOT_COMPLETED` — restart tracking after device reboot
