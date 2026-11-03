# Stubbed call activities: activate without a child, wait on a stand-in job

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the stubbed-call-activity switch — how a call activity named by the switch waits
instead of starting its child, while un-named call activities run their real child.

**Audience**: Zeebe engine engineers working on call activities or job processing, and AI agents
reasoning about BPMN execution or the CPT recording path.

## Context

A recorded process containing a call activity would start the real child before the recorder can stand
in for it, and CPT's `mockChildProcess` deploys a cluster-global stub that hijacks every other
instance. Stubbing is the [recording switch](0015-811-per-instance-recording-switches.md) that makes a
call activity wait on a job scoped to one instance. The shared create-time, root-inheritance,
poll-index, and REST-only mechanics are defined in [ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. A `STUB_CALL_ACTIVITIES` instruction names the called-process ids to stub; each matching call
activity activates, starts no child, and waits on a stub job.** The instruction carries a
`processIds` list, and a call activity is stubbed when the process id it resolves to is in that list;
call activities calling any other process run their real child. The list is stored on the root
instance and inherited by the whole tree, so a nested call activity calling a listed process is
stubbed at any depth. The called definition is never looked up — only the `calledElement` process-id
expression is evaluated, to test list membership and to tell the stub job's completer which process is
stood in for — so a stubbed process need not be deployed. Completing the stub job stands in for the
called process. There is no per-occurrence "run the real child" escape: to run a child for real, omit
its process id from the list. A call activity whose process id is a FEEL expression is stubbed only
when its resolved value is listed; a call site whose resolved id cannot be predicted up front cannot be
targeted by the list (a known limitation — see Alternatives).

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
activity forever. To run a call activity's real child, omit its process id from the stub list rather
than stub-then-override.

**D5. CPT drives the stub entirely through the generic job instructions, with no stub-specific
instruction.** Standing in for the called process is a `COMPLETE_JOB`, and failing it a
`THROW_BPMN_ERROR_FROM_JOB`, each selecting the stand-in job by `{ jobKind: CALL_ACTIVITY_STUB,
elementId }`. The dedicated job kind from D2 — not a per-called-process job type — is what lets an
element-scoped selector pick exactly the stub, since a call activity's execution-listener jobs share its
element id. An earlier design added a `RUN_CALLED_PROCESS` instruction to run the real child of a
stubbed occurrence; it was dropped — running real is expressed by not listing the process, keeping
mocking explicit rather than mock-all-then-override.

## Alternatives considered

- **Stub all call activities with a single boolean (`stubCallActivities: true`).** The first design:
  a boolean on `CONFIGURE_EXECUTION` stubbed every call activity, and the real child was opted back in
  per occurrence with `runCalledProcess`. Rejected: when a process has many call activities and a test
  mocks only a few, stub-all forces a `runCalledProcess` for every remaining one. An explicit
  `processIds` list stubs only the named dependencies, so the un-listed ones run real with no extra
  instruction, and a recording survives a model gaining a new call activity (it runs real rather than
  parking forever on replay). The cost is the FEEL limitation in D1 and that selection is per-called-
  process, not per-occurrence; completing the stub still selects per-occurrence by `elementId`.
- **Key the list on call-activity element id instead of called-process id.** Element ids are static in
  the model and would target a FEEL-bound call site, but they collide across nested definitions (a
  child's `CallActivity_1` is indistinguishable from the root's), so a tree-wide list keyed on element
  id is ambiguous. Called-process id is globally meaningful across the inherited tree and gives drop-in
  parity with `mockChildProcess(withProcessId)`. Element-id keying is left as a possible later addition
  for the FEEL case.
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
- Selection is per called-process id, so a process is mocked everywhere or nowhere within one instance;
  there is no per-occurrence split (mock process `X` at one call site, run it real at another). Accepted:
  the explicit list keeps mocking declarative, and the per-occurrence case was not worth a stub-then-
  override escape hatch.
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

