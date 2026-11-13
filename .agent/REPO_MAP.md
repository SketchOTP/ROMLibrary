# Repository map navigation

See [Atlas navigation](atlas/README.md) for bounded code and journey lookup. The
runtime maintains the eligible-file inventory and rebuildable snapshot under
`atlas/generated/`; use [operations](../.authority/OPERATIONS.md) for search and recovery.

## Project source

- [`RomShelf/`](../RomShelf/) — native Android/Java GBA library project and its source-level build guide.
- [`RomShelf/PROJECT_HANDOFF.md`](../RomShelf/PROJECT_HANDOFF.md) — owner goal, source provenance, current evidence, and project acceptance gaps.
- [`artifacts/rom-shelf/`](../artifacts/rom-shelf/) — preserved APK/source provenance plus signed ROM Shelf release artifacts.
- [`RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java`](../RomShelf/app/src/main/java/com/animus/romshelf/MainActivity.java) — library UI, plus-menu ROM/art intake, SAF pickers, Pizza-only routing, title/cover actions, and save recency.
- [`RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java`](../RomShelf/app/src/main/java/com/animus/romshelf/FileStore.java) — SAF ROM import, content hashing/deduplication, newest matching save selection, and guarded save import/backup/restore.
- [`RomShelf/app/src/main/java/com/animus/romshelf/Game.java`](../RomShelf/app/src/main/java/com/animus/romshelf/Game.java) — stable game identity and newest battery-save/quickstate/session activity timestamp selection.
- [`RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java`](../RomShelf/app/src/main/java/com/animus/romshelf/CoverLoader.java) — Libretro/custom cover loading, persistent artwork-folder selection, and verified image import.

