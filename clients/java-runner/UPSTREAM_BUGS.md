# Upstream bug handover — Camunda 8.10-SNAPSHOT

> **For:** another agent or engineer who will fix these in the Camunda monorepo (or file
> the issues for the team that owns each area).
>
> **Context:** found while building the LiveBpmn one-file process runner
> (`clients/java-runner/`). The LiveBpmn API surface itself is fine; both bugs are in the
> Camunda app / gateway code paths it exercises. The runner is just a convenient
> reproducer.
>
> Both bugs are present on the `main` branch / `8.10.0-SNAPSHOT` build of the monorepo
> as of 2026-05-08.

---

## Bug 1 — `ResponseMapper.toUserTaskProperties` NPE on missing action header

**Severity:** breaking. Any deployed BPMN with a pre-assigned user task and a task
listener cannot complete. The user task hangs in `ASSIGNING` indefinitely; the listener
job never reaches a worker.

### Symptom

When a job worker subscribes to a TASK_LISTENER job type (e.g. for an `assigning` event
on a user task pre-assigned via `<zeebe:assignmentDefinition assignee="..." />`), the
broker emits the listener job, the gateway tries to deliver it via the
`/v2/jobs/activation` REST endpoint, and crashes:

```
[Broker-0] [zb-actors-0] [ActivateJobsHandlerRest-Broker] ERROR io.camunda.zeebe.util.actor
  Uncaught exception in 'ActivateJobsHandlerRest-Broker' in phase 'STARTED'.
java.lang.NullPointerException: action
  at java.base/java.util.Objects.requireNonNull(Objects.java:259)
  at io.camunda.gateway.mapping.http.ResponseMapper.toUserTaskProperties(ResponseMapper.java:251)
  at io.camunda.gateway.mapping.http.ResponseMapper.toActivatedJob(ResponseMapper.java:227)
  at io.camunda.gateway.mapping.http.ResponseMapper.toActivateJobsResponse(ResponseMapper.java:194)
  at io.camunda.zeebe.gateway.impl.job.RoundRobinActivateJobsHandler.lambda$handleResponseSuccess$3
```

The activate-jobs response batch fails entirely, so the worker that polled for the job
gets nothing back. The broker re-marks the job activatable on retry, the worker polls
again, broker NPEs again, infinite loop.

The user task in Operate stays in `ASSIGNING` forever; the SDK job-search shows:

```
job kind=TASK_LISTENER state=CREATED type=<prefix>-review-tl-assigning retries=3 worker=  eventType=ASSIGNING
                                                                                       ^^^^ empty: never activated
```

### Root cause — broker has two paths to ASSIGNING, only one sets `action`

**Path A (always sets action):** explicit user-task command from API / Tasklist UI / SDK.
`UserTaskAssignProcessor.onCommand` (line 81) calls
`userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION))` where
`DEFAULT_ACTION = "assign"`. Header always present. Mapper happy.

**Path B (never sets action):** broker-initiated auto-assignment from a static
`<zeebe:assignmentDefinition assignee="..." />` declared at modelling time.
`UserTaskCreateProcessor.onFinalizeCommand` → `assignUserTask()` →
`BpmnUserTaskBehavior.userTaskAssigning(record, assignee)` writes the `ASSIGNING` event
**without ever calling `setAction(...)`**:

```java
public void userTaskAssigning(final UserTaskRecord userTaskRecord, final String assignee) {
  if (!userTaskRecord.getAssignee().equals(assignee)) {
    userTaskRecord.setAssignee(assignee);
    userTaskRecord.setAssigneeChanged();
  }
  stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);
  // ^^ no setAction
}
```

The same three-layer chain then crashes because all three layers disagreed about
whether action is optional:

