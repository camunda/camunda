# Implementation spec: static-membership takeover for consumer groups (task #24, item 2)

Status: ready to implement. Scope: ONLY the static-membership takeover. Item 1 of #24 (debounce
cap) reduces to a verification preamble below — the arm-if-absent semantics already exist. Item 3
(join-as-heartbeat) is explicitly OUT OF SCOPE: it is a protocol milestone scheduled separately.
When this spec conflicts with what you find in the code, STOP and report the conflict instead of
improvising.

## Context you must internalize first

The consumer-group coordinator lives in `event-bridge/event-bridge-consumer-groups`. It is a
REPLICATED state machine: command processors (`processing/`) validate against derived state
(`state/`), append events, and every replica derives identical state from the same event
sequence. Consequences that bind every change here:

- A processor may use ONLY the command (including its replicated timestamp) and current state —
  no wall clock, no randomness, nothing replica-local.
- State changes happen ONLY by applying appended events; the processor never mutates state
  directly.
- Read `processing/JoinGroupProcessor.java`, `processing/CoordinationValidator.java` (the
  `UNRELEASED_INSTANCE_ID` rejection at ~line 96 is the behavior this spec REPLACES),
  `processing/RebalanceDebounce.java`, `processing/SessionEvictionTask.java`,
  `session/GroupLiveness.java` (the #23 never-heartbeated grace fix — do not regress it),
  `state/` (MemberSnapshot — note `instanceId` is already carried, currently only used for the
  duplicate rejection), and the existing test styles in
  `src/test/java/.../processing/CoordinatorProcessorTest.java`.

Why this feature matters operationally: today an app restart is leave-or-die + rejoin-as-new →
group epoch bump → full rebalance → partition churn on every deploy. With takeover (Kafka's
static membership semantics), a restart with the same `group.instance.id` slips back into its
old slot with zero rebalance.

## Preamble — verify item 1 is truly done (test-only)

`RebalanceDebounce.dueAt` keeps the existing deadline while the group is already
`PREPARING_REBALANCE` (fixed window from first change). Verify a regression test exists that a
CHURN BURST (join, join, leave, join within the window) does not move the deadline; if missing,
add it to `CoordinatorProcessorTest` (assert the due-ordered index still fires at
firstChange + debounce). No production code change expected here — if you find the semantics are
NOT arm-if-absent, STOP and report.

## The takeover semantics (the contract — invariants, not implementation)

A `JOIN_GROUP` whose `instanceId` matches a LIVE roster member becomes a **takeover** instead of
an `UNRELEASED_INSTANCE_ID` rejection:

```
   BEFORE (today)                          AFTER (this spec)
   join(instanceId=X) while X held  →      join(instanceId=X) while X held  →
   reject UNRELEASED_INSTANCE_ID           the new incarnation REPLACES the old:
   (new joiner fenced; slot freed            new memberId assigned
   only by leave or eviction)                memberEpoch STRICTLY > old incarnation's
                                             targetPartitions inherited VERBATIM
                                             assignmentEpoch unchanged
                                             groupEpoch NOT bumped → NO rebalance
                                             old incarnation FENCED from now on
```

The five invariants, each of which must have a dedicated test:

1. **Epoch monotonicity (the fencing invariant — highest stakes).** The new incarnation's
   memberEpoch is strictly greater than every epoch the old incarnation ever held. Rationale
   outside this module: the analytics serving fence derives `WriteVersion.epoch` from
   memberEpoch, so a takeover must guarantee the zombie's in-flight serving writes lose to the
   successor's. The test must assert strict increase across takeover, and a second takeover of
   the takeover (double restart) must increase again.
2. **No rebalance.** groupEpoch and assignmentEpoch are unchanged by a takeover; the assignor
   must not run; no other member's assignment moves. (A takeover while the group happens to be
   `PREPARING_REBALANCE` inherits the pending state without re-arming the debounce window.)
3. **Old incarnation is dead the moment the takeover event applies.** Its heartbeats and offset
   commits are rejected with the fenced-epoch error from the first post-takeover command on.
   Its session/liveness bookkeeping (including any `unseenSince` grace entry from the #23 fix)
   is replaced by the new incarnation's, freshly seeded — the new member must not inherit the
   old one's heartbeat staleness.
4. **Inheritance is exact.** The new incarnation's target/assigned partitions equal the old
   one's at takeover time — including the empty assignment of a member that joined but was never
   assigned.
5. **Determinism.** The entire decision derives from command + state; replaying the event
   sequence on a fresh replica reconstructs the identical roster (add a replay/reconciliation
   test in the existing state-test style).

Non-takeover paths must stay exactly as they are (tests must pin them): a join with an
instanceId whose holder already left or was evicted is a NORMAL join (epoch bump + rebalance);
a join with a fresh instanceId is untouched; a join with NO instanceId (dynamic member) is
untouched.

## Implementation shape (constraints, with freedom inside them)

- The replicated event must carry everything a replica needs to apply the takeover
  deterministically (old memberId, new memberId, new memberEpoch, the inherited assignment or a
  reference to it). Whether that is a new event type or a flagged variant of the existing
  MEMBER_JOINED event is the implementer's choice — pick the one that keeps state-application
  code simplest, and document the choice in the commit body.
- `CoordinationValidator.validateJoin` stops rejecting the live-duplicate case and instead
  classifies it (normal join vs takeover) for the processor. Keep `UNRELEASED_INSTANCE_ID` for
  any case that remains genuinely invalid (if none remains, remove the code path and say so).
- The join REPLY for a takeover keeps today's shape (`REBALANCE_IN_PROGRESS`, assignment learned
  via heartbeat) — do NOT invent a new response carrying the assignment inline; the client
  already handles the learn-via-heartbeat flow.
