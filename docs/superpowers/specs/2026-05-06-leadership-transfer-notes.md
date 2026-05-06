# Coordinated Leadership Transfer — Design Notes

Product Hub issue: https://github.com/camunda/product-hub/issues/3630

---

## What the feature does

Provides a reliable, controlled way to force each partition's leadership to the node
with the highest configured election priority. The existing `/actuator/rebalance`
endpoint is a fire-and-forget best-effort operation; this replaces it with a
coordinated protocol that can guarantee transfer when the target member is eligible.

Same `/actuator/rebalance` endpoint, gated by a feature flag:

- **Flag off (default):** existing fire-and-forget behaviour is preserved
- **Flag on:** new coordinated transfer runs

Desired leader is always the **highest-priority member** for the partition as configured
in the cluster configuration. Operators cannot specify a target.

---

## Transfer protocol (per partition)

1. **Pre-checks — skip partition if:**
   - Desired leader is already the current leader
   - Desired leader is offline
   - Desired leader's replication lag > configured threshold

2. **Transfer sequence — if pre-checks pass:**
   - Current leader **pauses command acceptance** (see details below)
   - Leader monitors replication lag until target is fully caught up (lag = 0 bytes)
   - Leader sends **TimeoutNow** signal to target → target skips election timer and
     immediately starts a voting round (one voting round for correctness; target wins
     because it has the full log and highest priority)
   - **Fallback timer:** if target has not caught up within configured duration, abort —
     leader resumes accepting commands, marks partition as failed, moves on to next partition

3. **Replication lag** = snapshot bytes remaining (if snapshot install in progress) +
   sum of log entry bytes from `matchIndex+1` to `lastLogIndex` for the target member.

4. Transfers proceed **one partition at a time** to minimise blast radius.

---

## Pausing command acceptance

