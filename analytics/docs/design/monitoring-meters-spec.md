# Implementation spec: Micrometer meters for pipeline observability

Status: ready to implement. Motivation: during the 1 PI/s load run every operational question —
"is ingestion lagging?", "how big is state?", "what throughput?", "did a zombie write?" — was
answered by hand (querying cube windows, `du`, diffing search counts, log greps). Each of those
hand-measurements becomes a meter. The existing seams (`MicrometerProjectionMetrics`,
`LateDropAlarm`, `eb.streaming.cut.*` timers) show the intended wiring style — extend, don't
reinvent.

Conventions (bind everything through these):
- Names: `eb.streaming.*` for library-level meters (event-bridge-streaming),
`analytics.*` for app/domain meters. Lowercase dot-separated, nouns; counters are
past-tense events (`….sealed`, `….rejected`).
- Tags: `stage` (projection|aggregation), `source` (source partition id) where per-source,
`dataset` where per-dataset, `backend` (rdbms|elasticsearch|opensearch) on serving meters.
Keep tag cardinality bounded — never tag by key, window, or instance.
- The streaming library ALREADY depends on micrometer-core directly; its house idiom is
`CutMetrics` (`streaming/internals/CutMetrics.java`): a small per-group interface with a
zero-allocation `NOOP` default and a Micrometer implementation, constructed via
`of(registry, …)` that returns NOOP for a null registry — the hot path pays nothing when
uninstrumented. New LIBRARY meters (phases 1-2) MUST copy this pattern (e.g. a `FlowMetrics`
group for records/segments/deltas/dedup/watermark, a `StoreMetrics` group for the overlay),
wired from `StreamRuntime`'s existing MeterRegistry. APP meters (phase 3) follow the engine
pattern instead: `ProjectionMetrics` interface + `MicrometerProjectionMetrics` adapter in the
pipeline. The RocksDB gauges already exist opt-in in `RocksDbStateStoreProvider` behind
`StoreTuning` — phase 2 only turns them on in app config.
- Every new meter: one unit test through `SimpleMeterRegistry` asserting it moves at the right
seam, following the module's test conventions (JUnit 5, AssertJ, should…, given/when/then).

## Phase 1 — "am I keeping up" (the lag pack; highest value)

|              meter               |    type    |       tags        |                                                                                               seam                                                                                                |
|----------------------------------|------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eb.streaming.watermark.lag`     | gauge (ms) | stage             | `now − operator clock` — the min-of-watermarks clock lives in `SegmentMergingAggregation`; expose the current clock via the owning task like the cut timers are exposed                           |
| `eb.streaming.records.processed` | counter    | stage             | the task's `process()` loop (`ProjectionStageTask` / `AggregationStageTask`)                                                                                                                      |
| `eb.streaming.dedup.skipped`     | counter    | stage             | BOTH skip sites: the pre-fold position skip (stage 1) and the segment-coordinate dedup (stage 2). Replay visibility: a spike after restart is EXPECTED and seeing it confirms recovery is working |
| `eb.streaming.segments.sealed`   | counter    | stage=projection  | seal path in `SegmentSealingAggregation`'s sink callback (count seals, not cells)                                                                                                                 |
| `eb.streaming.deltas.merged`     | counter    | stage=aggregation | the merge-accept path (after dedup)                                                                                                                                                               |
| `analytics.facts.emitted`        | counter    | factType          | the fact consumer in `ProjectionStageTask`                                                                                                                                                        |
| `analytics.facts.dropped`        | counter    | —                 | already counted internally (`ProjectionMetrics.factDropped`) — verify it reaches Micrometer via `MicrometerProjectionMetrics`; if yes just document, if no wire it                                |

Alerting story (document in the meters doc, don't implement alerts): watermark.lag growing =
falling behind; records.processed flat while the source moves = wedged (the #23 signature);
dedup.skipped nonzero OUTSIDE a restart window = duplicate source traffic.

## Phase 2 — "is state healthy"

|                meter                 |  type   |     tags     |                                                                                                 seam                                                                                                  |
|--------------------------------------|---------|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eb.streaming.store.overlay.entries` | gauge   | stage, store | `CachingKeyValueStore` — active overlay size (the records cache; its growth forces early cuts)                                                                                                        |
| `eb.streaming.store.overlay.bytes`   | gauge   | stage, store | same seam if a byte estimate is cheap; skip if it would require serializing to measure — note the skip                                                                                                |
| `eb.streaming.cut.early`             | counter | stage        | cuts triggered by `needsCheckpoint()` (overlay full) rather than the cadence — a sustained nonzero rate means the overlay is undersized for the load                                                  |
| RocksDB gauges                       | gauge   | stage        | opt-in gauges already exist behind `StoreTuning` — turn them ON by default in the analytics app's config (not in the library) and verify they appear under `/actuator/metrics`; do not build new ones |

