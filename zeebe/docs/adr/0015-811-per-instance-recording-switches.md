# Per-instance recording switches set at create time via runtime instructions

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the shared mechanism behind the per-instance recording switches
(reserved jobs, stubbed call activities) — how they are set, where they are read, how the engine
withholds work, and why they are REST-only. Timers are not a create-time switch; they are driven by
a separate timer API ([ADR 0018](0018-811-timer-search-and-trigger-by-key.md)).

**Audience**: Zeebe engine engineers working on job activation, call activities, or timers, and AI
agents reasoning about the Camunda Process Test (CPT) recording path.

## Context

CPT records a test by driving a real instance on a shared cluster and authoring the matching
assertion after each step. The engine acts faster than a human can author: a worker grabs a job, a
call activity starts its child, a timer fires on the wall clock — so the step is silently skipped and
the test passes anyway, which is worse than no test. The existing levers (disable workers, pin the
clock, deploy a global stub definition) are all cluster-wide and block anyone else recording on the
same cluster.

The outcome: isolation is pushed into the engine one process instance at a time, through switches set
when the instance is created. Each switch makes the engine stand aside for that instance while every
other instance on the cluster runs normally, so two people can record side by side. The two
switches — [reserved jobs](0016-811-reserved-jobs.md) and
[stubbed call activities](0017-811-stubbed-call-activities.md) — share the mechanism defined here.
Timers are not withheld at create time: a recording fires a waiting timer on demand through the timer
API ([ADR 0018](0018-811-timer-search-and-trigger-by-key.md)). Run isolation of global signals and
messages is a separate carrier ([ADR 0014](0014-811-tag-scoped-run-isolation.md)).

## Decision

**D1. Recording isolation is per process instance, chosen at create time.** A switch affects only the
instance it is set on, not the cluster, so concurrent recorders do not interfere. The engine hands
the recorder exclusive control of that instance's jobs and call activities.

**D2. Each switch is its own runtime instruction that names what the engine stands aside for:
`RESERVE_JOBS` and `STUB_CALL_ACTIVITIES`.** The create request carries runtime instructions in its
`runtimeInstructions` list. `RESERVE_JOBS` carries a `jobTypes` list and a `jobReservationToken`
([ADR 0016](0016-811-reserved-jobs.md) D1); `STUB_CALL_ACTIVITIES` carries a `processIds` list
([ADR 0017](0017-811-stubbed-call-activities.md) D1). At most one of each is allowed per instance, and
omitting an instruction leaves that switch off. Both use the same shape on purpose: an explicit list
of the job types or called processes the recorder drives, so everything not listed runs for real. The
token is written on the create request and on every job command that needs it, rather than being
minted by the client.

**D3. Switches are read at the root instance and inherited by call-activity children.** The engine
reads the switch from the root element instance's record, so a called process's jobs and call
activities are governed too without the recording repeating the switch on every call activity.

**D4. One "withheld from workers" decision, and withheld jobs are kept out of the poll index.** Both
hand-out paths — the poll collector and the push/notify path — consult a single reason that says why
a job reaches no worker (reserved, or a call-activity stub). A withheld job is never inserted into the
activatable-by-priority poll index, so a poll never walks a parked job; indexing-then-skipping would
cost O(parked) per poll and degrade the whole job type as parked instances accumulate.

**D5. The feature is REST-only and alpha.** `gateway.proto` gains no counterpart for any switch, so an
instance cannot be reserved or stubbed over gRPC. The Java client's setters write only the
REST request and `send()` throws over gRPC rather than silently dropping a switch. REST is the client
default, so this only affects a caller who opted into gRPC and asked for a REST-only feature.

## Alternatives considered

- **Cluster-wide levers — disable workers, pin the clock, deploy a global stub definition.** Each is
  the pre-existing way to achieve one switch's effect, and each freezes or pollutes every other
  instance on the cluster, so two people cannot record at once. The per-instance switches replace them
  one for one.
- **Process-instance suspension.** Per-instance, but it pauses rather than steps (resume releases
  everything at once) and blocks job completion while suspended, so a recorder cannot drive the
  instance. Unusable for interleaved recording.
- **Top-level boolean fields, or one shared `CONFIGURE_EXECUTION` instruction of bare booleans.** The
  first design reserved every job with a `reserveJobs: true` boolean on a shared instruction. Rejected
  once reservation became selective: both switches now name what they act on (job types, called
  process ids), so a boolean no longer fits either, and a shared instruction left holding no switch of
  its own had no reason to exist.

## Consequences

- A parked instance (a reserved or stub job) has no engine-enforced lifetime: an abandoned recording
  leaks an instance until it is cancelled. CPT cancels the instances a run created on teardown; a raw
  API caller or a crashed recorder tab is unbounded. A TTL or sweeper is not built.
- The switches and the reservation token live on the process-instance record (and the job records),
  so they ride exported data. Moving them to engine-only side state keyed by the root
  `processInstanceKey` would keep them out of secondary storage; it is an un-built alternative.
- The switches are orthogonal: an instance may reserve its jobs and stub its call activities in any
  combination, and each is independent of run-scope isolation
  ([ADR 0014](0014-811-tag-scoped-run-isolation.md)).

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [Hosted Camunda Process Test (product-hub #3546)](https://github.com/camunda/product-hub/issues/3546) (internal)

