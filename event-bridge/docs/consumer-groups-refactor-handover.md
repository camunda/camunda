# Consumer-groups refactor — handover

A handover of the consumer-groups module refactor, written so the same conventions
can be applied to the **metadata** module. Organized by component (what was cleaned
up) plus the conventions worth carrying over.

## The model everything was aligned to

StreamProcessor pipeline, mirroring `zeebe/engine`:

```
command → processor (validate + resolve/stamp + emit event) → applier (mutate state)
reads   → query service on its own ZeebeDb context (no shared flyweights, no mirror)
```

`CoordinatorStream` extends the shared `ReplicatedStream` template base (inheritance
kept deliberately — `MetadataStream` does the same).

## What was cleaned up, by component

### Appliers (`state/appliers/`)
`MemberJoined / MemberLeft / OffsetCommitted / GroupRebalanced / MemberReconciled / GroupDeleted`

- Now **pure writers**: they write the event's value verbatim — no decisions, no
  guard-and-return. Registered by intent in `Appliers.java`.
- Everything they used to decide (monotonic offset guard, epoch bumps, which lifecycle
  index a group belongs in, `emptySince`) moved up into the processors.

### Processors (`processing/`)
`JoinGroup / LeaveGroup / OffsetCommit / Rebalance / ReconcileMember / DeleteGroup`

- Own **all** validation (via validators) plus value resolution/stamping: resolve the
  topic partition count from the registry, stamp the monotonic offset, stamp the
  debounce deadline (`rebalanceDueAt`) and retention base (`emptySince`). The leader
  that emits the durable event makes the decision.

### Validators (`processing/`)
- `CoordinationValidator` — client commands, returns `Either<Rejection, T>` with a
  `CoordinationErrorCode`.
- `TransitionValidator` — internal state-machine commands (reconcile/rebalance/delete),
  returns `Either<String, Void>`.
- `Rejection` record. Validation lives here, not in processors or appliers.

### State (`state/`)
- **Removed the in-memory group mirror** — groups are read from replicated state.
- Engine-style split: immutable (`ConsumerGroupState`, `OffsetState`) / mutable
  (`Mutable*`) / concrete (`DbConsumerGroupState`, `DbOffsetState`).
- `Db*State` is **dumb storage**: granular get/put/delete + `track/untrack` index
  primitives, no decisions.
- Replicated the **debounce + retention deadlines** (`rebalanceDueAt`, `emptySince`)
  so they survive failover. Due-ordered indexes (`CONSUMER_GROUPS_REBALANCE_DUE`,
  `CONSUMER_GROUPS_EMPTY`) give scheduled tasks a work-list without full scans.
- Reads **pin values**: `readSnapshot` / `groupIds` copy out of the shared flyweights
  (`key.toString()`, `Map.copyOf`, fresh `TopicPartition`s) before returning, so
  downstream never holds a mutating flyweight.
- Query services (`ConsumerGroupQueryService`, `OffsetQueryService`) are thin
  delegating wrappers, each built lazily on its **own** ZeebeDb context per reader actor.

### Scheduled tasks (`processing/`)
- `RebalanceAssignorTask`, `GroupRetentionTask` — **stateless**, clock-driven, read
  their work-list from the replicated indexes (`rebalancesDueBy(now)`, `emptyGroups()`).
- `SessionEvictionTask` runs as a stream task off the liveness mirror.

### Records (`record/`)
- event-bridge has **first-class value types** now
  (`ValueType.EVENT_BRIDGE_MEMBERSHIP / OFFSET / REBALANCE`) instead of borrowing the
  engine's.

### Request actors (`membership/`) — three symmetric per-leader actors
- `ConsumerGroupCoordinator` (writes) — writes the **wire-supplied record straight to
  the log**, no request→record mapping; stamps the server-assigned member id on join only.
- `HeartbeatHandler` (liveness) — own actor, own query services, reconciliation
  handshake + liveness mirror.
- `ConsumerGroupQueryHandler` (reads) — offset-fetch + describe-groups, own query services.
- The log writer (`Sequencer`) is thread-safe, so the separate actors are for path
  symmetry, not write safety.

### Transport (`transport/`)
- `CoordinationRequestHandler` owns decode + dispatch + **response framing** (mirrors
  the engine's `AsyncApiRequestHandler`); business code returns raw payloads.
- `CoordinationResponseEncoder` collapsed to `encode / encodeValue / serialize / encodeRejection`.

### Gateway
- Kept **one** deliberate mapping (`RequestMapper` → DTO → `Broker*Request` builds the
  record), pending a later gateway/service split.

## Most recent session's concrete changes
- **feat**: OffsetFetch optional `(topic, partition)` filter — point reads, `-1` for
  uncommitted, empty filter = whole group; exposed as repeated `?partition=topic:id`.
- **docs**: removed all Kafka/KIP references from comments across the event-bridge tree.
- **refactor**: simplified `HeartbeatHandler.heartbeat()` (`maybeRecordConvergence`,
  `reconciliationFor`); pruned reconciliations from a single pinned `groupIds()` read
  instead of a snapshot-per-group scan.
- 50 tests green in consumer-groups.

## Conventions to carry into the metadata module
1. Appliers pure writers · processors validate + resolve · validators own validation ·
   `Db*State` dumb storage.
2. No in-memory mirror for unbounded state; reader actors hold query services on private
   contexts.
3. **Pin** every state read (copy out of flyweights before returning).
4. Replicate deadlines/debounce into state; drive stateless tasks from clock + due-ordered
   indexes.
5. First-class value types; write the wire record straight through.
6. Transport layer owns decode + frame; business code returns raw results.
7. Style: no Kafka/KIP in comments, no inline imports (always import), simple comments —
   not everywhere.
