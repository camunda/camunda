# Track when distributed commands are received and completed on every partition

**DRI**: Stephan Epping

**Status**: Proposed (8.11)

**Purpose**: Defines two observable milestones for every distributed command: received by all
partitions, and all follow-up work completed on all partitions. Developers can attach a command to
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

**D1. Every distribution has two milestones per partition, RECEIVED and COMPLETED, and both are
observable.**

- RECEIVED: the partition processed the command (today's ACK).
- COMPLETED: the partition also finished all follow-up work the command started.
- A command without follow-up work completes when it is received. This is the common case:
  deployment, clock, identity, cluster variables, global listeners, signals.
- There is no opt-in. Every distribution is tracked and exported, so a client or user can see when
  every partition has received it, and when all of its work is done.
- Ordered queues advance on RECEIVED, as today. Follow-up work can take hours or years (a drain),
  and must never block the queue.

**D2. Developers attach command hooks to either milestone.**

- `withKey(k).inQueue(q).onAllReceived(cmd).onAllCompleted(cmd).distribute(command)`. Both hooks are
  optional.
- The source writes the `onAllReceived` command once every partition has RECEIVED. It writes the
  `onAllCompleted` command once every partition has COMPLETED.
- Both are written with a locally minted key, so they are not mistaken for distributed commands
  (d4e45a7d575d).
- A hook re-reads current state when it runs, because a lot may have changed since the distribution
  started.
- `onAllReceived` replaces `afterQueue(...).continueWith(...)` and the `CONTINUATION_*` intents.
  These are deprecated.

**D3. Follow-up work is an opaque handle the domain opens and closes.**

- While processing the command, a processor that starts follow-up work calls
  `handle = distribution.openFollowUp(command)`. Without that call, the partition is COMPLETED when
  it ACKs.
- The domain keeps the handle in its own state, next to its own substate: for example on the
  `DRAINING` definition, or on the suspension marker.
- When the domain decides the work is done, it calls `distribution.closeFollowUp(handle)`. It may
  pass an outcome and a reason (`COMPLETED`, or `CANCELLED` when a later command made the work
  obsolete, for example a resume during a suspend).
- The framework does not know about entities, counts or substates. How many things are involved,
  and when "done" is reached, is up to the domain.
- Calling `openFollowUp` twice for the same command returns the same handle.
- The source partition works the same way for its own share. Distribution skips the current
  partition, so its report is written locally instead of being sent.

**D4. Receivers report reliably; the ACK says whether follow-up work is still open.**

- The ACK carries `followUpOpen`. The framework computes it from its own state for that distribution
  key, not from the domain's processing result. A redelivered command therefore gets the correct
  answer, even when the domain rejects the redelivery as a duplicate.
- `followUpOpen = false`: the partition is RECEIVED and COMPLETED at once.
- When the domain closes the handle, the partition sends a `COMPLETE` report to the source. The
  report goes out as a normal unordered distribution to the source partition, so it reuses the
  existing retries and ACK.
- The source handles reports idempotently. A `COMPLETE` that arrives before the ACK counts as both.
- A rejected command is not a failure. It is RECEIVED with the rejection recorded (D5), and
  COMPLETED unless a handle is open.

**D5. Every partition's outcome is exported.**

- The ACK and `COMPLETE` records carry everything needed to trace a partition:
  - the rejection type and reason, if the command was rejected there;
  - whether follow-up work was opened;
  - the outcome and reason from `closeFollowUp`;
  - timestamps.
- Together with today's `STARTED`, `ENQUEUED`, `DISTRIBUTING`, `ACKNOWLEDGED` and `FINISHED`,
  secondary storage can show, per partition, whether a command is queued, sent, received, rejected,
  in follow-up, completed or cancelled, and when.
- The source ends every distribution with one summary event, `COMPLETED`. It carries the overall
  outcome (`CANCELLED` if any partition cancelled) and the per-partition outcomes. If any partition
  cancelled, `onAllCompleted` is not written.

**D6. Protocol and compatibility.**

- New intents on `CommandDistribution`: `COMPLETE` (command) and `PARTITION_COMPLETED` and
  `COMPLETED` (events).
- New properties on `CommandDistributionRecord`: `followUpOpen`, the rejection fields, the outcome
  and reason, and the hook commands.
- A partition-local column family for open handles on receivers, and one for partitions still
  pending completion on the source.
- Exporter templates and golden files change. This needs approval from the protocol owners.
- In a mixed-version cluster, an old receiver ACKs without `followUpOpen`, so its share counts as
  COMPLETED at once. That is today's behavior.
- A feature that relies on `onAllCompleted` (for example deletion moving off its hand-rolled path)
  must keep its old path until every broker is upgraded.

## Alternatives considered

- **Opt-in tracking per distribution (`trackCompletion`).** Rejected. It hides the common case from
  users, and the cost of completion for a command without follow-up work is one extra summary
  event.
- **Track follow-up work per entity, inside the framework.** Rejected. The domain often does not
  know up front how many things are involved, and the distribution does not need to know.
- **The domain reports "done" on the ACK.** Rejected. The ACK is not retried. If the domain decides,
  a redelivery that the domain rejects as a duplicate would report the wrong state. D4 avoids this
  because the framework computes the flag from its own state.
- **Block the queue until COMPLETED.** Rejected. This would bring back the head-of-line blocking of
  deployments by deletion.
- **Keep hand-rolling per feature.** Rejected. Suspension would be the third copy.

## Consequences

- Deletion, batch operations and suspension keep their substates (`DRAINING`, chunk states,
  `SUSPENDING`). They drop their own report commands, pending-partition column families and
  last-one-wins checks.
- Every distribution writes one extra event (`COMPLETED`). A distribution with follow-up work
  writes about three extra records per partition: the report, its distribution, and
  `PARTITION_COMPLETED`.
- Source state for a distribution with open follow-up work lives until every partition reports.
  Drains can last years, so a gauge for open distributions and their age is needed.
- Issue #23156 can build a user-facing status from the exported milestones. How that status is
  presented is decided separately.

## Open questions

- First adopter: suspension (#65686, new code) or deletion (proven need, needs the compatibility
  period).
- Retention of finished distributions for status queries: in the engine with a TTL, or only in
  secondary storage.
- Whether any feature needs a FAILED outcome in addition to CANCELLED.

## Source

- [Wait until request has been processed on all partitions (camunda/camunda#23156)](https://github.com/camunda/camunda/issues/23156)
- Process definition suspension distribution (camunda/camunda#65686)
- Receiver errors leave distributions pending (camunda/camunda#54132)
- Draining deletion: camunda/camunda#55930, camunda/camunda#56975

