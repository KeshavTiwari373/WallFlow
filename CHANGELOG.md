# Changelog

> Chronological log of every meaningful change to WallFlow. **Add an entry for every change set.**
> Newest first. Dates are absolute (YYYY-MM-DD).

## 2026-07-18 — Real "Enhance HD": Real-ESRGAN on-device super-resolution

Driven by live on-device QA (SM-A236E): a 14 KB / ~500px web image enhanced with the classical
pipeline came back bigger but still blurry — classical resampling cannot invent missing detail.
Verified by pulling the app's own files off the device and inspecting all pipeline stages.

- **`RealEsrganUpscaler`**: `realesr-general-x4v3` (float TFLite, 4.9 MB, BSD-3-Clause, Qualcomm
  AI Hub export) bundled in `assets/`, runs fully offline via TensorFlow Lite (new dep,
  `noCompress "tflite"` so the model memory-maps). Fixed 128→512 tiles; larger images processed as
  an overlapping tile grid (8px context pad, edge-replicated borders, central-region stitching).
  Sources >1024px on the longest side — or any inference failure — fall back to `LanczosUpscaler`.
  Wired in `UpscaleModule`; everything else untouched behind the `Upscaler` interface. Closes
  plan deviation D1. APK grows ~5 MB; a 500px image enhances in ~30 s on a mid-range phone.
- **`LanczosUpscaler` rewritten honestly**: was `createScaledBitmap` (bilinear) + a fixed-radius
  unsharp mask that vanished after rendering to screen size; now true separable Lanczos-3
  resampling (≤1600px sources) with scale-aware sharpening radius.
- **Enhance feedback**: `applyUpscale` returns an `UpscaleResult` (from/to pixel sizes); the
  editor toast now reports "Enhanced: 736×1472 → 2048×4096", "Already high resolution —
  sharpening applied", or "Could not enhance" instead of an uninformative "Enhanced".
- **`RealEsrganUpscalerTest`** (instrumented): runs the bundled model end-to-end on a real
  low-res photo, asserts 4× output, and writes the result to `filesDir` for host-side visual
  inspection. Verified green on a physical SM-A236E (29 s) with visibly denoised, clean-edged
  output.

## 2026-07-17 — Launch-readiness pass: field-failure fixes + full verification

**Correctness fixes (found by a line-by-line pipeline review before launch):**
- **EXIF orientation** (`ImageStorageManager.normalizeOrientation`): camera JPEGs carry their
  rotation in EXIF, which every `BitmapFactory`/`BitmapRegionDecoder` path ignored — portrait
  photos processed sideways (while Coil-based views honored EXIF, so the Detail screen even
  disagreed with the output). Import now bakes rotation/flip into the pixels once (long side
  capped at 4096px to bound memory; images without EXIF rotation stay byte-identical). Added
  `androidx.exifinterface:1.3.7`.
- **Undecodable imports rejected**: corrupt files and HEIC on API 26–27 imported "successfully"
  as forever-grey tiles; import now verifies decodability and fails cleanly instead.
- **Share duplicate-import**: the `ACTION_SEND` intent survives activity recreation, so rotating
  the screen (or toggling dark mode) after sharing re-imported the image. `onCreate` now only
  handles the share intent when `savedInstanceState == null`.
- **Depth-effect silent no-op** (closes plan deviation U3): when segmentation finds no subject,
  the editor now says so in a toast instead of silently saving without the effect.
  `WallpaperRepository.saveEdit` returns whether a requested depth composite was omitted.
- **OOM guards**: `saveEdit` (via the editor ViewModel) and `applyUpscale` now fail soft with a
  toast (`save_failed` / `enhance_failed`) instead of crashing on low-RAM devices.
- **Portrait lock on `MainActivity`**: processed sizes come from live display metrics, so a
  landscape editing session would have baked a landscape-sized wallpaper.

**Launch infrastructure:**
- Release signing via gitignored `keystore.properties` (see `keystore.properties.template`);
  builds stay unsigned when absent so CI works. `.gitignore` covers keystores.
