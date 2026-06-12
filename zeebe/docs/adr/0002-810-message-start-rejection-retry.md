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
reuses the existing cross-partition ask, while a local Business ID is retried natively on this
partition. The same observable guarantee applies whether the Business ID is local or remote, even
though the mechanism differs.

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
back-off is event-sourced as a per-ask **magnitude**: the rejection applier doubles a persisted
back-off magnitude (capped) on each rejection reply. This advance is a pure function of the prior
persisted value and the configured bounds — it needs no clock and no event timestamp, so it is
trivially deterministic on replay. The scheduler derives the next-retry time from that magnitude
plus its transient last-sent tracking, re-sending an ask once `lastSent + max(baseInterval,
magnitude)` has passed; the transient also serves as the in-flight guard between a send and its
reply. On recovery the magnitude survives in persisted state while the transient last-sent is
rebuilt, so a long-blocked ask resumes at its backed-off cadence rather than resetting to a
base-interval storm (at the cost of a bounded one-interval phase imprecision after a leader change).

**D4. The retry has a single owner per topology — never the correlation-key buffer.** For
cross-partition rejections the pending-ask registry owns the retry: a buffered message with a live
pending ask is skipped by the buffer scan. For same-partition rejections the native Business-ID-keyed
retry (D5) owns it. In neither case does the correlation-key buffer re-attempt these starts — it keys
on correlation key, while the unblocking event is Business-ID-scoped (the holder may carry a different
correlation key, or none at all).

**D5. Same-partition rejections are retried natively-local by a dedicated registry + scheduler, not
via the cross-partition ask.** When the Business ID hashes to the local partition (`P_K = P_B`,
always so on single-partition clusters), both the success and the retry stay local. A successful
start uses the normal local message-start flow (synchronous, no handshake). A rejection keeps the
message buffered and enrolls it in a **local blocked-start registry** keyed by `(messageKey,
processDefinitionKey)` (a local twin of the cross-partition pending-ask entry, but **never sent over
the wire**). A scheduler periodically re-drives the **local** start for enrolled entries under the
same capped exponential back-off as D3; once the Business ID frees, the re-attempt starts the
instance through the ordinary local flow and the entry is removed (it is also removed when the
buffered message expires). This path uses **no** `MessageStartProcessInstanceRequest` records, dedup
row, or cross-partition lock. A scheduler — rather than a one-shot hook on holder completion — is
chosen deliberately: a Business ID frees not only on completion but also on termination and on
**banning** (banned holders are treated as free), so periodic re-evaluation catches every unblock
cause without hooking each one. The cross-partition ask (D2/D3) is engaged **only** when the Business
ID is remote; the two topologies share the goal (a blocked start eventually runs, bounded by its TTL)
but deliberately **not** the mechanism.

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
- **Event-driven same-partition retry (one-shot on holder completion).** Instead of a scheduler,
  re-attempt the blocked start exactly once, hooked off the holder instance's completion. Rejected:
  a Business ID frees on completion, termination *and* banning, and a banned holder never "completes",
  so a completion hook would leave a start blocked behind a banned holder stuck until its TTL. A
  scheduler re-evaluating live state catches every unblock cause, at the cost of bounded polling that
  back-off already limits.
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
- Two topology-specific retry mechanisms — the cross-partition ask (remote Business ID) and a
  native-local registry + scheduler (local Business ID) — and the correlation-key buffer is relieved
  of a responsibility it could not fulfil correctly. The two flows diverge by design; unifying them
  is a deferred follow-up (see Alternatives).
- Both retries use capped exponential back-off advanced by an event applier, keeping schedulers free
  of persisted-state writes and surviving leader changes without a reset storm.
- A start still expires unstarted if its Business ID stays held for the entire TTL — the legitimate
  "blocked the whole window" outcome.
- The same-partition path stays fully local: success is synchronous and a blocked start is retried
  locally under back-off — no cross-partition records on either path.
- Routing to `P_B` is independent of the uniqueness flag (D6), so the placement invariant holds even
  while the feature is switched off, and toggling the flag never strands instances on the wrong
  partition.

## Source

- [Business ID + message correlation — design thread 1](https://camunda.slack.com/archives/C0AF71HUQ5V/p1776937625439169) (internal)
- [Business ID + message correlation — design thread 2](https://camunda.slack.com/archives/C0AF71HUQ5V/p1777043511569139) (internal)
- [ADR 0001 — Business ID message correlation cross-partition routing](0001-810-message-correlation-business-id-cross-partition.md)

