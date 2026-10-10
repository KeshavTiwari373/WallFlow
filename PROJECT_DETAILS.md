# WallFlow — Complete Project Reference

> **⚠️ Historical snapshot (July 2026, pre-pivot).** This document describes the project *before*
> the "wallpaper studio" pivot (editor, filters, AI features, share target, QS tile) and before the
> test suite was added. For the current state, see [README.md](README.md).

> A single-document, top-to-bottom description of the WallFlow Android project: what it is, every
> technology and exact version used, the full architecture, every source file and its job, the data
> model, the rotation system, permissions, the build problems we hit and how they were fixed, and the
> current known issues. Intended so a reviewer can find any problem without opening the IDE.

---

## 1. What the app is

WallFlow is a **native Android wallpaper manager and auto-rotator**. The user imports images from
their gallery, optionally tags them, and the app can:

- Apply any wallpaper manually to the **home screen**, **lock screen**, or **both**.
- **Auto-rotate** wallpapers automatically, in one of three modes:
  - **Off** — manual only.
  - **Timed interval** — changes on a schedule (15 min up to daily) via WorkManager.
  - **Every screen ON** — changes the lock screen each time the device wakes, via a foreground service.
- Pick the next wallpaper using a **least-recently-used (LRU)** rule so nothing repeats until the
  whole collection has cycled.
- Work **fully offline** — all images and data are stored locally on the device.

It is built as a portfolio project to demonstrate modern Android architecture.

---

## 2. Exact technology stack and versions

### Build toolchain (the part that caused the build errors)
| Tool | Version | Notes |
|---|---|---|
| Android Gradle Plugin (AGP) | **8.13.2** | Stable AGP 8.x line. Minimum the installed Android Studio allows. |
| Gradle | **8.13** | Required by AGP 8.13.2. |
| Kotlin | **2.1.0** | Language + compiler. |
| KSP (Kotlin Symbol Processing) | **2.1.0-1.0.29** | Runs annotation processors (Room, Hilt). |
| JDK / Java | **17** | `sourceCompatibility`/`targetCompatibility`/`jvmTarget` all 17. |

### Android SDK levels
| Setting | Value | Meaning |
|---|---|---|
| `minSdk` | **26** | Android 8.0 — oldest supported device. |
| `targetSdk` | **35** | Android 15 — behavior target. |
| `compileSdk` | **35** | Compiled against Android 15 APIs. |
| `versionCode` / `versionName` | 1 / "1.0" | First version. |
| `applicationId` / `namespace` | `com.keshav.wallflow` | Package. |

### Libraries (all via Gradle version catalog `gradle/libs.versions.toml`)
| Library | Version | Purpose |
|---|---|---|
| Jetpack Compose BOM | 2025.01.00 | UI toolkit version alignment. |
| Compose UI / Graphics / Tooling / Material3 | (from BOM) | Declarative UI + Material Design 3. |
| Material Icons Extended | (from BOM) | Icon set. |
| Navigation Compose | 2.8.5 | In-app screen navigation. |
| Lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | 2.8.7 | ViewModels + lifecycle-aware state collection. |
| Activity Compose | 1.9.3 | Compose entry point + Activity Result APIs. |
| Room (runtime, ktx, compiler) | **2.6.1** | Local SQLite database with type-safe DAOs. |
| WorkManager (work-runtime-ktx) | 2.10.0 | Background periodic interval rotation. |
| Hilt (hilt-android, hilt-compiler) | **2.54** | Dependency injection. |
| androidx.hilt (hilt-navigation-compose) | 1.2.0 | Hilt + Compose Navigation bridge. |
| androidx.hilt (hilt-work, hilt-compiler) | 1.2.0 | Hilt-injected WorkManager workers. |
| Coil (coil-compose) | 2.7.0 | Async image loading into Compose. |
| DataStore (datastore-preferences) | 1.1.1 | Persisted key-value settings (rotation config). |

### Critical build setting
In `gradle.properties`:
```
ksp.useKSP2=false
```
This forces KSP to use the **KSP1** engine. Room 2.6.1's annotation processor is not compatible with
KSP2 and fails with `unexpected jvm signature V`. KSP 2.1.0-1.0.29 still ships KSP1, so this flag is
valid and makes the build work. (See Section 8 for the full story.)

---

## 3. Architecture overview

The app uses **MVVM (Model–View–ViewModel)** with a **single source of truth** repository. Data flows
in one direction and every component talks to one central `WallpaperRepository`.

