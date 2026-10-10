# WallFlow — Privacy Policy

_Last updated: 17 July 2026_

WallFlow is a wallpaper manager and editor for Android, developed by Keshav Tiwari
("we", "us"). This policy explains what data WallFlow handles and where it goes.

## The short version

**WallFlow collects nothing.** The app has no network permission — it is technically incapable of
sending your data anywhere. There are no accounts, no analytics, no ads, and no tracking of any
kind.

## What the app stores, and where

All data lives **only on your device**, in the app's private storage:

- **Images you import.** When you pick a photo (or share one into WallFlow), a private copy is
  made inside the app's storage so your wallpaper survives even if you delete the original.
  WallFlow can only access images you explicitly pick or share — it uses the Android Photo
  Picker and never requests access to your photo library.
- **Edits and metadata.** Crop positions, filter choices, tags, and usage timestamps (used for
  the "least recently used" rotation order) are stored in a local database.
- **Settings.** Rotation mode, interval, and similar preferences.

Deleting a wallpaper in the app deletes its files. Uninstalling the app deletes everything.

## Backups

Your device's standard Android backup may include WallFlow's settings. Images and the wallpaper
library are excluded from cloud backups. If you move to a new phone using Android's
device-to-device transfer, your full library transfers directly between your devices.

## On-device machine learning

Two optional editor features — subject-aware auto-crop (face detection) and the depth effect
(subject segmentation) — use **Google ML Kit**, which runs on your device via Google Play
services. Your images are processed locally and are not uploaded. Google Play services may
download the ML models themselves and, as a Google component, operates under the
[Google Privacy Policy](https://policies.google.com/privacy).

## Permissions WallFlow requests

| Permission | Why |
|---|---|
| Set wallpaper | To apply wallpapers — the app's core purpose. |
| Notifications | Shows the ongoing notification Android requires while "rotate on every screen on" is active. |
| Run foreground service | Powers the same "rotate on every screen on" mode. |
| Start at boot | Resumes your auto-rotation after a restart. |

WallFlow does **not** request internet access, location, contacts, camera, microphone, or
general file/photo-library access.

## Children

WallFlow does not collect data from anyone, including children.

## Changes to this policy

If a future version of WallFlow ever changes what data is handled, this policy will be updated
and the "last updated" date revised before that version ships.

## Contact

Questions about privacy in WallFlow: **dekichatgpt@gmail.com**
