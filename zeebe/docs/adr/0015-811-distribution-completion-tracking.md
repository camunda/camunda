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
- `onAllAcknowledged` replaces `afterQueue(...).continueWith(...)` and the `CONTINUATION_*`
  intents. These are deprecated.

**D3. A partition holds back its completion under a key the domain chooses, and releases it later.**

- While processing the command, a processor that starts further work calls
  `distribution.holdCompletion(command, completionKey)`. Without that call, the partition is
  COMPLETED when it acknowledges.
- When the domain decides the work is done, it calls
  `distribution.releaseCompletion(completionKey)`. It may pass an outcome and a reason: `COMPLETED`,
  or `CANCELLED` when a later command made the work obsolete (for example a resume during a
  suspend).
- The framework stores the hold in its own column family, `[valueType | completionKey] →
  distributionKey`. The domain persists nothing and never handles distribution keys.
- The completion key is opaque and chosen by the domain: the process definition key for deletion
  and suspension, the batch operation key for batch operations. The value type namespaces it, so
  features cannot collide.
- At most one hold exists per (value type, completion key) on a partition:
  - holding again for the same distribution is a no-op;
  - holding for a newer distribution releases the older hold as `CANCELLED`, with the newer
    distribution as the reason.
- The framework does not know about entities, counts or substates. What is involved, and when it is
  done, is up to the domain.
- The source partition works the same way for its own share. Distribution skips the current
  partition, so its report is written locally instead of being sent.

**D4. Receivers report reliably; the ACK says whether completion is held.**

- The ACK carries `completionHeld`. The framework computes it from its own hold state for that
  distribution key, not from the domain's processing result. A redelivered command therefore gets
  the correct answer, even when the domain rejects the redelivery as a duplicate.
- `completionHeld = false`: the partition is ACKNOWLEDGED and COMPLETED at once.
- When the hold is released, the partition sends a `COMPLETE` report to the source. The report goes
  out as a normal unordered distribution to the source partition, so it reuses the existing retries
  and ACK.
- The source handles reports idempotently. A `COMPLETE` that arrives before the ACK counts as both.
- A rejected command is not a failure. It is ACKNOWLEDGED with the rejection recorded (D5), and
  COMPLETED unless completion is held.

**D5. Every partition's outcome is exported.**

- The ACK and `COMPLETE` records carry everything needed to trace a partition:
  - the rejection type and reason, if the command was rejected there;
  - whether completion was held, and the completion key;
  - the outcome and reason from `releaseCompletion`;
  - timestamps.
- Together with today's `STARTED`, `ENQUEUED`, `DISTRIBUTING`, `ACKNOWLEDGED` and `FINISHED`,
  secondary storage can show, per partition, whether a command is queued, sent, acknowledged,
  rejected, held, completed or cancelled, and when.
- The source ends every distribution with one summary event, `COMPLETED`. It carries the overall
  outcome (`CANCELLED` if any partition cancelled) and the per-partition outcomes. If any partition
  cancelled, `onAllCompleted` is not written.

**D6. Protocol and compatibility.**

- New intents on `CommandDistribution`: `COMPLETE` (command) and `PARTITION_COMPLETED` and
  `COMPLETED` (events).
- New properties on `CommandDistributionRecord`: `completionHeld`, `completionKey`, the rejection
  fields, the outcome and reason, and the hook commands.
- Partition-local column families: holds on each partition (D3), and partitions still pending
  completion on the source.
- Exporter templates and golden files change. This needs approval from the protocol owners.
- In a mixed-version cluster, an old receiver ACKs without `completionHeld`, so its share counts as
  COMPLETED at once. That is today's behavior.
- A feature that relies on `onAllCompleted` (for example deletion moving off its hand-rolled path)
  must keep its old path until every broker is upgraded.

## Alternatives considered

- **Opt-in tracking per distribution (`trackCompletion`).** Rejected. It hides the common case from
  users, and the cost of completion for a command that starts no further work is one extra summary
  event.
- **Track work per entity, with counts, inside the framework.** Rejected. The domain often does not
  know up front how many things are involved, and the distribution does not need to know. One
  opaque completion key per hold is enough.
- **The domain keeps the hold in its own state.** Rejected. Every adopter would need its own state,
  appliers and migration for it, and open holds would be scattered across features instead of
  visible in one place.
- **The domain reports "done" on the ACK.** Rejected. The ACK is not retried. If the domain decides,
  a redelivery that the domain rejects as a duplicate would report the wrong state. D4 avoids this
  because the framework computes the flag from its own state.
- **Block the queue until COMPLETED.** Rejected. This would bring back the head-of-line blocking of
  deployments by deletion.
- **Keep hand-rolling per feature.** Rejected. Suspension would be the third copy.

## Consequences

- Deletion, batch operations and suspension keep their substates (`DRAINING`, chunk states,
  `SUSPENDING`). They drop their own report commands, pending-partition column families and
  last-one-wins checks, and adopt with two calls.
- Every distribution writes one extra event (`COMPLETED`). A distribution with held completion
  writes about three extra records per partition: the report, its distribution, and
  `PARTITION_COMPLETED`.
- Source state for a distribution with held completion lives until every partition reports. Drains
  can last years, so a gauge for open holds and their age is needed.
- Issue #23156 can build a user-facing status from the exported milestones. How that status is
  presented is decided separately.

## Open questions

- First adopter: suspension (#65686, new code) or deletion (proven need, needs the compatibility
  period).
- Retention of finished distributions for status queries: in the engine with a TTL, or only in
  secondary storage.
- Whether any feature needs a FAILED outcome in addition to CANCELLED.
- Naming: `holdCompletion`/`releaseCompletion` versus alternatives such as
  `deferCompletion`/`complete`.

## Source

- [Wait until request has been processed on all partitions (camunda/camunda#23156)](https://github.com/camunda/camunda/issues/23156)
- Process definition suspension distribution (camunda/camunda#65686)
- Receiver errors leave distributions pending (camunda/camunda#54132)
- Draining deletion: camunda/camunda#55930, camunda/camunda#56975

