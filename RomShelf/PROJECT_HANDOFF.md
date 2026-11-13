# ROM Shelf project handoff

## Owner goal

ROM Shelf is a clean, intelligent Android GBA game library that automatically organizes ROMs into a beautiful cover-art grid, uses AI to identify games and find accurate artwork, eliminates duplicates, tracks save progress and recent activity, and launches any game seamlessly in the user's preferred emulator with its existing save—creating one simple, unified gaming experience.

This charter comes from the owner in the Codex conversation and is authoritative for project direction. The linked Notion handoff below supplies historical evidence and proposed operating guidance. Its embedded copy-ready prompt is source material, not a new authorization to modify unrelated repositories, publish, distribute ROMs, or use credentials.

## Source and artifact provenance

- Canonical handoff: [ROM Shelf 0.3.0 project handoff](https://app.notion.com/p/417833cb27ff81a285abd8a8e4cec453?pvs=207), last edited 2026-11-13 13:41:41 UTC; Notion verification metadata says `unverified`.
- Frozen source: private repository [`SketchOTP/tymwayne`](https://github.com/SketchOTP/tymwayne), branch `rom-shelf-apk-build`, commit [`2a021820e45c04029b454ba2b9b7bbedcd94294f`](https://github.com/SketchOTP/tymwayne/commit/2a021820e45c04029b454ba2b9b7bbedcd94294f).
- Imported project: this `RomShelf/` tree, extracted from the commit's `rom-shelf/source.tar.gz`. The archive's Git blob SHA-1 is `6bb2d45480d4892b714d1133e4abfd318b06316e`; its SHA-256 is `120bb417435103f55fa1148f48355399f1b5e083402ae1a884038fd781b9162f`.
- Preserved APK: [`artifacts/rom-shelf/apk/ROM-Shelf-0.3.0-install-reissued.apk`](../artifacts/rom-shelf/apk/ROM-Shelf-0.3.0-install-reissued.apk), SHA-256 `2e653acd5b786a169bacc7ec80df89dd21e543dda4b34b6ab8007c19e8588e26`. It declares package `com.animus.romshelf.launcher`, version `0.3.0` / code `3`, min SDK 26 and target SDK 35. Declared v1/v2/v3 signer certificate SHA-256 is `b094cd94d6975c3c62c467658d38523091c638068b9fb3b06d0d2998a19d2456`, matching the handoff's expected fingerprint. Static signer declarations alone do not prove authenticity; verify the APK with Android's `apksigner` before redistribution.
- Signed update: [`artifacts/rom-shelf/apk/ROM-Shelf-0.3.1.apk`](../artifacts/rom-shelf/apk/ROM-Shelf-0.3.1.apk), package `com.animus.romshelf.launcher`, version `0.3.1` / code `4`, SHA-256 `954a1c6e082b799d6e24f0ced6439e771d45651c0bdd33acd9ae2e54860d0085`. Android `apksigner` verified v2/v3 and the expected certificate SHA-256 `b094cd94d6975c3c62c467658d38523091c638068b9fb3b06d0d2998a19d2456`. It was installed over the Pixel's 0.3.0 package and its launcher activity started successfully.
- Unsigned build input: [`artifacts/rom-shelf/apk/ROM-Shelf-0.3.1-unsigned-aligned.apk`](../artifacts/rom-shelf/apk/ROM-Shelf-0.3.1-unsigned-aligned.apk), SHA-256 `77681fa089d63df384238997673e475d9d6b436da5f3eb465702870ccfed3acb`.
- At takeover, the Pixel's 0.3.0 APK bytes matched the supplied APK. The 0.3.1 update has since installed in place; no uninstall or ROM/save write was performed.

## Handoff claims versus current evidence

The Notion page describes the source as a six-class native Java app with Storage Access Framework (SAF) library selection, `.gba`/`.zip` scanning, first-`.gba` ZIP hashing, exact-content deduplication, shared `.sav` detection and import/export/backup, emulator-specific `ACTION_VIEW` routing, optional ChatGPT OAuth/AI metadata, and Libretro/manual art fallback. These are implementation descriptions to review and qualify, not blanket runtime acceptance.

Source inspection confirms corresponding code paths exist. It does not prove actual ROM identification, cover accuracy, save compatibility, emulator loading, OAuth eligibility, Responses API behavior, or safety under large libraries. Those remain explicit acceptance checks. The source README itself warns that emulator intent handling and shared battery-save compatibility require device validation. A ROM `.sav` is not an emulator save state.

## Takeover review

- Reviewed all six Java classes, the manifest, Gradle configuration, and the source README. The imported project has no unit or instrumentation tests. The release build's `lintVitalRelease` task passed.
- Tightened OAuth plan authorization to match an exact whitespace-delimited `chatgpt.tokens.use.direct` scope, rather than a substring. The model resolver now chooses the first model marked `visibility: list` in the signed-in account's current catalog, preserving server order. This matches the current [Sign in with ChatGPT registration and scope contract](https://developers.openai.com/siwc/token-sharing-open-source/sign-in) and [models and inference flow](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference). The current [Responses web-search tool](https://developers.openai.com/api/docs/guides/tools-web-search) accepts `type: web_search`, as used by this project. The live account/API path is still untested; no account was connected and no plan usage was incurred.
- **Security finding at takeover (superseded below):** the original `CoverLoader.safeUrl()` rejected obvious local/numeric URLs but did not check DNS answers or pin a validated public address across HTTPS and redirects. The follow-up source review replaced that boundary with a strict exact-host HTTPS allowlist and rechecks redirects; see the follow-up fixes section.
- `MainActivity.launchGame()` sends `ACTION_VIEW` with a SAF content URI to an explicitly selected emulator and catches immediate launch failures. The app cannot tell from that call whether the emulator actually loaded the selected ROM. John GBA/Pizza Boy launch, save pickup, and shared `.sav` compatibility remain unverified.
- `FileStore` scans the user-selected SAF tree, hashes raw `.gba` bytes or the first `.gba` member in a `.zip`, and leaves duplicate files in place. Hash caching uses document URI, size, and modified time; providers that change bytes while reusing both metadata values can make a cached digest stale. The scan cap and cache behavior still need fixture/device coverage.
- Local Gradle 8.13 tests and `assembleRelease` passed after source fixes. The 0.3.1 APK was signed outside Gradle with the original owner PKCS#12 key, then `apksigner`, ZIP integrity, package metadata, and `zipalign` checks passed. The keystore and password were kept out of the repository and logs.
- The Pixel accepted `adb install -r` for 0.3.1/versionCode 4 over 0.3.0/versionCode 3. The installed APK's signer matches the expected fingerprint; `MainActivity` returned `Status: ok` and the process remained alive. No uninstall or clean reinstall occurred. Emulator launch, ROM loading, save compatibility, OAuth, and AI requests remain untested.

## Follow-up fixes after source review — 2026-11-13

The following source changes supersede the applicable open code findings above. They began as working-source changes on top of the preserved frozen archive and were pushed to [SketchOTP/ROMLibrary](https://github.com/SketchOTP/ROMLibrary) on `main` in commit `fa60e8107f4276d99858285c5d646d31b2bba136`; the archived source and supplied APK remain unchanged.

- **Save target identity:** transfer, import, and restore now pass the exact selected `DocumentFile` through backup and write. They no longer re-resolve a same-named root file when the selected save is nested. New files are deleted after failed creation/write verification, and backups are staged, synced, and assigned unique names before commit.
- **Artwork fetch boundary:** AI image URLs must use HTTPS on the exact `thumbnails.libretro.com` host. Redirects are checked against the same policy. Arbitrary AI image hosts are rejected; catalog or manual art remains available. Source links are only opened after a user tap.
- **Manual cover replacement:** cover imports are size-limited and validated in a staged file before atomic replacement, preserving the prior cover if validation or staging fails.
- **ZIP work bound:** scan caps ROM bytes at 96 MiB, expanded data before the GBA member at 16 MiB, preceding entry traversal at 256 entries, and entry names at 4096 characters. Directory-named entries are explicitly drained under the prelude cap to prevent implicit unbounded decompression.
- **Artwork state:** a title change or AI result without an approved image clears the old AI image URL/source and cached image, allowing catalog fallback.
- **Digest freshness and identity:** full scans recompute content hashes rather than reusing URI/size/mtime cache entries. When readable, the ROM SHA-256 now keys title/version, emulator choice, AI status, and cover state; existing URI-based settings are migrated.
- **Save folders:** Settings now supports independent shared, John GBA, and Pizza Boy save trees. Each is scanned and shown independently; export, import, and backup restore are folder-specific. The app still cannot access Android-private emulator folders unless the provider grants access, nor can it make an emulator load an externally supplied ROM or save.
- **Signed update:** the reissued backup's PKCS#12 certificate matched the expected fingerprint before signing. The 0.3.1 update was signed with the matching key, verified, and installed successfully. Gradle and test outputs remain unsigned by design; the signed artifact is a separate local release file.
- **Still open:** no controlled ROM/save fixture or real John GBA/Pizza Boy workflow was exercised. Persistent SAF grants, actual save-provider behavior, emulator ROM loading/save pickup, OAuth, and live AI calls still need owner-controlled acceptance. No ROM or save data was modified or uploaded during this update.

## Project guardrails carried forward

- ROM and save bytes stay local; do not upload them, bundle them into builds, or distribute games.
- Never delete source ROM files to deduplicate. Do not overwrite a save except after a user action and verified backup.
- Do not read Codex CLI credentials or embed API keys. ChatGPT AI remains optional and requires an explicit user sign-in and plan-use consent.
- Do not promise Android private-folder access or cross-emulator progress until both the selected folder provider and emulator save behavior are verified.
- Never place the owner's private signing key or password in this repository, Notion, build logs, or workflow artifacts. A release build is not an installable release until it is signed with the established owner key and independently verified.

## Takeover checklist

1. Keep reproducing the unsigned Gradle build from this imported source with the pinned toolchain, then sign outside the build with the existing owner key.
2. Review the strict remote-art host allowlist if artwork sources need to expand.
3. Verify library selection, persistent SAF grants, nested ROM scanning, exact duplicate behavior, ZIP handling, and save detection using owner-controlled fixtures.
4. On the Pixel, test real emulator launch and shared `.sav` behavior only with a user-approved test ROM/save; preserve all existing user data.
5. Test ChatGPT OAuth and AI only after current official interfaces and account consent are confirmed; do not treat this handoff's claims as live API verification.
6. Keep the private signing key and password out of Git, build logs, and workflow artifacts. Do not generate a replacement key or claim update compatibility under a different signer.

## Historical owner steering — 2026-11-13 (superseded by 0.3.9)

This section supersedes the original charter's optional AI matching and preferred-emulator language. The full effective revision is recorded in `.agent/GOAL.md`.

- ROM Shelf is Pizza Boy only for launches. One app-wide Pizza Boy version is used for every game; installed Pizza Boy GBA variants are fallback choices. Legacy per-game emulator choices are cleared. John GBA remains installed and its `GBA/save` directory is the shared battery-save location; this does not imply John is a supported Shelf launch target.
- ChatGPT OAuth and AI title/art matching have been removed from the app. Startup clears legacy local token preferences/keystore alias and old AI image URL references; it preserves cached cover files and saved game metadata. Libretro cover download, manually installed art, and per-game screenshot/cover import remain.
- Settings is reduced to ROM and one John/Pizza shared save-folder link, Pizza Boy version, screenshot-cover toggle/source, rescan, and help. ROM Shelf reads and writes the one canonical folder even if older settings contain separate emulator folder URIs. Game cards show the save file's last-modified time in `America/New_York`.
- Release candidate 0.3.6/versionCode 9 builds and signs with the original owner certificate. The Pixel was disconnected before it could be installed. The previously installed 0.3.5 was device-verified: tapping Pokémon Legends Delta opened Pizza Boy GBA Pro 3.9.2, and Pizza Boy logged the ROM load plus battery-save read through the John GBA `GBA/save` document tree.
- Before the Pixel disconnected, the shared folder contained 33 unique nonempty `.sav` files. Newer source content won duplicate-name collisions; Pizza Boy won two collisions. The empty placeholder was skipped, seven John `.sav0` sidecars and emulator save-state files were retained, and all output hashes matched the newest nonempty source inputs.
- **Still pending:** reconnect the Pixel, compare live `.sav` modification times (especially `monomyth_v1_1.sav`) against all current saves and Pizza Boy's inactive private save copies, inspect Pizza Boy's screenshot/preview output folder, install 0.3.6 over the same signed package, and verify the Pizza-only menu, Eastern-time label, and screenshot source/cache behavior. Do not change saves during the timestamp check.

Historical sections above describe the source state and claims at takeover. They remain records, not current product direction.

## Follow-up — Pizza quickstate recency — 2026-11-13

The owner's current focus is to launch every title through Pizza Boy and treat the newest usable Pizza state or shared John/Pizza battery save as the most recent progress.

- ROM Shelf 0.3.7/versionCode 10 adds completed Pizza launch-session return times to Recent ordering, compares that activity time with the shared `.sav` timestamp, and labels the source under each title in Eastern Time. Details/help state that a session timestamp is only an activity estimate.
- Every game card reaches the same `launchGame()` / explicit Pizza Boy intent path. On the Pixel, a Monomyth card tap foregrounded Pizza Boy Pro and passed its selected ROM content URI. Pizza reported that the same ROM was already running, so that final tap did not force a fresh emulator reload.
- Pizza Boy Pro's Save settings were observed with **Auto Load Stat** on, **Auto Save Stat** on, and automatic slot **1** selected. Five automatic slot-1 states were present; four had exact matching shared `.sav` files and newer state timestamps at inspection, while one had no battery save. The Monomyth automatic state timestamp was rewritten during this verification; it cannot be used as evidence of when the owner last played. A pre-change copy of the Monomyth state and preview remains outside the repository in local recovery storage.
- The Pixel installed 0.3.7 over the existing app. The live library remained visible with 49 games, 31 battery saves, and three exact duplicates hidden. Returning from Pizza displayed a `Pizza session` time for Monomyth and placed it first in Recent sorting.
- `./gradlew testDebugUnitTest assembleRelease lintVitalRelease` passed: eight unit tests, release assembly, and release lint. The signed APK was verified with the established certificate and zip alignment; in-place installation succeeded.
- **Platform limit:** Android prevents ROM Shelf from selecting/reading Pizza Boy's `Android/data` tree. Therefore ROM Shelf cannot directly compare a live `.stat` modification time against `.sav` or prove which file is newer. Pizza's own automatic slot restores/updates the state; ROM Shelf's session timestamp improves recency but is not a direct state-file timestamp. A direct comparison requires Pizza Boy to expose state timestamps through shared storage or an integration API. Do not claim exact quickstate recency from the current app-side timestamp.

Evidence: `.authority/setup/rom-shelf-quickstate-recency-20261113.json`.

## Follow-up — quickstate-inclusive Recent — 2026-11-13

ROM Shelf 0.3.8 treats Pizza's automatic quickstate activity as a recent-progress marker alongside the shared battery `.sav` timestamp. When a game launched from ROM Shelf returns to it, the app records a Pizza quickstate activity estimate and sorts by the newest available timestamp across that estimate, the shared `.sav`, and retained legacy Pizza-session records. Cards and details identify which marker won.

This marker is recorded at Pizza-session return because Auto Save Stat is enabled on the Pixel; it is not a read of the `.stat` file's actual modification time. Android does not allow ROM Shelf to read Pizza's app-specific `Android/data` state directory, even with the all-files permission, so an exact live `.stat` versus `.sav` comparison remains unavailable. The Pixel's direct Pizza auto-state files are preserved/managed by Pizza Boy; ROM Shelf does not copy or overwrite them.

Release evidence: `.authority/setup/rom-shelf-quickstate-recency-0.3.8-20261113.json`.

## Owner refinement — remove emulator screenshot covers — 2026-11-13

ROM Shelf 0.3.9 removes Pizza screenshot and John state-preview artwork features. Settings no longer requests a screenshot source folder or toggles screenshot covers; game actions no longer advertise screenshot import; launch/return tracking remains only for Pizza session and quickstate activity recency. Automatic screenshot detection, image copying, and `.state.img` cover caches are removed. Startup clears ROM Shelf's generated screenshot cache and screenshot settings, and releases the saved SAF grant for the old screenshot source folder unless that same URI is still used for the ROM or save folder. Pizza Boy's source screenshots, emulator states, ROMs, existing custom cover images, and shared `.sav` files remain outside that cleanup.

Custom cover import and Libretro box-art fetching remain. Recent still uses the shared `.sav` time and the Pizza session-return quickstate estimate; the exact `.stat` mtime remains unavailable to ROM Shelf.

Release evidence: `.authority/setup/rom-shelf-no-screenshots-0.3.9-20261113.json`.
