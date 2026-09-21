# Stubbed call activities — implementation plan

Status: local spike, implemented and tested on branch `feat/exclusive-job-dispatch-lease`.
Not pushed, not reviewed. Changes BPMN execution semantics and a public API contract, so it needs
sign-off and most likely an ADR before it becomes a PR.

The call-activity half of the problem `RESERVED-JOBS-PLAN.md` describes, and independent of it: a
recorded process
that contains a call activity would otherwise have the engine start the real called process, while
the recorded test wants CPT's `mockChildProcess(childId, vars)`. CPT itself mocks a child process by
**deploying a stub definition**, which is global and would hijack every other instance on a shared
recording cluster - the same objection as turning the cluster's workers off.

Creating an instance with **`stubCallActivities`** makes each of its call activities activate, start
no child instance, and wait on a job that stands in for the called process. The recorder then
chooses per call activity instance:

```
POST /v2/process-instances
{ "processDefinitionId": "my-process", "stubCallActivities": true }

POST /v2/jobs/{jobKey}/completion
{ "variables": { "amount": 42 } }                                      # stand in for it
{ "result": { "type": "callActivity", "runCalledProcess": true } }     # run the real process
```

## What changed

|         Concern          |                                                                                                                    Where                                                                                                                    |
|--------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Flag on the instance     | `ProcessInstanceCreationRecord` / `ProcessInstanceRecord` (`stubCallActivities`), read at the **root** instance, exactly as the reservation token is                                                                                        |
| Activate without a child | `CallActivityProcessor.finalizeActivation` splits two ways; the stub path resolves the process id, subscribes to events, activates, and creates the stub job. The definition is never looked up, so the called process need not be deployed |
| The stub job             | `BpmnJobBehavior.createStubCallActivityJob` - job type `io.camunda.zeebe.protocol.Protocol.CALL_ACTIVITY_STUB_JOB_TYPE`, retries and priority set explicitly, resolved process id / binding type / version tag as custom headers            |
| Hidden from workers      | `JobBatchCollector` and `BpmnJobActivationBehavior.publishWork` skip `jobKind == BPMN_ELEMENT && elementType == CALL_ACTIVITY` (`JobRecord.isCallActivityStub()`), counted as `JobAction.SKIPPED_CALL_ACTIVITY_STUB`                        |
| Completion               | A branch in `JobCompleteProcessor.postCompleteActions` that replicates the propagate gate of `ProcessInstanceElementCompletedV2Applier` before writing the event trigger, then appends `COMPLETE_ELEMENT`                                   |
| Escape hatch             | `JobResultType.CALL_ACTIVITY` with `runCalledProcess`, which appends the new internal `ProcessInstanceIntent.START_CALLED_PROCESS` instead; `CallActivityProcessor.onStartCalledProcess` runs the deferred child creation                   |
| Incident resolution      | `IncidentResolveProcessor` retries `START_CALLED_PROCESS` for an activated call activity, so an undeployed called process raises a resolvable incident instead of banning the instance                                                      |
| Termination              | `onTerminate` now cancels the element's job unconditionally; `terminateChildProcessInstance` already tolerated a missing child                                                                                                              |
| Not releasable           | `JobReleaseProcessor` rejects releasing a stub job: no job worker could run it, so handing it back would park the call activity forever                                                                                                     |

Deliberate design points, in case a reviewer asks:

- **No new `JobKind`.** A new enum value ripples through every switch on `JobKind` across engine,
  protocol, gateway, exporters and webapps, including name-based paths (`JobKind.valueOf`,
  `JobKindEnum.fromValue`) that fail at runtime rather than at compile time.
- **Not keyed on the reservation token.** Synthesizing a token to reuse its hide would then demand
  that token back on every completion, rejecting completions for instances never given one. The two
  switches stay independent, so recording *through* a real child process is still possible.
- **The propagate gate is load-bearing, and lives in one place.** A real child writes the parent's
  event trigger only when the call activity has output mappings or propagates all child variables;
  a completed job always writes one. Without that condition, stubbing would start propagating
  variables the model says to drop. The condition is
  `ExecutableCallActivity.propagatesCalledProcessVariables()`, asked by both the applier for a real
  child's completion and the stub's completion, so the two cannot drift apart.
- **One place decides that a job is withheld from workers.**
  `JobWorkerDispatch.withheldFromWorkersReason(job)` returns the metric reason a job reaches no
  worker — stub, or reserved — and both hand-out paths (`JobBatchCollector` for a poll,
  `BpmnJobActivationBehavior.publishWork` for a push/notify) ask only that. `JobReleaseProcessor`
  deliberately asks the two reasons separately: a reserved job is released back to the workers,
  while a stub job has no worker to release it to.

## Tests

`zeebe/engine/.../processing/bpmn/container/StubbedCallActivityTest.java` - 20 tests: no child
instance is created, the call activity activates and waits, the stub job names the process it stands
in for, an undeployed called process is fine, a plain poll and a `withLease` poll and a job stream
all get nothing, completion completes the call activity, variables arrive through output mappings
and through propagate-all but **not** when the model propagates nothing, cancellation and an
interrupting boundary event both terminate it and cancel the job, `runCalledProcess` starts the real
process (whose own call activities are stubbed in turn) without leaking the stub job's variables, an
undeployed process raises a resolvable incident that continues the instance once deployed,
`runCalledProcess` is rejected for an ordinary job and for a cancelled instance's job, and the
stubbing survives log replay. `ReleasedJobsTest` gained one test: releasing a stub job is rejected.

`ActivateJobsTest.shouldActivateJobsUpToMaxMessageSize` needed an unrelated repair: it sized each
job's variables as `maxRecordSize / expectedJobsInBatch` exactly, leaving no room for the job's own
fields, so adding `JobResult.runCalledProcess` dropped the batch to a single job and failed the test
for a reason that has nothing to do with batch limiting. It now reserves 1 KiB per job, which makes
the test robust against the next field added to `JobRecord`.

## Open items before this could be a PR

These are in addition to the ones `RESERVED-JOBS-PLAN.md` lists, which apply here too.

1. **gRPC and the Java client** are untouched for both switches, for the same reason - and with
   the same caveat: that reason holds only for CPT's `MANAGED` and `SHARED` runtime modes. In
   `REMOTE` mode CPT replays against a live cluster, where `mockChildProcess`'s deployed stub is
   the very cluster-wide hijack this switch removes. Re-basing `mockChildProcess` on
   `stubCallActivities` is the natural follow-up once the client exposes it.
2. The same ADR and alpha-status decisions as the reserved-jobs work, and firmer here: this changes
   BPMN execution semantics, not only who may act on a job.

## Decisions taken on the stub job's inherited behavior

Both were side effects of the job form; both are now intended. Neither has a test yet — adding one
per decision is the remaining work here.

- **`THROW_ERROR` and `FAIL` are supported on the stub job.** `JobKind.BPMN_ELEMENT` allows both, so
  the caller can throw a BPMN error to drive the call activity's error boundary event — "the called
  process failed" — or fail the job into an incident. Test Studio needs exactly those two, because a
  recorded test asserts the failure paths of a called process as well as its result. Kept rather
  than blocked.
- **The stub job is exported and searchable.** It appears in `/v2/jobs/search` and as a job-based
  wait state in Operate. This is the mechanism Test Studio uses to find what is waiting for a
  decision, and it is the same list the reserved jobs appear in, so a recording session has one
  inbox rather than two. Visibility is intended, not incidental.

