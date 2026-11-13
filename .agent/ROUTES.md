# Task and authority router

Choose the primary workflow by the actual task. Load only relevant role modules;
the core contract always applies. Markdown links are retrieval pointers, not proof
that the host loaded a file.

| Task | Workflow | Specialist when needed |
|---|---|---|
| Scoped implementation | [Implement](../.authority/procedures/implement.md) | Implementer |
| Bug or failed check | [Debug](../.authority/procedures/debug.md) | Researcher for unresolved causes |
| Prior art or uncertain external facts | [Research](../.authority/procedures/research.md) | [Researcher](authority/20-researcher.md) |
| Milestone, dependency, or directive | [Plan](../.authority/procedures/plan.md) | [Planner](authority/30-planner.md) |
| Correctness or acceptance | [Review](../.authority/procedures/review.md) | [Verifier](authority/90-verifier.md) |
| Delegation, integration, reprioritization | [Coordinate](../.authority/procedures/coordinate.md) | [Conductor](authority/10-conductor.md) |
| Release, migration, external effects | [Release](../.authority/procedures/release.md) | [Security and memory](authority/95-security-memory.md) when triggered |
| Authority setup or historical import | [Migrate](../.authority/procedures/migrate.md) | [Scribe](authority/40-scribe.md) |

## Conditional overlays

| Trigger | Load |
|---|---|
| Records, identity, map publication, restart packet | [Scribe](authority/40-scribe.md) |
| Cost review, costly fan-out, repeated failure | [Efficiency reviewer](authority/50-efficiency.md) |
| New branch, milestone, repeated failure, cumulative divergence | [Drift analyst](authority/60-drift.md) |
| Feasibility, goal wording, scope, or acceptance changes | [Goal keeper](authority/70-goalkeeper.md) |
| Supported reusable finding or failure signature | [Learner](authority/80-learner.md) |
| Credentials, private memory, cross-project export, destructive effects | [Security and memory](authority/95-security-memory.md) |

Mixed work loads all applicable overlays. Do not invent permission or skip an
applicable constraint. A small edit does not require a role meeting. Ten role
profiles are installed as reusable responsibilities; the host may execute them
sequentially. Model routing, parallel workers, hooks, and independent review are
claims only when observed.

Shared workflow bodies live in `.authority/procedures/` to avoid native discovery
collisions. The logical workflow-to-file mapping is recorded in
`.authority/setup/setup-20261113T135057Z/adapter-plan.json`.
