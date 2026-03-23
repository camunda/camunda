# Migrating from Camunda 7 Process Engine Plugins to Camunda 8 Global Execution Listeners

This guide helps Camunda 7 users migrate process engine plugin patterns — particularly
`BpmnParseListener` and `ExecutionListener` — to Camunda 8's global execution listeners. It covers
the conceptual mapping, configuration examples, and common use cases.

## Overview

In Camunda 7, process engine plugins provided global hooks into the process engine lifecycle. The
most common pattern was using `BpmnParseListener` to automatically attach `ExecutionListener`
instances to every process definition at parse time — enabling cross-cutting concerns like audit
logging, metrics collection, and data replication without modifying BPMN models.

Camunda 8 replaces this pattern with **global execution listeners**: cluster-wide, configuration-
driven lifecycle hooks that fire on process and element events across all process definitions.

### What changed

| Aspect | Camunda 7 | Camunda 8 |
|--------|-----------|-----------|
| Mechanism | Java plugin classes loaded into the engine JVM | External job workers triggered by the engine |
| Configuration | `camunda.cfg.xml` or Spring `@Configuration` | REST API, Admin UI, or YAML config file |
| Execution model | In-process, synchronous Java method calls | Out-of-process, asynchronous job-based (but blocking) |
| Scoping | Code-level: `BpmnParseListener` injects listeners per element type programmatically | Declarative: `eventTypes`, `elementTypes`, `categories` fields |
| Deployment | Packaged in the engine WAR/JAR, requires restart | Runtime API (no restart) or config file (restart on change) |
| Language | Java only | Any language with a Zeebe job worker SDK |

### What stayed the same

- **Blocking semantics**: Both C7 execution listeners and C8 global execution listeners block
  element progression until the listener completes.
- **Variable access**: Listeners can read process variables and write variables back into scope.
- **Element-level granularity**: You can target specific element types (tasks, gateways, events)
  and lifecycle phases (start, end).
- **Ordering control**: C7 had listener ordering via `BpmnParseListener` injection order. C8 has
  explicit `priority` and `afterNonGlobal` fields.

---

## Camunda 7 Patterns and Their Camunda 8 Equivalents

### Pattern 1: Global audit listener via `BpmnParseListener`

This is the most common C7 plugin pattern — injecting an `ExecutionListener` on every activity to
capture lifecycle events for audit or compliance purposes.

**Camunda 7** (Java):

```java
public class AuditParseListener extends AbstractBpmnParseListenerPlugin {

    @Override
    public void parseStartEvent(Element element, ActivityImpl activity) {
        activity.addListener(ExecutionListener.EVENTNAME_START, new AuditExecutionListener());
        activity.addListener(ExecutionListener.EVENTNAME_END, new AuditExecutionListener());
    }

    @Override
    public void parseServiceTask(Element element, ActivityImpl activity) {
        activity.addListener(ExecutionListener.EVENTNAME_START, new AuditExecutionListener());
        activity.addListener(ExecutionListener.EVENTNAME_END, new AuditExecutionListener());
    }

    @Override
    public void parseUserTask(Element element, TaskDefinition taskDefinition) {
        taskDefinition.addTaskListener(TaskListener.EVENTNAME_CREATE, new AuditTaskListener());
        taskDefinition.addTaskListener(TaskListener.EVENTNAME_COMPLETE, new AuditTaskListener());
    }

    // ... repeated for each element type
}

public class AuditExecutionListener implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        String eventName = execution.getEventName();
        String processDefinitionId = execution.getProcessDefinitionId();
        String processInstanceId = execution.getProcessInstanceId();

        auditService.log(processDefinitionId, processInstanceId, activityId, eventName);
    }
}
```

**Camunda 7 configuration** (`camunda.cfg.xml`):

```xml
<bean id="processEngineConfiguration"
      class="org.camunda.bpm.engine.impl.cfg.StandaloneProcessEngineConfiguration">
  <property name="processEnginePlugins">
    <list>
      <bean class="com.example.AuditParseListener" />
    </list>
  </property>
</bean>
```

**Camunda 8 equivalent** — one REST API call replaces the entire plugin class:

```bash
curl -X POST http://localhost:8080/v2/global-execution-listeners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "audit-all-elements",
    "type": "audit-execution-event",
    "eventTypes": ["start", "end"],
    "categories": ["all"]
  }'
```

Or via configuration file (`application.yaml`):

