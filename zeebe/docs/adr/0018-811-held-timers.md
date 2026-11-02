# Held timers: kept out of the due-date scheduler, fired on demand by element

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the held-timers switch — how a timer is kept from firing on its own, how it is
fired on demand, and why it is addressed by element rather than by a token.

**Audience**: Zeebe engine engineers working on timers or process-instance suspension, and AI agents
reasoning about timer scheduling or the CPT recording path.

## Context

On a live cluster a short timer reaches its due date and the scheduler fires it before the recorder can
author the step. Pinning the cluster clock freezes every instance's timers; suspension fires all
buffered timers at once on resume and blocks job completion. Holding is the
[recording switch](0015-811-per-instance-recording-switches.md) that keeps one instance's timers from
firing while leaving the instance drivable. The shared create-time, root-inheritance, and REST-only
mechanics are defined in [ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. `holdTimers` keeps every timer of the instance and its children out of the due-date scheduler.**
A held timer is created and stored like any other and is visible as a timer wait state, but it is never
inserted into the due-date index the scheduler scans — only into the timer store — so it never fires on
its own. This mirrors how suspension holds a timer back by removing its due-date entry, applied from
creation.

**D2. A held timer is fired by `(processInstanceKey, elementId)` through a trigger command.**
`POST /process-instances/{processInstanceKey}/timers/trigger` fires exactly one held timer on demand.
It reuses the processor that already fires a single timer and already skips the due-date check, adding a
client-command entry path that resolves the concrete timer, authorizes the caller, hydrates the command,
and writes a response; the scheduler's own path is untouched.

**D3. The held-timer index is keyed only on fields immutable for a timer's life.** The index keys on
`(processInstanceKey, elementInstanceKey, timerKey)` and matches `elementId` as a read-time predicate,
not a key. `elementId` is mutable — process-instance migration rewrites it — so keying on it would
orphan the index entry on migration and make the timer unreachable under its new element id.

**D4. The trigger command is authorized like any process-instance command, not by a token.** It
requires `UPDATE_PROCESS_INSTANCE` on the process definition plus a tenant check, so only a caller
already permitted to update the instance can fire its timers. The tenant rejection is `NOT_FOUND`, not
`FORBIDDEN`, so the endpoint cannot probe whether a held timer exists in a tenant the caller cannot
access.

## Alternatives considered

- **Pin the cluster clock, or suspend the instance.** The clock is cluster-wide and freezes everyone's
  timers; suspension fires all buffered timers at once on resume and blocks job completion, so neither
  gives per-instance, step-by-step control.
- **A per-instance or per-token clock offset.** Preserves real due dates but adds a clock dimension to
  every timer and to the scheduler — the heaviest option — for no recording benefit.
- **Address the trigger by `(elementInstanceKey, timerKey)`.** Would remove the held-timer index
  entirely, but there is no timers search API, so a client has no way to learn a `timerKey`. Element-id
  addressing keeps the command usable by its only consumer.

## Consequences

- There is no revoke/unhold command (the timer analogue of releasing a reserved job): a held timer can
  only be fired, not handed back to the scheduler to fire on its real due date, which would require
  recomputing due dates. Not built; a recorded test fires its timers explicitly.
- Two live held timers on one element id reject with `409` rather than guessing; disambiguating them
  needs an optional `elementInstanceKey` on the request. Not built.
- Holding and suspension both remove a timer from the due-date index, so they are reconciled: resume
  skips a held timer, and a client trigger against a suspended instance is rejected rather than buffered.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)