```
 ┌─────────────────────────────────────────────────────────────┐
 │  UI layer (Jetpack Compose screens + ViewModels)             │
 │  GalleryScreen / DetailScreen / RotationSettingsScreen       │
 └───────────────┬─────────────────────────────────────────────┘
                 │ (ViewModels expose StateFlow, call repo functions)
                 ▼
 ┌─────────────────────────────────────────────────────────────┐
 │  WallpaperRepository  ◄── the ONE funnel for all data/actions│
 └───┬───────────────┬──────────────────┬─────────────────┬─────┘
     │               │                  │                 │
     ▼               ▼                  ▼                 ▼
  Room DB     ImageStorageManager  WallpaperApplier   RotationPreferences
 (metadata)   (files on disk)      (WallpaperManager) (DataStore settings)

 ┌─────────────────────────────────────────────────────────────┐
 │  RotationEngine  (chooses + drives exactly ONE strategy)     │
 │     ├── WorkManagerStrategy → WallpaperRotationWorker        │
 │     └── ScreenOnStrategy    → ScreenOnWallpaperService       │
 │  Both call repository.rotateToNext(...) — shared select+apply│
 └─────────────────────────────────────────────────────────────┘
```

**Key design properties:**
1. **Single source of truth** — UI and rotation strategies never touch the database or files directly;
   they only call `WallpaperRepository`.
2. **Mode exclusivity** — `RotationEngine` always stops the other strategy before starting one, so
   WorkManager and the screen-on service can never both be changing the wallpaper at once.
3. **Shared selection/apply path** — both rotation modes call the same `repository.rotateToNext()`, so
   the LRU logic and bitmap-applying exist in exactly one place.
4. **Strategy abstraction** — the riskier foreground-service code is isolated behind a
   `RotationStrategy` interface.

---

## 4. Folder structure and every source file

All code lives under `app/src/main/java/com/keshav/wallflow/`.

### Root
- **`MainActivity.kt`** — single Activity; sets the Compose content and theme, hosts the nav graph.
- **`WallFlowApp.kt`** — `Application` subclass. `@HiltAndroidApp` (enables DI app-wide). Implements
  `Configuration.Provider` to give WorkManager a Hilt-aware `WorkerFactory` (so workers can have
  dependencies injected). Creates the notification channel used by the foreground service.

### `di/` — Dependency Injection
- **`AppModule.kt`** — Hilt module. Provides the Room `AppDatabase` (singleton) and the three DAOs
  (`WallpaperDao`, `TagDao`, `GroupDao`). Everything else is constructor-injected via `@Inject`.

### `data/local/` — Room database (metadata only; images are on disk)
- **`AppDatabase.kt`** — Room `@Database`, version 1, `exportSchema = true`. Lists all 5 entities and
  exposes the 3 DAOs. DB file name: `wallflow.db`.
- **`WallpaperEntity.kt`** — table `wallpapers`. Columns: `id` (autogen PK), `uuid` (unique, durable
  identity for backup/sync), `filePath`, `thumbnailPath`, `dateAdded`, `usageCount`, `last_used_at`.
- **`TagEntity.kt`** — table for tags (id, name).
- **`WallpaperTagCrossRef.kt`** — many-to-many join between wallpapers and tags.
- **`GroupEntity.kt`** — table for groups (id, uuid, name). **Scaffolded; not used by any UI yet.**
- **`WallpaperGroupCrossRef.kt`** — many-to-many join between wallpapers and groups. **Unused.**
- **`WallpaperWithTags.kt`** — Room `@Relation` POJO: a wallpaper plus its list of tags (one query).
- **`WallpaperDao.kt`** — queries: observe all/by-tag (as Flow), get by id, count, **LRU pick**
  (`pickLeastRecentlyUsed` / `…ByTag`), `markUsed`, insert/delete, tag cross-ref insert/delete.
- **`TagDao.kt`** — observe all tags, find by name, insert (IGNORE on conflict).
- **`GroupDao.kt`** — group CRUD + cross-ref. **Unused by UI.**

### `data/storage/`
- **`ImageStorageManager.kt`** — all on-disk image handling:
  - `importImage(uri)` — copies the picked image bytes into `filesDir/wallpapers/<uuid>.jpg`, then
    generates a downscaled thumbnail in `filesDir/thumbs/<uuid>.jpg`. Returns null if unreadable.
  - `decodeForWallpaper(path, w, h)` — decodes a bitmap downsampled to roughly screen size (uses
    `inJustDecodeBounds` + `inSampleSize`) to avoid out-of-memory on huge images.
  - `deleteFiles(...)` — removes image + thumbnail on delete.
  - Thumbnails target 400px on the longest side, JPEG quality 80.

