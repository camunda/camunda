# Suspended timers: buffer the due trigger instead of rejecting it

**DRI**: Ambrose Tan

**Status**: Accepted (8.10)

**Purpose**: Defines how a timer that comes due while its process instance is suspended is held and
later fired — the `Timer.SUSPENDED`/`Timer.RESUMED` events, the due-date index removal, and the
`onSuspended`/`onResuming` shape of the suspension gate this required.

**Audience**: Zeebe engineers working on timers, the due-date checker, or process instance
suspension, and AI agents reasoning about the suspension gate.

## Decision

**D1. A due `Timer.TRIGGER` on a suspended instance is buffered and `Timer.SUSPENDED` drops the
timer from the due-date index.** `TimerTriggerProcessor.onSuspended`
appends `Timer.SUSPENDED` and returns `BUFFER`. `TimerSuspendedApplier` removes only the due-date
index row and keeps the timer instance, so `Timer.CANCEL` and the eventual fire still find the
timer. The checker no longer sees the timer, so it stops rewriting the command; the original
`TRIGGER` waits in the buffer and drains during `RESUMING` in order.

Consequences of dropping the index rather than the instance:

- `remove()` must now delete the due-date row with `deleteIfExists`, so a suspended timer without
  a due-date row can take the normal removal path.

**D2. `Timer.RESUMED` is written for every `TRIGGER` the gate lets through while `RESUMING`, not
only for previously buffered ones.** The resumed applier upserts the due-date entry when the
timer still exists, so an already indexed fresh trigger is unchanged and a duplicate trigger cannot
recreate an index entry after the timer was removed. This side-effect is currently accepted.

**D3. The gate exposes two classification callbacks, `onSuspended` and `onResuming`, both returning
`SuspensionAction`.** The gate checks the marker and calls `onSuspended` while `SUSPENDED` and
`onResuming` while `RESUMING`. Events that accompany buffering belong in `onSuspended`; events that
accompany resume belong in `onResuming`.

- Processors must ensure that the commands returned are compatible between `onSuspended` and
  `onResuming`. For instance, a command that always gets processed on suspend should always get
  processed when resuming.

**D4. Start-event timers are unchanged.** They have no process instance, so the gate never resolves
a suspension marker for them.

## Alternatives considered

- **Keep rejecting `TRIGGER` and re-arm the checker on resume.** Rejected due to two failure
  points: repeated rejections while a timer was suspended, and an out of order timer trigger since
  rescheduling the timer only happens after draining.
- **Derive `onResuming` from `onSuspended`.** Rejected because `onSuspended` is not a pure
  classification: it may write events such as `Timer.SUSPENDED`, and returning `BUFFER` during
  `RESUMING` would re-buffer drained commands and prevent resume from completing.

## Consequences

- A repeating timer that missed several cycles while suspended fires **twice** after resume: the
  buffered trigger, plus one overdue reschedule snapped to now — not once per missed cycle. This is
  the user-visible catch-up contract and is asserted on a live broker. This is illustrated by the
  sequence diagram below.

  ```mermaid
  sequenceDiagram
    autonumber
    participant Clock
    participant DueDateChecker
    participant Processor as TimerTriggerProcessor
    participant Interval as Interval.toEpochMilli
    participant Catch as CatchEventBehavior
    participant State as Timer state

    Note over Clock,State: Cycle R/PT1H. Timer CREATED due at t=1h.
    Clock->>Clock: Instance suspended
    Processor->>State: TIMER.SUSPENDED (BUFFER the TRIGGER)
    Clock->>Clock: Time jumps 1h → 4.5h<br/>(slots 2h, 3h, 4h never scheduled)

    Clock->>Processor: Resume
    Processor->>State: TIMER.RESUMED (restore due-date index)
    Note over Processor: PROCESS buffered TRIGGER (due=1h)

    Processor->>State: TIMER.TRIGGERED (fire 1)
    Processor->>Processor: refreshTimer: start = 1h + 1h = 2h
    Processor->>Catch: subscribeToTimerEvent(refreshed)
    Catch->>Interval: getDueDate(now=4.5h)
    Interval-->>Catch: max(2h, 4.5h) = 4.5h  (snap)
    Catch->>State: TIMER.CREATED due=4.5h
    Catch->>DueDateChecker: scheduleTimer(4.5h)

    DueDateChecker->>Processor: TIMER.TRIGGER (already due)
    Processor->>State: TIMER.TRIGGERED (fire 2, catch-up)
    Processor->>Processor: refreshTimer: start = 4.5h + 1h = 5.5h
    Processor->>Catch: subscribeToTimerEvent(refreshed)
    Catch->>Interval: getDueDate(now≈4.5h)
    Interval-->>Catch: max(5.5h, 4.5h) = 5.5h
    Catch->>State: TIMER.CREATED due=5.5h
    Note over Clock,State: Missed 2h/3h/4h are not replayed.<br/>Next fire is at 5.5h.
  ```
- A duplicate `TRIGGER` that arrives while suspended is also buffered; drain rejects the extra as
  `NOT_FOUND`. Suspension is not deduplicating.
- A timer that becomes due after resume is neither suspended nor resumed — the normal path.
- Every processor implementing `SuspensionAware` must explicitly classify commands for both
  `SUSPENDED` and `RESUMING`. The compiler prevents omissions, while the classifications still need
  to be reviewed together for consistency.
- No downgrade once a timer is suspended: older brokers do not know `TimerIntent` values 6 and 7.

## Testing

The due-date behavior is covered by a MultiDb IT (`ProcessInstanceSuspendResumeTimerIT`)
with a controlled actor clock, asserting the user-visible outcomes: a timer due while suspended does
not fire until resume, a timer due after resume fires normally, and the repeating-timer catch-up
above. Unit test coverage sits in `TimerSuspensionGateTest`, `TimerSuspendedApplierTest`,
`TimerInstanceStateTest`, and `SuspensionBehaviorTest`.

## Source

- [#61750](https://github.com/camunda/camunda/issues/61750) — overhaul timer suspension.
- [#62241](https://github.com/camunda/camunda/pull/62241) — implementation summarized by this ADR.

