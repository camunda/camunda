# Tag-scoped run isolation: run scope rides a reserved instance tag, copied onto subscriptions and filtered locally

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how signal broadcast and message correlation can be restricted to a single
run, so concurrent runs of the same process definition on one cluster do not cross-trigger each
other's catch events, message correlations, or message-start uniqueness.

**Audience**: Zeebe engine engineers and AI agents working on signals, message correlation, or the
Camunda Process Test (CPT) run-isolation path.

## Context

Camunda Process Test runs multiple tests against one shared cluster. Signals broadcast globally and
messages correlate by name plus correlation key, so a signal or message raised by one test can wake
or start instances belonging to another test running concurrently. The prior approach carried a run
ID in a reserved process variable (`__camundaProcessTestRunId`) and filtered on it, but that carrier
is overwritten by broadcast/correlate variable payloads, leaks into worker/connector/child data, and
forces page-limited key resolution to find a run's jobs and user tasks.

The outcome: the run ID is carried as an immutable instance **tag** rather than a variable, and both
the signal-broadcast and message-correlation paths gain an optional run-scope selector. When set, a
waking catch event, a message correlation, or a message-start uniqueness check matches only within
the selected run; when unset, behaviour is identical to today. The message path reuses the shipped
`businessId` cross-partition filter mechanism ([ADR 0001](0001-810-message-correlation-business-id-cross-partition.md)).

## Decision

**D1. Run scope is a reserved-prefix tag inside the existing `tags` set.** A run is identified by a
single distinguished tag, `camunda:runScope:<id>`, stored among an instance's normal tags. The
engine treats only the reserved-prefix tag as a scope discriminator; general user tags never scope
anything. Because jobs and user tasks already inherit their process instance's `tags` and are
searchable by tag, a run's jobs and user tasks are searchable with no new fields on the job, user
task, or process-instance records.

**D2. Signal broadcast, message publish, and message correlate gain an optional run-scope
selector.** When the selector is unset, the command behaves exactly as today: global signal fan-out,
unscoped correlation. When set, it restricts matching to the selected run (D3–D4, D7). An unset
selector also defines the rolling-upgrade and mixed-version contract: the scoped path is distributed
to all partitions, and a broker that does not understand the selector treats the command as global.

**D3. The scope value is copied onto the subscription record at open time and filtered locally at
match time.** When a catch-event subscription opens, the engine reads the root instance's reserved
scope tag — local on the process-instance partition — and stores it on `SignalSubscriptionRecord`,
`ProcessMessageSubscriptionRecord`, and (shipped in the open command) `MessageSubscriptionRecord`.
At broadcast/correlate time the match is a purely local comparison against the stored value, with no
cross-partition read. This is required for messages: a message subscription lives on
`hash(correlationKey)`, a different partition from the root instance, so the root's tags are not
readable at correlate time. A scoped selector matches a stored scope only when they are equal; an
unset selector matches any subscription.

**D4. Start-event subscriptions always pass the scope filter, and the engine stamps the new instance
with the selector's scope tag.** A start-event subscription has no instance yet and therefore no
stored scope, so the filter must let it through regardless of the selector — otherwise every scoped
signal-start and message-start would silently do nothing. When a scoped broadcast/publish starts an
instance, the engine stamps the reserved scope tag onto the new instance, threading the scope into
`EventHandle.activateProcessInstanceForStartEvent` (the mechanism already used for `businessId`).

**D5. An internal signal throw stamps its broadcast with the thrower's root scope tag.** A signal
thrown inside a process reads its root instance's reserved scope tag and stamps it as the selector on
the emitted broadcast command. An instance carrying no reserved scope tag broadcasts globally, so
existing in-model signal throwers are unaffected.

**D6. The reserved scope tag propagates to call-activity children.** A call activity copies only the
reserved scope tag (not general tags) from parent to child, so a run's jobs, user tasks, and catch
events inside called processes remain within the run's scope. This is a behaviour change from today,
where a child inherits no tags; it is required because run-scoped search and correlation would
otherwise miss everything below a call activity.

**D7. Message-start uniqueness extends its correlation-key lock identity with the run scope.** The
"one active instance per correlation key" lock is re-keyed from `(bpmnProcessId, correlationKey)` to
`(bpmnProcessId, correlationKey, runScope)`, so two runs sharing a correlation key each start their
own instance instead of colliding. An unset scope reproduces today's exact key byte-for-byte. The
appliers that write, release, and migrate this lock are released code; each gains a new applier
*version* rather than a logic edit, and the cross-partition message-start `REQUEST` from `P_K` to
`P_B` carries the scope so the remotely created instance is stamped correctly.

## Alternatives considered

- **A dedicated `runScope` field on process-instance, job, user-task, and subscription records.**
  Gives the cleanest separation from user tags, but to keep a run's jobs and user tasks searchable it
  must be added and indexed on the job and user-task records and exposed through search — a large
  protocol, exporter, and index-template blast radius that duplicates what the inherited `tags` field
  already provides. Rejected for scope; the reserved-tag carrier (D1) reuses existing inheritance and
  search.
- **Reading the root instance's tag at broadcast/correlate time** instead of copying it onto the
  subscription. Impossible on the message path: the subscription lives on `hash(correlationKey)`,
  the root instance on another partition, so its tags are not local at correlate time. Rejected in
  favour of copy-at-open (D3), consistent with the `businessId` design.
- **Reusing `businessId` as the run carrier, or combined-hash routing.** `businessId` enforces
  one-active-instance uniqueness and is user-facing domain semantics; reusing it would forbid multiple
  instances per run and change routing contracts. The run scope must be orthogonal to it.

## Consequences

- An unset selector preserves today's behaviour exactly for signals and messages, including the
  message-start lock key, so existing models and mixed-version clusters are unaffected.
- Run-scoped search of jobs and user tasks needs no key resolution and no paging: the scope rides the
  existing inherited `tags` field (retiring the prior variable-isolation phase-2 key resolution).
- Call-activity tag propagation (D6) is a behaviour change: children now carry the parent's reserved
  scope tag. Only the reserved-prefix tag propagates, keeping the change invisible to non-scoped users.
- Message throw events are implemented as jobs, so a message published by a worker or connector inside
  a run is not scoped and can still cross runs. This leak is accepted and out of scope.
- Run isolation for the clock, engine mocks, purge, timers, and decision evaluation is not addressed
  by this or any carrier and remains out of scope.
- The message-start lock appliers gain new versions (D7); the change must preserve replay for every
  newer minor and pass the engine golden-file check (`NoChangesTest`).

## Source

- Signal mechanics and tag proposal — internal design doc (claude.ai artifact) (internal)
- Variable-based run-isolation proposal being superseded — internal design doc (claude.ai artifact) (internal)
- [ADR 0001 — Business ID message correlation cross-partition](0001-810-message-correlation-business-id-cross-partition.md)
- [ADR 0003 — Business ID call activity propagation](0003-810-business-id-call-activity-propagation.md)

