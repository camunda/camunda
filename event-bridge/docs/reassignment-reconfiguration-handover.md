# Handover: per-topic Raft reconfiguration (#18 grow-JOIN, #17 LEAVE)

> **Audience:** a new session picking up the two remaining gaps in event-bridge dynamic
> reconfiguration on branch `roman/optimize`. Everything else in the metadata control plane (broker
> liveness FSM, placement, fencing, heal *decision*, metadata-leader failover, topic readiness via
> logged leadership) is **done and cluster-validated**. The two open items are both in the
> **per-topic Raft group reconfiguration** path that the heal/reassign feeds.

## TL;DR

A topic's replica set is changed **grow-before-shrink**, one Raft member at a time:

1. Something sets a `target` assignment on the topic (`ReassignTopicProcessor` from a client
   `POST /v1/topics/{name}/reassign`, or `PlacementHealTask` moving replicas off a fenced/draining
   broker).
2. The metadata leader's change-coordinator drives `committed → target` one step at a time
   (`MetadataManager.driveReconfiguration` + `ReconfigurationPlanner.nextOp`): **JOIN the new member
   first, then LEAVE the old one.**
3. Each step is sent as a `ReconfigurationCommand` to the broker that must act; that broker runs the
   runtime `RaftPartition.join()` / `.leave()` and replies only on completion.

- **#18 — runtime grow-JOIN:** the JOIN step. Likely *already works* via the change-coordinator's
  retry, but the specific heal-onto-a-spare scenario was **never re-validated on a cluster after this
  session's bug-A/B fixes**. Verify it; if it still stalls, apply the same close→recreate→re-join
  retry the metadata passive join now uses.
- **#17 — LEAVE of a gone broker:** genuinely blocked. The LEAVE is routed to the *departing* member,
  which can't act if it was killed (or shuts down before the LEAVE commits when drained). Needs a
  **leader-driven remote-removal API in Atomix** + routing the LEAVE to a surviving member. This is a
  sensitive consensus-layer change the user deliberately postponed.

## The reconfiguration pipeline (exact code path)

```
trigger ─ ReassignTopicProcessor (client) / PlacementHealTask (heal)
            └─ appends TOPIC_REGISTERED with a `target` assignment (committed stays, target = goal)

metadata leader ─ MetadataManager.scheduleReconfiguration()   [RECONFIG_INTERVAL = 1s tick]
   └─ for each topic with hasTarget() and not already reconfiguring:
        driveReconfiguration(name):
          op = ReconfigurationPlanner.nextOp(committed, target)   // grow-before-shrink, 1 member/step
          if op empty → registerTopic(committed, target=∅)  // done
          else reconfigurationExecutor.execute(op, partitionMembers, partitionCount)
                 ├─ success → registerTopic(advanced committed) → driveReconfiguration(name)  // chain
                 └─ failure → reconfiguring.remove(name)  // next 1s tick re-derives + retries

reconfigurationExecutor (lambda in BrokerBootstrap)
   └─ comm.send(ReconfigurationCommand.SUBJECT, {kind, topic, partitionId, member, members,
                partitionCount}, to = MemberId "broker-<op.member()>", timeout 30s)

target broker ─ BrokerBootstrap comm.replyToAsync(ReconfigurationCommand.SUBJECT)
   └─ JOIN  → TopicReconciler.join(topic, partitionId, members, partitionCount)
                └─ PartitionBootstrapper.joinDataPartition → startDataPartition(join=true)
                     └─ raftPartition.join(managementService, snapshotStore)   // Atomix runtime join
      LEAVE → TopicReconciler.leave(topic, partitionId)
                └─ PartitionBootstrapper.leaveDataPartition → raftPartition.leave()   // SELF-only
   (replies a 0-byte confirm only once the join/leave future completes)
```

Key property: the change-coordinator **persists each advance** (writes the grown `committed` to the
log before the next step) and re-derives from committed/target after failover, so it is failover-safe
and idempotent. A failed step just drops `reconfiguring` and the next 1s tick retries. `startDataPartition`
**rolls back its tracking on a failed join** (removes from `dataPartitions`/`createdPartitions`, closes
the lifecycle) so a retry genuinely re-attempts rather than no-op'ing on a half-started partition.

## #18 — runtime grow-JOIN

### Status / what's uncertain