```yaml
camunda:
  listener:
    execution:
      - id: audit-all-elements
        eventTypes: [start, end]
        type: audit-execution-event
        categories: [all]
```

**Job worker** (Java, using the Camunda 8 Java SDK):

```java
@JobWorker(type = "audit-execution-event")
public void handleAuditEvent(@Variable Map<String, Object> variables,
                              ActivatedJob job) {
    String elementId = job.getElementId();
    String elementType = job.getCustomHeaders().get("elementType");
    String eventType = job.getCustomHeaders().get("eventType");
    long processInstanceKey = job.getProcessInstanceKey();
    String bpmnProcessId = job.getBpmnProcessId();

    auditService.log(bpmnProcessId, processInstanceKey, elementId, eventType);
}
```

> **Key difference**: In C7, the `AuditExecutionListener` Java class ran inside the engine JVM. In
> C8, the `audit-execution-event` job worker runs externally and communicates with the engine via
> the job protocol. This decouples the listener logic from the engine, enabling polyglot
> implementations and independent scaling.

---

### Pattern 2: Process lifecycle tracking

**Camunda 7** — a plugin that tracks process instance start, completion, and cancellation:

```java
public class ProcessLifecyclePlugin extends AbstractBpmnParseListenerPlugin {

    @Override
    public void parseProcess(Element element, ProcessDefinitionEntity processDefinition) {
        processDefinition.addListener(
            ExecutionListener.EVENTNAME_START, new ProcessStartListener());
        processDefinition.addListener(
            ExecutionListener.EVENTNAME_END, new ProcessEndListener());
    }
}

public class ProcessStartListener implements ExecutionListener {
    @Override
    public void notify(DelegateExecution execution) {
        if (!execution.hasVariable("_tracked")) {
            metricsService.recordProcessStart(
                execution.getProcessDefinitionId(),
                execution.getProcessInstanceId()
            );
            execution.setVariable("_tracked", true);
        }
    }
}
```

**Camunda 8 equivalent**:

```yaml
camunda:
  listener:
    execution:
      - id: process-lifecycle
        eventTypes: [start, end, cancel]
        type: process-lifecycle-tracker
        elementTypes: [process]
        retries: 3
```

This is the recommended starting point for any migration — it provides the same process-level
visibility as a C7 `parseProcess` plugin with minimal overhead (2–3 jobs per process instance).

> **Note on `cancel`**: Camunda 7 did not have a distinct "cancel" execution event — process
> cancellation was observed indirectly. Camunda 8 introduces `cancel` as a first-class event type,
> but only on `process` elements. This simplifies detecting whether a process instance completed
> normally (`end`) or was terminated (`cancel`).

---

### Pattern 3: Service task monitoring

**Camunda 7** — monitoring service task execution time:

```java
public class ServiceTaskMonitorPlugin extends AbstractBpmnParseListenerPlugin {

    @Override
    public void parseServiceTask(Element element, ActivityImpl activity) {
        activity.addListener(ExecutionListener.EVENTNAME_START,
            new TimerStartListener());
        activity.addListener(ExecutionListener.EVENTNAME_END,
            new TimerEndListener());
    }
}
```

**Camunda 8 equivalent**:

```yaml
camunda:
  listener:
    execution:
      - id: service-task-monitor
        eventTypes: [start, end]
        type: monitor-service-tasks
        elementTypes: [serviceTask]
        retries: 3
        priority: 50
```

The job worker receives both `start` and `end` events with process instance metadata, allowing
calculation of task durations, SLA tracking, and alerting.

---

### Pattern 4: Gateway routing audit

**Camunda 7** — logging which path was taken at exclusive gateways:

```java
public class GatewayAuditPlugin extends AbstractBpmnParseListenerPlugin {

    @Override
    public void parseExclusiveGateway(Element element, ActivityImpl activity) {
        activity.addListener(ExecutionListener.EVENTNAME_START,
            new GatewayRoutingListener());
    }
}
```

**Camunda 8 equivalent**:

```yaml
camunda:
  listener:
    execution:
      - id: gateway-routing-audit
        eventTypes: [start]
        type: gateway-audit
        categories: [gateways]
```

> **Note**: Gateways in Camunda 8 only support the `start` event type. The `end` event is not
> available because gateways evaluate and route without a distinct completion lifecycle. This
> matches the practical behavior in C7 where `end` on gateways was rarely useful.

---

## Migration Checklist

Use this checklist when migrating each C7 process engine plugin to C8 global execution listeners.

