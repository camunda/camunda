# Concurrent, decoupled exporters: one actor per exporter

**DRI**: TBD

**Status**: Proposed

**Purpose**: Defines how exporters are decoupled so that one exporter's failure, stall, or backlog
no longer blocks any other exporter on the same partition, and how their progress and failure
domains are isolated going forward.

**Audience**: Zeebe broker engineers working on the exporter module, log compaction, or partition
lifecycle, and AI agents implementing or reviewing camunda/camunda#44931.

## Context

A partition's exporters currently share one `LogStreamReader` and one retry cursor: a record is
only considered done once every configured exporter has accepted it, so the reader cannot advance
to the next record until all exporters have processed the current one. A single slow, stuck, or
misconfigured exporter therefore blocks every other exporter on the partition, and with multiple
exporters configured this is a direct, compounding throughput and availability risk.

This is not solvable by decoupling positions alone. The `Exporter.export(Record)` SPI is a
synchronous, potentially-blocking `void` call with no async contract, and real exporters (e.g. the
Elasticsearch exporter) do block synchronously when flushing a batch. A design that gives each
exporter its own reader and position but keeps them on one shared actor thread would still
serialize those blocking calls — fixing bookkeeping, but not delivering actual concurrent
throughput. Achieving both failure isolation and real parallelism requires each exporter to run on
its own actor thread.

## Decision

**D1. Each configured exporter runs on its own actor, with its own reader, position, and retry
strategy.**
Replaces the current design where one actor drives a shared reader and a shared retry cursor across
all exporters. Each exporter's actor owns a `LogStreamReader` opened at that exporter's own
position and retries only its own stuck record, so a blocking or failing exporter cannot delay any
other exporter's reads, exports, or position advancement.

**D2. A thin per-partition coordinator remains, but performs no record processing.**
The rest of the broker (partition transition, health monitoring, dynamic exporter
enable/disable/pause/resume) needs one lifecycle handle and one health signal per partition, not
one per exporter. A coordinator actor owns spawning and tearing down exporter actors, fans out
lifecycle operations to them, aggregates their health into the partition's single health report,
and distributes exporter positions to followers — but never reads the log or exports a record
itself.

**D3. Exporter state remains a single, independently-keyed store; each exporter actor writes to it
through its own transaction context.**
The persisted exporter-position store is already keyed independently per exporter and requires no
schema change. Each exporter actor obtains its own storage transaction context rather than sharing
one owned by the coordinator, following the same independent-context pattern already used
elsewhere in the broker to read exporter positions off the exporting actor's thread.

**D4. Log compaction's dependency on exporter positions is unchanged in mechanism, but its
consequence changes in practice.**
Compaction must still never delete log segments a configured exporter has not yet consumed — that
correctness invariant does not change and requires no new mechanism. What changes is the *spread*
between exporters' positions: today's shared-cursor design keeps all exporters within roughly one
record of each other, so retained log volume stays small; once exporters progress independently, a
stalled exporter can fall arbitrarily far behind its siblings, and retained log volume grows with
that gap until the stalled exporter recovers, is paused, or is removed.

**D5. Failure isolation replaces shared failure.**
An unrecoverable error in one exporter today halts exporting for the entire partition. Under this
decision, it parks only that exporter — preserving its position for a later restart — while
siblings continue unaffected. Partition-level health becomes an aggregate (worst-of) over
individual exporter health, not a single shared state.

**D6. No public API changes.**
The `Exporter`/`Controller`/`Context` SPI exporter implementations are built against is untouched.
This is an internal broker refactor of how configured exporters are scheduled and isolated from
each other, not a change to what an exporter implementation does or receives.

## Alternatives considered

- **Single shared actor with a per-exporter reader and position, but one thread.** Would remove the
  shared retry cursor and let each exporter track its own position, improving on today's
  bookkeeping. Rejected because `Exporter.export()` is a synchronous, potentially-blocking call: a
  shared thread still serializes those calls across exporters, so a slow exporter would continue to
  delay every other exporter's throughput — it does not satisfy the parallel-exporting goal, only
  the isolation goal.

## Consequences

- Multi-exporter deployments gain real throughput and latency improvements: exporters no longer
  wait on each other.
- One exporter's fatal error, network partition, or backlog no longer stops exporting for the
  whole partition.
- The number of actors scheduled per partition grows from one to one-per-configured-exporter;
  in practice this is small (deployments typically configure a handful of exporters), but it is a
  real increase in scheduled units on the broker's IO-bound actor pool.
- Retained log volume is no longer implicitly bounded by lock-step exporter progress; a stalled
  exporter can now force materially larger log retention than before, requiring new operator-facing
  visibility into per-exporter position lag so a stalled exporter can be identified and paused or
  removed before it drives excessive retention.
- This is a structural internal refactor touching partition transition wiring, health aggregation,
  and the exporter module's lifecycle APIs, with a correspondingly larger test surface than a
  point fix would have.
- No persisted-state migration is required; the change is safe across rolling upgrades.

## Source

- [Decouple and parallelize exporters (camunda/camunda#44931)](https://github.com/camunda/camunda/issues/44931)
- [Concurrent exporters: solution proposal](../design/0007-concurrent-exporters-design.md)

