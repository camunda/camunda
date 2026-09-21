# Reserved jobs — implementation plan

Status: local spike, implemented and tested on branch `feat/exclusive-job-dispatch-lease`.
Not pushed, not reviewed. Changes a public API contract, so it needs sign-off and most likely an
ADR before it becomes a PR.

The call-activity half of the same recording problem is a separate, independent switch, planned in
`STUBBED-CALL-ACTIVITIES-PLAN.md`.

## Problem

Test Studio records a test case that is compatible with Camunda Process Test (CPT) by driving a real
cluster: it creates a process instance, and after each step the user authors the CPT equivalent of
the action they just performed (`completeJob`, `throwBpmnError`, …) and appends it to a test file.

If a job worker on that cluster is subscribed to the job type, the engine hands it the job the
moment it is created and the worker completes it within milliseconds. The user never gets to author
the action, so the recorded test file silently lacks that instruction — and a test with a missing
step is worse than no test, because it still passes.

Turning the cluster's workers off works (`camunda.client.worker.defaults.enabled=false`, or the
`jobworkers` actuator endpoint per job type) but it is cluster-wide: every other instance on that
cluster stalls, so two people cannot record at the same time, and it only reaches workers started by
the Spring Boot starter. The isolation has to be per process instance.

## Solution

Creating a process instance with a **job reservation token** — a caller-chosen string the engine
stamps onto every job that instance creates — reserves those jobs. A reserved job:

- is handed to no job worker — not by polling, not by push, not even to a worker that opts into
  leases;
- is not announced to pollers at all;
- can only be completed, failed or error-thrown by a caller that supplies the matching token;
- stays in the ordinary `ACTIVATABLE` state, so completing it by job key works as usual.

Test Studio therefore drives the instance itself, while every worker on the cluster keeps serving
every other instance normally. Two people can record side by side on the same cluster, each with
their own token.

### Why this shape

The engine already has the two moving parts a reservation needs: it skips "leased" jobs when
assembling an activation (`JobBatchCollector`, `BpmnJobActivationBehavior`), and it fences job
commands against a token (`JobLeaseFencingCheck`). Reservation follows that same shape as its own
check in those same two places — a separate skip branch, and a `JobReservationFencingCheck` chained
ahead of the lease check — rather than new machinery, which is why the change is small.

The reservation token is deliberately its **own field**, separate from the existing `jobLeaseToken`.
A lease token is something the engine already uses for a different purpose: each time it hands a job
to a worker, it generates a brand-new token so it can tell which worker currently holds the job and
reject a stale hand-off from an earlier one. That "brand-new every time" is the whole point of a
lease, and a test (`ActivateJobsWithLeaseTest.shouldGenerateNewJobLeaseTokenOnReactivation`) guards
it. So the reservation cannot just be a lease token set up front: doing that would either break that
guarantee, or let any worker that understands leases replace Test Studio's token with its own and
take the job. A separate field keeps the reservation out of the lease's way entirely — on the stored job record,
on the wire, and in the engine check that reads it.

### Alternatives rejected

|                        Option                         |                                                                                                                         Why not                                                                                                                         |
|-------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Disable the cluster's job workers                     | Cluster-wide; blocks other people's instances; Spring-only                                                                                                                                                                                              |
| Process instance suspension (already shipped)         | Hides the jobs, but `JobCompleteProcessor.onSuspended()` returns `REJECT`, so a suspended instance's job cannot be completed. Gives pause, not step                                                                                                     |
| Per-instance job type (job type is a FEEL expression) | Zero engine change, but requires rewriting the BPMN; the model must be recorded as-is                                                                                                                                                                   |
| New marker column family + a withheld job state       | Airtight and visible in Operate, but every path that makes a job activatable again (timeout, retry backoff, incident resolution) must re-check the marker, as `JobTimeOutProcessor` already does for suspension. Roughly 3-5x the work; keep in reserve |

## What changed

### Protocol

- `ProcessInstanceCreationRuntimeInstruction` — new `jobReservationToken` string property
  (property count 2 → 3). The token travels as a `RESERVE_JOBS` runtime instruction, not as a
  field of its own on `ProcessInstanceCreationRecord`.