"Stop accepting commands" means:
- The entry currently mid-write is allowed to complete (one entry grace)
- All **pending append operations fail** via the existing append listener failure path
  (same mechanism used during normal leader elections — reuse, don't reinvent)
- `ZeebePartition` is notified to **pause processing** (stream processor stops reading
  commands and writing results — the engine can't write results anyway, so it must stop)
- `ZeebePartition` is notified to **pause exporting** (simplest; can evaluate later whether
  to let exporting continue — it could keep running but can't acknowledge exported positions
  without writes, which is still a net benefit for data visibility; deferred evaluation)
- **Replication continues** — the leader keeps sending entries to followers normally

This is treated as a partial step-down: the leader holds the term and keeps replicating,
but is functionally unavailable for new work from the cluster's perspective.

**Throttling as alternative:** Rejected. Finding the right throttle level that lets the
desired leader catch up while not fully stopping availability is too risky; the marginal
benefit over a full pause is small (e.g., 80% backpressure ≈ fully paused for users).

---

## Replication lag in bytes (independent slice)

**Hard constraint: zero I/O in the hot path.**

### Log lag

**Proposed approach — incremental tracking:**
- On follower join or reset: calculate initial lag in bytes (some I/O acceptable here,
  infrequent event)
- On each append to a follower: increment lag counter by size of data being appended
- On follower acknowledgment: decrement by how much was confirmed received
- On follower reset: recalculate from scratch

**Shortcuts for initial calculation to minimise I/O:**
- **Segment descriptor:** segments have a fixed known size; if a whole segment needs
  replicating, use the descriptor size — no per-entry I/O needed
- **Sparse journal index:** extend to track cumulative byte offsets between indexed
  positions — pure in-memory for most of the range; only the last leg (closest indexed
  position to target index) needs any I/O
- **Entry metadata only:** reading size metadata doesn't require full deserialization,
  but still touches pages and may trigger mmap pre-fetching — not ideal

### Snapshot lag

Snapshot size must also be tracked without hot-path I/O.
**Proposed approach:** snapshot metadata file (separate from snapshot ID) contains
the snapshot size; this is read once and cached in-memory in the `SnapshotStore` when
a snapshot is committed. If a snapshot install is in progress to the target, snapshot
lag = total snapshot size minus bytes already sent.

### Verdict

Needs prototyping. Delivered as an **independent slice** within the epic — self-contained
enough to be built and validated separately before the coordinator/transfer slice.
Until this slice ships, the pre-check threshold falls back to entry count as a proxy.

---

## Transfer completion confirmation

After TimeoutNow is sent, the election proceeds within Raft normally. The old leader
observes the new term and new leader through Raft.

**Primary mechanism:** The coordinator reacts to topology updates — watches the cluster
topology until the partition leader matches the desired member, or the per-partition
timeout expires.

**Stretch goal:** Old leader explicitly notifies the coordinator upon discovering the
new leader, reducing polling latency. Deferred — adds state on the old leader and
crash-safety complexity; topology watching is sufficient for v1.

The per-partition fallback timer lives entirely on the partition leader (not the
coordinator), so coordinator crashes do not leave partitions stuck in a paused state.

---

## Orchestration: In-memory coordinator

### What it is

One designated broker (the existing cluster topology coordinator — lowest broker ID)
sequences the per-partition transfers. Any broker receiving a request proxies to the
coordinator using the same mechanism as backup scheduling/compaction and dynamic-config.
The coordinator holds in-memory state only.

### State

- Overall status: `IDLE` / `IN_PROGRESS` / `COMPLETED` / `FAILED`
- Ordered list of partition IDs to process
- Index of partition currently being processed
- Per-partition result: `PENDING` / `IN_PROGRESS` / `SKIPPED:<reason>` / `TRANSFERRED` / `FAILED:<reason>`

### Concurrent requests and cancellation

**If priorities are static:** subsequent `POST` while in-progress returns `202` with
current status (idempotent — everyone converges to the same desired state).

**If priorities are dynamic:** subsequent `POST` should return `409` with a pointer to
`DELETE`. **Decision pending — need to check with team** on whether dynamic priorities
are imminent.

**`DELETE /actuator/rebalance`:** Required. Cancels rebalance by preventing continuation
to further partitions. Does NOT interrupt an in-flight per-partition transfer (too many
edge cases). The current partition's transfer completes or times out naturally.

**No global timeout.** The only way to abort a running rebalance is `DELETE`. Per-partition
fallback timers are the safety net for individual stuck partitions.

### Coordinator unavailability

If the coordinator broker is down, rebalance cannot be started or queried. This is the
same limitation as dynamic-config. Only fully parallel mode avoids this — rejected for
other reasons (see alternatives). Acceptable trade-off for a manual maintenance operation.

On coordinator restart: in-memory state is lost. Already-transferred partitions remain
transferred (Raft durably elected the new leaders). Operator re-triggers to continue.

---

## Alternatives considered and rejected

### Parallel (stateless, no coordinator)

All partitions transferred simultaneously. No coordinator, no state, any node answers
any request. Restart-safe and idempotent.

**Rejected:** violates the "one partition at a time" user story. Worst case all partitions
lose their leader simultaneously. Could serve as a future opt-in "fast mode".

### Dynamic-config integration

Use the existing `dynamic-config` module for sequencing and persistence. Benefits:
free persistence, built-in dry-run and tracing, mutual exclusion with topology changes,
any-node routing already solved.

**Rejected:**
- Semantic mismatch: dynamic-config is declarative config deltas; leadership transfer is
  a live procedural protocol (pause, monitor real-time metric, send Raft signal)
- Blocks entire dynamic-config queue for potentially 8+ minutes on a 50-partition cluster
  (50 × 10s timeout), preventing unrelated operations like exporter pause or scale-in
- Partial-failure resume semantics are murky
- Coordinator unavailability problem is identical in both approaches

---

## Observability

Mirror the dynamic-config observability model (`zeebe/dynamic-config/`).

### Dry-run mode

`POST /actuator/rebalance` with `{"dryRun": true}` in the request body.
Returns what *would* happen per partition, without executing any transfer:

```json
{
  "dryRun": true,
  "plannedOperations": [
    { "partitionId": 1, "currentLeader": "broker-0", "desiredLeader": "broker-1",
      "currentLag": 1024, "action": "TRANSFER" },
    { "partitionId": 2, "currentLeader": "broker-1", "desiredLeader": "broker-1",
      "action": "SKIP", "reason": "ALREADY_LEADER" },
    { "partitionId": 3, "currentLeader": "broker-0", "desiredLeader": "broker-2",
      "currentLag": 5000000, "action": "SKIP", "reason": "LAG_EXCEEDS_THRESHOLD" },
    { "partitionId": 4, "currentLeader": "broker-0", "desiredLeader": "broker-2",
      "action": "SKIP", "reason": "DESIRED_LEADER_OFFLINE" }
  ]
}
```

### Metrics

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `zeebe.cluster.rebalance.status` | GAUGE | — | 0=idle, 1=in_progress, 2=completed, 3=failed |
| `zeebe.cluster.rebalance.partitions.pending` | GAUGE | — | |
| `zeebe.cluster.rebalance.partitions.completed` | GAUGE | — | |
| `zeebe.cluster.rebalance.partition.duration` | TIMER | `partitionId`, `outcome` | outcome: transferred/skipped/failed |
| `zeebe.cluster.rebalance.partition.attempts` | COUNTER | `partitionId`, `outcome` | |
| `zeebe.cluster.rebalance.partition.skip.reason` | COUNTER | `reason` | already_leader/offline/lag_too_high |
| `zeebe.raft.replication.lag.bytes` | GAUGE | `partitionId`, `followerId` | continuously updated per-follower lag in bytes; part of Slice 1; general Raft health metric, exists independently of rebalancing |
| `zeebe.cluster.rebalance.partition.paused` | GAUGE | `partitionId` | 1 while command acceptance halted, 0 otherwise; or retrofit into existing partition state metrics |
| `zeebe.cluster.rebalance.fallback.triggered` | COUNTER | `partitionId` | fires when per-partition fallback timer expires |
| `zeebe.cluster.rebalance.partition.pause.duration` | TIMER | `partitionId` | how long command acceptance was halted; the availability impact number operators tune lag threshold around |
| `zeebe.cluster.rebalance.elapsed` | TIMER | — | end-to-end rebalance duration including coordination overhead |
| `zeebe.cluster.balance.ratio` | GAUGE | — | fraction of partitions led by desired leader (0.0–1.0); passive health metric, independent of active rebalance; alert on < 1.0 |
| **TimeoutNow sent** | COUNTER? | `partitionId` | **maybe** — check if existing Raft request tracking already covers it |

Duration SLO buckets (from dynamic-config): 100ms, 1s, 2s, 5s, 10s, 30s, 60s, 120s,
180s, 300s, 600s.

### Structured logs

- Pre-check outcome per partition (INFO)
- Transfer start, lag reached zero, TimeoutNow sent (INFO)
- Fallback triggered (WARN)
- Transfer complete / failed (INFO / WARN)

---

## Key Raft internals involved

| Concept | Location |
|---|---|
| Election priority | `PriorityElectionTimer`, `RaftElectionConfig` |
| Per-follower `matchIndex` | `RaftContext` |
| Step-down | `LeaderRole.stepDown()` / `RaftPartition.stepDown()` |
| **TimeoutNow signal** | **New — needs implementing in Atomix Raft. Prior work: Ongaro dissertation §3.10 ("Leadership transfer extension"), "Consensus: Bridging Theory and Practice" (2014)** |
| **Command pause** | **New — needs implementing in `LeaderRole` + `ZeebePartition`** |
| **Lag in bytes** | **New — independent slice; see above** |
| **Snapshot size cache** | **New — snapshot metadata cached in `SnapshotStore`** |

---

## Open / deferred decisions

- Whether `POST` while in-progress returns `202` or `409`: **open, to be discussed during proposal review with team.** `409` is safer for future dynamic priority support but may be a breaking change if relaxed later; `202` is more ergonomic for static priorities. See concurrent POST section above for full reasoning.
- ~~One voting round vs. none~~ → **keep voting round for correctness**. Target sends `RequestVote` normally; the latency cost (one network round trip) is negligible vs. the correctness guarantee.
- Coordinator selection/discovery mechanism (how non-coordinator nodes locate coordinator)
- Exact lag threshold and timeout defaults: **TBD, requires benchmarking.** Direction: **low threshold + low timeout** (conservative). Low threshold = only attempt transfer when desired leader is nearly caught up → short pause. Low timeout = fall back quickly if catch-up stalls → bounded unavailability. Operators raise both to accept longer pauses in exchange for more successful transfers.

## Configuration

New subsection in unified config: `camunda.cluster.rebalance.*`

Initial properties:
- `camunda.cluster.rebalance.enabled` (boolean, default `false`) — feature flag
- `camunda.cluster.rebalance.replicationLagThreshold` — max lag in bytes before skipping
  a partition (exact default TBD; requires lag tracking slice to benchmark)
- `camunda.cluster.rebalance.partitionTimeout` — per-partition fallback timer duration
  (default TBD)

Designed for future expansion: auto-rebalancing triggers, scheduling, etc. will fit
naturally under `camunda.cluster.rebalance.*`.
- Whether exporting can continue during transfer pause: **tentatively yes** (continue exporting, can't ack positions without writes but still beneficial for data visibility); final decision during implementation
- Stretch goal: old leader notifies coordinator on new leader discovery

## GET /actuator/rebalance response shape (tentative, iterate during implementation)

Unified shape for both idle and in-progress states. `rebalanceStatus` and `skipReason`
only present when a rebalance is running or has run.

**No lag in the response** — each partition would need to report lag continuously, and
the coordinator observes state via topology updates. Surfacing lag in the response would
require high-frequency topology updates per partition to propagate that data, which is
too expensive for the value it provides.

```json
{
  "status": "IDLE | IN_PROGRESS | COMPLETED | CANCELLED | FAILED",
  "startedAt": "2026-05-06T14:30:00Z | null",
  "partitions": [
    {
      "partitionId": 1,
      "currentLeader": "broker-1",
      "desiredLeader": "broker-1",
      "balanced": true,
      "rebalanceStatus": "SKIPPED",
      "skipReason": "ALREADY_LEADER"
    },
    {
      "partitionId": 2,
      "currentLeader": "broker-0",
      "desiredLeader": "broker-1",
      "balanced": false,
      "rebalanceStatus": "IN_PROGRESS"
    },
    {
      "partitionId": 3,
      "currentLeader": "broker-0",
      "desiredLeader": "broker-2",
      "balanced": false,
      "rebalanceStatus": "PENDING"
    }
  ]
}
```

`balanced` reflects live reality (current leader == desired leader), independent of
rebalance status. Useful as a standalone diagnostic even when `status` is `IDLE`.

## Partition processing order

Default: **ascending partition ID** (1, 2, 3…). Simple and predictable.

**Future extension:** operator-specified partition list in the request body, enabling
partial rebalances (only rebalance a subset of partitions). Could also naturally enable
custom ordering. No concrete use case identified yet — if one emerges during v1 development,
could still be bundled in v1; otherwise deferred.

**Lag-ascending order** (fastest transfers first): rejected — coordinator would need
real-time lag data for all partitions upfront, which changes constantly and would require
broad propagation before the rebalance even starts.
