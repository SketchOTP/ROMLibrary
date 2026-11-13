---
name: authority-setup
description: Use when installing, upgrading, repairing, or backfilling Authority in a repository.
---

# Authority setup

Follow the pinned setup protocol in `.authority/setup/` and the migration workflow referenced by `.agent/ROUTES.md`. Detect the actual target tool first, preserve existing bytes, resolve the exact Authority version, build missing required runtime components, and verify target-host loading. Never infer an application goal or fabricate project history. Record limitations and retain partial state when a required gate is unobserved.
