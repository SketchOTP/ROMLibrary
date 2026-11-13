# Current checkpoint

ENTRY_ID: A6-CHECKPOINT-20261113
UTC: 2026-11-13T13:50:57Z
ACTOR: setup-agent
TYPE: CHECKPOINT
GOAL: NOT_SET
TASK: D-A6-SETUP-20261113
STATUS: IN_PROGRESS
SUPERSEDES: none
EVIDENCE: .authority/setup/setup-20261113T135057Z/discovery.json
SUMMARY: New, empty, non-Git workspace. Authority 6.0 setup is active; no ROM product goal/history is known. Runtime and Atlas work are being installed locally.
NEXT: Build, qualify, and record the required runtime; then perform native-host and fresh-session checks.

## A6-CHECKPOINT-TAKEOVER-20261113 — ROM Shelf adoption and partial qualification

ENTRY_ID: A6-CHECKPOINT-TAKEOVER-20261113
UTC: 2026-11-13T14:55:34Z
ACTOR: setup-agent
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-A6-SETUP-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-20261113
EVIDENCE: .authority/setup/setup-20261113T135057Z/adoption.json; .authority/setup/setup-20261113T135057Z/coverage.json; .authority/setup/setup-20261113T135057Z/rom-shelf-build.json
SUMMARY: Imported the frozen ROM Shelf source and supplied APK, built an unsigned release artifact, and verified Authority runtime/viewer behavior. Fresh Codex Desktop loading and ROM/save/emulator workflows remain open.
NEXT: Open a fresh Codex Desktop session at this repository; then qualify app workflows with owner-controlled disposable ROM/save fixtures.

## A6-CHECKPOINT-ROM-SHELF-REVIEW-FIX-20261113 — review fixes and source verification

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-REVIEW-FIX-20261113
UTC: 2026-11-13T15:20:26Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-ROM-SHELF-REVIEW-FIX-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-TAKEOVER-20261113
EVIDENCE: .authority/setup/setup-20261113T135057Z/rom-shelf-build.json; .authority/setup/setup-20261113T135057Z/coverage.json; RomShelf/PROJECT_HANDOFF.md
SUMMARY: Fixed reviewed source defects in exact save target handling, artwork fetch policy, manual cover replacement, ZIP scan bounds, content hash freshness, and stable per-ROM preferences; added independent save folder selection for Shared, John GBA, and Pizza Boy. Seven JVM regression tests, release build, and lint vital passed. Product acceptance remains open for SAF/provider and real emulator behavior with controlled ROM/save fixtures.
NEXT: Exercise the app on the owner device with disposable ROM/save fixtures; verify folder grants, duplicate presentation, save operations, and emulator launch/save pickup without touching existing game data.

## A6-CHECKPOINT-ROM-SHELF-UPDATE-20261113 — signed update preparation

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-UPDATE-20261113
UTC: 2026-11-13T15:24:00Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-ROM-SHELF-SIGNED-UPDATE-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-REVIEW-FIX-20261113
EVIDENCE: .authority/setup/setup-20261113T135057Z/rom-shelf-build.json; .authority/setup/setup-20261113T135057Z/coverage.json; artifacts/rom-shelf/apk/ROM-Shelf-0.3.1-unsigned-aligned.apk
SUMMARY: Built and validated ROM Shelf 0.3.1/versionCode 4 as an update candidate. The Pixel's installed 0.3.0 APK signer matches the expected fingerprint. The original signing backup is unavailable in local key-file search and connected Drive; no replacement key was generated and no install attempted.
NEXT: Use the owner's existing signing backup to sign the candidate, verify the fingerprint, and install it over Pixel 0.3.0. The signing backup location is requested from the owner.

