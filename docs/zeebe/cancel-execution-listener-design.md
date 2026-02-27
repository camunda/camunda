# Cancel Execution Listener — Design Document

> **Epic:** product-hub#2768 — "Cancel" Process Instance Execution Listener
> **Status:** Implementation complete (model, protocol, engine processing, and tests done)
> **DRI:** @marcosgvieira
> **Labels:** `migration-blocker` (C7→C8)
> **Cross-references:** product-hub#3102 (QA), camunda#46880 (ELT Experiment)

## 1. Overview

This document describes the design for adding a `cancel` event type to execution listeners.
The `cancel` value has been added to `ZeebeExecutionListenerEventType` (alongside `start` and
`end`) and to `JobListenerEventType` as `CANCEL`. The new type allows users to attach execution
listeners that fire when a process instance (or element) is being terminated — before the
termination completes.

**Scope:** Process-instance-level cancel execution listeners only, not per-element cancel
(confirmed by @aleksander-dytko). However, the implementation described here applies to any
`ExecutableFlowNode` in the hierarchy, as the process element inherits from `ExecutableFlowNode`
via `ExecutableProcess → ExecutableFlowElementContainer → ExecutableActivity → ExecutableFlowNode`.

## 1.1 Implementation Progress

The following tracks what has been implemented vs what remains.

### Completed

| Component | File | Change |
|-----------|------|--------|
| BPMN Model | `ZeebeExecutionListenerEventType.java` | Added `cancel` enum value |
| Protocol | `JobListenerEventType.java` | Added `CANCEL` enum value with javadoc |
| Engine Model | `ExecutableFlowNode.java` | Added `getCancelExecutionListeners()` method |
| Engine Behavior | `BpmnJobBehavior.java` | Extended `fromExecutionListenerEventType()` to map `cancel → CANCEL` |
| BPMN Builder | `ZeebeExecutionListenersBuilder.java` | Added `zeebeCancelExecutionListener(type)` and `zeebeCancelExecutionListener(type, retries)` |
| BPMN Builder Impls | `ProcessBuilder`, `AbstractActivityBuilder`, `AbstractEventBuilder`, `AbstractEventSubProcessBuilder` | Implemented cancel EL convenience methods |
| BPMN Validation | `ZeebeExecutionListenersValidationTest.java` | Validation tests for cancel listener |
| Engine Processing | `BpmnStreamProcessor.java` | Handle `ELEMENT_TERMINATING` state in `COMPLETE_EXECUTION_LISTENER` case; add `onCancelExecutionListenerComplete()` |
| Engine Processing | `ProcessProcessor.java` | Trigger cancel ELs on the process element after children terminate; override `finalizeTermination()` |
| State Transition Guard | `ProcessInstanceStateTransitionGuard.java` | Allow `COMPLETE_EXECUTION_LISTENER` when element is in `ELEMENT_TERMINATING` state |
| Incident Resolution | `IncidentResolveProcessor.java` | Map `ELEMENT_TERMINATING` → `COMPLETE_EXECUTION_LISTENER` for incident retry |
| State Applier | `ProcessInstanceElementTerminatingApplier.java` | Reset execution listener index when transitioning to `ELEMENT_TERMINATING` |
| Tests | `ExecutionListenerCancelTest.java` | Unit and integration tests for cancel EL behavior (15 tests passing) |
| API Schema (Zod) | `client-components/packages/camunda-api-zod-schemas/lib/8.9/job.ts` | Added `CANCEL` to `listenerEventTypeSchema` enum |
| Webapps Schema | `ListenerEventType.java` | Added `CANCEL` to webapp-level enum |
| Search Domain | `JobEntity.java`, `JobEntityTransformer.java` | Added `CANCEL` to search entity and transformer |
| Java Client | `ListenerEventType.java` | Added `CANCEL` to Java client search enum |
| REST API Spec | `jobs.yaml`, `rest-api.yaml` | `CANCEL` included in `ListenerEventType` enum |
| gRPC Protocol | `gateway.proto` | `CANCEL` included in `ListenerEventType` enum |
| Operate Schema Test | `ListenerEventTypeTest.java` | Tests for `CANCEL` enum mapping |

### Pending (External Repos — Companion PRs Required)

| Component | Repository | Change |
|-----------|------------|--------|
| Modeler Properties Panel | `bpmn-io/bpmn-js-properties-panel` | Add `cancel` to execution listener event type dropdown (shared by Desktop and Web Modeler) |

