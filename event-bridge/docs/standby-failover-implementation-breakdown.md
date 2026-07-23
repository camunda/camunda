# Standby tasks & snapshot bootstrap — implementation breakdown

Implements [ADR 0006 — standby tasks: warm failover and snapshot-based bootstrap](
../event-bridge-streaming/docs/adr/0006-standby-tasks-warm-failover.md).

Five milestones. M1→M2→M3 are sequential (each builds on the previous); M4 runs in parallel with
M2/M3; M0 lands first and independently. **M1+M2 alone already deliver warm-standby failover** on
deployments where retention covers history (behind the retained-window guard), so the standby path
can ship and soak before the snapshot subsystem exists.

Sizes: S = a day-ish, M = a few days, L = a week+.

---

## M0 — Guardrails first

| # | Item | Where | Size |
|---|------|-------|------|
| 0.1 | **Determinism contract test**: two independent folds of the same topic content produce identical envelope sequences (`(segment, chunk)` + payload bytes). Guards the invariant every later step relies on. | `event-bridge-streaming` test (seal path), `analytics-pipeline` variant | M |
| 0.2 | **Multi-member test harness**: two real consumers + embedded broker/coordinator, with kill/restart helpers. Prerequisite for every IT below. | `event-bridge-streaming` / `event-bridge-examples` test support | M |

## M1 — Protocol: roles in the assignment

