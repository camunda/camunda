# A way out for rejected cross-partition message-starts: retry the ask with back-off

**DRI**: Mustafa Dagher

**Status**: Accepted (8.10)

**Purpose**: Defines how a message-start that is rejected on Business ID uniqueness (or a not-yet-
distributed subscription) is eventually started, instead of silently expiring.

**Audience**: Zeebe engine engineers and AI agents working on message correlation or partitioning.

## Context

Under [ADR 0001](0001-810-message-correlation-business-id-cross-partition.md), a message-start whose
Business ID is held elsewhere is rejected (`REJECT_UNIQUENESS`), and one whose subscription has not
yet reached `P_B` is rejected (`REJECT_NO_SUBSCRIPTION`). Today the rejected message stays buffered
and is only re-attempted if its own correlation key sees another completion — otherwise it expires
unstarted at its TTL, even if the blocker cleared seconds earlier. The correlation-key buffer is the
wrong thing to wait on: the unblocking event is a Business ID freeing, which may come from an
instance with a different correlation key or none at all.

The outcome: a rejected start is retried until it starts or its TTL expires, reusing the existing
cross-partition ask; a dedicated registry owns that retry; and the same behaviour applies whether
the Business ID is local or remote.

## Decision

**D1. A rejected message-start is retried until it starts or its TTL expires.** `UNIQUENESS_REJECTED`
and `NO_SUBSCRIPTION_REJECTED` are treated as retriable, not terminal. The message remains buffered
(its TTL is the bound); when the blocker clears, the retry starts it.

**D2. Retry reuses the existing cross-partition ask, not a new query.** On rejection the pending ask
is kept rather than removed; the scheduler re-sends the `REQUEST`, `P_B` re-evaluates live state, and
the attempt flips to `STARTED` once the blocker clears. The existing `(processDefinitionKey,
messageKey)` dedup row on `P_B` makes the retried ask idempotent — no duplicate instance.

**D3. Retries use capped exponential back-off, advanced by the event applier.** Back-off prevents a
long-blocked message from storming `P_B`. Because schedulers may not mutate persisted state, the
back-off is event-sourced: the rejection applier advances it on each rejection reply (using the
deterministic event timestamp); the scheduler only reads the resulting next-retry deadline and keeps
its transient send-tracking.

**D4. The rejection registry is the single owner of this retry.** The pending-ask state owns retrying
rejected starts; the correlation-key buffer no longer re-attempts them. The buffer keys on
correlation key, but the unblocking event is Business-ID-scoped, so the buffer cannot trigger these
retries correctly — a buffered message with a live pending ask is skipped by the buffer scan.

**D5. Same-partition parity: unify the retry, keep the local success path.** When the Business ID
hashes to the local partition (`P_K = P_B`, always so on single-partition clusters), the success
path stays local and synchronous, but a rejection enrolls in the same registry and retries via a
self-addressed `REQUEST`. One retry implementation serves both topologies; the handshake cost is
paid only by the rare blocked message.

## Alternatives considered

- **A dedicated poll mirroring the lock-release query.** A separate read-only "is the Business ID
  free?" query plus its own registry. Rejected: the poll is non-authoritative (it still needs the
  ask to start), so it is two mechanisms where one suffices, and it re-derives the ask path's
  existing re-evaluation and idempotency for a traffic saving that back-off already bounds.
- **Full same-partition unification — handshake even for the same-partition success path.** One code
  path, but it taxes the common and single-partition case with extra records and a dedup write per
  start and makes a synchronous start asynchronous, to pay for at-least-once machinery that
  exactly-once same-log delivery does not need.

## Consequences

- Closes the liveness gap: a start blocked purely on Business ID now runs once the blocker clears,
  bounded by the message TTL; no duplicates and no leaked state.
- One retry mechanism for both topologies; the correlation-key buffer is relieved of a
  responsibility it could not fulfil correctly.
- Back-off is event-sourced, keeping schedulers free of persisted-state writes and surviving leader
  changes without a reset storm.
- A start still expires unstarted if its Business ID stays held for the entire TTL — the legitimate
  "blocked the whole window" outcome.
- The same-partition success path is unchanged; only the rare blocked case pays the ask handshake.

## Source

- [Business ID + message correlation — design thread 1](https://camunda.slack.com/archives/C0AF71HUQ5V/p1776937625439169) (internal)
- [Business ID + message correlation — design thread 2](https://camunda.slack.com/archives/C0AF71HUQ5V/p1777043511569139) (internal)
- [ADR 0001 — Business ID message correlation cross-partition routing](0001-810-message-correlation-business-id-cross-partition.md)

