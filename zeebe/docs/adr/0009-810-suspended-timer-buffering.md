# Suspended timers: buffer the due trigger instead of rejecting it

**DRI**: Ambrose Tan

**Status**: Accepted (8.10)

**Purpose**: Defines how a timer that comes due while its process instance is suspended is held and
later fired — the `Timer.SUSPENDED`/`Timer.RESUMED` events, the due-date index removal, and the
`onSuspended`/`onResuming` shape of the suspension gate this required.

**Audience**: Zeebe engineers working on timers, the due-date checker, or process instance
suspension, and AI agents reasoning about the suspension gate.

## Context

Firing a timer advances the token, so `Timer.TRIGGER` could not simply be processed while the
instance is suspended. The first implementation rejected it. Rejection is a dead end for timers:

- **The command comes back.** A rejection leaves the timer's due-date index row
  (`TIMER_DUE_DATES`) in place, so `DueDateTimerCheckScheduler` rewrites the same `TRIGGER` on
  every check for as long as the instance stays suspended. The log fills with rejections that
  change nothing.
- **Resume had to re-arm the checker, and that reordered.** To make a missed timer fire at all,
  `COMPLETE_RESUMING` nudged the checker with a `scheduleTimer(-1)` side effect. A side effect runs
  after the batch, so the resulting `TRIGGER` landed *after* the buffered commands drained — a
  timer that came due before a buffered internal command could fire after it, reordering the
  instance's own history relative to how it would have run unsuspended.

The gate itself also could not express what timers needed. `SuspensionAware.suspensionBehavior`
returned a classification, and the events that had to accompany buffering were written elsewhere
(`onBuffer`), while resume ran a separate `void onResume` hook after the gate had already flipped
`BUFFER` to `PROCESS`. Writing `Timer.SUSPENDED` at buffer time was easy to miss, and the resume
hook fired for commands that had never been buffered.

## Decision

**D1. A due `Timer.TRIGGER` on a suspended instance is buffered, not rejected, and
`Timer.SUSPENDED` drops the timer from the due-date index.** `TimerTriggerProcessor.onSuspended`
appends `Timer.SUSPENDED` and returns `BUFFER`. `TimerSuspendedApplier` removes only the due-date
index row and keeps the timer instance, so `Timer.CANCEL` and the eventual fire still find the
timer. The checker no longer sees the timer, so it stops rewriting the command; the original
`TRIGGER` waits in the buffer and drains during `RESUMING`, in the same position it would have had.

Consequences of dropping the index rather than the instance:

- `remove()` deletes the due-date row with `deleteIfExists`, so a suspended timer takes the normal
  removal path. The `TRIGGERED`/`CANCELED` v2 appliers that were added to tolerate a missing
  due-date row were deleted again — the v1 appliers already work with or without it.
- `hasDueDateEntry(elementInstanceKey, timerKey)` encapsulates the "is this timer suspended"
  check on the state interface, reading the stored due date rather than making callers pass one.

**D2. `RESUMING` no longer re-arms the due-date checker.** With the trigger buffered there
is nothing for the checker to find and nothing to re-arm: the drained `TRIGGER` fires inside
the drain chain in order. This removes the side-effect-ordering hazard described in Context.
`Timer.RESUMED` is therefore a no-op applier — it restores no due date. It exists so the lifecycle
is visible in the log and on replay.

**D3. `Timer.RESUMED` is written for every `TRIGGER` the gate lets through while `RESUMING`, not
only for previously buffered ones.** An earlier revision checked if a due-date index was missing so
that a timer that triggered *during* `RESUMING` did not get an unpaired `RESUMED`. That guard was
dropped: distinguishing the two cases costs a state read on every pass-through and buys only strict
event pairing, which nothing consumes (D5). `shouldEmitResumedForFreshTriggerWhileResuming` tests
the chosen behavior.

**D4. The gate exposes two classification callbacks, `onSuspended` and `onResuming`, both returning
`SuspensionAction`.** The gate switches on the marker and calls `onSuspended` while `SUSPENDED`,
`onResuming` while `RESUMING`. Events that accompany buffering belong in `onSuspended`; events that
accompany resume belong in `onResuming`. This replaces the split between a classifier and a
separate `onBuffer`/`onResume` pair, and is what makes the timer's two writes sit next to the
decision that causes them.

- `onResuming` defaults to `PROCESS`, so buffered commands drain without every processor
  overriding it. The default also applies to commands that arrive after the marker changes to
  `RESUMING`; the gate does not distinguish a drained command from a fresh one.
- A processor that rejects while `SUSPENDED` must also return `REJECT` from `onResuming`.
  Rejected commands were never buffered, so they have no work that needs to drain. Letting the
  default `PROCESS` apply would admit external job, user-task, migration, or repeated-suspend
  commands while buffered commands and suspended jobs are still being restored. Depending on how
  far resume had progressed, the same command could then be accepted or rejected by downstream
  state checks. Keeping it at `REJECT` makes the instance unavailable for those operations until
  the suspension marker is removed.
