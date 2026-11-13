# Goal ledger

## Original charter status — unknown

ENTRY_ID: A6-GOAL-UNKNOWN-20261113
UTC: 2026-11-13T13:50:57Z
ACTOR: setup-agent
TYPE: GOAL_BASELINE
GOAL: NOT_SET
TASK: Authority 6.0 repository setup
STATUS: UNKNOWN
SUPERSEDES: none
EVIDENCE: owner request to set up Authority 6.0; no ROM Library product goal or charter was present in the repository
SUMMARY: No application charter was supplied. The operational setup request is recorded separately as a directive and does not define a ROM product outcome.
NEXT: Await an explicit ROM Library product goal.

## Effective product goal

ROM Shelf is a clean, intelligent Android GBA game library that automatically organizes ROMs into a beautiful cover-art grid, uses AI to identify games and find accurate artwork, eliminates duplicates, tracks save progress and recent activity, and launches any game seamlessly in the user's preferred emulator with its existing save—creating one simple, unified gaming experience.

## Owner-provided charter — 2026-11-13

ENTRY_ID: A6-GOAL-ROM-SHELF-20261113
UTC: 2026-11-13T14:39:53Z
ACTOR: owner
TYPE: GOAL_REVISION
GOAL: ROM Shelf Android GBA library
TASK: D-A6-SETUP-20261113
STATUS: EFFECTIVE
SUPERSEDES: A6-GOAL-UNKNOWN-20261113
EVIDENCE: explicit owner request in Codex Desktop; canonical project handoff at https://app.notion.com/p/417833cb27ff81a285abd8a8e4cec453?pvs=207
SUMMARY: Organize GBA ROMs in a cover grid, identify games and accurate art with optional AI, hide exact duplicates without deleting source files, track save presence and recent activity, and launch in the preferred emulator with existing progress.
NEXT: Preserve local-first behavior and qualify each product promise against source and device evidence. Treat emulator save compatibility and external launch as unverified until tested on the target device.

## Owner steering — Pizza-only launch and local screenshots

ENTRY_ID: A6-GOAL-ROM-SHELF-PIZZA-ONLY-20261113
UTC: 2026-11-13T18:50:47Z
ACTOR: owner
TYPE: GOAL_REVISION
GOAL: ROM Shelf Android GBA library
TASK: D-ROM-SHELF-OWNER-STEER-20261113
STATUS: EFFECTIVE
SUPERSEDES: A6-GOAL-ROM-SHELF-20261113
EVIDENCE: explicit owner direction in Codex Desktop on 2026-11-13
SUMMARY: ROM Shelf is a local-first GBA library with an artwork grid, exact-content duplicate hiding, battery-save status and Eastern-time save timestamps. It launches every game through Pizza Boy only. John GBA stays installed and its existing save folder remains the shared battery-save location for Pizza Boy. Remove ChatGPT/AI features and clean the Settings menu. Provide an app-owned screenshot cache fed by matching Pizza Boy save-state previews/screenshots when available, with manual screenshot import as a fallback. Never delete ROMs or emulator recovery data to deduplicate.
NEXT: Install the signed 0.3.6 candidate after the Pixel reconnects, verify Monomyth's live save timestamp and Pizza screenshot storage, then refresh repository evidence and publish the completed changes.


## Owner refinement — one Pizza Boy path and save system