| Layer | What it says |
|---|---|
| **Engine** — `BpmnJobBehavior.extractUserTaskHeaders:518` (`zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnJobBehavior.java`) | `if (StringUtils.isNotEmpty(userTaskRecord.getAction())) headers.put(USER_TASK_ACTION_HEADER_NAME, ...);` — header is **conditional**. Path B has empty action → no header. |
| **OpenAPI schema** — `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml`, `UserTaskProperties` | `action` is in the `required:` list (alongside `assignee`, `dueDate`, `followUpDate`, `formKey`, `priority`, `userTaskKey`, `candidateGroups`, `candidateUsers`, `changedAttributes`). Schema says: action is mandatory. |
| **Mapper** — `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/ResponseMapper.java:251` (commit `1bba1641dd6c`, *"feat: enforce OpenAPI nullability contract with NullAway in mapping-http"*, 2026-04-17, by `megglos`) | `props.setAction(requireNonNull(headers.get(USER_TASK_ACTION_HEADER_NAME), "action"));` — enforces the schema's claim. |

The recent NullAway pass made the mapper enforce the OpenAPI contract. The OpenAPI
contract has been wrong for a while (just silently tolerated). Now any consumer that
hits Path B trips the NPE.

The same asymmetry likely exists for other broker-internal transitions (programmatic
completion, parent-scope cancellation, …). Each `BpmnUserTaskBehavior` method that
writes an `ASSIGNING` / `COMPLETING` / `CANCELING` / `UPDATING` event without an
explicit command should be audited for the same omission.

The existing test `ResponseMapperTest.java:155-160` only covers the *with-action* case
and even comments:

```
// action is required by the OpenAPI contract; headers must carry it for
// TASK_LISTENER jobs
```

That comment encodes the bug.

### Reproducer (Java, ~30 LOC)

```java
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.net.URI;

public class TaskListenerNpeRepro {
  public static void main(String[] args) throws Exception {
    var client = CamundaClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:26500"))
        .restAddress(URI.create("http://localhost:8080"))
        .build();

    var model = Bpmn.createExecutableProcess("npe-repro")
        .startEvent()
        .userTask("review", t -> t
            .zeebeUserTask()                              // modern Camunda User Task
            .zeebeAssignee("demo")                         // static pre-assignment
            .zeebeTaskListener(b -> b
                .eventType(ZeebeTaskListenerEventType.assigning)
                .type("repro-assigning-listener")))
        .endEvent()
        .done();

    client.newDeployResourceCommand().addProcessModel(model, "npe-repro.bpmn").send().join();

    // worker subscribed to the listener job type
    client.newWorker()
        .jobType("repro-assigning-listener")
        .handler((c, job) -> {
          System.out.println("got listener job: " + job.getKey());
          c.newCompleteCommand(job).send().join();
        })
        .open();

    // create one instance — the listener job will sit in CREATED state forever,
    // and the broker logs a NullPointerException on every activation attempt.
    client.newCreateInstanceCommand()
        .bpmnProcessId("npe-repro").latestVersion()
        .send().join();

    Thread.sleep(60_000);  // let the broker NPE a few times
  }
}
```

Run against any 8.10-SNAPSHOT broker (e.g. `StandaloneCamunda` from the monorepo).
Expected output: nothing (worker never gets a job). Broker log: NPE every ~5 s.

### Fix

**Preferred — fix the engine to set the missing action** (smallest change, restores
contract symmetry between the two paths). In
`zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/bpmn/behavior/BpmnUserTaskBehavior.java`:

```java
public void userTaskAssigning(final UserTaskRecord userTaskRecord, final String assignee) {
  final long userTaskKey = userTaskRecord.getUserTaskKey();
  if (!userTaskRecord.getAssignee().equals(assignee)) {
    userTaskRecord.setAssignee(assignee);
    userTaskRecord.setAssigneeChanged();
  }
  if (userTaskRecord.getAction() == null || userTaskRecord.getAction().isEmpty()) {
    userTaskRecord.setAction("assign");   // align with UserTaskAssignProcessor.DEFAULT_ACTION
  }
  stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.ASSIGNING, userTaskRecord);
}
```

Audit the sibling methods (`userTaskCompleting`, `userTaskCanceling`, `userTaskUpdating`,
…) for the same omission and apply the same default in each. After this, the engine
emits the same shape of event regardless of who initiated the transition; the schema's
`required: action` becomes honest; the mapper's `requireNonNull` becomes correct.

**Alternative — relax the schema** (if any internal use case actually wants `null`
action):