### `data/prefs/`
- **`RotationPreferences.kt`** — wraps Jetpack **DataStore**. Holds a `RotationConfig` (mode,
  intervalMinutes, target, filterTagId) as a Flow, plus setters. Constants: `MIN_INTERVAL_MINUTES = 15`,
  `DEFAULT_INTERVAL_MINUTES = 60`. `filterTagId` of `-1` means "no filter".

### `data/repository/`
- **`WallpaperRepository.kt`** — the single source of truth. Functions: observe wallpapers/tags/groups,
  `importImages`, `deleteWallpaper`, add/remove tag, `applyWallpaper` (manual apply + record usage),
  `rotateToNext` (LRU pick + apply + record usage — shared by both rotation modes), `count`, and group
  helpers (unused by UI).

### `wallpaper/`
- **`WallpaperTarget.kt`** — enum: `HOME`, `LOCK`, `BOTH`.
- **`RotationMode.kt`** — enum: `OFF`, `INTERVAL`, `SCREEN_ON`.
- **`WallpaperApplier.kt`** — thin wrapper over Android's `WallpaperManager`. Decodes a screen-sized
  bitmap and calls `setBitmap(...)` with the right flags (`FLAG_SYSTEM` / `FLAG_LOCK` / both). Recycles
  the bitmap after use. Reads true screen `widthPixels × heightPixels`.

### `rotation/`
- **`RotationStrategy.kt`** — interface with `start(config)` / `stop()`.
- **`RotationEngine.kt`** — the single entry point the UI uses. `apply(config)` reconciles which
  strategy runs based on the mode and guarantees only one is active. `applyCurrent()` re-applies the
  persisted mode (e.g. after reboot or settings change).
- **`WorkManagerStrategy.kt`** — INTERVAL mode. Schedules a unique periodic
  `WallpaperRotationWorker`. Uses `ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE` so a changed
  interval re-times immediately. Interval coerced to a minimum of 15 minutes.
- **`ScreenOnStrategy.kt`** — SCREEN_ON mode. Starts/stops the `ScreenOnWallpaperService` foreground
  service.

### `work/`
- **`WallpaperRotationWorker.kt`** — `@HiltWorker` `CoroutineWorker`. On each run: reads config, if
  the collection is empty returns success, otherwise calls `repository.rotateToNext(target, filterTag)`.
  Returns `retry()` if applying failed.

### `service/`
- **`ScreenOnWallpaperService.kt`** — foreground service for SCREEN_ON mode. Registers
  `ScreenOnReceiver` at runtime, shows an ongoing notification, and on each screen wake applies the
  next LRU wallpaper to the **lock screen**. Debounces (2s) because SCREEN_ON and USER_PRESENT fire
  close together. Uses a `Mutex` so rotations don't overlap. `START_STICKY` so the system restarts it.
