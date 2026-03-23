# Global Execution Listeners — API Reference

Global execution listeners provide cluster-wide lifecycle hooks for BPMN process and element
events. They use the same job worker infrastructure as BPMN-level execution listeners but are
configured at cluster scope — no BPMN model modifications required.

## Overview

Global execution listeners fire on `start`, `end`, and `cancel` events of BPMN flow elements
(activities, gateways, events, processes). They are registered via:

- **REST API** (`/v2/global-execution-listeners`) — runtime CRUD, no restart required
- **Configuration file** (`camunda.listener.execution`) — Self-Managed, loaded at startup

Listener jobs are **blocking**: the engine waits for the job worker to complete before element
execution proceeds.

---

## REST API Endpoints

Base path: `/v2/global-execution-listeners`

All endpoints require authentication. Resource-based permissions control access.

### Create global execution listener

```
POST /v2/global-execution-listeners
```

Registers a new global execution listener. The listener immediately applies to all matching
element lifecycle events across all processes in the cluster.

**Request body** (required):

```json
{
  "id": "audit-process-lifecycle",
  "type": "audit-execution-event",
  "eventTypes": ["start", "end", "cancel"],
  "elementTypes": ["process"],
  "retries": 3,
  "afterNonGlobal": false,
  "priority": 100
}
```

**Response** `201 Created`:

```json
{
  "id": "audit-process-lifecycle",
  "type": "audit-execution-event",
  "eventTypes": ["start", "end", "cancel"],
  "elementTypes": ["process"],
  "categories": [],
  "retries": 3,
  "afterNonGlobal": false,
  "priority": 100,
  "source": "API"
}
```

**Error responses**:

| Status | Condition |
|--------|-----------|
| `400`  | Validation error (missing required fields, unsupported event-element combination) |
| `401`  | Not authenticated |
| `403`  | Insufficient permissions |
| `409`  | A global listener with this `id` already exists |
| `500`  | Internal server error |
| `503`  | Service unavailable |

---

### Get global execution listener

```
GET /v2/global-execution-listeners/{id}
```

Retrieves a global execution listener by its ID. This endpoint is **eventually consistent** —
recently created or updated listeners may not be immediately visible.

**Path parameters**:

| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| `id`      | string | yes      | The listener ID |

**Response** `200 OK`:

```json
{
  "id": "audit-process-lifecycle",
  "type": "audit-execution-event",
  "eventTypes": ["start", "end", "cancel"],
  "elementTypes": ["process"],
  "categories": [],
  "retries": 3,
  "afterNonGlobal": false,
  "priority": 100,
  "source": "API"
}
```

**Error responses**:

| Status | Condition |
|--------|-----------|
| `401`  | Not authenticated |
| `403`  | Insufficient permissions |
| `404`  | Listener with given ID not found |
| `500`  | Internal server error |

---

### Update global execution listener

```
PUT /v2/global-execution-listeners/{id}
```

Replaces the configuration of an existing global execution listener. The `id` in the path
identifies the listener to update. All mutable fields must be provided.

**Path parameters**:

| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| `id`      | string | yes      | The listener ID |

**Request body** (required):

```json
{
  "type": "audit-execution-event",
  "eventTypes": ["start", "end"],
  "elementTypes": ["process", "serviceTask"],
  "retries": 5,
  "afterNonGlobal": true,
  "priority": 200
}
```

**Response** `200 OK`: Returns the updated listener (same shape as create response).

**Error responses**:

| Status | Condition |
|--------|-----------|
| `400`  | Validation error |
| `401`  | Not authenticated |
| `403`  | Insufficient permissions |
| `404`  | Listener not found |
| `500`  | Internal server error |
| `503`  | Service unavailable |

---

### Delete global execution listener

```
DELETE /v2/global-execution-listeners/{id}
```

Removes a global execution listener. In-flight listener jobs are not affected — only future
element events stop triggering this listener.

**Path parameters**:

| Parameter | Type   | Required | Description |
|-----------|--------|----------|-------------|
| `id`      | string | yes      | The listener ID |

**Response** `204 No Content`: Listener deleted successfully (empty body).

**Error responses**:

