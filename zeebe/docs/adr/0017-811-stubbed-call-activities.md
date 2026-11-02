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

**D2. No new `JobKind` is introduced.** The stub reuses the existing BPMN-element job kind. A new kind
would ripple through every `switch` on it across engine, protocol, gateway, exporters, and webapps,
including name-based lookups that fail at runtime rather than compile time.

**D3. Stub completion reuses the real child's variable-propagation rule.** A call activity propagates
variables to its parent only under its model's output-mapping conditions; stub completion asks the same
condition, so it never propagates variables the model says to drop and cannot drift from real
completion.

**D4. A stub job is not releasable.** No worker can run it, so handing it back would park the call
activity forever. `runCalledProcess` is the escape hatch instead.

## Alternatives considered

- **Reuse `mockChildProcess` (deploy a stub definition).** Cluster-global, so it hijacks every
  concurrent instance of the called process. The stub is scoped to one instance instead.
- **Key the stub on the reservation token.** Would demand that token on every completion and couple the
  two switches. They stay independent, so recording through a real child remains possible.

## Consequences

- This changes BPMN execution semantics — a call activity activates with no child — so it is a deeper
  change than the other two switches.
- `runCalledProcess` against an undeployed called process raises a resolvable incident rather than
  banning the instance.
- Stub jobs are exported and searchable alongside reserved jobs, giving a recorder one inbox of waiting
  work. `THROW_ERROR` and `FAIL` work on a stub job, so a recording can drive the error boundary event.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)
- [ADR 0016 — Reserved jobs](0016-811-reserved-jobs.md)