### Step 1: Inventory your C7 plugins

List all `ProcessEnginePlugin` and `BpmnParseListener` implementations in your C7 deployment.
For each, identify:

- Which element types does it target? (`parseServiceTask`, `parseUserTask`, `parseProcess`, etc.)
- Which lifecycle events? (`start`, `end`, `take` on sequence flows)
- What does the listener logic do? (audit, metrics, variable enrichment, external system call)

### Step 2: Map element types

Map C7 `BpmnParseListener` methods to C8 `elementTypes` or `categories`:

| C7 method | C8 `elementTypes` | C8 `categories` |
|-----------|-------------------|-----------------|
| `parseProcess` | `process` | — |
| `parseServiceTask` | `serviceTask` | `tasks` (includes all task types) |
| `parseUserTask` | `userTask` | `tasks` |
| `parseSendTask` | `sendTask` | `tasks` |
| `parseReceiveTask` | `receiveTask` | `tasks` |
| `parseScriptTask` | `scriptTask` | `tasks` |
| `parseBusinessRuleTask` | `businessRuleTask` | `tasks` |
| `parseCallActivity` | `callActivity` | — |
| `parseSubProcess` | `subprocess` | — |
| `parseExclusiveGateway` | `exclusiveGateway` | `gateways` |
| `parseParallelGateway` | `parallelGateway` | `gateways` |
| `parseInclusiveGateway` | `inclusiveGateway` | `gateways` |
| `parseStartEvent` | `startEvent` | `events` |
| `parseEndEvent` | `endEvent` | `events` |
| `parseIntermediateCatchEvent` | `intermediateCatchEvent` | `events` |
| `parseIntermediateThrowEvent` | `intermediateThrowEvent` | `events` |
| `parseBoundaryEvent` | `boundaryEvent` | `events` |