In `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml`, `UserTaskProperties`, drop
`action` (and the other always-conditional fields) from `required:`:

```yaml
UserTaskProperties:
  required:
    - userTaskKey   # always present (broker sets it whenever userTaskKey > 0)
  type: object
  description: Contains properties of a user task.
  properties:
    action:
      description: The action performed on the user task. Absent for programmatic transitions.
      type: string
      nullable: true
    assignee:
      type: string
      nullable: true
    candidateGroups: { type: array, items: { type: string } }
    candidateUsers:  { type: array, items: { type: string } }
    changedAttributes: { type: array, items: { type: string } }
    dueDate:        { type: string, nullable: true }
    followUpDate:   { type: string, nullable: true }
    formKey:        { type: string, nullable: true }
    priority:       { type: integer, nullable: true }
```

Re-run the OpenAPI generator, fix the mapper to drop the `requireNonNull`, drop the
misleading test comment, add a new test case for the headers-without-action scenario.

**Pragmatic workaround (1 line)** — `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/ResponseMapper.java:251`:

```java
// before:
props.setAction(requireNonNull(headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME), "action"));

// after:
final String action = headers.get(Protocol.USER_TASK_ACTION_HEADER_NAME);
if (action == null) {
  return null;   // task-listener job has no action header (e.g. programmatic ASSIGNING); skip props
}
props.setAction(action);
```

This unblocks the runtime path. The schema fix should follow.

### Test to add

`gateways/gateway-mapping-http/src/test/java/io/camunda/gateway/mapping/http/rest/ResponseMapperTest.java` — add to the parameterized stream:

```java
new ActivatedJobWithUserTaskPropsCase(
    "TASK_LISTENER job for programmatic assigning (no action header)",
    JobKind.TASK_LISTENER,
    Map.of(
        // No USER_TASK_ACTION_HEADER_NAME — the broker omits it for programmatic transitions.
        Protocol.USER_TASK_KEY_HEADER_NAME, "100",
        Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "demo"),
    props -> {
      // Mapper should not throw; either return null or return a props object with a null action.
      // Pick whichever the schema fix lands on.
    }),
```

---

## Bug 2 — `ProcessInstanceStateEnum` rejects `CANCELED` value

**Severity:** noise / API skew. Doesn't break processing. Surfaces as repeated DEBUG/WARN
log lines on the broker when Operate's UI is open.

### Symptom

```
[http-nio-0.0.0.0-8080-exec-10] DEBUG io.camunda.zeebe.gateway.rest -
JSON parse error: Cannot construct instance of
`io.camunda.gateway.protocol.model.ProcessInstanceStateEnum`,
problem: Unexpected value 'CANCELED'
 (through reference chain: ProcessInstanceSearchQuery["filter"]->ProcessInstanceFilter["state"])

Caused by: java.lang.IllegalArgumentException: Unexpected value 'CANCELED'
  at io.camunda.gateway.protocol.model.ProcessInstanceStateEnum.fromValue(ProcessInstanceStateEnum.java:60)
```