- Gradle wrapper jar regenerated (Gradle 8.14.4) and committed — clean-clone CLI builds work.
- **Backup rules corrected** (predated the studio pivot): `processed/` and `upscaled/` were
  missing from the excludes, and the Room DB was cloud-backed-up *without* its images — a restore
  would have produced a gallery of dead rows. Cloud backup now carries settings only; full
  library moves via device-to-device transfer.
- `PRIVACY_POLICY.md` (Play requirement) and `LAUNCH_PLAN.md` (end-to-end launch checklist)
  added. README/PLAN_LOG synced (actual Gradle is 8.14.4, not 8.13).

**Verification (all green today):**
- `assembleDebug`, `testDebugUnitTest`, `assembleRelease` + `bundleRelease` (R8 + lintVital;
  unsigned APK 2.6 MB).
- **First-ever `connectedDebugAndroidTest` run** (API 36.1 emulator): required adding the missing
  `androidx.test:runner` dependency — the suite crashed with
  `ClassNotFoundException: AndroidJUnitRunner` (`ext:junit` does not pull the runner
  transitively). All 5 classes pass: `WallpaperDaoLruTest`, `Migration1To2Test`,
  `Migration2To3Test`, `ImageProcessorTest`, `SmartCropTest`.

## 2026-07-02 — Production-readiness pass (first green builds)

**Build fixes (found by actually compiling):**
- `OnboardingScreen.kt`: removed invalid `androidx.compose.foundation.layout.weight` import — it
  resolved to an internal compiler symbol and broke `compileDebugKotlin`. (`Modifier.weight()` needs
  no import; it resolves through the `ColumnScope` receiver.)
- `values/colors.xml`: declared `wallflow_background_dark` in base values. It existed only in
  `values-night/`, which `lintVitalRelease` correctly flags as a potential crash
  (MissingDefaultResource) — this blocked the release build.

**Verification:**
- First-ever green `assembleDebug` (debug APK produced).
- `testDebugUnitTest` green.
- `assembleRelease` green — R8 minify + resource shrinking + lintVital all pass. APK is unsigned
  (signing config is a pre-publish task).
- Room schemas `1.json` + `3.json` exported to `app/schemas/`.
- Instrumented tests **not yet run** (need device/emulator).

**Repo & docs:**
- `git init` (branch `main`), staged all sources; `.kotlin/` added to `.gitignore`.
- README rewritten to describe the actual product (studio pivot: editor, filters, AI, share
  target, QS tile) and current testing/build/Play-store facts.
- `PROJECT_DETAILS.md` marked as a historical (pre-pivot) snapshot.
- Added `PLAN_LOG.md` (plan deviations) and this `CHANGELOG.md`.

## 2026-07-02 — Static-analysis fixes (pre-build deep review)

- **ML Kit subject segmentation:** fixed a nonexistent dependency coordinate
  (`com.google.mlkit:subject-segmentation` → `com.google.android.gms:play-services-mlkit-subject-segmentation`).
- **ML Kit family conflict:** face detection moved from bundled (`com.google.mlkit:face-detection:16.1.7`)
  to Play-services (`play-services-mlkit-face-detection:17.1.0`) — mixing the two families causes
  duplicate-class build failures. Consequence: the two ML features rely on Play services model
  delivery (documented deviation from the "models in APK" plan constraint, which was unsatisfiable).
- **Type error:** `EditorScreen.CropViewport` passed an `ImageBitmap` where `clampOffset` expects a
  `Size`.
- **Resource bug:** unescaped double quotes in `battery_opt_desc` (would fail aapt2); rephrased.
- **Shadowing:** local `val blur` in `LanczosUpscaler.unsharpMask` shadowed the `blur()` function;
  renamed to `blurPixels`.

## 2026-07-02 — Phase 4: Flow & distribution polish

- Share target: `ACTION_SEND image/*` intent filter on `MainActivity`; shared images are imported
  and land directly in the editor (`MainViewModel` + `pendingEditorId` navigation).