- Client (`event-bridge-client`'s group coordinator): verify the existing rejoin/restart path
  sends the configured instanceId and copes with the takeover reply (it should, since the reply
  shape is unchanged). If a client change IS needed, keep it minimal and report it prominently.
- Do not touch `GroupLiveness`'s #23 grace logic except where invariant 3 requires replacing the
  old member's entries; the #23 regression tests must stay green untouched.

## Test matrix (all mandatory)

Processor level (`CoordinatorProcessorTest` style):
1. takeover happy path — new memberId, strictly greater memberEpoch, inherited partitions,
   groupEpoch/assignmentEpoch unchanged, no assignor activation
2. old incarnation's heartbeat after takeover → fenced-epoch rejection
3. old incarnation's offset commit after takeover → fenced-epoch rejection
4. double takeover (restart twice) → epochs strictly increase twice; second old incarnation fenced
5. takeover while PREPARING_REBALANCE → pending state inherited, debounce deadline unmoved
6. takeover of a never-assigned member → empty inheritance, no rebalance
7. instanceId of a LEFT/EVICTED member → normal join (epoch bump + rebalance) — the boundary
   between takeover and fresh join
8. dynamic member (no instanceId) join → unchanged behavior
9. the preamble churn-burst debounce test (if missing)

State/replay level: apply the takeover event sequence on a fresh state → identical roster,
epochs, assignments (determinism, invariant 5). Liveness level: old member's session/grace
entries gone, new member seeded fresh (invariant 3, tied to the #23 machinery).

## Gates

Maven isolation is MANDATORY (other sessions share ~/.m2): `cp -Rc ~/.m2/repository
<worktree>/.m2-lane` before the first build, `-Dmaven.repo.local=<worktree>/.m2-lane` on EVERY
./mvnw invocation, `-T2`. Never commit .m2-lane.

Per commit: module-scoped `license:format spotless:apply` (NOT repo-wide), then
`verify -pl event-bridge/event-bridge-consumer-groups -DskipTests=false -DskipITs -Dquickly -T2`
(plus `event-bridge/event-bridge-client` if touched). Finally full-repo `install -Dquickly -T2`.
Conventional commits, no scopes; suggest: one `test:` commit for the preamble, one `feat:` for
the takeover, split a `refactor:` out if the validator restructuring is substantial.

Repo rules: never inline FQNs; no Kafka/KIP names in comments or javadoc (describe semantics in
our own vocabulary — "static membership takeover", not the KIP number); JUnit 5 + AssertJ +
should… + given/when/then.

## Phase 2 — consumer-client metrics (the deferred phase 4 of the monitoring spec)

After the takeover lands, instrument the client along the SAME pattern this repo's flagship
Java client uses for job workers — read
`clients/java/src/main/java/io/camunda/client/api/worker/JobWorkerMetrics.java` and
`metrics/MicrometerJobWorkerMetricsBuilder.java` first; that is the precedent to copy, not to
improve upon:

1. A dependency-free `ConsumerMetrics` interface in `event-bridge-client`, next to the existing
   `RebalanceListener`, with NO-OP DEFAULTS for every method — the client calls it
   unconditionally and never sees Micrometer:
   - `onRebalance(int revoked, int assigned)` — the owned-partitions change path
     (`GroupCoordinator.applyOwnedPartitions` / `notifyRebalance`, ~lines 548/570)
   - `onHeartbeatFailure()` — the heartbeat error branches (`scheduleSendHeartbeat`
     whenComplete error path ~line 240, `onHeartbeatResponse` error branch ~line 296)
   - `onFencedRejoin()` and `onRejoinRejected()` — the fenced-rejoin path (~lines 312-330);
     count the HTTP-409 rejection separately from a successful fenced rejoin
   - `onEpochChanged(long memberEpoch)` — wherever the volatile `memberEpoch` (~line 77) is
     written (`applyJoinResponse` ~line 171 and the heartbeat/rejoin paths). A CALLBACK, not a
     gauge: gauges are the adapter's business.
2. Thread it through consumer construction (builder/subscribe parameter defaulting to the
   no-op instance) — an ADDITIVE public-API change, same shape as the SDK's
   `JobWorkerBuilderStep3.metrics(...)`.
3. The Micrometer adapter lives in `event-bridge-streaming` (already depends on Micrometer;
   `StreamRuntime.subscribeWithRetry` holds the registry): meters `eb.consumer.rebalances`
   (counter, tag group), `eb.consumer.heartbeat.failures` (counter, tag group),
   `eb.consumer.rejoins.rejected` (counter, tag group), `eb.consumer.assignment.epoch`
   (gauge over an adapter-owned AtomicLong, tag group). Register gauges once per subscription;
   on resubscribe REUSE the same AtomicLong rather than re-registering (Micrometer keeps the
   first registration per id+tags).
4. Tests: the no-op default is used when nothing is configured; each callback fires at its
   seam (SimpleMeterRegistry through the adapter); the epoch gauge tracks a takeover — after a
   takeover the gauge must show the INCREASED epoch (the observable end-to-end proof of
   invariant 1) — and `eb.consumer.rebalances` must NOT increment on a static restart.
5. Update `analytics/docs/monitoring.md` with the four meters and their alert story (pinned
   epoch while rebalances climb = the wedge signature; rejoins.rejected bursts = the 409
   storm observed live on 2026-07-16).

## Out of scope

Join-as-heartbeat (item 3 — separate milestone, gated on a validated smoke run), SERVER-side
coordinator metrics, member capacity/quota changes, any protocol field additions beyond what
the takeover event needs, and rebalance-algorithm changes.