The request returns 400 to the caller (Operate's UI) and the entire query fails.
Operate may show empty tabs as a result.

### Root cause — value-set inconsistency across components

| Component | Allowed `ProcessInstanceState` values |
|---|---|
| Public REST API (`zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml`) | `ACTIVE`, `COMPLETED`, `TERMINATED` |
| SDK enum (`clients/java/src/main/java/io/camunda/client/api/search/enums/ProcessInstanceState.java`) | `ACTIVE`, `COMPLETED`, `TERMINATED`, `UNKNOWN_ENUM_VALUE` |
| Internal exporter / search entity (`webapps-schema/src/main/java/io/camunda/webapps/schema/entities/listview/ProcessInstanceState.java`) | `ACTIVE`, `COMPLETED`, **`CANCELED`** |
| Operate UI DTO (`operate/webapp/src/main/java/io/camunda/operate/webapp/reader/dto/listview/ProcessInstanceStateDto.java`) | `ACTIVE`, `INCIDENT`, `COMPLETED`, **`CANCELED`**, `UNKNOWN`, `UNSPECIFIED` |

Operate's frontend builds search queries using its internal vocabulary (`CANCELED`)
and posts them to `/v2/process-instances/search`. The gateway's REST OpenAPI enum
doesn't accept `CANCELED` — it only knows `TERMINATED`. Deserialization fails.

The internal enum value `CANCELED` and the public API value `TERMINATED` refer to the
same underlying state (process instance terminated by a cancellation command).

### Reproducer (curl)

```bash
curl -X POST http://localhost:8080/v2/process-instances/search \
  -H 'Content-Type: application/json' \
  -d '{"filter": {"state": "CANCELED"}}'
```

Expected: 400 with the JSON parse error above.

To reproduce inside the running app: just open Operate at `http://localhost:8080/operate`
while a Camunda app is running. Operate polls in the background and trips the same query.

### Fix

Two valid paths; pick one. **Either** path resolves the noise. Both might be desirable
together — accept both values on the REST API, and have Operate normalise to the public
spelling.

**Path A — public API surface fix.** Add `CANCELED` (or `TERMINATED` as the canonical,
with `CANCELED` accepted as an alias) to the gateway protocol enum:

`zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml`:

```yaml
ProcessInstanceStateEnum:
  description: Process instance states
  type: string
  enum:
    - ACTIVE
    - COMPLETED
    - TERMINATED
    - CANCELED   # alias for TERMINATED (process terminated via cancellation command)
```

Then update `ProcessInstanceStateEnum.fromValue` (generated) to accept both, OR add a
custom Jackson deserializer that maps `CANCELED` → `TERMINATED` before enum lookup.

**Path B — Operate fix.** Translate Operate's internal `CANCELED` to the public
`TERMINATED` when building outgoing v2 queries:

In Operate's request-building code (search for `ProcessInstanceStateDto.CANCELED`
usages in `operate/webapp/src/main/java/io/camunda/operate/webapp`), map `CANCELED`
to the public-spelled value before calling the gateway.

### Test to add

`zeebe/gateway-rest/.../JobControllerTest` (or wherever process-instance search REST
tests live):

```java
@Test
void shouldAcceptCanceledStateValue() throws Exception {
  mockMvc.perform(post("/v2/process-instances/search")
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"filter\": {\"state\": \"CANCELED\"}}"))
      .andExpect(status().isOk());
}
```

---

## Reproducer in this repo

`clients/java-runner/src/main/java/io/camunda/runner/examples/TaskListenerDiagnosticDemo.java`
is a higher-level reproducer for **Bug 1** that also runs the diagnostic poller showing
both bugs side-by-side in the broker log.

To run:
1. Start Camunda from sources (`StandaloneCamunda` or similar) on `localhost:26500` /
   `:8080`, with Elasticsearch as secondary store.
2. `./mvnw install -pl clients/java-runner -am -Dquickly -T1C`
3. Right-click `TaskListenerDiagnosticDemo.main()` → Run.
4. Watch the broker stdout: NPE for Bug 1 every ~5s; CANCELED parse error for Bug 2 if
   Operate UI is open in a browser tab pointing at `http://localhost:8080/operate`.

The diagnostic prints SDK-side snapshots showing the listener job stuck in `CREATED`
with `worker=` empty — confirming the broker emitted it but the gateway's response
mapping failed on every activation attempt.

---

## Out of scope for these bugs

The following observations came up during diagnosis but are **separate** issues, not
covered by this doc:

- Some prior failed jobs in the user's index have `flowNodeId == null`, causing
  `JobEntityTransformer.apply()` to throw `NullPointerException: elementId` when the
  search returns those rows. Symptom in `JobController.searchJobs`. Likely cause: the
  exporter's `JobHandler.updateEntity` line 135 (`entity.setFlowNodeId(null)` on
  FAILED/ERROR_THROWN intents) writing null on first-insert when the CREATED event
  was missed. This is its own bug — file separately if it bites someone else.

- The `Run.await(...)` polling in LiveBpmn currently filters by process-instance keys
  only; it doesn't send a state filter, so it doesn't itself trip Bug 2. If we ever add
  a state filter we must skip `CANCELED` (use `TERMINATED`) until Bug 2 is fixed.
