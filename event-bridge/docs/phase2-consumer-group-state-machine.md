# Phase 2 — consumer-group membership state machine (implementation brief)

> Focused brief for picking this up in a fresh session. It is the **one remaining**
> piece of the event-bridge "full engine model" refactor (tracked as task #14). It is
> deliberately scoped, behavior-sensitive, and **must be validated on a running cluster**,
> not just by unit tests.

## Goal

Make consumer-group **membership and target assignment first-class replicated state on the
coordinator stream**, driven by log commands and processed the engine way — replacing the
current best-effort `replicateGroupMetadata` side-channel. Run the **assignor asynchronously**
(off the stream's processing path). Keep the heartbeat as request/response.

This is essentially Kafka's KIP-848 model (server-side assignment, async target computation,
reconciliation via heartbeat, epochs).

## What's already in place (do NOT rebuild)

The engine framework is done and committed (module `event-bridge-stream`):

- `RecordProcessingEngine` (one per stream) + `RecordProcessors` factory + `TypedRecordProcessor`
  + `EventAppliers`/`TypedEventApplier`.
- `Writers { state(), command(), response() }` injected at construction.
  - `state().appendFollowUpEvent(key, intent, value)` — append event + apply in one step.
  - `command().appendFollowUpCommand(key, intent, value)` — append a follow-up COMMAND (this is
    how a processor schedules more work, but for the rebalance we use the async task below).
  - `response().respond(command, responseValue)` — staged reply, flushed after commit via
    `RequestResponseBridge` (correlates by `requestId`). `ReplicatedStream.writeRequest(intent,
    valueType, command) -> CompletableFuture<byte[]>` is the request entry point.
- **Async background tasks** (the verified seam for the assignor):
  `RecordProcessors.scheduleAtFixedRate(Duration, io.camunda.zeebe.stream.api.scheduling.Task)`;
  the engine schedules it in `init()` via `ProcessingScheduleService.runAtFixedRateAsync` on
  `AsyncTaskGroup.ASYNC_PROCESSING` — runs **only on the leader, off the processing path**.
  A `Task` gets only a `TaskResultBuilder` (`appendCommandRecord(intent, value)`); it **cannot
  read the stream's RocksDB** — it reads a thread-safe mirror and emits a command.

`COMMIT_OFFSET` and topic admin already use this model end to end — copy their shape
(`OffsetCommitProcessor` / `CreateTopicProcessor` + `OffsetCommitValidator` / `TopicValidator`).

## The decision: what goes through the stream vs request/response

| Through the stream (commands, validated in processor, replicated) | Request/response (in-memory, no log write) |
|---|---|
| `JOIN_GROUP`, `LEAVE_GROUP`, `REBALANCE` (internal) | `HEARTBEAT` |
| (`COMMIT_OFFSET` — already done)                    | |

**Heartbeat stays R/R** — confirmed against both Kafka protocols: the heartbeat RPC never writes
to the log for liveness; only state transitions do. So the heartbeat reads the member's target from
replicated state, computes the assign/revoke delta in memory, and replies. Session liveness is an
in-memory timer; **eviction** writes a `LEAVE_GROUP` command on timeout.

## Target design

```
JOIN_GROUP  (cmd) --> JoinGroupProcessor: resolve member (static via instanceId / new dynamic),
                      idempotent active-rejoin, bump member+group epoch
                  --> MEMBER_JOINED (event) --> applier: add member to durable roster + mirror
                  --> reply JoinGroupResponse(memberId, memberEpoch, REBALANCE_IN_PROGRESS)

LEAVE_GROUP (cmd) --> LeaveGroupProcessor: epoch-fence
                  --> MEMBER_LEFT (event) --> applier: remove member + bump group epoch

async assignor Task (ASYNC_PROCESSING, leader): scan mirror for groupEpoch > assignmentEpoch
                  --> run BalancedStickyAssignor over roster
                  --> appendCommandRecord(REBALANCE, {groupId, groupEpoch, target})

REBALANCE   (cmd) --> RebalanceProcessor: if cmd.groupEpoch == current groupEpoch (still valid)
                  --> GROUP_REBALANCED (event) --> applier: set member targets, assignmentEpoch = groupEpoch
                      (idempotent: drop if assignmentEpoch already == groupEpoch)

HEARTBEAT   (R/R) --> read member target + epochs from mirror --> compute assign/revoke delta
                  --> reply. Liveness = in-memory timer; eviction -> writes LEAVE_GROUP.
```

### Replicated state (coordinator `ZeebeDb`)
- group: `groupId -> { groupEpoch, assignmentEpoch, partitionCount }`
- member: `(groupId, memberId) -> { instanceId(nullable=dynamic), memberEpoch, targetPartitions }`
- **thread-safe membership mirror** (`groupId -> snapshot`) maintained by the appliers, for the
  async assignor task to read off-actor (same pattern as the metadata `registryCache`).

Today the roster is an encoded blob in `DbGroupMetadataState` via `GroupMetadataCodec`
(`{assignmentEpoch, members[{memberId, instanceId, memberEpoch, partitions}]}`). Decide:
extend that blob with `groupEpoch` and keep decode-modify-encode in the appliers (small groups,
lowest churn), **or** introduce proper per-member column families (cleaner, more code). The blob
reuse is the faster path; first-class column families are the cleaner long-term shape.

### Reuse, don't rewrite
- `assignor/BalancedStickyAssignor` + `PartitionAssignment` — call from the async task unchanged.
- The **reconciliation logic** in `ConsumerGroup.reconcileAssignment` (assign/revoke deltas,
  `pendingRevocations`, `stableConsumers`, `restoreToConfirmedAssignment`) and **eviction** — keep
  this logic; it moves into the heartbeat handler, now reading the durable target (from the mirror)
  + the ephemeral owned set (reported per heartbeat). This is the tuned, fragile part.

## Build order (each step compiles; the module is only fully green when the set lands)

1. Intents (`CoordinatorIntent`): `JOIN_GROUP`/`MEMBER_JOINED`, `LEAVE_GROUP`/`MEMBER_LEFT`; keep
   `REBALANCE_GROUP`/`GROUP_METADATA_COMMITTED` as the rebalance/target command+event (the assignor
   task emits `REBALANCE_GROUP`).
2. Records for the membership commands/events (member id, instanceId, epoch; group epoch).
3. Replicated state + appliers (`MemberJoinedApplier`, `MemberLeftApplier`, `GroupRebalancedApplier`)
   maintaining durable roster + mirror + epochs.
4. Processors: `JoinGroupProcessor`, `LeaveGroupProcessor`, `RebalanceProcessor` (+ a
   `MembershipValidator`, per the validator pattern).
5. Async assignor `Task`, registered via `processors.scheduleAtFixedRate(...)` in
   `CoordinatorStream.createRecordProcessor`.
6. Rewire `CoordinationManager`: `handleJoinGroup`/`handleLeaveGroup` write commands and return the
   reply future (like `handleCommit`); `handleHeartbeat` reads target from the mirror + reconciles
   in memory; eviction timer writes `LEAVE_GROUP`. **Retire `replicateGroupMetadata`** and the
   in-memory `ConsumerGroupRegistry` (membership now comes from replicated state).
7. Make `ConsumerGroup` a **projection** of committed membership events (appliers populate it on
   leader *and* replay), or replace it with the durable state + an in-memory reconciliation tracker.

## Hazards — be careful here (this code is fragile)
- **Static-member rejoin idempotency.** A static member re-joining while its session is live must
  NOT bump the epoch or add a second session (see `registerMember` + the comment about the
  "perpetual rejoin storm" it caused). Preserve this exactly.
- **Reconciliation handshake.** `pendingRevocations` (don't assign a partition until its prior owner
  revokes), `stableConsumers`, the `STABILIZING -> STABILIZED` transition driven by heartbeats.
- **Failover restore.** Today `restoreGroups` rebuilds from replicated metadata on leader
  activation; the new model rebuilds from replayed membership events instead.
- **Schedule-service timing.** The engine builds processors in its *constructor* but the
  `ProcessingScheduleService` is only available in `init()` — the async task is registered as a
  `Task` object at construction and scheduled in `init()` (already wired in `RecordProcessingEngine`).
- **Offset-commit validation source.** `OffsetCommitValidator` reads `DbGroupMetadataState`. Once
  membership is authoritative replicated state, that validation becomes authoritative for free
  (no code change to the validator).

## Validation (REQUIRED before merge — unit tests will not catch the regressions)
Run the local cluster and exercise the full lifecycle:
- `event-bridge/run-local-cluster.sh` (3 nodes; see the `event-bridge-runtime-smoke` memory note).
- Join several consumers → observe a single debounced rebalance → assignment delivered via heartbeat
  → commit offsets (fenced by ownership) → leave → re-rebalance.
- Kill the coordinator leader → confirm the new leader replays membership + target and consumers
  re-attach **without** a rejoin storm.

## Related follow-ups (separate tasks)
- Command **rejection capability is now available** (engine-aligned, two parts): pair
  `Writers.rejection().appendRejection(command, type, reason)` (logs a `COMMAND_REJECTION` record)
  with `Writers.response().writeRejection(command, type, reason)` (fails the request future with a
  `CommandRejectionException`). Use it for genuine join/leave rejections (fenced epoch, unknown
  group) instead of a success-shaped response. Still deferred: mapping a rejection to an HTTP status
  + reason in the gateway/client.
- #8 remove the reused-engine-`ValueType` hack.
- KIP-848 parity: persist each member's reconciled epoch (durable reconciliation progress); we keep
  it ephemeral initially.