> **Tip**: If your C7 plugin targets all element types by implementing every `parse*` method,
> use `categories: [all]` in C8 — but be aware of the
> [performance implications](global-execution-listeners.md#performance-considerations).

### Step 3: Map lifecycle events

| C7 `ExecutionListener` event | C8 `eventTypes` | Notes |
|------------------------------|------------------|-------|
| `ExecutionListener.EVENTNAME_START` | `start` | Direct equivalent |
| `ExecutionListener.EVENTNAME_END` | `end` | Direct equivalent |
| (no C7 equivalent) | `cancel` | New in C8; only on `process` elements |
| `ExecutionListener.EVENTNAME_TAKE` | — | **No C8 equivalent** — sequence flow listeners are not supported |

> **Breaking change**: Camunda 7's `take` event (fired when a sequence flow is traversed) has no
> equivalent in Camunda 8. If your C7 plugin uses `take` listeners, you must redesign the logic
> to use `end` on the source element or `start` on the target element instead.

### Step 4: Extract listener logic into a job worker

In C7, listener logic runs as a Java method inside the engine. In C8, it must be extracted into an
external job worker. The general pattern:

1. **Create a job worker** that handles the listener's job type.
2. **Read execution context** from the `ActivatedJob` object instead of `DelegateExecution`:
   - `job.getProcessInstanceKey()` replaces `execution.getProcessInstanceId()`
   - `job.getBpmnProcessId()` replaces `execution.getProcessDefinitionId()`
   - `job.getElementId()` replaces `execution.getCurrentActivityId()`
   - `job.getVariablesAsMap()` replaces `execution.getVariables()`
3. **Write variables** via the job completion response instead of `execution.setVariable()`.
4. **Handle errors** by throwing `ZeebeFault` or failing the job with a retry, instead of throwing
   Java exceptions that the engine catches.

**C7 listener** → **C8 job worker** field mapping:

| C7 `DelegateExecution` method | C8 `ActivatedJob` equivalent |
|-------------------------------|------------------------------|
| `getProcessDefinitionId()` | `getBpmnProcessId()` |
| `getProcessInstanceId()` | `getProcessInstanceKey()` (returns `long`) |
| `getCurrentActivityId()` | `getElementId()` |
| `getCurrentActivityName()` | (not directly available — use element ID or process model metadata) |
| `getEventName()` | `getCustomHeaders().get("eventType")` or inferred from `getListenerEventType()` |
| `getVariables()` | `getVariablesAsMap()` |
| `getVariable(name)` | `getVariable(name)` |
| `setVariable(name, value)` | Set via `client.newCompleteCommand(job).variables(map)` |
| `getBusinessKey()` | (not available — use process variables to store business keys) |
| `getTenantId()` | `getTenantId()` |

### Step 5: Register global execution listeners

Choose one of three registration methods:

**Option A — REST API** (recommended for CI/CD pipelines and SaaS):

```bash
curl -X POST http://localhost:8080/v2/global-execution-listeners \
  -H "Content-Type: application/json" \
  -d '{
    "id": "my-audit-listener",
    "type": "audit-execution-event",
    "eventTypes": ["start", "end"],
    "elementTypes": ["process", "serviceTask", "userTask"],
    "retries": 3,
    "priority": 100
  }'
```

**Option B — Configuration file** (recommended for Self-Managed, GitOps workflows):

```yaml
camunda:
  listener:
    execution:
      - id: my-audit-listener
        eventTypes: [start, end]
        type: audit-execution-event
        elementTypes: [process, serviceTask, userTask]
        retries: 3
        priority: 100
```

**Option C — Admin UI** (recommended for ad-hoc management):

Navigate to **Listeners → Execution** in the Identity Admin UI and use the "Add listener" form.

### Step 6: Validate and test

1. Deploy a test process to the cluster.
2. Start a process instance.
3. Verify that your job worker receives listener jobs by checking:
   - The job type matches your configured `type`.
   - The `elementId`, `elementType`, and event metadata are correct.
   - Variables are accessible in the job payload.
4. Check Operate for listener activity — filter by **Source: Global** to see global listener jobs.
5. Simulate a failure to verify incident handling and retry behavior.

---

## Common Use Cases

### Audit and compliance

**Goal**: Capture every process lifecycle event for regulatory reporting.

**Recommended configuration** — start minimal and expand:

```yaml
camunda:
  listener:
    execution:
      # Process start/end/cancel — low overhead, high value
      - id: audit-process
        eventTypes: [start, end, cancel]
        type: compliance-audit
        elementTypes: [process]
        retries: 5
        priority: 100
      # User task tracking — for approval audit trails
      - id: audit-user-tasks
        eventTypes: [start, end]
        type: compliance-audit
        elementTypes: [userTask]
        retries: 5
        priority: 90
```

**Job worker logic**: Log the event to an audit database or compliance system. Include the process
instance key, element ID, event type, timestamp, variables (filtered to relevant business data),
and tenant ID.

```java
@JobWorker(type = "compliance-audit")
public void handleAuditEvent(ActivatedJob job) {
    AuditRecord record = AuditRecord.builder()
        .processInstanceKey(job.getProcessInstanceKey())
        .bpmnProcessId(job.getBpmnProcessId())
        .elementId(job.getElementId())
        .eventType(job.getListenerEventType().name())
        .tenantId(job.getTenantId())
        .timestamp(Instant.now())
        .variables(filterSensitiveData(job.getVariablesAsMap()))
        .build();

    auditRepository.save(record);
}
```

> **Performance note**: This configuration generates ~4–5 jobs per process instance (process
> start + end/cancel + user task start + end per user task). For most compliance needs, this is
> sufficient without targeting all element types.

---

### Real-time data replication

**Goal**: Replicate process instance state changes to an external database or data lake.

```yaml
camunda:
  listener:
    execution:
      - id: replicate-process-state
        eventTypes: [start, end, cancel]
        type: state-replicator
        elementTypes: [process]
        retries: 3
        priority: 100
      - id: replicate-task-state
        eventTypes: [start, end]
        type: state-replicator
        categories: [tasks]
        retries: 3
        priority: 80
```

**Job worker logic**: Publish an event to a message broker (Kafka, RabbitMQ) or write directly to
a data warehouse. The shared `type: state-replicator` means a single worker handles all events,
routing by element type and event type in the payload.

```java
@JobWorker(type = "state-replicator")
public void replicateState(ActivatedJob job) {
    StateChangeEvent event = StateChangeEvent.builder()
        .processInstanceKey(job.getProcessInstanceKey())
        .elementId(job.getElementId())
        .elementType(job.getCustomHeaders().get("elementType"))
        .eventType(job.getListenerEventType().name())
        .variables(job.getVariablesAsMap())
        .build();

    kafkaTemplate.send("process-state-changes", event);
}
```

> **Alternative**: For non-blocking, high-throughput replication where you do not need to modify
> variables or block execution, consider using a Zeebe exporter instead. Exporters are
> asynchronous and process the full event stream without adding latency to process execution.

---

### Observability and tracing

**Goal**: Instrument all processes with execution tracing for monitoring and alerting.

```yaml
camunda:
  listener:
    execution:
      - id: trace-process
        eventTypes: [start, end, cancel]
        type: execution-tracer
        elementTypes: [process]
        retries: 1
        priority: 100
      - id: trace-service-tasks
        eventTypes: [start, end]
        type: execution-tracer
        elementTypes: [serviceTask]
        retries: 1
        priority: 50
```

**Job worker logic**: Create spans in a distributed tracing system (OpenTelemetry, Jaeger):

```java
@JobWorker(type = "execution-tracer")
public void traceExecution(ActivatedJob job) {
    String eventType = job.getListenerEventType().name();
    String elementId = job.getElementId();

    if ("START".equals(eventType)) {
        Span span = tracer.spanBuilder("process:" + elementId)
            .setAttribute("process.instance.key",
                String.valueOf(job.getProcessInstanceKey()))
            .setAttribute("bpmn.process.id", job.getBpmnProcessId())
            .startSpan();
        spanStore.put(job.getElementInstanceKey(), span);
    } else {
        Span span = spanStore.remove(job.getElementInstanceKey());
        if (span != null) {
            span.end();
        }
    }
}
```

> **Performance note**: Set `retries: 1` for observability listeners — retrying a tracing event
> is usually not worth the latency cost. If the job fails, the incident is logged but does not
> block execution indefinitely.

---

### CRM and external system integration

**Goal**: Notify a CRM system when a process starts or a service task completes.

**Camunda 7 approach** (process engine plugin):

```java
public class CrmPlugin extends AbstractBpmnParseListenerPlugin {
    @Override
    public void parseServiceTask(Element element, ActivityImpl activity) {
        activity.addListener(ExecutionListener.EVENTNAME_END, execution -> {
            String orderId = (String) execution.getVariable("orderId");
            crmClient.updateStatus(orderId, "task-completed",
                execution.getCurrentActivityId());
        });
    }
}
```

**Camunda 8 equivalent**:

```yaml
camunda:
  listener:
    execution:
      - id: crm-service-task-notify
        eventTypes: [end]
        type: crm-notifier
        elementTypes: [serviceTask]
        retries: 3
```

```java
@JobWorker(type = "crm-notifier")
public void notifyCrm(ActivatedJob job,
                       @Variable String orderId) {
    crmClient.updateStatus(orderId, "task-completed", job.getElementId());
}
```

---

### Variable enrichment during execution

**Goal**: Inject data from an external system into process variables at element start.

**Camunda 7 approach**:

```java
activity.addListener(ExecutionListener.EVENTNAME_START, execution -> {
    String customerId = (String) execution.getVariable("customerId");
    CustomerData data = customerService.lookup(customerId);
    execution.setVariable("customerName", data.getName());
    execution.setVariable("customerTier", data.getTier());
});
```

**Camunda 8 equivalent**:

```yaml
camunda:
  listener:
    execution:
      - id: enrich-customer-data
        eventTypes: [start]
        type: customer-enrichment
        elementTypes: [serviceTask, userTask]
        retries: 3
        priority: 200
```

```java
@JobWorker(type = "customer-enrichment")
public Map<String, Object> enrichCustomerData(ActivatedJob job,
                                               @Variable String customerId) {
    CustomerData data = customerService.lookup(customerId);
    return Map.of(
        "customerName", data.getName(),
        "customerTier", data.getTier()
    );
}
```

> **Note**: Set a high `priority` (e.g., 200) and `afterNonGlobal: false` (the default) to ensure
> enrichment runs before other listeners and before the element's main logic executes.

---

## Concepts That Do Not Migrate Directly

### Sequence flow listeners (`take` event)

Camunda 7's `ExecutionListener.EVENTNAME_TAKE` fired when a sequence flow was traversed. Camunda 8
does not support sequence flow listeners. To achieve similar behavior:

- Use `end` on the source element to capture when it completes (just before the sequence flow).
- Use `start` on the target element to capture when execution arrives.

### `DelegateExecution` API

The `DelegateExecution` interface provided rich access to the engine's internal state (activity
instance tree, process definition model, parent execution). In Camunda 8, the `ActivatedJob` object
provides a focused subset:

- Process metadata: `bpmnProcessId`, `processInstanceKey`, `processDefinitionKey`,
  `processDefinitionVersion`
- Element metadata: `elementId`, `elementInstanceKey`
- Variables: full snapshot at element scope
- Tenant: `tenantId`

Features like traversing the activity instance tree or reading the BPMN model XML are not available
from the job worker. Use the Camunda 8 REST API or Operate API for runtime introspection.

### Shared in-process state

C7 plugins could share state within the engine JVM (e.g., caching service results in a
`ConcurrentHashMap`, using CDI/Spring beans directly). C8 job workers are external processes — use
external caches (Redis), databases, or message brokers for shared state.

### Transactional consistency

C7 execution listeners ran within the same database transaction as the engine. If a listener failed,
the entire transaction rolled back. C8 global execution listeners are eventually consistent — the
listener job is a separate unit of work. If a listener job fails, it creates an incident that must
be resolved, but the engine does not roll back prior state changes.

---

## Frequently Asked Questions

### Can I use global execution listeners and BPMN-level listeners on the same element?

Yes. By default, global listeners run **before** BPMN-level listeners. Set `afterNonGlobal: true`
to run **after**. The `priority` field controls ordering among global listeners.

### Do global execution listeners affect all tenants?

Yes. Global execution listener configuration is cluster-wide, not per-tenant. Each listener job
includes a `tenantId` field so your job worker can route or filter by tenant.