- `RuntimeInstructionType` — new `RESERVE_JOBS` constant.
- `ProcessInstanceRecord` — same property (19 → 20). Deliberately **not** copied in `wrap(...)`, so
  only the root element instance record carries it.
- `JobRecord` — same property (31 → 32), copied in `wrapWithoutVariables(...)` so it survives
  activation and re-dispatch.
- Value interfaces gained `default` getters, which keeps existing implementors compiling:
  `ProcessInstanceCreationRecordValue`, `ProcessInstanceRecordValue`, `JobRecordValue`.
- Golden files regenerated for the changed records (they are literal source copies — re-copy them
  after any `spotless:apply`).

### Engine

|                    Concern                    |                                                                                                Where                                                                                                |
|-----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Read the token off the creation command       | `ProcessInstanceCreationHelper.jobReservationTokenOf`, which picks the `RESERVE_JOBS` instruction out of `runtimeInstructions`                                                                      |
| Store the token on the instance at creation   | `ProcessInstanceCreationHelper.initProcessInstanceRecord`, beside `tags` / `businessId`; both creation processors pass the extracted token                                                          |
| Stamp it on every job                         | `BpmnJobBehavior.java:674`, reading the **root** instance via `getJobReservationTokenFromProcessInstance` (`:716`)                                                                                  |
| Hide it from polls                            | `JobBatchCollector.java:118` — skip before the lease check, count `JobAction.SKIPPED_RESERVED`                                                                                                      |
| Hide it from push, and skip notifying pollers | `BpmnJobActivationBehavior.java:164` — early return in `publishWork`                                                                                                                                |
| Fence its commands                            | `JobReservationFencingCheck.forCommand()`, chained ahead of the lease check in `JobCompleteProcessor`, `JobFailProcessor`, `JobThrowErrorProcessor`, `JobReleaseProcessor` and `JobUpdateBehaviour` |
| New metric label                              | `EngineMetricsDoc.JobAction.SKIPPED_RESERVED` → `"skipped reserved"`                                                                                                                                |

The root-instance lookup is what makes a call activity's child instance inherit the reservation:
`context.getProcessInstanceKey()` would be the child's own key, and the child's record never went
through `initProcessInstanceRecord`.

No new job state, no change to the activatable index, and no new re-dispatch edge cases: the token
lives on the job record, so timeout, retry backoff and incident resolution keep it automatically.

### API (REST v2 only)

`process-instances.yaml` — a new `RESERVE_JOBS` variant of the existing
`ProcessInstanceCreationRuntimeInstruction` union, marked alpha, `minLength: 1`, `maxLength: 128`:

```
POST /v2/process-instances
{
  "processDefinitionId": "my-process",
  "runtimeInstructions": [
    { "type": "RESERVE_JOBS", "jobReservationToken": "recorder-session-1" }
  ]
}
```

`runtimeInstructions` is the request's existing extension point for "instructions that affect the
runtime behavior of the process instance", and it already carries the alpha
`TERMINATE_PROCESS_INSTANCE` variant. Putting the token there instead of in a top-level field of
its own keeps every recording switch in one place, and it costs nothing today: both fields are
unreleased, so there is no migration.

`stubCallActivities` deliberately stays a top-level boolean. An instruction object with no
properties of its own would be a boolean in a costume, and an instruction type carries a payload by
construction — the reserve-jobs token is one, a stub-everything switch is not.

Plumbed through `ProcessInstanceMapper.toRuntimeInstruction` → `ProcessInstanceCreateRequest`
→ `BrokerCreateProcessInstanceRequest` (and the with-result variant), with
`ProcessInstanceRequestValidator.validateRuntimeInstructions` rejecting blank and over-long tokens
and more than one `RESERVE_JOBS` instruction.

Three consequences of living in that list, all handled:

- `validateRuntimeInstructionAfterElementsExist` mapped every instruction to its `afterElementId`
  and rejected ids the process does not contain. A `RESERVE_JOBS` instruction carries none, so the
  check is now scoped to `TERMINATE_PROCESS_INSTANCE`. `ReservedJobsTest` pins this.
- `ProcessInstanceCreationCreatedV2Applier` stores runtime instructions in a side state keyed by
  `processInstanceKey`, read per element transition by `afterElementId`. Only element-triggered
  instructions are written there now: a `RESERVE_JOBS` instruction would never match a lookup, so
  there is no reason to copy its token into that state.
