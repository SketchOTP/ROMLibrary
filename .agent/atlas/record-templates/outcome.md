# Outcome authoring template

NOT A LIVE RECORD. Fill only from actual evidence at execution. Retain the adopted recorder grammar.

```yaml
ENTRY_ID: <new UUID>
UTC: <actual execution timestamp>
ACTOR: <actual actor>
TYPE: <record type>
GOAL: <effective goal revision>
TASK: <directive alias or NONE>
STATUS: <record-specific status>
SUPERSEDES: <predecessor entry or NONE>
EVIDENCE: <source references or NONE>
SUMMARY: <factual bounded summary>
NEXT: <bounded next action or NONE>
LOCAL_ID: <O-* alias>
DIRECTIVE_REVISION: <exact D-* version>
ATTEMPT: <id>
STAGE: <WORKER_REPORT / VERIFICATION / CONDUCTOR_DISPOSITION>
REPOSITORY_BASIS: <repo, worktree, commit or filesystem snapshot, dirty digest>
CHANGED_PATHS: <actual paths>
OBSERVED_RESULT: <what actually happened>
CRITERIA:
  - ID: <AC-id>
    RESULT: <MET / PARTIAL / NOT_MET / NOT_EVALUATED>
    EVIDENCE: <reference>
CHECKS:
  - METHOD: <exact command or inspection>
    STATUS: <PASSED / FAILED / NOT_RUN / NOT_APPLICABLE / BLOCKED>
    ENVIRONMENT: <actual basis>
    EVIDENCE_LEVEL: <E0-E5, justified per claim>
    ARTIFACT: <path or source>
LIMITATIONS: <remaining risks and counterevidence>
LESSONS: <candidate ids>
DISPOSITION: <Conductor decision or PENDING>
```