- A mixed processor such as `BpmnStreamProcessor` still rejects only externally issued commands
  and passes internal ones through.
- Renames that follow: `SuspensionCheck` → `SuspensionBehavior` (the gate),
  `SuspensionAware.SuspensionBehavior` → `SuspensionAction` (the enum).
- `Engine` now applies the outcome with an exhaustive switch that names `PROCESS`, so a new
  `SuspensionAction` fails to compile until it is handled, replacing the `IllegalStateException`
  fallback.

**D5. Both events drive engine state through their appliers, but change nothing in secondary
storage.** They are added to `TimerIntent` as events (values 6 and 7), and they are handled at two
distinct stages:

- **Engine state (applied).** `TimerSuspendedApplier` removes the due-date index row (D1);
  `TimerResumedApplier` is deliberately a no-op (D2). Both are registered in `EventAppliers`, so
  replay reconstructs the same state.
- **Secondary storage (not written).** `TimerBasedWaitStateTransformer` decides what a timer record
  does to the wait-state index from `WaitStateConfigs.TIMER_CONFIG`, whose intent lists are
  `CREATED` (add), `MIGRATED` (update), and `TRIGGERED`/`CANCELED` (remove). `SUSPENDED` and
  `RESUMED` are in none of them, so a suspended timer keeps its existing wait-state entry rather
  than being removed and re-added around the suspension.

This is the same position [ADR-0008](0008-810-suspended-job-state.md) D4 takes for
`Job.SUSPENDED`/`Job.RESUMED`: suspension stays visible at process instance level only.

**D6. Start-event timers are unchanged.** They have no process instance, so the gate never resolves
a suspension marker for them.

## Alternatives considered

- **Keep rejecting `TRIGGER` and re-arm the checker on resume.** The shipped-then-replaced
  behavior. Rejected on the three failures in Context: repeated rejections while suspended, a
  post-drain fire that reorders against buffered commands, and a catch-up count that depends on
  checker passes.
- **Restore the due-date index on `Timer.RESUMED` and let the checker fire the timer again.** Would
  make `RESUMED` symmetric with `SUSPENDED`, but puts the fire back on the checker — outside the
  drain chain — reintroducing exactly the ordering problem D2 removes.
- **Remove the whole timer instance on suspend and recreate it on resume.** Rejected: `Timer.CANCEL`
  and element termination need to keep finding the timer while the instance is suspended, and
  recreating it would have to reconstruct the record from the buffered command.
- **Guard `Timer.RESUMED` on a missing due-date index** (see D3). Rejected as a state read per
  pass-through for pairing no consumer needs.
- **Derive `onResuming` from `onSuspended`.** Rejected because `onSuspended` is not a pure
  classification: it may write events such as `Timer.SUSPENDED`, and returning `BUFFER` during
  `RESUMING` would re-buffer drained commands and prevent resume from completing.
- **Default `onResuming` to `REJECT`.** Rejected because most buffered commands must process during
  `RESUMING`. Processors whose commands are rejected instead opt out explicitly; this repetition
  makes the exceptional policy visible at each command processor.

## Consequences

- A repeating timer that missed several cycles while suspended fires **twice** after resume: the
  buffered trigger, plus one overdue reschedule snapped to now — not once per missed cycle. This is
  the user-visible catch-up contract and is asserted on a live broker.
- A duplicate `TRIGGER` that arrives while suspended is also buffered; drain rejects the extra as
  `NOT_FOUND`. Suspension is not deduplicating.
- A timer that becomes due after resume is neither suspended nor resumed — the normal path.
- `hasDueDateEntry` is currently exercised only by tests, since D3 removed its production caller.
  It stays on the state interface as the sanctioned way to ask the question.
- Every processor implementing `SuspensionAware` now has two callbacks to keep consistent; a
  processor that rejects while suspended but forgets `onResuming` would silently start processing
  during resume. Reviewed per processor, not statically enforced.
- No downgrade once a timer is suspended: older brokers do not know `TimerIntent` values 6 and 7.

## Testing

`EngineRule` drives time in-process and cannot race `DueDateTimerCheckScheduler` against a cluster
clock, so the due-date behavior is covered by a MultiDb IT
(`ProcessInstanceSuspendResumeTimerIT`) with a controlled actor clock, asserting the user-visible
outcomes: a timer due while suspended does not fire until resume, a timer due after resume fires
normally, and the repeating-timer catch-up above. Unit coverage sits in `TimerSuspensionGateTest`,
`TimerSuspendedApplierTest`, `TimerInstanceStateTest`, and `SuspensionBehaviorTest`.

## Source

- [#61750](https://github.com/camunda/camunda/issues/61750) — overhaul timer suspension.
- [#62241](https://github.com/camunda/camunda/pull/62241) — implementation summarized by this ADR.
- [ADR-0008](0008-810-suspended-job-state.md) — suspended job state; the export position (D5) and
  the suspend/resume gate this ADR extends.

