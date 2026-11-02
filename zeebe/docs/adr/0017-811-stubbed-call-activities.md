# Stubbed call activities: activate without a child, wait on a stand-in job

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the stubbed-call-activity switch — how a call activity waits without starting its
child, how the stand-in job is completed, and the escape hatch that runs the real child after all.

**Audience**: Zeebe engine engineers working on call activities or job processing, and AI agents
reasoning about BPMN execution or the CPT recording path.

## Context

A recorded process that contains a call activity would otherwise have the engine start the real child
process before the recorder can stand in for it. CPT's existing `mockChildProcess` deploys a stub
definition, which is global and hijacks every other instance on a shared cluster. Stubbing is the
[recording switch](0015-811-per-instance-recording-switches.md) that makes a call activity wait on a
job scoped to one instance. The shared create-time, root-inheritance, poll-index, and REST-only
mechanics are defined in [ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. `stubCallActivities` makes each call activity activate, start no child, and wait on a stub job.**
The stub path resolves the called process id, subscribes to events, and activates the element, but
never looks up the called definition — so it need not be deployed. Completing the stub job stands in
for the called process; completing it with a `runCalledProcess` result starts the real child after all.

**D2. No new `JobKind` is introduced.** The stub reuses `BPMN_ELEMENT` on a call-activity element. A new
`JobKind` value would ripple through every switch on the kind across engine, protocol, gateway,
exporters, and webapps — including name-based `valueOf` paths that fail at runtime rather than compile
time.

**D3. The variable-propagation gate lives in one place, shared by real and stub completion.** A real
child writes the parent's event trigger only when the call activity has output mappings or propagates
all child variables; a completed job always writes one. Stub completion asks the same
`ExecutableCallActivity.propagatesCalledProcessVariables()` condition the real-child applier asks, so
stubbing never starts propagating variables the model says to drop and the two paths cannot drift.

**D4. A stub job is not releasable.** Unlike a reserved job, no worker could ever run a stub job, so
handing it back would park the call activity forever. `runCalledProcess` is the deliberate escape
hatch: it starts the real child instead of releasing the job.

## Alternatives considered

- **Reuse CPT's `mockChildProcess` (deploy a stub definition).** Global to the cluster, so it hijacks
  every concurrent instance of the called process. The stub is scoped to one instance instead.
- **Key the stub on the reservation token.** Synthesizing a reservation to reuse its hide would then
  demand that token on every completion and couple the two switches. They stay independent, so
  recording *through* a real child process remains possible.

## Consequences

- This changes BPMN execution semantics — an activated call activity with no child instance — not only
  who may act on a job, so it is a deeper change than the other two switches.
- `runCalledProcess` against an undeployed called process raises a resolvable incident rather than
  banning the instance, so the recording continues once the process is deployed.
- Stub jobs are exported and searchable, appearing as a job-based wait state. This is intended: it is
  the one inbox a recorder uses to find what is waiting, shared with reserved jobs.
- `THROW_ERROR` and `FAIL` are supported on a stub job, so a recording can drive the call activity's
  error boundary event or fail it into an incident.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)
- [ADR 0016 — Reserved jobs](0016-811-reserved-jobs.md)
