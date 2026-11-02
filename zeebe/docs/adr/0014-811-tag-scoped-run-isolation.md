# Tag-scoped run isolation: run scope rides a reserved instance tag, copied onto subscriptions and filtered locally

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how signal broadcast and message correlation are restricted to a single run, so
concurrent runs of the same process definition on one cluster do not cross-trigger each other's catch
events, correlations, or message-start uniqueness.

**Audience**: Zeebe engine engineers working on signals, message correlation, or the Camunda Process
Test (CPT) run-isolation path.

## Context

CPT runs many tests against one shared cluster. Signals broadcast globally and messages correlate by
name plus correlation key, so a signal or message from one test can wake or start another's instances.
An earlier approach carried a run id in a reserved process variable and filtered on it, but that carrier
is overwritten by broadcast/correlate payloads, leaks into worker and child data, and forces
page-limited key resolution.

The outcome: the run id is carried as an immutable instance **tag**, and the signal-broadcast and
message-correlation paths gain an optional run-scope selector. When set, a waking catch event, a
correlation, or a message-start uniqueness check matches only within the selected run; when unset,
behaviour is identical to today. The message path reuses the shipped `businessId` cross-partition filter
mechanism ([ADR 0001](0001-810-message-correlation-business-id-cross-partition.md)).

## Decision

**D1. Run scope is a reserved-prefix tag inside the existing `tags` set.** A run is identified by one
distinguished tag, `camunda:runScope:<id>`, stored among an instance's normal tags; only the
reserved-prefix tag scopes anything. Because jobs and user tasks already inherit their instance's `tags`
and are searchable by tag, a run's jobs and user tasks are searchable with no new record fields.

**D2. Signal broadcast, message publish, and message correlate gain an optional run-scope selector.**
Unset, the command behaves exactly as today (global fan-out, unscoped correlation); set, it restricts
matching to the selected run. An unset selector is also the mixed-version contract: a broker that does
not understand the selector treats the command as global.

**D3. The scope value is copied onto the subscription record at open time and filtered locally at match
time.** When a catch-event subscription opens, the engine reads the root instance's scope tag — local on
the process-instance partition — and stores it on the subscription record; at match time the comparison
is purely local. This is required for messages, whose subscription lives on `hash(correlationKey)`, a
different partition from the root instance. A scoped selector matches only an equal stored scope; an
unset selector matches any subscription.

**D4. Start-event subscriptions always pass the filter, and the engine stamps the new instance with the
selector's scope.** A start-event subscription has no instance yet and so no stored scope, so the filter
must let it through or every scoped start would silently do nothing. When a scoped broadcast/publish
starts an instance, the engine stamps the scope tag onto it, threading the scope through
`EventHandle.activateProcessInstanceForStartEvent` as `businessId` already does. This covers signals,
conditional starts (D4b), and messages started on the message partition and delegated cross-partition.

**D4b. Conditional start events carry a run-scope selector on the evaluate command.** A conditional start
has no broadcast/publish carrier to read a scope from, so `EvaluateConditionalStartEvent` gains its own
optional selector, stamped onto the instance it starts. Unset = today's behaviour.

**D5. An internal signal throw stamps its broadcast with the thrower's root scope tag.** A signal thrown
inside a process reads its root instance's scope tag and stamps it as the selector; an instance carrying
no scope tag broadcasts globally, so existing in-model throwers are unaffected.

**D6. The reserved scope tag propagates to call-activity children.** A call activity copies only the
reserved scope tag (not general tags) from parent to child, so a run's jobs, user tasks, and catch events
inside called processes stay in scope. This is a behaviour change — a child inherits no tags today — and
is required or run-scoped search and correlation would miss everything below a call activity.

**D7. Message-start uniqueness is scoped through a parallel lock column family.** The "one active
instance per correlation key" lock keys on `(bpmnProcessId, correlationKey)` in a column family that
shipped in 8.10 — released state whose key schema must not change. Rather than re-key it, run-scoped
uniqueness uses separate column families keyed on `(bpmnProcessId, correlationKey, runScope)`, written
only when the selector is set; the shipped families are used unchanged when it is unset. A three-part key
cannot collide with the raw two-part key, so released lock bytes are never disturbed. The lock writes sit
on `Mutable*State` and are called from released appliers, so each gains a new applier **version**, and the
cross-partition message-start `REQUEST` carries the scope so the remotely created instance is stamped.

## Alternatives considered

- **A dedicated `runScope` field on process-instance, job, user-task, and subscription records.** Cleanest
  separation from user tags, but to keep a run's jobs and user tasks searchable it must be indexed and
  exposed through search — a large protocol, exporter, and index blast radius duplicating what the
  inherited `tags` field already gives. Rejected for scope.
- **Reading the root instance's tag at broadcast/correlate time** instead of copying it onto the
  subscription. Impossible on the message path: the subscription and the root instance live on different
  partitions, so the tag is not local at correlate time.
- **Reusing `businessId` as the run carrier.** `businessId` enforces one-active-instance uniqueness and is
  user-facing domain semantics; reusing it would forbid multiple instances per run and change routing. The
  run scope must be orthogonal.
- **Composing the run scope into the correlation-key value (D7).** Keeps the shipped families but requires
  an encoding scheme on a released key and disturbs its on-disk bytes. The parallel column family was
  chosen instead to leave released lock state byte-for-byte untouched, at the cost of new permanent column
  families.

## Consequences

- An unset selector preserves today's behaviour exactly for signals and messages, so existing models and
  mixed-version clusters are unaffected.
- Run-scoped search of process instances and user tasks needs no key resolution: the scope rides the
  inherited `tags` field those searches filter on. Jobs and incidents have no tag filter on their search,
  so their instances are still resolved by tag first and the search scoped to those keys.
- The reserved scope tag is settable through the public `tags` field, so a user could in principle set or
  collide a run scope. This spoofing risk is accepted; a tenant-per-run would avoid it.
- Message throw events are jobs, so a message a worker or connector publishes inside a run is unscoped and
  can still cross runs. Accepted and out of scope.
- The scope tag is one carrier among several: per-instance job, call-activity, and timer isolation are
  separate recording switches ([ADR 0015](0015-811-per-instance-recording-switches.md) and
  [0018](0018-811-held-timers.md)). Clock, purge, and decision evaluation remain out of scope.
- The D7 appliers carry new versions; the change preserves replay for every newer minor and must pass the
  engine golden-file check (`EventAppliersTest` / `NoChangesTest`) — new versions, never edits to released
  applier source.

## Source

- Signal mechanics and tag proposal — internal design doc (claude.ai artifact) (internal)
- [ADR 0001 — Business ID message correlation cross-partition](0001-810-message-correlation-business-id-cross-partition.md)
- [ADR 0003 — Business ID call activity propagation](0003-810-business-id-call-activity-propagation.md)