- **`ScreenOnReceiver.kt`** — `BroadcastReceiver` for `ACTION_SCREEN_ON` / `ACTION_USER_PRESENT`.
  Registered dynamically (these actions can't be declared in the manifest).
- **`BootReceiver.kt`** — on `BOOT_COMPLETED`, restarts the SCREEN_ON service if that mode was active.
  (WorkManager interval work is persisted by the OS and resumes on its own.)

### `ui/`
- **`navigation/WallFlowNavHost.kt`** — Navigation graph. Routes: `gallery` (start), `detail/{id}`,
  `settings`.
- **`screen/gallery/GalleryScreen.kt`** + **`GalleryViewModel.kt`** — grid of wallpaper thumbnails,
  tag filter row, import button (Android Photo Picker — no storage permission), empty state.
- **`screen/detail/DetailScreen.kt`** + **`DetailViewModel.kt`** — large preview, usage metadata,
  add/remove tags, apply to home/lock/both, delete with confirmation.
- **`screen/settings/RotationSettingsScreen.kt`** + **`RotationSettingsViewModel.kt`** — pick rotation
  mode, interval, target, and tag filter. Requests notification permission on Android 13+ before
  enabling SCREEN_ON mode.
- **`component/`** — reusable Compose pieces: `WallpaperGridItem`, `TagChip` (filter + removable),
  `EmptyState`.
- **`theme/`** — `Color.kt`, `Theme.kt`, `Type.kt` — Material 3 theming (light/dark).

### `res/` — resources
- `values/strings.xml` (+ all user-facing text), `values/colors.xml`, `values/themes.xml`,
  `values-night/` (dark theme), `drawable/` (icons), `mipmap-anydpi-v26/` (adaptive launcher icon),
  `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`.

---

## 5. Data model (database)

```
wallpapers                       tags                  groups (unused UI)
----------                       ----                  ------
id (PK, auto)                    id (PK, auto)         id (PK, auto)
uuid (unique)                    name                  uuid
filePath                                               name
thumbnailPath
dateAdded             wallpaper_tag_cross_ref          wallpaper_group_cross_ref
usageCount            -----------------------          -------------------------
last_used_at          wallpaperId (FK)                 wallpaperId (FK)
                      tagId (FK)                       groupId (FK)
```

- A wallpaper has **many** tags; a tag has **many** wallpapers (via the cross-ref table).
- Image **bytes** are NOT in the database — only the file path. The actual `.jpg` lives in app-private
  internal storage (`filesDir/wallpapers/`), with a thumbnail in `filesDir/thumbs/`.
- `uuid` is the durable identity meant for future backup/restore/sync; `id` is internal only.

### The LRU (least-recently-used) selection — core algorithm
SQL ordering used to pick the next wallpaper:
```sql
ORDER BY (last_used_at IS NULL) DESC,  -- never-used wallpapers first
         last_used_at ASC,            -- then the one used longest ago
         usageCount ASC,              -- then the least-used
         RANDOM()                     -- random tie-break
LIMIT 1
```
This guarantees nothing repeats until the entire collection has been shown once. After applying,
`markUsed` bumps `usageCount` and sets `last_used_at = now`.

---

## 6. Rotation system in detail

**Three modes, set in Settings, persisted via DataStore:**

1. **OFF** — `RotationEngine` stops both strategies. Wallpaper changes only on manual apply.

2. **INTERVAL** — `WorkManagerStrategy` enqueues a unique periodic `WallpaperRotationWorker`
   (`CANCEL_AND_REENQUEUE` policy). The OS runs the worker roughly every N minutes (15/30/60/180/360/
   720/1440). Each run picks the next LRU wallpaper and applies it to the configured target
   (home/lock/both), honoring an optional tag filter. WorkManager periodic minimum is 15 min — matched
   by `MIN_INTERVAL_MINUTES`.

3. **SCREEN_ON** — `ScreenOnStrategy` starts `ScreenOnWallpaperService`, a foreground service holding
   an ongoing notification. It registers a runtime receiver for screen-on/user-present, debounces
   rapid double-fires (2s), and on each wake applies the next LRU wallpaper to the **lock screen only**.
   A `Mutex` prevents overlapping rotations. Restarts after reboot via `BootReceiver`.

**Why a foreground service for SCREEN_ON:** `ACTION_SCREEN_ON` cannot be registered in the manifest;
it must be registered at runtime by a live component. A foreground service keeps that component alive.
This uses the `specialUse` foreground service type, which requires a Play Store justification.

---

## 7. Permissions and manifest

Declared in `AndroidManifest.xml`:
- `SET_WALLPAPER` — to change wallpapers.
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — for the SCREEN_ON service.
- `POST_NOTIFICATIONS` — the ongoing service notification (Android 13+ asks at runtime).
- `RECEIVE_BOOT_COMPLETED` — to restart SCREEN_ON after reboot.

Components:
- `MainActivity` (launcher).
- `ScreenOnWallpaperService` (`foregroundServiceType="specialUse"`, with a required justification
  property string).
- `BootReceiver` (listens for `BOOT_COMPLETED`).
- WorkManager is initialized **on-demand** (the default initializer is removed via the
  `InitializationProvider` meta-data) so Hilt can supply the worker factory.

**Image import uses the Android Photo Picker** (`PickMultipleVisualMedia`), which needs **no storage
permission** at all.

---

## 8. Build problems we hit, and how they were resolved (IMPORTANT for your friend)

This is the part most likely to be the "problem" your friend is looking for. The **source code was
never the issue** — every failure was a **build-tool version mismatch**.

### Problem 1 — `unexpected jvm signature V` at `:app:kspDebugKotlin`
- **Cause:** KSP defaulted to its newer **KSP2** engine, but **Room 2.6.1's** annotation processor is
  not compatible with KSP2. The `V` is the JVM descriptor for `void`; the processor hit a signature it
  couldn't handle.
- **Not a code bug** — purely a tool incompatibility.

### Problem 2 — `KSP1 is no longer available` during configuration
- **Cause:** The project's toolchain had been bumped (via Android Studio's **AGP Upgrade Assistant**)
  to a bleeding-edge set: **AGP 9.2.1 / Kotlin 2.2.10 / KSP 2.3.2 / Gradle 9.4.1**. KSP 2.3.x
  **removed the KSP1 engine entirely**, so the `ksp.useKSP2=false` fallback was rejected. On top of
  that, Room 2.6.1 and Hilt 2.54 were too old for that toolchain.

