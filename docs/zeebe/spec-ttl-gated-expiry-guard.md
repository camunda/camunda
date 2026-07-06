# Spec: Deterministic TTL-Gated Expiry Guard for Cross-Partition Message-Start Requests

|                         |                                                                                                                                                                                                                                                                                                                               |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Status**              | Draft — for team review                                                                                                                                                                                                                                                                                                       |
| **Supersedes / amends** | Proposed solution in [#55476](https://github.com/camunda/camunda/issues/55476) (`isRetry` flag approach)                                                                                                                                                                                                                      |
| **Related**             | [#51689](https://github.com/camunda/camunda/issues/51689) (parent feature), [#54108](https://github.com/camunda/camunda/pull/54108), [#55198](https://github.com/camunda/camunda/pull/55198) (grace-period mitigation, merged `11bdf26`), ADR 0002                                                                            |
| **Component**           | `component/zeebe` — engine, message correlation                                                                                                                                                                                                                                                                               |
| **Author**              | Mustafa Dagher                                                                                                                                                                                                                                                                                                                |
| **Revision**            | v3 — incorporates the Task 0 precondition findings (§12) and the outcome-plumbing code review (§13); final design: TTL-gated guard + `REJECT_EXPIRED` / `EXPIRED_REJECTED` pair with `backOff()` applier semantics. Alternatives considered during design are recorded in §3.2 (`isRetry`) and §6.3 (local command rejection) |

---

## 1. Summary

Replace the time-window (`messageStartAskRetryGrace`) mitigation for the near-deadline
cross-partition duplicate window with a **deterministic guard on `P_B`**:

> Reject any `MESSAGE_START_PROCESS_INSTANCE_REQUEST` whose `messageDeadline <= now` **unless the
message was published with `TTL == 0`**.

The guard applies uniformly to first-arrivals and retries. It closes the duplicate window regardless
of inter-partition delay magnitude, is immune to clock skew by construction (single-clock
comparison), preserves the documented TTL=0 first-arrival activation, and — unlike the `isRetry`
proposal in #55476 — also closes the symmetric **reordered/late first-arrival** window (Section
3.2), which the `isRetry` design leaves open.

The grace period becomes redundant for correctness and is removed (Section 6.5), shrinking the
config surface introduced in #55198. When the guard fires, `P_B` replies with a new
**`REJECT_EXPIRED`** command, applied on `P_K` as an **`EXPIRED_REJECTED`** event whose applier
calls **`backOff()`** — the identical semantics of the two existing rejections (Section 6.3). This
preserves the protocol's shape (every `REQUEST` gets exactly one reply), feeds the existing
exponential back-off so post-deadline retry churn is damped, and leaves pending-ask *removal*
exclusively owned by `P_K`'s own message-expiry path, on `P_K`'s clock.

---

## 2. Background

Cross-partition message-start uniqueness uses a pending-ask handshake: `P_K` (message owner,
`hash(correlationKey)`) forwards a `REQUEST` to `P_B` (`hash(businessId)`), which dedups on
`(processDefinitionKey, messageKey)` and replies. `P_K` retries un-replied or rejected asks under
exponential back-off until the message deadline; `P_B` garbage-collects dedup rows after the
deadline (currently `now - grace`).

**The race (as filed in #55476):** a retry dispatched just before the deadline can arrive at `P_B`
after the sweep has removed the dedup row. Finding no row, `P_B` re-evaluates live state; if the
holder PI has since completed and freed the Business ID, a **duplicate PI** is started. The 30s
grace shipped in #55198 absorbs this only while `inter-partition delay + skew < grace` — it
mitigates, it does not close.

**Why this matters:** delays > 30s are not exotic; they are what network partitions, leader
failovers, and backpressure incidents look like. The residual window opens precisely under degraded
conditions. Additionally, a duplicate is not merely a second PI: two live instances both "hold" the
same Business ID on `P_B`, so the first completion frees the ID while the second still runs,
corrupting subsequent uniqueness decisions for that ID.

---

## 3. Design

### 3.1 The guard

In `MessageStartProcessInstanceRequestRequestProcessor#processRecord` — **after** the unconditional
`REQUESTED` follow-up event (the processor journals every arrival; see §13.2) and **before**
`lookupValidDedupHit`:

```
if (request.getMessageDeadline() <= clock.millis() && request.getMessageTtl() > 0) {
    // Deterministically expired: never create, never live-evaluate.
    // Reply like every other outcome in this processor (see §6.3);
    // the EXPIRED_REJECTED applier on P_K backs the ask off.
    commandSender.sendStartProcessInstanceExpiredRejected(request);
    return;
}
// TTL == 0, or deadline not yet passed: existing flow unchanged
// (dedup lookup → live evaluation → START / REJECT_UNIQUENESS / REJECT_NO_SUBSCRIPTION)
```

Decision table:

| `messageDeadline <= now` (P_B clock) | `messageTtl` | Attempt                              | Outcome                                                                                                                        |
|--------------------------------------|--------------|--------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| no                                   | any          | any                                  | **Unchanged** — dedup lookup, then live evaluation                                                                             |
| yes                                  | `> 0`        | first-arrival **or** retry           | **`REJECT_EXPIRED` reply** — no dedup lookup, no activation; on `P_K` the `EXPIRED_REJECTED` event's applier calls `backOff()` |
| yes                                  | `== 0`       | first-arrival or (theoretical) retry | **Unchanged** — dedup lookup, then live evaluation; preserves documented TTL=0 activation                                      |

### 3.2 Why gate on TTL rather than on `isRetry` (the #55476 proposal)

`isRetry` is a proxy for the property that actually makes past-deadline activation safe. The
property is **not** "this is the first dispatch"; it is "this message had `TTL == 0`", because only
then is it guaranteed that no *other* attempt can already have created a PI.

The `isRetry` design (`!isRetry → activate even past deadline`) leaves a window open for **TTL > 0**
messages:

1. First-arrival `F` is dispatched and severely delayed in flight (the same unbounded-delay
   assumption the issue itself rests on).
2. Retry `R` overtakes `F`, arrives before the deadline, finds no dedup row, starts the PI, writes
   the dedup row. (Correct so far — `F` would normally be absorbed by the row.)
3. The deadline passes; the holder completes and frees the Business ID; the sweep removes the dedup
   row.
4. `F` arrives: `isRetry == false`, `messageDeadline <= now`, no dedup hit → under the `isRetry`
   rule it **activates → duplicate**.

This is the same race relocated from "late retry" to "late first-arrival racing its own retry". Any
fix claiming determinism "regardless of delay magnitude" cannot exempt one class of delayed message.
The TTL gate rejects `F` in step 4 (TTL > 0, deadline passed) and closes both windows with one rule.

**TTL=0 safety under the TTL gate:** for a TTL=0 message, every attempt carries
`messageDeadline <= now` on arrival. Under this guard no TTL=0 attempt is expiry-rejected;
duplicates among TTL=0 attempts are prevented by the dedup row exactly as today (a
redelivered/duplicate attempt either hits the row or is the byte-identical single logical request).
No competing *rejected-then-raced* path exists because rejection never fires for TTL=0.

### 3.3 Clock-skew immunity (the core correctness argument)

The guard is deterministic where the grace is not because it is **single-clock**:

- Both the expiry guard and the dedup sweep compare the *same* `messageDeadline` value against
  *`P_B`'s* clock. They can never disagree with each other: if the sweep considers the row expired (
  removable), the guard considers the request expired (rejectable), for every possible interleaving.
- Skew between `P_K` and `P_B` therefore only shifts the accept/reject boundary — a bounded *
  *liveness** effect (a near-deadline request on a fast `P_B` clock may be rejected although `P_K`
  still considered the message alive; the message then expires unstarted). It can never reopen a *
  *safety** (duplicate) window.
- The grace approach, by contrast, races `P_B`'s sweep clock against `P_K`'s send timing plus
  transit — a cross-clock comparison for which no finite window is provably safe.

For a *uniqueness* feature this is the correct trade: the boundary failure mode becomes "expired
message not started" (at-most-once) instead of "duplicate started" (uniqueness violated). The record
this principle in ADR 0002: **safety comparisons must be single-clock; multi-clock windows may only
ever serve liveness.**

### 3.4 Grace period: replace, not complement

With the guard in place the grace window contributes nothing to correctness. Its only residual
value — an in-grace late retry receives an informative cached-PI-key re-reply instead of
`REJECT_EXPIRED` — is moot: neither reply changes the end state (no PI results from the retry, and
the ask ends at `P_K`'s message expiry either way). Decision: **remove** `messageStartAskRetryGrace`
and revert the sweep/lookup thresholds to `now` (Section 6.5), following the deprecation policy in
Section 7.3.

---

## 4. Precondition investigation (Task 0 — gates the rest of the plan)

The cheapest variant of this design requires **no wire change**. Before any schema work, verify
against `11bdf26`:

- **(a)** Does the `MessageStartProcessInstanceRequestRecord` (or the embedded message payload it
  carries) already contain the message **TTL** or the **publish timestamp**?
  - If **TTL** present: guard uses it directly. *No schema change; skip Section 6.1.*
  - If **publish timestamp** present: `messageTtl == 0 ⇔ messageDeadline == publishTimestamp` (both
    derived on `P_K` from the same clock and stored, so the equality is exact, not skew-sensitive).
    *No schema change; skip Section 6.1.*
  - If **neither**: add a field per Section 6.1.
- **(b)** Confirm TTL=0 retry behavior on `P_K`: #55198 states message expiry clears the pending
  ask, and TTL=0 expires synchronously in the dispatch step. Verify whether the ask survives for
  reply correlation and whether `PendingMessageStartAskCheckScheduler` can ever re-dispatch a TTL=0
  ask. This determines whether the "TTL=0 retry" row in the decision table is reachable (the design
  is safe either way, but the ADR should state the fact).
- **(c)** Confirm `P_K` tolerates a late `REJECT_EXPIRED` command after the ask/message are gone
  (expected: no-op, mirroring the existing tolerated late `START`). *Answered in §12.3.*
- **(d)** ~~Confirm that a command rejection written for an inter-partition `REQUEST` command is
  well-defined end-to-end (exported, rendered sensibly).~~ *Obsolete: this check applied only to
  the local-command-rejection alternative, which the code review rejected (§13) — command
  rejections are not exported by default and the processor's outcome channel is the reply
  command, not the rejection writer.*

Deliverable: short findings comment on the issue; flips Section 6.1 between "skip" and "execute".

---

## 5. Goals / non-goals

**Goals**

1. Zero duplicate-PI windows in the cross-partition handshake, for any delay magnitude and any
   `P_K`/`P_B` clock skew.
2. Preserve documented TTL=0 cross-partition first-arrival activation.
3. Reduce mechanism: remove the grace config knob and its threshold plumbing.
4. Auditable outcome: every expiry-rejected request produces exported, replayable records — the
   `REQUESTED` event on `P_B` (already unconditional) and the `EXPIRED_REJECTED` event on `P_K`
   (§6.3).
5. Preserve the handshake's protocol shape: every `REQUEST` receives exactly one reply command
   (§13.2).
6. Rolling-upgrade safe.

**Non-goals**

- Ask *removal* driven from `P_B`. The `EXPIRED_REJECTED` applier backs the ask off; removal
  remains exclusively owned by `P_K`'s message-expiry path — on `P_K`'s clock, the authoritative
  clock for that message's lifecycle.
- Changing same-partition Business-ID uniqueness or the completion re-drive hooks from #55198.
- Changing retry/back-off cadence on `P_K` (rejection-count back-off stays as shipped).
- Guaranteeing that near-deadline messages *start* under extreme skew (bounded liveness loss at the
  boundary is accepted and documented).

---

## 6. Implementation plan

### 6.1 Wire/schema (required — Task 0 resolved, see §12.1)

Task 0 confirmed the record carries neither TTL nor publish timestamp, so this section executes:

- Add `messageTtl` (long, millis) to `MessageStartProcessInstanceRequestRecord` — carry the
  *semantic* fact, not an attempt ordinal. Prefer this over `isRetry`/`attemptCount` for the guard;
  `attemptCount` may still be added independently later for observability, but it is not
  load-bearing here.
- Default value `0`, matching `MessageRecord.timeToLive` (whose absent value is likewise `0` — a
  message without a positive TTL is fire-and-forget). Because the guard rejects only on
  `messageTtl > 0`, an unset field falls through exactly as a genuine TTL=0 message does. **No
  `-1`/"unknown" sentinel is needed**: this feature is unreleased, so no broker ever emits this
  record without the field (see the pre-GA note in Section 7.1).
- Set at both dispatch sites from the buffered message's stored TTL:
  - `MessageCorrelateBehavior#dispatchCrossPartitionStartProcessInstanceAsk` (first dispatch)
  - `PendingMessageStartAskCheckScheduler#sendAsk` (re-dispatch; note retries re-emit the original
    `messageDeadline` — they must equally re-emit the original TTL)
  - See §12.1 for the concrete plumbing: `MessageCorrelateBehavior.MessageData` gains a
    `messageTtl` component (three construction sites), and the **persisted**
    `MessageStartProcessInstanceAsk` state entry must also carry `messageTtl` so re-dispatch can
    re-emit it verbatim.
- Update SBE/serialization, protocol version notes, and record printers/asserts.

### 6.2 Target processor guard (`P_B`)

`MessageStartProcessInstanceRequestRequestProcessor#processRecord`:

- Insert the guard of Section 3.1 **after** the unconditional `REQUESTED` follow-up event (every
  arrival stays journaled, matching all other outcomes — §13.2) and **before**
  `lookupValidDedupHit` and the `businessIdUniquenessEnabled` gate — the guard is about message
  lifetime, not uniqueness, so it must fire even when uniqueness is disabled (routing is
  flag-independent since #55198; an expired request must not create a PI on `P_B` in either mode).
- Simplify `lookupValidDedupHit` back to `deletionDeadline <= now` once 6.5 lands.

### 6.3 Expiry handling: `REJECT_EXPIRED` → `EXPIRED_REJECTED` with `backOff()` semantics

**Decision:** when the guard fires, `P_B` replies with a new **`REJECT_EXPIRED`** command —
exactly like the two existing rejection outcomes. On `P_K`, the handling processor writes the
**`EXPIRED_REJECTED`** event; its V1 applier calls **`backOff()`**, the identical semantics of
`MessageStartProcessInstanceUniquenessRejectedV1Applier` / `...NoSubscriptionRejectedV1Applier`.
The ask is **kept, never removed** by this path; removal remains exclusively owned by `P_K`'s
message-expiry cleanup (`MessageExpiredV2Applier` → `askState.removeAllByMessageKey`).

Concretely (per the enum convention verified in §12.4):

- `MessageStartProcessInstanceRequestIntent`: add **`REJECT_EXPIRED((short) 10, false)`** and
  **`EXPIRED_REJECTED((short) 11, true)`** (next free ordinals after `EXPIRED_DEDUP_DELETED = 9`);
  extend `from(short)`.
- `SubscriptionCommandSender`: add `sendStartProcessInstanceExpiredRejected(request)` — the
  reviewer's originally suggested line in #55198, essentially.
- `P_K` reply handling: a processor branch mirroring the existing two rejects, writing the
  `EXPIRED_REJECTED` follow-up event.
- `MessageStartProcessInstanceExpiredRejectedV1Applier`: calls `backOff()`; tolerant no-op when
  the ask is already gone (`ask == null → return`, the pattern the existing `backOff` appliers
  already implement — §12.3). Golden file included.

**Why `backOff()` and not `remove()`:** `remove()` would let `P_B`'s clock drive `P_K`-side
cleanup and, under a fast `P_B` clock, would drop `hasLivePendingAsk` while the buffered message
is still alive — re-opening the single-retry-owner guard to a fresh ask from the
completion-driven buffer re-drive (ask/reject ping-pong). `backOff()` keeps every invariant
intact: the ask stays the single retry owner, `P_K`'s expiry stays the sole terminator, and the
persisted `rejectionCount` feeds the scheduler's existing exponential back-off, damping
post-deadline retries (1 reject → 2× interval → 4× → …) until the TTL checker expires the message
and clears the ask. Expiry's terminality is enforced where it belongs — the message-expiry
applier — not by the reply.

**Alternative considered and rejected during design: local command rejection on `P_B`, no
reply.** Reviewing the actual code at `11bdf26` (§13) overturned each of its supporting arguments:

- **It starves the back-off mechanism.** The scheduler re-sends every ask due by
  `lastSentTime + backoff` with *no deadline check* (§13.1), and the back-off exponent advances
  *only* via rejection replies. A silent outcome therefore leaves post-deadline asks retrying at
  the **undamped base interval** until the (interval-driven) TTL checker expires the message —
  not the "skew-only, 0–2 requests" window that alternative assumed, and indistinguishable on
  `P_K` from
  message loss, repurposing the at-least-once recovery path as an intentional steady state.
- **It breaks the protocol's shape.** Every outcome path in the request processor ends in exactly
  one `commandSender.send...` reply; there is no silent outcome and no `rejectionWriter` in this
  protocol (§13.2). The processor also unconditionally journals `REQUESTED` before evaluating —
  a command-rejection outcome would either skip that journaling or incoherently write both.
- **Its auditability claim fails in practice.** Command rejections are not exported by default
  (the ES/OS exporters index events, not rejections), so the "greppable audit record" exists only
  on the raw stream, invisible to Operate and incident tooling (§13.3). The protocol's audit
  trail is *events* — which is what `EXPIRED_REJECTED` provides.
- **Its central objection dissolves under `backOff()`.** The applier "trilemma" that initially
  argued against the pair evaluated
  `backOff()` against the wrong goal ("stop retries entirely") and dismissed it as pointless; the
  right goal is "damp retries while preserving invariants", for which `backOff()` is exactly
  right — and it makes the symmetry with the other two rejections real, not false: the correct
  response to all three is *keep the ask, count the rejection, let the scheduler back off, let
  `P_K`'s lifecycle decide the end state*.

Net cost comparison: the pair is the **third instance of an existing pattern** (two exemplars to
copy for the sender, processor branch, applier, and tests), whereas the local rejection would have
been the **first instance of a new one**. In an event-sourced request/reply protocol, a
deliberately silent outcome is the more expensive kind of novelty — record this lesson in the ADR.

### 6.4 TTL=0 path

- No behavior change intended; the guard's `messageTtl == 0` branch falls through to today's flow.
- If Task 0(a) resolved to the publish-timestamp equality, encapsulate `isZeroTtl(record)` in one
  place with a comment explaining exactness (single-writer, single-clock on `P_K`).

### 6.5 Grace removal

- Delete `messageStartAskRetryGrace` plumbing end-to-end:
  `EngineConfiguration.DEFAULT_MESSAGE_START_ASK_RETRY_GRACE`, `ProcessInstanceCreationCfg`,
  `EngineCfg` mapping, Spring `ProcessInstanceCreation` property, `BrokerBasedPropertiesOverride`.
- Revert `MessageStartDedupExpirationSweepScheduler` and
  `MessageStartProcessInstanceRequestSweepExpiredDedupsProcessor` thresholds from `now - grace` to
  `now`.
- Follow deprecation policy (7.3): if the property shipped in a released minor,
  accept-and-warn-ignore for one minor before hard removal; if it only ever existed on `main`,
  delete outright.
- Update `MessageStartProcessInstanceDedupBehaviorTest` grace tests: the "late retry within grace
  re-replies cached key" pin is replaced by "late retry receives `REJECT_EXPIRED`; no PI created;
  ask backed off on `P_K`".

### 6.6 Suggested commit/PR slicing

Mirroring the incremental style of #55198 (each commit green):

1. Task 0 findings (§12) and code-review findings (§13) — done.
2. Schema: add `messageTtl` to the request record + serialization + defaults, and to the persisted
   `MessageStartProcessInstanceAsk` state entry (§12.1).
3. Dispatch sites populate TTL (`MessageData` component + first dispatch + re-dispatch).
4. Protocol pair: `REJECT_EXPIRED` / `EXPIRED_REJECTED` intents + `from(short)`, sender method,
   `P_K` processor branch, `backOff()` applier + golden file (§6.3).
5. The guard in the request processor (after `REQUESTED`, before dedup lookup) + unit tests.
6. Grace removal (config, sweep thresholds, lookup simplification) + test updates.
7. Integration tests (Section 8.2) — including the reordered-first-arrival scenario.
8. ADR 0002 amendment + docs (Section 9), including the outcome-plumbing decision record (§6.3,
   §13).

Steps 2–5 in one PR, 6 in a second, 7–8 alongside — or a single PR if the team prefers, given the
feature is pre-GA.

---

## 7. Compatibility & rolling upgrade

### 7.1 Mixed-version cluster matrix

**Pre-GA note.** The entire cross-partition message-start-by-businessId feature (this request
record, its intents, and appliers) is **unreleased**. No released broker emits
`MESSAGE_START_PROCESS_INSTANCE_REQUEST` at all, let alone one without `messageTtl`, so the
`-1`/"unknown" sentinel and dedicated fail-open branch originally sketched here are unnecessary.
The field defaults to `0` (Section 6.1); because the guard rejects only on `messageTtl > 0`, an
absent field falls through identically to a genuine TTL=0 message — the safe-for-liveness
behavior. The matrix below therefore reduces to "does `P_B` run the guard?".

| `P_K` version | `P_B` version | Behavior                                                                                                                                                                                                                                |
|---------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| new           | new           | Guard active. A request with `messageTtl > 0` past its deadline gets `REJECT_EXPIRED`; the `EXPIRED_REJECTED` applier backs the ask off. `messageTtl == 0` (explicit or defaulted-absent) falls through.                                |
| old           | new           | Guard active (absent `messageTtl` defaults to `0` → falls through, safe-for-liveness). `P_B` would only send `REJECT_EXPIRED` for `messageTtl > 0`, which an old `P_K` never emits — so an old `P_K` never receives the unknown intent. |
| any           | old           | `P_B` predates the guard → today's behavior (dedup + grace, if configured). No regression.                                                                                                                                              |

Because the field defaults to `0` and the guard is `messageTtl > 0`, there is no separate
"missing field" case to reason about — an absent TTL is indistinguishable from, and handled exactly
like, a genuine TTL=0 message. This defaulting has a second benefit: the guard can only
fire for requests that carry `messageTtl > 0`, i.e. requests from a **new** `P_K`, so the new
`REJECT_EXPIRED` command is never sent to a node that cannot process it — the unknown-intent
mixed-version hazard is structurally excluded, not merely unlikely. (And per the pre-GA note,
mid-upgrade mixed versions of this unreleased protocol are not a supported scenario anyway.)

### 7.2 Replay / reprocessing

- The guard reads `clock.millis()` inside command processing — same determinism class as the
  existing deadline/sweep logic (processing-time decision materialized as a recorded outcome;
  replay applies the *recorded* outcome, never re-evaluates the clock). The reply is dispatched
  through `SubscriptionCommandSender` as a side effect, identically to the three existing
  outcomes; on `P_K` the recorded `EXPIRED_REJECTED` event is what replay applies.
- `EXPIRED_REJECTED` is a feature-new V1 applier with no production stream history → no
  applier-versioning or stream-history migration concerns (same argument as #55198's in-place
  applier updates).

### 7.3 Config deprecation

Per 6.5; document in release notes either way ("property removed; correctness no longer depends on a
tuned window").

---

## 8. Test plan

### 8.1 Unit (

`MessageStartProcessInstanceRequestRequestProcessorTest`, applier tests, scheduler tests)

- Guard truth table: (deadline past / not past) × (TTL `0` / `> 0`) × (dedup hit / miss) — 8
  cases; assert `REJECT_EXPIRED` vs dedup-reply vs live-evaluation, and that **no dedup lookup
  occurs and exactly one reply is sent** on the reject path, with the `REQUESTED` event still
  journaled. (No `-1` sentinel case: the field defaults to `0`, which the `TTL == 0` rows already
  cover — see the pre-GA note in §7.1.)
- Boundary: `messageDeadline == now` (define and pin `<=` vs `<`).
- `EXPIRED_REJECTED` applier: calls `backOff()` (increments persisted `rejectionCount`, does
  **not** remove the ask, does not touch transient send-tracking — matching the two existing
  rejection appliers); no-op when the ask is absent; golden file.
- Scheduler: an `EXPIRED_REJECTED`-backed-off ask retries at the grown interval (extends
  `PendingMessageStartAskCheckSchedulerTest`); re-dispatch carries original TTL and original
  deadline.
- Guard fires with `businessIdUniquenessEnabled = false` (extends
  `MessageStartProcessInstanceCrossPartitionUniquenessDisabledTest`).

### 8.2 Multi-partition integration

1. **Original race** (acceptance test from #55476): retry queued just before deadline; holder
   completes; sweep removes dedup; retry arrives post-deadline → `REJECT_EXPIRED`, exactly one PI
   ever exists on `P_B`, no state leak.
2. **Reordered first-arrival race (new — absent from #55476's criteria):** delay `F` in transport;
   let a retry start the PI; pass deadline; complete holder; sweep; deliver `F` →
   `REJECT_EXPIRED`, exactly one PI. *This is the scenario that distinguishes this design from
   `isRetry` and must be pinned.*
3. **TTL=0 first-arrival** still activates cross-partition (regression pin for the carve-out).
4. **Skew simulation** (advance `P_B`'s actor clock relative to `P_K`): assert the failure mode is
   *only* "expired unstarted" (liveness), never a duplicate (safety), on both skew signs. With a
   fast `P_B` clock, additionally assert the damping property: each `EXPIRED_REJECTED` increments
   `rejectionCount` so retry cadence grows exponentially, the ask survives (single-retry-owner
   intact, no re-drive ping-pong) until `P_K`'s own message expiry cleans it (§6.3).
5. **Late `REJECT_EXPIRED` tolerance:** the command arriving on `P_K` after ask/message are gone
   is a clean no-op (§12.3).
6. Re-run/adjust `MessageStartProcessInstanceNearDeadlineRetryTest` and
   `MessageStartProcessInstanceDedupBehaviorTest` for the post-grace world.

### 8.3 Chaos/e2e (nice-to-have)

- Existing multi-partition chaos suite: inject inter-partition delivery delay > former grace during
  message-start load with shared Business IDs; invariant-check: never two PIs holding one
  `(tenantId, businessId, bpmnProcessId)`.

---

## 9. Observability & docs

- **Observability:** primary signal is the record stream itself — `EXPIRED_REJECTED` events on
  `P_K` (exported by default, unlike command rejections; §13.3) and the always-journaled
  `REQUESTED` events on `P_B`. An optional broker counter for expiry rejections can supplement
  this; a spike is a network-health signal, not an error.
- **ADR 0002 amendment:** record (a) the guard, (b) the reordered-first-arrival analysis and why
  `isRetry` was rejected, (c) the single-clock safety principle (Section 3.3), (d) the TTL=0
  exactness argument, (e) grace removal rationale, (f) the accepted boundary liveness trade, (g)
  the outcome-plumbing decision — `REJECT_EXPIRED` / `EXPIRED_REJECTED` with `backOff()`
  semantics, and why the local-command-rejection alternative was rejected on code review (§6.3,
  §13): silent outcomes starve the back-off, break the one-reply-per-REQUEST protocol shape, and
  produce audit records that default exporters do not index.
- **Code comments** at the guard and the TTL=0 carve-out; user docs updated if the grace property
  was ever publicly documented.

---

## 10. Acceptance criteria

- [ ] Task 0 findings recorded (§12); code-review findings recorded (§13); schema path (6.1)
  executed per §12.1.
- [ ] Guard implemented on `P_B` after the `REQUESTED` append; expired TTL>0 requests
  (first-arrival **and** retry) receive `REJECT_EXPIRED` — no dedup lookup, no activation.
- [ ] TTL=0 cross-partition first-arrival activation preserved (test-pinned).
- [ ] `REJECT_EXPIRED((short) 10, false)` / `EXPIRED_REJECTED((short) 11, true)` added per §12.4;
  sender method, `P_K` processor branch, `backOff()` V1 applier (no-op when ask absent) + golden
  file.
- [ ] Reordered-first-arrival integration test (8.2 #2) passes — no duplicate under any delivery
  order/delay.
- [ ] Skew tests show liveness-only degradation, never duplication; exponentially damped retries
  under a fast `P_B` clock (rejectionCount grows, ask kept), ask removed only at `P_K` expiry.
- [ ] `messageStartAskRetryGrace` removed (or deprecated per policy); sweep/lookup thresholds
  reverted to `now`.
- [ ] Rolling-upgrade behavior per §7.1 verified (absent field defaults to `0` → falls through;
  `REJECT_EXPIRED` structurally never sent to an old `P_K`).
- [ ] ADR 0002 amended (including the §13 decision record); release notes drafted.

---

## 11. Risks & open questions

| # | Item                                                                                                                                                                              | Type                    | Mitigation / owner call                                                                                                                                                                                                     |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Record may not carry TTL/publish-time → schema change needed after all                                                                                                            | **Resolved (§12.1)**    | Confirmed: schema change required and scoped in 6.1 (record + `MessageData` + persisted ask state)                                                                                                                          |
| 2 | Near-deadline messages on skewed clusters expire unstarted more often than under grace                                                                                            | Accepted trade          | Document; metric makes it visible; correct direction for a uniqueness feature                                                                                                                                               |
| 3 | Is there any consumer of the documented behavior "first-arrival of a *short-but-nonzero* TTL message activates past deadline"? If ADR 0002 promises this, the TTL gate changes it | **Resolved (§12.4)**    | ADR 0002 (D1) promises retry "until it starts or its TTL expires" — no past-deadline activation promise for TTL>0; no forced amendment                                                                                      |
| 4 | Fast `P_B` clock: retries during the skew window are repeatedly `REJECT_EXPIRED`-ed until `P_K`'s own expiry                                                                      | Accepted trade          | Damped by design: each `EXPIRED_REJECTED` advances `rejectionCount`, so the scheduler backs off exponentially; ask kept (no re-drive ping-pong), removed only at `P_K` expiry. Pinned by the skew integration test (8.2 #4) |
| 5 | Post-deadline ask lingers until the interval-driven message TTL checker expires the buffered message (§13.1); during that lag, backed-off retries continue                        | Accepted trade / verify | Bounded by checker interval × exponential damping — a handful of cheaply-rejected commands per expired ask, even in mass-expiry incidents; confirm checker interval config in the integration tests                         |
| 6 | Boundary semantics `<=` vs `<` at exact deadline                                                                                                                                  | Nit                     | Pick `<=` (matches reviewer suggestion; note `lookupValidDedupHit` uses `<=` on the same value), pin with a test                                                                                                            |

---

## 12. Task 0 findings (verified against working tree)

Investigated the precondition questions from Section 4 against the current engine
code. **Conclusion: the schema path (Section 6.1) must be executed** — the request
record carries neither TTL nor publish timestamp.

### 12.1 — 0(a): does the wire already carry TTL or publish timestamp? → **No; schema change

required**

`MessageStartProcessInstanceRequestRecord` (and its `MessageStartProcessInstanceRequestRecordValue`
interface) carries `messageDeadline` (`= publishTime + ttl`) **only** — no `messageTtl`,
no `publishTimestamp`. The deadline alone cannot distinguish a `TTL == 0` message from
a `TTL > 0` one (that requires the publish time, which is not on the wire). Therefore,
**Section 6.1 is executed, not skipped**: add a `messageTtl` field.

TTL is readily available at every dispatch site, so populating the field is cheap:

- **First dispatch** — `MessageCorrelateBehavior#dispatchCrossPartitionStartProcessInstanceAsk`
  reads from the `MessageCorrelateBehavior.MessageData` record, which today carries
  `messageDeadline` but **not** TTL. `MessageData` must gain a `messageTtl` component,
  populated at its three construction sites:
  - `MessagePublishProcessor#createMessageData` → `messageRecord.getTimeToLive()` (directly
    available).
  - `BpmnBufferedMessageStartEventBehavior#triggerCorrelation` →
    `storedMessage.getMessage().getTimeToLive()`
    (buffered messages only exist for `TTL > 0`, since `TTL == 0` never gets buffered).
  - `MessageCorrelationCorrelateProcessor#createMessageData` → correlate commands carry
    no TTL and already pass `messageDeadline = -1` (immediate expiry). Set `messageTtl = 0`
    here so the guard falls through to live evaluation (preserves today's create-then-expire
    behavior).
- **Re-dispatch** — `PendingMessageStartAskCheckScheduler#sendAsk` rebuilds the request
  from the **persisted** `MessageStartProcessInstanceAsk`. That state entry stores
  `messageDeadline` but not TTL, so **`MessageStartProcessInstanceAsk` (and its DB
  schema) must also gain `messageTtl`**, set by the `REQUESTED` applier and re-emitted
  verbatim on every retry (like `messageDeadline` is today).

### 12.2 — 0(b): is a `TTL == 0` retry reachable? → **No (unreachable)**

In `MessagePublishProcessor#handleNewMessage` the cross-partition ask is dispatched by
`correlateToMessageStartEvents` (writing `REQUESTED`, whose applier persists the pending
ask) **before** the `TTL <= 0` branch appends `MessageIntent.EXPIRED`. The
`MessageExpiredV2Applier` then calls `askState.removeAllByMessageKey(key)`, removing the
just-written pending ask in the same processing cycle. The retry scheduler therefore
never re-dispatches a `TTL == 0` ask. The **"TTL == 0 / retry" row in the Section 3.1
decision table is theoretical**; the design is safe either way, but the ADR should state
the fact.

### 12.3 — 0(c): is a late `REJECT_EXPIRED` command tolerated on `P_K`? → **Yes (clean no-op)**

*(Note the adopted applier calls `backOff()`, not `remove()`, which only strengthens the
tolerance argument below.)*

The `backOff` mutation the `EXPIRED_REJECTED` applier reuses already tolerates a missing entry
(explicit `ask == null → return`, as the two existing rejection appliers do). For completeness,
`askState.remove(messageKey, processDefinitionKey)` is likewise idempotent
(`columnFamily.deleteIfExists(...)` + transient-state remove). So a `REJECT_EXPIRED` command
arriving after the ask and buffered message are gone is a harmless no-op, exactly like a late
`START`.

### 12.4 — Extra findings (adjust plan)

- **No "reply intent" concept exists.** All intents live in
  `MessageStartProcessInstanceRequestIntent`,
  which follows a paired command/event convention (`REJECT_UNIQUENESS`/`UNIQUENESS_REJECTED`,
  etc.): `P_B` sends the command to `P_K`, whose processor writes the corresponding event. The
  expiry pair is added accordingly (§6.3):
  **`REJECT_EXPIRED((short) 10, false)`** and **`EXPIRED_REJECTED((short) 11, true)`**
  (next free ordinals after `EXPIRED_DEDUP_DELETED = 9`), extending `from(short)`.
- **Risk #3 resolved.** ADR 0002 (`zeebe/docs/adr/0002-810-message-start-rejection-retry.md`)
  frames the contract as "a rejected message-start is retried until it starts **or its
  TTL expires**" (D1). It does **not** promise that a past-deadline first-arrival of a
  short-but-nonzero-TTL message must activate. The TTL gate therefore changes nothing
  ADR 0002 promises — no amendment forced by this, only the additive documentation in
  Section 9.
- **Grace config surface (for Section 6.5)** confirmed in three places:
  `EngineConfiguration`, `zeebe/broker/.../engine/ProcessInstanceCreationCfg`, and
  `configuration/.../ProcessInstanceCreation` (all keyed on
  `DEFAULT_MESSAGE_START_ASK_RETRY_GRACE`), plus the `retryGrace` field and
  `lookupValidDedupHit` threshold in
  `MessageStartProcessInstanceRequestRequestProcessor`.

---

## 13. Code-review findings — outcome plumbing (verified against `11bdf26`)

A second review pass against the shipped code settled the outcome-plumbing question. An
alternative considered during design — writing a **local command rejection** on `P_B`
with no reply to `P_K` (§6.3) — was rejected on three verified facts, each traceable to
a specific site:

### 13.1 — The scheduler has no deadline check, and back-off is fed only by rejection replies

`PendingMessageStartAskCheckScheduler#run` re-sends every pending ask where
`lastSentTime + retryIntervalMillis(rejectionCount) <= now`. There is **no**
`messageDeadline <= now → skip` branch; the class Javadoc's "no retry is emitted [past
expiry]" holds only *after* the pending ask has actually been cleared — which the
`MessageExpiredApplier`-driven cleanup does, on the cadence of the interval-driven
message TTL checker, not at the deadline instant. The back-off exponent grows only via
the persisted `rejectionCount`, which only rejection-reply appliers advance.

**Consequence for a silent outcome:** a post-deadline ask retries at the **undamped
base interval** until the TTL checker fires — not the "skew-only, 0–2 requests" window
the local-rejection alternative assumed — and is indistinguishable on `P_K` from
message loss, repurposing the
at-least-once recovery path as an intentional steady state. The reply is the input the
existing back-off mechanism was built to consume.

### 13.2 — The protocol shape is "every REQUEST → exactly one reply command", journaled via events

`MessageStartProcessInstanceRequestRequestProcessor#processRecord` **unconditionally**
appends the `REQUESTED` follow-up event before evaluating anything, and every outcome
path terminates in exactly one `commandSender.send...` reply
(`sendStartProcessInstanceStarted` / `...NoSubscriptionRejected` /
`...UniquenessRejected`). The processor holds no `rejectionWriter`; nothing in this
protocol produces a silent outcome or a command rejection. A rejection-based guard
would have to either skip the `REQUESTED` journaling or incoherently write both an
acceptance event and a rejection for the same command.

**Consequence:** the expiry outcome should be the protocol's fourth reply, not its
first exception. The pair is the third instance of an existing pattern (two exemplars
to copy for sender, `P_K` processor branch, applier, golden, tests); the local
rejection would have been the first instance of a new one. In an event-sourced
request/reply protocol, a deliberately silent outcome is the more expensive kind of
novelty.

### 13.3 — Command rejections are not exported by default; the protocol's audit trail is events

The default ES/OS exporter configuration indexes events, not command rejections, so
the alternative's "auditable via the rejection record" claim was true of the raw stream
and false of
anything operators actually consult (Operate, exported indices) during an incident. The
exported, replayable audit records for this design are the unconditional `REQUESTED`
event on `P_B` and the new `EXPIRED_REJECTED` event on `P_K`.

### 13.4 — Resolution: `backOff()` dissolves the applier "trilemma"

The pair was initially resisted because every applier semantics seemed wrong under a fast
`P_B` clock: `remove()` risks the ask/re-drive ping-pong, force-expiry violates clock
authority, and `backOff()` was dismissed as "retrying a terminally expired request,
pointless." That dismissal evaluated `backOff()` against the wrong goal ("stop retries
entirely") instead of the right one ("damp retries while preserving invariants"). With
`backOff()`:

- the ask is kept → `hasLivePendingAsk` stays true → single-retry-owner invariant
  intact, no ping-pong;
- removal stays exclusively with `P_K`'s message expiry → clock authority intact —
  expiry's terminality is enforced by the message-expiry applier, where it belongs, not
  by the reply;
- retries damp exponentially (1 reject → 2× → 4× → …) until the TTL checker clears the
  ask — precisely the "probe rarely while blocked" behavior the back-off was built for;
- the symmetry with `REJECT_UNIQUENESS` / `REJECT_NO_SUBSCRIPTION` is real, not false:
  the correct response to all three is *keep the ask, count the rejection, back off,
  let `P_K`'s lifecycle decide the end state*.

Incidental confirmation from the same review: the processor Javadoc states the dedup
entry's `deletionDeadline` is taken directly from the request's `messageDeadline`
(`= publishTime + ttl` on `P_K`), corroborating §12.1, and `lookupValidDedupHit`
compares that same value against `P_B`'s clock — the concrete instantiation of the
single-clock property in §3.3.
