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

The outcome: a rejected start is retried until it starts or its TTL expires; a remote Business ID
reuses the existing cross-partition ask, while a local Business ID is retried by re-driving the local
start when its holder frees the ID — discovered through a Business-ID index on the buffered message.
The same observable guarantee applies whether the Business ID is local or remote, even though the
mechanism differs.

## Decision

**D1. A rejected message-start is retried until it starts or its TTL expires.** `UNIQUENESS_REJECTED`
and `NO_SUBSCRIPTION_REJECTED` are treated as retriable, not terminal. The message remains buffered
(its TTL is the bound); when the blocker clears, the retry starts it.

**D2. Retry reuses the existing cross-partition ask, not a new query.** On rejection the pending ask
is kept rather than removed; the scheduler re-sends the `REQUEST`, `P_B` re-evaluates live state, and
the attempt flips to `STARTED` once the blocker clears. The existing `(processDefinitionKey,
messageKey)` dedup row on `P_B` makes the retried ask idempotent — no duplicate instance.

**D3. The cross-partition ask retries use capped exponential back-off, advanced by the event
applier.** Back-off prevents a long-blocked message from storming `P_B`. Because schedulers may not
mutate persisted state, the back-off is event-sourced as a per-ask **magnitude**: the rejection
applier doubles a persisted back-off magnitude (capped) on each rejection reply. This advance is a
pure function of the prior persisted value and the configured bounds — it needs no clock and no event
timestamp, so it is trivially deterministic on replay. The scheduler derives the next-retry time from
that magnitude plus its transient last-sent tracking, re-sending an ask once `lastSent +
max(baseInterval, magnitude)` has passed; the transient also serves as the in-flight guard between a
send and its reply. On recovery the magnitude survives in persisted state while the transient
last-sent is rebuilt, so a long-blocked ask resumes at its backed-off cadence rather than resetting to
a base-interval storm (at the cost of a bounded one-interval phase imprecision after a leader change).
The same-partition retry (D5) needs no back-off: it is event-driven — re-driven once when a holder
frees the Business ID — not polled, so there is nothing to storm.

**D4. The correlation-key buffer is never relied on to retry a Business-ID rejection — it keys on the
wrong thing.** The unblocking event is a Business ID freeing, which may come from a holder with a
different correlation key, or none at all, so the correlation-key buffer scan cannot be trusted to
re-attempt these starts. For cross-partition rejections the pending-ask registry is the single retry
owner: a buffered message with a live pending ask is skipped by the buffer scan, so it does not emit a
redundant second ask. For same-partition rejections the Business-ID index + completion hook (D5)
drives the retry; should the correlation-key scan happen to revisit the same message once the ID is
free, the ordinary correlation guards (`existMessageCorrelation`, the deadline check) make the overlap
a harmless no-op rather than a double start.

**D5. Same-partition rejections are retried by re-driving the local start when the Business ID frees,
discovered through a Business-ID index on the buffered message — no scheduler, no cross-partition
machinery.** When the Business ID hashes to the local partition (`P_K = P_B`, always so on
single-partition clusters), both success and retry stay local. A successful start uses the normal
local message-start flow (synchronous, no handshake). On rejection the message simply stays buffered;
it is made discoverable by Business ID through a secondary index on the buffered-message store, keyed
by `(tenantId, businessId, messageKey)` and maintained on the **same publish/expiry lifecycle as the
message itself** (written when the message is buffered, removed when it expires) — so it needs no
separate registry, enrollment event, or cleanup path. When a holder instance completes or terminates,
the post-transition action that already re-drives the correlation-key buffer additionally looks up
buffered messages by the freed Business ID and re-drives the ordinary local start for each (routing
still honours D6). The existing correlation guards make a redundant re-drive a no-op. This path uses
**no** `MessageStartProcessInstanceRequest` records, dedup row, cross-partition lock, scheduler, or
back-off.

This deliberately mirrors the existing correlation-key buffer, which is likewise only re-driven on a
holder's completion/termination. It therefore inherits the same boundary: a Business ID also frees on
**banning** (banned holders are treated as free) and on migration, neither of which fires a
completion/termination event, so a start blocked behind a banned or migrated holder waits until its
TTL rather than restarting immediately. This is **accepted** because it is exactly the existing
behaviour of the correlation-key buffer — a message buffered behind a banned holder is already
stranded until TTL today — so the Business-ID buffer is made a faithful twin of it rather than being
granted a new, stronger liveness guarantee on only one arm. The cross-partition ask (D2/D3) is engaged
**only** when the Business ID is remote; the two topologies share the goal (a blocked start eventually
runs, bounded by its TTL) but deliberately **not** the mechanism.

**D6. The uniqueness flag gates the rejection, not the routing/placement.** `businessIdUniquenessEnabled`
controls only whether a uniqueness conflict is *rejected*, never whether a Business-ID-carrying
message-start is *routed* to `P_B`. A remote Business ID is always delegated to `P_B` — preserving the
ADR 0001 invariant that every root PI carrying a Business ID lives on `hash(businessId)` — and `P_B`
creates the instance, replying `REJECT_UNIQUENESS` only while the flag is on. Decoupling placement
from the flag keeps it consistent across flag flips, so enabling the feature later never finds
Business-ID-carrying instances stranded on the wrong partition. (Same-partition placement is trivially
flag-independent — the instance is already local — so this matters for the cross-partition arm.)

