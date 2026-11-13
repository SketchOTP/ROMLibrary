---
name: authority-atlas
description: Use when searching project history, locating code ownership, refreshing repository maps, or opening the Authority visual atlas.
---

# Authority Atlas

Use the repo-local Authority runtime in `.authority/runtime/authority.py`.

- Run `python3 .authority/runtime/authority.py status` for the current snapshot and coverage.
- Run `python3 .authority/runtime/authority.py search --layer journey|code|both --query TEXT` for bounded lexical retrieval.
- Run `python3 .authority/runtime/authority.py refresh` after edits or at handoff; it hashes all eligible files and regenerates only changed summaries/index rows.
- Run `python3 .authority/runtime/authority.py serve` to open the local Journey / Code / Both viewer at `http://127.0.0.1:4317`.

The canonical ledgers under `.agent/` remain authoritative. The SQLite index and generated snapshot are rebuildable. A search miss does not prove a record or dependency is absent.
