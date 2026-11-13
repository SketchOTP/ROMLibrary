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

The following source changes supersede the applicable open code findings above. They are local working-source changes on top of the preserved frozen archive; the archived source and supplied APK remain unchanged.

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
