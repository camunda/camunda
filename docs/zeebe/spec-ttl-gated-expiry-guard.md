# Spec: Deterministic TTL-Gated Expiry Guard for Cross-Partition Message-Start Requests

|                         |                                                                                                                                                                                                                                                    |
|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Status**              | Draft — for team review                                                                                                                                                                                                                            |
| **Supersedes / amends** | Proposed solution in [#55476](https://github.com/camunda/camunda/issues/55476) (`isRetry` flag approach)                                                                                                                                           |
| **Related**             | [#51689](https://github.com/camunda/camunda/issues/51689) (parent feature), [#54108](https://github.com/camunda/camunda/pull/54108), [#55198](https://github.com/camunda/camunda/pull/55198) (grace-period mitigation, merged `11bdf26`), ADR 0002 |
| **Component**           | `component/zeebe` — engine, message correlation                                                                                                                                                                                                    |
| **Author**              | Mustafa Dagher                                                                                                                                                                                                                                     |

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
config surface introduced in #55198.

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

In `MessageStartProcessInstanceRequestRequestProcessor#processRecord`, before the dedup lookup:

```
if (request.messageDeadline <= clock.millis() && request.messageTtl > 0) {
    // Deterministically expired: never create, never live-evaluate.
    reject with MessageStartProcessInstanceRequestIntent.EXPIRED_REJECTED (see §6.3)
    return;
}
// TTL == 0, or deadline not yet passed: existing flow unchanged
// (dedup lookup → live evaluation → START / UNIQUENESS_REJECTED / NO_SUBSCRIPTION_REJECTED)
```

Decision table:

| `messageDeadline <= now` (P_B clock) | `messageTtl` | Attempt                              | Outcome                                                                                   |
|--------------------------------------|--------------|--------------------------------------|-------------------------------------------------------------------------------------------|
| no                                   | any          | any                                  | **Unchanged** — dedup lookup, then live evaluation                                        |
| yes                                  | `> 0`        | first-arrival **or** retry           | **`EXPIRED_REJECTED`** — no dedup lookup, no activation                                   |
| yes                                  | `== 0`       | first-arrival or (theoretical) retry | **Unchanged** — dedup lookup, then live evaluation; preserves documented TTL=0 activation |

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
`EXPIRED_REJECTED` — is moot because `P_K` discards either reply (the buffered message is already
gone). Decision: **remove** `messageStartAskRetryGrace` and revert the sweep/lookup thresholds to
`now` (Section 6.5), following the deprecation policy in Section 7.3.

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
- **(c)** Confirm `MessageStartProcessInstanceRequestStartProcessor` (and rejection appliers)
  tolerate an `EXPIRED_REJECTED` reply arriving after the ask/message are gone (expected: no-op,
  mirroring the existing tolerated late `START`).

Deliverable: short findings comment on the issue; flips Section 6.1 between "skip" and "execute".

---

## 5. Goals / non-goals

**Goals**

1. Zero duplicate-PI windows in the cross-partition handshake, for any delay magnitude and any
   `P_K`/`P_B` clock skew.
2. Preserve documented TTL=0 cross-partition first-arrival activation.
3. Reduce mechanism: remove the grace config knob and its threshold plumbing.
4. Auditable rejection: an expired request leaves a record on `P_B`'s stream.
5. Rolling-upgrade safe.

**Non-goals**

- Changing same-partition Business-ID uniqueness or the completion re-drive hooks from #55198.
- Changing retry/back-off cadence on `P_K` (rejection-count back-off stays as shipped).
- Guaranteeing that near-deadline messages *start* under extreme skew (bounded liveness loss at the
  boundary is accepted and documented).

---

## 6. Implementation plan

### 6.1 Wire/schema (contingent on Task 0)

Only if the record carries neither TTL nor publish timestamp:

- Add `messageTtl` (long, millis) to `MessageStartProcessInstanceRequestRecord` — carry the
  *semantic* fact, not an attempt ordinal. Prefer this over `isRetry`/`attemptCount` for the guard;
  `attemptCount` may still be added independently later for observability, but it is not
  load-bearing here.
- Default value `-1` (= "unknown / sent by an old node"). See Section 7 for how the guard treats
  `-1`.
- Set at both dispatch sites from the buffered message's stored TTL:
  - `MessageCorrelateBehavior#dispatchCrossPartitionStartProcessInstanceAsk` (first dispatch)
  - `PendingMessageStartAskCheckScheduler#sendAsk` (re-dispatch; note retries re-emit the original
    `messageDeadline` — they must equally re-emit the original TTL)
- Update SBE/serialization, protocol version notes, and record printers/asserts.

### 6.2 Target processor guard (`P_B`)

`MessageStartProcessInstanceRequestRequestProcessor#processRecord`:

- Insert the guard of Section 3.1 **before** `lookupValidDedupHit` and before the
  `businessIdUniquenessEnabled` gate — the guard is about message lifetime, not uniqueness, so it
  must fire even when uniqueness is disabled (routing is flag-independent since #55198; an expired
  request must not create a PI on `P_B` in either mode).
- Simplify `lookupValidDedupHit` back to `deletionDeadline <= now` once 6.5 lands.

### 6.3 `EXPIRED_REJECTED` reply intent

Prefer an explicit intent over a silent drop:

- **Why:** (a) auditability — "we deliberately refused this expired request" must be reconstructible
  from `P_B`'s stream during incident analysis; (b) symmetry with `UNIQUENESS_REJECTED` /
  `NO_SUBSCRIPTION_REJECTED`; (c) deterministic pending-ask cleanup on `P_K` when the ask still
  exists (rare skew case where `P_B` rejects while `P_K`'s message hasn't expired yet), instead of
  waiting for expiry cleanup.
- Add `MessageStartProcessInstanceReplyIntent.EXPIRED_REJECTED` (naming aligned with existing reply
  intents), command sender method (`sendStartProcessInstanceExpiredRejected`), and a V1 applier.
- **Applier semantics — the one deliberate asymmetry:** unlike the other two rejections (which call
  `backOff()` and keep the ask alive), `EXPIRED_REJECTED` **removes** the pending ask. Retrying an
  expired request is pointless by definition; keeping it would re-probe `P_B` until message-expiry
  cleanup races it. If the ask/message is already gone, the applier is a no-op (verified in Task 0(
  c)).
- Golden files for the new applier.

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
  re-replies cached key" pin is replaced by "late retry is `EXPIRED_REJECTED`".

### 6.6 Suggested commit/PR slicing

Mirroring the incremental style of #55198 (each commit green):

1. Task 0 findings (issue comment / ADR note, no code).
2. *(contingent)* Schema: add `messageTtl` to the request record + serialization + defaults.
3. Dispatch sites populate TTL (first dispatch + re-dispatch).
4. New reply intent `EXPIRED_REJECTED` + applier (ask removal) + golden files + command sender.
5. The guard in the request processor + unit tests.
6. Grace removal (config, sweep thresholds, lookup simplification) + test updates.
7. Integration tests (Section 8.2) — including the reordered-first-arrival scenario.
8. ADR 0002 amendment + docs (Section 9).

Steps 2–5 in one PR, 6 in a second, 7–8 alongside — or a single PR if the team prefers, given the
feature is pre-GA.

---

## 7. Compatibility & rolling upgrade

### 7.1 Mixed-version cluster matrix

| `P_K` version | `P_B` version | Behavior                                                                                                                                                                                                                                                                                                                                                                                                      |
|---------------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| new           | new           | Full guard active.                                                                                                                                                                                                                                                                                                                                                                                            |
| new           | old           | Old `P_B` ignores the extra field; behaves as today (grace window, if still configured, keeps mitigating). **No regression.**                                                                                                                                                                                                                                                                                 |
| old           | new           | Request lacks TTL (`-1` sentinel). Guard must **fail open**: treat `messageTtl == -1` as "do not expiry-reject" and fall through to today's flow. Rationale: fail-closed would break TTL=0 first-arrivals from old nodes — an *observable documented behavior* — to close a window that the (still-present-during-upgrade) grace already mitigates. The window is fully closed once all brokers are upgraded. |
| old           | old           | Status quo.                                                                                                                                                                                                                                                                                                                                                                                                   |

If Task 0 finds TTL/publish-timestamp already on the wire, the `old P_K` row collapses into "full
guard active" and this section reduces to a note.

### 7.2 Replay / reprocessing

- The guard reads `clock.millis()` inside command processing — same determinism class as the
  existing deadline/sweep logic (processing-time decision materialized as a follow-up event/reply;
  replay applies the *recorded* outcome, never re-evaluates the clock). Confirm the reject path
  writes its outcome through `stateWriter`/side-effect conventions identically to the existing
  rejection paths.
- New applier is V1, feature-new intent → no stream-history migration concerns (same argument as
  #55198's in-place applier updates).

### 7.3 Config deprecation

Per 6.5; document in release notes either way ("property removed; correctness no longer depends on a
tuned window").

---

## 8. Test plan

### 8.1 Unit (
`MessageStartProcessInstanceRequestRequestProcessorTest`, applier tests, scheduler tests)

- Guard truth table: (deadline past / not past) × (TTL 0 / >0 / `-1` sentinel) × (dedup hit /
  miss) — 12 cases; assert reject vs dedup-reply vs live-evaluation, and that **no dedup lookup
  occurs** on the reject path.
- Boundary: `messageDeadline == now` (define and pin `<=` vs `<`).
- `EXPIRED_REJECTED` applier: removes pending ask; no-op when ask absent; golden file.
- Re-dispatch carries original TTL and original deadline (
  `PendingMessageStartAskCheckSchedulerTest`).
- Guard fires with `businessIdUniquenessEnabled = false` (extends
  `MessageStartProcessInstanceCrossPartitionUniquenessDisabledTest`).

### 8.2 Multi-partition integration

1. **Original race** (acceptance test from #55476): retry queued just before deadline; holder
   completes; sweep removes dedup; retry arrives post-deadline → `EXPIRED_REJECTED`, exactly one PI
   ever exists on `P_B`, no state leak.
2. **Reordered first-arrival race (new — absent from #55476's criteria):** delay `F` in transport;
   let a retry start the PI; pass deadline; complete holder; sweep; deliver `F` →
   `EXPIRED_REJECTED`, exactly one PI. *This is the scenario that distinguishes this design
   from `isRetry` and must be pinned.*
3. **TTL=0 first-arrival** still activates cross-partition (regression pin for the carve-out).
4. **Skew simulation** (advance `P_B`'s actor clock relative to `P_K`): assert the failure mode is
   *only* "expired unstarted" (liveness), never a duplicate (safety), on both skew signs.
5. **Late reply tolerance:** `EXPIRED_REJECTED` arriving on `P_K` after message expiry is a clean
   no-op.
6. **Upgrade matrix** (if 6.1 executed): old-`P_K`/new-`P_B` TTL=0 first-arrival activates (
   fail-open pin).
7. Re-run/adjust `MessageStartProcessInstanceNearDeadlineRetryTest` and
   `MessageStartProcessInstanceDedupBehaviorTest` for the post-grace world.

### 8.3 Chaos/e2e (nice-to-have)

- Existing multi-partition chaos suite: inject inter-partition delivery delay > former grace during
  message-start load with shared Business IDs; invariant-check: never two PIs holding one
  `(tenantId, businessId, bpmnProcessId)`.

---

## 9. Observability & docs

- **Metric:** counter for expiry-rejected requests (labels: partition), so operators can see the
  guard firing during incidents; a spike is a network-health signal, not an error.
- **ADR 0002 amendment:** record (a) the guard, (b) the reordered-first-arrival analysis and why
  `isRetry` was rejected, (c) the single-clock safety principle (Section 3.3), (d) the TTL=0
  exactness argument, (e) grace removal rationale, (f) the accepted boundary liveness trade.
- **Code comments** at the guard and the TTL=0 carve-out; user docs updated if the grace property
  was ever publicly documented.

---

## 10. Acceptance criteria

- [ ] Task 0 findings recorded; schema path (6.1) confirmed executed or skipped.
- [ ] Guard implemented on `P_B`; expired TTL>0 requests (first-arrival **and** retry) are
  `EXPIRED_REJECTED` without dedup lookup or activation.
- [ ] TTL=0 cross-partition first-arrival activation preserved (test-pinned).
- [ ] `EXPIRED_REJECTED` intent + applier (ask removal, no-op tolerance) + golden files.
- [ ] Reordered-first-arrival integration test (8.2 #2) passes — no duplicate under any delivery
  order/delay.
- [ ] Skew tests show liveness-only degradation, never duplication.
- [ ] `messageStartAskRetryGrace` removed (or deprecated per policy); sweep/lookup thresholds
  reverted to `now`.
- [ ] Rolling-upgrade behavior per §7.1 verified (fail-open on `-1` if applicable).
- [ ] ADR 0002 amended; metric added; release notes drafted.

---

## 11. Risks & open questions

| # | Item                                                                                                                                                                              | Type           | Mitigation / owner call                                                                                                                                         |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Record may not carry TTL/publish-time → schema change needed after all                                                                                                            | Risk           | Task 0 first; schema work is scoped in 6.1 either way                                                                                                           |
| 2 | Near-deadline messages on skewed clusters expire unstarted more often than under grace                                                                                            | Accepted trade | Document; metric makes it visible; correct direction for a uniqueness feature                                                                                   |
| 3 | Is there any consumer of the documented behavior "first-arrival of a *short-but-nonzero* TTL message activates past deadline"? If ADR 0002 promises this, the TTL gate changes it | Open question  | Check ADR 0002 wording in Task 0; if promised, the promise itself is unsound (it is the duplicate window) and the ADR should be amended, not the design         |
| 4 | `EXPIRED_REJECTED` removing the ask while `P_K`'s message is still alive (P_B clock fast) — subsequent buffer scans could theoretically re-ask                                    | Design check   | Verify single-retry-owner guard (`hasLivePendingAsk`) interaction; a re-ask would just be expiry-rejected again — safe, but confirm no ask/reply ping-pong loop |
| 5 | Boundary semantics `<=` vs `<` at exact deadline                                                                                                                                  | Nit            | Pick `<=` (matches reviewer suggestion), pin with a test                                                                                                        |
