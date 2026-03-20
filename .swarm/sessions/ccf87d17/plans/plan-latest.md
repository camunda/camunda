# Plan

**Timestamp:** 2026-03-19T21:57:28.807Z

## Original Request

Now, we want to refactor the consumer group concept in the event bridge. Here are the details

# :brain: 0. Simplified Model (Partition-Only)

We eliminate topics entirely:

```text
Partitions = fixed set of work units
Consumers = workers in a group

Group State:
  current_assignment: Map<ConsumerId, Set<PartitionId>>
  target_assignment:  Map<ConsumerId, Set<PartitionId>>
```

---

# :gear: 1. Default Configuration (IMPORTANT)

These defaults make the system stable out of the box:

```yaml
heartbeat_interval_ms: 3000
session_timeout_ms: 10000
rebalance_interval_ms: 2000
max_partitions_per_consumer: unlimited
assignment_strategy: "BALANCED_STICKY"
ack_timeout_ms: 5000
max_inflight_revocations: 100
epoch_start: 1
```

---

# :globe_with_meridians: 2. REST API (Final Spec)

---

## 2.1 Heartbeat (Single Control Endpoint)

### Endpoint

```http
POST /groups/{groupId}/consumers/{consumerId}/heartbeat
```

---

### Request (with defaults)

```json
{
  "epoch": 0,
  "owned_partitions": [],
  "load": 0.0,
  "capacity": 1.0,
  "metadata": {}
}
```

---

### Field Defaults

```text
epoch:                default = 0 (new consumer)
owned_partitions:     default = []
load:                 default = 0.0
capacity:             default = 1.0
metadata:             default = {}
```

---

### Response

```json
{
  "epoch": 1,
  "revoke": [],
  "assign": [],
  "full_assignment": [],
  "status": "OK"
}
```

---

### Response Defaults

```text
epoch:            always >= request.epoch
revoke:           default = []
assign:           default = []
full_assignment:  optional (empty if not needed)
status:           "OK"
```

---

# :white_check_mark: 2.2 Acknowledge

### Endpoint

```http
POST /groups/{groupId}/consumers/{consumerId}/ack
```

---

### Request

```json
{
  "epoch": 1,
  "revoked": [],
  "assigned": []
}
```

---

### Defaults

```text
revoked:  default = []
assigned: default = []
```

---

### Response

```json
{
  "status": "OK"
}
```

---

# :heavy_plus_sign: 2.3 Join (Optional)

You can skip this and rely on heartbeat auto-registration.

### Default behavior:

```text
IF consumerId not known:
  auto-register on first heartbeat
```

---

# :bricks: 3. Coordinator Internal State

```java
class ConsumerState {
    String consumerId;
    long lastHeartbeat;
    Set<String> ownedPartitions = new HashSet<>();
    double load = 0.0;
    double capacity = 1.0;
}

class GroupState {
    Map<String, ConsumerState> consumers = new HashMap<>();
    Map<String, String> partitionOwner = new HashMap<>(); // partition → consumer
    Map<String, String> targetOwner = new HashMap<>();
    long epoch = 1;
}
```

---

# :repeat: 4. Assignment Strategy (Default: BALANCED_STICKY)

## Goals:

1. Balance partitions evenly
2. Minimize movement
3. Respect capacity

---

## Algorithm

```text
1. Start from current_assignment
2. Remove dead consumers' partitions → UNASSIGNED
3. Keep existing assignments where valid
4. Assign unassigned partitions to least-loaded consumers
5. Rebalance if skew > 1 partition difference
```

---

## Default Behavior

```text
- Each consumer gets ≈ (total_partitions / total_consumers)
- Movement is minimized
```

---

# :arrows_counterclockwise: 5. Delta Computation

For each consumer:

```text
revoke = owned_partitions - target_partitions
assign = target_partitions - owned_partitions
```

---

# :warning: 6. Two-Phase Partition Transfer

## Rule (STRICT)

```text
Partition MUST NOT be reassigned until revoked + ACKed
```

---

## Coordinator Logic

```java
if (partition is moving from A → B):

  send revoke to A

  WAIT until:
    ACK received OR ack_timeout_ms exceeded

  assign to B
```

---

# :heartbeat: 7. Heartbeat Handling

```java
onHeartbeat(req):

  consumer = upsertConsumer(req.consumerId)

  consumer.lastHeartbeat = now
  consumer.ownedPartitions = req.owned_partitions
  consumer.load = req.load
  consumer.capacity = req.capacity

  if rebalanceNeeded():
      computeTargetAssignment()
      epoch++

  delta = computeDelta(consumer)

  return delta + epoch
```

---

# :stopwatch: 8. Failure Detection

## Rule

```text
IF now - lastHeartbeat > session_timeout_ms:
  consumer = DEAD
```

---

## Action

```text
FOR each partition owned:
  mark UNASSIGNED
```

---

# :repeat: 9. Rebalance Trigger Conditions

```text
- new consumer appears
- consumer times out
- partition count changes
- load imbalance detected
```

---

# :jigsaw: 10. Partition State Machine

```text
UNASSIGNED
ASSIGNED(C)
REVOKING(C)
```

---

## Default Transitions

```text
UNASSIGNED → ASSIGNED(C)

ASSIGNED(C1) → REVOKING(C1)

REVOKING(C1) → UNASSIGNED → ASSIGNED(C2)
```

---

# :repeat: 11. Epoch Rules

```text
epoch starts at 1
increment on every rebalance
```

---

## Consumer Behavior

```text
IF response.epoch < current_epoch:
  IGNORE

IF response.epoch > current_epoch:
  reconcile fully
```

---

# :zap: 12. Minimal Consumer Loop (Final)

```java
while (true) {

    res = POST /heartbeat

    for (p : res.revoke) {
        stopProcessing(p);
        commit(p);
    }

    for (p : res.assign) {
        startProcessing(p);
    }

    POST /ack

    sleep(heartbeat_interval_ms)
}
```

---

# :classical_building: 13. Minimal Coordinator Loop

```java
while (true) {

    removeDeadConsumers()

    if (rebalanceNeeded()) {
        computeTargetAssignment()
        epoch++
    }

    processAcks()
}
```

---

# :fire: 14. Hard Guarantees (Non-Negotiable)

### 1. Single ownership

```text
One partition → one consumer
```

---

### 2. Revoke-before-assign

```text
No double processing ever
```

---

### 3. Incremental movement

```text
Only move necessary partitions
```

---

### 4. No global pause

```text
Other consumers continue unaffected
```

---

### 5. Eventual convergence

```text
System always stabilizes
```

---

# :compass: Final Mental Model

