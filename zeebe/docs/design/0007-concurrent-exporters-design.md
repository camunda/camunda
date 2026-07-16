# Concurrent exporters: solution proposal

Companion implementation design for
[ADR 0007: Concurrent, decoupled exporters](../adr/0007-TBD-concurrent-exporters.md). The ADR
records the decision; this document covers the class-level design, migration path, and testing
plan that the ADR deliberately excludes.

## 1. Current-state recap

One `ExporterDirector` actor per partition
(`zeebe/broker/src/main/java/io/camunda/zeebe/broker/exporter/stream/ExporterDirector.java`) owns:

- a single shared `LogStreamReader` (`ExporterDirector.java:80`, opened in `onActorStarting`/
  `restartActiveExportingMode`)
- an `ArrayList<ExporterContainer>` (`ExporterDirector.java:73`), one container per configured
  exporter
- a `RecordExporter` (`RecordExporter.java`) that wraps each read record once and then walks the
  container list via a shared `exporterIndex` cursor: `export()` calls
  `container.exportRecord(...)` for the container at the current index; if it returns `true`
  (accepted or successfully retried) the index advances, otherwise the loop returns immediately
  with the index unchanged, so the *same* container is retried on the next attempt before any
  container after it in the list is even called for that record.

`ExporterDirector.readNextEvent()` only reads the next record once `RecordExporter.export()` has
returned `true` for the current one — i.e., once every container has accepted it. This is the
head-of-line blocking mechanism: containers positioned after a stuck one in the list never even see
new records until the stuck one clears, and the reader itself cannot move forward either.

## 2. Target class design

### `ExporterActor` (new)

One instance per configured exporter per partition. Folds in the per-exporter fields currently on
`ExporterContainer` (`position`, `lastAcknowledgedPosition`, `lastUnacknowledgedPosition`,
soft-pause flag, `ExporterReplayControl`) plus:

- its own `LogStreamReader`, opened at its own persisted position on start
- its own `BackOffRetryStrategy` (replacing the single `exportingRetryStrategy` shared by
  `ExporterDirector` today)
- its own read loop: read next event → apply this exporter's filter → export with retry → on
  success, advance this exporter's position and read the next event. No cross-exporter cursor.
- implements `HealthMonitorable`, reporting only its own health

`ExporterContainer.exportRecord`/`updatePositionOnSkipIfUpToDate`/`requestReplay`/
`softPauseExporter`/`undoSoftPauseExporter` move onto `ExporterActor` largely unchanged in logic —
the behavior per exporter is the same, only the driving loop around it changes.

### `ExporterCoordinator` (replaces `ExporterDirector` as the broker-facing surface)

Owns a `Map<String, ExporterActor>` instead of the `ArrayList<ExporterContainer>` +
`recordExporter.resetExporterIndex()` dance. Responsibilities:

- partition transition: on leader, schedules one `ExporterActor` per configured exporter via
  `actorSchedulingService.submitActor(child, SchedulingHints.ioBound())`; on follower, schedules
  none (followers never read the log — only the distribution-consumption path runs, as today)
- lifecycle fan-out: `pauseExporting`/`softPauseExporting`/`resumeExporting` become a future over
  `allOf` of each child's own call; `enableExporter`/`enableExporterWithRetry` spawn and schedule a
  new child; `removeExporter` closes the child, waits for its actor to fully stop, then removes its
  state row (avoids a write race between the closing actor and the coordinator)
- health aggregation: subscribes as `FailureListener` to every child, aggregates worst-of (`dead` if
  any child is dead, `unhealthy` if any is unhealthy, `healthy` otherwise) as the partition's single
  `HealthReport`, preserving today's one-registration-per-partition contract while isolating
  failure per exporter
- owns `ExporterStateDistributionService` exactly as today — a single batch broadcast of all
  exporters' positions, built from `ExportersState.visitExporterState`, unchanged in wire format

### `RecordExporter` — deleted

Its only purpose was the shared `exporterIndex` cursor across multiple containers. With one
container per actor, there is no list to walk; exporting a record is a direct
retry-until-success call on that actor's own record.

## 3. State ownership and RocksDB access

`ExportersState` (`ExportersState.java`) keeps its existing schema: a single RocksDB column family
(`ZbColumnFamilies.EXPORTER`) keyed by exporter id, storing `{position, metadata, metadataVersion}`.
No migration is needed — each exporter's row is already independent.

