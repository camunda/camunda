# Stubbed call activities: activate without a child, wait on a stand-in job

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the stubbed-call-activity switch — how a listed call activity waits on a stand-in
job instead of starting its child, and how a mock worker completes that job.

**Audience**: Zeebe engine engineers working on call activities or job processing, and AI agents
reasoning about BPMN execution or the CPT recording path.

## Context

A recorded process containing a call activity would start the real child before the recorder can stand
in for it, and CPT's `mockChildProcess` deploys a cluster-global stub that hijacks every other
instance. Stubbing is the [recording switch](0016-811-per-instance-recording-switches.md) that makes a
call activity of one instance wait on a job instead. The shared create-time, root-inheritance and
REST-only mechanics are in ADR 0016.

## Decision

**D1. `STUB_CALL_ACTIVITIES` lists the called-process ids to stub.** A call activity is stubbed when
the process id its `calledElement` resolves to is in `processIds`; it activates, starts no child, and
waits on a stub job. Call activities calling any other process run their real child, so to run a child
for real, leave its id off the list. The list is inherited by the whole tree, so a nested call activity
calling a listed process is stubbed at any depth. The called definition is never looked up, so a
stubbed process need not be deployed. A FEEL-bound process id is stubbed only when its resolved value is
listed.

**D2. The stub job has its own `JobKind.CALL_ACTIVITY_STUB`.** The kind makes the stand-in job
distinguishable wherever a job surfaces: CPT selects it by kind, `/v2/jobs/search` returns it so a
recorder can find it, and views that should not show it as ordinary work (Operate's element-detail job
view) exclude it by default. The resolved called-process id is set as a job header. The kind is
mirrored across the protocol, gRPC, REST, search, Java client, and zod layers, and every exhaustive
`switch` and allow-list on `JobKind` handles it explicitly.

**D3. Completing the stub completes the call activity as the child would have.** Variables propagate
to the parent under the same output-mapping condition as a real child's completion, so the two cannot
drift. `THROW_ERROR` and `FAIL` work on a stub job, so a recording can drive the call activity's error
boundary event.

**D4. A stub's job type names its called process, so an ordinary activation drains it.** A stubbed call
activity's job carries the type `io.camunda.zeebe:callActivityStub:<calledProcessId>` (see
`Protocol.callActivityStubJobType`). A mock worker drains one process's stubs by activating that exact
`type` — no new activation field, no mutual-exclusion rule, and the job-type index already serves only
that process's stubs, a multi-instance call activity's iterations included. No ordinary worker
subscribes to that type, so none is served a stub. A stub carries no
[reservation token](0017-811-reserved-jobs.md), even in an instance that reserves other job types, and
needs none: the dedicated type isolates it, and parallel test runs are out of scope. Nothing special
withholds a stub from a job stream; no stream subscribes to the type, so none receives one.

An earlier revision added a `calledProcessId` activation field (mutually exclusive with `type`, `400`
if both) translated to one shared stub type and post-filtered by a called-process header. It was
dropped after review (it added mock-specific surface to the job API) in favour of the dedicated type,
which keeps the activation API unchanged.

**D5. CPT stands in for a called process with a mock worker per called-process id.**
`mockCallActivity(calledProcessId)` (`MOCK_JOB_WORKER_*` with `calledProcessId` in a test case) opens a
polling worker that drains that process's stubs and completes or errors each with the generic job
commands. A single stub can still be completed directly with `COMPLETE_JOB` /
`THROW_BPMN_ERROR_FROM_JOB`, selected by `{ jobKind: CALL_ACTIVITY_STUB, elementId }`.

## Alternatives considered

- **Stub every call activity with one boolean, opting real children back in per occurrence.** The
  first design. Rejected: a test mocking a few of many call activities had to opt every other one back
  in, and a model gaining a call activity broke recordings. An explicit list stubs only the named
  dependencies.
- **Key the list on call-activity element id.** Would target FEEL-bound call sites, but element ids
  collide across nested definitions, so a tree-wide list is ambiguous. May be added later for the FEEL
  case.
- **Reuse `mockChildProcess` (deploy a stub definition).** Cluster-global, so it hijacks every
  concurrent instance of the called process.
- **Reserve the stubs with a `reservationToken` on the switch.** An earlier revision. Rejected: it tied
  the stub switch to the instance's one reservation token (forcing `RESERVE_JOBS` and
  `STUB_CALL_ACTIVITIES` to agree on it) and required the token on every stub command, for no
  exclusivity the dedicated stub job type does not already give.
- **A `calledProcessId` activation field over one shared stub type.** An earlier revision. Rejected on
  review: it added a mock-specific property to the job activation API (and a `type`/`calledProcessId`
  mutual-exclusion `400`) where baking the called process into the job type needs no API change (D4).
- **Select stubs only through job search by element id.** Cannot drive a multi-instance call activity,
  whose stub count is known only at runtime, and keeps stubs off the worker path recorders already use.
- **Reuse `BPMN_ELEMENT` as the job kind.** The per-called-process job type (D4) is kept, but the
  dedicated `JobKind.CALL_ACTIVITY_STUB` is retained alongside it: completion needs to tell a stub from
  real work regardless of type, and reusing `BPMN_ELEMENT` would blur that. The per-process type does
  raise job-type cardinality in metrics, but stubs exist only in tests and the distinct types are
  bounded by the deployed called-process ids, and `JobMetricsCfg.maxJobTypeLength` caps the label
  length.

## Consequences

- This changes BPMN execution semantics — a call activity activates with no child — so it is a deeper
  change than reserved jobs.
- A process is mocked everywhere or nowhere within one instance tree; there is no per-occurrence split.
- `JobKind.CALL_ACTIVITY_STUB` is not rolling-upgrade-neutral: an older broker cannot deserialize the
  kind on replay. Accepted for an alpha 8.11 feature, but the kind must reach every newer minor before
  8.11 ships.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0016 — Per-instance recording switches](0016-811-per-instance-recording-switches.md)
- [ADR 0017 — Reserved jobs](0017-811-reserved-jobs.md)