## A6-CHECKPOINT-ROM-SHELF-SIGNED-UPDATE-20261113 — update installed, repository push pending

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-SIGNED-UPDATE-20261113
UTC: 2026-11-13T16:10:00Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-ROM-SHELF-SIGNED-UPDATE-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-UPDATE-20261113
EVIDENCE: .authority/setup/setup-20261113T135057Z/rom-shelf-build.json; .authority/setup/setup-20261113T135057Z/rom-shelf-device.json; artifacts/rom-shelf/apk/ROM-Shelf-0.3.1.apk
SUMMARY: Signed 0.3.1/versionCode 4 with the original signer after certificate match, installed it in place over Pixel 0.3.0, verified the installed APK signer, and started MainActivity successfully. The signed artifact and source are prepared for the requested GitHub push; ROM/save/emulator feature acceptance remains open.
NEXT: Commit and push this repository to SketchOTP/ROMLibrary using the supplied Git identity, then finish owner-controlled ROM/save/emulator acceptance when a fixture is available.

## A6-CHECKPOINT-ROM-LIBRARY-PUBLISHED-20261113 — source and signed update pushed

ENTRY_ID: A6-CHECKPOINT-ROM-LIBRARY-PUBLISHED-20261113
UTC: 2026-11-13T16:18:55Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-ROM-SHELF-PUBLISH-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-SIGNED-UPDATE-20261113
EVIDENCE: .authority/setup/setup-20261113T135057Z/rom-shelf-build.json; .authority/setup/setup-20261113T135057Z/rom-shelf-device.json; .authority/setup/setup-20261113T135057Z/coverage.json
SUMMARY: Pushed source, Authority setup, preserved provenance, and signed 0.3.1 APK to SketchOTP/ROMLibrary main at fa60e8107f4276d99858285c5d646d31b2bba136 with author SketchOTP <sketchotp@gmail.com>. Remote ref matched and the current workspace is connected to origin/main. Pixel update and launcher smoke test passed; product-level emulator/save acceptance remains open.
NEXT: Use a disposable owner-controlled ROM/save fixture to verify SAF grants, scan/dedup behavior, save operations, and John GBA/Pizza Boy loading without touching existing game data.

## A6-CHECKPOINT-ROM-SHELF-COVERS-20261113 — local cover art installed

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-COVERS-20261113
UTC: 2026-11-13T17:16:29Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-20261113
TASK: D-ROM-SHELF-COVERS-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-LIBRARY-PUBLISHED-20261113
EVIDENCE: .authority/setup/rom-shelf-covers-20261113/manifest.json; RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java
SUMMARY: Installed 52 per-ROM custom cover images from 38 online/local source assets into Pixel package com.animus.romshelf version 0.1.0, which holds the 52-entry ROM folder. Device SHA-256 checks matched all 52 files; no partials or stale missing markers remain. Three hack entries use Emerald base artwork because matching box art was not found; Petalburg Dynasty uses its title-screen image. ROMs, saves, APK source, and repository media were untouched. Visual UI confirmation remains open. The separately installed 0.3.1 package com.animus.romshelf.launcher has separate data and was not seeded.
NEXT: After the phone is unlocked, reopen com.animus.romshelf and visually confirm the covers. If using com.animus.romshelf.launcher, grant it the ROM folder and import covers into that separate library.


## A6-CHECKPOINT-ROM-SHELF-036-PREP-20261113 — signed candidate, device acceptance pending

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-036-PREP-20261113
UTC: 2026-11-13T19:10:00Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-SHARED-PIZZA-20261113
TASK: D-ROM-SHELF-OWNER-STEER-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-COVERS-20261113
EVIDENCE: .authority/setup/rom-shelf-release-0.3.6-20261113.json; RomShelf/README.md; RomShelf/PROJECT_HANDOFF.md
SUMMARY: ROM Shelf 0.3.6/versionCode 9 builds with seven JVM tests passing and release lint vital passing. The APK is zipaligned and signed with the original expected certificate; SHA-256 is 30f7fcefcd76fa1e373c4ece779eba9472417a798d3b24f04da62d0e45599a5f. Settings and data routing now use one canonical John GBA/Pizza Boy save folder and a global Pizza Boy launcher; John GBA and its data are preserved. AI resolver/OAuth source is removed. Eastern-time save labels and Pizza launch-session screenshot association are included. The Pixel is disconnected; only the tablet is visible and untouched. Live Monomyth comparison, screenshot folder inspection, install and device UI verification remain pending.
NEXT: When Pixel reconnects, inspect all candidate save copies read-only, determine whether Monomyth is the newest Pizza save, verify/adjust Pizza Boy's save folder only with preserved backup evidence, then install and verify 0.3.6.

