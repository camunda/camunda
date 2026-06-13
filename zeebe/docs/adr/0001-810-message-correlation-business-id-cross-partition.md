# Business ID message correlation: P_K owns messages, P_B enforces uniqueness, P_K pulls for release

**DRI**: Mustafa Dagher

**Status**: Accepted (8.10)

**Purpose**: Defines how Business ID interacts with cross-partition message routing for message
events, so the existing message and uniqueness routing contracts are both preserved.

**Audience**: Zeebe engine engineers and AI agents working on message correlation or partitioning.

## Context

A message is routed to `P_K = hash(correlationKey)`, where all of its lifecycle state lives
(subscriptions, TTL, deduplication, buffering, the process-correlation-key lock). A process instance
with a `businessId` is owned by `P_B = hash(businessId)`, where Business ID uniqueness is a local
lookup. When Business ID is used as an additional filter on message events, a single publish can
carry a `correlationKey` and a `businessId` whose hashes resolve to different partitions, so the
message and the instance it would create no longer co-locate.

The outcome: message routing is unchanged; `P_K` remains the single owner of message state and
`P_B` remains the single owner of Business ID uniqueness. Message-start events bridge the two
partitions with a cross-partition ask; other message events filter locally; and the resulting
cross-partition lock is released by `P_K` polling `P_B`.

## Decision

**D1. `P_K` owns message state; `P_B` owns Business ID uniqueness.** Messages keep routing by
`hash(correlationKey)`. Subscriptions, TTL, dedup, buffering, and the correlation-key lock stay on
`P_K`; the uniqueness check stays a local lookup on `P_B`. Neither concern is relocated or
duplicated.

**D2. Message-start events reconcile the two partitions with a cross-partition ask.** When a
businessId-bearing start would create its instance on a different `P_B`, `P_K` sends a `REQUEST` to
`P_B`, which creates the instance locally and replies `STARTED`, or declines with `REJECT_UNIQUENESS`
(an active instance already holds the businessId) or `REJECT_NO_SUBSCRIPTION` (the start-event
subscription has not yet been distributed to `P_B`). On a rejection the message stays buffered on
`P_K`.

**D3. The cross-partition correlation-key lock is released by pull, not push.** For a remotely
created start, `P_K` records the holder instance and polls `P_B` (with back-off and batching) for
that specific instance's completion, then releases the lock and picks up the next buffered message —
keeping lock semantics identical to a single-partition start. Locally created starts use the
existing local release path unchanged.

**D4. Cross-partition creates are idempotent via a dedup row on `P_B`.** `P_B` records
`(processDefinitionKey, messageKey) → processInstanceKey` with a deletion deadline equal to the
message deadline. At-least-once ask retries either hit this row (re-reply the same instance, no
second create) or arrive after it and the buffered message have expired together. The contract
`retryDeadline <= messageDeadline` is what prevents duplicate instances.

**D5. Catch, boundary, and intermediate message events filter Business ID locally, not via the ask.**
A subscription stores its instance's `businessId` at open time. Correlation applies an asymmetric
local match: a message without a `businessId` correlates regardless; a message with one correlates
only to a subscription whose stored `businessId` matches exactly. Only start events need a uniqueness
check, and only `P_B` can answer it — hence the asymmetry with D2.

## Alternatives considered

- **Combined-hash routing `hash(correlationKey + businessId)`.** Co-locates the lock and the
  uniqueness check on one partition, but breaks both existing routing contracts and cannot satisfy
  the requirement that a message without a `businessId` still correlates to an instance that has one
  (it would route to a different partition than the subscription).
- **`P_B` owns blocked starts; `P_K` forwards-and-forgets.** Moves the message itself to `P_B` on
  forward. Relocates cross-partition state rather than removing it, and breaks `messageId`
  deduplication and correlation to processes that listen for the same message without a Business ID.
- **Push-based release notification from `P_B`.** `P_B` would track which partitions hold dependent
  work and notify them on each completion. Rejected: it requires per-waiter bookkeeping and reliable
  delivery on `P_B`, is not self-healing across restarts/leadership changes, and has no decisive
  efficiency advantage over the pull (D3).

## Consequences

- Existing routing contracts for `correlationKey` and `businessId` are preserved; message lifecycle
  stays observable from one partition per correlation key.
- New cross-partition machinery is confined to the message-start path: an ask with three reply
  outcomes, a per-`P_K` record of remotely held locks, and the dedup row on `P_B`.
- The pull keeps all reaction-to-completion logic on the partition that owns the work and is
  self-healing, at the cost of added latency on the release path (bounded by the poll interval and
  back-off).
- A start rejected purely on Business ID uniqueness stays buffered and relies on its TTL and existing
  buffered-message triggers, matching single-partition behaviour. Proactively retrying it when the
  Business ID frees is decided separately in
  [ADR 0002](0002-810-message-start-rejection-retry.md).

## Source

- [Business ID + message correlation — design thread 1](https://camunda.slack.com/archives/C0AF71HUQ5V/p1776937625439169) (internal)
- [Business ID + message correlation — design thread 2](https://camunda.slack.com/archives/C0AF71HUQ5V/p1777043511569139) (internal)
- [Process Instance Creation: Message event](https://docs.camunda.io/docs/next/components/concepts/process-instance-creation/#message-event)

