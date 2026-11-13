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