What changes is who writes to it. Today, one `TransactionContext` (created once in
`ExporterDirector.recoverFromSnapshot()`) is shared by the single actor for all containers. Under
this design, each `ExporterActor` creates its own `TransactionContext` via `zeebeDb.createContext()`
and constructs its own `ExportersState` instance bound to that context, writing only its own row.
This mirrors the pattern already used by `DbPositionSupplier`
(`zeebe/broker/src/main/java/io/camunda/zeebe/broker/logstreams/state/DbPositionSupplier.java`),
which today creates an independent context to read exporter positions off the exporting actor's
thread entirely — proof that concurrent, independently-owned transaction contexts against this
column family are already safe in this codebase.

Compaction's read path (`ExportersState.getLowestPosition()` →
`DbPositionSupplier.getLowestExportedPosition()` →
`StateControllerImpl.tryFindNextSnapshotId()` →
`LogCompactor`) needs no change: it already reads through its own independent context regardless of
which actor last wrote to the rows it scans.

## 4. Partition transition and lifecycle

`ExporterDirectorPartitionTransitionStep`
(`zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/partitions/impl/steps/ExporterDirectorPartitionTransitionStep.java`)
changes to build an `ExporterCoordinator` instead of an `ExporterDirector`. `PartitionContext`'s
`getExporterDirector()`/`setExporterDirector()` accessors are renamed to hold the coordinator; all
other call sites (`BrokerAdminServiceImpl`, admin/status reporting) go through the coordinator's
equivalent API (e.g. `getLowestPosition()` stays a coordinator method backed by the same
`DbPositionSupplier`-style independent read).

Dynamic exporter reconfiguration (enable/disable/remove at runtime, exporter added/removed from
broker config) goes through the coordinator, which starts or stops the corresponding
`ExporterActor` without affecting any other exporter's actor.

## 5. Monitoring and mitigation for the retention consequence (ADR D4)

Since a stalled exporter can now diverge arbitrarily from its siblings, add:

- a per-exporter position-lag metric: `logHeadPosition - exporterPosition`, exposed per exporter id
- a position-spread metric: `max(exporterPosition) - min(exporterPosition)` across configured
  exporters on a partition, as a single signal for "how much is decoupling costing us in retention
  right now"
- operator documentation: how to identify a chronically stalled exporter from these metrics, and
  how to pause or remove it to unblock compaction, since compaction cannot advance past it
- a suggested default alert threshold on sustained per-exporter lag (exact threshold is an
  operational tuning decision, not an architectural one — left to rollout, not this document)

## 6. Rollout plan

RocksDB schema is unchanged, so this refactor is safe across rolling upgrades with no data
migration and no version-skew handling beyond what already exists for the state column family. No
persisted feature flag is required for this reason. Given the size of the structural change
(partition transition wiring, health aggregation, and the full lifecycle API surface), an internal
soak period ahead of general availability is recommended, gated on the acceptance test in §7
passing under sustained load with an intentionally stalled exporter.

## 7. Testing plan

- Replace `ExporterDirectorTest` with `ExporterActorTest`, covering the same invariants against a
  single actor: retry-until-success, skip-record position updates, snapshot/position recovery,
  filter application.
- Add `ExporterCoordinatorTest`: lifecycle fan-out (enable/disable/remove/pause/resume across
  multiple children), aggregated health (worst-of semantics), stale exporter-state cleanup on
  startup.
- Retarget `ExporterDirectorPauseTest` and `ExporterDirectorDistributionTest` at the coordinator;
  behavior (pause/soft-pause/resume semantics, leader→follower state distribution) is unchanged in
  contract, only in which class implements it.
- Retarget `ExporterDirectorPartitionTransitionStepTest` at coordinator construction.
- Add the key acceptance test that does not exist today, and is the direct regression guard for
  camunda/camunda#44931: with two or more exporters configured, one exporter fails/stalls
  indefinitely while the others continue to advance their own positions and export new records
  without delay.
- Add a reader-lifecycle test confirming no `LogStreamReader` is leaked across exporter add/remove.
- Add a compaction regression test with deliberately divergent per-exporter positions, confirming
  `getLowestPosition()` still returns the true minimum and compaction still never deletes segments
  the slowest exporter has not consumed.

