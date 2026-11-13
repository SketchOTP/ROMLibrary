# Directive authoring template

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
LOCAL_ID: <existing or new stable D-* alias>
MILESTONE: <id>
PARENT: <directive or user request>
BRANCH: <id or main>
ORIGIN_OUTCOME: <id or NONE>
OBJECTIVE: <observable result>
WHY: <contribution to goal>
IN_SCOPE: <bounded scope>
OUT_OF_SCOPE: <explicit exclusions>
WRITE_OWNERSHIP: <paths and shared resources>
DEPENDENCIES: <ids and interface contract>
SOURCE_REFS: <research, code revisions, decisions>
RELEVANT_LESSONS: <ids or none found within searched scope>
RETRIEVAL_CONFIDENCE: <ADEQUATE / UNCERTAIN / INSUFFICIENT>
ACCEPTANCE:
  - ID: <AC-id>
    REQUIREMENT: <observable criterion>
    METHOD: <check>
    REQUIRED_EVIDENCE: <appropriate level>
BUDGET: <authorized time/cost/retries or explicitly unobservable dimension>
STOP_WHEN: <failure, budget, permission or uncertainty condition>
REJOIN_OR_NEXT: <id or UNKNOWN>
READIED_BY: <Conductor event or NOT_READY>
```
