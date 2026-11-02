# Recording and replaying isolated process tests in Camunda Process Test

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines how Camunda Process Test (CPT) and the Java client consume the per-instance
recording switches — how a reservation token is scoped, how a replayed mutation addresses its instance,
and how the new instructions relate to the existing mocks.

**Audience**: Engineers on the CPT library (`testing/`), the Java client (`clients/`), and the v2 REST
surface (`zeebe/gateway-protocol/`), and AI agents reasoning about CPT recording and replay.

## Context

The engine switches for [reserved jobs](../../zeebe/docs/adr/0016-811-reserved-jobs.md),
[stubbed call activities](../../zeebe/docs/adr/0017-811-stubbed-call-activities.md), and
[held timers](../../zeebe/docs/adr/0018-811-held-timers.md), plus
[run-scope isolation](../../zeebe/docs/adr/0014-811-tag-scoped-run-isolation.md), let a test drive one
instance on a shared cluster without colliding with concurrent runs. CPT is their consumer: it records
a test by driving an instance by hand and replays it by re-issuing the recorded actions. This ADR
covers the decisions that span the client, the JSON test-case format, and the CPT runtime; it depends
on the engine ADRs rather than restating them.

## Decision

**D1. One reservation token per test-case run, not per instance.** CPT mints one token per run and uses
it for every instance that run reserves. This still guarantees that two runs on one cluster never
collide, and it avoids resolving a per-instance token back from each job before every command (CPT
finds jobs by selector, not by instance).

**D2. The recording stores no token; it is a runtime value minted at replay.** The stored test case
holds the user's intent keyed by element id. Because the recording carries no token, the create-block
switches are plain booleans rather than a token-bearing instruction.

**D3. A replayed mutation addresses its instance through a registry of what the run created, not a
selector.** A selector queries the cluster by process-definition id and takes the oldest match — correct
for an assertion, wrong for a mutation that must hit exactly one instance (it could pick an earlier test
case's instance, another user's, or a second instance the case created on purpose). The runtime records
every instance it creates and resolves triggers and teardown cancels from that registry.

**D4. Stub completion and error reuse the generic job instructions; only `RUN_CALLED_PROCESS` is
dedicated.** A stubbed call activity waits on an ordinary job carrying `JobKind.CALL_ACTIVITY_STUB`
(see [ADR 0017](../../zeebe/docs/adr/0017-811-stubbed-call-activities.md)), so standing in for the
called process is a `COMPLETE_JOB` and failing it a `THROW_BPMN_ERROR_FROM_JOB`, each selecting the
stand-in job by `{ jobKind: CALL_ACTIVITY_STUB, elementId }`. No stub-specific completion instruction is
added. `RUN_CALLED_PROCESS` stays dedicated because it starts the real child rather than completing a
job. Selecting by `jobKind` — a concrete, visible property of the job — avoids the invisible coupling of
a create-time boolean flag (see the rejected alternative). The existing cluster-wide `MOCK_*`
instructions remain unchanged beside these.

**D5. A reserve-plus-mock-worker conflict warns rather than rejects.** A reserved job is hidden from
CPT's own mock worker, so a case that both reserves and registers a `MOCK_JOB_WORKER_*` starves. CPT
warns at parse time and names the combination in the await-timeout message rather than rejecting the
run, because one case may legitimately reserve one instance while a mock worker serves another.

## Alternatives considered

- **A per-instance reservation token.** Matches the engine's "one token per instance" shape, but forces
  CPT to resolve each job's token back from the job before every command, for no isolation gain over one
  token per run. Rejected for the run-level token (D1).
- **Dedicated `STUB_CALL_ACTIVITY_COMPLETE` / `STUB_CALL_ACTIVITY_THROW_ERROR` instructions.** First
  shipped on the prototype, then dropped (D4): a stub is an ordinary job, so the generic job
  instructions complete or fail it once the stand-in job is selectable by `jobKind`. A separate
  `callActivityStub` *boolean flag* on `JobSelector` was also rejected — its meaning would depend
  invisibly on the create-time switch — but selecting by the job's own `CALL_ACTIVITY_STUB` kind carries
  no such coupling.

## Consequences

- Instances a run isolated are cancelled on teardown, in the failure path as well as success, because a
  parked instance has no engine-enforced lifetime
  ([ADR 0015](../../zeebe/docs/adr/0015-811-per-instance-recording-switches.md)).
- The feature is REST-only end to end, carried over from the engine ADRs: the client setters throw over
  gRPC, and the recording cannot be replayed over gRPC.
- End-to-end coverage against a running engine is open: CPT's managed mode pulls a released image that
  lacks these engine changes, so coverage is unit-level until an acceptance test builds a cluster from
  source.
- Multi-instance and parallel-branch call activities replay non-deterministically: `JobSelector` has no
  occurrence index, so two live jobs on one element id cannot be told apart. Deferred.
- DMN decision mocking is not addressed, and a public non-JUnit CPT runner API
  ([camunda/camunda#65574](https://github.com/camunda/camunda/issues/65574)) is a separate dependency.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [Hosted Camunda Process Test (product-hub #3546)](https://github.com/camunda/product-hub/issues/3546) (internal)
- [Public non-JUnit CPT runner API (camunda/camunda#65574)](https://github.com/camunda/camunda/issues/65574)