- **The union had only one variant, so adding a second changed generated types.** This is the
  first two-variant `x-polymorphic-schema` in this spec to be consumed by hand-written mapping
  code, and the three generated model flavors do not agree on the shape:

|           Flavor           |                                 What `getRuntimeInstructions()` returns                                 |                                                Consumer                                                 |
|----------------------------|---------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `...protocol.model`        | The parent, rewritten by `DiscriminatorModelPostProcessor` into an **interface** the variants implement | `ProcessInstanceMapper` — now a pattern-matching switch, replacing a hard cast to the terminate variant |
| `...protocol.model.simple` | A **fat class** merging every variant's fields, which the variants do **not** implement                 | `SimpleRequestMapper` — builds and reads the union class itself and branches on `getType()`             |
| `...client.protocol.rest`  | An interface, as in the main flavor                                                                     | `CreateProcessInstanceRestTest` — declares the union type and casts to read the variant                 |

`-Dquickly` without `clean` leaves stale files under `target/generated-sources`, so the old
signatures still link and a full-repo `test-compile` passes green while these breaks hide. Verify
a union change with `./mvnw clean install -Dquickly` and a clean `test-compile`.

Every command on a reserved job carries the token back in its own `jobReservationToken` property,
added to the completion, fail, throw-error and update requests alongside the existing
`jobLeaseToken`:

```
POST /v2/jobs/{jobKey}/completion
{ "variables": {}, "jobReservationToken": "recorder-session-1" }
```

The two are separate properties because they are separate contracts. `JobLeaseToken`
(`identifiers.yaml`) is engine-minted, fresh per activation, and its schema says a client never
creates one; a reservation token is caller-chosen and lives as long as the instance. Sharing the
wire field would have made that schema false — and the storage was already split, so the wire was
the only half still pretending they were one thing. The new `JobReservationToken` schema is the
shared definition for both halves of the feature: the create instruction, the four job commands,
and the release request all point at it.

The engine can afford two fields on one record because it can never need both at once: a reserved
job is skipped in `JobBatchCollector` and `publishWork` before either lease-minting site
(`JobBatchCollector:163`, `BpmnJobActivationBehavior:372`) is reached, so a job is never leased and
reserved at the same time. Sending a stray `jobLeaseToken` alongside the reservation token is
therefore harmless and is not rejected: the stored lease token is empty, so the lease check passes
on its own.

`maxLength: 128` is a chosen cap, not one derived from a column width — nothing persists the token
in secondary storage (the only `JOB_LEASE_TOKEN` column belongs to `AGENT_HISTORY`). It bounds what
gets copied onto every job record of a reserved instance.

Rejections are `409 INVALID_STATE`: _"a matching reservation token must be provided because the
job's process instance was created with one"_ when absent, and _"the supplied reservation token does
not match the one its process instance was created with"_ when wrong.

The token is caller-chosen, so it should be unique per recording session — not to keep it secret,
but so two recordings on the same cluster never pick the same token and step on each other. It is
not exposed by the job search API (only `ActivatedJobResult` ever returned a token), so the recorder
keeps the one it generated.

## Tests

- `zeebe/engine/.../processing/job/ReservedJobsTest.java` — 13 tests: the token is stamped (and not
  stamped without one), a plain poll and a `withLease` poll both return nothing, a stream is not
  pushed to, completion succeeds with the token and is rejected without it and with a wrong one, the
  job stays hidden after failing with retries and after a retry backoff recurs it, a child
  instance's job inherits the token, and the reservation survives log replay.
- `ProcessInstanceMapperTest` — 3 tests covering the REST mapping for both create variants.
- `EngineMetricsDocTest`, `JsonSerializableToJsonTest`, `RecordGoldenFilesTest` updated for the new
  property and metric label.

Verified green: all 402 tests under `zeebe/engine/.../processing/job`, the `zeebe/protocol`,
`protocol-impl` and `protocol-jackson` suites, and a full `./mvnw install -Dquickly -T1C`.

Manually verified over REST against a local broker: a real worker completes a plain instance's job
in milliseconds and never touches a reserved one; `409` without or with a wrong token; `204` and the
instance completes with the right one.

