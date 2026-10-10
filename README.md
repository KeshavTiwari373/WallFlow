# WallFlow

A native Android **wallpaper studio**: import any image, perfect it for your exact screen — crop,
filters, AI enhance, depth effect — then apply it or let your collection auto-rotate. Built as a
portfolio project demonstrating modern Android architecture.

## Features

**Import**
- Unlimited wallpapers via the Android Photo Picker (no storage permission needed)
- Share an image from any app straight into the WallFlow editor
- Images are copied into app-private storage, so they survive deletion of the source

**Edit (non-destructive — originals are never modified)**
- Pinch/pan crop locked to your device's exact screen resolution, with mock home-screen and
  lock-screen overlays so you preview the real result
- **Auto-crop** — ML Kit face detection centers the crop on the subject
- **Blur fill** — images that don't cover the screen get a blurred backdrop instead of bars
- One-tap filters with an intensity slider: AMOLED, Vibrant, Soft, Duotone, Grain
- **Enhance HD** — AI super-resolution via Real-ESRGAN (general-x4v3, TFLite, bundled in the APK,
  fully offline): denoises and reconstructs low-res images at 4×. Sources over 1024px (or any
  inference failure) fall back to a Lanczos + unsharp pipeline behind the same `Upscaler` interface
- **Dim lock screen** — auto-generates a darker, blurred lock variant
- **Depth effect** — ML Kit subject segmentation pops the subject over the lock-screen clock band
- Edits are stored as parameters (`EditParams` JSON), so every edit stays re-editable

**Organize & apply**
- Grid gallery with thumbnails, tags (many-to-many) and tag filtering
- Apply to home, lock, or both — each surface gets its own processed version
- **Auto-rotation:** timed interval (WorkManager, 15 min–daily), every screen ON (foreground
  service, lock screen), or off — with target + tag filter
- **Least-recently-used** selection: nothing repeats until the whole collection has cycled
- Quick Settings tile: rotate to the next wallpaper from anywhere

**Private by design** — no network permission, no analytics, all data on-device. (The two ML Kit
features use Google Play services for model delivery; everything else is fully offline.)

## Architecture

- **Kotlin + Jetpack Compose + Material 3**, single-Activity, Navigation Compose
- **MVVM** with a single-source-of-truth `WallpaperRepository` — the UI, rotation strategies, share
  target, and QS tile all go through it
- **Room** (schema v3, versioned migrations + tests) for metadata; images live in `filesDir`
- **`ImageProcessor`** — one pure pipeline (`source + EditParams + screen size → Bitmap`) shared by
  the editor preview, save, and rotation paths; region decoding keeps memory bounded
- **WorkManager** for interval rotation; **foreground service** (`specialUse`) for screen-on rotation;
  rotation is behind a strategy abstraction (`RotationEngine`) that guarantees exactly one mode runs
- **Hilt** for DI (incl. `HiltWorkerFactory`), **Coil** for image loading, **DataStore** for settings,
  **kotlinx-serialization** for edit parameters

```
ui/ (Compose screens + ViewModels) ──► data/repository ──► data/local (Room v3)
        editor/ (EditorScreen)              │                data/storage (originals · processed · upscaled)
        onboarding/ · share target          │                data/prefs (DataStore)
                                            ▼
        editor/ImageProcessor · SmartCrop · DepthEffect · upscale/Upscaler
                                            ▲
rotation/RotationEngine ────────────────────┘ (WorkManagerStrategy | ScreenOnStrategy)
work/WallpaperRotationWorker · service/ScreenOnWallpaperService · service/RotateTileService
```

## Building

- minSdk 26 (Android 8.0), targetSdk 35, compileSdk 35, JDK 17, AGP 8.13.2, Gradle 8.14.4,
  Kotlin 2.1.0, KSP 2.1.0-1.0.29
- The toolchain is pinned to this stable set. **Do not run the AGP Upgrade Assistant** — Room 2.6.1
  requires `ksp.useKSP2=false` (in `gradle.properties`), and a newer KSP removes the KSP1 engine.
- Open the project in **Android Studio** and press Run, or from the command line:
  `./gradlew assembleDebug`
- Release builds have R8 minify + resource shrinking enabled. Signing: copy
  `keystore.properties.template` to `keystore.properties` (gitignored), point it at your upload
  keystore, and `bundleRelease` produces a signed AAB. Without the file, release builds are
  unsigned (fine for CI).

## Testing

- **Unit** (`./gradlew testDebugUnitTest`): `RotationEngineTest` — mode exclusivity (exactly one
  rotation strategy active).
- **Instrumented** (`./gradlew connectedDebugAndroidTest`, needs a device/emulator):
  `WallpaperDaoLruTest` (LRU selection), `Migration1To2Test` / `Migration2To3Test` (Room
  migrations), `ImageProcessorTest` (crop/scale/filter pipeline), `SmartCropTest` (graceful
  fallback).
- Room schemas are exported to `app/schemas/` for migration history.

## Play Store notes

- The "Every screen ON" mode uses a `specialUse` foreground service. The Play Console requires a
  short justification for `FOREGROUND_SERVICE_SPECIAL_USE`:
  "Continuously updates the lock-screen wallpaper on device wake at the user's request."
- Data safety form: no data collected or shared; all processing on-device.
- Privacy policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md) — host it at a public URL (e.g. GitHub
  Pages) and link it in the Play Console listing.
