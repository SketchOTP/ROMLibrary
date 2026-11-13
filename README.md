# ROM Shelf

ROM Shelf is a local-first Android GBA library. It organizes a user's `.gba` and `.zip` files into a cover grid, helps identify games and find artwork, hides exact duplicates without deleting source files, tracks battery-save presence and recent save activity, and launches games in a preferred emulator where its import intent is supported.

The project is in [`RomShelf/`](RomShelf/). It was imported from the frozen handoff source at commit [`2a021820e45c04029b454ba2b9b7bbedcd94294f`](https://github.com/SketchOTP/tymwayne/commit/2a021820e45c04029b454ba2b9b7bbedcd94294f). The prior 0.3.0 APK and signed 0.3.1 update are preserved under [`artifacts/rom-shelf/`](artifacts/rom-shelf/); provenance and limitations are recorded in [`RomShelf/PROJECT_HANDOFF.md`](RomShelf/PROJECT_HANDOFF.md). The GitHub repository is [SketchOTP/ROMLibrary](https://github.com/SketchOTP/ROMLibrary).

Authority 6.0 routing and the project atlas live in [`AGENTS.md`](AGENTS.md), [`.agent/`](.agent/), and [`.authority/OPERATIONS.md`](.authority/OPERATIONS.md). The APK and save files are not build inputs. Never put ROM files, save files, OAuth tokens, or signing keys in this repository.