## A6-CHECKPOINT-ROM-SHELF-QUICKSTATE-RECENCY-20261113 — 0.3.7 installed; exact state-time comparison limited

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-QUICKSTATE-RECENCY-20261113
UTC: 2026-11-13T20:44:27Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-SHARED-PIZZA-20261113
TASK: D-ROM-SHELF-QUICKSTATE-RECENCY-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-036-PREP-20261113
EVIDENCE: .authority/setup/rom-shelf-quickstate-recency-20261113.json; RomShelf/PROJECT_HANDOFF.md; artifacts/rom-shelf/apk/ROM-Shelf-0.3.7.apk
SUMMARY: ROM Shelf 0.3.7/versionCode 10 is signed with the established owner certificate and installed over the existing Pixel app data. All game cards still use the global Pizza Boy launch route. Pizza Auto Load Stat and Auto Save Stat are enabled with automatic slot 1. Recent sorting now compares shared battery-save time to the last completed ROM Shelf → Pizza session return time and labels the date source. Eight unit tests, release build, lint vital, signature and alignment checks passed. The app cannot directly read Pizza's private state timestamps; Monomyth's slot was rewritten during verification, so test time is not owner play-time evidence. A pre-change state/preview copy remains outside the repo.
NEXT: A direct guarantee that the newest `.stat` or `.sav` wins requires Pizza Boy to expose state timestamps via shared storage or a supported API. Keep the session timestamp explicitly labeled as an estimate; do not claim exact state-file comparison.

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-QUICKSTATE-INCLUSIVE-038-20261113
UTC: 2026-11-13T21:01:22Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-SHARED-PIZZA-20261113
TASK: D-ROM-SHELF-QUICKSTATE-INCLUSIVE-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-QUICKSTATE-RECENCY-20261113
EVIDENCE: .authority/setup/rom-shelf-quickstate-recency-0.3.8-20261113.json; RomShelf/PROJECT_HANDOFF.md; artifacts/rom-shelf/apk/ROM-Shelf-0.3.8.apk
SUMMARY: ROM Shelf 0.3.8/versionCode 11 records a Pizza quickstate activity estimate when returning from a Shelf-launched Pizza session and sorts Recent by the maximum of that estimate, the shared `.sav` time, and retained Pizza session time. Release build, eight unit tests, lint vital, signature/alignment checks, in-place Pixel installation, and the new sort-menu label were verified. Pixel Auto Load Stat/Auto Save Stat are configured with slot 1. The app still cannot read the exact private `.stat` mtime; Monomyth's existing session stamp remains explicitly a session estimate, not evidence of owner play time.
NEXT: Exact quickstate-file versus battery-save timestamp ranking remains dependent on Pizza Boy exposing quickstate metadata through shared storage or a supported integration API.

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-NO-SCREENSHOTS-039-20261113
UTC: 2026-11-13T21:19:16Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-NO-SCREENSHOTS-20261113
TASK: D-ROM-SHELF-NO-SCREENSHOTS-20261113
STATUS: COMPLETE
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-QUICKSTATE-INCLUSIVE-038-20261113
EVIDENCE: .authority/setup/rom-shelf-no-screenshots-0.3.9-20261113.json; RomShelf/PROJECT_HANDOFF.md; artifacts/rom-shelf/apk/ROM-Shelf-0.3.9.apk
SUMMARY: ROM Shelf 0.3.9/versionCode 12 removes emulator screenshot/state-preview art capture, scanning, cache selection, and Settings controls. Startup removes ROM Shelf-generated `.state.img` cache entries and retired screenshot settings; it releases the old SAF grant only if that URI is not still used for ROMs or saves. A read-only Pixel check found zero generated `.state.img` cache files; Settings no longer lists screenshot controls. Generic custom cover import, Libretro art, Pizza launch/session recency, emulator source files, ROMs, and shared saves are preserved. Release build/lint, expected-signature verification, alignment, and in-place Pixel install passed.
NEXT: Exact Pizza `.stat` timestamp comparison remains the separate documented platform limitation; the current Pizza session timestamp is still only an estimate.

