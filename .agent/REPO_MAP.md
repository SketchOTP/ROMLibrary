# Repository map navigation

See [Atlas navigation](atlas/README.md) for bounded code and journey lookup. The
runtime maintains the eligible-file inventory and rebuildable snapshot under
`atlas/generated/`; use [operations](../.authority/OPERATIONS.md) for search and recovery.

## Project source

- [`RomShelf/`](../RomShelf/) — native Android/Java GBA library project and its source-level build guide.
- [`RomShelf/PROJECT_HANDOFF.md`](../RomShelf/PROJECT_HANDOFF.md) — owner goal, source provenance, current evidence, and project acceptance gaps.
- [`artifacts/rom-shelf/`](../artifacts/rom-shelf/) — preserved 0.3.0 install APK and exact source archive; see its provenance note before using either artifact.
- [`RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java`](../RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java) — UI, SAF selection, emulator routing, save actions, and opt-in AI actions.
- [`RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java`](../RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java) — SAF scanning, ROM hashing/deduplication, save discovery, and guarded save import/backup/restore.
- [`RomShelf/app/src/main/java/com/animus/romshelf/ChatGPTAuth.java`](../RomShelf/app/src/main/java/com/animus/romshelf/ChatGPTAuth.java) — browser OAuth, token validation, encrypted local storage, refresh, and disconnect.
- [`RomShelf/app/src/main/java/com/animus/romshelf/AiResolver.java`](../RomShelf/app/src/main/java/com/animus/romshelf/AiResolver.java) and [`CoverLoader.java`](../RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java) — optional metadata/artwork retrieval and local cover caching.

