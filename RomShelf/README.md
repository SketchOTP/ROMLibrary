# ROM Shelf 0.3.7 — GBA library

ROM Shelf is a native Android library for organizing games the owner already has. It scans user-selected `.gba` and `.zip` folders, shows a cover grid, hides exact-content duplicates without deleting ROMs, tracks battery-save files, and launches games through Pizza Boy.

## Current behavior

- Artwork comes from manually installed/imported covers, the Libretro GBA box-art catalog, or the optional latest-screenshot feature. Screenshot images are copied into ROM Shelf's own cache and matched to ROM filenames. If no screenshot matches, the existing cover remains.
- The screenshot folder is selected through Android's document picker. Inspection of the installed Pizza Boy GBA Pro 3.9.2 APK shows its screenshot handler writes timestamp-named JPEGs under `Pictures/Pizza Boy A Screenshots`; the Pixel's live folder/files still need confirmation after reconnect. ROM Shelf records which game it launched and associates the newest Pizza Boy screenshot created in that session when the user returns, then copies it to ROM Shelf's per-game cache. Take the screenshot after reaching the save point you want shown. John-style `.js0` / `.jsa` previews with ROM-matching filenames are also supported. Manual import is available if no matching screenshot exists.
- Battery saves stay in one shared folder. The Pixel's selected folder is `/storage/emulated/0/Johnemulators/GBA/save`; Pizza Boy GBA Pro is configured to use that same folder. ROM Shelf now scans and edits that one canonical folder, even when an older install stored separate John or Pizza folder choices. John GBA remains installed with its data and files, but ROM Shelf no longer offers it as a launcher.
- Pizza Boy Pro is preferred. If it is absent, ROM Shelf offers the installed Pizza Boy GBA or Free app. Every game uses the same app-wide Pizza Boy version; legacy per-game emulator choices are cleared.
- Recent sorting compares the shared battery `.sav` timestamp with the latest ROM Shelf → Pizza session return time. The card labels which timestamp it shows in Eastern Time. A session return is an activity estimate, not the exact timestamp of Pizza's save-state file.
- Pizza Boy's automatic save states remain in its app-specific `Android/data` folder. On Android 11+, the system blocks ROM Shelf from selecting or reading that directory, so Pizza manages its own state timestamps. Enable **Auto Load Stat** and **Auto Save Stat**, with automatic slot **1**, in Pizza Boy to restore and refresh that state. Battery `.sav` files stay in the shared John/Pizza folder.
- ChatGPT sign-in and AI matching have been removed. On startup, the app clears the old local ChatGPT token preferences/keystore alias and AI URL references. Cached covers and saved game titles are preserved. Internet permission remains for online cover-art downloads.

## Pixel evidence

On 2026-11-13, ROM Shelf 0.3.7 was installed over the same signed package without clearing its app data. A ROM Shelf game-card tap foregrounded Pizza Boy GBA Pro 3.9.2 for Monomyth. Auto Load Stat and Auto Save Stat are enabled, with automatic slot 1 selected. Five Pizza automatic slot-1 states were present; four had matching shared `.sav` files and newer state timestamps, and one had no matching `.sav`. The Monomyth automatic state was rewritten during verification, so its current file time does not establish the owner's last play time. The app now includes completed Pizza-session activity in Recent sorting and labels it as an estimate because it cannot read Pizza's private state-file timestamps.

Before that launch test, 33 unique nonempty `.sav` files were verified in the shared folder. Duplicate-name collisions were resolved by retaining the newest source; two collision winners came from Pizza Boy. One empty placeholder was excluded. John's `.sav0` recovery sidecars and emulator save-state files were retained. A fresh on-device timestamp check for the owner's Monomyth save is still pending.

## Setup

1. Select the folder that contains the ROMs. If Android rejects a root or `Download` folder, choose the permitted ROM subfolder instead.
2. Use `Johnemulators/GBA/save` as the shared battery-save folder and set Pizza Boy's **Save folder** to that same directory. ROM Shelf reads and writes this single selected folder.
3. Tap a cover to launch through Pizza Boy. Long-press for save operations, title editing, screenshot/cover import, and details. The Pizza Boy version is selected once in Settings for all games.
4. To use screenshots, enable **Use latest game screenshot as cover** and choose `Pictures/Pizza Boy A Screenshots`. ROM Shelf attributes a timestamped screenshot to the game that was open in Pizza Boy when it was captured. For older John GBA previews, select the `Johnemulators/GBA` parent so `state` and `autostate` are scanned.

ROM Shelf can only read folders for which Android grants a persistent document-tree permission. Battery `.sav` files and emulator save states are different formats. The app does not bundle or distribute ROMs.

## Build and signing

```bash
cd RomShelf
./gradlew testDebugUnitTest assembleRelease
```

The Gradle release build is unsigned. The signed update is `artifacts/rom-shelf/apk/ROM-Shelf-0.3.7.apk`, signed with the owner-provided key whose certificate SHA-256 is `b094cd94d6975c3c62c467658d38523091c638068b9fb3b06d0d2998a19d2456`. Keep the keystore and password outside Git and build logs.
