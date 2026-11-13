# Atlas navigation contract

The Project Atlas provides Journey, Code and Both views. Journey covers goals,
directives, outcomes, decisions, branches and lessons. Code covers eligible files,
responsibilities and evidenced connections. Bridges explain which work affects code.

Search exact IDs/paths/symbols first, then filtered lexical results, then bounded
neighbors. Read authoritative source excerpts for material decisions. Keep original
IDs stable; numeric browse addresses can move. An inferred link is not a verified
runtime dependency. Every result must expose source, snapshot and freshness.

The repo-local runtime in `.authority/runtime/authority.py` implements bounded
lexical search, exact ID/path/symbol lookup, refresh, and the visual viewer. Use
the commands in `.authority/OPERATIONS.md`; if the index is stale or unavailable,
search the authoritative ledgers and live source directly. A search miss does not
prove absence.

The record forms are in `record-templates/`; the JSON schemas are proposed
contracts in `schemas/`. Files under `examples/` are synthetic fixtures and are
explicitly marked in the Code Atlas; they are not ROM project history or runtime
evidence.

Canonical records remain in the existing Markdown ledgers. Accepted manual
relationship/identity assertions append to `RELATIONSHIPS.jsonl` when implemented.
Disposable indexes/views live in `.agent/atlas/generated/`. Their loss must
not erase history. Do not create synthetic project records in this repository.

