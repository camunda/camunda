# Timers: searchable as wait states, fired on demand by timer key

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how a recording tool fires a waiting BPMN timer on demand without moving the
cluster clock — how timers are made discoverable, how one is addressed for triggering, and why by its
own key rather than by element.

**Audience**: Zeebe engine engineers working on timers, and AI agents reasoning about timer
scheduling or the CPT recording path.

## Context

CPT records a test by driving a real instance on a shared cluster and authoring the matching
assertion after each step. A BPMN timer — intermediate catch, boundary, or event sub-process start —
fires on its own due date, seconds or days later. On a `REMOTE` cluster whose clock the recorder does
not own, it cannot shorten the wait reproducibly, and pinning the shared clock would disturb every
other instance. What a recorder needs is to fire the one timer it is waiting on, now, as if its due
date had arrived, leaving every other timer on the cluster alone.

An earlier design ([superseded held-timers approach]) kept an instance's timers out of the due-date
scheduler (`holdTimers`) and fired one by `(processInstanceKey, elementId)`, because no API exposed a
timer key. That coupled "fire on demand" to "never fire on its own", added a per-instance held-timer
index and a `held` flag on every timer record, and rejected `elementId` collisions (multi-instance,
loops) with no way to disambiguate. Introducing a timer search removes the reason element-id
addressing existed, so the whole held-timer machinery is dropped in favour of the two decisions below.

## Decision

**D1. Every waiting timer is exported as a timer wait state and is searchable.** `POST
/v2/timers/search` returns the timers process instances wait on — intermediate catch events, boundary
events, and event sub-process start events — in state `CREATED`, filterable by process instance,
element instance, and element id. This gives a client (and a recorder) the timer's key, due date, and
element without any engine-private addressing. The search is eventually consistent (secondary
storage), like every other `/search`.

**D2. A timer is fired on demand by its own key.** `POST /v2/timers/{timerKey}/trigger` fires the
timer now, as if its due date had arrived, without changing the cluster clock; a cycle timer
reschedules its next repetition as a new timer with a new key. The command reuses the processor that
already fires a single timer by key; a client-command entry path resolves the stored timer from a
`timerKey -> elementInstanceKey` index, hydrates the command from it, authorizes the caller, and from
the resolved key on runs the same activation as a scheduler-issued trigger.

**D3. The trigger is authorized like a process-instance command, not by a token.** It requires
`UPDATE_PROCESS_INSTANCE` on the process definition plus a tenant check, so only a caller already
permitted to update the owning instance can fire its timers. The tenant rejection is `NOT_FOUND`, not
`FORBIDDEN`, so the endpoint cannot be used to probe whether a timer key exists in a tenant the caller
cannot access.

**D4. Timers are no longer held.** There is no `holdTimers` switch, no `held` flag on the timer
record, and no held-timer index. A timer sits in the due-date scheduler as normal and can be fired
early by key before its due date arrives. The feature is therefore a timer API, not a per-instance
recording switch ([ADR 0015](0015-811-per-instance-recording-switches.md)).

## Alternatives considered

- **Hold timers out of the scheduler and fire by `(instance, elementId)`.** The original design. It
  gave step-by-step control on a running cluster clock, but required a `held` flag on every timer
  record, a second column family indexing held timers by process instance, reconciliation with
  suspend/resume, and `409` on element-id collisions with no disambiguation. Once a timer search
  exists, a client can learn the key directly, so the simpler key-addressed trigger replaces it.
- **Pin the cluster clock, or suspend the instance.** Cluster-wide or coarse: the clock freezes
  everyone's timers; suspension releases all buffered timers at once on resume and blocks job
  completion. Neither gives per-timer, on-demand firing.
- **Address the trigger by `(elementInstanceKey, timerKey)` from search.** Redundant: once search
  returns the timer key, the key alone routes the command to the owning partition and resolves the
  timer, so the element instance adds nothing.

## Consequences

- **A timer can still fire on its own.** Without a hold, a short timer on a `REMOTE` cluster can reach
  its due date and fire before the recorder triggers it; the trigger then returns `NOT_FOUND`. A
  recorder relying on step-by-step timer control must either keep due dates long enough, or drive the
  clock in a mode that owns it (`MANAGED`/`SHARED` CPT). This is the deliberate trade for dropping the
  hold machinery.
- **Search is eventually consistent, the trigger is not.** A timer may not be indexed in secondary
  storage the instant it is created, so a client (and CPT) polls search until the timer appears, then
  triggers by key. The trigger command itself is strongly consistent.
- **A client trigger against a suspended instance is rejected, not buffered**, so a recorder is not
  left waiting for a resume that never comes.
- **No migration backfill of the `timerKey -> elementInstanceKey` index.** The index is populated on
  timer creation, so a timer already created before the upgrade has no entry and `getByKey` returns
  `NOT_FOUND` for it. Acceptable while the feature is alpha and the index is new; a migration task
  would be needed before timers created pre-upgrade must be triggerable by key.
- `TimerStateEnum` exposes `TRIGGERED` and `CANCELED` for completeness, but search only ever returns
  `CREATED` timers; filtering by another state returns empty.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)

