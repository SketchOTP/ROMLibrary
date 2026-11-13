# Authority 6.0 operations

This repository contains a local implementation of the required Authority Atlas
runtime. It implements the requested workspace functions; it is not a claim that
an official Authority governor release exists.

## Daily use

From the repository root:

```sh
python3 .authority/runtime/authority.py status
python3 .authority/runtime/authority.py search --layer journey --query 'D-A6-SETUP-20261113'
python3 .authority/runtime/authority.py search --layer code --query '.authority/runtime/authority.py'
python3 .authority/runtime/authority.py search --layer both --query 'refresh_snapshot'
python3 .authority/runtime/authority.py context --id 'D-A6-SETUP-20261113' --depth 1
python3 .authority/runtime/authority.py refresh
python3 .authority/runtime/authority.py audit
```

Search and status refresh hashes before returning results. Search is bounded to
50 results and shows the snapshot, source, hash, evidence class, and fallback
guidance. A no-hit result is not evidence that a record or relationship is
absent; use `rg` against canonical ledgers and inspect live code.

## Visual Atlas

```sh
python3 .authority/runtime/authority.py serve
```

Open `http://127.0.0.1:4317`. The viewer is read-only, loopback-bound, and uses
the same SQLite snapshot and API as CLI search. Use Ctrl+C to stop it. Do not
bind it to a LAN interface without a separate, explicit task authorization.

## Record writing

Use `python3 .authority/runtime/authority.py record --help` to see the bounded
append interface. It writes complete, idempotent Markdown records under the
canonical ledgers, takes an OS file lock, flushes to disk, and refuses to append
after an incomplete final line. It never rewrites earlier bytes. Correct a prior
record by appending a new record that links its predecessor.

## Freshness and recovery

At turn start and after meaningful edits, run `refresh`; run it again before a
handoff. The Scribe role also checks source changes and marks unsupported dynamic
relationships as unknown. No automatic hook is installed. The current fallback
is an explicit refresh at task/turn boundaries.

The generated cache is disposable. To rebuild it, remove only
`.agent/atlas/generated/` and run `refresh`. Keep canonical ledgers,
`.agent/atlas/RELATIONSHIPS.jsonl`, and `.agent/atlas/REPOSITORY.json`; they carry
history and stable identities. Setup evidence and receipts live under
`.authority/setup/` and are not copied into public exports.

## Coverage limits

The scanner inventories regular files under this root, excludes VCS/build/cache
directories and generated Atlas data, and does not follow symlinks. Files over
2 MiB and binary assets are hashed and listed but not text-indexed. Semantic edge
extraction is limited to Python AST imports and explicit local Markdown links.
Dynamic calls, runtime behavior, plugin discovery, and unparsed configuration
relationships remain unknown until directly evidenced. Model selection is left
to the actual Codex host; this repository does not set model overrides or global
hooks.
