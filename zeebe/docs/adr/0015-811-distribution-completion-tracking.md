# Track when distributed commands are acknowledged and completed on every partition

**DRI**: Stephan Epping

**Status**: Proposed (8.11)

**Purpose**: Defines two observable milestones for every distributed command: acknowledged by all
partitions, and all remaining work completed on all partitions. Developers can attach a command to
either milestone, and the outcome of every partition is exported. This replaces per-feature
hand-rolled "fully done" aggregation.

**Audience**: Zeebe engineers working on command distribution, or on any feature whose distributed
command starts work that finishes later (deletion, suspension, batch operations); and AI agents
reasoning about cross-partition lifecycles.

## Context

Feature request [camunda/camunda#23156](https://github.com/camunda/camunda/issues/23156) asks to
know when a request has been processed on all partitions.

Today command distribution knows one milestone per partition: the receiver processed the command
and sent `CommandDistribution:ACKNOWLEDGE`. When all partitions have acknowledged, the source writes
`FINISHED`. Nothing can be attached to `FINISHED` per command. The only hook,
`afterQueue(...).continueWith(...)`, fires when a whole queue drains and has had no caller since
46e375d0595d.

Some commands start work on a partition that finishes much later:

- **Process definition deletion.** Every partition marks the definition `DRAINING` and deletes it
  locally when its last instance ends. Partition 1 writes `FULLY_DELETED` when all partitions are
  done. This is hand-rolled: a `DELETE_COMPLETE` command sent to partition 1, the
  `PENDING_PROCESS_DELETIONS_PER_PARTITION` column family, and a last-one-wins check.
- **Batch operations.** The same shape, built again: `COMPLETE_PARTITION`, `finishedPartitions` and
  a stored `followUpCommand`.
- **Process definition suspension (#65686).** Next in line: "all instances suspended" finishes later
  on each partition, and nothing aggregates it.

Each copy re-solves the same problems: reporter identity, idempotent aggregation, retries and key
routing (d4e45a7d575d).

## Decisions

**D1. Every distribution has two milestones per partition, ACKNOWLEDGED and COMPLETED, and both
are observable.**

- ACKNOWLEDGED: the partition processed the command. This keeps today's name and meaning.
- COMPLETED: the partition has also finished all work the command started there.
- A command that starts no further work is COMPLETED as soon as it is ACKNOWLEDGED. This is the
  common case: deployment, clock, identity, cluster variables, global listeners, signals.
- There is no opt-in. Every distribution is tracked and exported, so a client or user can see when
  every partition has acknowledged it, and when all of its work is done.
- Ordered queues advance on ACKNOWLEDGED, as today. Remaining work can take hours or years (a
  drain), and must never block the queue.

**D2. Developers attach command hooks to either milestone.**

- `withKey(k).inQueue(q).onAllAcknowledged(cmd).onAllCompleted(cmd).distribute(command)`. Both
  hooks are optional.
- The source writes the `onAllAcknowledged` command once every partition has ACKNOWLEDGED. It
  writes the `onAllCompleted` command once every partition has COMPLETED.
- Both are written with a locally minted key, so they are not mistaken for distributed commands
  (d4e45a7d575d).
- A hook re-reads current state when it runs, because a lot may have changed since the distribution
  started.
- The hook commands are stored once, in the source's state for the distribution. They are not
  copied onto the per-partition records (`DISTRIBUTING`, `ACKNOWLEDGE`, ...), so tracking never
  changes how large a distributed command can be.
- New code uses `onAllAcknowledged` instead of `afterQueue(...).continueWith(...)`. The
  `CONTINUATION_*` intents, their processor and their appliers stay: older brokers and replay of
  an existing log can still produce them (D7).

**D3. A partition defers its completion under a key the domain chooses, and acknowledges it later.**

- While processing the command, a processor that starts further work calls
  `distribution.deferCompletion(command, completionKey)`. The value type is taken from the command.
  Without that call, the partition is COMPLETED when it acknowledges.
- When the domain decides the work is done, it calls
  `distribution.ackCompletion(valueType, completionKey)`. It may pass an outcome and a reason:
  `COMPLETED`, or `CANCELLED` when a later command made the work obsolete (for example a resume
  during a suspend).
- The framework stores the deferral in its own column family, `[valueType | completionKey] →
  distributionKey`. The domain persists nothing and never handles distribution keys.
- The completion key is opaque and chosen by the domain: the process definition key for deletion
  and suspension, the batch operation key for batch operations. The value type namespaces it, so
  features cannot collide.
- At most one deferral exists per (value type, completion key) on a partition:
  - deferring again for the same distribution is a no-op;
  - deferring for a newer distribution acknowledges the older deferral as `CANCELLED`, with the newer
    distribution as the reason.
- The framework does not know about entities, counts or substates. What is involved, and when it is
  done, is up to the domain.
- The source partition defers its own share with the same call, using the distribution key it
  mints before distributing. Within one processing step the order of `deferCompletion` and
  `distribute` does not matter. Processing order does not change: the source processes the command
  locally first and distributes in the same step, without waiting for its own remaining work.
  Distribution skips the current partition, so its `COMPLETE` command is written locally instead
  of being sent.
- The source can ask whether work is still open, and push it again:
  - `isCompletionOpen(valueType, completionKey)` answers "is this still in progress somewhere",
    for example to reject a history deletion while another partition still drains;
  - `redistribute(valueType, completionKey)` re-sends the open distribution to the partitions that
    have not completed. Starting a new distribution instead would cancel the open one.

**D4. Receivers tell the source reliably when their work is done; the ACK says whether completion
is deferred.**

- The ACK carries `completionDeferred`. The framework computes it from its own deferral state for that
  distribution key, not from the domain's processing result. A redelivered command therefore gets
  the correct answer, even when the domain rejects the redelivery as a duplicate.
- `completionDeferred = false`: the partition is ACKNOWLEDGED and COMPLETED at once.
- When the domain acknowledges completion (`ackCompletion`), the partition sends a
  `CommandDistribution:COMPLETE` command to the source: "my deferred work for distribution K is
  done, with this outcome". It replaces per-feature commands such as
  `ResourceDeletion:DELETE_COMPLETE`.
- `COMPLETE` reuses the transport of distribution (send, retry, ACK), but it is not itself a
  tracked distribution: it has no milestones, no hooks and no summary event of its own. Tracking it
  like any other distribution would cost about ten records per `COMPLETE`.
- The source handles `COMPLETE` idempotently. A `COMPLETE` that arrives before the ACK counts as both.
- A rejected command is not a failure. It is ACKNOWLEDGED with the rejection recorded (D5), and
  COMPLETED unless completion is deferred.

**D5. Every partition's outcome is recorded and exported.**

- The ACK and `COMPLETE` records carry everything needed to trace a partition:
  - the rejection type and reason, if the command was rejected there;
  - whether completion was deferred, and the completion key;
  - the outcome and reason from `ackCompletion`;
  - timestamps.
- The source ends every distribution with one summary event, `COMPLETED`. It names the value type
  and intent of the distributed command, and carries the overall outcome (`CANCELLED` if any partition cancelled) and the per-partition outcomes. If any partition
  cancelled, `onAllCompleted` is not written.
- Where the per-partition state is exported is open. There are two options:
  - **Source only.** The source copies the fields of every ACK and `COMPLETE` into its own events
    (`ACKNOWLEDGED`, `PARTITION_COMPLETED`, `COMPLETED`). One distribution is one ordered stream
    under one key, and a status read model needs no join across partitions. The drawback is that a
    partition's state is known only once its `COMPLETE` arrives.
  - **Source and receivers.** In addition, each receiver exports its local events (deferring and
    acknowledging completion, with the distribution key and completion key). This shows more detail,
    such as why a partition still defers its completion, but a read model must correlate records
    from several partitions.

**D6. Delivery is unreliable; every step survives loss, duplication, reordering and failover.**

- All state changes, on the source and on receivers (deferring, acknowledging and cancelling
  completion, pending `COMPLETE`s), are events with appliers. Nothing is lost on replay or leader
  change.
- `COMPLETE` is retried from a persistent outbox on the receiver, `[distributionKey] →
  (source partition, outcome)`, by the redistribution scheduler, until the source acknowledges it
  with `COMPLETE_ACKNOWLEDGE`. That acknowledgement is routed by an explicit partition field, not
  by decoding the key.
- The source always acknowledges `COMPLETE`: also a duplicate, one for an unknown or finished
  distribution, and one from a partition outside the distribution.
- The outbox entry doubles as a tombstone. While a deferral or an unacknowledged `COMPLETE` exists
  for a distribution key, a redelivered command is not processed again, `deferCompletion` is a
  no-op, and the ACK says `completionDeferred = true`. A lost ACK followed by an early
  `ackCompletion(CANCELLED)` therefore cannot be reported as COMPLETED.
- A `COMPLETE` that arrives before the ACK does everything the ACK does (advance the queue,
  finish the distribution). The late ACK is then harmless.
- Distributions that can cancel each other's deferral use an ordered queue of their own, one per
  feature (for example `SUSPENSION` for suspend and resume, `RESOURCE_DELETION` for deletion), and
  come from one owning partition. The queue delivers B only after A is acknowledged, so a receiver
  sees them in order, and a slow drain of one feature never blocks another feature's queue.
- A queue cannot stop a retry of A that is already in flight from arriving after B. Each receiver
  therefore keeps, per (source partition, queue), the highest distribution key it has processed.
  A command with a lower or equal key is acknowledged without being processed again. "Newer" thus
  means a larger key from the same source, and an older key never cancels a newer deferral.
- Distributions that cannot cancel each other stay unordered; the tombstone (above) handles their
  redelivery.
- A distribution whose target partitions are only the source still creates its completion state,
  so hooks fire also in a single-partition cluster.
- An unexpected processing error on a distributed command is acknowledged generically, with the
  rejection recorded, so the distribution cannot stay open forever (#54132).
- `redistribute` re-sends only to partitions that acknowledged but have not completed, and never
  re-enqueues.
- Scaling up: the participants of a distribution are fixed when it starts.
  - A distribution that started before the scale-up does not wait for the new partition. The new
    partition has no instances to drain anyway: it copies global state from partition 1 (for
    example a `DRAINING` definition, which cannot start new instances) and finishes that state
    locally.
  - A distribution that starts during the scale-up includes the new partition. It is delivered
    once the partition is bootstrapped, as today.
  - Deferral, completion and highest-processed-key column families are partition-local, so the
    new partition starts without them. `ackCompletion` without a deferral is a no-op, and a
    `COMPLETE` from a partition outside the participants is acknowledged and ignored.
  - A feature whose state must hold on every partition (for example a suspension marker) must keep
    it in state that scaling copies, or a partition added after the distribution would miss it.
- Operators cannot change this mechanism: there is no API to cancel, complete or re-send a
  distribution. Work that never ends (for example an instance stuck in an incident) keeps its
  distribution open until the domain resolves it; the open-deferral gauge makes it visible. A
  read API for distributions may follow later.

**D7. Protocol and compatibility.**

- New intents on `CommandDistribution`: `COMPLETE` and `COMPLETE_ACKNOWLEDGE` (commands);
  `PARTITION_COMPLETED` and `COMPLETED` on the source, and the receiver's events for deferring,
  acknowledging and cancelling completion (events).
- New properties on `CommandDistributionRecord`: `completionDeferred`, `completionKey`, the
  rejection fields, and the outcome and reason. All are small and empty by default.
- Partition-local column families: deferrals on each partition (D3); on the source, the
  completion state of each distribution (value type, intent, hook commands, partitions still
  pending) and an index from (value type, completion key) to the open distribution.
- Every new or changed event gets a new applier version. Existing appliers stay unchanged, so
  replay of an existing log behaves as before.
- No intent is removed. Intents that new code stops writing (`CONTINUATION_*`, and per feature its
  old report commands such as `ResourceDeletion:DELETE_COMPLETE`) keep their processors and
  appliers, so records from older brokers and from replay are handled as before.
- In a mixed-version cluster, an old receiver ACKs without `completionDeferred`, so its share
  counts as COMPLETED at once. That is today's behavior.
- Existing column families are not migrated or reused. A feature's old column family (such as
  `PENDING_PROCESS_DELETIONS_PER_PARTITION`) keeps serving its old path and empties as that work
  finishes; new work does not write it.
- A feature moving onto `onAllCompleted` runs both paths: work started on the old path finishes
  on it, and new work uses the new path only once every broker is upgraded.
- "Every broker is upgraded" needs an explicit cluster-wide signal (a dynamic-config feature flag
  or a minimum broker version); the engine has none today. Until it is set, no broker writes the
  new intents and no feature switches paths, so no `COMPLETE` reaches a broker that cannot
  process it, and a new source never finishes while an old receiver still works on the old path.
- Exporter templates and golden files change. This needs approval from the protocol owners.

## Alternatives considered

- **Opt-in tracking per distribution (`trackCompletion`).** Rejected. It hides the common case from
  users, and the cost of completion for a command that starts no further work is one extra summary
  event.
- **Track work per entity, with counts, inside the framework.** Rejected. The domain often does not
  know up front how many things are involved, and the distribution does not need to know. One
  opaque completion key per deferral is enough.
- **The domain keeps the deferral in its own state.** Rejected. Every adopter would need its own state,
  appliers and migration for it, and open deferrals would be scattered across features instead of
  visible in one place.
- **The domain reports "done" on the ACK.** Rejected. The ACK is not retried. If the domain decides,
  a redelivery that the domain rejects as a duplicate would report the wrong state. D4 avoids this
  because the framework computes the flag from its own state.
- **Block the queue until COMPLETED.** Rejected. This would bring back the head-of-line blocking of
  deployments by deletion.
- **Keep hand-rolling per feature.** Rejected. Suspension would be the third copy.

## Consequences

- Deletion, batch operations and suspension keep their substates (`DRAINING`, chunk states,
  `SUSPENDING`). New work no longer writes their own completion commands, pending-partition column
  families and last-one-wins checks; they adopt with two calls. The old code stays only to finish
  work started before the upgrade and to replay old logs (D7).
- Batch operations report structured errors per partition and decide between COMPLETED and FAILED
  from them. Whether they fit needs a FAILED outcome with a payload; this is explored in a
  separate spike.
- Every distribution writes one extra event (`COMPLETED`). A distribution with deferred completion
  writes about three extra records per partition: the `COMPLETE` command, its ACK, and
  `PARTITION_COMPLETED`.
- Source state for a distribution with deferred completion lives until every partition has sent
  `COMPLETE`. Drains can last years, so a gauge for open deferrals and their age is needed.
- Issue #23156 can build a user-facing status from the exported milestones. How that status is
  presented is decided separately.

## Open questions

- First adopter: suspension (#65686, new code) or deletion (proven need, needs the compatibility
  period).
- Retention of finished distributions for status queries: in the engine with a TTL, or only in
  secondary storage.
- Whether any feature needs a FAILED outcome in addition to CANCELLED.
- Naming: `deferCompletion`/`ackCompletion`, or `completeDistribution` instead of `ackCompletion`.
- Export of per-partition state: by the source only, or by the source and the receivers (D5).

## Source

- [Wait until request has been processed on all partitions (camunda/camunda#23156)](https://github.com/camunda/camunda/issues/23156)
- Process definition suspension distribution (camunda/camunda#65686)
- Receiver errors leave distributions pending (camunda/camunda#54132)
- Draining deletion: camunda/camunda#55930, camunda/camunda#56975