## Open items before this could be a PR

1. **Where the token lives.** The token sits on `ProcessInstanceRecord`, so it ends up in exported
   records and the webapps schema. Not a security risk — it is an isolation label, not a secret, and
   a reserved job is never handed to a worker. It is just untidy to carry a per-session token
   through exported data; a side state keyed by `processInstanceKey` (see
   `state/suspension/DbSuspensionState.java`) would keep it engine-only if we ever want that. Not a
   blocker for a PR.
2. **Caller-chosen vs engine-minted token.** A caller-chosen token keeps the spike simple and fits
   Test Studio's flow. Letting the engine create the token instead (`exclusiveJobDispatch: true`,
   token returned in the response) is the cleaner public API, and it matches the documented rule
   that only the engine creates a job token.
3. **gRPC and the Java client are not changed, so the whole feature is REST-only.** Test Studio
   does not need them (it is a UI that talks REST), and recorded CPT tests run against a clean
   engine with no other workers, so they need no token. Since `jobReservationToken` is on neither
   the gRPC create-instance request nor its job commands, a reserved instance cannot be created or
   driven over gRPC at all. Adding a `reserveJobs(token)` client step and a gRPC `oneof` variant is
   extra work if we ever want it.

   The clean-engine argument only covers CPT's `MANAGED` and `SHARED` runtime modes. In `REMOTE`
   mode CPT runs against a live cluster, where competing workers are exactly the problem this
   switch solves - and CPT drives everything through `CamundaClient`, so it cannot set the switch
   or carry the token at all. Test Studio is expected to execute recorded tests through CPT, which
   turns this from a deferred nicety into a follow-up: the client work above, plus re-basing CPT's
   own mocks, which are job workers and would be starved by the reservation. Note also that
   `POST /jobs/{jobKey}/release` is absent from the `rest-api.yaml` bundle the client generator
   reads, so it would not appear in a generated client even once the rest is wired up.

4. **Reserved jobs cannot be updated without the token**, unlike leased ones. `JobLeaseFencingCheck`
   deliberately lets an operator update a leased job's retries or timeout with no token; the
   reservation check does not, because a reservation is exclusivity rather than staleness fencing —
   while the instance is reserved, only its creator may touch its jobs. `PATCH /v2/jobs/{jobKey}`
   carries the token, so the recorder can still update its own jobs. The update-retries and
   update-timeout commands and job batch operations share `JobUpdateBehaviour` but have no field to
   carry a token, so for a reserved job they are blocked outright with no way through — acceptable
   for a recording session, but it is a real restriction rather than a gate. Pinned by
   `shouldRejectUpdateOfReservedJobWithoutToken` and `shouldUpdateReservedJobWithMatchingToken`.

5. An ADR, docs, and a decision on whether the instruction stays alpha for its first release.

## Connectors

No new mechanism is needed for **outbound** connectors, and none is possible for **inbound** ones.

### Outbound connectors are already covered

An outbound connector is a service task whose job type is the connector's id
(`io.camunda:http-json:1`, …), executed by a connector-runtime job worker. The engine has no notion
of a connector at all — only a job type string. The reservation token therefore already hides those
jobs, and nothing about the connector runtime escapes it:

- **Polling** — gRPC `ActivateJobs` and REST `/v2/jobs/activation` both land on
  `JobBatchIntent.ACTIVATE`, handled by `JobBatchActivateProcessor` → `JobBatchCollector`, where the
  skip lives. There is no activation path that bypasses it.
- **Streaming** — `BpmnJobActivationBehavior.publishWork` returns early, so a job-streaming
  connector runtime is never pushed to and pollers are not even notified.

No stubbed-call-activity analogue is needed: a call activity required a second mechanism because the
engine itself starts the child instance, whereas for a connector the engine only creates a job and
waits, so reservation alone parks it.

Stubbing one means completing its job with the variables the _downstream elements_ should see — the
post-mapping result, not the connector's raw response payload. The connector's `resultVariable` /
`resultExpression` are applied by the connector runtime, which never runs for a reserved job.

