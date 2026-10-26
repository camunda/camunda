# Java client and CPT support for reserved jobs and stubbed call activities

Companion to `RESERVED-JOBS-PLAN.md` (engine + REST for `RESERVE_JOBS`) and
`STUBBED-CALL-ACTIVITIES-PLAN.md` (engine + REST for `stubCallActivities`).
Both of those are implemented on `feat/exclusive-job-dispatch-lease`. This plan
covers what is still missing above them: the Java client cannot set either
switch, and Camunda Process Test (CPT) can neither record nor replay a test that
uses them.

`TEST-STUDIO-MOCK-ARCHITECTURE.html` is the design write-up this plan implements;
its "CPT Compatibility" tab holds the ordered roadmap. Sections of that document
that this plan changes are listed under [Document updates](#document-updates).

## Why

Test Studio will execute recorded tests through CPT. That makes CPT a consumer of
both switches rather than a bystander:

- A recording made with either switch on cannot be replayed at all today. There
  is no fluent API for `reserveJobs`, `stubCallActivities`,
  `jobReservationToken`, `runCalledProcess` or job release, and no JSON
  instruction for a released job or a stubbed call activity. `runCalledProcess`
  and release have no fallback in the four existing `MOCK_*` instructions, so
  this is missing in **every** runtime mode — `MANAGED`, `SHARED` and `REMOTE`
  alike, not only against a live cluster.
- Against a live cluster (`REMOTE`) CPT's own mocks are the thing the switches
  isolate against: `mockJobWorker` opens a real worker and `mockChildProcess`
  deploys a cluster-wide stub definition. A reserved job is hidden from CPT's own
  mock worker too, so a test case that mixes the two starves and hangs until the
  await timeout with no diagnostic.

## Scope

In scope:

1. `/jobs/{jobKey}/release` into the `rest-api.yaml` bundle — it blocks 2.
2. Java client: both create-time switches, the reservation token on the job
   commands, `forCallActivity().runCalledProcess()`, and a release command.
3. CPT JSON test-case format: the two create-block switches and the new
   instruction types.
4. CPT runtime: instruction handlers, the public context API they call, the
   token session, conflict detection and teardown.

Out of scope:

- Engine and REST behavior — already on the branch.
- The Test Studio UI. It lives in `camunda-hub`, a different repository.
- gRPC and `gateway.proto`. Both switches stay REST-only (a deliberate carry-over
  from the two engine plans); see [gRPC](#grpc-and-the-dual-transport-commands).
- Deprecating `MOCK_CHILD_PROCESS` / `MOCK_JOB_WORKER_*`. The new instructions are
  added **alongside** them and the existing ones keep behaving exactly as they do
  today. Re-pointing a published instruction is a breaking change.
- Whether `CLUSTER_PURGE` should be refused outside `MANAGED` mode. Real, and
  bigger than this work; tracked separately.
- An occurrence index on `JobSelector`. It carries job type, element id and
  process definition id only, so when two jobs are live on one element id
  `findJob` picks arbitrarily. Every new instruction here is keyed by
  `jobSelector`, and a multi-instance call activity produces exactly that — two
  concurrent stub jobs on one element id. Consequence, deferred knowingly: stub
  instructions replay non-deterministically on multi-instance and parallel-branch
  call activities. The architecture document names this as biting both recording
  and replay; fixing it is a change to the selector format for all instructions,
  not just the new ones.

## Decisions taken up front

**Recording carries no token.** The stored JSON holds the user's intent keyed by
element id. The token is a runtime value minted at replay. This is what the
two-layer recording in the architecture document describes, and it is why the
create block gets booleans rather than a token field.

**One token per test-case run, not per instance.** The architecture document says
"one fresh token per instance". CPT mints one UUID per test-case run and uses it
for every instance that run reserves. It still satisfies the property that
motivated per-instance tokens — two runs on one cluster never collide — and CPT
finds jobs by selector rather than by instance, so a per-instance token would
have to be resolved back from the job before every command. Revisit if a single
test case ever needs two independently reserved instances.

**The switches are booleans on `CreateProcessInstanceInstruction`, not runtime
instructions.** On the REST side `RESERVE_JOBS` rides in `runtimeInstructions`
because that list is the request's extension point, and it carries the token.
The recording has no token to carry, so a `RESERVE_JOBS` runtime-instruction
subtype in the JSON format would be an empty object whose only content is its own
type. Two booleans next to `variables` read better and match the document's
layer-1 example.

**Stub actions are their own element-selected instructions.** A stub job is an
ordinary job, but reusing `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` to drive it
would force a way to say _which_ job of the element is meant: element ids are
unique per definition, but a call activity can carry execution listeners
(`ExecutionListenerTest.java:309-312`) whose jobs share that element id and are
hidden by reservation too. Rather than add a `callActivityStub` flag to
`JobSelector` — whose meaning would silently depend on the create-time switch —
the three actions become `STUB_CALL_ACTIVITY_COMPLETE`,
`STUB_CALL_ACTIVITY_THROW_ERROR` and `RUN_CALLED_PROCESS`, each taking an
`elementSelector`. The selector factory resolves the element to job type
`io.camunda.zeebe:callActivityStub` at that element id, keeping that engine
constant out of the stored recording.

**Instruction names follow the existing `SCREAMING_SNAKE` convention**
(`RELEASE_JOB`, not `ReleaseJob`), matching every constant in
`TestCaseInstructionType`.

## Step 1 — REST bundle

`POST /jobs/{jobKey}/release` exists in `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml:244`
and has a hand-written controller mapping, but it is **not** referenced from
`rest-api.yaml` (the jobs block is `rest-api.yaml:293-306`). The OpenAPI generator
reads the bundle, so no request model is generated for it — confirmed: the
generated sources hold `ProcessInstanceCreationReserveJobsInstruction`,
`JobResultCallActivity` and `JobCompletionRequest.jobReservationToken`, but
nothing named `JobRelease*`.

The operation itself is complete — it already has a `requestBody` bound to a
`JobReleaseRequest` schema in the same file — so this is a one-line reference,
not a schema to write.

- Add `/jobs/{jobKey}/release` → `jobs.yaml#/paths/~1jobs~1{jobKey}~1release` to
  `rest-api.yaml`, in jobs order.
- Regenerate and confirm `JobReleaseRequest` appears under
  `clients/java/target/generated-sources/`.

This blocks step 2: the release command has nothing to serialize until it exists.

## Step 2 — Java client

### New public API

|               Command               |                                   Addition                                    |
|-------------------------------------|-------------------------------------------------------------------------------|
| `CreateProcessInstanceCommandStep1` | `reserveJobs(String token)`, `stubCallActivities(boolean)`                    |
| `CompleteJobCommandStep1`           | `withJobReservationToken(String)`                                             |
| `FailJobCommandStep1`               | `withJobReservationToken(String)`                                             |
| `ThrowErrorCommandStep1`            | `withJobReservationToken(String)`                                             |
| `UpdateJobCommandStep1`             | `withJobReservationToken(String)`                                             |
| `CompleteJobCommandJobResultStep`   | `forCallActivity()` → `CompleteCallActivityJobResultStep1.runCalledProcess()` |
| `CamundaClient`                     | `newReleaseJobCommand(long jobKey)`                                           |

`withJobReservationToken` mirrors `withJobLeaseToken` exactly — including the
null-tolerant early return — and is deliberately a **separate** wire property, not
a second meaning for the lease token. `forCallActivity()` is the third
`JobResult` variant alongside `forUserTask()` and `forAdHocSubProcess()`;
`JobResultCallActivity` is already generated.

There is deliberately no `newReleaseJobCommand(ActivatedJob)` overload, unlike
every other job command: a reserved job is never activated — `JobBatchCollector`
skips it and `publishWork` early-returns — so no caller can hold an
`ActivatedJob` for a job release applies to.

`ReleaseJobCommandStep1` follows `UpdateJobCommandImpl`, which is the repo's
precedent for a REST-only command: no gRPC stub in the constructor, `send()` goes
straight to `httpClient`. It exposes `withJobReservationToken(String)` — the token
is the whole request body — and returns a void-shaped response (204).

### gRPC and the dual-transport commands

Every setter on `CreateProcessInstanceCommandImpl` and `CompleteJobCommandImpl`
today writes both `grpcRequestObjectBuilder` and `httpRequestObject`.
`gateway.proto` has no counterpart for any of the new fields and this work does
not add one.

There is no existing precedent in `clients/java` for a REST-only _field_ on a
dual-transport command (the grep for a `useRest`-conditional throw finds nothing),
so this is a call to make rather than a pattern to copy: **the new setters write
only `httpRequestObject`, and `send()` throws `UnsupportedOperationException`
naming the field when `useRest == false` and one of them was set.** REST is the
client default (`DEFAULT_PREFER_REST_OVER_GRPC = true`), so this only fires for a
caller who opted into gRPC and asked for a REST-only feature — failing there is
better than silently dropping the switch and handing the job to a real worker.

`newReleaseJobCommand` needs no such guard: it has no gRPC form at all, like
`newUpdateJobCommand`.

### Files

Derive the exact file set from the two precedent commits rather than from this
list — `git log --follow -p` on `CompleteJobCommandStep1.java` (for
`withJobLeaseToken`) and on `CreateProcessInstanceCommandStep1.java` (for
`terminateAfterElement`) enumerates every file a new fluent method touches,
including the client test and any revapi entry.

- `api/command/` — the interfaces above, plus new
  `CompleteCallActivityJobResultStep1`.
- `api/command/ReleaseJobCommandStep1` and `api/response/ReleaseJobResponse`.
- `impl/command/` — the matching impls, plus `ReleaseJobCommandImpl`.
- `CamundaClient` / `CamundaClientImpl` — the two `newReleaseJobCommand`
  overloads.
- `clients/java/revapi.json` — only if the precedent commits needed an entry;
  method additions to an interface may already be covered.

**Ask the engineer before merging:** AGENTS.md lists changing public API contracts
under _Ask first_, and this adds public client API — the whole fluent surface
above.

## Step 3 — CPT JSON test-case format

Module `testing/camunda-process-test-json-test-cases`. Every instruction is an
Immutables interface plus a `schema.json` entry plus a `TestCaseInstructionType`
constant.

### Create block

Two optional booleans on `CreateProcessInstanceInstruction`, both defaulting to
`false`:

```json
{
  "type": "CREATE_PROCESS_INSTANCE",
  "processDefinitionSelector": { "processDefinitionId": "order-fulfillment" },
  "variables": { "orderId": "A-1001" },
  "stubCallActivities": true,
  "reserveJobs": true
}
```

### New instruction types

|               Type               |      Payload      |                          Replays as                           |
|----------------------------------|-------------------|---------------------------------------------------------------|
| `RELEASE_JOB`                    | `jobSelector`     | `newReleaseJobCommand(key).withJobReservationToken(t)`        |
| `STUB_CALL_ACTIVITY_COMPLETE`    | `elementSelector` | `newCompleteCommand` on the stub job                          |
| `STUB_CALL_ACTIVITY_THROW_ERROR` | `elementSelector` | `newThrowErrorCommand` on the stub job                        |
| `RUN_CALLED_PROCESS`             | `elementSelector` | `newCompleteCommand` + `forCallActivity().runCalledProcess()` |

The three call-activity instructions select the element, not the job: a call
activity is a BPMN element, and naming the instruction after what it does to the
call activity keeps the three symmetric. Each resolves the element to the job the
stub waits on — job type `io.camunda.zeebe:callActivityStub` at that element id —
so the caller never names that job, and there is no selector flag whose meaning
depends on a create-time switch. `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` stay
job-only and no longer carry any call-activity concept.

The format still has no fail instruction, and this work does not add one. CPT's
only `newFailCommand` is the internal escape hatch in
`JobWorkerMockImpl.java:47-61`, so recording a _failed_ job is not replayable —
but that is true of any job, reserved or not, and is a gap of its own rather than
something these two switches introduce.

### Also touched

- `schema.json` — one entry per type, and the two booleans.
- `src/test/resources/full-test-cases.json` — an example of each; this is what
  `SchemaValidationTest`, `DeserializationTest` and `TestCasesReaderTest` read.
- `PojoCompatibilityTest` — walks the model, so new interfaces must satisfy it.
- `revapi.json` in that module, if the additions trip it.

## Step 4 — CPT runtime

Module `testing/camunda-process-test-java`.

### Token session

The token is per-run state, and the handlers all reach the client through
`CamundaProcessTestContext`, so that is where it lives. Add to
`CamundaProcessTestContextImpl` a nullable reservation token, minted on the first
create instruction that sets `reserveJobs`, cleared at teardown. Every job command
the context issues attaches it when set — which means threading it through
`doCompleteJob`, `completeJobOfUserTaskListener`, the throw-error path and the new
release/fail paths. A hand-written CPT test that never reserves is unaffected:
the token is null and no command carries one.

### Public context API

`CamundaProcessTestContext` gains, mirroring the existing `completeJob` /
`throwBpmnErrorFromJob` overload style:

- `releaseJob(JobSelector)`
- `runCalledProcess(JobSelector)`

All of them resolve the job through the existing `awaitJob` path, so eventual
consistency is handled the same way as every other mock. Nothing is needed for
standing in for a called process: that is `completeJob`, which already exists.

### Handlers

One per new instruction in `impl/testCases/instructions/`, each registered in
`TestCaseInstructionHandlerRegistry`. They are thin: build the selector with
`InstructionSelectorFactory.buildJobSelector`, call the context method.
`CreateProcessInstanceInstructionHandler` reads the two booleans, mints or reuses
the run's token, and sets `reserveJobs(token)` / `stubCallActivities(true)` on the
create command.

### Conflict detection

A reserved job is hidden from CPT's own mock worker, so a test case that both
reserves and registers `MOCK_JOB_WORKER_COMPLETE_JOB` /
`MOCK_JOB_WORKER_THROW_BPMN_ERROR` starves: the run hangs until the await timeout
with nothing pointing at the cause.

Do not reject the run up front. `reserveJobs` is per create instruction, so a test
case may legitimately create one reserved instance and one unreserved instance
whose jobs the mock worker serves — an up-front rejection is a false positive
there. Instead: log a warning at parse time when both appear, and name the
combination in the await-timeout failure message, pointing at the `COMPLETE_JOB` /
`THROW_BPMN_ERROR_FROM_JOB` form. That cannot block a valid case and still puts the cause in
front of whoever reads the failure.

The other known-bad combination — `reserveJobs` plus `COMPLETE_USER_TASK` on a
task that has listeners — parks on listener jobs nobody authored. That one is a
warning in the failure message rather than an up-front rejection, because whether
a task has listeners is not knowable from the test case alone.

### Teardown

A reserved job and a stubbed call activity have no TTL: if a test fails midway the
instance stays parked forever, which matters on a shared or remote cluster.
`CamundaProcessTestExtension` cancels the instances the run created whenever
either switch was used, in the failure path as well as the success path.

## As built

Every step above landed. Where the implementation departed from the plan:

- **`ReleaseJobCommandStep1` is a two-step command.** `withJobReservationToken`
  returns the final step, because the token is the entire request body and a
  release without one is never valid.
- **The reservation token is set after `retries` / `errorCode`, not before.**
  `withJobLeaseToken` lives on the _second_ step of the fail and throw-error
  commands, so its sibling does too; the call order in a chain follows from that.
- **A null token is not passed to the command at all.** The context sets
  `withJobReservationToken` only when this run reserved something, rather than
  passing `null` for the command to ignore. Passing `null` worked but made every
  existing mock-based test in `camunda-process-test-java` observe a call it had
  not stubbed.
- **Three call-activity instructions, element-selected.** The architecture
  document listed `STUB_JOB_COMPLETE`, `STUB_JOB_FAIL`, `STUB_JOB_THROW_ERROR`
  and `STUB_JOB_RUN_CALLED_PROCESS`. The shipped set is
  `STUB_CALL_ACTIVITY_COMPLETE`, `STUB_CALL_ACTIVITY_THROW_ERROR` and
  `RUN_CALLED_PROCESS`, each taking an `elementSelector`. An earlier iteration
  reused `COMPLETE_JOB` / `THROW_BPMN_ERROR_FROM_JOB` with a `callActivityStub`
  flag on `JobSelector`; that flag only meant anything when the instance was
  created with `stubCallActivities`, an invisible coupling nothing in the schema
  expressed. Dedicated element-selected instructions drop the flag, keep the
  three actions symmetric, and distinguish this element-level stubbing from the
  definition-level `MOCK_CHILD_PROCESS`. `RUN_CALLED_PROCESS` keeps its name: it
  matches the wire field `runCalledProcess` and the client's
  `forCallActivity().runCalledProcess()`.
- **No `FAIL_JOB`.** An earlier draft added one, on the reasoning that a reserved
  service-task job can be failed and the format cannot record that. Dropped: the
  format could not record a failed job before these switches either, so it is a
  pre-existing gap and not part of this scope. The context therefore gains no
  public fail API either.
- **Only `releaseJob` and `runCalledProcess` are new on the context.** Driving a
  stub job otherwise goes through the `completeJob` and `throwBpmnErrorFromJob`
  methods that were already there.
- **The conflict check is a warning, not a rejection.** `reserveJobs` is per
  create instruction, so a test case may legitimately reserve one instance while
  a mock worker serves another; rejecting the run up front would be a false
  positive there. The runner logs the combination before the first
  instruction runs, and the await-timeout failure names it.
- **Only unit-level coverage shipped.** The plan called for a `MANAGED`-mode
  test case per new instruction. What shipped asserts each handler against a
  mocked `CamundaProcessTestContext` and a mocked `CamundaClient`: the wiring is
  checked, but no test drives a reserved job or a stub job on a running engine.
  The blocker is the branch — CPT `MANAGED` mode pulls
  `camunda/camunda:SNAPSHOT`, which does not contain these engine changes, so a
  `TestCasesIT` case would fail for the wrong reason. End-to-end coverage belongs
  in `qa/acceptance-tests`, which builds a cluster from source; it is still to
  do, and the architecture document says so where it claims the CPT problems are
  fixed.
- **Teardown cancels in the failure path too.** The cancel runs in a `finally`
  inside the runner's client block, since the case that leaves an instance parked
  is exactly the one that failed partway.
- **The two switches on `CREATE_PROCESS_INSTANCE` are recorded in
  `full-test-cases.json` as one new test case**, alongside every new instruction,
  so `SchemaValidationTest` covers the schema additions end to end.

`revapi` needed no entry: the additions are new methods on interfaces in
`io.camunda.client.api`, which its published configuration already tolerates.

## Verification

Baseline first — the memory note about polymorphic unions applies directly here,
and `-Dquickly` without `clean` leaves stale generated sources that hide a broken
model behind a green build:

```bash
./mvnw clean install -Dquickly -T1C
```

Then, per module:

```bash
./mvnw verify -pl clients/java -DskipTests=false -Dquickly -T1C
./mvnw verify -pl testing/camunda-process-test-json-test-cases -DskipTests=false -Dquickly -T1C
./mvnw verify -pl testing/camunda-process-test-java -DskipTests=false -Dquickly -T1C
```

New tests:

- Client: one per new fluent method asserting the serialized REST body, plus one
  asserting the gRPC path throws. Follow the existing command tests.
- Format: round-trip each new instruction through `full-test-cases.json`.
- Runtime: a handler test per new instruction, one for the create-block switches,
  one for the reserve + `MOCK_JOB_WORKER_*` detection, and one asserting an
  isolated instance is cancelled when the test case fails partway. End-to-end
  coverage against a running cluster is still open — see **As built**.

Before committing:

```bash
./mvnw license:format spotless:apply -T1C
```

## Commits

Split by the boundary the reviewer cares about, behavioral separate from
structural:

1. `build: add the job release endpoint to the REST API bundle`
2. `feat: let a client reserve a process instance's jobs and stub its call activities`
3. `feat: record a released job and a stubbed call activity in a test case` (format)
4. `feat: replay a released job and a stubbed call activity` (runtime)

## Document updates

`TEST-STUDIO-MOCK-ARCHITECTURE.html` needs these changes as the work lands:

- **"Suggested behaviour changes" roadmap, rows 3 and 4.** They read "re-point
  `mockChildProcess` at `stubCallActivities`" and "reserve the instance instead of
  opening a `mockJobWorker`". The correction card further down the same tab says
  the opposite and is the decision that holds: add the isolated instructions
  alongside, leave the published mocks untouched. Rewrite the two rows to match
  the card so the tab stops contradicting itself.
- **Instruction naming and count.** Card 6 and the layer-2 example use
  `ReleaseJob` / `StubJobComplete` and list five new types. The shipped set is
  `RELEASE_JOB`, `STUB_CALL_ACTIVITY_COMPLETE`, `STUB_CALL_ACTIVITY_THROW_ERROR`
  and `RUN_CALLED_PROCESS` — the three call-activity actions take an
  `elementSelector`, not a job selector. Update both, and say why: the actions are
  named for what they do to the call activity, and dropping the `callActivityStub`
  flag removes a selector option whose meaning silently depended on a create-time
  switch.
- **"Java client · REST v2 · schema" blocked-banner.** Once step 2 lands the
  fluent API is no longer missing. Replace the banner with what shipped, and keep
  the gRPC gap as its own note.
- **One token per run.** Add it to the same card: the recording holds no token,
  and CPT mints one per run rather than per instance.
- **"Limitations & Verdict", CPT row.** It says CPT support is a planned
  follow-up. Narrow it to what is still open after this plan — the purge decision
  and the round-trip breaks (eager N-shot rules, DMN and script tasks, listener
  jobs, no occurrence index on `JobSelector`), none of which this work closes.

