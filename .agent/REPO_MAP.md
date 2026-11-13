# Repository map navigation

See [Atlas navigation](atlas/README.md) for bounded code and journey lookup. The
runtime maintains the eligible-file inventory and rebuildable snapshot under
`atlas/generated/`; use [operations](../.authority/OPERATIONS.md) for search and recovery.

## Project source

- [`RomShelf/`](../RomShelf/) — native Android/Java GBA library project and its source-level build guide.
- [`RomShelf/PROJECT_HANDOFF.md`](../RomShelf/PROJECT_HANDOFF.md) — owner goal, source provenance, current evidence, and project acceptance gaps.
- [`artifacts/rom-shelf/`](../artifacts/rom-shelf/) — preserved APK/source provenance plus signed ROM Shelf release artifacts.
- [`RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java`](../RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java) — UI, SAF selection, Pizza-only routing, save actions, and quickstate/session recency estimates.
- [`RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java`](../RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java) — SAF scanning, ROM hashing/deduplication, save discovery, and guarded save import/backup/restore.
- [`RomShelf/app/src/main/java/com/animus/romshelf/Game.java`](../RomShelf/app/src/main/java/com/animus/romshelf/Game.java) — stable game identity and newest battery-save/quickstate/session activity timestamp selection.
- [`RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java`](../RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java) — catalog/manual artwork, screenshot matching, and local cover caching.