## Phase 3 — "is serving healthy"

|                meter                |  type   |       tags       |                                                                                            seam                                                                                            |
|-------------------------------------|---------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `analytics.serving.rows.written`    | counter | backend, dataset | `RdbmsDatasetWriter` / `DocumentDatasetWriter` success paths                                                                                                                               |
| `analytics.serving.fenced.rejected` | counter | backend          | the version-fence rejection branches (RDBMS fenced upsert no-op, ES 409→NOOP). A nonzero rate NOT during failover = zombie writer — this is the meter that makes fencing observable at all |
| `analytics.serving.write.duration`  | timer   | backend          | around the write/bulk call (bulk: one sample per batch + a `analytics.serving.batch.size` distribution summary for ES/OS)                                                                  |
| `analytics.query.duration`          | timer   | endpoint         | one `Timer` around the dashboard read layer — simplest correct seam: time `DatasetQueryExecutor.execute` (tag by dataset) rather than per-controller-method plumbing                       |
| `analytics.dataset.empty.alarm`     | counter | dataset          | the existing silent-empty-cube warning path — count when it fires, so the alarm is graphable, not just logged                                                                              |

## Phase 4 — consumer-group client visibility (would have made the #23 wedge visible instantly)

|              meter               |  type   | tags  |                                                       seam                                                       |
|----------------------------------|---------|-------|------------------------------------------------------------------------------------------------------------------|
| `eb.consumer.rebalances`         | counter | group | the client's assignment-change callback                                                                          |
| `eb.consumer.assignment.epoch`   | gauge   | group | current assignment epoch as seen by the client — a pinned epoch while group epoch climbs was the wedge signature |
| `eb.consumer.heartbeat.failures` | counter | group | heartbeat error path in the client                                                                               |

These live in the event-bridge client/consumer-groups modules — if the wiring there has no
metrics facade at all, add the smallest possible one following the streaming library's pattern;
if that turns out to be a large change, implement phases 1–3 and report phase 4 as
needs-a-facade with the seam locations, rather than forcing it.

## Documentation deliverable

`analytics/docs/monitoring.md`: one table of ALL meters (existing + new) — name, type, tags,
what it means, what to alert on. The primer's audit-table style. Include the three existing
`eb.streaming.cut.*` timers and `analytics.aggregation.late.dropped`.

## Gates

Standard: `license:format spotless:apply` before every commit; module-scoped
`verify -DskipTests=false -DskipITs -Dquickly` for every touched module
(event-bridge-streaming, analytics-engine, analytics-pipeline, analytics-store-rdbms,
analytics-store-document, analytics-webapp as applicable); full-repo `install -Dquickly` at the
end. Maven isolation: clone `~/.m2/repository` to a worktree-local `.m2-lane` and pass
`-Dmaven.repo.local` on every invocation; `-T2`.

Conventional commits, no scopes; suggest one commit per phase. Never inline FQNs; no
Kafka/KIP mentions in comments.

## Out of scope

Dashboards/alert rules (document alert conditions only), pushing to any external system,
changing metric names that already exist (the cut timers and late.dropped keep their names),
per-window or per-key tags (cardinality), and any Phase-4 work that requires redesigning the
client — report instead.
