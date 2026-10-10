# Plan Log

> Tracks how the implementation deviated from the plan (`WALLFLOW_STUDIO_SPEC.md`), what was wrong
> in the plan itself, and what remains open. **Update this file whenever the plan or a deviation
> changes.** Newest entries at the top of each section.

---

## 1. Errors found in the original plan (spec)

These were mistakes in the spec itself, discovered during implementation/build:

| # | Spec claim | Reality | Resolution |
|---|---|---|---|
| P1 | §2: dependency `com.google.mlkit:subject-segmentation:16.0.0-beta1` | That artifact **does not exist**. Subject segmentation is only published as `com.google.android.gms:play-services-mlkit-subject-segmentation` | Corrected the coordinate in the version catalog |
| P2 | §0 constraint 5: "100% offline… ML models ship in the APK. Exception: none." | **Impossible** for subject segmentation — it has no bundled variant; models are delivered via Google Play services. The spec's own feature 3c (depth effect) contradicts its constraint 5 | Both ML features moved to the Play-services family. Everything else stays fully offline. Documented in README ("Private by design" paragraph) |
| P3 | §2: bundled `com.google.mlkit:face-detection:16.1.7` alongside segmentation | Mixing the bundled (`com.google.mlkit`) and Play-services (`com.google.android.gms`) ML Kit families causes **duplicate-class build failures** | Face detection switched to `play-services-mlkit-face-detection:17.1.0` (same import packages, no code change) |
| P4 | §3: `Migration1To2Test` using Room's `MigrationTestHelper` | `MigrationTestHelper` requires a committed v1 schema JSON, which did not exist when Phase 0 was written (v1 had never had a successful build at that point) | Migration tests written self-contained with raw SQL — they test the identical `Migration` objects Room runs. (A `1.json` did later surface from an earlier Studio build and is committed, so helper-based tests are possible from now on) |

## 2. Sanctioned deviations (allowed by the spec's own fallback clauses)

| # | Spec | Implementation | Why |
|---|---|---|---|
| D1 | §3b Real-ESRGAN TFLite upscaler | `Upscaler` interface + `LanczosUpscaler` (upscale + unsharp mask), no TFLite deps | Spec §2 explicitly allows this when no model file is provided; the TFLite implementation is a drop-in behind the same interface (`UpscaleModule`) |
| D2 | §4 `zoomable` library for pan/zoom | Built-in `detectTransformGestures` | Spec offers "or custom detectTransformGestures"; avoids a third-party dependency |
| D3 | §2 RenderScript-replacement Toolkit for blur on API 26–30 | Own dependency-free separable box blur (`editor/Blur.kt`) used on all API levels | Spec allows "implement a fast stack blur in Kotlin"; one code path instead of two |
| D4 | §3a smart crop via subject segmentation, face fallback | Face detection primary, center fallback | Lower API risk (face detection is a stable API; segmentation is beta); spec lists face detection as the sanctioned fallback |

## 3. Unsanctioned deviations (engineering judgment — flagged when made)

| # | Spec | Implementation | Why |
|---|---|---|---|
| U1 | §5 filter strip with live mini-preview thumbnails | Filter chips + the main preview renders the filter live | Same information (you see the filter applied), avoids rendering N filtered thumbnails on every editor open. Upgradeable later |
| U2 | §6 3b "Enhance HD" before/after slider | Direct apply with progress + result toast | Slider is polish on a working feature; deferred |
| U3 | §6 3c `depth_no_subject` toast | ~~Silent graceful degradation~~ **Closed 2026-07-17**: toast implemented as the spec wanted (`depth_no_subject` string; `saveEdit` reports omission) | A silent no-op read as "broken button" — reversed the deviation before launch |
| U4 | §5 Material You hint as snackbar | Toast | No SnackbarHost plumbing needed on a screen that closes immediately after save |

## 4. Spec items NOT yet implemented (open)

| Item | Spec ref | Status |
|---|---|---|
| "Default target" setting (for manual apply) | §7.4 | **Recommended won't-do** (2026-07-17): Detail screen offers explicit Home/Lock/Both buttons — a default adds a setting without saving a tap. Revisit only if a one-tap apply surface (e.g. gallery long-press) is added |
| "Ask before replacing edit" setting | §7.4 | **Recommended won't-do** (2026-07-17): the editor pre-loads the existing `EditParams`, so re-saving is an update of a re-editable edit, not a destructive replace — there is nothing to warn about |
| Full empty-state / error-toast audit | §7.5 | Partial — new screens have states; no systematic audit pass |
| Phase 5 (premium gating / Play Billing) | §8 | Not started — spec marks it OPTIONAL / LAST |
| Real-ESRGAN TFLite model + `RealEsrganUpscaler` | §3b | ✅ Done 2026-07-18 — `realesr-general-x4v3` float TFLite (4.9 MB, BSD-3-Clause, Qualcomm AI Hub export) bundled in assets; tiled 128→512 inference with edge-replicated padding and central-region stitching; Lanczos fallback for >1024px sources or inference failure. Closes deviation D1 |
| Gradle wrapper jar in repo | — | ✅ Done 2026-07-17 — regenerated from the cached Gradle 8.14.4 distribution and committed; clean-clone CLI builds work |
| Release signing config | — | ✅ Done 2026-07-17 — `keystore.properties` pattern in `app/build.gradle.kts` (unsigned fallback when absent); see `keystore.properties.template` |

## 5. Verification status (as of 2026-07-17)

| Gate | Status |
|---|---|
| `assembleDebug` | ✅ green |
| `testDebugUnitTest` | ✅ green (`RotationEngineTest`) |
| `assembleRelease` + `bundleRelease` (R8 minify + `lintVitalRelease`) | ✅ green — unsigned APK 2.6 MB + AAB produced |
| `connectedDebugAndroidTest` (instrumented: LRU, migrations, ImageProcessor, SmartCrop) | ✅ green (2026-07-17, API 36.1 emulator) — after adding the missing `androidx.test:runner` dependency, which had made the very first run crash with `ClassNotFoundException: AndroidJUnitRunner` |
| Manual on-device pass (editor gestures, filters, AI features, share, QS tile, rotation) | ⬜ **not yet done** — checklist in `LAUNCH_PLAN.md` §4 |

## 6. Standing constraints (unchanged, still in force)

- Toolchain pinned: AGP 8.13.2 / Gradle 8.14.4 (wrapper; docs previously said 8.13) / Kotlin 2.1.0 /
  KSP 2.1.0-1.0.29 / Room 2.6.1 / Hilt 2.54, with `ksp.useKSP2=false`. **Do not run the AGP Upgrade
  Assistant.** If ever upgrading, bump Room (→2.7.2+) and Hilt (→2.57+) together with the toolchain.
- Originals are never modified; all processing writes to `processed/` / `upscaled/`.
- UI and rotation strategies talk only to `WallpaperRepository`.
- No network permission, no analytics. (ML Kit Play-services model delivery is the one documented
  exception — see P2.)
