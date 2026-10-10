# WallFlow — Production Launch Plan

> Status as of **2026-07-17**. This is the single checklist to get WallFlow 1.0 from the repo to
> the Play Store. Items marked ☑ are done; ☐ items are ordered — work top to bottom.

---

## 1. Where the product stands

The app is feature-complete for a 1.0: import (picker + share target), non-destructive editor
(crop, blur fill, filters, enhance, dim-lock, depth effect), tags, manual apply, three rotation
modes with LRU selection, QS tile, onboarding. Builds are green (debug, unit tests, R8 release).

Deliberately **out of scope for 1.0** (recorded in `PLAN_LOG.md` §4): default-apply-target
setting, ask-before-replace-edit setting (both superseded by the actual UX), Real-ESRGAN model,
premium/billing.

## 2. Engineering — done in the 2026-07-17 pass

- ☑ **Gradle wrapper jar** restored and committed — clean-clone CLI builds work.
- ☑ **Release signing** — `keystore.properties` pattern; unsigned fallback keeps CI working.
- ☑ **Backup rules bug fixed** — rules predated the studio pivot: `processed/` + `upscaled/`
  were being backed up toward the ~25 MB quota, and the DB was backed up *without* its images
  (a cloud restore would have produced a gallery of dead rows). Now: cloud backup carries
  settings only; device-to-device transfer carries the full library.
- ☑ **Privacy policy** drafted (`PRIVACY_POLICY.md`) — Play requires a hosted URL.
- ☑ **Docs synced** (real Gradle version, signing instructions, plan-log closures).
- ☑ **Field-failure fixes** (see CHANGELOG 2026-07-17): EXIF orientation baked in at import,
  undecodable imports rejected, share duplicate-import guard, depth no-subject toast,
  OOM-safe save/enhance, portrait lock.

## 3. Verification gates

| Gate | Status |
|---|---|
| `assembleDebug` | ✅ green (2026-07-17) |
| `testDebugUnitTest` | ✅ green (2026-07-17) |
| `assembleRelease` + `bundleRelease` (R8, lintVital) | see CHANGELOG for latest run |
| `connectedDebugAndroidTest` (LRU, 2 migrations, ImageProcessor, SmartCrop) | see CHANGELOG for latest run |
| Manual on-device pass | ☐ **you** — checklist in §4 |

## 4. Manual QA pass (☐ you, on a real phone — ~30 minutes)

Install the release APK (`app/build/outputs/apk/release/`) on a physical device:

1. **Onboarding** shows once; not again on relaunch.
2. **Import** several photos via the picker; thumbnails appear.
3. **Share** an image from Google Photos into WallFlow → lands in the editor.
4. **Editor**: pinch/pan crop; Auto (smart crop) on a photo with a face; blur fill on a
   portrait-orientation source; each filter + intensity slider; Enhance HD on a small image;
   dim-lock toggle; depth effect on a photo with a clear subject (requires Play services to
   fetch the ML model — first use needs network); save → "Edited" badge on detail.
5. **Apply** home / lock / both — verify each surface actually changes, edited variant is used.
6. **Rotation — interval**: set 15 min, lock the phone, wait ≥15 min (Doze can delay; that's
   expected) → wallpaper changed; LRU means no repeats until all cycle.
7. **Rotation — screen-on**: enable (grant notification permission), wake the device a few
   times → lock screen changes each wake; notification present; survives reboot.
8. **QS tile**: add the WallFlow tile, tap → wallpaper rotates.
9. **Delete** a wallpaper → gone from grid; rotation skips it.
10. **Dark theme** + rotate device on each screen — no crashes, sane layouts.
11. Check OEM battery-optimization flow via Settings → the exemption button.

## 5. Release artifacts (☐ you — one-time keystore, then one command)

1. Create the upload keystore (once, keep it safe):
   `keytool -genkeypair -v -keystore wallflow-upload.jks -alias wallflow -keyalg RSA -keysize 2048 -validity 10000`
2. `copy keystore.properties.template keystore.properties` and fill in the passwords.
3. `./gradlew bundleRelease` → signed AAB at `app/build/outputs/bundle/release/app-release.aab`.

## 6. Play Console (☐ you — ~1–2 hours the first time)

1. **Developer account** at play.google.com/console ($25 one-time). Identity verification can
   take a few days — start this first.
2. **Host the privacy policy** — e.g. push this repo to GitHub and enable Pages, or a Gist;
   any public URL serving `PRIVACY_POLICY.md` content works.
3. **Create app** → fill **App content**:
   - Privacy policy URL.
   - **Data safety**: "No data collected, no data shared." (True: no INTERNET permission.)
   - **Content rating** questionnaire → Everyone.
   - Ads: none. Target audience: 13+ (avoids the children's-policy track).
   - **Foreground service / special use declaration** (targetSdk 35 requires it):
     *"WallFlow's optional 'rotate on every screen on' mode must react to ACTION_SCREEN_ON,
     which cannot be received from the manifest or scheduled by WorkManager/AlarmManager
     (sub-minute, event-driven). A user-initiated foreground service with a visible
     notification is the only mechanism; it runs only while the user keeps the mode enabled."*
4. **Store listing** (draft — edit to taste):
   - **Title**: WallFlow — Wallpaper Studio
   - **Short description** (80 chars max):
     *"Crop, filter and perfect your photos as wallpapers — then let them auto-rotate."*
   - **Full description**: lead with the editor (crop locked to your exact screen, smart crop,
     filters, depth effect, blur fill), then rotation (interval / every-wake / LRU no-repeats),
     then privacy ("no internet permission — your photos never leave your phone"). The README
     feature list is ready-made source material.
   - **Graphics**: app icon (512×512 export of the adaptive icon), feature graphic 1024×500,
     ≥4 phone screenshots (gallery, editor with filter strip, depth effect, settings).
     Screenshot tip: `adb exec-out screencap -p > shot.png` on a clean device with attractive
     sample images.
5. **Upload the AAB** to **Internal testing** first (instant review, installable via link) →
   run the §4 QA pass on the internal build → promote the same release to **Production**.
   First production review typically takes 1–7 days.

## 7. Post-launch (v1.1 candidates, in value order)

1. **Crash visibility** — the app ships with zero telemetry (by design). Consider ACRA or
   Play Console's built-in ANR/crash stats (no code needed) as the only signal source.
2. **Empty-state / error-toast audit** (open item from spec §7.5).
3. **Real-ESRGAN TFLite** upscaler behind the existing `Upscaler` interface.
4. **Filter thumbnails** in the editor strip (deviation U1) and before/after slider (U2).
5. **Per-device tuning** for OEM battery killers (dontkillmyapp.com guidance in-app).
6. Localization — strings are already fully resource-based.

## 8. Standing constraints (unchanged)

- Toolchain pinned (AGP 8.13.2 / Gradle 8.14.4 / Kotlin 2.1.0 / KSP 2.1.0-1.0.29 /
  Room 2.6.1 / Hilt 2.54, `ksp.useKSP2=false`). **Never run the AGP Upgrade Assistant.**
- No network permission; keep the data-safety form honest by keeping it that way.
- Originals are never modified; UI and rotation talk only to `WallpaperRepository`.
