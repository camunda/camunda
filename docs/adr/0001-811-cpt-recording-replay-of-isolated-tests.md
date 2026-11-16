# Recording and replaying isolated process tests in Camunda Process Test

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how Camunda Process Test (CPT) and the Java client consume the per-instance
recording switches — how a reservation token is scoped, how a replayed mutation addresses its
instance, and how isolated jobs and stubs are driven.

**Audience**: Engineers on the CPT library (`testing/`), the Java client (`clients/`), and the v2 REST
surface (`zeebe/gateway-protocol/`), and AI agents reasoning about CPT recording and replay.

## Context

The engine provides [reserved jobs](../../zeebe/docs/adr/0017-811-reserved-jobs.md),
[stubbed call activities](../../zeebe/docs/adr/0018-811-stubbed-call-activities.md), and a
[timer trigger-by-key API](../../zeebe/docs/adr/0019-811-timer-search-and-trigger-by-key.md) so a test
can drive one instance on a shared cluster ([ADR 0016](../../zeebe/docs/adr/0016-811-per-instance-recording-switches.md)).
CPT records a test by driving an instance by hand and replays it by re-issuing the recorded actions.
This ADR covers the decisions spanning the client, the JSON test-case format, and the CPT runtime.

## Decision

**D1. One reservation token per test-case run, not per instance.** CPT mints one token per run and
uses it for every instance the run reserves. Two runs on one cluster still never collide, and CPT
need not resolve a per-instance token back from each job it finds by selector.

**D2. A recording stores a placeholder, never a token.** Test cases write `${reservationToken}`
wherever a token goes (`RESERVE_JOBS`, and every job instruction acting on a reserved job); the runner
substitutes the run's token. A token named on a job instruction applies to that instruction only, so
it never leaks into a later instruction.

**D3. A replayed mutation addresses its instance through a registry of what the run created, not a
selector.** A selector takes the oldest instance matching a process-definition id — fine for an
assertion, wrong for a mutation that must hit exactly one instance (another test case's, another
user's, or a second instance on purpose). The runtime records every instance it creates and resolves
triggers and teardown cancels from that registry.

**D4. Isolated work is driven with the generic job instructions and mocks.** A reserved job is
completed or failed by `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` with the token, or by a
`MOCK_JOB_WORKER_*` that carries it. A stubbed call activity is stood in for by a `MOCK_JOB_WORKER_*`
with a `calledProcessId` (`mockCallActivity`), which drains every stub of that process — a
multi-instance call activity included — without a token; a single stub can also be completed by job
instruction, selected by `{ jobKind: CALL_ACTIVITY_STUB, elementId }`. No stub-specific instruction
exists. A recorded timer is fired with `TRIGGER_TIMER`.

**D5. A reserve-plus-mock conflict warns rather than rejects.** When a test case mocks a job type it
also reserves, CPT logs a warning, because a mock without the token never sees the reserved jobs and
the case would only fail on the await timeout. It does not reject, because one case may legitimately
reserve one instance while the mock serves another.

## Alternatives considered

- **A per-instance reservation token.** Matches the engine's shape, but forces CPT to resolve each
  job's token before every command, for no isolation gain over one token per run.
- **Dedicated stub instructions (`STUB_CALL_ACTIVITY_COMPLETE`, `RUN_CALLED_PROCESS`, …).** Dropped: a
  stub is an ordinary job, selectable by its kind or drained by called process, and running a child for
  real is expressed by leaving it off the stub list. A `callActivityStub` boolean on `JobSelector` was
  rejected too, as its meaning would depend invisibly on the create-time switch.

## Consequences

- Instances a run isolated are cancelled on teardown, on failure as well as success, because a parked
  instance has no engine-enforced lifetime.
- The feature is REST-only end to end: the client setters throw over gRPC, so a recording cannot be
  replayed over gRPC.
- End-to-end coverage against a running engine is open: CPT's managed mode pulls a released image that
  lacks these engine changes, so coverage is unit-level until an acceptance test builds a cluster from
  source.
- `JobSelector` has no occurrence index, so selector-based job instructions cannot tell apart two live
  jobs on one element id (multi-instance, parallel branches). Mock workers avoid this for stubs and
  reserved types. Deferred.
- DMN decision mocking is not addressed, and a public non-JUnit CPT runner API
  ([camunda/camunda#65574](https://github.com/camunda/camunda/issues/65574)) is a separate dependency.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [Hosted Camunda Process Test (product-hub #3546)](https://github.com/camunda/product-hub/issues/3546) (internal)

