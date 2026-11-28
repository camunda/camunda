# Receive Hub messages in the engine of partition 1 through a scheduled state machine

**DRI**: Christian Thiel

**Status**: Proposed (8.11)

**Purpose**: Defines where the connection to Hub runs, how it hands Hub messages to the engine one
at a time, and how the engine keeps its position in the Hub sequence.

**Audience**: Zeebe engineers working on Hub-managed identity or on scheduled tasks of the engine;
engineers building the Hub client library; and AI agents reasoning about how Hub messages reach the
engine.

## Context

In hub-managed mode, Hub sends messages to an OC in one sequence per physical tenant. Policy change
sets are the first message type ([ADR 0014](0014-811-hub-managed-identity-policy-updates.md)); more
will follow. The Hub client library offers a pull API: the caller asks for the next message after a
sequence number, owns that position, and the call blocks until Hub has a message.

ADR 0014 left three questions open: which component runs the client, how polling pauses while an
update is in progress, and how the client learns where to resume. Engine actors must never block,
and an outcome must not be reported to Hub before it is durable in the log.

## Decisions

**D1. The Hub connector runs inside the engine, on the leader of partition 1.**
- It is a component of the stream processor of partition 1, not a separate broker service. It
starts when partition 1 starts processing, and stops when processing pauses or the leader steps
down.
- It writes Hub messages as ordinary commands to the log, and reads engine state, as any scheduled
task does. Leadership, replay, and access to the log come with the stream processor.
- This replaces the separate transformer component of ADR 0014 D1.

**D2. A scheduled task drives a small, explicit state machine that handles one message at a time.**
- Each run of the task performs one step of the current phase and schedules the next run. The
phases are: take over after a leader change, await a message, start writing it, write its
commands, and await its outcome.
- Only one message is in flight. The next message is requested only after the outcome of the
current one is reported, so polling pauses while an update is in progress.
- An outcome is reported to Hub only after it is committed in the log; the processors hand it over
in post-commit tasks. The state machine reads engine state only to decide when to write, never to
decide what to report.
- The task runs in its own task group, so waiting for Hub never delays stream processing or other
scheduled tasks.
- After a leader change, the new leader first looks at the message in progress: one whose commands
are still incomplete is canceled and sent again; one that is already being applied is awaited,
and its outcome is reported. Every attempt gets a new random ID, so the engine never mixes the
commands of two attempts.

**D3. The Hub sequence is engine state of its own, independent of policy updates.**
- Partition 1 stores the status of its Hub events by sequence number: in progress, finished
(applied or rejected), or open for another attempt (error, canceled).
- Each message type moves the status through its own events, so future message types share the
same position without changing the policy update records.
- The connector resumes after the last finished sequence, read once when the stream processor has
recovered. The engine rejects a message whose sequence is already finished, so a message handed
over twice is never applied twice.
- This replaces ADR 0014 D8, which stored the sequence with the committed policy version.

**D4. The blocking client is wrapped in an inbox with a non-blocking poll.**
- The inbox keeps at most one request to the client outstanding, on a virtual thread, and keeps the
message that arrives. Each step of the state machine polls the inbox and gets a message or
nothing, without waiting.
- A failing or slow client is retried with backoff inside the inbox, so it never stalls partition 1.
- The state machine owns the position and passes it with every poll; the inbox holds no position of
its own.

## Alternatives considered

- **A broker service next to the engine.** Would need its own leadership tracking, a way to write
  commands, and a way to read engine state, which the stream processor already provides.
- **A push client that calls back with each message.** Delivers on the client's thread, without a
  natural way to hold the next message back until the current outcome is committed.
- **Calling the blocking client directly from the scheduled task.** Blocks an actor thread for the
  length of a long poll.
- **The sequence in the policy state (ADR 0014 D8).** Ties the position to committed policy
  versions, so rejections and other message types could not move it.
- **An update ID derived from the sequence.** The engine could not tell a new attempt at a message
  from the leftovers of an earlier one.

## Consequences

- Throughput is one message per round trip through the log, which is sufficient for the expected
  rate of Hub messages.
- A message may be handed to the engine more than once, for example after a leader change; the
  stored sequence makes that safe. Hub may receive the report of a message again.
- A new message type needs its mapping to commands and its own events that update the Hub event
  status; the scheduling, the inbox and the position stay as they are.
- The engine depends on the Hub client library and its message model.
- Every leader of partition 1 uses one virtual thread for the client.

## Source

- [Hub-managed identity policy updates (camunda/camunda#68925)](https://github.com/camunda/camunda/issues/68925)
- [Persist the handled Hub sequence in the engine, for every message type (camunda/camunda#71101)](https://github.com/camunda/camunda/issues/71101)
- [ADR 0014: Hub policy updates as versioned identity state](0014-811-hub-managed-identity-policy-updates.md)
- [Hub-managed identity data](../../../docs/zeebe/hub-identity.md)

