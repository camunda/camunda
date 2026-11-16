# Per-instance recording switches set at create time via runtime instructions

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the mechanism shared by the per-instance recording switches (reserved jobs,
stubbed call activities) — how they are set, where they are read, how the engine withholds work, and
why they are REST-only.

**Audience**: Zeebe engine engineers working on job activation or call activities, and AI agents
reasoning about the Camunda Process Test (CPT) recording path.

## Context

CPT records a test by driving a real instance on a shared cluster and authoring the matching
assertion after each step. The engine acts faster than a human can author: a worker grabs a job, a
call activity starts its child, a timer fires — so the step is silently skipped and the test passes
anyway. The existing levers (disable workers, pin the clock, deploy a global stub definition) are
cluster-wide and block anyone else recording on the same cluster.

So isolation moves into the engine one process instance at a time: a switch set at creation makes the
engine stand aside for that instance while every other instance runs normally. The two switches are
[reserved jobs](0017-811-reserved-jobs.md) and
[stubbed call activities](0018-811-stubbed-call-activities.md). Timers are not a switch; a recording
fires a waiting timer on demand ([ADR 0019](0019-811-timer-search-and-trigger-by-key.md)).

## Decision

**D1. Each switch is a create-time runtime instruction listing what the engine stands aside for.**
`RESERVE_JOBS` lists job types (plus a `reservationToken`); `STUB_CALL_ACTIVITIES` lists called-process
ids. At most one of each per instance; omitting one leaves that switch off. Both use an explicit list,
so everything not listed runs for real.

**D2. Switches are read at the root instance and inherited by call-activity children.** The engine
reads them from the root element instance's record, so a called process's jobs and call activities are
governed without repeating the switch.

**D3. One "withheld from workers" decision, applied while walking the poll index.** The poll collector
and the push path consult a single rule: a reserved job is served only to an activation presenting its
token, and a stub job only to an activation naming its called process. Withheld jobs stay in the
activatable-by-priority poll index, because their owner activates them through the same poll. Job
streams carry neither a token nor a called-process id, so a withheld job is never pushed, but a waiting
long poll is still notified so its owner re-polls. The cost is that a poll walks past its type's parked
jobs; accepted, as a parked job's type is normally driven only by its recorder.

**D4. The switches are REST-only and alpha.** `gateway.proto` gains no counterpart, so an instance
cannot be reserved or stubbed over gRPC. The Java client's setters write only the REST request, and
`send()` throws over gRPC rather than silently dropping a switch.

## Alternatives considered

- **Cluster-wide levers — disable workers, pin the clock, deploy a global stub definition.** Each
  freezes or pollutes every other instance, so two people cannot record at once.
- **Process-instance suspension.** Per-instance, but it pauses rather than steps and blocks job
  completion while suspended, so a recorder cannot drive the instance.
- **Boolean switches on one shared `CONFIGURE_EXECUTION` instruction.** The first design. Rejected once
  both switches became selective: each names what it acts on, so a boolean fits neither.

## Consequences

- A parked instance has no engine-enforced lifetime: an abandoned recording leaks an instance until it
  is cancelled. CPT cancels the instances a run isolated on teardown; a raw API caller or a crashed
  recorder is unbounded.
- The switches and the token live on the process-instance and job records, so they ride exported data.
  Keeping them in engine-only side state would avoid that; not built.
- The switches are orthogonal and may be combined on one instance.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [Hosted Camunda Process Test (product-hub #3546)](https://github.com/camunda/product-hub/issues/3546) (internal)

