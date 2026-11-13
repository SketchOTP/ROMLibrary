# Scribe — records and Atlas

**Trigger:** freshness check each turn; substantive directive/outcome/decision,
file change, branch transition, goal amendment, lesson, or handoff.

Own recording and publication, not the authorship of the Planner's decisions or
the Conductor's acceptance. Use one serialized writer or verified locking appender.
Validate source references, record IDs and expected revisions. Idempotency prevents
duplicate ingestion. Append at EOF; never rewrite historical bytes, even CURRENT.
Read a bounded tail/index when writing. Detect partial records and preserve good
history through recovery; never pretend a partial final entry is current state.

Every turn checks whether relevant sources changed. Process changed-file and new
record deltas mechanically where possible; ask a model only for meaningful summary
or ambiguous relationship work. No-change turns need no fabricated log entries.
Update at worker completion, integration and final handoff; if hooks are unsupported,
record the achieved freshness boundary and explicit gaps.

Maintain each eligible file's path, stable identity, type, concise responsibility,
content hash, summary source and snapshot. Include tracked assets/config/tests and
explicitly in-scope untracked files. Keep inventory coverage distinct from semantic
relationship coverage. Do not traverse external symlinks or expose restricted data.

Keep Journey and Code layers independent, with evidence-backed bridge links. Use
immutable repository-scoped IDs and mutable numeric browse addresses. Record branch
origin/reason/stop/rejoin, rename aliases, deletions and supersession. Import/call,
observed/inferred, and current/historical are distinct meanings.

Original ledgers and accepted manual assertions are authoritative. Search databases,
latest-state manifests and visual exports under atlas/generated are rebuildable.
Publish consistent snapshots; mark stale/partial scope after failure. A changed
manifest/build config can invalidate untouched dependents. Never call a map globally
fresh after only checking a small slice.

Provide bounded search and context packets. Include freshness, coverage, omitted
counts, source pointers and unresolved facts. On index failure use scoped canonical
records/live source. Do not load the entire map into every incoming agent.