ENTRY_ID: A6-GOAL-ROM-SHELF-SHARED-PIZZA-20261113
UTC: 2026-11-13T19:10:00Z
ACTOR: owner
TYPE: GOAL_REVISION
GOAL: ROM Shelf Android GBA library
TASK: D-ROM-SHELF-OWNER-STEER-20261113
STATUS: EFFECTIVE
SUPERSEDES: A6-GOAL-ROM-SHELF-PIZZA-ONLY-20261113
EVIDENCE: owner direction in Codex Desktop, 2026-11-13
SUMMARY: ROM Shelf is a local-first Android GBA library with cover grid, exact ROM duplicate hiding, shared battery-save activity timestamps shown in America/New_York, and Pizza Boy as the only launch family. One app-wide Pizza Boy version launches every game. The John GBA application and its existing user data remain installed; the existing Johnemulators/GBA/save directory is the canonical .sav folder and Pizza Boy must use that same path. Migrate duplicate-named saves by retaining the newest nonempty version; preserve backups, recovery sidecars, states and ROMs. Remove ChatGPT/OAuth/AI matching and simplify Settings to ROM folder, one shared save folder, global Pizza Boy version, screenshot toggle/folder, rescan and help. Associate the newest Pizza Boy timestamp-named screenshot taken during a game launch session with that ROM, cache it inside ROM Shelf, and allow manual cover import as fallback.
NEXT: When Pixel reconnects, compare the live Monomyth save timestamp with every canonical and inactive save copy, verify Pizza Boy screenshot storage and shared save configuration, install 0.3.6 as an update, and qualify visible UI plus launch/save/screenshot behavior.

## Owner refinement — remove screenshot cover functionality

ENTRY_ID: A6-GOAL-ROM-SHELF-NO-SCREENSHOTS-20261113
UTC: 2026-11-13T21:12:03Z
ACTOR: owner
TYPE: GOAL_REVISION
GOAL: ROM Shelf Android GBA library
TASK: D-ROM-SHELF-NO-SCREENSHOTS-20261113
STATUS: EFFECTIVE
SUPERSEDES: A6-GOAL-ROM-SHELF-SHARED-PIZZA-20261113
EVIDENCE: explicit owner direction in Codex Desktop: “Forget the screenshot functionality and remove it”
DIFFERENCE: Removes emulator screenshot and save-state-preview cover capture, discovery, cache, folder grant, and settings controls from the prior revision.
REASON: The owner no longer wants screenshots used as box art.
PRESERVED_INTENT: Keep a clean local-first GBA library with useful covers, exact-content duplicate hiding, visible save/recent progress, one Pizza Boy launch path, and shared battery saves; preserve ROMs and emulator data.
CHANGED_ACCEPTANCE: No screenshot setting/source folder, screenshot-based cover selection, Pizza screenshot capture, John preview scanning, or screenshot cache remains active. Custom cover image import and Libretro box-art fetch remain. Removed feature state and its saved folder grant are retired on app start unless that same URI is still used by the active ROM or save folder.
AFFECTED: `RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java`, `CoverLoader.java`, Settings/help UI, project docs, and next signed release. No ROM, `.sav`, Pizza state, Pizza screenshot source files, or John GBA app data are to be deleted.
AUTHORITY_BASIS: Direct owner request, within delegated repository/device implementation scope.
EFFECTIVE_POINT: 2026-11-13
CURRENT_GOAL: ROM Shelf is a clean, local-first Android GBA library that organizes the owner's selected ROMs into a cover grid using manual custom art or the Libretro box-art catalog, hides exact duplicate ROM content without deleting originals, reports shared battery-save and Pizza quickstate/session recency, and launches every game through the selected Pizza Boy version with the shared battery-save folder. John GBA and its existing files remain installed but are not launch targets. ChatGPT/OAuth/AI matching and all emulator screenshot/state-preview artwork features are removed. Settings stays concise. Pizza Auto Load Stat/Auto Save Stat use automatic slot 1. Recent ranks the newest known shared `.sav` time or Pizza session-return quickstate estimate in Eastern Time; Android does not expose Pizza's exact private `.stat` mtime to ROM Shelf. Preserve ROMs, battery saves, emulator state, source screenshots, and existing custom covers; only ROM Shelf's generated screenshot cache and the unused screenshot-folder permission are cleaned as retired feature state.
OPEN_ASSUMPTIONS: Pizza Boy continues to update its automatic slot because the owner has Auto Save Stat enabled. Quickstate recency is a session-return estimate and does not establish an exact `.stat` timestamp.
NEXT: Remove screenshot behavior/UI, retire app-generated cache and folder permission without touching source media or emulator saves, build/sign/install the update, push the source, and record evidence.