| # | Item | Where | Size |
|---|------|-------|------|
| 1.1 | Role (`ACTIVE`/`STANDBY`) in the three partition-only shapes: `MemberAssignment` (wire record), `PartitionAssignment` (assignor output), `PartitionAssignmentContext` (assignor input, + `standby.replicas`). | `event-bridge-consumer-groups` (`record/`, `assignor/`) | M |
| 1.2 | Group-level `standby.replicas` config (default 0 = today's behavior exactly). | coordinator group state / group creation | S |
| 1.3 | `BalancedStickyAssignor` role-aware: sticky actives untouched, **anti-affine** standby placement (never on the active's member), promotion preference for an existing standby, warming cap for members with many unready partitions. | `assignor/BalancedStickyAssignor` | L |
| 1.4 | Heartbeat plumbing end-to-end: role in `assign`/`revoke` deltas and the full-assignment list; proto messages; gateway mapping; client `GroupCoordinator` delta handling; role exposed in `RebalanceListener`. | `event-bridge-gateway` (`ConsumerGroupController`), api proto, `event-bridge-client` | M |
| 1.5 | Commit validation: offset commit accepted only from the partition's **active** (extend `validateCommit` ownership check; wire the reserved `FENCED_MEMBER_ACTIVE` code). | `consumer-groups/processing/CoordinationValidator` | S |

Exit: assignor unit tests (placement, anti-affinity, crossed-pair topology, promotion stickiness,
caps); gateway JSON/proto round-trip; client delta tests. `standby.replicas=0` groups behave
byte-identically to today.

## M2 — Runtime: passive standby tasks

| # | Item | Where | Size |
|---|------|-------|------|
| 2.1 | Role-aware assignment application: `SourceLoop.applyRebalance`/`materialize` create shards in passive mode; role changes on owned partitions do not re-materialize. | `event-bridge-streaming/internals/SourceLoop` | M |
| 2.2 | **Passive gate at the barrier**: skip `cut.publish()` + `commitOffset(...)`, keep `persist()`; discard staged output cleanly (envelope buffers, staged serving rows) so nothing accumulates across cuts. | `PartitionActor`, `PartitionCommitter`, `Task`/`CommitCut` contract; `EnvelopePublisher` + `FreezableDatasetWriter` discard paths | L |
| 2.3 | **Frontier throttle**: committed offsets from the heartbeat surfaced to the runtime; a passive partition folds only up to its frontier, parking the rest (reuse pause/park machinery). | client → runtime plumbing, `SourceLoop`/`PartitionActor` | M |
| 2.4 | Role transitions: **promote** (enable publish+commit, unthrottle, resume from local cut) and **demote** (final passive cut, disable) as actor-side state changes. | `PartitionActor` + runtime | M |
| 2.5 | Guard the existing replay-from-start path (`seekToBeginning` on empty state) behind a retained-window check — refuse (and surface) rather than silently build a partial shard. | `SourceLoop.materialize` + broker retained-range query | S |

Exit: passive shard provably emits nothing, commits nothing, discards staged output, respects the
frontier; role flip without rebuild covered in `StreamRuntimeRebalanceTest`-style tests; both
analytics stage tasks green in passive mode.

## M3 — Readiness & progressive handover

| # | Item | Where | Size |
|---|------|-------|------|
| 3.1 | Readiness computation (passive local offset caught up to the frontier) reported per standby partition on the heartbeat request. | runtime + client + proto | M |
| 3.2 | Coordinator tracks standby readiness; readiness changes trigger the debounced assignor run (the "probing rebalance"). | `HeartbeatHandler`, `RebalanceAssignorTask` | M |
| 3.3 | Assignor consumes readiness: promote **only ready** standbys (no ready standby → partition waits); joiners get capped warming sets and receive active roles progressively as readiness arrives. | `BalancedStickyAssignor` | M |

Exit: IT — joiner warms partition-by-partition and absorbs actives progressively; active kill →
ready standby promoted within one commit interval; cold member never promoted.

## M4 — Snapshot subsystem (parallel to M2/M3)

| # | Item | Where | Size |
|---|------|-------|------|
| 4.1 | Snapshot task: RocksDB checkpoint at a committed cut on the IO thread (WAL-off ⇒ checkpoint flushes the memtable — piggyback on a cut), staggered per shard, default 5 min. | `event-bridge-streaming` (new `state/snapshot`), analytics task hooks | M |
| 4.2 | Upload + manifest: full-checkpoint upload via `zeebe/backup-stores` clients (S3/GCS/Azure/filesystem); manifest {file set, offset, watermarks, member epoch} committed by conditional put (zombie fence); checksums via `zeebe/snapshot` discipline. | new module or `state/snapshot`, deps on `zeebe/backup-stores`, `zeebe/snapshot` | L |
| 4.3 | Pruning & GC: keep last **verified** manifest until a newer one verifies; delete files no manifest references. | same | M |
| 4.4 | Restore path: newest manifest → parallel, file-granular **resumable** download, throttled → open → tail-replay → follow frontier. Rejoiner freshness rule: continue from max(local cut offset, manifest offset); local usable only if the retained window reaches it. | runtime restore entry point (feeds the M2 standby lifecycle) | L |
| 4.5 | Observability: **snapshot age vs. retained window** gauge + alert; upload/restore duration, bytes, readiness lag metrics. | streaming gauges + actuator | S |

Exit: snapshot/restore round-trip IT on the filesystem store (testkit); lost-disk failover IT;
stalled-upload alert test. Incremental (SST-file-name dedup) upload is explicitly **not** in
scope — bolt-on later, changes nothing structural.

## M5 — Hardening & end-to-end

| # | Item | Size |
|---|------|------|
| 5.1 | Multi-member failover IT: kill active → promotion → sink converges, finalized windows untouched, zombie's commits/manifests fenced. | M |
| 5.2 | All-copies-lost IT: blocking restore from store, correct state after tail replay. | M |
| 5.3 | Soak on the local eb-cluster: crossed-pair topology, kills, joins, rolling restart; verify no dips, no double counts, metrics sane. | M |
| 5.4 | Docs: ops runbook (store setup, retention contract, alert response), README updates. | S |

## Deferred (tracked, not scheduled)

- Sink-side epoch fence (strict zombie protection on serving upserts).
- Incremental upload (SST-file-name dedup against the store).
- Per-stage **changelog knob** for stages whose fold cost dwarfs their state-write cost (emission
  choke point makes it one more publish target).
- Disaggregated read path, adopt-not-build: ForSt-as-dependency or stock-RocksDB-on-JuiceFS;
  trigger = all-copies-lost restore SLAs binding. Actor-per-partition confines miss stalls to the
  affected partition, so no async runtime rewrite is expected.
- Tiered EB log segments (backfill horizon; independent broker track).