## Alternatives considered

- **A dedicated poll mirroring the lock-release query.** A separate read-only "is the Business ID
  free?" query plus its own registry. Rejected: the poll is non-authoritative (it still needs the
  ask to start), so it is two mechanisms where one suffices, and it re-derives the ask path's
  existing re-evaluation and idempotency for a traffic saving that back-off already bounds.
- **Same-partition retry via a self-addressed cross-partition ask** (the original D5). A
  same-partition rejection would enroll in the pending-ask registry and re-send a `REQUEST` addressed
  to its own partition, reusing one retry implementation for both topologies. Rejected on closer
  analysis: it imports the entire cross-partition apparatus onto a single partition — a dedup row, a
  cross-partition lock plus its self-poll release, and the multi-record `REQUEST`/`STARTED`
  handshake — so a same-partition start that succeeds *after a retry* diverges from one that succeeds
  *immediately* (different record shape, and the retried instance ends up tracked by two release
  mechanisms at once). The loopback also reset the persisted back-off magnitude on every retry and
  leaked the pending ask for fire-and-forget (`TTL <= 0`) publishes. The native-local retry (D5)
  avoids all of this.
- **Same-partition retry via a dedicated blocked-start registry + scheduler** (an earlier D5). A
  rejection would enroll the message in its own persisted registry keyed by `(messageKey,
  processDefinitionKey)` with an event-sourced back-off magnitude, and a poller would re-drive the
  local start on a back-off cadence. Rejected: it builds a parallel registry, a second back-off
  mechanism, and a poller purely to restart *promptly* in the cases a completion hook misses — a
  banned or migrated holder, which frees the Business ID without a completion/termination event. Those
  cases are rare and already bounded by the message TTL, and giving the Business-ID buffer a *stronger*
  liveness guarantee than the correlation-key buffer sitting next to it (which is itself only re-driven
  on completion/termination, and so also strands a message behind a banned holder until TTL) is an
  inconsistency, not an improvement. The completion-hook design (D5) reuses the existing post-transition
  re-drive and keeps the two buffers behaviourally identical.
- **Full same-partition unification — run the ask handshake for both topologies (deferred,
  possible follow-up).** Instead of a local flow for same-partition and a separate ask flow for
  cross-partition, route *every* message-start (both topologies) through one uniform `REQUEST`
  handshake. This removes the two-flow divergence by construction (a single code path, a single
  retry mechanism). It is **not chosen now** because it taxes the common and single-partition case
  with extra records and a dedup write per start and makes a synchronous start asynchronous, to pay
  for at-least-once machinery that exactly-once same-log delivery does not need. Given the divergence
  between the two flows, however, it remains a legitimate candidate to revisit — as a separately
  analysed, benchmarked decision in a follow-up ADR — once the native retry has shipped.

## Consequences

- Closes the liveness gap: a start blocked purely on Business ID now runs once the blocker clears,
  bounded by the message TTL; no duplicates and no leaked state.
- Two topology-specific retry mechanisms — the cross-partition ask (remote Business ID) and re-driving
  the local start on holder completion via a Business-ID index (local Business ID) — and the
  correlation-key buffer is relieved of a responsibility it could not fulfil correctly. The two flows
  diverge by design; unifying them is a deferred follow-up (see Alternatives).
- The cross-partition ask uses capped exponential back-off advanced by an event applier, keeping the
  scheduler free of persisted-state writes and surviving leader changes without a reset storm. The
  same-partition retry needs no scheduler or back-off — it is event-driven off holder completion.
- A start still expires unstarted if its Business ID stays held for the entire TTL — the legitimate
  "blocked the whole window" outcome.
- Accepted limitation (same-partition): a start blocked behind a holder that frees its Business ID
  *without* a completion/termination event — a **banned** holder (banned holders are treated as free)
  or a **migrated** one — waits until its TTL rather than restarting immediately. This is identical to
  the existing correlation-key buffer, which is also only re-driven on completion/termination, so the
  Business-ID buffer is a faithful twin of it rather than a new, stronger guarantee on one arm.
- The same-partition path stays fully local: success is synchronous and a blocked start is re-driven
  locally on holder completion — no cross-partition records, no scheduler, on either path.
- Routing to `P_B` is independent of the uniqueness flag (D6), so the placement invariant holds even
  while the feature is switched off, and toggling the flag never strands instances on the wrong
  partition.

## Source

- [Business ID + message correlation — design thread 1](https://camunda.slack.com/archives/C0AF71HUQ5V/p1776937625439169) (internal)
- [Business ID + message correlation — design thread 2](https://camunda.slack.com/archives/C0AF71HUQ5V/p1777043511569139) (internal)
- [ADR 0001 — Business ID message correlation cross-partition routing](0001-810-message-correlation-business-id-cross-partition.md)