### The decision: roll back to a stable, known-good toolchain
Rather than chase the bleeding edge (and keep upgrading every library to match), we reverted the
toolchain to the stable AGP 8.x line:
- AGP 9.2.1 → **8.13.2** (8.13.2 is the minimum the installed Android Studio allows; still stable 8.x).
- Gradle 9.4.1 → **8.13**.
- Kotlin 2.2.10 → **2.1.0**.
- KSP 2.3.2 → **2.1.0-1.0.29** (this version still ships KSP1).
- Room and Hilt kept at **2.6.1** and **2.54**.
- Removed the 11 AGP-9 migration flags the Upgrade Assistant added to `gradle.properties` (they were
  the deprecation warnings).
- Added `ksp.useKSP2=false` so Room 2.6.1 runs on KSP1, which KSP 2.1.0-1.0.29 still provides.

### Rule going forward
**Do not run the AGP Upgrade Assistant** on this project, and dismiss any "AGP upgrade recommended"
prompt. If a toolchain bump is ever wanted, the **libraries must be upgraded together with it**
(e.g. Room → 2.7.2+, Hilt → 2.57+) — never the toolchain alone.

---

## 9. Code fixes already applied (quality/correctness)

Four issues from a code review were corrected:

1. **WorkManager interval timing** — changed the enqueue policy from `UPDATE` to
   `CANCEL_AND_REENQUEUE` so changing the interval actually re-times the schedule immediately. (`UPDATE`
   could keep the original next-run instant.)
2. **Wallpaper decode sizing** — `WallpaperApplier` now decodes using the **real screen width × height**
   instead of a square (`longest × longest`). The square target could skip downsampling on tall source
   images and risk an out-of-memory crash on very large photos.
3. **Hardcoded strings** — moved the literal `"Failed to apply"` toast and the `"Back"` content
   descriptions into `strings.xml` (`apply_failed_toast`, `back`) for consistency/i18n.
4. **README/UI mismatch** — the README claimed a "custom" interval that the UI doesn't offer; reworded
   to "preset steps from 15 min to daily".

---

## 10. Current known issues / limitations (candidates for your friend to flag)

These are **not build-breaking**; they are quality/scope observations:

1. **No tests.** `testInstrumentationRunner` is declared but there is no `src/test` or `src/androidTest`.
   The LRU query and `RotationEngine` reconciliation would be the highest-value targets.
2. **`groups/` is dead code.** `GroupEntity`, `GroupDao`, `WallpaperGroupCrossRef`, and the repository
   group methods exist but no screen uses them. Intentionally scaffolded for future use.
3. **Room has no migrations yet.** Version is 1. The first schema change will require a migration
   (schema export is already enabled, which is good).
4. **README toolchain section is outdated** — it still lists AGP 8.9.1 / Gradle 8.11.1, but the project
   is actually on AGP 8.13.2 / Gradle 8.13 (the installed Studio's minimum).
5. **SCREEN_ON mode reliability** depends on the OS not killing the foreground service; aggressive
   battery optimizers on some OEMs (Xiaomi, Samsung, etc.) can stop it. This is an inherent Android
   limitation, not a code bug.
6. **Square-vs-aspect cropping** — wallpapers are applied with the system allowed to crop; exact
   framing depends on the device.

---

## 11. How to build and run (for reference)

1. Open the project folder in **Android Studio** (the folder containing `settings.gradle.kts`).
2. Let **Gradle Sync** finish (it creates `local.properties` with the SDK path, downloads Gradle 8.13,
   and regenerates the wrapper jar — the wrapper jar is intentionally not committed).
3. Pick a device/emulator (Android 8.0 / API 26 or higher), then **Run ▶**, or
   **Build → Build APK(s)** for an installable `app-debug.apk`.
4. **Do not** accept any AGP upgrade prompt.

---

## 12. One-paragraph summary for your friend

WallFlow is a clean, modern MVVM Android app (Kotlin + Jetpack Compose + Material 3) with a single
`WallpaperRepository` as the source of truth, Room for metadata, internal storage for image files,
DataStore for settings, Hilt for DI, WorkManager for interval rotation, and a foreground service for
screen-on rotation, with the two rotation modes unified behind a `RotationEngine` strategy that
guarantees only one runs at a time and both share the same least-recently-used selection. The code is
solid; **the only real trouble has been build-tool version mismatches** — specifically Room 2.6.1 not
working under the newer KSP2 engine — which is now resolved by pinning to a stable AGP 8.x toolchain
(AGP 8.13.2 / Gradle 8.13 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29) and setting `ksp.useKSP2=false`. The main
remaining gaps are the absence of tests and some scaffolded-but-unused "groups" code.
