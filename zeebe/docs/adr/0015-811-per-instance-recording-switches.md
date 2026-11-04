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

**D2. The payload-free switches ride one `CONFIGURE_EXECUTION` runtime instruction; call-activity
stubbing is its own `STUB_CALL_ACTIVITIES` instruction.** The create request carries runtime
instructions in its `runtimeInstructions` list. `CONFIGURE_EXECUTION` holds the boolean switch
`reserveJobs` (+ `jobReservationToken`); it defaults off, and at most one is allowed per instance.
Keeping it a payload-free instruction leaves room to bundle further bare booleans here rather than
forcing an empty-object instruction each. Stubbing instead carries a `processIds` list
([ADR 0017](0017-811-stubbed-call-activities.md) D1), a real payload of its own, so it is a separate
`STUB_CALL_ACTIVITIES` instruction (also at most one per instance) rather than a field whose value
would sit oddly next to the bare booleans. Splitting the concern this way also keeps each instruction
independently extensible.

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
- **Top-level boolean fields, or a separate runtime instruction per switch.** Top-level fields scatter
  related switches across the request; a per-switch instruction for a payload-free boolean forces an
  empty-object instruction carrying only its own type. `CONFIGURE_EXECUTION` gives the bare booleans one
  consistent shape; a switch that does carry a payload (`STUB_CALL_ACTIVITIES` with its `processIds`
  list) earns its own instruction, so the split is by whether the switch has a payload, not one
  instruction for everything.

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

