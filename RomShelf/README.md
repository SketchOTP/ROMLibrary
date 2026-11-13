# ROM Shelf 0.4.0 — GBA library

ROM Shelf is a native Android library for organizing games the owner already has. It scans user-selected `.gba` and `.zip` folders, shows a cover grid, hides exact-content duplicates without deleting ROMs, tracks battery-save files, and launches games through Pizza Boy.

## Current behavior

- Tap **+** to add `.gba`/`.zip` games and optional matching `.sav` files. ROM Shelf copies games into the selected ROM folder, leaves the source files alone, and skips exact ROM duplicates by content. Selected battery saves are copied into the shared folder only when their source timestamp is newer than the existing matching save; an existing save is backed up before replacement. If the source timestamp is unavailable, an existing save is kept.
- Choose a writable box-art folder once from Settings or the first **Change box art** action. **Add box art images** copies supported PNG/JPEG/WebP images into that folder and skips identical images. Long-press a game and choose **Change box art** to pick from that folder; the selected image becomes that game's custom cover. **Edit game name** changes only the display title.
- Artwork comes from manually imported custom covers or the Libretro GBA box-art catalog. ROM Shelf does not inspect emulator screenshots or save-state preview images.
- Battery saves stay in one shared folder. The Pixel's selected folder is `/storage/emulated/0/Johnemulators/GBA/save`; Pizza Boy GBA Pro is configured to use that same folder. ROM Shelf now scans and edits that one canonical folder, even when an older install stored separate John or Pizza folder choices. John GBA remains installed with its data and files, but ROM Shelf no longer offers it as a launcher.
- Pizza Boy Pro is preferred. If it is absent, ROM Shelf offers the installed Pizza Boy GBA or Free app. Every game uses the same app-wide Pizza Boy version; legacy per-game emulator choices are cleared.
- Recent sorting picks the newest known time across the shared battery `.sav`, a Pizza quickstate activity estimate, and a legacy Pizza session return. Returning from Pizza records the quickstate estimate because Auto Save Stat is enabled; the card labels the winning source in Eastern Time. Android keeps Pizza's `.stat` files private, so this is a session-return estimate rather than the exact quickstate file timestamp.
- Pizza Boy's automatic save states remain in its app-specific `Android/data` folder. On Android 11+, the system blocks ROM Shelf from selecting or reading that directory, so Pizza manages its own state timestamps. Enable **Auto Load Stat** and **Auto Save Stat**, with automatic slot **1**, in Pizza Boy to restore and refresh that state. Battery `.sav` files stay in the shared John/Pizza folder.
- ChatGPT sign-in and AI matching have been removed. On startup, the app clears the old local ChatGPT token preferences/keystore alias and AI URL references. Cached covers and saved game titles are preserved. Internet permission remains for online cover-art downloads.

## Pixel evidence

On 2026-11-13, signed ROM Shelf 0.4.0/versionCode 13 was installed over 0.3.9 with the established signer and existing app data. The home screen displayed 49 games, 31 shared battery saves, and three hidden duplicate ROMs. The `+` menu displayed **Add ROMs and optional .sav files**, **Add box art images**, and **Rescan Shelf**. Settings displayed the ROM folder, shared save folder, box-art folder, image import, Pizza Boy version, rescan, and help entries. The existing `Pictures/Pizza Boy A Screenshots/ROM Shelf Box Art - Re-add` folder was granted persistently; both the long-press cover picker and Settings image picker opened in that folder. No ROM, save, or cover image was selected or modified during this check.

On 2026-11-13, ROM Shelf 0.3.7 was installed over the same signed package without clearing its app data. A ROM Shelf game-card tap foregrounded Pizza Boy GBA Pro 3.9.2 for Monomyth. Auto Load Stat and Auto Save Stat are enabled, with automatic slot 1 selected. Five Pizza automatic slot-1 states were present; four had matching shared `.sav` files and newer state timestamps, and one had no matching `.sav`. The Monomyth automatic state was rewritten during verification, so its current file time does not establish the owner's last play time. The app now includes completed Pizza-session activity in Recent sorting and labels it as an estimate because it cannot read Pizza's private state-file timestamps.

Before that launch test, 33 unique nonempty `.sav` files were verified in the shared folder. Duplicate-name collisions were resolved by retaining the newest source; two collision winners came from Pizza Boy. One empty placeholder was excluded. John's `.sav0` recovery sidecars and emulator save-state files were retained. A fresh on-device timestamp check for the owner's Monomyth save is still pending.

## Setup

1. Select the folder that contains the ROMs. If Android rejects a root or `Download` folder, choose the permitted ROM subfolder instead.
2. Select `Johnemulators/GBA/save` as the shared battery-save folder and set Pizza Boy's **Save folder** to the same directory. ROM Shelf cannot change Pizza Boy's private settings; the Pixel is already configured to use this shared path.
3. Tap **+ → Add ROMs and optional .sav files**. Choose one or more `.gba`/`.zip` files and, when available, matching `.sav` files in the same picker. ROM Shelf copies them into the selected folders, skips exact ROM duplicates, and keeps the newest timestamped matching battery save. All game cards continue to use the app-wide Pizza Boy launcher.
4. Set the box-art folder in Settings once. Use **+ → Add box art images** or Settings → **Add box art images** to copy new images there. Long-press a game and choose **Change box art** to select an image from that folder, or **Edit game name** to set its display title. ROM Shelf can also fetch GBA box art from the Libretro catalog.

ROM Shelf can only read folders for which Android grants a persistent document-tree permission. Battery `.sav` files and emulator save states are different formats. The app does not bundle or distribute ROMs.

## Build and signing

```bash
cd RomShelf
./gradlew :app:compileDebugJavaWithJavac assembleRelease lintVitalRelease
```

The Gradle release build is unsigned. The signed 0.4.0 update is `artifacts/rom-shelf/apk/ROM-Shelf-0.4.0.apk`, SHA-256 `ba990ebb518779601e958611836fd0e2811ae5e368ac0ce5f974f11da444e0b2`. Its signer matches the owner-provided certificate SHA-256 `b094cd94d6975c3c62c467658d38523091c638068b9fb3b06d0d2998a19d2456`; `apksigner` and `zipalign` checks passed. Keep the keystore and password outside Git and build logs.