Driving such a job is REST-only, in both directions: `jobReservationToken` was added neither to the
gRPC `CreateProcessInstanceRequest` nor to its job commands, so an instance cannot be reserved over
gRPC and a reserved job cannot be completed over it either.

### Reservation is coarser than "disable all connectors"

It is per instance, but within that instance it hides **every** job — connector jobs, ordinary
service task jobs, execution listener and task listener jobs. There is no
connector-only variant and no cheap way to add one: selecting by an `io.camunda:` job-type prefix
would miss custom connectors and any element template that sets an arbitrary job type. For a
recorder this is the right default — it must author an action for every job either way.

### Releasing a reserved job

Reservation on its own only offers one choice — stand in for the work. `POST
/v2/jobs/{jobKey}/release` adds the other one, the counterpart of `runCalledProcess`: drop the
reservation and hand the job to the workers, so the connector runtime that would have served it
runs it for real.

```
POST /v2/jobs/{jobKey}/release
{ "jobReservationToken": "recorder-session-1" }
```

Releasing is a job **command**, not a `JobResult`. A call activity's stub job stands in for an
engine action that never happened, which is why completing it can tell the engine to go do it. A
connector job has no deferred action: the engine created it and is waiting, and only the
reservation keeps the worker away. So the job is not completed — it stays `ACTIVATABLE` and the
worker that takes it completes it as usual.

|     Concern      |                                                                                                                       Where                                                                                                                       |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| New intents      | `JobIntent.RELEASE` (25, `shouldBanInstance` false) / `RELEASED` (26)                                                                                                                                                                             |
| Drop the hide    | `JobReleasedApplier` — `updateJobRecord` with the token cleared. A state event, not a side effect: the hide reads the token off the stored record, so replay would otherwise restore the reservation                                              |
| Hand the job out | `JobReleaseProcessor.release` calls `BpmnJobActivationBehavior.publishWork`, as `ProcessInstanceResumeJobsProcessor:169` does for a resumed instance's jobs — a job stream is push-only, so a stream worker learns of the job only from that call |
| Fence it         | The same `JobReservationFencingCheck.forCommand()` every other command on a reserved job goes through, so holding the job key is not enough to un-reserve someone else's recording                                                                |
| Reject the rest  | A job that was never reserved, and any state other than `ACTIVATABLE`                                                                                                                                                                             |
| API              | `jobs.yaml` — `/jobs/{jobKey}/release`, alpha, `jobReservationToken` required → `RequestMapper.toJobReleaseRequest` → `JobServices.releaseJob` → `BrokerReleaseJobRequest`                                                                        |

The release is one-way: the token is gone from the job record, so a timeout or a retry backoff
keeps the job released, and the recorder cannot re-reserve it. Making it revocable is a different
and much larger design.

`ReleasedJobsTest` — 10 tests: the token is cleared, a poll and a stream both get the released job,
a worker completes it without a token, release is rejected without the token, with a wrong one, for
an unreserved job and for a completed job, the release survives a retry backoff, and it survives log
replay. `JobControllerTest` gained the two REST mapping tests.

### Open items for the release command

1. **Permission.** It carries `UPDATE_PROCESS_INSTANCE` on `PROCESS_DEFINITION`, copied from
   complete. Defensible, but release un-fences the job for _everyone_, which complete does not, so
   whether it deserves its own permission is a decision rather than an inheritance.
2. **REST only**, for the same reason as both switches it completes: `jobReservationToken` is on no
   gRPC request, so an instance cannot be reserved over gRPC in the first place.
3. The same ADR and alpha-status decisions as the rest of the branch.

Checked while building it, since a new intent is the failure mode the no-new-`JobKind` decision
above was taken to avoid: nothing resolves a `JobIntent` by name. `JobExportHandler` and the
camunda-exporter handlers all filter through allowlists before their `valueOf`, and
`ListenerState.fromZeebeJobIntent` falls through to `UNKNOWN`, so `Job.RELEASED` is inert in the
exporters.

### Inbound connectors are a gap

A webhook, a polling subscription or an intermediate inbound connector never activates a job. The
connector runtime publishes a message or creates a process instance from outside the engine, so a
per-instance switch cannot reach it; for an inbound _start_ connector there is no instance to scope
anything to. Blocking those still means turning the connector runtime off, which is cluster-wide.
