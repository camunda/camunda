# Stubbed call activities: activate without a child, wait on a stand-in job

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the stubbed-call-activity switch — how a call activity waits instead of starting
its child, and the escape hatch that runs the real child after all.

**Audience**: Zeebe engine engineers working on call activities or job processing, and AI agents
reasoning about BPMN execution or the CPT recording path.

## Context

A recorded process containing a call activity would start the real child before the recorder can stand
in for it, and CPT's `mockChildProcess` deploys a cluster-global stub that hijacks every other
instance. Stubbing is the [recording switch](0015-811-per-instance-recording-switches.md) that makes a
call activity wait on a job scoped to one instance. The shared create-time, root-inheritance,
poll-index, and REST-only mechanics are defined in [ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. `stubCallActivities` makes each call activity activate, start no child, and wait on a stub job.**
The called definition is never looked up, so it need not be deployed. Completing the stub job stands in
for the called process; completing it with a `runCalledProcess` result starts the real child instead.

**D2. The stub job carries its own `JobKind.CALL_ACTIVITY_STUB`.** A first-class kind makes the
stand-in job distinguishable everywhere a job surfaces: `isCallActivityStub()` is a single kind check,
and CPT selects the stand-in job by kind rather than by a magic job-type string. `/v2/jobs/search`
still returns stub jobs — that is how a recorder finds the stand-in job — while callers that should not
present them as ordinary work, like Operate's element-detail job view, exclude the kind by default. This reverses an earlier decision to reuse
`BPMN_ELEMENT`. The kind is mirrored across the protocol, gRPC, REST, search, Java client, and zod
layers, and every exhaustive `switch` on `JobKind` and every allow-list (job-activation hiding,
`THROW_ERROR`/`FAIL` support, incident error-type mapping) handles it explicitly. The ripple is real
but bounded, and a distinguishable stub was worth it.

**D3. Stub completion reuses the real child's variable-propagation rule.** A call activity propagates
variables to its parent only under its model's output-mapping conditions; stub completion asks the same
condition, so it never propagates variables the model says to drop and cannot drift from real
completion.

**D4. A stub job is not releasable.** No worker can run it, so handing it back would park the call
activity forever. `runCalledProcess` is the escape hatch instead.

**D5. CPT drives the stub through the generic job instructions, not stub-specific ones.** Standing in
for the called process is a `COMPLETE_JOB`, and failing it a `THROW_BPMN_ERROR_FROM_JOB`, each selecting
the stand-in job by `{ jobKind: CALL_ACTIVITY_STUB, elementId }`. Only `RUN_CALLED_PROCESS` stays a
distinct instruction, because it starts the real child rather than completing a job. The dedicated job
kind from D2 — not a per-called-process job type — is what lets an element-scoped selector pick exactly
the stub, since a call activity's execution-listener jobs share its element id.

## Alternatives considered

- **Reuse `mockChildProcess` (deploy a stub definition).** Cluster-global, so it hijacks every
  concurrent instance of the called process. The stub is scoped to one instance instead.
- **Key the stub on the reservation token.** Would demand that token on every completion and couple the
  two switches. They stay independent, so recording through a real child remains possible.
- **Reuse `BPMN_ELEMENT` and append the called-process id to the job type** (so the existing job
  instructions could target one stub by type). Rejected: the stub is hidden from job workers, so a
  per-type mock worker never sees it, and the search-based `COMPLETE_JOB` already singles out one stub
  by `elementId`. A per-called-process job type bought nothing and would have multiplied job-type
  cardinality in metrics. A dedicated `JobKind` (D2) gives the clean discriminator instead.

## Consequences

- This changes BPMN execution semantics — a call activity activates with no child — so it is a deeper
  change than the other two switches.
- `runCalledProcess` against an undeployed called process raises a resolvable incident rather than
  banning the instance.
- Stub jobs are exported and searchable alongside reserved jobs, giving a recorder one inbox of waiting
  work. `THROW_ERROR` and `FAIL` work on a stub job, so a recording can drive the error boundary event.
- The new `JobKind.CALL_ACTIVITY_STUB` is not rolling-upgrade-neutral: a broker that predates it cannot
  deserialize a stub job's kind on replay, so a stub job written by a new leader makes an old follower
  unhealthy. This is accepted for an alpha 8.11 feature — no released cluster holds stub jobs — but the
  kind must reach every newer minor before 8.11 ships.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)
- [ADR 0016 — Reserved jobs](0016-811-reserved-jobs.md)

