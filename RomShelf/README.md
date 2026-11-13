# ROM Shelf 0.3.1 — Unified GBA launcher

A native Android ROM-cover library built with Java and AndroidX DocumentFile. It does not include or download games.

## Core 0.3.0 features

- **Three selectable `.sav` folders**: a shared location, John GBA, and Pizza Boy. ROM Shelf scans each selected tree, shows saves separately, and offers folder-specific export, import, backup, and restore. Save modification times estimate activity; they are not play telemetry. Android may block emulator-private folders, and save states are not compatible battery saves.
- **Tap cover to launch** using a remembered per-game emulator preference, or configured global default; **long press** for emulator choice, artwork, title, save export/import, and details. Supports candidate package IDs for John GBA/Lite and Pizza Boy GBA/Free. Launch uses a SAF `content://` URI with read grant and `ACTION_VIEW`; **some emulator versions may reject it or may open without loading**. This requires device tests; neither emulator's loading behavior is under ROM Shelf control.
- **ChatGPT OAuth** using OpenAI's documented open-source dynamic-client browser flow (`dynamic_agent_client`, PKCE S256, loopback callback, verified OIDC ID token with JWKS, scoped plan consent, encrypted AES-GCM Android Keystore storage, token refresh, disconnect/revoke). Does not read or copy Codex CLI `auth.json` or ask for a user's ChatGPT password. The official flow is documented for supported open-source clients, and an individual private APK's eligibility cannot be guaranteed. AI is optional. The user's plan controls usage/credits.
- AI requests comply with ChatGPT plan OAuth inference (`store:false`, `stream:true`, available model discovery), and request web search when permitted to research names/art. Generated title metadata is cached; original filenames remain unchanged. Artwork also falls back to Libretro's catalog or manual selection.
- SHA-256 content-based deduplication without deleting files; thumbnails and metadata cached locally.
- **Installation/signature:** 0.1.0 and 0.2.0 had different CI debug-signing certificates, preventing updates. 0.3.0 changes the Android application ID to `com.animus.romshelf.launcher`, avoiding collision and initially installing **alongside** old versions. From 0.3.0 onward, use the private signing key generated once and backed up separately; do not rebuild or redistribute using new debug keys if you intend in-place upgrades.

## Setup
1. Install the signed 0.3.1 update. In Settings, choose the folder with `.gba`/`.zip` ROMs and any accessible shared, John GBA, or Pizza Boy `.sav` folders. Some Android `/Android/data/` folders are not selectable due to platform restrictions.
2. Choose the default installed emulator; or tap a cover and choose an emulator for that title. Long-press to change it later.
3. Optionally, Settings → ChatGPT AI → Continue with ChatGPT. Complete consent in the browser. The app locally validates the token before storing it. Account connection may be unavailable depending on eligibility or plan/workspace settings.
4. Optionally perform AI matching on one game or up to 12 new games. Only filename and current display metadata are sent, **not ROM files or battery saves**.
5. Test one game to confirm the chosen emulator accepts the URI and loads the battery save. ROM Shelf cannot force resume from proprietary emulator save states.

## Review fixes in the working source

This working source is version 0.3.1 (version code 4), intended to update an existing 0.3.0 install with the same signing certificate. The local build remains unsigned until the established owner key is available.

- Save transfers, imports, and restores back up and write the exact selected SAF document, even when it is nested under the selected tree. New saves are removed if creation or verification fails; backups use unique staged files.
- AI artwork downloads are restricted to HTTPS on the exact `thumbnails.libretro.com` host, including every redirect. Other AI-suggested image hosts fall back to the catalog or manual cover selection.
- Manual cover imports are size-limited, validated in a temporary file, and atomically committed so a rejected replacement leaves the old cover intact.
- ZIP scanning caps entry traversal, expanded metadata before the first GBA member, and ROM bytes. Full ROM scans recompute content hashes instead of trusting URI/size/mtime cache keys.
- Per-game metadata, emulator choice, AI state, and covers use content-based ROM identity where a digest is available, with migration from existing URI-based settings.
- Seven local JVM regression tests cover URL policy, bounded ZIP handling, identity stability, and atomic cover behavior. They do not replace real SAF/provider and emulator tests.

## Security / limitations
- No embedded API key, no CLI token extraction, no password collection. OAuth tokens are kept in encrypted app-private storage backed by Android Keystore; the host UUID is nonsensitive app-private data.
- The app's sign-in uses an ephemeral loopback listener (`127.0.0.1`) with random `state`, PKCE verifier, nonce, and signature-verified ID token. A successful identity login without `chatgpt.tokens.use.direct` scope cannot access plan inference.
- The OAuth runtime uses OpenAI's direct public Responses API; web search and model availability depend on account permissions.
- The first 0.3.0 install uses a *different Android application ID*: old local settings do not automatically migrate, and old versions remain separately installed until removed. ROMs and accessible shared .sav files are not removed when uninstalling old app versions.
- Android 11+ document providers may restrict accessing another app's private folders. External ROM intents are best-effort and may require in-emulator import.

## Build
The isolated GitHub Actions build compiles an **unsigned release** APK using Android SDK 36 and Gradle 8.13, aligns it, and exports `apksigner.jar` to an artifact. A separate offline signing step with a user-owned stable signing key is needed for installable release output. Keep that keystore and password secure for 0.4.0 and newer.
