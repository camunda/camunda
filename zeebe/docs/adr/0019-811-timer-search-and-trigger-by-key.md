# Timers: searchable as wait states, fired on demand by timer key

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how a recording tool fires a waiting BPMN timer on demand without moving the
cluster clock — how timers are discovered and how one is addressed for triggering.

**Audience**: Zeebe engine engineers working on timers, and AI agents reasoning about timer
scheduling or the CPT recording path.

## Context

A recorder drives one instance on a shared cluster
([ADR 0016](0016-811-per-instance-recording-switches.md)). A BPMN timer — intermediate catch,
boundary, or event sub-process start — fires on its own due date, seconds or days later. On a cluster
whose clock the recorder does not own it cannot shorten the wait, and pinning the shared clock disturbs
every other instance. The recorder needs to fire the one timer it waits on, now, leaving every other
timer alone.

## Decision

**D1. Every waiting timer is searchable.** `POST /v2/timers/search` returns the timers process
instances wait on, in state `CREATED`, filterable by process instance, element instance, and element
id, with each timer's key and due date. Like every `/search`, it is eventually consistent.

**D2. A timer is fired on demand by its own key.** `POST /v2/timers/{timerKey}/trigger` fires the timer
as if its due date had arrived, without changing the cluster clock; a cycle timer schedules its next
repetition as a new timer with a new key. A client-command path resolves the stored timer from a
`timerKey -> elementInstanceKey` index, authorizes the caller, and then runs the same processor as a
scheduler-issued trigger.

**D3. The trigger is authorized like a process-instance command.** It requires
`UPDATE_PROCESS_INSTANCE` on the process definition plus a tenant check. A tenant rejection is
`NOT_FOUND`, not `FORBIDDEN`, so the endpoint cannot probe for timer keys in other tenants.

**D4. Timers are not held.** A timer stays in the due-date scheduler and can be fired early by key, so
this is a timer API rather than a per-instance recording switch.

## Alternatives considered

- **Hold an instance's timers out of the scheduler (`holdTimers`) and fire by `(instance,
  elementId)`.** The original design, needed while no API exposed a timer key. It added a `held` flag
  on every timer record, a held-timer index, suspend/resume reconciliation, and a `409` on element-id
  collisions (multi-instance, loops) with no way to disambiguate. A timer search makes the key directly
  available, so the key-addressed trigger replaces it.
- **Pin the cluster clock, or suspend the instance.** The clock freezes everyone's timers; suspension
  releases all timers at once on resume and blocks job completion.
- **Address the trigger by `(elementInstanceKey, timerKey)`.** The key alone routes to the owning
  partition and resolves the timer.

## Consequences

- **A timer can still fire on its own.** A short timer can reach its due date before the recorder
  triggers it; the trigger then returns `NOT_FOUND`. Step-by-step control needs long enough due dates
  or a CPT mode that owns the clock (`MANAGED`/`SHARED`). This is the trade for dropping the hold.
- **Search lags; the trigger does not.** A client (and CPT) polls search until the timer appears, then
  triggers by key.
- **A trigger against a suspended instance is rejected, not buffered.**
- **CPT replays by element id.** It resolves the recorded `elementId` to the first `CREATED` timer of
  the run's instance and triggers that key, as it does for jobs, user tasks, and incidents. Several
  timers on one element are interchangeable for replay; a client that must pick one can use
  `elementInstanceKey` from search.
- **No backfill of the `timerKey -> elementInstanceKey` index.** A timer created before the upgrade
  cannot be triggered by key. Acceptable while alpha; a migration is needed before that changes.
- Search only ever returns `CREATED` timers, although `TimerStateEnum` also lists `TRIGGERED` and
  `CANCELED`.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0016 — Per-instance recording switches](0016-811-per-instance-recording-switches.md)

