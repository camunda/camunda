# Upstream bug handover — Camunda 8.10-SNAPSHOT

> **For:** the engineer who'll fix these in the Camunda monorepo (or file the issues
> for the team that owns each area).
>
> Both bugs are present on `main` / `8.10.0-SNAPSHOT` as of 2026-05-08.
>
> **Quick start:**
>
> 1. Read this file end-to-end (~5 min).
> 2. Copy-paste either standalone reproducer below into a scratch project and run it
>    against your local broker. Both should fail today; both should pass once the
>    upstream fix lands.
> 3. Apply the preferred fix (see each section). Add the upstream tests listed.
> 4. Re-run. Should pass.

---

## Bug 1 — `ResponseMapper.toUserTaskProperties` NPE on missing action header

**Severity:** breaking. Any deployed BPMN with a pre-assigned user task and a task
listener cannot complete. The user task hangs in `ASSIGNING` indefinitely; the
listener job never reaches a worker.

### Symptom

When a job worker subscribes to a `TASK_LISTENER` job type (e.g. for an `assigning`
event on a user task pre-assigned via `<zeebe:assignmentDefinition assignee="..." />`),
the broker emits the listener job, the gateway tries to deliver it via the
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

The activate-jobs response batch fails entirely, so the worker that polled gets
nothing back. The broker re-marks the job activatable on retry, the worker polls
again, broker NPEs again, infinite loop.

The user task in Operate stays in `ASSIGNING` forever; an SDK job-search shows:

```
job kind=TASK_LISTENER state=CREATED type=<your-listener-type> retries=3 worker= eventType=ASSIGNING
                                                                              ^^^ empty: never activated
```

### Root cause — broker has two paths to ASSIGNING, only one sets `action`

**Path A (always sets action):** explicit user-task command from API / Tasklist UI / SDK.
`UserTaskAssignProcessor.onCommand` (line 81) calls
`userTaskRecord.setAction(command.getValue().getActionOrDefault(DEFAULT_ACTION))` where
`DEFAULT_ACTION = "assign"`. Header always present. Mapper happy.

**Path B (never sets action):** broker-initiated auto-assignment from a static
`<zeebe:assignmentDefinition assignee="..." />` declared at modelling time.
`UserTaskCreateProcessor.onFinalizeCommand` → `assignUserTask()` →
`BpmnUserTaskBehavior.userTaskAssigning(record, assignee)` writes the `ASSIGNING`
event **without ever calling `setAction(...)`**:

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

The same gap exists in the sibling broker-internal writers in
`BpmnUserTaskBehavior` — `userTaskCreating` and `userTaskCanceling` likewise omit
`setAction(...)`. The corresponding explicit-command processors
(`UserTaskAssignProcessor`, `UserTaskClaimProcessor`, `UserTaskCompleteProcessor`,
`UserTaskUpdateProcessor`) all set `action = getActionOrDefault(DEFAULT_ACTION)`.

The three layers then disagree on whether `action` is optional:

| Layer | What it says |
|---|---|
| **Engine** — `BpmnJobBehavior.extractUserTaskHeaders:518` | `if (StringUtils.isNotEmpty(userTaskRecord.getAction())) headers.put(USER_TASK_ACTION_HEADER_NAME, ...);` — header is **conditional**. Path B has empty action → no header. |
| **OpenAPI schema** — `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml`, `UserTaskProperties` | `action` is in the `required:` list. Schema says: action is mandatory. |
| **Mapper** — `gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/ResponseMapper.java:251` | `props.setAction(requireNonNull(headers.get(USER_TASK_ACTION_HEADER_NAME), "action"));` — enforces the schema's claim. |

The recent NullAway pass (commit `1bba1641dd6c`, 2026-04-17, by `megglos` —
*"feat: enforce OpenAPI nullability contract with NullAway in mapping-http"*) made
the mapper enforce the OpenAPI contract. The OpenAPI contract has been wrong for a
while (just silently tolerated). Now any consumer that hits Path B trips the NPE.

