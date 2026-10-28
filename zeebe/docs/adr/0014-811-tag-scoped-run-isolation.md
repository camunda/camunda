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
`EventHandle.activateProcessInstanceForStartEvent` (the mechanism already used for `businessId`). This
is shipped for signals, for conditional starts (D4b), and for messages started locally on the message
partition. A message start delegated cross-partition to the business-id partition (`P_B`) is not yet
stamped — that path is deferred with the uniqueness lock (D7).

**D4b. Conditional start events carry a run-scope selector on the evaluate command.** A conditional
start has no broadcast/publish carrier to read a scope from, so `EvaluateConditionalStartEvent` gains
its own optional run-scope selector, stamped onto the instance it starts the same way as D4. Unset =
today's behaviour. This closes the gap left when the run-id process variable is retired, since a
conditional-started instance previously carried the run marker only in that variable.

**D5. An internal signal throw stamps its broadcast with the thrower's root scope tag.** A signal
thrown inside a process reads its root instance's reserved scope tag and stamps it as the selector on
the emitted broadcast command. An instance carrying no reserved scope tag broadcasts globally, so
existing in-model signal throwers are unaffected.

**D6. The reserved scope tag propagates to call-activity children.** A call activity copies only the
reserved scope tag (not general tags) from parent to child, so a run's jobs, user tasks, and catch
events inside called processes remain within the run's scope. This is a behaviour change from today,
where a child inherits no tags; it is required because run-scoped search and correlation would
otherwise miss everything below a call activity.

**D7. Message-start uniqueness composes the run scope into the correlation-key lock value (follow-up,
not yet implemented).** The "one active instance per correlation key" lock keys on `(bpmnProcessId,
correlationKey)` as two plain strings, built once in `DbMessageState`. This key, and its companion
cross-partition lock column family, shipped in 8.10 — a *released* column family whose key schema must
not change, so the lock cannot be re-keyed to a three-part `(bpmnProcessId, correlationKey, runScope)`
tuple (an earlier draft of this ADR proposed that; it is not implementable without rewriting released
state). Instead, the run scope is **composed into the correlation-key value** when set (unset → the
raw correlation key, byte-for-byte as today; set → `correlationKey` joined with the run scope), which
keeps the released key schema intact while giving two runs that share a correlation key their own
lock entry. The methods that write, release, and migrate this lock sit on a `Mutable*State` interface
and are called from *released* appliers, so each gains a new applier **version** (never a logic edit
to the released source), and the cross-partition message-start `REQUEST` from `P_K` to `P_B` carries
the scope so the remotely created instance is stamped correctly.

This work is scoped as a **follow-up** to the intermediate/boundary message-correlation scoping (D3),
because it alters released replay-critical appliers and must propagate the new applier versions to
every newer minor before release. A locally started message instance is already stamped with the run
scope (D4), so run-scoped *selection* finds it; what the follow-up adds is **start uniqueness** — until
it lands, two runs sharing a correlation key still collide on the one-active-instance start lock
(the second is deduplicated instead of starting its own instance). The cross-partition message-start
delegation to `P_B` is also not stamped until then. Migration appliers
(`ProcessInstanceElementMigrated` V2–V5) are expected to leave the composed scope unscoped on
migration — an accepted gap unless a migrated run-scoped instance must keep its start-lock scope.

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
- **A parallel run-scoped lock column family, instead of composing the run scope into the
  correlation-key value (D7).** Keep the shipped `MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY` and
  `CROSS_PARTITION_MESSAGE_START_LOCK` families byte-for-byte unchanged (used only when the selector is
  unset), and add new families keyed on a three-part `(bpmnProcessId, correlationKey, runScope)` tuple,
  written only when the selector is set. *Advantage:* the released families never change — their
  on-disk bytes and replay stay untouched — and no encoding scheme is needed, because a three-part key
  cannot collide with a raw two-part key. *Cost:* two or more new column families that are permanent,
  backed-up, migrated state, plus a `scoped ? newCF : oldCF` branch in every lock write, release, and
  the cross-partition path (the reverse `processInstanceKey → correlationKey` lookup must also carry the
  run scope so release targets the right family). Both options require new applier **versions** and
  propagation to newer minors; they differ only in whether released lock bytes are disturbed
  (value-composition) or a parallel structure is added (new CF). D7 chose value-composition to avoid
  column-family proliferation; the parallel-CF form is the safer-for-replay alternative when review
  prefers leaving shipped state untouched, and is being prototyped on this branch to compare the two.

## Consequences

- An unset selector preserves today's behaviour exactly for signals and messages, including the
  message-start lock key, so existing models and mixed-version clusters are unaffected.
- Run-scoped search of process instances and user tasks needs no key resolution and no paging: the
  scope rides the existing inherited `tags` field, which those searches can filter on directly
  (retiring the prior variable-isolation phase-2 key resolution for them). Jobs and incidents have no
  tag filter on their search, so their instances are still resolved by tag first and the search scoped
  to those instance keys — key resolution survives for those two, now keyed on the tag rather than the
  reserved variable.
- The reserved scope tag is settable through the public `tags` field (rejecting the prefix there would
  reject CPT's own create call). Tag validation enforces the tag format but not the reserved prefix, so
  a user could in principle set or collide a run scope. This spoofing risk is accepted; a
  tenant-per-run would avoid it.
- Call-activity tag propagation (D6) is a behaviour change: children now carry the parent's reserved
  scope tag. Only the reserved-prefix tag propagates, keeping the change invisible to non-scoped users.
- Message throw events are implemented as jobs, so a message published by a worker or connector inside
  a run is not scoped and can still cross runs. This leak is accepted and out of scope.
- Run isolation for the clock, engine mocks, purge, timers, and decision evaluation is not addressed
  by this or any carrier and remains out of scope.
- **Delivery is staged.** Signal broadcast scoping (D2–D6), conditional-start scoping (D4b), and
  intermediate/boundary message correlation scoping plus local message-start stamping (D3/D4 for
  messages) ship first. Message-start **uniqueness** (D7 — the one-active-instance lock) and
  cross-partition message-start stamping are a follow-up because they edit released replay-critical
  appliers. Until that lands, a scoped publish isolates catch, boundary, intermediate, and locally
  started message events; two runs sharing a correlation key still collide on the message-start lock.
- When the message-start lock work (D7) lands, its appliers gain new versions; the change must preserve
  replay for every newer minor and pass the engine golden-file check (`EventAppliersTest` /
  `NoChangesTest`), which fingerprints applier source — so new versions, never edits to released
  applier source.

## Source

- Signal mechanics and tag proposal — internal design doc (claude.ai artifact) (internal)
- Variable-based run-isolation proposal being superseded — internal design doc (claude.ai artifact) (internal)
- [ADR 0001 — Business ID message correlation cross-partition](0001-810-message-correlation-business-id-cross-partition.md)
- [ADR 0003 — Business ID call activity propagation](0003-810-business-id-call-activity-propagation.md)

