# Proof of concept: disk limit per physical tenant

Noisy-neighbour scoping, direction item 10 and slice 1: the out-of-disk detector compares the disk
used by each tenant with a limit, so one tenant cannot fill the shared volume for the others.

## What it proves

- An operator sets `camunda.data.primary-storage.disk.used-space.processing` and
  `...used-space.replication` (max bytes used by one tenant's data on one broker), at the root for
  every tenant or under `camunda.physical-tenants.<id>` for one tenant. Unset means no limit and
  today's behaviour.
- When a tenant's data exceeds its processing limit, only that tenant's partitions reject commands
  with the usual `RESOURCE_EXHAUSTED` "Broker is out of disk space" error and pause processing.
  Other tenants on the broker keep creating and completing instances.
- Exporting, snapshotting and compaction keep running, so the tenant recovers on its own when its
  usage drops: no restart, no operator action beyond fixing the cause.
- A follower stops accepting replicated segments for a tenant above its replication limit, while
  the other tenants on the same broker keep replicating.
- Per-tenant gauges for used bytes and limits, a walk-duration timer, the existing per-partition
  health ("Not enough disk space available") and a log line on each crossing.
- The volume-level free-space check is unchanged and still applies to every tenant.

## How it works

|                                                 Piece                                                 |                                  Where                                   |
|-------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|
| Config `disk.used-space.{processing,replication}`, startup validation                                 | `configuration/.../disk/UsedSpace.java`, `zeebe/broker/.../DiskCfg.java` |
| Per-tenant override allowed (deny-list narrowed to the volume-level `disk.*` children)                | `PhysicalTenantOverridePolicyValidation`, golden files                   |
| Accounting: walk of `<data>/<tenant>` (+ `<runtime>/<tenant>` when separate), hard links counted once | `DiskUsageScanner`                                                       |
| Per-tenant monitor: wraps the broker-wide monitor, adds the processing limit, IO-bound actor          | `PhysicalTenantDiskSpaceUsageMonitor`                                    |
| Wiring: a tenant with a limit hands its monitor to its partitions instead of the broker one           | `PartitionManagerImpl`                                                   |
| Replication limit: decorated `SegmentAllocator`, refuses before the segment file exists               | `SegmentAllocator#checkCanAllocate`, `RaftPartitionFactory`              |
| Metrics `zeebe_broker_disk_used_bytes`, `..._used_limit_bytes{threshold}`, `..._used_scan_duration`   | `PhysicalTenantDiskUsageMetrics(Doc)`                                    |

## Run it

```bash
./mvnw verify -pl zeebe/broker -Dtest='PhysicalTenantDiskSpaceUsageMonitorTest,DiskUsageScannerTest,DiskCfgTest' -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl zeebe/journal -Dtest=SegmentedJournalTest -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl configuration -Dtest='PhysicalTenantOverridePolicyValidationTest,PhysicalTenantResolverTest,PhysicalTenantOverridablePropertiesGoldenTest,PrimaryStoragePropertiesTest*' -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl zeebe/qa/integration-tests -Dit.test='PhysicalTenantDiskUsageLimitIT,PhysicalTenantDiskReplicationLimitIT' -DskipTests=false -DskipUTs -Dquickly
```

The ITs need no Docker. `PhysicalTenantDiskUsageLimitIT` fills one tenant with 256 KiB messages
past a 24 MiB limit, checks the default tenant still completes instances, then forces a snapshot
and checks the tenant accepts commands again. `PhysicalTenantDiskReplicationLimitIT` runs three
brokers where only broker 2 limits one tenant's replication.

Example configuration:

```yaml
camunda:
  data.primary-storage.disk.used-space:
    processing: 20GB     # default for every tenant
    replication: 22GB
  physical-tenants:
    bigcustomer:
      data.primary-storage.disk.used-space:
        processing: 60GB
        replication: 64GB
```

## Design decisions

- **Directory walk, not incremental accounting.** One walk sees journal segments, snapshots and the
  RocksDB runtime, needs no hooks in three subsystems and cannot drift. Snapshots hard-link the
  runtime's SST files, so files are counted once per file key. Measured cost below: about 3 µs per
  file, a few ms for a typical tenant.
- **Same cadence as the volume check** (`disk.monitoring-interval`, default 1 s), on an IO-bound
  actor so the walk never blocks a CPU actor thread. The walk duration is a metric so its cost is
  visible in production.
- **Reuse the existing listener path.** The tenant monitor implements `DiskSpaceUsageMonitor` and
  is handed to the tenant's partitions, so the command API, inter-partition commands, backup API
  and stream processor all react exactly as for a full volume. No new rejection type or error.
- **Volume state is folded in.** A partition is "out of disk" when the volume is full *or* its
  tenant is over its processing limit; it recovers only when both are fine.
- **Replication limit at segment allocation**, the same granularity as the existing free-space
  replication check. Allocations are reserved against the last measurement immediately, so several
  partitions cannot overshoot between two walks. The check runs before the file is created; a small
  journal hook (`checkCanAllocate`) was needed because refusing in `allocate` left an empty file
  that the journal warned about on every append retry (about 2500 warnings in 3 s in the IT).
- **Limits in bytes only.** A percentage of the volume was optional in the brief; a byte limit is
  what an operator sizes from a quota, and a percentage can be added to `UsedSpace` without changing
  enforcement.
- **Validation at startup:** each limit must exceed the log segment size, and replication must
  exceed processing (the tenant needs room to snapshot and compact). Disabling disk monitoring
  removes the limits.
- **No limit, no cost:** a tenant without `used-space` keeps the broker-wide monitor directly; no
  actor, no walk, no metrics.
- **Root value is a per-tenant default**, following ADR-0007 D1: `camunda.data.primary-storage.disk.used-space`
  applies to every tenant, including `default`, and each tenant can override it.

## Measurements

Directory walk with `Files.walkFileTree` + `BasicFileAttributes`, hard-link dedupe by file key,
synthetic tenant trees (128 MiB sparse segments, 1 KiB SSTs hard-linked into snapshots), Apple
M5 Pro laptop, APFS, warm page cache, median of 21 runs after 5 warm-ups
(harness: a standalone `WalkBench.java`, not committed):

| Partitions | Segments/partition | SSTs/partition | Snapshots | Files  | Median  | Per file |
|------------|--------------------|----------------|-----------|--------|---------|----------|
| 3          | 4                  | 20             | 1         | 147    | 0.69 ms | 4.7 µs   |
| 3          | 50                 | 100            | 2         | 1 068  | 3.6 ms  | 3.4 µs   |
| 8          | 50                 | 200            | 2         | 5 248  | 15 ms   | 2.9 µs   |
| 8          | 500                | 200            | 2         | 8 848  | 23 ms   | 2.6 µs   |
| 32         | 100                | 300            | 2         | 32 192 | 91 ms   | 2.8 µs   |

At a 1 s interval a typical tenant costs well under 1% of one core; a pathological 32k-file
tenant costs about 9%. Not measured: Linux ext4/xfs on a cloud persistent disk with a cold cache.

In the IT, pause takes effect within one interval (100 ms) of crossing the limit, and the tenant
resumes within one interval of compaction (25.2 MB → 18.2 MB after a forced snapshot).

## Stubbed or missing

- No runtime change of a limit: it is static config (raising it needs a restart). The lifecycle
  API would make it changeable; the monitor reads plain longs, so a setter is all it needs.
- No percentage-of-volume limit.
- No dedicated per-tenant health indicator: the existing partition health of the tenant turns
  unhealthy with "Not enough disk space available", which does not say whether the volume or the
  tenant limit is the cause (the log line does).
- The broker does not compact more aggressively when a tenant is over its limit. With the default
  `prefer-snapshot-replication-threshold` (100 entries) and large records, a snapshot can free
  nothing; the IT lowers that threshold. Production limits should be large relative to 100 entries.
- Only one IO-bound actor per limited tenant; no shared walker across tenants.
- Not measured on a real cluster or under a dead-exporter scenario.

## Open questions

1. Should the limit be per broker (as built) or per tenant across the cluster? Per broker matches
   the per-volume problem; a cluster-wide quota needs aggregation and placement awareness.
2. Bytes or a share of the volume, or both? Should the root default be derived from the volume
   size and tenant count?
3. Should an over-limit tenant trigger `compactIgnoringReplicationThreshold`, as the leader does
   on a full volume, to recover faster?
4. Who changes the limit at runtime: the lifecycle API (persisted) or an actuator like
   `flowControl` (lost on restart)?
5. Is a 1 s walk acceptable at the tenant counts in the high-tenant-counts epic (20–40 tenants per
   broker), or should the interval for the walk be separate from the volume check?
6. Should the out-of-disk error tell the client that its tenant, not the broker, is out of space?
   Today the message is the same, which keeps clients unchanged but hides the cause.