```text
Consumers repeatedly say:

  "Here’s what I currently own"

Coordinator responds:

  "Give up these partitions, take these partitions"

No phases.
No leaders.
No stop-the-world.

Just continuous convergence toward balance.

Additionally:
* remove the generation property
* broker-0 is the coordinator

## Refined Requirements

## Structured Requirements: Consumer Group Refactoring (Event Bridge)

### Problem Statement
The current consumer group model requires an explicit `subscribe` call before heartbeating, uses a simple `generation` counter, lacks two-phase partition transfer safety, and provides no delta-based assignment signaling through the heartbeat. This refactoring replaces it with a continuous convergence model: heartbeat becomes the single control channel, consumers auto-register, partitions follow a strict state machine, and `epoch` replaces `generation` throughout.

---

### Acceptance Criteria (Testable)

1. A consumer that sends its first heartbeat is auto-registered; no prior `subscribe` call is required.
2. Heartbeat response includes `epoch`, `revoke`, `assign`, and `full_assignment` lists.
3. A partition being moved from consumer A → B is never assigned to B until A has ACKed the revocation **or `ack_timeout_ms` has elapsed**.
4. A partition is sent for revocation to at most one consumer at a time.
5. If a consumer fails to ACK within `ack_timeout_ms`, the coordinator proceeds with reassignment anyway.
6. `epoch` starts at 1 and increments on every rebalance.
7. A consumer receiving a response `epoch > current_epoch` performs full reconciliation; a response with `epoch < current_epoch` is ignored.
8. Dead consumers (no heartbeat within `session_timeout_ms`) have their partitions released and rebalanced.
9. Rebalance skew is ≤ 1 partition between any two consumers (BALANCED_STICKY: minimize movement, keep existing valid assignments).
10. Commit offset remains a separate endpoint — not merged with ACK.
11. `load` and `capacity` are **not** tracked anywhere in the system.

---

### Technical Requirements

#### A. Internal State (`ConsumerGroupRegistry`)
- **Rename** `generation` → `epoch`; **starts at 1** (not 0).
- **Per-group record field:**
  - `configuredPartitionCount: int` — the partition count set at group creation time. Used to detect partition count changes: on each rebalance cycle the coordinator compares `configuredPartitionCount` against `currentAssignment.size() + unassigned.size()`, and if they differ it reconciles the tracked partition set and increments `epoch`.
- **Per-consumer tracked state:**
  - `ownedPartitions: Set<Integer>` — partition IDs the consumer most recently reported
  - `pendingRevoke: Set<Integer>` — partitions instructed to revoke (sent in a heartbeat response) but not yet ACKed by this consumer
  - `pendingAssign: Set<Integer>` — partitions instructed to assign (sent in a heartbeat response) but not yet ACKed by this consumer
  - `ackDeadline: Instant` — the absolute deadline by which an ACK must arrive; reset on each heartbeat that returns a non-empty `revoke` or `assign` list; `null` when no pending items exist
- **Partition state machine per partition:**
  ```
  UNASSIGNED → PENDING_ASSIGN(consumerId)    [coordinator sends assign in heartbeat]
  PENDING_ASSIGN(consumerId) → ASSIGNED(consumerId)    [consumer ACKs the assigned partition]
  ASSIGNED(C1) → REVOKING(C1)    [coordinator sends revoke in heartbeat]
  REVOKING(C1) → UNASSIGNED → PENDING_ASSIGN(C2)    [C1 ACKs revoke; rebalance targets C2]
  ```
  A partition in `PENDING_ASSIGN(C)` has been instructed to consumer C but is not confirmed until the ACK arrives. It is treated as unconfirmed for the purposes of `currentAssignment`.
- **Global coordinator maps:**
  - `currentAssignment: Map<partitionId, consumerId>` — confirmed (`ASSIGNED`) assignments only
  - `targetAssignment: Map<partitionId, consumerId>` — desired assignments after the current rebalance
  - `partitionState: Map<partitionId, PartitionState>` — one of `{UNASSIGNED, PENDING_ASSIGN(consumerId), ASSIGNED(consumerId), REVOKING(consumerId)}`
- **Inflight revocations cap and deferral queue:**
  - Inflight revocations count = `∑ |pendingRevoke[C]|` across all consumers.
  - Cap: `max_inflight_revocations = 100`. When the inflight count equals the cap, new `ASSIGNED → REVOKING` transitions are deferred.
  - `pendingReassignment: Queue<ReassignmentTask>` — ordered queue of deferred reassignments blocked by the cap, where `ReassignmentTask = { partitionId: int, toConsumerId: String }`. The coordinator drains this queue (in FIFO order) whenever the inflight count drops below the cap (i.e., after ACKs are processed or timeouts occur).
- BALANCED_STICKY rebalance: keep existing valid assignments, assign orphaned partitions to least-loaded consumer (by partition count), limit skew to ≤ 1.
- Remove `addOrRefresh()` separate subscribe flow — unify into `heartbeat()` upsert.

#### B. `CoordinatorActor`
- **Remove** `subscribe()` method and `SubscribeResult` record.
- **Replace** `heartbeat(groupId, consumerId)` with `heartbeat(groupId, consumerId, clientEpoch, ownedPartitions)` returning `HeartbeatResult { epoch, revoke, assign, fullAssignment }`.

  **Processing the client-sent `clientEpoch`:** The coordinator uses `clientEpoch` (the epoch the consumer last observed) to decide whether `fullAssignment` must be populated:
  - If `clientEpoch < coordinatorEpoch`: a rebalance has occurred since the consumer's last heartbeat; the coordinator populates `fullAssignment` with the consumer's complete current `targetAssignment`. `revoke` and `assign` are empty lists in this case — the client reconciles entirely from `fullAssignment`. The coordinator does **not** mutate `pendingRevoke[C]`, `pendingAssign[C]`, or any partition states on this path; those are updated when the subsequent ACK arrives.
  - If `clientEpoch == coordinatorEpoch`: the coordinator sends only deltas (`revoke` / `assign`); `fullAssignment` is an empty list. See delta computation below.
  - If `clientEpoch > coordinatorEpoch`: this is unexpected (client epoch cannot lead the coordinator); the coordinator logs a warning, treats it as `clientEpoch == coordinatorEpoch`, and returns the normal delta response. The coordinator does not reject or error the heartbeat.
  - The coordinator **never** rejects a heartbeat based on `clientEpoch` alone — it is informational only and does not gate registration or assignment.

  **Delta computation (applies when `clientEpoch == coordinatorEpoch`):**

  Let `owned` = `ownedPartitions` reported by consumer C in this heartbeat, `target` = `targetAssignment[C]`, `pRevoke` = `pendingRevoke[C]`, `pAssign` = `pendingAssign[C]`.

  1. **Compute new revocations:** `newRevoke = (owned − target) − pRevoke`. These are partitions C claims to own that are not in the target, excluding any already pending revocation. For each partition in `newRevoke`: transition `ASSIGNED(C) → REVOKING(C)`; add to `pendingRevoke[C]`. If the inflight count would exceed `max_inflight_revocations`, defer the excess to `pendingReassignment` (with `toConsumerId` = the consumer targeted by `targetAssignment`).
  2. **Compute new assignments:** `newAssign = (target − owned − pAssign)` restricted to partitions currently in `UNASSIGNED` state, subject to available inflight slots (`max_inflight_revocations − current inflight count`). For each partition in `newAssign`: transition `UNASSIGNED → PENDING_ASSIGN(C)`; add to `pendingAssign[C]`.
  3. **Defer overflow:** Any eligible partition from step 2 that could not be assigned due to the inflight cap is enqueued in `pendingReassignment` with `toConsumerId = C`.
  4. Return `revoke = newRevoke`, `assign = newAssign`.

- **Add** `ack(groupId, consumerId, epoch, revoked, assigned)` returning `AckResult { status }`.

  **Epoch check:** If `epoch != coordinatorEpoch`, log a warning and silently discard the ACK (make no state changes). Return `AckResult { status: "OK" }`. No error is surfaced to the consumer; the consumer will observe `res.epoch > currentEpoch` on its next heartbeat and perform a full reconcile, which self-corrects any inconsistency.

  **Processing `revoked`:**
  - For each partition in `revoked` that is in `REVOKING(consumerId)`: transition `REVOKING(consumerId) → UNASSIGNED`; remove from `pendingRevoke[consumerId]`; then immediately check `targetAssignment` — if the target assigns this partition to another consumer, process it from `pendingReassignment` or directly initiate `UNASSIGNED → PENDING_ASSIGN(targetConsumer)` if inflight slots are available.
  - For each partition in `revoked` that is **not** in `REVOKING(consumerId)`: log a warning and ignore; coordinator state prevails.
  - Partitions the coordinator requested for revocation (`pendingRevoke[consumerId]`) that are **absent** from `revoked` remain in `REVOKING` state. They are not implicitly acknowledged and continue to wait for an explicit ACK until `ack_timeout_ms` elapses.

  **Processing `assigned`:**
  - For each partition in `assigned` that is in `PENDING_ASSIGN(consumerId)`: transition `PENDING_ASSIGN(consumerId) → ASSIGNED(consumerId)`; set `currentAssignment[partitionId] = consumerId`; remove from `pendingAssign[consumerId]`.
  - For each partition in `assigned` that is already in `ASSIGNED(consumerId)` (the partition was previously held by this consumer and included in a full-reconcile ACK as part of `fullAssignment`): treat as a no-op (idempotent confirmation). No state transition is required, and `currentAssignment` is already correct.
  - For each partition in `assigned` in any other state (e.g., `ASSIGNED` to a different consumer, `REVOKING`, or `UNASSIGNED`): log a warning and ignore; the coordinator does not add unsolicited assignments to `currentAssignment`.

  **`assigned` field semantics on a full-reconcile ACK:** When the client performs a full reconcile it sends `assigned: res.fullAssignment`, which includes **all** partitions the consumer is now taking ownership of — including partitions it was already processing before the reconcile (previously-`ASSIGNED(C)` partitions that remain in the new target). The coordinator processes these exactly as described above: `PENDING_ASSIGN(C) → ASSIGNED(C)` for newly dispatched partitions, no-op for already-`ASSIGNED(C)` ones. This is expected and correct; the coordinator imposes no special full-reconcile mode.

- Keep `commitOffset()` unchanged.
- Rename `generation` → `epoch` in `AssignmentResult` and all result records.
- **Coordinator loop** (runs at `rebalance_interval_ms`):
  1. Evict dead consumers (no heartbeat within `session_timeout_ms`): transition their `ASSIGNED`, `PENDING_ASSIGN`, and `REVOKING` partitions to `UNASSIGNED`; trigger rebalance.
  2. Process ACK timeouts: for each consumer where `ackDeadline` has passed:
     - Each partition in `pendingRevoke[C]` that is in `REVOKING(C)`: force `REVOKING(C) → UNASSIGNED`; clear from `pendingRevoke[C]`; enqueue in `pendingReassignment`.
     - Each partition in `pendingAssign[C]` that is in `PENDING_ASSIGN(C)`: force `PENDING_ASSIGN(C) → UNASSIGNED`; clear from `pendingAssign[C]`; enqueue in `pendingReassignment`.
     - Both types of forced transitions constitute a reassignment and **increment `epoch`** (once per timeout-processing batch per coordinator loop iteration, not once per partition).
  3. Drain `pendingReassignment`: while inflight count < `max_inflight_revocations` and queue is non-empty, dequeue the next `ReassignmentTask` and, if the partition is `UNASSIGNED`, initiate `UNASSIGNED → PENDING_ASSIGN(toConsumerId)`; add to `pendingAssign[toConsumerId]`. If the partition is no longer `UNASSIGNED` (e.g., already reassigned), discard the task.
  4. Check for partition count change: compare `configuredPartitionCount` against `currentAssignment.size() + unassigned.size()`; if they differ, reconcile the tracked partition set and trigger a rebalance.
  5. Trigger BALANCED_STICKY rebalance if needed (new consumer joined, eviction detected, partition count change): recompute `targetAssignment`; increment `epoch`.
- **Rebalance triggers** — the following events constitute a rebalance and cause `epoch` to increment:
  - A new consumer joins (first heartbeat from an unknown consumer ID).
  - A consumer is evicted (no heartbeat received within `session_timeout_ms`).
  - The total number of partitions in the group changes (detected as above).
  - An ACK timeout forces a partition from `REVOKING → UNASSIGNED` or `PENDING_ASSIGN → UNASSIGNED` (reassignment event).
  - No other event (e.g., a routine heartbeat from an already-known consumer, or a successful ACK) increments the epoch.
- **Partition count change mechanism:** The group's configured partition count is stored as `configuredPartitionCount` in the consumer group record (Section A) and set at group creation time. To change the partition count after creation, a caller must invoke a dedicated admin operation (`PUT /v1/groups/{groupId}/config`, body `{ partitionCount: N }`; this endpoint is out of scope for the current refactoring but must be accounted for in the data model). The coordinator detects a partition count change by comparing `configuredPartitionCount` against `currentAssignment.size() + unassigned.size()` on each rebalance cycle. On a mismatch it reconciles the tracked partition set and increments the epoch.
- **Remove** `ConsumerNotRegisteredException` for heartbeat — unknown consumer is now auto-registered.

#### C. Configuration (`EventBridgeProperties`)
Update/add the following:
| Property | Default |
|---|---|
| `consumer.heartbeatTimeoutMs` → `consumer.sessionTimeoutMs` | `10000` |
| `consumer.rebalanceIntervalMs` (new) | `2000` |
| `consumer.ackTimeoutMs` (new) | `5000` |
| `consumer.maxInflightRevocations` (new) | `100` |
| `consumer.heartbeatIntervalMs` (new, client-side) | `1000` |
| Remove `consumer.subscribeTimeoutMs` | — |

`heartbeatIntervalMs` is the interval the client sleeps between heartbeat calls. The server does not enforce this interval directly; enforcement is via `sessionTimeoutMs` (the consumer is evicted if no heartbeat arrives within that window).

#### D. SBE Schema (`event-bridge-core`)
- **Update** `HeartbeatRequest`: add `epoch` (`int64`) and `ownedPartitions` (repeated `int32`, each value is a partition ID).
- **Update** `HeartbeatResponse`: add `epoch` (`int64`), `revoke` (repeated `int32`), `assign` (repeated `int32`), `fullAssignment` (repeated `int32`).
- **Add** `AckRequest`: `epoch` (`int64`), `revoked` (repeated `int32`), `assigned` (repeated `int32`).
- **Add** `AckResponse`: `status` (enum/string).
- **Add** message type constants for Ack request/response in `MessageTypes`.
- Regenerate SBE codecs.

#### E. Gateway DTOs (`EventBridgeDtos`)
- **Add** `HeartbeatRequest { epoch: long, ownedPartitions: List<Integer> }` (no load, capacity, or metadata fields). `epoch` is the consumer's last known epoch; it is forwarded to the coordinator as `clientEpoch`.
- **Replace** `HeartbeatResponse` with `{ epoch: long, revoke: List<Integer>, assign: List<Integer>, fullAssignment: List<Integer> }`. *(Note: `status` is not included — it belongs only on `AckResponse`.)*
- **Add** `AckRequest { epoch: long, revoked: List<Integer>, assigned: List<Integer> }`.
- **Add** `AckResponse { status: String }`. Valid values: `"OK"`.
- **Remove** `generation` from `CommitRequest` (was `{groupId, consumerId, position, generation}`).
- **Remove** `StaleGenerationResponse`.
- **Remove** `SubscribeResponse` (the subscribe endpoint is deleted; no rename is required).
- Rename `generation` → `epoch` in `PollResponse`.

#### F. Gateway Controllers
- **Remove** `SubscribeController` entirely.
- **Update** `HeartbeatController`:
  - URL: `POST /v1/groups/{groupId}/consumers/{consumerId}/heartbeat`
  - Accepts `HeartbeatRequest` body; upserts consumer on first contact.
  - Returns `HeartbeatResponse` with delta + epoch.
  - **HTTP status codes:**
    - `200 OK` — heartbeat accepted and response produced.
    - `400 Bad Request` — malformed request body (e.g., missing required fields, invalid JSON).
    - `404 Not Found` — `groupId` does not exist.
    - `503 Service Unavailable` — coordinator actor unreachable or broker overloaded.
- **Add** `AckController`:
  - URL: `POST /v1/groups/{groupId}/consumers/{consumerId}/ack`
  - Accepts `AckRequest` body.
  - Returns `AckResponse { status: "OK" }`.
  - **HTTP status codes:**
    - `200 OK` — ACK received. This is returned in all cases where the request is well-formed and the group/consumer exist, including when the ACK epoch is stale (coordinator logs a warning and discards the stale ACK; no error is surfaced to the consumer).
    - `400 Bad Request` — malformed request body.
    - `404 Not Found` — `groupId` or `consumerId` does not exist.
    - `503 Service Unavailable` — coordinator actor unreachable.
- Update `CommitController` to remove `generation` stale-check logic.
  - **HTTP status codes (unchanged except noted):** `200 OK` on success; `400 Bad Request` for malformed body; `404 Not Found` for unknown group/consumer; `503 Service Unavailable` for broker errors.

#### G. `BrokerRequestRouter` / `SbeCodec`
- Add codec support for `AckRequest`/`AckResponse`.
- Update heartbeat codec to encode/decode new fields (`epoch` and `ownedPartitions` on request; `epoch`, `revoke`, `assign`, `fullAssignment` on response).

#### H. Client (`event-bridge-client/Consumer.java`)

**`fullAssignment` population rule (coordinator-side):** The coordinator populates `fullAssignment` in the heartbeat response if and only if `clientEpoch < coordinatorEpoch` (i.e., a rebalance has occurred since the consumer's last heartbeat). In all other cases `fullAssignment` is an empty list. Clients must not rely on `fullAssignment` being present outside of an epoch advance.

**Full reconciliation procedure:** When the client observes `res.epoch > currentEpoch`, it executes a full reconcile in this exact order:
1. Stop processing all currently held partitions.
2. Commit offsets for all currently held partitions (see commit-failure handling below).
3. Clear `ownedPartitions` locally.
4. Apply `fullAssignment` as the new owned set: call `startProcessing(p)` for each partition in `fullAssignment`.
5. Update `currentEpoch = res.epoch`.
6. Update `ownedPartitions = new Set(res.fullAssignment)`.
7. Send an ACK with `revoked` = all previously held partitions not in `fullAssignment`, `assigned` = all partitions in `fullAssignment` (including any previously held that remain in the new assignment — the coordinator handles these as idempotent confirmations).

**Commit-failure policy during full reconcile:** If one or more commits fail at step 2, the client does **not** proceed with steps 3–7. It logs the failure, retains the existing `ownedPartitions` (continuing to process them), and retries on the next heartbeat cycle. The client will receive another response with `res.epoch > currentEpoch` (since `currentEpoch` was not updated), triggering another full reconcile attempt. If the coordinator's `ack_timeout_ms` elapses before the client succeeds, the coordinator forces reassignment (AC #5); any duplicate processing thereafter is an accepted at-least-once consequence and must be handled by idempotent consumers.

**Consumer loop:**
```
while (true) {
    res = POST /v1/groups/{groupId}/consumers/{consumerId}/heartbeat
           body: { epoch: currentEpoch, ownedPartitions: ownedPartitions }

    if (res.epoch > currentEpoch) {
        // full reconcile (see procedure above)
        prevOwned = copy of ownedPartitions
        stopProcessing(all partitions in ownedPartitions)
        commitFailures = commitAll(ownedPartitions)
        if (commitFailures is not empty) {
            log("Commit failed for partitions: " + commitFailures + "; deferring full reconcile")
            sleep(heartbeat_interval_ms)
            continue
        }
        ownedPartitions.clear()
        for (p : res.fullAssignment) { startProcessing(p) }
        currentEpoch = res.epoch
        ownedPartitions.addAll(res.fullAssignment)
        POST /v1/groups/{groupId}/consumers/{consumerId}/ack
             body: { epoch: res.epoch,
                     revoked: prevOwned \ res.fullAssignment,
                     assigned: res.fullAssignment }
        // A non-2xx response from the ACK endpoint is logged and discarded;
        // local state (currentEpoch, ownedPartitions) is already updated.
        // The coordinator's ack_timeout_ms handles the case where no ACK is received.
    } else if (res.epoch < currentEpoch) {
        // stale response — ignore, do not update state
        sleep(heartbeat_interval_ms)
        continue
    } else {
        // delta apply
        commitFailures = []
        for (p : res.revoke) {
            stopProcessing(p)
            ok = commit(p)
            if (!ok) { commitFailures.add(p) }
        }
        if (commitFailures is not empty) {
            // do NOT send ACK for this cycle; retry commit on next iteration
            log("Commit failed for partitions: " + commitFailures + "; deferring ACK")
            sleep(heartbeat_interval_ms)
            continue
        }
        ownedPartitions.removeAll(res.revoke)
        for (p : res.assign) { startProcessing(p) }
        ownedPartitions.addAll(res.assign)

        POST /v1/groups/{groupId}/consumers/{consumerId}/ack
             body: { epoch: res.epoch,
                     revoked: res.revoke,
                     assigned: res.assign }
        // A non-2xx response from the ACK endpoint is logged and discarded;
        // local state is already updated. The coordinator's ack_timeout_ms handles
        // the case where no ACK is received.
    }

    sleep(heartbeat_interval_ms)
}
```

**Commit-failure policy (delta path):** The ACK for a revocation is withheld if any commit in that batch fails. The consumer retries the commit on the next heartbeat cycle. This prevents silent offset loss. If the coordinator's `ack_timeout_ms` elapses before the consumer succeeds in committing and ACKing, the coordinator proceeds with reassignment (AC #5); the consumer's duplicate processing after that point is an accepted at-least-once consequence and must be handled by idempotent consumers.

- Track `currentEpoch` and `ownedPartitions` locally.
- Remove `RebalanceInProgressException` (rebalances are now invisible to the consumer — they just get a delta or a full reconcile signal via epoch advance).

---

### Edge Cases
- Consumer ACK times out → coordinator forces `REVOKING(C) → UNASSIGNED` and/or `PENDING_ASSIGN(C) → UNASSIGNED` after `ack_timeout_ms`; this increments `epoch`, causing affected consumers to perform full reconcile on next heartbeat.
- Consumer dies mid-revocation → eviction releases partitions; `REVOKING` and `PENDING_ASSIGN` states transition to `UNASSIGNED`; epoch increments.
- Empty group → all partitions `UNASSIGNED`; first heartbeat triggers rebalance + full assignment.
- Partition count changes → coordinator detects `configuredPartitionCount != currentAssignment.size() + unassigned.size()`; reconciles tracked partition set; rebalance triggered; orphaned partitions redistributed; epoch increments.
- Duplicate heartbeat from same consumer (idempotent) → refreshes timestamp, returns current delta (no epoch increment).
- `max_inflight_revocations = 100` cap: if 100 revocations are already in-flight, additional reassignments are enqueued in `pendingReassignment` and processed in FIFO order as inflight slots free up (via ACKs or timeouts).
- Consumer commit fails before ACK (delta path) → consumer defers ACK and retries commit next cycle; coordinator may time out and proceed with reassignment (at-least-once delivery).
- Consumer commit fails during full reconcile → consumer retains existing owned set and re-attempts full reconcile on next heartbeat cycle; coordinator may time out and force reassignment.
- `clientEpoch > coordinatorEpoch` (unexpected) → coordinator logs a warning, treats as `clientEpoch == coordinatorEpoch`, returns delta response; no error surfaced to consumer.
- Stale-epoch ACK → coordinator logs a warning and silently discards (no state changes); returns `200 OK`; consumer self-corrects on next heartbeat via epoch advance and full reconcile.
- Partitions absent from ACK `revoked` list (but coordinator-requested) → remain `REVOKING` until `ack_timeout_ms` elapses.
- Previously held partitions included in full-reconcile ACK `assigned` list → coordinator treats as idempotent no-ops (`ASSIGNED(C)` already; no state change required).

---

### Out of Scope
- Offset commit endpoint (`CommitController`) — logic unchanged except `generation` field removed from request.
- `PollController` and publish pipeline — unchanged.
- RAFT/snapshot/truncation subsystems — unchanged.
- Persistence of consumer group state across broker restarts.
- Multi-broker coordinator failover.
- Admin endpoint for dynamic partition count changes (`PUT /v1/groups/{groupId}/config`) — data model must support it (`configuredPartitionCount` field in the consumer group record), but the endpoint itself is not implemented in this refactoring.

## Engineering Decisions

## Technical Decisions & Assumptions

### Architecture decisions (from your answers)

1. **Group lifecycle**: Groups are lazily created on first heartbeat (no 404 for first-ever contact with a group ID). Consumer auto-registration on first heartbeat also auto-creates the group if it doesn't exist. The `404` HTTP status that was previously documented for `HeartbeatController` is **removed from the OpenAPI spec and controller documentation** — it is unreachable under the lazy-create model and must not be left as a silent dead-end for future readers.

2. **Rebalance timing**: Rebalance is **deferred** to the coordinator loop, never inline in `heartbeat()`. A new consumer's first heartbeat registers it and marks "rebalance needed"; assignments arrive after the next loop cycle (`rebalance_interval_ms = 2000ms`). First heartbeat response has empty `revoke`/`assign`/`fullAssignment`.

3. **SBE `CommitOffsetRequest`**: `generation` field removed from the SBE schema (templateId=5) and the broker-side `BrokerSbeCodec`/`BrokerRequestDispatcher`. Fully consistent with the DTO and coordinator.

4. **`AckController`**: Calls `coordinatorActor.ack()` directly, following the existing controller pattern. SBE codec work in Section G is transport-layer plumbing for the distributed path.

---

### Key implementation assumptions

**`ConsumerGroupRegistry` restructuring:**
- `ConsumerGroup` gains: `configuredPartitionCount`, `epoch` (replaces `generation`, starts at 1), `partitionState: Map<Integer, PartitionState>`, `currentAssignment: Map<Integer, String>`, `pendingReassignment: Queue<ReassignmentTask>`, `consumersChanged: boolean`
- **`PartitionState` definition:** `PartitionState` is a plain value class (record or final class) with the following fields:
  - `String assignedConsumerId` — the consumer currently holding this partition; `null` if unassigned.
  - `ReassignmentState state` — the current state of this partition in any in-flight reassignment: one of `STABLE` (no active task), `REVOKING` (a task has been issued asking the current holder to release), `ASSIGNING` (the revoke ack has been received and the partition is being handed to a new consumer), or `COMPLETE` (the assign ack has been received; the partition is confirmed stable at its new owner and the task will be drained on the next loop cycle). `STABLE` is the initial state.
  - `PartitionState` is updated by the coordinator loop and by `ack()` as `ReassignmentTask` states advance. It mirrors `ReassignmentTask.state` for quick per-partition lookup without scanning the full queue.
- `targetAssignment` **is removed** from the `ConsumerGroup` field list. The intended target state is fully represented by the set of pending `ReassignmentTask`s in `pendingReassignment`; a redundant `targetAssignment` map would require keeping two structures in sync and serves no additional purpose. The coordinator derives the expected final assignment from `currentAssignment` plus any in-flight tasks.
- `ReassignmentTask` fields: `int partitionId`, `String fromConsumerId` (nullable — null means unassigned), `String toConsumerId`, `ReassignmentState state` (enum: `REVOKING`, `ASSIGNING`, `COMPLETE`)
- `configuredPartitionCount` is passed as a constructor argument when a `ConsumerGroup` is created (lazily on first heartbeat). The value is read from `ConsumerProperties.partitionCount` at group-creation time and never changes for the lifetime of the group object. It is **not** derived from the number of registered consumers.
- Per-consumer `ConsumerEntry` gains: `ownedPartitions`, `pendingRevoke`, `pendingAssign`, `ackDeadline`
- Rebalance trigger condition: a rebalance is needed when `partitionState.size() != configuredPartitionCount || consumersChanged`.
  - **`consumersChanged` semantics:**
    - Set to `true` when: (a) a new consumer registers (first heartbeat for that consumer ID), or (b) a consumer is evicted due to session timeout expiry or `ackDeadline` expiry.
    - Reset to `false` at **loop step (1)** (described below), immediately after draining all `COMPLETE` tasks — but only when the drain results in an empty `pendingReassignment` queue **and** `consumersChanged` was not set again during steps (2)–(3) of the same cycle. Concretely: after step (1) empties the queue, the coordinator proceeds through steps (2) and (3); if either step evicts a consumer (setting `consumersChanged = true`), the flag is left as `true` going into step (4). If the queue is empty after step (1) and no eviction occurs in steps (2)–(3), then at step (4) the coordinator recognizes the rebalance as complete and evaluates the trigger condition with `consumersChanged = false`. An empty queue after step (1) when `pendingReassignment` was already empty before the cycle (i.e., nothing was drained) does **not** reset `consumersChanged` — the reset only occurs as a consequence of successfully draining at least one `COMPLETE` task and finding the queue empty afterward.
    - A completed rebalance always clears `consumersChanged`, regardless of how it was set.
- **`COMPLETE` task draining and `consumersChanged` reset:** At **step (1) of each coordinator loop cycle**, before any new rebalance computation, the coordinator drains all `COMPLETE` tasks from `pendingReassignment`. For each drained task, the corresponding `PartitionState` entry is updated to `STABLE`. If after draining the queue is empty, the coordinator checks whether `consumersChanged` should be cleared (subject to the condition above — no evictions in the same cycle's steps (2)–(3)). This prevents unbounded queue growth and ensures stale tasks are never re-evaluated.
- `addOrRefresh()` removed; `heartbeat()` becomes the upsert entry point

**`CoordinatorActor` restructuring:**
- `subscribe()` + `SubscribeResult` removed
- `heartbeat(groupId, consumerId, clientEpoch, ownedPartitions)` → `HeartbeatResult { epoch, revoke, assign, fullAssignment }`
  - `clientEpoch`: the epoch value last seen by the consumer (from its most recent `HeartbeatResponse` or `AckResponse`). The coordinator uses it for staleness detection: if `clientEpoch < group.epoch`, the coordinator includes the full current assignment in `fullAssignment` so the consumer can reconcile. `clientEpoch == 0` means the consumer has never received an epoch (first heartbeat). The coordinator **never rejects** a heartbeat based on `clientEpoch` alone; it only uses the value to decide response content.
- New `ack(groupId, consumerId, epoch, revoked, assigned)` → `AckResult { status }`

  **Server-side `ack()` processing logic:**
  1. Look up the group by `groupId`. If the group does not exist or `consumerId` is not present in the group's consumer registry, return `AckStatus.CONSUMER_NOT_FOUND` immediately; no state is mutated.
  2. If `epoch != group.epoch`, return `AckStatus.EPOCH_MISMATCH` immediately; no state is mutated.
  3. Clear `ConsumerEntry.ackDeadline` for this consumer (set to null).
  4. **Normal task-ack path** (i.e., this ack corresponds to an in-flight `REVOKING` or `ASSIGNING` task — not a `fullAssignment` reconciliation, see step 5):
     - For each partition in `revoked`: find the matching `ReassignmentTask` in `pendingReassignment` where `state == REVOKING` and `fromConsumerId == consumerId`. Advance the task state to `ASSIGNING`. Update `PartitionState.state` to `ASSIGNING`. Remove the partition from `ConsumerEntry.ownedPartitions` for `fromConsumerId`. Update `ConsumerEntry.ackDeadline` for `toConsumerId` to `now + ackTimeoutMs` (the assign deadline for the receiving consumer).
     - For each partition in `assigned`: find the matching `ReassignmentTask` where `state == ASSIGNING` and `toConsumerId == consumerId`. Advance the task state to `COMPLETE`. Update `PartitionState` to `state = COMPLETE, assignedConsumerId = consumerId`. Update `currentAssignment` to map this partition to `consumerId`. Add the partition to `ConsumerEntry.ownedPartitions` for `consumerId`.
     - If no matching task is found for a reported partition (e.g., the task was already completed or the consumer was evicted and re-registered), the coordinator silently skips that partition — no error is returned.
  5. **`fullAssignment` reconciliation path** (consumer sends `revoked=[]`, `assigned=<fullAssignment contents>`): The coordinator treats the `assigned` list as confirmation that the consumer now holds exactly those partitions. For each partition in `assigned`: if there is a matching in-flight `ASSIGNING` task for this consumer, advance it to `COMPLETE` as in step 4. Update `currentAssignment` and `ConsumerEntry.ownedPartitions` accordingly. No tasks are expected for partitions that were already stable in `currentAssignment`; those are recorded silently without task transitions. `REVOKING` tasks are not affected by this path (the consumer is not being asked to revoke anything in a `fullAssignment` reconciliation).
  6. Return `AckStatus.OK`.

- Coordinator loop via `actor.runAtFixedRate(rebalanceIntervalMs)` replaces the current eviction-only timer. **Each loop cycle executes in order:**
  1. Drain all `COMPLETE` tasks from `pendingReassignment`; update corresponding `PartitionState` entries to `STABLE`. If the queue is empty after draining and no eviction occurs in steps (2)–(3), reset `consumersChanged = false` at step (4).
  2. Check `ackDeadline` expiry per consumer.
  3. Evict expired sessions (session-timeout expiry).
  4. Evaluate the rebalance trigger condition; reset `consumersChanged = false` if the rebalance is now complete (empty queue, no evictions in this cycle).
  5. Issue new revocations subject to `maxInflightRevocations` limit.
- `ConsumerNotRegisteredException`: **retained as a thrown exception from `commitOffset`** only. The server throws it; the client catches it (see Client section below for handling). It is **not** thrown from the heartbeat path.
- `AssignmentResult.generation` → `AssignmentResult.epoch`

**`maxInflightRevocations` enforcement:**
- Enforced **per-group** by the coordinator loop at step (5) above.
- Before issuing a new `REVOKING` task, the coordinator counts the number of `ReassignmentTask`s in `pendingReassignment` whose state is `REVOKING` or `ASSIGNING` (i.e., tasks waiting for consumer acks). If that count equals or exceeds `maxInflightRevocations`, the loop skips issuing additional revocations for that group in the current cycle and retries in the next.
- No error is returned to any consumer. The rebalance is stalled (delayed by one loop interval) until in-flight tasks drain below the limit. Tasks are never rejected or discarded.

**`ackTimeoutMs` enforcement:**
- Enforced **per-consumer** within the coordinator loop at step (2) above.
- When a `REVOKING` or `ASSIGNING` task is issued for a consumer, `ConsumerEntry.ackDeadline` is set to `now + ackTimeoutMs`.
- At step (2) of each loop cycle, the coordinator iterates over all `ConsumerEntry`s with a non-null `ackDeadline`. If `ackDeadline < now`, the consumer is **evicted** from the group (same behavior as session-timeout expiry): its `ConsumerEntry` is removed, `consumersChanged` is set to `true`, and any `REVOKING`/`ASSIGNING` tasks referencing it are removed from `pendingReassignment`. The eviction triggers a fresh rebalance in the same or next cycle.
- `ackDeadline` is cleared (set to null) when the consumer successfully calls `ack()` (see server-side `ack()` step 3 above).

**`pendingReassignment` queue ordering:**
- `pendingReassignment` is a **FIFO queue**. Tasks are appended in the order the coordinator generates them (i.e., the order the rebalance algorithm iterates over partitions to assign). The coordinator issues revocations in FIFO order subject to `maxInflightRevocations`. Ordering does not affect correctness — no semantic priority is attached to task order.

**Configuration (`ConsumerProperties` record):**
- `heartbeatTimeoutMs` → `sessionTimeoutMs` (default `10000`)
- `subscribeTimeoutMs` removed
- `partitionCount` — **existing field; confirm its presence before implementation.** If it does not currently exist in `ConsumerProperties`, it must be added as a required field (no default is appropriate — the group cannot be created without knowing its partition count). Document it as: "Number of partitions for this consumer group. Set at group creation time and immutable for the lifetime of the group." If a default is required for backward compatibility, use `1` and note the deviation in the implementation PR.
- Added: `rebalanceIntervalMs` (2000), `ackTimeoutMs` (5000), `maxInflightRevocations` (100), `heartbeatIntervalMs` (1000)
- Existing `CoordinatorProperties.sessionTimeoutMs` is a separate field and left unchanged

**SBE schema (`event-bridge-protocol.xml`):**
- Before assigning template IDs 18 and 19 to `AckRequest` and `AckResponse`, **verify that IDs 18 and 19 are unoccupied** in `event-bridge-protocol.xml`. If either ID is taken, use the next available IDs above the current maximum and update this plan accordingly.
- `HeartbeatRequest` (id=7): add `epoch int64`, `ownedPartitions` repeating group of `int32`
- `HeartbeatResponse` (id=8): replace `generation` with `epoch int64`; add `revoke` group `int32`, `assign` group `int32`, `fullAssignment` group `int32`; **retain `errorCode ErrorCode` and `errorMessage varData`** fields unchanged.
- `CommitOffsetRequest` (id=5): drop `generation` field
- New `AckRequest` (id=18, pending ID verification above): `epoch int64`, `revoked` group `int32`, `assigned` group `int32`
- New `AckResponse` (id=19, pending ID verification above): `errorCode ErrorCode`, `errorMessage varData`
- `SubscribeRequest` (id=9) / `SubscribeResponse` (id=10): retained in schema **indefinitely for backward compatibility** with any existing deployed clients that may still send subscribe messages. The `BrokerRequestDispatcher` handler for `SUBSCRIBE_REQUEST` is **removed**; when the dispatcher receives a message of this type it falls through to the **default unknown-message-type error path**, which returns an error response with error code `NOT_SUPPORTED` (or the existing generic unknown-type error, whichever is already defined in the dispatcher). No new special-case handler is added. A follow-up task is tracked to evaluate full schema removal once no active clients depend on these message types (see tracking item below).
- `MessageTypes`: add `ACK_REQUEST = "eb.ack.request"`, `ACK_RESPONSE = "eb.ack.response"`; remove `SUBSCRIBE_REQUEST` and `SUBSCRIBE_RESPONSE` constants (the schema entries are retained, but the Java constants are deleted since the dispatcher no longer references them). The `HEARTBEAT_REQUEST` and `HEARTBEAT_RESPONSE` constant **names and their string values are unchanged** — no modification to these constants is required.
- SBE codecs regenerated after schema changes

> **Follow-up tracking item — Subscribe removal:** Once it is confirmed that no active clients send `SubscribeRequest`, remove `SubscribeRequest`/`SubscribeResponse` from the SBE schema, delete the corresponding codec encode/decode paths, and remove any remaining references.

**Gateway DTOs (`EventBridgeDtos`):**
- Add `HeartbeatRequest { long epoch, List<Integer> ownedPartitions }`
- Replace `HeartbeatResponse` with `{ long epoch, List<Integer> revoke, List<Integer> assign, List<Integer> fullAssignment }`
- Add `AckRequest { long epoch, List<Integer> revoked, List<Integer> assigned }`
- Add `AckResponse { AckStatus status }` where `AckStatus` is an enum with values:
  - `OK` — ack accepted; task state advanced and consumer ownership recorded (see server-side `ack()` logic above)
  - `EPOCH_MISMATCH` — returned by the server when `epoch != group.epoch`; consumer should re-sync by sending a heartbeat
  - `CONSUMER_NOT_FOUND` — returned by the server when the consumer ID is not present in the group's registry; consumer should re-register via heartbeat
  - The client treats any non-`OK` status as a trigger to immediately send a heartbeat to re-synchronize state.
- Remove `CommitRequest.generation` field; remove `StaleGenerationResponse`
- `SubscribeResponse` DTO is **retained but deprecated** (annotated `@Deprecated`, no callers). It must not be deleted while the SBE codec retains the `SubscribeResponse` schema entry, since the codec references the DTO for serialization. Removal of the DTO is deferred to the same follow-up task as the schema removal.
- `PollResponse.generation` → `PollResponse.epoch`

**Controllers:**
- `SubscribeController` deleted
- `HeartbeatController` updated: accepts `HeartbeatRequest` body, URL stays at `/v1/consumers/{groupId}/{consumerId}/heartbeat` **permanently** — the `/v1/consumers/` prefix is the established convention for all existing consumer-facing endpoints and will not be migrated. The new `AckController` deviates from this convention because its spec was authored with `/v1/groups/`; to avoid introducing a third URL scheme, `AckController` will **also use `/v1/consumers/`**: `POST /v1/consumers/{groupId}/{consumerId}/ack`. The `/v1/groups/` prefix from the original spec is hereby overridden in favor of consistency with existing endpoints. **The API spec (`rest-api.yaml` or equivalent) must be updated to reflect the `/v1/consumers/{groupId}/{consumerId}/ack` URL; this is an explicit implementation task.** If the spec was drafted with `/v1/groups/`, that path entry must be renamed before the spec is published or used to generate client stubs.
- The `404` response previously documented in `HeartbeatController` is **removed from the OpenAPI spec** (see Architecture Decision 1).
- New `AckController`: `POST /v1/consumers/{groupId}/{consumerId}/ack`
- `CommitController`: removes `generation` field read, removes stale-generation check, removes `StaleGenerationResponse` usage

**`RebalanceInProgressException` removal scope:**
- This exception is removed from **all locations where it currently exists**: the client (`Consumer.java`), any server-side coordinator or service class that declares or throws it, any shared DTO or exception module that defines it, and any test code that references it. Before removing the class definition, search the full module graph (event-bridge client, event-bridge server, any shared libraries) to identify all declaration and usage sites. Remove them all in the same commit. If the exception is defined in a shared module consumed by other components outside this feature's scope, confirm with the team that those components do not rely on it before deleting the class.

**Client (`Consumer.java`):**
- Field: `generation` → `currentEpoch`; initial value is **`0`** (the coordinator treats `clientEpoch == 0` as "consumer has never received an epoch — first heartbeat"). Add `ownedPartitions: Set<Integer>` (initially empty).
- `sendHeartbeat()` is replaced by a **continuous consumer loop** (see Section D — Consumer Loop Specification for the full behavioral definition). The loop's response-handling logic is:
  1. Send a heartbeat carrying `currentEpoch` and `ownedPartitions`.
  2. On response: if `fullAssignment` is non-empty, it **supersedes** any `revoke`/`assign` lists in the same response — replace `ownedPartitions` wholesale with `fullAssignment` and send a single `AckRequest` containing the new `ownedPartitions` as `assigned` and an empty `revoked` list. Steps 3–4 are skipped.
  3. Otherwise (i.e., `fullAssignment` is empty): apply `revoke` by removing those partitions from `ownedPartitions`; apply `assign` by adding those partitions.
  4. If either `revoke` or `assign` is non-empty, send a single `AckRequest` carrying `revoked` and `assigned`. At most one `AckRequest` is sent per heartbeat response.
  5. Sleep `heartbeatIntervalMs` before the next iteration.
- `RebalanceInProgressException` removed from the client (see full removal scope above).
- `ConsumerNotRegisteredException`: **retained in the client** as a caught exception from `commitOffset()`. When `commitOffset()` receives this exception from the server, the client logs a warning, sends an immediate heartbeat to re-register, and **retries the commit exactly once**. If the single retry also fails (with any exception, including `ConsumerNotRegisteredException`), the exception is propagated to the caller. No further automatic retries are performed; the caller is responsible for any higher-level retry policy.
- `commitOffset()` removes `generation` from request body

**`BrokerRequestDispatcher` / `BrokerSbeCodec`:**
- Add handler for `ACK_REQUEST` → `coordinatorActor.ack()`
- Update heartbeat handler for new request/response fields
- Remove `SUBSCRIBE_REQUEST` handler; inbound messages of this type fall through to the existing default unknown-type error path
- Remove `generation` decode from commit handler

## Design Decisions

**Summary of Design Decisions (Systems Perspective)**

This is a pure backend refactoring. No UI/UX design work is required. The changes touch four distinct areas of the backend, each described below with the rationale for the approach taken.

---

### 1. Component Change Surface

The following backend components are modified. All fully-qualified names and file paths listed below are **[PENDING VERIFICATION]** and must be confirmed against the current source tree before implementation begins.

**Path Verification Owner:** `[assign to a named engineer]`
**Exit Criterion:** Verified paths are recorded in the implementation PR description or a linked tracking ticket before implementation begins. "Confirmed" means: the file exists at the stated path in the current `main` branch, the class name matches, and the Maven module matches.

| Component | Location | Nature of Change |
|---|---|---|
| `CoordinatorActor` | `[verify path, e.g. zeebe/broker/src/main/java/.../CoordinatorActor.java]` | State machine logic updated to drive heartbeat and acknowledgement lifecycle; handles session timeout events (see §5) |
| `ConsumerGroupRegistry` | `[verify path]` | Registration trigger changes from subscription event to session establishment. Deregistration trigger changes from unsubscribe event to heartbeat timeout (after the missed-heartbeat threshold defined in §5). Deregistration action: remove consumer from the active registry, trigger partition reassignment, re-enqueue in-flight unacknowledged records (see §5 for in-flight handling semantics) |
| `HeartbeatController` | `[verify path, e.g. zeebe/gateway-rest/src/main/java/.../HeartbeatController.java]` | New Spring REST controller; introduced to make liveness a distinct, explicit operation separate from subscription |
| `AckController` | `[verify path]` | New Spring REST controller; introduced to make acknowledgement a distinct, auditable operation with its own HTTP semantics (see rationale in §3) |
| `SubscribeController` | `[verify path]` | Deleted — contingent on completing the external caller audit documented in §3; do not delete before audit is complete |
| SBE schema(s) | `[verify path, e.g. zeebe/protocol/src/main/resources/sbe/...]` | Scope not yet determined — see §2 |
| Consumer client loop | `[verify path and class name]` | Polling and reconnection logic updated; see §4 |

---

### 2. SBE Schema Changes

**⚠ This section is a blocking prerequisite for implementation.** Binary protocol changes carry forward- and backward-compatibility risk and must be treated with care. Before implementation begins, an engineer must audit the affected components and produce either:

- **(a)** A completed version of the field-change table below listing every affected schema and field, **or**
- **(b)** The explicit statement: *"No SBE schema changes are required by this refactoring."*

Leaving this section in placeholder form is not acceptable at handoff — it is misleading rather than merely incomplete.

**In-scope schema modifications** *(complete or resolve before handoff — see above)*:

| Schema Name | Field Name | Change Type | Old Type / Value | New Type / Value | Compatibility Impact |
|---|---|---|---|---|---|
| `[SchemaName]` | `[fieldName]` | added / removed / type-changed | `[old]` | `[new]` | See posture below |

**Compatibility posture:**

- **Supported upgrade window:** **[BLOCKING — state the actual supported upgrade window for this project before any field-removal work begins. Example: "This project supports N−1→N rolling upgrades; therefore a two-release dual-encode cycle is the minimum." The window determines whether two releases are sufficient or a longer cycle is required. Until confirmed, assume the minimum two-release cycle applies. The stated window must be reviewed and signed off alongside any field-removal proposal — see blocking item 3 in the Status section.]**

- **Field additions:** A field added to an existing message must use a tag/ID that does not collide with any existing field in the schema. Older readers that do not recognise the new tag must treat it as optional and skip it (forward compatibility). The schema evolution rules of the SBE version in use apply.

- **Field removals:** Removing a field breaks backward compatibility for any reader that still references it. If any existing consumer (exporter, client library, downstream processor) reads a field being removed, a **dual-encode cycle** is required before removal.

  > **Definition — Dual-encode cycle:** A compatibility bridge spanning at minimum the number of releases dictated by the supported upgrade window confirmed above. In release N, both the old and new field encodings are written simultaneously: the old encoding for backward compatibility with existing readers, the new encoding for forward compatibility with updated readers. In release N+1 (or later, per the confirmed upgrade window), the old encoding is dropped. The cycle is considered complete when no consumer within the supported upgrade window still reads the old field. Completion of the cycle must be tracked in a dedicated ticket with an explicit release milestone, and the ticket must be linked from this document before any field-removal work is merged.

- This section must be completed and reviewed before any SBE changes are merged. If no schema changes are ultimately required, that must be stated explicitly here.

---

### 3. Rationale for Key Design Decisions

**Why delete `SubscribeController` rather than deprecate it?**

The subscription model it encodes is being replaced by a heartbeat-based session model — the two models are semantically incompatible, not just interface-incompatible. Keeping a deprecated endpoint would require maintaining dual state-machine paths in `CoordinatorActor` and `ConsumerGroupRegistry` indefinitely.

Deletion is the preferred approach, **contingent on the following audit being completed and documented here before the deletion PR is opened:**

> **External Caller Audit — Status: PENDING**
>
> The claim that there are no external callers outside the controlled client loop must be verified before `SubscribeController` is deleted. The required verification steps are:
> 1. Run a `grep`/`rg` search across all modules in this repository for any reference to `SubscribeController` endpoints, by URL path and by class name.
> 2. Search all published client library versions (Java client, Spring Boot starter, and any other released artifact) for calls to the subscribe endpoint. Because published artifacts are compiled JARs, `grep`/`rg` cannot search them directly. The required concrete method is: decompile each published artifact using `javap` or a bytecode analysis tool such as `jdeps`; additionally, inspect the corresponding source-release tags in this repository; and review any published JavaDoc for the subscribe endpoint. All three approaches must be executed and their results recorded individually — a search is not considered complete if any of the three is omitted.
> 3. Check `CHANGELOG.md` and any public API documentation for prior advertisement of the subscribe endpoint as a public or stable contract.
>
> **Intermediate state during the audit period:** Until the audit is complete and the deletion PR is opened, `SubscribeController` must remain in a stable, unmodified state. It must not be deleted, and any implementation work on other components that would remove its existing in-repository callers must not be merged before the audit completes (doing so would contaminate the audit by eliminating evidence of callers). Annotating `SubscribeController` with `@Deprecated` during this period is permitted to signal intent, but must not be accompanied by any behavioral change or caller removal.
>
> **Verification owner:** `[assign to a named engineer]`
> **Exit criterion:** Audit results (method, scope searched, outcome) are recorded here — either inline or via a linked ticket — before the deletion PR is opened. If external callers are found, a deprecation period must be defined in a separate design decision record and this section updated before any deletion proceeds.

**Why introduce `AckController` as a separate controller rather than adding to an existing one?**

Acknowledgement has distinct HTTP semantics (idempotent, resource-scoped), a distinct authorization surface (future: per-partition ACL), and a distinct audit trail requirement. Collapsing it into an existing controller (e.g., an extended `HeartbeatController`) would conflate liveness signalling with progress signalling. Separation also makes each controller independently testable and independently evolvable.

**Why introduce `HeartbeatController` as a separate controller?**

Heartbeat is a liveness probe: it carries no business payload and must remain cheap and stable even as the acknowledgement protocol evolves. Keeping it separate protects its latency profile and allows rate-limiting and health-check routing to be applied without affecting the ack path.

---

### 4. Consumer Loop Changes

The consumer client loop is the programmatic component that polls for work and drives the heartbeat/ack cycle. It is **not** a human-facing interface.

The loop is implemented in `[verify path and class name]`. It is **[BLOCKING — specify: single-threaded, multi-threaded, or reactive. The threading model must be stated here before handoff. The reconnection/retry policy specified below and the in-flight record handling semantics in §5 must be consistent with this choice — see blocking item 7 in the Status section.]**

The following aspects of the loop are modified:

- **Session establishment:** **[BLOCKING — specify the mechanism that replaces `SubscribeController`. Options include: (a) an explicit `POST /sessions` call to a new `SessionController`; (b) implicit session creation on first heartbeat received by `CoordinatorActor`; (c) a session token issued via an existing authentication flow. The chosen mechanism must be documented here, its HTTP contract must appear in the API Specification (§7) — or, if no new endpoint is introduced, §7 must explicitly state that — and it must be consistent with the server-side session lifecycle in §5.]**

- **Heartbeat cadence:** The loop sends a periodic `POST /heartbeat` to `HeartbeatController` on a configurable interval. The path `/heartbeat` is fixed by the HTTP contract in §7; refer to §7 as the authoritative definition. **[BLOCKING — specify: the interval value and units (e.g., 10 seconds); the number of consecutive missed heartbeats after which the client treats the session as lost and initiates reconnection. These values must be consistent with the server-side timeout threshold in §5.]**

- **Acknowledgement:** After processing each record, the loop calls `AckController`. **[BLOCKING — specify the ack request payload fields. At minimum, specify: session ID, partition ID, and record offset or sequence number. The full request body schema must appear in the API Specification (§7).]**

- **Reconnection / retry:** **[BLOCKING — specify all of the following: (a) trigger condition (e.g., N consecutive missed heartbeats, explicit session-expired response from server, network error); (b) backoff algorithm (linear, exponential with jitter, or fixed interval); (c) backoff parameters (initial delay, multiplier if exponential, maximum delay); (d) maximum retry attempts or indefinite retry; (e) behaviour on exhausting retries (fatal exit, operator alarm, fallback to degraded mode). The reconnection trigger must be consistent with the server-side session eviction behaviour defined in §5. The backoff and concurrency behaviour of the retry loop must also be reviewed for consistency with the threading model stated above.]**

---

### 5. Server-Side Session Lifecycle

This section defines what the server does when a consumer session expires. It is the server-side counterpart of the client reconnection logic in §4 and is equally required before an engineer can implement `CoordinatorActor` or `ConsumerGroupRegistry`.

**Heartbeat timeout detection (`CoordinatorActor`):**

**[BLOCKING — specify: (a) the timeout threshold — how many seconds (or how many missed heartbeat intervals) before `CoordinatorActor` declares a session expired; this value must be consistent with the client-side missed-heartbeat count × interval specified in §4; (b) whether the timeout is enforced per-session or per-partition; (c) the internal event or signal that `CoordinatorActor` emits to `ConsumerGroupRegistry` on expiry.]**

**Session expiry action sequence (`CoordinatorActor` and `ConsumerGroupRegistry`):**

Upon session expiry, the following sequence is executed. **[BLOCKING — confirm or revise this sequence before implementation:]**

1. `CoordinatorActor` marks the session as expired and ceases accepting heartbeats for that session ID.
2. `ConsumerGroupRegistry` removes the consumer from the active registry.
3. Partitions previously assigned to the expired consumer are **[BLOCKING — specify: immediately unassigned and available for reassignment to another consumer? Held for a grace period before reassignment? Assigned to a specific failover consumer?]**

**In-flight record handling on session expiry:**

If a consumer has polled one or more records and its session expires before it calls `AckController`, those records must not be silently dropped or permanently lost. **[BLOCKING — specify the exact behavior: (a) are unacknowledged records re-enqueued for delivery to another consumer? (b) is there a per-record delivery-attempt counter to prevent unbounded redelivery? (c) what is the maximum redelivery count, and what happens when it is exceeded (e.g., route to dead-letter queue, emit error log entry, halt partition processing)? This behavior directly determines `ConsumerGroupRegistry` semantics and the delivery guarantee (at-least-once or exactly-once) of the system, and must be defined here before any implementation of session expiry begins.]**

---

### 6. Out-of-Scope Items

The following are explicitly excluded from this change. Any work touching these areas requires a separate design decision record.

| Item | Reason for Exclusion |
|---|---|
| Poll logic / fetch mechanics | Existing poll behaviour is unchanged; only session and ack signalling changes |
| Commit / offset persistence | Persistence strategy is a separate concern deferred to a later milestone |
| Admin endpoints | Operator-facing APIs are out of scope for this consumer-facing change |

For the complete out-of-scope list as defined by product requirements, see the project specification. **[BLOCKING — insert the correct document name and section number before handoff. If the specification does not yet contain a scope and exclusions section, one must be created and referenced here before this document is considered ready for handoff.]**

---

### 7. API Specification Reference

Endpoint definitions, HTTP verbs, status codes, and request/response body shapes for **all new HTTP endpoints introduced by this refactoring** must be fully specified in a dedicated API Specification section of this document before implementation begins. This includes `HeartbeatController`, `AckController`, and any session establishment endpoint introduced as a result of the decision in §4. If the session establishment mechanism chosen in §4 does not introduce a new endpoint (e.g., session creation is implicit on first heartbeat), that must be stated explicitly both here and in §4; it is not sufficient to simply omit session establishment from the API Specification without explanation.

**Current status — one of the following must be true at handoff; delete the inapplicable option:**

- ☐ **Exists:** The API Specification is at **§[N]** of this document. That section is the authoritative reference; the Design Decisions section does not duplicate it.
- ☐ **Does not yet exist:** The API Specification section has not been written. This is a blocking item — engineering handoff cannot proceed until it is complete. See §Status, item 13.

This section must not remain in its current undecided state at handoff.

---

### Status and Next Steps

This section is **not yet complete and is not ready for engineering handoff.** Every row in the table below is a blocking item. Each has a named owner and an explicit exit criterion. **Implementation must not begin until all items are resolved.** Item 1 is not an exception to this rule: component paths must be confirmed before implementation begins, not merely before merging.

| # | Blocking Item | Owner | Exit Criterion |
|---|---|---|---|
| 1 | Confirm fully-qualified class names and file paths for all components in §1 | `[assign]` | Verified paths recorded in the implementation PR description or a linked ticket **before implementation begins**; "confirmed" means file exists at the stated path on `main`, class name matches, Maven module matches |
| 2 | Complete §2: either list all SBE schema field changes with compatibility impact, or state explicitly that no SBE changes are required | `[assign]` | §2 contains no placeholder rows and no ambiguous statements; reviewed and signed off by a protocol/schema owner before any schema changes are merged |
| 3 | State the supported upgrade window in §2 compatibility posture | `[assign]` | A concrete upgrade window (e.g., "N−1→N rolling upgrades; two-release dual-encode cycle is the minimum") is stated in §2; the BLOCKING placeholder in the compatibility posture section is removed; statement reviewed and signed off alongside any field-removal proposal |
| 4 | Specify §4 session establishment mechanism (replacement for `SubscribeController`) | `[assign]` | A specific mechanism is named in §4; either its HTTP contract appears in §7, or §7 and §4 both explicitly state that no new endpoint is introduced; mechanism is consistent with §5 server-side session lifecycle |
| 5 | Specify §4 heartbeat interval, missed-heartbeat count, timeout value, and units | `[assign]` | Specific numeric values with units appear in §4 (client-side interval and the number of consecutive missed heartbeats before reconnection) and in §5 (server-side timeout threshold); all values are consistent between the two sections |
| 6 | Specify §4 ack request payload fields | `[assign]` | Ack fields are listed in §4 and fully specified in the API Specification referenced by §7 |
| 7 | Specify §4 consumer loop threading model | `[assign]` | The threading model (single-threaded, multi-threaded, or reactive) is named in §4; the reconnection/retry policy in blocking item 8 is consistent with that model |
| 8 | Specify §4 reconnection/retry policy (trigger condition, backoff algorithm, backoff parameters, max attempts, exhaustion behaviour) | `[assign]` | All sub-items in the §4 reconnection bullet are answered with specific values; behaviour is consistent with §5 server-side session eviction and with the threading model confirmed in blocking item 7 |
| 9 | Complete §5 server-side heartbeat timeout threshold, timeout detection scope, and session expiry action sequence | `[assign]` | §5 contains no BLOCKING placeholder blocks; values reviewed alongside §4 for consistency |
| 10 | Complete §5 in-flight record handling on session expiry (re-enqueue behaviour, redelivery counter, max redelivery, exhaustion action) | `[assign]` | Delivery guarantee (at-least-once or exactly-once) is explicitly stated in §5; redelivery behaviour and maximum redelivery count are specified with concrete values |
| 11 | Complete external caller audit for `SubscribeController` deletion and document results in §3 | `[assign]` | Audit method, scope, and outcome are recorded in §3 before the deletion PR is opened; compiled artifacts searched via `javap`/`jdeps` in addition to source-release tag review and JavaDoc inspection (all three approaches executed and results recorded individually); if callers are found, a separate deprecation decision record exists |
| 12 | Confirm the complete out-of-scope list and insert the specification document name and section number in §6 | `[assign]` | §6 references an existing, named section of the project specification with a real section number; no placeholder references remain |
| 13 | Confirm that the API Specification section exists (or create it) and update §7 with the section number | `[assign]` | §7 contains a real section number with no undecided checkboxes; the referenced section covers all endpoints introduced by this refactoring, including any session establishment endpoint, or explicitly states that no session establishment endpoint exists |

Once all blocking items are resolved, the specification is ready for engineering handoff.

## Technical Analysis

---

## Technical Feasibility Assessment: Consumer Group Refactoring (Event Bridge)

---

### Complexity: **HIGH**

This is a deep, vertical slice through every layer of the stack — schema, codec, registry, actor, controllers, and client. The central change replaces a flat `partitionAssignment` map with a 4-state partition state machine plus delta-based heartbeat signaling, a dedicated ACK flow, an inflight-cap queue, and a multi-step coordinator loop. Each layer change is individually tractable, but the interdependencies are tight and the behavioral changes are non-trivial to test correctly.

---

### Affected Files / Modules

#### `event-bridge-core` (shared types + SBE)

| File | Change |
|---|---|
| `src/main/resources/sbe/event-bridge-protocol.xml` | Update `HeartbeatRequest` (add `epoch int64`, `ownedPartitions group<int32>`); update `HeartbeatResponse` (add `epoch`, `revoke`, `assign`, `fullAssignment` groups); add `AckRequest` (id=18) and `AckResponse` (id=19); remove `STALE_GENERATION` / `CONSUMER_NOT_REGISTERED` error codes as active paths (keep for backwards compat or remove); schema version bump to 3 |
| `src/main/java/.../transport/MessageTypes.java` | Remove `SUBSCRIBE_REQUEST/RESPONSE`; rename heartbeat response constant; add `ACK_REQUEST`/`ACK_RESPONSE` constants |
| `target/generated-sources/sbe/**` | Regenerated — `HeartbeatRequestEncoder/Decoder`, `HeartbeatResponseEncoder/Decoder`, new `AckRequestEncoder/Decoder`, `AckResponseEncoder/Decoder`; `SubscribeRequest/ResponseEncoder/Decoder` deleted or kept as dead code |

#### `event-bridge-broker` (coordinator logic)

| File | Change |
|---|---|
| `src/main/java/.../coordinator/ConsumerGroupRegistry.java` | **Full rewrite.** Current: flat `partitionAssignment` map + `generation` counter + `subscribe()/heartbeat()` API. Required: `epoch` (starts at 1), `configuredPartitionCount`, per-consumer `{ownedPartitions, pendingRevoke, pendingAssign, ackDeadline}`, global `{currentAssignment, targetAssignment, partitionState<PartitionState enum>}`, `pendingReassignment` queue, BALANCED_STICKY rebalance, inflight-cap logic. The `ConsumerGroup` inner class grows from ~120 lines to ~400+ lines. |
| `src/main/java/.../actor/CoordinatorActor.java` | **Major refactor.** Remove `subscribe()` + `SubscribeResult`; change `heartbeat()` signature (add `clientEpoch, ownedPartitions`) returning `HeartbeatResult{epoch, revoke, assign, fullAssignment}`; add `ack(groupId, consumerId, epoch, revoked, assigned)` returning `AckResult{status}`; rewrite `onActorStarted()` to add `rebalanceIntervalMs` loop; rewrite `evictDeadConsumers()` to handle `PENDING_ASSIGN`/`REVOKING` transitions; add ACK-timeout processing; drain `pendingReassignment`; remove `ConsumerNotRegisteredException` for heartbeat path; rename `generation` → `epoch` throughout result records |
| `src/test/java/.../actor/CoordinatorActorSubscribeHeartbeatTest.java` | **Full rewrite.** All 15+ tests use the old subscribe/heartbeat/generation API. New tests must cover auto-registration, delta computation, full-reconcile path, ack processing, inflight cap, timeout behavior, epoch semantics. |
| `src/test/java/.../coordinator/ConsumerGroupRegistryTest.java` | **Full rewrite.** All 20+ tests use `subscribe()` directly. New tests must exercise the state machine transitions, `pendingRevoke/pendingAssign`, `ackDeadline`, the rebalance trigger conditions, and `configuredPartitionCount` change detection. |

#### `event-bridge-gateway` (HTTP layer + codec + routing)

| File | Change |
|---|---|
| `src/main/java/.../dto/EventBridgeDtos.java` | Add `HeartbeatRequest{epoch, ownedPartitions}`; replace `HeartbeatResponse` with `{epoch, revoke, assign, fullAssignment}`; add `AckRequest{epoch, revoked, assigned}` and `AckResponse{status}`; remove `SubscribeResponse`, `StaleGenerationResponse`; remove `generation` from `CommitRequest`; rename `generation` → `epoch` in `PollResponse` |
| `src/main/java/.../controller/HeartbeatController.java` | Add `@RequestBody HeartbeatRequest`; change URL to `/v1/groups/{groupId}/consumers/{consumerId}/heartbeat`; update response mapping to new `HeartbeatResult` shape; handle 404 (group not found) in addition to 503 |
| `src/main/java/.../controller/SubscribeController.java` | **Delete** |
| `src/main/java/.../controller/AckController.java` | **New file.** `POST /v1/groups/{groupId}/consumers/{consumerId}/ack`; accepts `AckRequest`; returns `AckResponse{status:"OK"}`; handles 404/400/503 |
| `src/main/java/.../controller/CommitController.java` | Remove generation stale-check block (lines 100–103); remove `StaleGenerationResponse` import; update `CommitRequest` usage (no `generation` field) |
| `src/main/java/.../transport/SbeCodec.java` | Update `encodeHeartbeat()` to accept `epoch + ownedPartitions`; update `decodeHeartbeat()` to return `{epoch, revoke, assign, fullAssignment}`; remove `encodeSubscribe/decodeSubscribe`; add `encodeAck(groupId, consumerId, epoch, revoked, assigned)` and `decodeAck(bytes)` |
| `src/main/java/.../transport/BrokerRequestRouter.java` | Remove `subscribe()` method; update `heartbeat()` signature; add `ack()` method with `sendToCoordinator` routing; remove `SubscribeResult` import |
| `src/test/java/.../transport/BrokerRequestRouterTest.java` | Update heartbeat test; add ack routing test; remove subscribe test |
| `src/test/java/.../controller/GlobalExceptionHandlerTest.java` | Likely minor updates if SubscribeController was tested here |
| `src/test/java/.../StandaloneEventBridgeIT.java` | **Major rewrite.** Currently tests subscribe → poll → heartbeat lifecycle. Must be rewritten for heartbeat-auto-register → ack → poll lifecycle. |

#### `event-bridge-client`

| File | Change |
|---|---|
| `src/main/java/.../client/Consumer.java` | **Full rewrite.** Replace entire heartbeat/poll loop with the new `currentEpoch`/`ownedPartitions`-based consumer loop. Remove `generation`-in-poll-URL usage. Add `sendAck()` method. Rename `generation` → `currentEpoch`. |
| `src/main/java/.../client/RebalanceInProgressException.java` | **Delete** |
| `src/main/java/.../client/ConsumerNotRegisteredException.java` | **Delete** (auto-registration removes this error path) |
| `src/main/java/.../client/EventBridgeClient.java` | Remove `subscribe()` API (or keep as thin wrapper that calls heartbeat); update internal subscribe call site |
| `src/test/java/.../client/ConsumerTest.java` | **Full rewrite.** All tests stub the old subscribe/poll/heartbeat/generation flow. |
| `src/test/java/.../client/EventBridgeClientTest.java` | Update to remove subscribe test stubs |

#### `event-bridge-core` (configuration)

| File | Change |
|---|---|
| `src/main/java/.../config/EventBridgeProperties.java` | Replace `ConsumerProperties(heartbeatTimeoutMs, subscribeTimeoutMs)` with `ConsumerProperties(sessionTimeoutMs, rebalanceIntervalMs, ackTimeoutMs, maxInflightRevocations, heartbeatIntervalMs)`; add defaults per spec; update compact constructor defaults |

---

### Approach

**Phase 1 – Schema & Config (foundation)**
Update the SBE XML schema; regenerate codecs. Update `EventBridgeProperties.ConsumerProperties`. Update `MessageTypes`.

**Phase 2 – Registry (state machine core)**
Rewrite `ConsumerGroupRegistry` to introduce `PartitionState` enum, the five coordinator maps, per-consumer pending sets, `ackDeadline`, `configuredPartitionCount`, BALANCED_STICKY rebalance, and inflight-cap / `pendingReassignment` queue. This is the highest-complexity piece — write its unit tests (`ConsumerGroupRegistryTest`) in parallel.

**Phase 3 – CoordinatorActor (orchestration)**
Wire the new registry API into actor methods: new `heartbeat()` (delta vs. full-reconcile path), new `ack()` (state transitions), coordinator loop (eviction → ACK-timeout → drain queue → partition-count check → BALANCED_STICKY rebalance). Remove `subscribe()` / `SubscribeResult`. Update `CoordinatorActorSubscribeHeartbeatTest`.

**Phase 4 – Transport layer**
Update `SbeCodec` (encode/decode for new heartbeat fields + ack messages). Update `BrokerRequestRouter` (new heartbeat signature, add ack, remove subscribe). Update `BrokerSbeCodec` in broker transport (handler registration for `ACK_REQUEST`).

**Phase 5 – Gateway DTOs + Controllers**
Update `EventBridgeDtos`, `HeartbeatController`, `CommitController`; delete `SubscribeController`; add `AckController`.

**Phase 6 – Client**
Full rewrite of `Consumer.java` loop per spec. Delete `RebalanceInProgressException`, `ConsumerNotRegisteredException`. Update `ConsumerTest`, `StandaloneEventBridgeIT`.

---

### Risks & Blockers

1. **SBE schema backward compatibility.** Adding `epoch` as a fixed field and `ownedPartitions`/`revoke`/`assign`/`fullAssignment` as repeating groups to existing messages (7, 8) changes their wire format. All decoders expecting the old layout will break. There is no schema migration path for in-flight messages; this requires a coordinated cut-over (version bump to schema `version="3"`).

2. **`BrokerSbeCodec` / broker-side message dispatch.** `BrokerRequestDispatcher.java` (not yet read in detail) will need handler registrations for `ACK_REQUEST` / `ACK_RESPONSE` message types. This is not complex but is easy to miss, and missing it will cause silent routing failures. Confirm whether `BrokerRequestDispatcher` auto-registers from `MessageTypes` constants or requires explicit handler wiring.

3. **`PollResponse` still carries `generation`.** Per the requirements, `PollResponse.generation` is renamed to `epoch`. The SBE `PollRequest` also has a `generation` field (line 84 in schema). The requirements list the poll pipeline as out of scope but `PollResponse.generation → epoch` is listed as a DTO change (Req E). This creates a partial-scope conflict — the SBE poll messages and `PollController`/`PollActor` also reference `generation` and would need matching updates, or the field can be renamed in the HTTP DTO only while the SBE field retains its name.

4. **Coordinator loop timing with actor thread model.** The coordinator loop at `rebalanceIntervalMs` must run entirely on the actor thread (like the existing truncation timer). The five-step loop body — especially ACK-timeout processing and BALANCED_STICKY rebalance with inflight-cap — is substantially more work per tick than the current simple eviction call. Under high consumer churn this could cause actor-thread stalls. Needs a microbenchmark or at least a note to cap loop work per iteration.

5. **`StandaloneEventBridgeIT` relies on `subscribe()`** as its entry point into the consumer lifecycle. Removing `subscribe()` from `EventBridgeClient` will break it completely until the full client rewrite lands. Consider keeping a stub `subscribe()` that immediately delegates to a heartbeat during the transition, to avoid breaking the integration test midway.

6. **`ConsumerNotRegisteredException` is also thrown from `commitOffset()`** in `CoordinatorActor` (line 146). The requirements do not change `commitOffset()` logic, but they do remove the exception from the heartbeat path. This distinction must be preserved; deleting the exception class entirely would also break the commit path.

7. **Inflight-cap and `pendingReassignment` correctness.** The interaction between the cap drain (Phase 3 coordinator loop step 3) and concurrent ACK processing is subtle: a task may be dequeued when the partition is no longer `UNASSIGNED` (was snatched by another path). The discard condition (`if partition not UNASSIGNED, skip`) must be airtight; otherwise double-assignment is possible.

8. **Client-side commit-failure loop.** The new `Consumer.java` loop has two separate commit-failure retry paths (delta and full-reconcile). Both require that the `ackDeadline` on the coordinator eventually forces reassignment if commits never succeed. This is safe by spec (at-least-once), but the client must not silently swallow commit errors — the test for this path is non-trivial (requires mock commit that fails then succeeds).

---

### Estimated Scope

| Category | Estimate |
|---|---|
| **Files to modify** | 16 |
| **Files to create** | 2 (`AckController.java`, new `PartitionState.java` enum if split out) |
| **Files to delete** | 3 (`SubscribeController.java`, `RebalanceInProgressException.java`, `ConsumerNotRegisteredException.java`) |
| **Generated files regenerated** | ~10 SBE codec files |
| **Test classes to fully rewrite** | 4 (`ConsumerGroupRegistryTest`, `CoordinatorActorSubscribeHeartbeatTest`, `ConsumerTest`, `StandaloneEventBridgeIT`) |
| **Test classes to update** | 3 (`BrokerRequestRouterTest`, `GlobalExceptionHandlerTest`, `EventBridgeClientTest`) |
| **New test classes** | 1–2 (dedicated `AckController` test, potentially `CoordinatorActorAckTest`) |
| **Net new lines of production code** | ~800–1200 (state machine + coordinator loop + ack path + client loop) |
| **Lines removed** | ~300 (subscribe flow, generation stale-check, old heartbeat paths) |