> **Note:** The properties panel is a shared component in the external `bpmn-io/bpmn-js-properties-panel`
> repository. A companion PR there is required to expose `cancel` in the execution listener event
> type dropdown. See [Companion Changes Required](#companion-changes-required-external-repos) below.

### Pending (This Repo)

| Component | File | Change |
|-----------|------|--------|
| Documentation | User-facing docs | Cancel execution listener usage guide |
| QA | product-hub#3102 | Cross-component regression tests |

## 2. Blueprint: User Task `CANCELING` Task Listener

The user task `CANCELING` listener (shipped in 8.8) provides the reference pattern. Key
characteristics:

| Aspect | Task Listener (CANCELING) | Proposed Execution Listener (cancel) |
|--------|---------------------------|--------------------------------------|
| Enum value | `ZeebeTaskListenerEventType.canceling` | `ZeebeExecutionListenerEventType.cancel` |
| Job enum | `JobListenerEventType.CANCELING` | `JobListenerEventType.CANCEL` (new value) |
| Job kind | `JobKind.TASK_LISTENER` | `JobKind.EXECUTION_LISTENER` |
| Blocking | Yes — returns `TransitionOutcome.AWAIT` | Yes — returns `TransitionOutcome.AWAIT` |
| Denial support | No (canceling cannot be denied) | No (canceling cannot be denied) |
| Variable propagation | No | Yes (consistent with start/end ELs) |
| Resume mechanism | `UserTaskIntent.CANCELED` → `CONTINUE_TERMINATING_ELEMENT` | `COMPLETE_EXECUTION_LISTENER` → `CONTINUE_TERMINATING_ELEMENT` |
| Failure handling | Incident (`TASK_LISTENER_NO_RETRIES`) | Incident (`EXECUTION_LISTENER_NO_RETRIES`) |

### Task Listener CANCELING Flow (Reference)

```
UserTask termination
  ↓
UserTaskProcessor.onTerminateInternal()
  ├── jobBehavior.cancelJob(context)           — cancel any active job
  ├── userTaskBehavior.userTaskCanceling()      — write CANCELING event
  └── Find first canceling task listener
      ├── Found → createNewTaskListenerJob() → return AWAIT
      └── Not found → userTaskCanceled() → return CONTINUE

  [Worker completes CANCELING task listener job]
  ↓
JobCompleteProcessor → UserTaskIntent.COMPLETE_TASK_LISTENER
  ↓
UserTaskProcessor.processCompleteTaskListener()
  ├── Find next canceling listener
  │   ├── Found → createNewTaskListenerJob()
  │   └── Not found → finalizeCommand() → userTaskCanceled()
  ↓
UserTaskCancelProcessor.onFinalizeCommand()
  └── CONTINUE_TERMINATING_ELEMENT → finalizeTermination()
```

## 3. Proposed Design

### 3.1 Model Changes (✅ Implemented)

#### `ZeebeExecutionListenerEventType`
**File:** `zeebe/bpmn-model/src/main/java/.../ZeebeExecutionListenerEventType.java`

```java
public enum ZeebeExecutionListenerEventType {
  start,
  end,
  cancel   // ADDED
}
```

#### `JobListenerEventType`
**File:** `zeebe/protocol/src/main/java/.../JobListenerEventType.java`

The `CANCEL` value has been added for cancel execution listeners:

```java
/**
 * Represents the `cancel` event for an execution listener. This event type is used to indicate
 * that the listener should be triggered when an element is being terminated, such as due to
 * explicit process instance cancellation. The element termination can't be denied by a listener
 * of this type.
 */
CANCEL
```

> **Decision:** Use a new `CANCEL` value rather than reusing `CANCELING` from the task listener
> section. The existing `CANCELING` is semantically tied to user task lifecycle, while `CANCEL`
> aligns with the execution listener naming convention (`start`/`end`/`cancel`).

#### `ExecutableFlowNode` (✅ Implemented)
**File:** `zeebe/engine/src/main/java/.../element/ExecutableFlowNode.java`

The method to retrieve cancel execution listeners has been added:

```java
public List<ExecutionListener> getCancelExecutionListeners() {
  return executionListeners.stream()
      .filter(el -> el.getEventType() == ZeebeExecutionListenerEventType.cancel)
      .toList();
}
```

### 3.2 Transformer Changes (✅ No Changes Needed)

#### `ExecutionListenerTransformer`
**File:** `zeebe/engine/src/main/java/.../transformer/zeebe/ExecutionListenerTransformer.java`

No changes needed. The transformer already handles arbitrary `ZeebeExecutionListenerEventType`
values; adding `cancel` to the enum is sufficient.

#### `BpmnJobBehavior.fromExecutionListenerEventType()` (✅ Implemented)
**File:** `zeebe/engine/src/main/java/.../behavior/BpmnJobBehavior.java`

The mapping has been extended:

```java
private static JobListenerEventType fromExecutionListenerEventType(
    final ZeebeExecutionListenerEventType eventType) {
  return switch (eventType) {
    case start -> JobListenerEventType.START;
    case end -> JobListenerEventType.END;
    case cancel -> JobListenerEventType.CANCEL;  // NEW
  };
}
```

### 3.3 Engine Processing Changes (✅ Implemented)

#### `BpmnStreamProcessor` — Termination Flow
**File:** `zeebe/engine/src/main/java/.../BpmnStreamProcessor.java`

The `TERMINATE_ELEMENT` case calls `processor.onTerminate()` and, if `CONTINUE` is returned,
calls `processor.finalizeTermination()`. For the `ProcessProcessor`, `onTerminate()` now handles
cancel listener orchestration internally and always returns `AWAIT`:

```
TERMINATE_ELEMENT
  ↓
stateTransitionBehavior.transitionToTerminating(context)
  ↓
processor.onTerminate(element, terminatingContext)
  ↓
ProcessProcessor.onTerminate():
  ├── Cancel any active start/end EL jobs
  ├── Unsubscribe events, resolve incidents, delete compensations
  ├── Terminate child instances
  ├── If no active children remaining:
  │   ├── Has cancel listeners?
  │   │   ├── Yes → createCancelExecutionListenerJob(first listener) — element stays TERMINATING
  │   │   └── No → transitionToTerminated() directly
  │   └── (both paths return AWAIT)
  └── If active children: return AWAIT (wait for children to terminate, then re-enter via onChildTerminated)
```

The `COMPLETE_EXECUTION_LISTENER` handler needs a new case for `ELEMENT_TERMINATING` state:

```java
case COMPLETE_EXECUTION_LISTENER:
  final ProcessInstanceIntent elementState =
      stateBehavior.getElementInstance(context).getState();
  switch (elementState) {
    case ELEMENT_ACTIVATING -> onStartExecutionListenerComplete(...)
    case ELEMENT_COMPLETING -> onEndExecutionListenerComplete(...)
    case ELEMENT_TERMINATING -> onCancelExecutionListenerComplete(...)  // NEW
    default -> throw ...
  }
```

New method:

```java
public void onCancelExecutionListenerComplete(
    final ExecutableFlowNode element,
    final BpmnElementProcessor<ExecutableFlowElement> processor,
    final BpmnElementContext context) {
  mergeVariablesOfExecutionListener(context, false);

  final List<ExecutionListener> listeners = element.getCancelExecutionListeners();
  final int currentListenerIndex =
      stateBehavior.getElementInstance(context).getExecutionListenerIndex();
  final Optional<ExecutionListener> nextListener =
      findNextExecutionListener(listeners, currentListenerIndex);

  if (nextListener.isPresent()) {
    createExecutionListenerJob(context, nextListener.get())
        .ifLeft(failure -> incidentBehavior.createIncident(failure, context));
  } else {
    processor.finalizeTermination(element, context);
  }
}
```

> **Note:** Unlike `onStartExecutionListenerComplete` and `onEndExecutionListenerComplete` which
> return `Either<Failure, ?>`, this method returns `void` and handles incidents inline. The
> `ELEMENT_TERMINATING` case in `BpmnStreamProcessor` calls this without chaining `.ifLeft()`.

The existing `CONTINUE_TERMINATING_ELEMENT` intent (already defined in `ProcessInstanceIntent`)
serves as the mechanism for elements that pause termination for task listeners and then need to
resume — but for cancel execution listeners, the `COMPLETE_EXECUTION_LISTENER` intent suffices
because the `BpmnStreamProcessor` already handles listener completion inline.

#### `ProcessProcessor.onTerminate()` — Process-Level (✅ Implemented)
**File:** `zeebe/engine/src/main/java/.../container/ProcessProcessor.java`

The process-level termination now:
1. Cancels any active execution listener jobs (existing start/end EL jobs)
2. Unsubscribes from events, resolves incidents, deletes compensations
3. Terminates child instances
4. **Wait for all children to terminate** (existing behavior)
5. **Then execute cancel execution listeners** (NEW — after children are terminated)
6. Transition to terminated

The processor also overrides `finalizeTermination()` to handle the transition to `TERMINATED`
state after all cancel listeners complete. This is called by `BpmnStreamProcessor` when the
last cancel EL is completed via `COMPLETE_EXECUTION_LISTENER`.

`onTerminate()` always returns `TransitionOutcome.AWAIT` because termination is either:
- Deferred until children terminate (then cancel ELs fire in `onChildTerminated`)
- Deferred until cancel EL jobs complete (then finalized via `COMPLETE_EXECUTION_LISTENER`)
- Handled inline via `transitionToTerminated()` when no cancel ELs exist

> **Key design decision:** Cancel execution listeners on the process fire **after** all child
> elements have been terminated. This ensures the process-level cancel listener sees a consistent
> state where all child activities have completed their termination (including their own cancel
> listeners, if any in future scope).

#### Supporting Engine Changes (✅ Implemented)

**`ProcessInstanceStateTransitionGuard`** — The guard for `COMPLETE_EXECUTION_LISTENER` now
accepts elements in `ELEMENT_TERMINATING` state (in addition to `ELEMENT_ACTIVATING` and
`ELEMENT_COMPLETING`), allowing cancel EL completion commands to be processed.

**`IncidentResolveProcessor`** — When resolving an incident on an element in `ELEMENT_TERMINATING`
state, the processor now maps it to `COMPLETE_EXECUTION_LISTENER` intent (previously only
`ELEMENT_ACTIVATING` → `ACTIVATE_ELEMENT` and `ELEMENT_COMPLETING` → `COMPLETE_ELEMENT` were
supported). This enables incident resolution for failed cancel EL jobs.

**`ProcessInstanceElementTerminatingApplier`** — When an element transitions to
`ELEMENT_TERMINATING`, the execution listener index is reset to 0. This ensures cancel listeners
start from the first listener, independent of any start/end listener index.

### 3.4 BPMN Model Builder Support (✅ Implemented)

#### `ZeebeExecutionListenersBuilder`
**File:** `zeebe/bpmn-model/src/main/java/.../builder/ZeebeExecutionListenersBuilder.java`

Convenience methods have been added to the builder interface and all implementations
(`ProcessBuilder`, `AbstractActivityBuilder`, `AbstractEventBuilder`,
`AbstractEventSubProcessBuilder`, `ZeebeExecutionListenersBuilderImpl`):

```java
B zeebeCancelExecutionListener(String type, String retries);
B zeebeCancelExecutionListener(String type);
```

### 3.5 Protocol / SBE Changes

No new `ValueType` or `Intent` enums are needed. The existing infrastructure is sufficient:
- `ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER` — already exists
- `ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT` — already exists (used by task listeners)
- `JobKind.EXECUTION_LISTENER` — already exists
- `JobListenerEventType.CANCEL` — new enum value only

### 3.6 Exporter / Search / API Visibility

The new `JobListenerEventType.CANCEL` value will be:
- Serialized in job records via the existing `JobListenerEventType` field
- Exported by all exporters (ES, OS, RDBMS) without changes (they export the raw enum value)
- Visible in the REST API process instance queries as a new listener event type

No REST API spec changes are required for the initial implementation, but the OpenAPI spec
documentation for `JobListenerEventType` should be updated to include the new value.

## 4. Behavioral Questions and Answers

### Q1: What happens when a cancel execution listener job is not completed by a worker, and the instance is being canceled? (@korthout)

**Answer: The cancel listener job blocks termination until it completes, fails exhaustively, or is force-terminated.**

Detailed behavior:

1. **Job remains active:** The cancel EL job stays in `ACTIVATED` state. The element remains in
   `ELEMENT_TERMINATING` state. The process instance remains in `CANCELING` state.

2. **Job timeout:** If the job times out (exceeds its deadline), the engine writes a `TIMED_OUT`
   event and the job returns to `ACTIVATABLE` state. It can be picked up again by a worker. This
   is consistent with how all execution listener and task listener jobs handle timeouts.

3. **Job failure with retries:** If the worker fails the job and retries remain, the job returns
   to `ACTIVATABLE` and can be retried. The element stays `TERMINATING`.

4. **Job failure without retries:** If all retries are exhausted, the engine creates an incident
   with type `EXECUTION_LISTENER_NO_RETRIES`. The process instance enters an incident state.
   An operator must resolve the incident (which retries the job) or force-terminate the instance.

5. **Force termination:** If the operator explicitly cancels the process instance again while the
   cancel EL job is active, the engine should cancel the cancel EL job itself (write
   `JobIntent.CANCELED`) and proceed with termination. This prevents infinite blocking.
   **This is the escape hatch for stuck cancel listeners.**

**Rationale:** This follows the same pattern as the user task `CANCELING` listener and all
existing execution listeners. The blocking behavior is intentional — it gives the listener a
chance to perform cleanup (e.g., notify external systems, release resources). The incident
mechanism provides operator visibility and control.

**Important consideration:** The cancel EL itself is NOT cancellable by another cancel EL. If the
process instance is force-terminated while a cancel EL is running, the cancel EL job is simply
canceled (via `JobIntent.CANCELED`), and termination proceeds without firing any additional
listeners. This prevents infinite recursion.

### Q2: What triggers a cancel execution listener?

The following scenarios trigger cancel execution listeners on the process instance:

| Trigger | Description |
|---------|-------------|
| Explicit cancellation | `CancelProcessInstance` command via gRPC/REST API |
| Process instance migration (cancel) | When migrating replaces or removes elements |
| Terminate end event | End event with terminate semantics |

> **Note:** Interrupting boundary events and interrupting event sub-processes do **not** trigger
> cancel execution listeners on the process. These interruptions cause the process to complete
> normally (via the interrupting flow), not to terminate. Cancel ELs only fire when the process
> element itself transitions to `ELEMENT_TERMINATING` state.

### Q3: Are cancel listeners fired for individual elements or only the process?

**Current scope: Process level only.** The initial implementation targets cancel execution
listeners on the process instance element. Individual BPMN elements (tasks, subprocesses,
gateways) do NOT get cancel execution listeners in this iteration.

However, the design is intentionally generic — `ExecutableFlowNode.getCancelExecutionListeners()`
works for any element in the hierarchy. Future iterations could enable per-element cancel listeners
without architectural changes.

### Q4: Can a cancel listener deny the cancellation?

**No.** Consistent with the user task `CANCELING` listener, cancel execution listeners cannot
deny the cancellation. They are informational/cleanup-only. The `JobCompleteProcessor` does not
check for denial on execution listener jobs (only on task listener jobs).

### Q5: What is the variable scope for cancel execution listeners?

Cancel execution listeners execute in the scope of the terminating element, consistent with
start and end execution listeners. Variables set by the listener are merged into the element's
scope. Since the process is being terminated, these variables are primarily useful for:
- Passing data to subsequent cancel listeners in the chain
- Being captured by exporters for audit/logging purposes

### Q6: What about multi-instance elements and cancel listeners?

For multi-instance elements at the process level:
- Cancel listeners fire once for the process instance, not per multi-instance iteration
- Individual multi-instance element instances are terminated by the child termination mechanism
  before the process-level cancel listener fires

### Q7: Ordering of cancel listeners relative to child termination?

```
Process Instance Cancel Command
  ↓
1. Cancel any active start/end EL jobs on the process element
2. Unsubscribe events, resolve incidents
3. Terminate all child elements (recursive, including their termination logic)
4. [Wait for all children to reach TERMINATED state]
5. Fire cancel execution listeners on the process element (sequential, blocking)
6. Transition process to ELEMENT_TERMINATED
```

This ordering ensures cancel listeners see a consistent, fully-terminated child state.

## 5. Component Impact Summary

### Engine (zeebe/engine/)

| File | Change |
|------|--------|
| `ZeebeExecutionListenerEventType.java` | Add `cancel` enum value |
| `ExecutableFlowNode.java` | Add `getCancelExecutionListeners()` |
| `BpmnStreamProcessor.java` | Handle `ELEMENT_TERMINATING` in `COMPLETE_EXECUTION_LISTENER`; add cancel listener orchestration during termination |
| `BpmnJobBehavior.java` | Extend `fromExecutionListenerEventType()` mapping |
| `ProcessProcessor.java` | Trigger cancel ELs after children terminate; override `finalizeTermination()` |
| `ProcessInstanceStateTransitionGuard.java` | Allow `COMPLETE_EXECUTION_LISTENER` when element is in `ELEMENT_TERMINATING` state |
| `IncidentResolveProcessor.java` | Map `ELEMENT_TERMINATING` state → `COMPLETE_EXECUTION_LISTENER` intent for incident retry |
| `ProcessInstanceElementTerminatingApplier.java` | Reset execution listener index when transitioning to `ELEMENT_TERMINATING` state |

### Protocol (zeebe/protocol/)

| File | Change |
|------|--------|
| `JobListenerEventType.java` | Add `CANCEL` enum value |

### BPMN Model (zeebe/bpmn-model/)

| File | Change |
|------|--------|
| `ZeebeExecutionListenerEventType.java` | Add `cancel` value |
| `ExecutionListenerBuilder.java` | Add convenience method for cancel listeners |

### No Changes Required

| Component | Reason |
|-----------|--------|
| `ProcessInstanceIntent` | `COMPLETE_EXECUTION_LISTENER` and `CONTINUE_TERMINATING_ELEMENT` already exist |
| `JobKind` | `EXECUTION_LISTENER` already exists |
| Exporters (ES, OS, RDBMS) | Export raw enum values; no schema changes needed |
| `ExecutionListenerTransformer` | Already handles arbitrary event types |
| REST API spec | No new endpoints; `CANCEL` value auto-exposed via existing job queries |
| SBE protocol | No new value types or intents |

### Companion Changes Required (External Repos)

| Component | Repository | Work | Status |
|-----------|------------|------|--------|
| Modeler Properties Panel (shared by Desktop & Web) | `bpmn-io/bpmn-js-properties-panel` | Add `cancel` to the execution listener event type options in the process-level properties panel (EL dropdown) | **TODO** — companion PR required before this feature is user-facing |

> **Blocking dependency:** The backend changes in this PR add `cancel` as a valid
> `ZeebeExecutionListenerEventType`, but the Desktop/Web Modeler properties panel
> (in `bpmn-io/bpmn-js-properties-panel`) must also be updated to expose `cancel`
> in the execution listener event type dropdown. **This PR should not be merged
> until the companion PR in `bpmn-io/bpmn-js-properties-panel` is merged or at
> minimum opened and linked here.** Without that companion PR, users cannot
> configure cancel execution listeners through the modeler UI.
>
> The properties panel is a **shared component** used by both Desktop Modeler and
> Web Modeler — a single PR in `bpmn-io/bpmn-js-properties-panel` covers both.
> The expected change is to add `cancel` to the event type options array in the
> execution listener properties entry (alongside `start` and `end`).

### Future Work (Out of Scope)

| Component | Work |
|-----------|------|
| Documentation | User-facing docs for cancel execution listeners |
| QA | Cross-component regression tests (product-hub#3102) |
| Per-element cancel listeners | Extend cancel ELs beyond process level to activities, subprocesses |

## 6. Testing Strategy

### Unit Tests

- **New test class:** `ExecutionListenerCancelTest` (or extend `ExecutionListenerProcessInstanceTest`)
  - Process with cancel EL → cancel instance → verify cancel EL job created
  - Cancel EL job completed → verify process transitions to TERMINATED
  - Cancel EL job failed (no retries) → verify incident created
  - Cancel EL job timed out → verify job retried
  - Multiple cancel ELs → verify sequential execution
  - Cancel EL with variables → verify variable merge
  - Process without cancel ELs → verify normal termination (regression)
  - Force cancel during active cancel EL → verify cancel EL job canceled

### Integration Tests

- Cancel EL with external worker (end-to-end job lifecycle)
- Cancel EL with incident resolution flow
- Process with start + end + cancel ELs → verify ordering

## 7. Migration and Compatibility

- **Backward compatible:** Existing processes without cancel ELs behave identically
- **Deployment versioning:** New `cancel` event type requires deploying new process versions
- **Rolling updates:** The `CANCEL` value in `JobListenerEventType` is additive; older brokers
  will not create cancel EL jobs (they don't know the event type), newer brokers handle them
- **C7 migration:** This feature unblocks migration of C7 processes that use execution listeners
  on process-level `end` events triggered by cancellation