The existing test `ResponseMapperTest.java:155-160` only covers the *with-action*
case and even comments:

```
// action is required by the OpenAPI contract; headers must carry it for
// TASK_LISTENER jobs
```

That comment encodes the bug.

### Standalone reproducer (~30 LOC, copy-paste runnable)

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
        .usePlaintext()
        .build();

    var model = Bpmn.createExecutableProcess("npe-repro")
        .startEvent()
        .userTask("review", t -> t
            .zeebeUserTask()                                // modern Camunda User Task
            .zeebeAssignee("demo")                           // static pre-assignment
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

### JUnit 5 IT (drop into `qa/` or any module with `camunda-client-java` + Awaitility)

```java
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class TaskListenerActionHeaderNpeIT {

  @Test
  void shouldActivateAssigningTaskListenerJobWhenUserTaskHasStaticAssignee() {
    final String runId = "bug1-" + UUID.randomUUID().toString().substring(0, 8);
    final String processId = runId + "-process";
    final String listenerJobType = runId + "-assigning-listener";

    try (CamundaClient client = CamundaClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:26500"))
        .restAddress(URI.create("http://localhost:8080"))
        .usePlaintext()
        .build()) {

      // given — user task with static pre-assignment AND an assigning task listener
      final var model = Bpmn.createExecutableProcess(processId)
          .startEvent()
          .userTask("review", t -> t
              .zeebeUserTask()
              .zeebeAssignee("demo")
              .zeebeTaskListener(b -> b
                  .eventType(ZeebeTaskListenerEventType.assigning)
                  .type(listenerJobType)))
          .endEvent()
          .done();

      client.newDeployResourceCommand()
          .addProcessModel(model, processId + ".bpmn").send().join();

      final AtomicReference<String> seenJobType = new AtomicReference<>();

      // when — register a worker for the listener job type and create one instance
      try (var worker = client.newWorker()
          .jobType(listenerJobType)
          .handler((jobClient, job) -> {
            seenJobType.set(job.getType());
            jobClient.newCompleteCommand(job).send().join();
          })
          .open()) {

        client.newCreateInstanceCommand()
            .bpmnProcessId(processId).latestVersion().send().join();

        // then — the assigning listener should fire within a reasonable window.
        // Today this times out: broker emits the job, gateway response mapper
        // NPEs on the missing action header, worker never receives.
        Awaitility.await("assigning task listener fires")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> assertThat(seenJobType).hasValue(listenerJobType));
      }
    }
  }
}
```

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

Audit the sibling methods (`userTaskCreating`, `userTaskCanceling`, …) for the same
omission and apply the same default in each. After this, the engine emits the same
shape of event regardless of who initiated the transition; the schema's
`required: action` becomes honest; the mapper's `requireNonNull` becomes correct.

**Alternative — relax the schema** (if any internal use case actually wants `null`
action):

In `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml`, `UserTaskProperties`, drop
`action` from `required:` and mark it `nullable: true`. Re-run the OpenAPI
generator, fix the mapper to drop the `requireNonNull`, drop the misleading test
comment, add a new test case for the headers-without-action scenario.

**Pragmatic 1-line workaround** in
`gateways/gateway-mapping-http/src/main/java/io/camunda/gateway/mapping/http/ResponseMapper.java:251`:

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

### Tests to add upstream

#### Engine-side (preferred fix path)

`zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/usertask/UserTaskAssigningPathTest.java`
(or extend an existing `UserTaskTest`):

```java
@Test
void shouldSetActionHeaderOnTaskListenerJobWhenAssigneeIsStaticOnTheModel() {
  // given — process with static <zeebe:assignmentDefinition assignee="demo"/> + assigning listener
  final BpmnModelInstance model = Bpmn.createExecutableProcess("p")
      .startEvent()
      .userTask("review", t -> t
          .zeebeUserTask().zeebeAssignee("demo")
          .zeebeTaskListener(b -> b.eventType(assigning).type("listener-type")))
      .endEvent().done();
  ENGINE.deployment().withXmlResource(model).deploy();

  // when
  final long pi = ENGINE.processInstance().ofBpmnProcessId("p").create();

  // then — the assigning listener job carries the action header (= "assign", matching the
  // explicit-command default). This is the fix's contract.
  final Record<JobRecordValue> jobCreated =
      RecordingExporter.jobRecords(JobIntent.CREATED)
          .withProcessInstanceKey(pi)
          .withType("listener-type")
          .getFirst();
  assertThat(jobCreated.getValue().getCustomHeaders())
      .containsEntry(Protocol.USER_TASK_ACTION_HEADER_NAME, "assign");
}
```

#### Mapper-side (regression guard)

In `gateways/gateway-mapping-http/src/test/java/io/camunda/gateway/mapping/http/rest/ResponseMapperTest.java`,
add to the parameterized stream:

```java
new ActivatedJobWithUserTaskPropsCase(
    "TASK_LISTENER job for programmatic assigning (no action header)",
    JobKind.TASK_LISTENER,
    Map.of(
        // No USER_TASK_ACTION_HEADER_NAME — pre-engine-fix the broker omits it on Path B.
        Protocol.USER_TASK_KEY_HEADER_NAME, "100",
        Protocol.USER_TASK_ASSIGNEE_HEADER_NAME, "demo"),
    props -> {
      // Mapper should not throw. Whatever the schema lands on (null action vs default
      // "assign"), assert the contract here.
    }),
```

If the engine fix is taken, this case may become unreachable in practice — but it's
defence-in-depth against any future caller emitting a TASK_LISTENER job without an
action header.

---

## Workarounds for Bug 1 (without patching the broker)

The bug fires for **any task-listener event triggered programmatically by the
broker** (no API command). Easiest first:

#### W1 — Don't pre-assign in the BPMN; assign via API after `CREATED`
Drop `<zeebe:assignmentDefinition assignee="..."/>` from the model. The user task
goes `CREATED` → unassigned. Then issue an explicit assign command:

```java
client.newUserTaskAssignCommand(userTaskKey)
      .assignee("demo")
      .send().join();
// → UserTaskAssignProcessor.onCommand → setAction("assign") → Path A
// → assigning listener job carries the header → activates → fires.
```

To find the just-created `userTaskKey`, watch for `UserTaskIntent.CREATED` via
`client.newUserTaskSearchRequest()` filtered by `processInstanceKey`, or add a
small `start` execution listener on the user task that records the key.

#### W2 — Skip `creating` and `canceling` listeners entirely
These have no command-based trigger path on a normal user task — they're always
broker-emitted, always Path B, always tripped. Move `creating` logic to a `start`
execution listener on the user task element (or a service task immediately
preceding it). For `canceling`, similarly use an `end` execution listener or a
service task on the cancellation path.

#### W3 — FEEL-derived assignee (`assignee="=someVar"`) does NOT help
Tested for completeness: the broker still uses Path B for FEEL-derived assignees,
no command involved, same NPE.

#### W4 — Patch the broker's mapper locally (1 line — see "Pragmatic 1-line
workaround" above). Best for anyone running from sources.

---

## Bug 2 — `ProcessInstanceStateEnum` rejects `CANCELED` value

**Severity:** noise / API skew. Doesn't break processing. Surfaces as repeated
DEBUG/WARN log lines on the broker when Operate's UI is open.

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
| Internal exporter / search entity (`webapps-schema/.../ProcessInstanceState.java`) | `ACTIVE`, `COMPLETED`, **`CANCELED`** |
| Operate UI DTO (`operate/webapp/.../ProcessInstanceStateDto.java`) | `ACTIVE`, `INCIDENT`, `COMPLETED`, **`CANCELED`**, `UNKNOWN`, `UNSPECIFIED` |

Operate's frontend builds search queries using its internal vocabulary
(`CANCELED`) and posts them to `/v2/process-instances/search`. The gateway's REST
OpenAPI enum doesn't accept `CANCELED` — it only knows `TERMINATED`.
Deserialization fails.

The internal enum value `CANCELED` and the public API value `TERMINATED` refer to
the same underlying state (process instance terminated by a cancellation command).

### Standalone reproducer (curl)

```bash
curl -X POST http://localhost:8080/v2/process-instances/search \
  -H 'Content-Type: application/json' \
  -d '{"filter": {"state": "CANCELED"}}'
```

Expected: 400 with the JSON parse error above.

To reproduce inside the running app: open Operate at
`http://localhost:8080/operate` while a Camunda app is running. Operate polls in
the background and trips the same query.

### JUnit 5 IT (drop anywhere with `java.net.http`)

```java
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

final class ProcessInstanceCanceledStateEnumIT {

  private static final String BODY = """
      {"filter": {"state": "CANCELED"}}
      """;

  @Test
  void shouldAcceptCanceledStateOnProcessInstanceSearch() throws Exception {
    final HttpResponse<String> response = HttpClient.newHttpClient().send(
        HttpRequest.newBuilder(
                URI.create("http://localhost:8080/v2/process-instances/search"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(BODY))
            .build(),
        HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode())
        .as("Gateway should accept CANCELED on /v2/process-instances/search "
            + "(it's the spelling the internal exporter and Operate UI both use). "
            + "Got %d with body: %s", response.statusCode(), response.body())
        .isBetween(200, 299);
  }
}
```

This bypasses the SDK (which doesn't model `CANCELED` either) and sends the same
raw request that Operate's UI sends.

### Fix

Two valid paths; pick one. Both might be desirable together — accept both values on
the REST API, and have Operate normalise to the public spelling.

**Path A — public API surface fix.** Add `CANCELED` (or `TERMINATED` as the
canonical, with `CANCELED` accepted as an alias) to the gateway protocol enum in
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

Then update `ProcessInstanceStateEnum.fromValue` (generated) to accept both, OR
add a custom Jackson deserializer that maps `CANCELED` → `TERMINATED` before enum
lookup.

**Path B — Operate fix.** Translate Operate's internal `CANCELED` to the public
`TERMINATED` when building outgoing v2 queries (search for
`ProcessInstanceStateDto.CANCELED` usages in
`operate/webapp/src/main/java/io/camunda/operate/webapp`).

### Tests to add upstream

#### Path A — accept `CANCELED` at the gateway

`zeebe/gateway-rest/src/test/java/io/camunda/zeebe/gateway/rest/controller/ProcessInstanceControllerTest.java`:

```java
@Test
void shouldAcceptCanceledStateOnSearch() throws Exception {
  mockMvc.perform(post("/v2/process-instances/search")
      .contentType(MediaType.APPLICATION_JSON)
      .content("{\"filter\": {\"state\": \"CANCELED\"}}"))
      .andExpect(status().isOk());
}
```

#### Path B — Operate normalises before sending

If the fix is on Operate's side, add a unit test in
`operate/webapp/src/test/java/io/camunda/operate/webapp/v2/api/ProcessInstanceQueryBuilderTest.java`
(or wherever the v2 query construction lives) asserting that
`ProcessInstanceStateDto.CANCELED` translates to `"TERMINATED"` in the outgoing
request body.

---

## Workarounds for Bug 2

Doesn't break anything functionally; only generates noise in broker logs.

#### W1 — Close Operate's UI tab while you're not looking at it
The 400s only fire when Operate's frontend is making background requests for
state-grouped views. No UI open → no requests → no noise.

#### W2 — Don't send `CANCELED` from your own SDK code
The SDK enum doesn't even include `CANCELED` — you'd have to hand-craft a raw
HTTP request to trip this. As long as your code uses
`ProcessInstanceState.TERMINATED` (the public name), you're fine.

#### W3 — Patch one of the two ends locally
Either add `CANCELED` to the gateway enum (one-line patch in
`zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml` + regenerate)
OR map Operate's `CANCELED` → `TERMINATED` at the v2-query construction layer
inside Operate.

---

## Engineers to ping for review

Identified via `git blame` on the lines that introduced the current behavior — i.e.
the most-recent commit that touched each bug site. These are the people with the
freshest context.

### Bug 1

| File / area | Most-relevant engineer | What they know |
|---|---|---|
| `ResponseMapper.toUserTaskProperties:251` (the `requireNonNull` that now NPEs) | **Sebastian Bathke** (`megglos`, `sebastian.bathke@camunda.com`) — wrote the line in `1bba1641dd6c`, 2026-04-17 | Authored the NullAway enforcement pass that surfaced this. Knows the contract-enforcement intent. Should be a reviewer either way — schema fix or mapper relaxation. |
| `UserTaskProperties` schema in `zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml` (marks `action` required) | **Nicola Puppa** — added the `required:` block in `0419e3aa02d9`, 2025-11-06 | Authored the OpenAPI schema saying `action` is mandatory. Decision-maker on whether to mark `nullable: true` or drop from `required:`. |
| `BpmnUserTaskBehavior.userTaskAssigning` (Path B that doesn't set action) | **Dmitriy Melnychuk** (`@melnychukd`) — wrote the method in `62c9f96c76fc`, 2024-11-21; last touched it 2025-01-20 | Owns the broker-internal user-task lifecycle. Best person to confirm whether defaulting `action = "assign"` is right vs. introducing a distinct value. Same person to audit the sibling `userTaskCreating` / `userTaskCanceling` methods. |

**Suggested reviewers on the upstream PR:** Sebastian Bathke (mapper / schema
enforcement context) + Nicola Puppa (schema contract owner) + Dmitriy Melnychuk
(engine fix).

### Bug 2

| File / area | Most-relevant engineer | What they know |
|---|---|---|
| `ProcessInstanceStateEnum` in `zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml` (only `ACTIVE`/`COMPLETED`/`TERMINATED`) | **Nicola Puppa** — wrote the enum in `a09beed30889`, 2025-11-06 | Same author as the Bug 1 schema. Knows the deliberate naming choice (`TERMINATED` for the public surface). Decision-maker on whether to add `CANCELED` as an alias. |
| Internal `webapps-schema` `ProcessInstanceState` (with `CANCELED`) | **Panagiotis Goutis** — restructured into `webapps-schema` in `baed59568dfc`, 2025-03-26 | Owns the webapps-schema layer the exporter writes to. Knows whether `CANCELED` is load-bearing internally or could be renamed. |

**Suggested reviewers on the upstream PR:** Nicola Puppa (gateway enum) + whoever's
currently primary on Operate frontend (the team-of-record for the UI side that
emits `CANCELED`).

---

## Open questions / things I didn't verify

Worth checking before you cut the upstream PRs:

1. **Sibling lifecycle methods in `BpmnUserTaskBehavior`.** `userTaskCompleting`,
   `userTaskUpdating`, `userTaskAssigned` — do any of these also write events
   without setting `action`? If they do, they'll trigger the same NPE for
   `completing` / `updating` task listeners under the same
   programmatic-vs-explicit-command split.

2. **Does the engine fix break any consumer?** Defaulting the action to `"assign"`
   on the broker-internal path makes the event indistinguishable from an external
   `ASSIGN` command. Anyone reading the event log to distinguish "user clicked
   Assign in Tasklist" vs "broker auto-assigned from BPMN" would lose that signal.
   Two ways out: (a) use a distinct default like `"initialize"` or
   `"system-assign"` on the programmatic path, (b) add a separate boolean field /
   record attribute instead of overloading `action`. Worth a quick conversation
   with the user-task-feature owners before picking a default.

3. **Is there a JSpecify annotation on the OpenAPI-generated `UserTaskProperties`
   record that `action` is non-null?** If so, the generator should be re-run after
   relaxing `required:` (or the schema fix needs to mark `action` as
   `nullable: true` instead of just removing it from `required:`).

4. **Bug 2 — does Operate actually emit `CANCELED` to the public v2 API, or only
   on internal-only paths?** Trace the request from the Operate UI to confirm. If
   it's only internal, the fix may belong inside the internal layer (translate at
   the boundary) rather than expanding the public enum.
