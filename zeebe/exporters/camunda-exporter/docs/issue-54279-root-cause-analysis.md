# Root cause analysis: stale `incident=true` in Operate list view (#54279)

Symptom: finished (canceled **or** completed) process instances keep `incident = true` in
`operate-list-view-*` although their `operate-incident-*` documents are `RESOLVED`, and the
post-importer queue contains `CREATED`/`RESOLVED` pairs for the affected instances. Observed in
SUPPORT-33008 (8.9, SaaS, 45 terminated instances) and SUPPORT-33606 (8.9-based, OpenSearch,
1185 **completed** instances, recurring "every time someone resolves a bunch of incidents").

## Why the originally stated root cause is incomplete

The issue attributes the staleness to `createPendingIncidentBatch` dropping `CREATED` when the
matching `RESOLVED` is in the same batch
([collapse](https://github.com/camunda/camunda/blob/615e2a60175a367b2839b0c58aec71b0c0bf9bcc/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/ElasticsearchIncidentUpdateRepository.java#L408)).
That collapse alone cannot *create* the stale flag:

- If the incident document is `ACTIVE` when the (collapsed) batch is processed, the active-incident
  seeding finds it and the list-view flag is correctly cleared.
- If the incident document is still `PENDING` (its `CREATED` was genuinely never processed), the
  list-view flag was never set to `true` in the first place — the result is an *invisible*
  incident, not a stale one. Only the `IncidentUpdateTask` ever writes the list-view `incident`
  field; the export handlers do not.

Every replay/rewind/collapse permutation lands in one of those two consistent outcomes. To produce
a stale `true`, the state "list-view says `true`, but the incident document is not findable as
`ACTIVE`" must already exist — the collapse (and the silent skip below) merely make it permanent.

## Actual root cause: non-idempotent retry after a partially applied bulk

The incident-state update and the list-view flag update travel in **one non-atomic bulk**:

1. On `RESOLVED`, the task queues two items: incident `state → RESOLVED` and list-view
   `incident → false`, gated by
   [`changedState`](https://github.com/camunda/camunda/blob/b705400567efd38cf699733c5ed6257af63f3bd7/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/IncidentUpdateTask.java#L573-L577),
   which is derived from
   [a `state = ACTIVE` search](https://github.com/camunda/camunda/blob/615e2a60175a367b2839b0c58aec71b0c0bf9bcc/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/ElasticsearchIncidentUpdateRepository.java#L252-L268)
   over the incident documents
   ([`removeIncidentIdByPiId`](https://github.com/camunda/camunda/blob/6e4f05688557351aa8e6e62abf89a15bd7a9ee39/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/AdditionalData.java#L44)
   returns `false` when the process instance was never seeded).
2. If **any** bulk item fails, the whole attempt fails
   ([bulkUpdate](https://github.com/camunda/camunda/blob/615e2a60175a367b2839b0c58aec71b0c0bf9bcc/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/ElasticsearchIncidentUpdateRepository.java#L223))
   and the position is not advanced — but the **successful items stay applied** (Elasticsearch/
   OpenSearch bulks are not transactional).
3. On the retry, the incident document is already `RESOLVED`, so the `ACTIVE` search returns
   nothing, `changedState` is `false`, and **the failed list-view item is never re-issued**. The
   retry succeeds and
   [advances the position unconditionally](https://github.com/camunda/camunda/blob/b705400567efd38cf699733c5ed6257af63f3bd7/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/incident/IncidentUpdateTask.java#L175)
   (the #42366 fix — which turned this from an infinite-retry stall into a silent, permanent skip).

So the corrupting order is: *incident item applied, list-view item lost, retry*. Any per-item
failure produces it — `document_missing_exception` (the archiver moved the list-view document
between index capture and bulk execution), `es_rejected_execution`, version conflicts beyond
`retryOnConflict`, or a bulk transport timeout where the server applied the request. The archiver
is what makes it *correlated*: one archiver batch moving dozens of finished instances while one
`IncidentUpdateTask` batch resolves their incidents corrupts all of them in a single retry cycle
(45/46 at once in SUPPORT-33008). The mirror on the CREATE side: attempt applies
`incident → true` but the `state → ACTIVE` item fails; the `RESOLVED` record arrives before the
retry; the batch collapse then drops the `CREATED`, the document stays `PENDING`, and the
`RESOLVED` is skipped the same way — this is where the collapse described in the issue
participates.

A single episode logs approximately one line (`ReschedulingTaskLogger` logs the first occurrence
at ERROR, then suppresses to DEBUG), typically weeks before anyone investigates — hence "no errors
in the logs" in both support cases. The `CREATED`/`RESOLVED` pairs found in the post-importer
queue are residue (the task never deletes queue entries), not evidence that the pair was processed
in one batch.

## Second, independent mechanism: archiver lost update

The archiver moves documents with reindex + `delete_by_query`, both with
[`Conflicts.Proceed`](https://github.com/camunda/camunda/blob/835087667c163469ce24d8f93978ced93db2021e/zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/archiver/ElasticsearchArchiverRepository.java#L306).
An `IncidentUpdateTask` write that lands on the main-index copy *after* the reindex snapshot read
it is silently discarded by the delete; the dated copy keeps the snapshotted `incident = true`.
No error, no retry, nothing logged. This requires the task to be processing a `RESOLVED` more than
the archiver wait period (default 1h) after the instance finished, and is **not** addressed by any
fix inside the update task — it needs coordination between the archiver and in-flight incident
updates.

## Reproduction

`IncidentUpdateRepositoryIT.PartialBulkRetryRegressionIT` reproduces mechanism 1 deterministically
on both Elasticsearch and OpenSearch: it write-blocks the list-view index so exactly the list-view
item of the bulk fails, lets the first attempt fail, unblocks, retries — the incident document is
`RESOLVED`, the position advances past the batch, and the list-view document keeps
`incident = true` forever. The test asserts the desired behavior (flag cleared), so it fails as
long as #54279 is unfixed: verified red on current `main` against both Elasticsearch and
OpenSearch (only the final assertion fails; all intermediate state assertions pass), and green
with the seeding change from PR #55034 applied.

## Fix directions

- Mechanism 1: make the retry idempotent — the decision to update the list view must not depend on
  the incident document's current state, e.g. by seeding the affected-instance map from the
  `RESOLVED` queue entries themselves (PR #55034) or by unconditionally writing the recomputed
  flag (`incident =` "any other ACTIVE incident covers this instance").
- Mechanism 2: prevent the archiver from discarding concurrent incident updates (e.g. re-apply
  updates newer than the reindex snapshot, or fence incident updates against instances being
  archived).
