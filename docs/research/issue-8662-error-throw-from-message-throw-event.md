# Research: Issue #8662 — Fail to throw/catch error if the job is based on a message throw event

**Issue:** [camunda/camunda#8662](https://github.com/camunda/camunda/issues/8662)
**Status:** Open since Jan 2022, recurring in production (confirmed in versions 1.3.1, 8.4.7, 8.6.13, 8.8.14; likely affects all versions)
**Assigned to:** ce-dmelnych

---

## 1. Root Cause Summary

When a **message intermediate throw event** (or **message end event**) creates a job and the job
worker throws a BPMN error, `CatchEventAnalyzer.findErrorCatchEventInScope()` unconditionally
calls `process.getElementById(elementId, elementType, ExecutableActivity.class)`. This fails
because `ExecutableIntermediateThrowEvent` (and `ExecutableEndEvent`) do **not** extend
`ExecutableActivity` — they extend `ExecutableFlowNode` directly. The `getElementById` method
throws a `RuntimeException` and the process instance is blacklisted.

**Confidence: Very High (0.95).** The code path is deterministic and the class hierarchy mismatch
is unambiguous.

---

## 2. Exact Execution Path

### Entry point: `JobThrowErrorProcessor.throwError()`

**File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobThrowErrorProcessor.java`

```
processRecord()  →  throwError()
  ↓
  job.getElementInstanceKey()  →  serviceTaskInstanceKey
  elementInstanceState.getInstance(serviceTaskInstanceKey)  →  serviceTaskInstance
  ↓
  stateAnalyzer.findErrorCatchEvent(errorCode, serviceTaskInstance, ...)
```

At **line 154**, `job.getElementInstanceKey()` returns the element instance key of the element
that created the job. When the job was created by a **message intermediate throw event**, this
key points to the throw event's own element instance — not a service task.

### Scope walking: `CatchEventAnalyzer`

**File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/analyzers/CatchEventAnalyzer.java`

```
findErrorCatchEvent()           (line 55)  — outer loop: walks across process boundaries
  ↓
findErrorCatchEventInProcess()  (line 100) — inner loop: walks up parent scopes within a process
  ↓
findErrorCatchEventInScope()    (line 121) — checks a single scope for catch events
```

### The failing line: `findErrorCatchEventInScope()` line 132

```java
final var element = process.getElementById(elementId, elementType, ExecutableActivity.class);
```

When the `instance` is the message intermediate throw event:
- `elementId` = the throw event's BPMN element ID (e.g., `"Event_18rw56c"`)
- `elementType` = `BpmnElementType.INTERMEDIATE_THROW_EVENT`
- `expectedClass` = `ExecutableActivity.class`

### The exception: `ExecutableProcess.getElementById()` line 91–99

**File:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableProcess.java`

The method resolves the element, confirms the `BpmnElementType` matches, then checks:

```java
if (expectedClass.isAssignableFrom(element.getClass())) {
    return (T) element;
} else {
    throw new RuntimeException(
        "Expected element with id '...' to be instance of class 'ExecutableActivity', "
      + "but it is an instance of 'ExecutableIntermediateThrowEvent'");
}
```

---

## 3. Class Hierarchy — The Violated Assumption

```
ExecutableFlowElement (interface)
  └── AbstractFlowElement
        └── ExecutableFlowNode
              ├── ExecutableActivity  ← implements ExecutableCatchEventSupplier
              │     └── ExecutableJobWorkerTask  ← implements ExecutableJobWorkerElement
              │           ├── ExecutableServiceTask
              │           ├── ExecutableSendTask
              │           ├── ExecutableScriptTask
              │           └── ExecutableBusinessRuleTask
              │
              ├── ExecutableIntermediateThrowEvent  ← implements ExecutableJobWorkerElement
              │     (does NOT extend ExecutableActivity, does NOT implement ExecutableCatchEventSupplier)
              │
              └── ExecutableEndEvent  ← implements ExecutableJobWorkerElement
                    (does NOT extend ExecutableActivity, does NOT implement ExecutableCatchEventSupplier)
```

**The assumption:** `CatchEventAnalyzer` assumes every element instance it encounters while
walking scopes is an `ExecutableActivity` (which implements `ExecutableCatchEventSupplier` and
therefore has a `getEvents()` method).

**Why it's wrong:** `ExecutableJobWorkerElement` (the ability to create jobs) is orthogonal to
`ExecutableCatchEventSupplier` (the ability to host catch events like boundary events). Elements
like `ExecutableIntermediateThrowEvent` and `ExecutableEndEvent` can create jobs but cannot host
boundary error events.

**`ExecutableCatchEventSupplier` implementations** (the only element types that CAN have catch
events):
1. `ExecutableActivity` — activities with boundary events / event subprocesses
2. `ExecutableEventBasedGateway` — event-based gateway events

---

## 4. Exact Trigger Conditions

### Required BPMN structure

A process containing:
1. A **message intermediate throw event** (with `zeebe:taskDefinition` / job type) — OR a
**message end event** (with `zeebe:taskDefinition` / job type)
2. An **error catch event** somewhere in the scope hierarchy (boundary error event, error event
subprocess, or error catch in parent process)

### Required runtime sequence

1. Deploy a process with the above structure
2. Start a process instance
3. The message intermediate throw event activates and creates a job
4. A job worker picks up the job and calls **ThrowError** command with an error code
5. `JobThrowErrorProcessor.throwError()` is invoked
6. `CatchEventAnalyzer.findErrorCatchEvent()` starts walking scopes
7. The **first scope** it checks is the throw event itself → **CRASH**

### Does the error catch event need to exist?

**No.** The crash happens before any catch event search succeeds, because `findErrorCatchEventInScope`
fails on the very first iteration when it tries to cast the current element to `ExecutableActivity`.
Even if there is no catch event anywhere, the crash prevents the normal "no catch event found"
incident from being raised.

### Does this affect escalation too?

**Yes.** `findEscalationCatchEventInScope()` (line 212) has the identical pattern:

```java
final var element = process.getElementById(elementId, elementType, ExecutableActivity.class);
```

Any escalation thrown from a message intermediate throw event or message end event job will hit
the same crash.

### Confirmed affected element types (from issue comments)

|                  Element Type                   |               Class                |                                                     Reported                                                     |
|-------------------------------------------------|------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Message intermediate throw event                | `ExecutableIntermediateThrowEvent` | Original report + multiple prod occurrences                                                                      |
| Message end event (or error end event with job) | `ExecutableEndEvent`               | [Comment by @lenaschoenburg](https://github.com/camunda/camunda/issues/8662#issuecomment-2742675100), March 2025 |

---

## 5. Existing Tests and Patterns

### Directly relevant test files

|                                                 File                                                 |                            Description                             |
|------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobThrowErrorTest.java`           | Tests for `JobThrowErrorProcessor` — no test for throw event jobs  |
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/error/ErrorEventTest.java`       | Comprehensive error event tests — no message throw event scenarios |
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/error/ErrorCatchEventTest.java`  | Error catch event tests                                            |
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/incident/ErrorEventIncidentTest.java` | Incident creation for unhandled errors                             |

### Related test files (intermediate throw events)

|                                                                           File                                                                            |             Description             |
|-----------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/signal/SignalIntermediateThrowEventTest.java`                                              | Signal throw event tests            |
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/bpmn/activity/listeners/execution/ExecutionListenerIntermediateThrowEventElementTest.java` | Execution listeners on throw events |

### No existing tests cover

- Throwing a BPMN error from a job created by a message intermediate throw event
- Throwing a BPMN error from a job created by a message end event
- Throwing an escalation from a job created by a message throw/end event
- `CatchEventAnalyzer` encountering a non-`ExecutableActivity` element instance

### Best test class to extend

**`JobThrowErrorTest.java`** is the best target because:
- It directly tests the `JobThrowErrorProcessor` flow
- It uses `EngineRule.singlePartition()` which provides the full engine integration
- It has established patterns for BPMN model creation, job interaction, and assertion
- The bug is in the job error throwing path, not in BPMN error event semantics

---

## 6. Recommended Reproduction Test

### Test class

`zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobThrowErrorTest.java`

### Test method: `shouldCatchErrorThrownFromMessageIntermediateThrowEvent`

```java
@Test
public void shouldCatchErrorThrownFromMessageIntermediateThrowEvent() {
  // given
  final var process =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateThrowEvent(
              "message-throw",
              e -> e.message("msg").zeebeJobType(jobType))
          .endEvent()
          .moveToActivity("message-throw")
          .boundaryEvent("error-boundary", b -> b.error(ERROR_CODE))
          .endEvent("error-end")
          .done();

  ENGINE.deployment().withXmlResource(process).deploy();
  final long processInstanceKey =
      ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

  // when — throw error from the message throw event's job
  ENGINE
      .job()
      .ofInstance(processInstanceKey)
      .withType(jobType)
      .withErrorCode(ERROR_CODE)
      .throwError();

  // then — the error should be caught by the boundary event
  assertThat(
      RecordingExporter.processInstanceRecords()
          .withProcessInstanceKey(processInstanceKey)
          .limitToProcessInstanceCompleted())
      .extracting(r -> r.getValue().getElementId(), Record::getIntent)
      .contains(
          tuple("error-boundary", ProcessInstanceIntent.ELEMENT_ACTIVATED),
          tuple("error-end", ProcessInstanceIntent.ELEMENT_COMPLETED),
          tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
}
```

> **Note on BPMN validity:** An intermediate throw event cannot have a boundary event attached
> directly in standard BPMN. The model may need adjustment — see alternative below where the
> throw event is inside a subprocess that has the boundary error event:

### Alternative test (subprocess wrapper — more BPMN-correct)

```java
@Test
public void shouldCatchErrorThrownFromMessageIntermediateThrowEventInSubprocess() {
  // given — message throw event inside a subprocess, error caught on the subprocess
  final var process =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .subProcess("subprocess",
              s -> s.embeddedSubProcess()
                  .startEvent()
                  .intermediateThrowEvent(
                      "message-throw",
                      e -> e.message("msg").zeebeJobType(jobType))
                  .endEvent())
          .boundaryEvent("error-boundary", b -> b.error(ERROR_CODE))
          .endEvent("error-end")
          .done();

  ENGINE.deployment().withXmlResource(process).deploy();
  final long processInstanceKey =
      ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

  // when — throw error from the message throw event's job
  ENGINE
      .job()
      .ofInstance(processInstanceKey)
      .withType(jobType)
      .withErrorCode(ERROR_CODE)
      .throwError();

  // then — the error should propagate to the subprocess boundary event
  assertThat(
      RecordingExporter.processInstanceRecords()
          .withProcessInstanceKey(processInstanceKey)
          .limitToProcessInstanceCompleted())
      .extracting(r -> r.getValue().getElementId(), Record::getIntent)
      .contains(
          tuple("error-boundary", ProcessInstanceIntent.ELEMENT_ACTIVATED),
          tuple("error-end", ProcessInstanceIntent.ELEMENT_COMPLETED),
          tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
}
```

### Also test for message end event

```java
@Test
public void shouldCatchErrorThrownFromMessageEndEvent() {
  // given — error event subprocess catches error from message end event's job
  final var process =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .eventSubProcess("error-subprocess",
              s -> s.startEvent("error-start")
                  .error(ERROR_CODE)
                  .interrupting(true)
                  .endEvent("error-subprocess-end"))
          .startEvent()
          .endEvent("message-end", e -> e.message("msg").zeebeJobType(jobType))
          .done();

  ENGINE.deployment().withXmlResource(process).deploy();
  final long processInstanceKey =
      ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

  // when
  ENGINE
      .job()
      .ofInstance(processInstanceKey)
      .withType(jobType)
      .withErrorCode(ERROR_CODE)
      .throwError();

  // then
  assertThat(
      RecordingExporter.processInstanceRecords()
          .withProcessInstanceKey(processInstanceKey)
          .limitToProcessInstanceCompleted())
      .extracting(r -> r.getValue().getElementId(), Record::getIntent)
      .contains(
          tuple("error-start", ProcessInstanceIntent.ELEMENT_ACTIVATED),
          tuple("error-subprocess-end", ProcessInstanceIntent.ELEMENT_COMPLETED),
          tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
}
```

### Expected behavior

- **Before fix:** `RuntimeException` thrown at line 132 of `CatchEventAnalyzer.java`, process
  instance is blacklisted.
- **After fix:** Error propagates normally through the scope hierarchy and is caught by the
  matching error catch event (boundary event or error event subprocess). If no catch event is
  found, an incident is raised normally.

---

## 7. Recommended Fix

### Option A: Skip non-`ExecutableCatchEventSupplier` scopes (Recommended)

**What to change:** `CatchEventAnalyzer.findErrorCatchEventInScope()` and
`findEscalationCatchEventInScope()`

**Why:** Elements that don't implement `ExecutableCatchEventSupplier` can never have catch events
(no boundary events, no event subprocesses). The scope walker should skip them and continue to
the parent scope.

**Changed code in `findErrorCatchEventInScope` (line 121–151):**

```java
private Either<List<DirectBuffer>, CatchEventTuple> findErrorCatchEventInScope(
    final DirectBuffer errorCode,
    final ExecutableProcess process,
    final ElementInstance instance) {

  final Either<List<DirectBuffer>, CatchEventTuple> availableCatchEvents =
      Either.left(new ArrayList<>());
  final var processInstanceRecord = instance.getValue();
  final var elementId = processInstanceRecord.getElementIdBuffer();
  final var elementType = processInstanceRecord.getBpmnElementType();

  // Use ExecutableFlowElement instead of ExecutableActivity to avoid class cast failure
  final var element = process.getElementById(elementId, elementType, ExecutableFlowElement.class);

  // Only elements that implement ExecutableCatchEventSupplier can host catch events
  if (!(element instanceof final ExecutableCatchEventSupplier catchEventSupplier)) {
    return availableCatchEvents;
  }

  final Optional<ExecutableCatchEvent> errorCatchEvent =
      catchEventSupplier.getEvents().stream()
          .filter(ExecutableCatchEvent::isError)
          .filter(catchEvent -> catchEvent.getError().getErrorCode().isPresent())
          .sorted(ERROR_CODE_COMPARATOR)
          .filter(event -> matchesErrorCode(event, errorCode, availableCatchEvents))
          .findFirst();

  if (errorCatchEvent.isPresent()) {
    catchEventTuple.instance = instance;
    catchEventTuple.catchEvent = errorCatchEvent.get();
    return Either.right(catchEventTuple);
  }

  return availableCatchEvents;
}
```

**Same pattern for `findEscalationCatchEventInScope` (line 204–232):**

```java
private Optional<CatchEventTuple> findEscalationCatchEventInScope(
    final DirectBuffer escalationCode,
    final ExecutableProcess process,
    final ElementInstance instance) {

  final var processInstanceRecord = instance.getValue();
  final var elementId = processInstanceRecord.getElementIdBuffer();
  final var elementType = processInstanceRecord.getBpmnElementType();

  final var element = process.getElementById(elementId, elementType, ExecutableFlowElement.class);

  if (!(element instanceof final ExecutableCatchEventSupplier catchEventSupplier)) {
    return Optional.empty();
  }

  final Optional<ExecutableCatchEvent> escalationCatchEvent =
      catchEventSupplier.getEvents().stream()
          .filter(ExecutableCatchEvent::isEscalation)
          .filter(catchEvent -> catchEvent.getEscalation().getEscalationCode().isPresent())
          .sorted(ESCALATION_CODE_COMPARATOR)
          .filter(event -> matchesEscalationCode(event, escalationCode))
          .findFirst();

  if (escalationCatchEvent.isPresent()) {
    catchEventTuple.instance = instance;
    catchEventTuple.catchEvent = escalationCatchEvent.get();
    return Optional.of(catchEventTuple);
  }

  return Optional.empty();
}
```

**Pros:**
- Minimal change, surgical fix
- Preserves all existing error propagation semantics
- Non-activity scopes simply report "no catch events here" and the walker continues to parent
- Works for all current and future non-activity job worker elements
- Fixes both error and escalation paths

**Cons:**
- None significant

**Risks/Regressions:**
- Very low risk — the only behavioral change is that non-activity scopes are skipped instead of
crashing
- All existing tests should continue to pass because service tasks, script tasks, etc. all extend
`ExecutableActivity`

### Option B: Validate earlier in `JobThrowErrorProcessor`

**What to change:** Add a check in `JobThrowErrorProcessor.throwError()` before calling
`findErrorCatchEvent()`.

**Sketch:**

```java
// Before calling findErrorCatchEvent, check if the element supports error catching
final var process = processState.getProcessByKeyAndTenant(...);
final var element = process.getProcess().getElementById(elementId);
if (!(element instanceof ExecutableActivity)) {
    // Skip catch event search, raise incident directly
    raiseIncident(jobKey, job, new Failure("...", ErrorType.UNHANDLED_ERROR_EVENT));
    return;
}
```

**Pros:**
- Catches the issue earlier
- Clear separation of concerns

**Cons:**
- **Incorrect semantics**: Just because the current element isn't an activity doesn't mean there's
no catch event in a parent scope. A message throw event inside a subprocess should still
propagate errors to the subprocess's boundary events.
- Would need to replicate scope-walking logic

**Verdict:** **Not recommended** — this approach breaks error propagation semantics.

### Option C: Make `ExecutableIntermediateThrowEvent` extend `ExecutableActivity`

**What to change:** Change the class hierarchy so throw events can host catch events.

**Pros:**
- No change to `CatchEventAnalyzer`

**Cons:**
- **Semantically wrong**: Intermediate throw events cannot have boundary events in BPMN
- Would add dead code (empty catch event lists)
- High risk of unintended side effects throughout the engine

**Verdict:** **Not recommended** — violates BPMN semantics.

---

## 8. Summary

|           Item            |                                                                                                                                                                                     Detail                                                                                                                                                                                     |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Root cause**            | `CatchEventAnalyzer.findErrorCatchEventInScope()` (line 132) and `findEscalationCatchEventInScope()` (line 212) unconditionally cast elements to `ExecutableActivity.class`. Elements like `ExecutableIntermediateThrowEvent` and `ExecutableEndEvent` are `ExecutableFlowNode` subclasses that implement `ExecutableJobWorkerElement` but not `ExecutableCatchEventSupplier`. |
| **Exact trigger**         | A message intermediate throw event or message end event creates a job → job worker calls ThrowError → `CatchEventAnalyzer` crashes on the first scope check.                                                                                                                                                                                                                   |
| **Affected code**         | `CatchEventAnalyzer.java` lines 132 and 212                                                                                                                                                                                                                                                                                                                                    |
| **Recommended fix**       | Option A: Use `ExecutableFlowElement.class` for `getElementById`, then `instanceof ExecutableCatchEventSupplier` check before accessing `getEvents()`. Skip non-supplier scopes.                                                                                                                                                                                               |
| **Recommended test file** | `JobThrowErrorTest.java` — add tests for message intermediate throw event and message end event error throwing                                                                                                                                                                                                                                                                 |
| **Also affected**         | Escalation throwing from the same element types (same code pattern at line 212)                                                                                                                                                                                                                                                                                                |
| **Confidence**            | 0.95 — the class hierarchy mismatch is deterministic; only open question is the exact BPMN builder API for message throw events in tests                                                                                                                                                                                                                                       |
| **Open questions**        | 1. Verify the BPMN builder API for attaching `zeebeJobType` to intermediate throw events (may need `.message("name")` first). 2. Confirm whether the Zeebe BPMN model allows boundary events on intermediate throw events (standard BPMN does not; may need subprocess wrapper in test).                                                                                       |

### Key files

|                                                              File                                                              |                            Role                            |
|--------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------|
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/analyzers/CatchEventAnalyzer.java`                                   | **Fix target** — lines 132 and 212                         |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/job/JobThrowErrorProcessor.java`                                | Entry point for error throwing from jobs                   |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableProcess.java`                | `getElementById` — throws the exception                    |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableActivity.java`               | Expected class (implements `ExecutableCatchEventSupplier`) |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableIntermediateThrowEvent.java` | Actual class (does NOT extend `ExecutableActivity`)        |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableEndEvent.java`               | Also affected (does NOT extend `ExecutableActivity`)       |
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/deployment/model/element/ExecutableCatchEventSupplier.java`     | Interface that defines `getEvents()`                       |
| `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobThrowErrorTest.java`                                     | **Test target** — add reproduction tests                   |

