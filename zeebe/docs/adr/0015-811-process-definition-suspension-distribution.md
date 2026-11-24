# Process definition suspension decided once on the deployment partition, distributed in the deployment queue

**DRI**: Kristin Komschow

**Status**: Accepted (8.11)

**Purpose**: Defines how suspending and resuming a process definition reaches every partition, and
which partition decides what changes.

**Audience**: Zeebe engine engineers and AI agents working on process definition suspension, command
distribution, or the instance cascade of suspend and resume.

## Context

Process instances can be created on any partition, so every partition must know which process
definition versions are suspended. Suspend and resume requests resolve to one or more versions
(one by key or by BPMN process ID and version, all by BPMN process ID only) and are authorized
against `SUSPEND_PROCESS_DEFINITION`. The deployments of those versions, the suspension requests,
and authorization changes all reach the partitions through command distribution, which orders
commands only within one queue from one sending partition.

The outcome: the deployment partition alone resolves, authorizes and applies a request, then
distributes it in the `DEPLOYMENT` queue. The other partitions change exactly the versions it
changed, in the order it processed them.

## Decision

**D1. Suspend and resume requests always go to the deployment partition.**
The gateway sends both requests to partition 1, like deployments, instead of round-robin. A single
deciding partition gives every change one source and one order.

**D2. The deployment partition distributes the command in the `DEPLOYMENT` queue.**
Distributions in one queue are applied in order on every partition. Deployments and deletions of
process definitions are processed on partition 1 too (a deletion is routed by its definition key),
so they share that queue. While one leader of the deployment partition sends them, a suspension
therefore never overtakes the deployment or deletion of its version, and a suspend and a later
resume of the same version are applied in that order everywhere.

**D3. Versions are resolved, authorized and checked once, on the deployment partition.**
The distributed command carries the resolved versions with their outcomes. The other partitions
change exactly the versions the deployment partition changed, and do not resolve, authorize or
check their state again: by D2, their versions are in the state the deployment partition saw.
Authorization changes travel on the `IDENTITY` queue with no ordering against `DEPLOYMENT`, so
deciding again on each partition could apply a request on only some of them.

**D4. A request that changes no version is not distributed.**
If every resolved version already has the requested state on the deployment partition, nothing
changes, and by D2 the other partitions are in the same state, so there is nothing to send. Work
that must reach every partition regardless of a state change, like the instance cascade, cannot
rely on this distribution and is started on the deployment partition instead.

**D5. A distributed copy is acknowledged only when it is rejected for an expected reason.**
An expected rejection, such as a batch that exceeds the maximum size, would happen on every attempt,
so it is acknowledged and doesn't block the queue. An unexpected failure is not acknowledged: the
copy is sent again until it applies, and blocks the `DEPLOYMENT` queue in the meantime. A visibly
stuck queue is preferred over a partition that silently keeps the previous state.

**D6. The response does not wait for the other partitions.**
A request is answered once the deployment partition has applied it. Until another partition has
applied the distributed command, it still treats the version as before: it accepts new instances
of a suspended version, or rejects new instances of a resumed one.

## Alternatives considered

- **Distribute from whichever partition receives the request.** Queues are only ordered per sending
  partition, so a suspend and a resume sent through different partitions could be applied in
  opposite orders, and a suspension could reach a partition before its version's deployment.
- **Re-resolve and re-authorize the distributed command on every partition.** Each partition would
  check the permission against its own authorization state, which can briefly differ from the
  deployment partition's because authorization changes travel on the separate `IDENTITY` queue, so
  a request could be applied on only some partitions.
- **Acknowledge every distributed copy, also after an unexpected failure.** Keeps the `DEPLOYMENT`
  queue moving, but finishes the distribution as a success while the partition keeps the previous
  state, with no signal that the partitions diverged.
- **Respond only after all partitions acknowledge.** Closes the window of D6, but ties every
  request's latency to the slowest partition, fails requests while any partition is unavailable,
  and needs a response path the engine doesn't have for distributed commands.

## Consequences

- Suspend and resume throughput is bound to the deployment partition, like deployments.
- New instances of a just-suspended version can still start on other partitions for the duration of
  the distribution; callers that need a hard stop must account for this window.
- A distributed copy that fails unexpectedly on every attempt blocks the `DEPLOYMENT` queue, and
  with it the distribution of later deployments, until the cause is fixed.
- The order of D2 is limited by command distribution itself: receivers don't drop late copies, so
  a copy sent again by a previous leader of the deployment partition can arrive after a newer
  command and be applied after it. This applies to every distributed command, not only to
  suspension.
- Partitions added by scale-up get the suspended versions from the bootstrap snapshot, since the
  suspended marker lives in a global column family; they don't depend on this distribution.
- The instance cascade of suspend and resume must create its batch operation on the deployment
  partition only, never when a partition applies the distributed command, or one batch is created
  per partition.

## Source

- [Distribute process definition suspend/resume to all partitions (#65686)](https://github.com/camunda/camunda/issues/65686)
- [Suspend and resume running instances together with the process definition (#65707)](https://github.com/camunda/camunda/issues/65707)
- [feat: distribute process definition suspend and resume to all partitions (#70546)](https://github.com/camunda/camunda/pull/70546)