### What happens if my job worker is down?

The listener job stays in the queue. Since listeners are blocking, the element execution is paused
until the job is picked up and completed (or fails and creates an incident). This is the same
behavior as BPMN-level execution listeners.

### Can I modify variables from a global execution listener?

Yes. Completing the listener job with variables writes them into the element's scope — same as
BPMN-level execution listeners. `start` listeners set local variables; `end` listeners set
parent-scope variables.

### How do I know if an incident is from a global listener or a BPMN-level listener?

Operate displays a **Source** column on the listeners tab and the incidents table. Global listener
incidents show "Global" and BPMN-level listener incidents show "Model".

### Should I use global execution listeners or exporters?

| Use case | Global execution listeners | Exporters |
|----------|---------------------------|-----------|
| Need to block execution | ✅ Yes | ❌ No (async) |
| Need to set variables | ✅ Yes | ❌ No (read-only) |
| High-volume event streaming | ⚠️ Adds latency | ✅ Designed for this |
| Need real-time observability | ⚠️ Blocking overhead | ✅ Non-blocking |
| Need to modify process flow | ✅ Yes (via variables) | ❌ No |
| Audit logging (blocking required) | ✅ Yes | ❌ No |
| Audit logging (non-blocking OK) | ⚠️ Overhead | ✅ Better fit |

**Rule of thumb**: Use global execution listeners when you need to block execution or write
variables. Use exporters when you only need to observe events without affecting execution.

### How does this compare to C7's `TaskListener` for user tasks?

C7 `TaskListener` (create, assign, complete) maps to Camunda 8 **global task listeners** — a
separate feature with its own API at `/v2/global-task-listeners`. Global execution listeners and
global task listeners are complementary:

- **Global task listeners** fire on user task lifecycle events: creating, assigning, updating,
  completing, canceling.
- **Global execution listeners** fire on BPMN element lifecycle events: start, end, cancel.

Both can target user tasks, but they serve different purposes. If you had a C7 `TaskListener`
for `create`/`complete` events, migrate to global task listeners. If you had a C7
`ExecutionListener` for start/end on user tasks, migrate to global execution listeners.

---

## Quick Reference: C7 Plugin → C8 Configuration

| C7 plugin pattern | C8 configuration |
|-------------------|------------------|
| `BpmnParseListener` with `parseProcess` + start/end | `eventTypes: [start, end, cancel], elementTypes: [process]` |
| `BpmnParseListener` with all `parseServiceTask`/`parseUserTask`/... | `eventTypes: [start, end], categories: [tasks]` |
| `BpmnParseListener` on all element types | `eventTypes: [start, end], categories: [all]` |
| Gateway routing listener | `eventTypes: [start], categories: [gateways]` |
| Subprocess lifecycle | `eventTypes: [start, end], elementTypes: [subprocess, eventSubprocess]` |
| Process cancellation detection | `eventTypes: [cancel], elementTypes: [process]` |

---

## Further Reading

- [Global Execution Listeners — API Reference](global-execution-listeners.md)
- [Camunda 8 Migration Guide](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/)
- [Zeebe Job Workers](https://docs.camunda.io/docs/components/concepts/job-workers/)
- [Global Task Listeners](https://docs.camunda.io/docs/components/concepts/global-task-listeners/)