| Status | Condition |
|--------|-----------|
| `400`  | Invalid ID format |
| `401`  | Not authenticated |
| `403`  | Insufficient permissions |
| `404`  | Listener not found |
| `500`  | Internal server error |
| `503`  | Service unavailable |

---

### Search global execution listeners

```
POST /v2/global-execution-listeners/search
```

Searches for global execution listeners with optional filters, sorting, and pagination. This
endpoint is **eventually consistent**.

**Request body** (optional — empty body returns all listeners):

```json
{
  "filter": {
    "type": "audit-execution-event",
    "source": "API"
  },
  "sort": [
    {
      "field": "priority",
      "order": "DESC"
    }
  ],
  "page": {
    "limit": 20
  }
}
```

**Filter fields**:

| Field           | Type                | Description |
|-----------------|---------------------|-------------|
| `id`            | StringFilterProperty | Listener ID |
| `type`          | StringFilterProperty | Job type |
| `retries`       | IntegerFilterProperty | Retry count |
| `eventTypes`    | array of EventTypeFilter | Event types |
| `afterNonGlobal`| boolean             | Ordering flag |
| `priority`      | IntegerFilterProperty | Priority value |
| `source`        | SourceFilterProperty | `CONFIGURATION` or `API` |

String and integer filters support advanced operators (`$eq`, `$neq`, `$in`, `$like`, `$exists`).

**Sortable fields**: `id`, `type`, `afterNonGlobal`, `priority`, `source`

**Response** `200 OK`:

```json
{
  "items": [
    {
      "id": "audit-process-lifecycle",
      "type": "audit-execution-event",
      "eventTypes": ["start", "end", "cancel"],
      "elementTypes": ["process"],
      "categories": [],
      "retries": 3,
      "afterNonGlobal": false,
      "priority": 100,
      "source": "API"
    }
  ],
  "page": {
    "totalItems": 1,
    "firstSortValues": [],
    "lastSortValues": []
  }
}
```

---

## Data Model

### Request/response properties