- Quick Settings tile (`RotateTileService`): tap = rotate to next wallpaper via the repository.
- Onboarding: 3-page `HorizontalPager` gated by a DataStore flag.
- Settings: battery-optimization exemption link
  (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) with explanation for SCREEN_ON reliability.

## 2026-07-02 — Phase 3: AI features + schema v3

- Room v3: `upscaledPath` column; `MIGRATION_2_3`; self-contained `Migration2To3Test`.
- Smart crop (`SmartCrop`): ML Kit face detection → subject-centered, screen-aspect crop;
  center-crop fallback; never throws. Auto-runs on first edit; "Auto" button re-runs.
- Enhance HD: `Upscaler` interface + `LanczosUpscaler` (upscale + unsharp mask, 4096px cap);
  Hilt `UpscaleModule` is the documented drop-in point for a Real-ESRGAN TFLite implementation.
  Upscaled copy becomes the processing source for subsequent edits.
- Depth effect (`DepthEffect`): ML Kit subject segmentation composites the subject over a darkened,
  draggable clock band on the lock variant; graceful no-op when no subject is found.
- `SmartCropTest` (graceful fallback, instrumented).

## 2026-07-02 — Phase 2: Filters & per-target variants

- Filter chain in `ImageProcessor` (ColorMatrix/Canvas, no new deps): AMOLED, Vibrant, Soft,
  Duotone (purple→cyan), Grain+Vignette; intensity lerp (0 = original).
- "Dim lock screen": separate blurred+dimmed lock file; apply path sets different images per
  surface when they differ.
- Editor: live filtered preview, filter chips, intensity slider, dim toggle.
- Material You hint via `androidx.palette` on save.
- `ImageProcessorTest`: AMOLED mean-luminance assertion added.

## 2026-07-02 — Phase 1: Fit & Frame editor (MVP core)

- `editor/` package: `EditParams` (normalized crop, serialized to Room as JSON — non-destructive,
  re-editable), `ImageProcessor` (region-decode crop → exact screen size → optional blur-extend),
  dependency-free `Blur.kt`, `EditorScreen`/`EditorViewModel` (pinch/pan crop locked to device
  aspect, mock home/lock overlays, blur-fill toggle).
- Storage: `processed/<uuid>_home|lock.jpg`; deletes cascade.
- `WallpaperApplier` reworked to per-target paths (`processedHomePath`/`processedLockPath`
  fallback to original); rotation automatically applies processed versions.
- Detail screen: Edit action + "Edited" badge. Route `editor/{id}`.
- `ImageProcessorTest` (exact output size, crop-region correctness, instrumented).

## 2026-07-02 — Phase 0: Foundation cleanup & schema v2

- Deleted unused scaffolded groups feature (`GroupEntity`, `GroupDao`, `WallpaperGroupCrossRef`,
  repository/DI references).
- Room v2: `processedHomePath`, `processedLockPath`, `editParamsJson` columns; `MIGRATION_1_2`
  (also drops group tables); self-contained `Migration1To2Test`.
- kotlinx-serialization plugin + json runtime added.
- Tests: `WallpaperDaoLruTest` (LRU pick order, markUsed, tag filter), `RotationEngineTest`
  (mode exclusivity) — added just before Phase 0 as part of the review response.

## 2026-07-02 — Pre-pivot: review fixes & toolchain stabilization

- Code review fixes: WorkManager `UPDATE` → `CANCEL_AND_REENQUEUE` (interval changes re-time);
  wallpaper decode uses real screen width×height instead of a square (OOM risk on tall sources);
  hardcoded "Failed to apply"/"Back" strings moved to resources; README interval claim corrected.
- Toolchain crisis and resolution: an AGP Upgrade Assistant run had moved the project to
  AGP 9.2.1 / Kotlin 2.2.10 / KSP 2.3.2, where KSP1 no longer exists and Room 2.6.1 cannot build
  (`unexpected jvm signature V`, then `KSP1 is no longer available`). Rolled back to the pinned
  stable set — AGP 8.13.2 (Studio's minimum) / Gradle 8.13 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 —
  and set `ksp.useKSP2=false`. Rule: never run the Upgrade Assistant on this project.