The JOIN already has an orchestration retry (change-coordinator, 1s) **and** rollback-on-failure, so
it is effectively a close→recreate→re-join loop. A 2-days-older note claims "RF 1→3 converges
step-by-step, real joins succeed" (see [[event-bridge-dynamic-topics]] memory). **But** this session
found and fixed two bugs that previously *blocked* a spare broker from participating at all:

- **bug A** — a passive metadata observer never replayed the registry, so it never provisioned its
  assigned partitions (fixed: `PASSIVE` added to the REPLAY arm of `RaftPartitionLifecycle`).
- **bug B** — the metadata passive join raced leader election and gave up (fixed:
  `PartitionBootstrapper.joinMetadataAsPassiveWithRetry`, close→recreate→re-join with backoff).

The **heal-grow-onto-a-spare** scenario was *not* re-run end-to-end after those fixes. So the open
work is primarily **verification**, with a fallback fix ready if needed.

### What to do

1. **Reproduce the heal-grow scenario on a cluster** (this is the canonical #18 repro):
   - 4 brokers, RF 3, so a topic places on 3 of 4 leaving a spare. Use the 4-node script written this
     session at `/private/tmp/.../scratchpad/run4.sh` (or recreate: `run-local-cluster.sh` with
     `CLUSTER_SIZE=4`, `REPLICATION_FACTOR=3`, contact points incl. `localhost:26532`). **Start the
     voters first, then the spare**, to avoid the passive-join race masking the test.
   - Create a topic; confirm it reaches ACTIVE on 3 brokers and the spare is a registered, ACTIVE
     broker.
   - Kill one of the 3 replica-holders. After the 10s session timeout it is fenced; `PlacementHealTask`
     sets a `target` swapping the fenced replica for the spare; the change-coordinator drives a
     **JOIN of the spare** into the affected per-topic partition groups.
   - **Expected:** the spare joins those groups (`Raft partition event-bridge-topic-…/<p> joined`),
     `committed` advances, the topology shows the spare in the replica set, no `NoRemoteHandler`.
2. **If the JOIN stalls** (repeated "Sent join request to all known members, but all failed" /
   `joinWithRetry` give-up because the per-topic group has no reachable leader during the attempt):
   wrap `startDataPartition(join=true)`'s `raftPartition.join(...)` in the **same retry** as the
   metadata passive join. The template is `PartitionBootstrapper.joinMetadataAsPassiveWithRetry` /
   `schedulePassiveJoinRetry` / `passiveJoinRetryDelay`: on failure, `created.raftPartition().close()`
   (frees the Raft subjects), then re-create + re-join after a capped backoff. Note the join here is
   driven by a `ReconfigurationCommand` request that the broker must *reply to on completion*, so the
   retry must complete the reply only once the join finally succeeds (or fail it so the
   change-coordinator retries the whole op — pick one; don't do both unboundedly).
   - **Why the existing orchestration retry may be enough:** each change-coordinator retry already
     re-sends the command → fresh `startDataPartition` → fresh `raftPartition.join()`. The in-broker
     retry only helps if a single command must survive a transient unreachability without failing the
     reply. Decide based on what the repro shows.

### Why join can fail even with all brokers alive (background)

Atomix `ReconfigurationHelper.joinWithRetry` drains a queue of "assisting members" — one attempt per
member — and fails when the queue empties; before the group has an elected leader every member
answers `NO_LEADER`. `DefaultRaftServer.start` latches a one-shot `openFutureRef` (never reset on
failure), so you **cannot** re-drive `RaftServer.join()` on the same server — a retry must go through
`RaftPartition.join()` again, which rebuilds the server via `initServer` (hence the close-first
requirement). Full analysis in [[event-bridge-noremotehandler-root-cause]].

## #17 — LEAVE of a gone broker (leader-driven remote removal)

### The problem

After a JOIN grows the replica set, `nextOp` returns a **LEAVE** of the extra/fenced member. The
executor sends the LEAVE `ReconfigurationCommand` to `MemberId "broker-<member>"` — **the departing
member**. `RaftPartition.leave()` is *self-only* (Atomix exposes only `join`/`joinAsPassive`/`leave`/
`promote` — there is no "remove member X"). So:

- a **killed** broker can't receive or act on the LEAVE → the command times out → the
  change-coordinator retries forever → the reassignment stalls at the shrink step;
- a **drained** broker shuts down before its LEAVE commits.

This is why a kill/drain currently heals the *decision* (target is set, JOIN of the spare can proceed)
but never completes the **removal** of the dead replica.

### The fix (two parts)

1. **Atomix:** add a leader-driven remote-removal API to `RaftPartition`/`RaftServer` — the partition
   **leader** proposes a configuration change removing the dead member and commits it with the
   surviving quorum (a normal Raft membership change, not a force-reconfigure). This is the sensitive
   consensus-layer change; treat it carefully (no force-reconfiguration paths; preserve quorum safety;
   add `ReconfigurationTest` coverage).
2. **event-bridge:** route the LEAVE of a gone broker to a **surviving** member (the partition leader)
   instead of the departing one — i.e. `reconfigurationExecutor`/`ReconfigurationCommand` for a LEAVE
   targets a live member and asks it to remove `op.member()`. With leader-driven removal, a drained
   broker also no longer has to stay alive through the move.

### Safety guardrails (do not regress)

The change-coordinator is **grow-before-shrink** and advances `committed` only after the per-topic
group confirms the step (which needs that group's quorum). A new replica joins and catches up before
the old one is removed. If a majority of a partition's replicas are down the group loses quorum and
the move safely **stalls/retries** rather than dropping survivors — degraded availability, not loss.
**Never add a force-reconfiguration path** to "unstick" it.

## Key files

| Concern | File |
|---|---|
| Change-coordinator (drive committed→target, retry) | `event-bridge-cluster-metadata/.../MetadataManager.java` (`scheduleReconfiguration`, `driveReconfiguration`) |
| Step planning (grow-before-shrink) | `event-bridge-cluster-metadata/.../reconfig/ReconfigurationPlanner.java` |
| Step execution wiring (send to broker, reply-on-confirm) | `event-bridge-broker/.../bootstrap/BrokerBootstrap.java` (`reconfigurationExecutor`, `comm.replyToAsync(ReconfigurationCommand.SUBJECT)`) |
| Heal decision (sets target off non-active brokers) | `event-bridge-cluster-metadata/.../processing/PlacementHealTask.java` |
| Broker-side join/leave | `event-bridge-broker/.../bootstrap/TopicReconciler.java` (`join`/`leave`) → `PartitionBootstrapper.java` (`joinDataPartition`/`leaveDataPartition`/`startDataPartition`) |
| **Proven retry template (mirror for #18)** | `PartitionBootstrapper.joinMetadataAsPassiveWithRetry` / `schedulePassiveJoinRetry` / `passiveJoinRetryDelay` |
| Atomix join/leave (the `leave()` self-only limit, #17) | `zeebe/atomix/.../raft/partition/RaftPartition.java`, `.../impl/ReconfigurationHelper.java`, `.../impl/DefaultRaftServer.java` |

## Repro & validation

- Build + run: `event-bridge/run-local-cluster.sh start` (3 nodes). For the 4-node spare scenario,
  use a `CLUSTER_SIZE=4`/`RF=3` variant (the session's `scratchpad/run4.sh`, or adapt the script).
- Topology: `curl -s localhost:8080/v1/topology | jq` (shows brokers + per-partition replicas +
  topic status).
- Reassign trigger: `POST /v1/topics/{name}/reassign?replicationFactor=N`.
- Watch: `…/node-<n>/node.log` for `Reassignment …`, `Raft partition …/<p> joined`,
  `joinWithRetry`/`Sent join request to all known members`, `Healing topic …`, role changes.
- Launch details (JVM flags, ports): [[event-bridge-runtime-smoke]].

## Context / related

- Session commits on `roman/optimize`: bug A/B fixes; `829fbaa` (spread placement + leadership
  state); `ce42136` (leadership RPC + gossip removal); `de44d11` (docs).
- Memories: [[event-bridge-metadata-plane]] (control-plane state, what's done), [[event-bridge-dynamic-topics]]
  (architecture + the reassignment/change-coordinator design), [[event-bridge-noremotehandler-root-cause]]
  (the joinWithRetry / `openFutureRef` analysis), [[event-bridge-runtime-smoke]] (local launch).
- The whole event-bridge architecture still lacks an ADR — consider drafting one for the per-topic
  Raft model + its bounded-topic-count constraint before the Atomix #17 change.
