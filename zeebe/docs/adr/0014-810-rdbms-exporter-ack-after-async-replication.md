# RDBMS exporter acknowledges a position only after async replication confirms it

**DRI**: Carlo Sana

**Status**: Accepted (8.10)

**Purpose**: Defines how the RDBMS exporter keeps Zeebe log compaction behind the replication of an
asynchronously replicated secondary-storage database, so that a database failover loses no data.

**Audience**: Engineers and AI agents working on the RDBMS exporter
(`zeebe/exporters/rdbms-exporter/`), the RDBMS replication providers (`db/rdbms/`), log
compaction, or multi-region and HA deployments with an RDBMS secondary storage.

## Context

Multi-region active-active deployments use an asynchronously replicated database as secondary
storage. Some single-region deployments use one for HA. After a failover, the new primary can miss
the last writes of the old primary. Zeebe can export those records again, but only while the log
segments still exist. Zeebe compacts a segment as soon as all exporters acknowledge its records. A
plain RDBMS exporter acknowledges at flush time, so compaction can outpace replication, and a
failover then loses secondary-storage data permanently.

Synchronous database replication does not have this problem and already works without changes.
This ADR covers asynchronous replication only. The outcome: the RDBMS exporter delays its
acknowledgement until the replicas confirm the data, and it reports the replication state through
metrics.

## Decision

**D1. The exporter acknowledges a position only after the replicas confirm it.**
At each flush, the exporter records the flushed position together with a replication marker. It
acknowledges to the broker the highest position whose marker the replicas confirm. On restart, the
exporter does not acknowledge the stored RDBMS position directly (#51588). The feature is opt-in
(`async-replication.enabled`, default `false`).

**D2. One replication controller runs three signal strategies, ranked by preference.**
`LOG_SEQ` compares the log sequence number (SCN for Oracle) of the primary and the replicas.
`TIME_LAG` uses the lag that the database reports, anchored to a point in time on the database
clock. `DELAY` releases a position after a fixed delay and observes no replication state. Use
`LOG_SEQ` when the database supports it. Use `TIME_LAG` when `LOG_SEQ` is not available. Use
`DELAY` only as a fallback (for example, plain MySQL and MariaDB). The controller owns queueing,
scheduling, and metrics. Each strategy supplies only its signal.

**D3. A configurable quorum of replicas must confirm a position.**
A position is confirmed when the `minSyncReplicas` most up-to-date replicas reach its marker. With
fewer connected replicas than the quorum, the exporter confirms nothing. `DELAY` has no quorum.

**D4. The exporter stops exporting when replication lag exceeds `maxLag`.**
When the operator sets `pauseOnMaxLagExceeded`, each export call throws a retryable exception until the lag
is back within `maxLag`. The exporter makes no progress, and broker flow control limits new
writes. This does not use the exporter pause mechanism. The option is off by default. `DELAY`
never stops exporting, because it has no lag signal.

**D5. The exporter fails at startup when the database cannot supply the selected signal.**
The exporter does not fall back to a weaker strategy. It fails when it opens, for example on plain
MySQL with `LOG_SEQ`, on Oracle with `TIME_LAG`, on Aurora without Global Database, or when the
database user does not have the monitoring privileges. The operator finds a wrong setup at
deployment time, not during a region failure.

## Alternatives considered

- **One exporter per region, each writing to its own regional database.** This is the legacy
  dual-region architecture for Elasticsearch and OpenSearch. TODO(DRI): add the reason this was rejected for RDBMS.
- **Recover lost data by replay only.** Replay needs log segments that compaction already removed.
  Without D1 a failover loses data permanently.

## Consequences

- Log segments stay on disk until the replicas confirm them. Disk usage grows with replication lag
  (or with `delay` for `DELAY`). Size the broker disk for the worst expected lag. The docs give a
  sizing formula.
- `DELAY` gives no guarantee. The operator must monitor the real replication lag externally and
  set `delay` above it.
- With D4 enabled, a slow or lost replica stops processing. Without D4, disk usage grows until the
  replica recovers or the disk is full.
- The database user needs vendor-specific monitoring privileges (for example `pg_monitor` on
  PostgreSQL, `VIEW SERVER STATE` on MSSQL).
- A new vendor or a new signal needs a new provider in `db/rdbms` and a new strategy. The
  controller does not change.

## Source

- [[EPIC] Support Asynchronously Replicated RDBMS for Secondary Storage](https://github.com/camunda/camunda/issues/51414)
- [[EPIC] ECS Dual Region w/ Aurora Global](https://github.com/camunda/camunda/issues/51411)
- [RDBMS configuration: Multi-region support](https://docs.camunda.io/docs/next/self-managed/concepts/databases/relational-db/database-configuration/#multi-region-support)
- [Product Hub issue](https://github.com/camunda/product-hub/issues/3585) (internal)