## A6-CHECKPOINT-ROM-SHELF-BOX-ART-READD-20261113 — box art copied for easy re-adding

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-BOX-ART-READD-20261113
UTC: 2026-11-13T21:28:33Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-NO-SCREENSHOTS-20261113
TASK: D-ROM-SHELF-BOX-ART-READD-20261113
STATUS: COMPLETE
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-NO-SCREENSHOTS-039-20261113
EVIDENCE: .authority/setup/rom-shelf-box-art-readd-20261113.json; .authority/setup/rom-shelf-covers-20261113/manifest.json; Pixel wireless ADB SHA-256 comparison; RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java:635-642
SUMMARY: Copied 52 existing ROM Shelf cover images into Pixel Pictures/Pizza Boy A Screenshots/ROM Shelf Box Art - Re-add. Filenames are readable, extensions match PNG/JPEG/WebP contents, and all 52 on-device hashes match source images. The existing Pizza screenshot and original cover directory remain present. The app import picker uses ACTION_OPEN_DOCUMENT with image/*; opening that UI was not part of this copy task.
NEXT: Re-add a cover from a game card with Import custom cover, selecting the corresponding image from the new folder.


## A6-CHECKPOINT-ROM-SHELF-INTAKE-040-20261113 — add ROM and artwork workflows

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-INTAKE-040-20261113
UTC: 2026-11-13T22:21:29Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-NO-SCREENSHOTS-20261113
TASK: D-ROM-SHELF-FUTURE-INTAKE-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-BOX-ART-READD-20261113
EVIDENCE: .authority/setup/rom-shelf-0.4.0-20261113.json; .agent/OUTCOMES.md; artifacts/rom-shelf/apk/ROM-Shelf-0.4.0.apk
SUMMARY: Implemented future ROM and matching battery-save import, duplicate skipping, backup-safe newest-save replacement, persistent box-art folder management, and per-game title/cover actions in ROM Shelf 0.4.0/versionCode 13. Build, lint, signer, alignment, in-place Pixel install, main screen, add menu, Settings, long-press actions, and both art-folder picker entry paths were verified. The selected art folder permission survived app restart. No ROM, save, or cover was imported or modified; new-item Pizza launch/save pickup remains unqualified.
NEXT: Use an owner-approved disposable ROM and matching .sav to verify import, Pizza launch, and save pickup while preserving existing game data.


## A6-CHECKPOINT-ROM-SHELF-INTAKE-040-PUBLISHED-20261113 — 0.4.0 published

ENTRY_ID: A6-CHECKPOINT-ROM-SHELF-INTAKE-040-PUBLISHED-20261113
UTC: 2026-11-13T22:23:19Z
ACTOR: Codex
TYPE: CHECKPOINT
GOAL: A6-GOAL-ROM-SHELF-NO-SCREENSHOTS-20261113
TASK: D-ROM-SHELF-FUTURE-INTAKE-20261113
STATUS: PARTIAL
SUPERSEDES: A6-CHECKPOINT-ROM-SHELF-INTAKE-040-20261113
EVIDENCE: .authority/setup/rom-shelf-0.4.0-20261113.json; .agent/OUTCOMES.md; commit 28f59d66ab66b84e4b8b38d93f7b667633619aab; origin/main ref verification
SUMMARY: Published ROM Shelf 0.4.0 source and signed APK to SketchOTP/ROMLibrary main at 28f59d66ab66b84e4b8b38d93f7b667633619aab. Remote and local refs match; worktree is clean. The signed update is installed on the Pixel and the persistent box-art folder remains selected. New-item Pizza launch/save pickup is not yet qualified because no ROM or save fixture was imported.
NEXT: Verify add/import/launch/save pickup with an owner-approved disposable GBA ROM and matching .sav.
