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