| Property        | Type           | Required (create) | Required (update) | Default | Description |
|-----------------|----------------|-------------------|-------------------|---------|-------------|
| `id`            | string         | yes               | (path param)      | —       | Unique identifier. Immutable after creation. |
| `type`          | string         | yes               | yes               | —       | Job type — determines which job workers process this listener. |
| `eventTypes`    | array\<string> | yes               | yes               | —       | Lifecycle events: `start`, `end`, `cancel`. At least one required. |
| `elementTypes`  | array\<string> | no                | no                | —       | Fine-grained BPMN element types (see [element types](#supported-element-types)). |
| `categories`    | array\<string> | no                | no                | —       | Broad element groups: `all`, `tasks`, `gateways`, `events` (see [categories](#categories)). |
| `retries`       | integer        | no                | no                | `3`     | Number of job retries on failure. |
| `afterNonGlobal`| boolean        | no                | no                | `false` | If `true`, runs after BPMN-level listeners. If `false`, runs before. |
| `priority`      | integer        | no                | no                | `50`    | Execution priority among global listeners. Higher values execute first. |
| `source`        | string         | —                 | —                 | —       | Response only. `API` for API-created, `CONFIGURATION` for config-file-created. |

### Element type scoping

When both `elementTypes` and `categories` are specified, they combine as a **union** — the
listener fires on all element types from both lists.

When **neither** `elementTypes` nor `categories` is specified, the listener applies to **all**
element types (equivalent to `categories: ["all"]`). The engine validates each `eventTypes` entry
against each resolved element type's supported events.

---

## Supported Element Types

### Fine-grained element types (`elementTypes` field)

| Value                    | Description |
|--------------------------|-------------|
| `process`                | Process instance lifecycle |
| `subprocess`             | Embedded subprocesses |
| `eventSubprocess`        | Event subprocesses |
| `serviceTask`            | Service tasks |
| `userTask`               | User tasks |
| `sendTask`               | Send tasks |
| `receiveTask`            | Receive tasks |
| `scriptTask`             | Script tasks |
| `businessRuleTask`       | Business rule (DMN) tasks |
| `callActivity`           | Call activities |
| `multiInstanceBody`      | Multi-instance body (not individual iterations) |
| `exclusiveGateway`       | Exclusive (XOR) gateways |
| `parallelGateway`        | Parallel (AND) gateways |
| `inclusiveGateway`       | Inclusive (OR) gateways |
| `eventBasedGateway`      | Event-based gateways |
| `startEvent`             | Start events |
| `endEvent`               | End events |
| `intermediateCatchEvent` | Intermediate catch events |
| `intermediateThrowEvent` | Intermediate throw events |
| `boundaryEvent`          | Boundary events |

### Categories

Categories expand to groups of element types:

| Category   | Expands to |
|------------|------------|
| `all`      | Every element type listed above |
| `tasks`    | `serviceTask`, `userTask`, `sendTask`, `receiveTask`, `scriptTask`, `businessRuleTask` |
| `gateways` | `exclusiveGateway`, `parallelGateway`, `inclusiveGateway`, `eventBasedGateway` |
| `events`   | `startEvent`, `endEvent`, `intermediateCatchEvent`, `intermediateThrowEvent`, `boundaryEvent` |

---

## Event–Element Compatibility Matrix

Not all element types support all event types. The API validates configurations and rejects
unsupported combinations. The `cancel` event is only supported on `process`.

| Element type             | `start` | `end` | `cancel` |
|--------------------------|---------|-------|----------|
| `process`                | ✅       | ✅     | ✅        |
| `subprocess`             | ✅       | ✅     | ❌        |
| `eventSubprocess`        | ✅       | ✅     | ❌        |
| `serviceTask`            | ✅       | ✅     | ❌        |
| `userTask`               | ✅       | ✅     | ❌        |
| `sendTask`               | ✅       | ✅     | ❌        |
| `receiveTask`            | ✅       | ✅     | ❌        |
| `scriptTask`             | ✅       | ✅     | ❌        |
| `businessRuleTask`       | ✅       | ✅     | ❌        |
| `callActivity`           | ✅       | ✅     | ❌        |
| `multiInstanceBody`      | ✅       | ✅     | ❌        |
| `exclusiveGateway`       | ✅       | ❌     | ❌        |
| `parallelGateway`        | ✅       | ❌     | ❌        |
| `inclusiveGateway`       | ✅       | ❌     | ❌        |
| `eventBasedGateway`      | ✅       | ❌     | ❌        |
| `startEvent`             | ❌       | ✅     | ❌        |
| `endEvent`               | ✅       | ❌     | ❌        |
| `intermediateCatchEvent` | ✅       | ✅     | ❌        |
| `intermediateThrowEvent` | ✅       | ✅     | ❌        |
| `boundaryEvent`          | ❌       | ✅     | ❌        |

**Why some events are not supported on certain elements:**

- **Gateways** (`exclusiveGateway`, `parallelGateway`, `inclusiveGateway`, `eventBasedGateway`):
  Only `start` — gateways evaluate and route, they have no completion lifecycle.
- **`startEvent`**: Only `end` — the start event itself completes; use `start` on the parent
  process or subprocess to hook into the beginning of execution.
- **`endEvent`**: Only `start` — the end event is activated but does not have a separate
  completion phase. Error end events do not support `end`.
- **`boundaryEvent`**: Only `end` — boundary events fire when triggered; use `start` on the
  attached activity instead.
- **`cancel`**: Only `process` — cancellation is a process-instance-level concept.

---

## Validation Rules

The API enforces the following validation rules on create and update requests:

### Field-level validation

| Rule | Error |
|------|-------|
| `id` must be a non-empty string (create only) | `"No id provided"` |
| `type` must be a non-empty string | `"No type provided"` |
| `eventTypes` must be a non-empty array | `"No eventTypes provided"` |

### Cross-field validation (event–element compatibility)

When `elementTypes` and/or `categories` are specified, the API resolves all element types
(combining explicit `elementTypes` with expanded `categories`) and validates that every entry in
`eventTypes` is supported by every resolved element type.

**Example — valid configuration:**

```json
{
  "id": "my-listener",
  "type": "my-job-type",
  "eventTypes": ["start", "end"],
  "elementTypes": ["serviceTask", "userTask"]
}
```

Both `serviceTask` and `userTask` support `start` and `end` — valid.

**Example — invalid configuration:**

```json
{
  "id": "my-listener",
  "type": "my-job-type",
  "eventTypes": ["start", "end"],
  "elementTypes": ["exclusiveGateway"]
}
```

Returns `400 Bad Request`:

```json
{
  "type": "about:blank",
  "title": "INVALID_ARGUMENT",
  "status": 400,
  "detail": "Element type 'exclusiveGateway' does not support event type 'end'."
}
```

**Example — categories expand before validation:**

```json
{
  "id": "my-listener",
  "type": "my-job-type",
  "eventTypes": ["start", "end"],
  "categories": ["gateways"]
}
```

The `gateways` category expands to `exclusiveGateway`, `parallelGateway`,
`inclusiveGateway`, `eventBasedGateway` — none support `end`. Returns `400 Bad Request` with
an error for each incompatible combination.

**When no element types or categories are specified**, event–element compatibility validation is
**skipped**. The listener applies to all element types, and the engine only fires events that are
supported for each element type at runtime (e.g., `end` is silently not fired for gateways).

---

## Ordering Semantics

### Global vs. BPMN-level listener ordering

By default (`afterNonGlobal: false`), global execution listeners run **before** any BPMN-level
execution listeners defined in the process model.

Setting `afterNonGlobal: true` causes the global listener to run **after** all BPMN-level
execution listeners.

```
Element lifecycle event fires
│
├─ Global listeners (afterNonGlobal: false)     ← default: runs first
│    └─ ordered by priority (higher first)
│
├─ BPMN-level execution listeners               ← defined in the process model
│    └─ ordered by model definition order
│
└─ Global listeners (afterNonGlobal: true)       ← runs last
     └─ ordered by priority (higher first)
```

### Priority among global listeners

The `priority` field determines execution order among global listeners in the same position
(before or after BPMN-level). **Higher priority values execute first.** Default priority is `50`.

**Example**: Three global listeners with `afterNonGlobal: false`:
- Listener A: `priority: 100` — executes first
- Listener B: `priority: 50` — executes second
- Listener C: `priority: 10` — executes third

### Execution behavior

- **Blocking**: Every global execution listener job blocks element execution until the job worker
  completes. Slow workers directly increase process instance latency.
- **Variable access**: Same scoping rules as BPMN-level execution listeners:
  - `start` listeners can read process variables and set local variables.
  - `end` listeners can read local + output mapping variables and set parent-scope variables.
  - `cancel` listeners can read and set variables while the element instance is still active.
- **Incidents**: Failed listener jobs raise incidents. In Operate, global listener incidents are
  differentiated from BPMN-level listener incidents via a Source column.
- **Multi-tenancy**: Listener configuration is cluster-wide (not per-tenant). Each listener job
  carries a `tenantId` in its payload for downstream routing.

---

## Configuration File (Self-Managed)

Global execution listeners can be defined in the Camunda configuration file under the
`camunda.listener.execution` key. Listeners defined via configuration are loaded at startup and
appear with `source: CONFIGURATION` in API responses.

### Basic configuration

```yaml
camunda:
  listener:
    execution:
      - id: audit-process-lifecycle
        eventTypes:
          - start
          - end
          - cancel
        type: audit-execution-event
        elementTypes:
          - process
        retries: 3
        priority: 100
      - id: monitor-service-tasks
        eventTypes:
          - start
        type: monitor-service-tasks
        elementTypes:
          - serviceTask
        retries: 3
        priority: 50
```

### Configuration properties

| Property        | Type           | Default | Description |
|-----------------|----------------|---------|-------------|
| `id`            | string         | `""`    | Unique listener identifier |
| `eventTypes`    | list\<string>  | `[]`    | Lifecycle events: `start`, `end`, `cancel` |
| `type`          | string         | `""`    | Job type for worker routing |
| `elementTypes`  | list\<string>  | `[]`    | Fine-grained BPMN element types |
| `categories`    | list\<string>  | `[]`    | Broad element categories: `all`, `tasks`, `gateways`, `events` |
| `retries`       | string/integer | `"3"`   | Number of job retries |
| `afterNonGlobal`| boolean        | `false` | Run after BPMN-level listeners |
| `priority`      | integer        | `50`    | Execution priority (higher first) |

### Using categories in configuration

```yaml
camunda:
  listener:
    execution:
      # All tasks — start and end
      - id: audit-all-tasks
        eventTypes: [start, end]
        type: audit-execution-event
        categories: [tasks]
        retries: 3
      # All gateways — start only (gateways don't support end)
      - id: audit-gateways
        eventTypes: [start]
        type: audit-execution-event
        categories: [gateways]
        retries: 3
      # Process lifecycle including cancel
      - id: audit-process
        eventTypes: [start, end, cancel]
        type: audit-execution-event
        elementTypes: [process]
        retries: 3
```

---

## Examples

### Minimal listener — process lifecycle only

```json
{
  "id": "process-lifecycle",
  "type": "audit-event",
  "eventTypes": ["start", "end", "cancel"],
  "elementTypes": ["process"]
}
```

Low overhead: 2–3 jobs per process instance (start + end or cancel).

### All tasks — start and end

```json
{
  "id": "task-audit",
  "type": "audit-event",
  "eventTypes": ["start", "end"],
  "categories": ["tasks"]
}
```

Covers `serviceTask`, `userTask`, `sendTask`, `receiveTask`, `scriptTask`, `businessRuleTask`.
Two jobs per task element.

### Combined categories and element types

```json
{
  "id": "combined-audit",
  "type": "audit-event",
  "eventTypes": ["start", "end"],
  "categories": ["tasks"],
  "elementTypes": ["process"]
}
```

Fires on all 6 task types **and** `process` (union). Note: `process` supports `cancel` too, but
this listener only fires on `start` and `end`.

### Full tracing (high volume)

Two listeners for complete visibility:

```json
{
  "id": "trace-all",
  "type": "trace-event",
  "eventTypes": ["start", "end"],
  "categories": ["all"]
}
```

```json
{
  "id": "trace-cancel",
  "type": "trace-event",
  "eventTypes": ["cancel"],
  "elementTypes": ["process"]
}
```

> **Warning**: `categories: ["all"]` generates approximately 2 jobs per element per process
> instance. For a 20-element process, this creates ~40 additional blocking jobs per instance.
> Monitor partition throughput and job queue depth at scale.

### Search with filters

Find all API-created listeners with priority above 50:

```json
{
  "filter": {
    "source": "API",
    "priority": { "$gt": 50 }
  },
  "sort": [{ "field": "priority", "order": "DESC" }],
  "page": { "limit": 10 }
}
```

### Search by event type

Find listeners that fire on `cancel` events:

```json
{
  "filter": {
    "eventTypes": ["cancel"]
  }
}
```

---

## Performance Considerations

Global execution listeners create significantly more engine load than BPMN-level listeners because
they generate additional blocking jobs for every matching element in every process instance.

| Scoping                                          | Additional jobs per instance (20-element process) | Risk |
|--------------------------------------------------|---------------------------------------------------|------|
| `elementTypes: [process]` only                   | 2–3 (start + end/cancel)                          | Low  |
| Process + service tasks                          | 2–3 + 2 per service task                          | Medium |
| `categories: [all]` with `start` + `end`         | ~40                                                | **High** |
| Multiple listeners on overlapping element types  | Multiplicative (each listener generates own jobs) | **High** |

**Recommendations**:

1. **Start with `elementTypes: [process]`** for low-overhead process lifecycle tracking.
2. Add specific element types or categories incrementally based on need.
3. For observability-only use cases where blocking is unnecessary, consider exporters as an
   asynchronous alternative.
4. Monitor job queue depth when using element-level scoping at scale (10K+ instances/day).

---

## Comparison with Global Task Listeners

Global execution listeners and global task listeners are managed via parallel API endpoints with
the same CRUD + search pattern. Key differences:

| Aspect         | Global execution listeners              | Global task listeners                    |
|----------------|-----------------------------------------|------------------------------------------|
| API path       | `/v2/global-execution-listeners`        | `/v2/global-task-listeners`              |
| Event types    | `start`, `end`, `cancel`                | `all`, `creating`, `assigning`, `updating`, `completing`, `canceling` |
| Element scoping| `elementTypes`, `categories`            | N/A (always applies to user tasks)       |
| Triggers on    | BPMN element and process lifecycle      | User task lifecycle                      |
| Job kind       | `EXECUTION_LISTENER`                    | `TASK_LISTENER`                          |

Both share the same ordering controls (`afterNonGlobal`, `priority`), source tracking
(`CONFIGURATION`, `API`), and configuration file structure (`camunda.listener`).
