# Plan

**Timestamp:** 2026-03-19T16:25:16.847Z

## Original Request

# Global Execution Listeners

## Value Proposition Statement

For developers and operations teams who need cluster-wide execution lifecycle hooks — whether migrating from C7 process engine plugins or standardizing how they react to process and element events — Global Execution Listeners provide a centralized, configuration-driven mechanism to attach execution listeners at cluster scope without modifying BPMN models. This eliminates the need to manually add listeners to every process definition, reduces integration maintenance overhead, and restores the global listener capability lost in the C7→C8 migration.

## User Problem

Developers and operations teams need to react to execution lifecycle events (process start, element completion, instance cancellation) consistently across all processes in a cluster. Today, execution listeners must be configured per-element or per-process in each BPMN model. This creates three problems:

1. **Consistency gap**: Organizations cannot guarantee that every process captures execution events for audit, compliance, or integration purposes. Adding listeners to every model manually is error-prone and creates governance risk.

2. **Migration blocker**: C7 customers relied on process engine plugins (`BpmnParseListener`) to inject global execution listeners automatically. C8 has no equivalent — forcing process redesign or custom exporter workarounds that don't work on SaaS.

3. **Operational overhead**: When a cross-cutting concern changes (e.g., new audit requirement, new CRM integration), teams must update and redeploy every affected process model instead of changing a single cluster-level configuration.

### Business use cases this functionality enables

- **Audit & compliance**: Capture every process start/end/cancel event across all processes for regulatory reporting without modifying BPMN models.
- **Real-time data replication**: Replicate process instance state changes to external databases, data lakes, or analytics systems automatically.
- **C7 migration**: Restore global listener patterns from C7 process engine plugins, unblocking migration for customers who built integrations on global hooks.
- **Unified event routing**: Route execution lifecycle events to message brokers, case management systems, or CRM platforms consistently across all processes.
- **Observability**: Instrument all processes with execution tracing listeners for monitoring and alerting without touching BPMN models.

## Release Notes

### Global execution listeners

Define cluster-wide execution listeners that react to process and element lifecycle events across all processes — without modifying BPMN models.

- Configure global execution listeners via the Orchestration Cluster API, Admin UI, or configuration files (`camunda.listener`).
- Scope listeners by event types (`start`, `end`, `cancel`) and target elements using fine-grained `elementTypes` (e.g., `serviceTask`, `userTask`) or broad `categories` (`tasks`, `gateways`, `events`, `all`). A single listener can handle multiple event types — e.g., `eventTypes: [start, end, cancel]` for process instances.
- Global execution listeners use the same job worker infrastructure as BPMN-level execution listeners. Listener jobs include a full variable snapshot and element metadata in the payload.
- Manage listeners in the Admin UI with categorized element type selection and context-aware event validation.
- Distinguish global listener incidents from BPMN-level listener incidents in Operate using the new Source column.
- Execution order is deterministic: global listeners run before BPMN-level listeners by default, with `afterNonGlobal` and `priority` options for precise ordering control.

## User Stories

1. **As an administrator**, I can register cluster-wide execution listeners that react to process lifecycle events (start, end, cancel) and element lifecycle events (start, end) across all processes, so I do not need to modify or redeploy individual BPMN models.

2. **As an administrator**, I can scope global execution listeners to specific element types (e.g., only service tasks, only process instances) and event types (e.g., only start or end), so I can tailor listeners precisely to my integration and compliance use cases.

3. **As an administrator**, I can configure global execution listeners via:
   - Configuration files or environment variables (Self-Managed)
   - Orchestration Cluster REST API (SaaS + Self-Managed)
   - Admin UI (SaaS + Self-Managed)

   so I can choose the management approach that fits my deployment model and operational workflows.

4. **As an administrator**, I can create, read, update, and delete global execution listeners at runtime via the Orchestration Cluster API without restarting the cluster, so I can respond to changing integration requirements dynamically.

5. **As a developer**, I receive the same consistent job payload — variable snapshot, process metadata, element metadata, and tenant information — as with BPMN-level execution listeners, so my job worker implementation works identically for both global and model-level listeners.

6. **As a developer**, I can set variables when completing a global execution listener job, so I can enrich process data from external systems during execution — consistent with BPMN-level execution listener behavior.

7. **As a developer migrating from C7**, I understand that global execution listeners replace process engine plugin patterns (`BpmnParseListener`), providing a modern alternative that works on both SaaS and Self-Managed.

8. **As an operations engineer**, I can see global execution listener activity and incidents in Operate — clearly differentiated from BPMN-level listeners — so I can identify whether an incident originates from a global or model-level listener and troubleshoot accordingly.

9. **As an administrator**, I can control execution order between global and BPMN-level listeners using `afterNonGlobal` and `priority` settings, so I can ensure predictable execution sequencing.

10. **As an administrator**, I can control who has access to manage global execution listeners via resource-based permissions, so I can enforce least-privilege access.

## User Journey

### Journey 1: Administrator sets up audit listeners (Admin UI)

1. Administrator opens Admin UI → **Global Listeners** page.
2. Page shows empty state: "No global execution listeners configured. Global execution listeners react to process and element lifecycle events across all processes without modifying BPMN models." with a "Create Listener" button and link to docs.
3. Administrator clicks "Create Listener" → form opens with categorized element type selection (see Admin UI guidance below).
4. Administrator selects `process` element type → system shows supported events (`start`, `end`, `cancel`). Selects all three.
5. Administrator enters job type `audit-execution-event`, sets retries to 3.
6. Administrator saves → listener appears in the list with source badge `API`.
7. Developer starts a new instance in the cluster
8. Administrator verifies in Operate → filters listener tab by Source: "Global" in process instance view → sees first listener jobs completing.
9. When a failure occurs → Operate shows incident with Source column "Global" → administrator identifies the listener ID and job type → escalates to job worker team.

### Journey 2: Developer configures listeners via config file (Self-Managed)

1. Developer defines listener config in `camunda.listener` section of the Camunda Helm values or application config YAML.
2. Developer commits config to Git → infrastructure repo tracks all listener definitions as code.
3. CI/CD pipeline deploys updated config to Self-Managed cluster.
4. Cluster loads listener config on startup → listeners appear in Admin UI with source badge `CONFIGURATION`.
5. Developer deploys job worker that handles the configured job type.
6. Developer verifies in Operate → sees listener jobs completing.
7. On config change → developer updates YAML in Git → CI/CD redeploys → cluster picks up new config on restart.
8. Rollback → developer reverts Git commit → CI/CD redeploys previous config version.

### Journey 3: Developer configures listeners via API (CI/CD pipeline)

1. Developer writes listener config as API payload in their infrastructure repo (version-controlled in Git).
2. CI/CD pipeline calls `POST /v2/global-execution-listeners` on deployment to register a new global execution listener.
3. Developer deploys job worker that handles the configured job type.
4. Developer verifies via `POST /v2/global-execution-listeners/search` → confirms listener registered.
5. Developer monitors via Operate or API logs from job worker → validates listener jobs completing.
6. On config change → developer updates API payload in Git → CI/CD re-applies → existing listeners updated via `PUT`.
7. Rollback → developer reverts Git commit → CI/CD re-applies previous config.

### Journey 4: Operations engineer triages listener incident (Operate)

1. Alert fires (or engineer opens Operate dashboard) → incident list shows failed jobs.
2. Engineer filters by **Source: Global Listener** to isolate global listener incidents from BPMN-level issues.
3. Engineer identifies the failing listener by job type and listener ID in the incident detail view.
4. Engineer checks: Is this a job worker issue (code bug, external system down) or a misconfiguration (wrong element types, missing permissions)?
5. If job worker issue → escalates to integration team with job type and failure message.
6. If misconfiguration → navigates to Admin UI or contacts the CI/CD pipeline owner to fix the listener config.
7. Engineer resolves incident in Operate → element execution resumes.

## Implementation Notes

### Existing infrastructure to extend

**Alignment principle**: This feature follows the same patterns established by global user task listeners (#2586, #3287) for state management, API design, versioned configuration, and Admin UI. The global task listener implementation in the camunda/camunda repository serves as the reference implementation. All engineering decisions (state consistency during rolling upgrades, config/API conflict resolution, snapshot pinning, partition coordination) should match the global task listener behavior.

The global listener infrastructure already exists in `camunda/camunda`:

- **Engine state**: `DbGlobalListenersState` in `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/globallistener/` uses a `GlobalListenerType` enum (currently `USER_TASK` only). Extend with `EXECUTION_LISTENER`.
- **API spec**: `zeebe/gateway-protocol/src/main/proto/v2/global-listeners.yaml` defines CRUD + Search endpoints for global task listeners. Add parallel endpoints for global execution listeners.
- **Admin UI**: `identity/client/src/pages/global-task-listeners/` has List and AddModal components. Add equivalent pages for global execution listeners.
- **Configuration**: The config-file path from #2586 applies. Extend the configuration schema to support execution listener definitions.
- **Versioned configuration & pinning**: The existing `DbGlobalListenersState` supports versioned config snapshots and pinning to element instances — reuse for execution listener consistency.

### API data model (extends `GlobalListenerBase`)

The global execution listener request/response model follows the same pattern as global task listeners:

| Property         | Type                        | Description                                                                                                        |
| ---------------- | --------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `id`             | string (required)           | Unique identifier for the global listener                                                                          |
| `jobType`        | string (required)           | Which job workers process this listener                                                                            |
| `eventTypes`     | array of strings (required) | Execution lifecycle events: `start`, `end`, `cancel`. Multiple values allowed per listener (e.g., `[start, end]`). |
| `categories`     | array (optional)            | Broad element type groups: `all`, `tasks`, `gateways`, `events`. See categories table below.                       |
| `elementTypes`   | array (optional)            | Fine-grained BPMN element types (e.g., `serviceTask`, `userTask`). Combined with `categories` as union.            |
| `retries`        | integer                     | Job retry count (default: 3)                                                                                       |
| `afterNonGlobal` | boolean                     | Run after BPMN-level listeners (default: false — global runs first)                                                |
| `priority`       | integer                     | Execution priority among global listeners (higher = first)                                                         |
| `source`         | enum (response only)        | `CONFIGURATION` or `API`                                                                                           |

#### Supported events per element type

Not all element types support all event types. The API and Admin UI must validate configurations and reject unsupported combinations (`eventTypes` entries vs resolved element types from `categories`/`elementTypes`). This matrix defines the source of truth:

| Element type             | `start` | `end`  | `cancel` | Notes                                                              |
| ------------------------ | ------- | ------ | -------- | ------------------------------------------------------------------ |
| `process`                | yes     | yes    | yes      | Process instance lifecycle - only element type supporting `cancel` |
| `subprocess`             | yes     | yes    | no       | Embedded subprocesses                                              |
| `eventSubprocess`        | yes     | yes    | no       | Event subprocesses                                                 |
| `serviceTask`            | yes     | yes    | no       |                                                                    |
| `userTask`               | yes     | yes    | no       |                                                                    |
| `sendTask`               | yes     | yes    | no       |                                                                    |
| `receiveTask`            | yes     | yes    | no       |                                                                    |
| `scriptTask`             | yes     | yes    | no       |                                                                    |
| `businessRuleTask`       | yes     | yes    | no       |                                                                    |
| `callActivity`           | yes     | yes    | no       |                                                                    |
| `multiInstanceBody`      | yes     | yes    | no       | Fires on the MI body, not individual iterations                    |
| `exclusiveGateway`       | yes     | **no** | no       | Gateways evaluate and route - no end/cancel semantics              |
| `parallelGateway`        | yes     | **no** | no       |                                                                    |
| `inclusiveGateway`       | yes     | **no** | no       |                                                                    |
| `eventBasedGateway`      | yes     | **no** | no       |                                                                    |
| `startEvent`             | **no**  | yes    | no       | Use `start` on the process/subprocess instead                      |
| `endEvent`               | yes     | **no** | no       | Error end events do not support `end`                              |
| `intermediateCatchEvent` | yes     | yes    | no       |                                                                    |
| `intermediateThrowEvent` | yes     | yes    | no       | Interrupting escalation throw does not support `end`               |
| `boundaryEvent`          | **no**  | yes    | no       | Use `start` on the attached activity instead                       |

The `all` category is also supported: `categories: [all]` (or omitting both `categories` and `elementTypes`) fires on all elements for the supported events of each element type.

**Categories** provide broad element type grouping via the `categories` field:

| Category   | Expands to                                                                                    |
| ---------- | --------------------------------------------------------------------------------------------- |
| `all`      | Every element type (equivalent to omitting both `categories` and `elementTypes`)              |
| `tasks`    | `serviceTask`, `userTask`, `sendTask`, `receiveTask`, `scriptTask`, `businessRuleTask`        |
| `gateways` | `exclusiveGateway`, `parallelGateway`, `inclusiveGateway`, `eventBasedGateway`                |
| `events`   | `startEvent`, `endEvent`, `intermediateCatchEvent`, `intermediateThrowEvent`, `boundaryEvent` |

`categories` and `elementTypes` can be combined on the same listener — the engine takes the union of expanded categories and explicit element types, then applies event validation per `eventTypes` entry.

**Validation rules**: The API and Admin UI must validate each entry in `eventTypes` against all resolved element types. For example, `{elementTypes: [exclusiveGateway], eventTypes: [start, end]}` must return a validation error because gateways do not support `end` events. However, `{elementTypes: [process], eventTypes: [start, end, cancel]}` is valid because `process` supports all three.

**Configuration example**: To match the use case "notify on process start/end/cancel, service task start, user task start+end, intermediate catch event end, all gateways start":

```yaml
camunda:
  listener:
    # Process lifecycle (all three events in one listener)
    - eventTypes: [start, end, cancel]
      jobType: audit-event
      elementTypes: [process]
    # Service task start
    - eventTypes: [start]
      jobType: audit-event
      elementTypes: [serviceTask]
    # User task start + end
    - eventTypes: [start, end]
      jobType: audit-event
      elementTypes: [userTask]
    # Intermediate catch event end
    - eventTypes: [end]
      jobType: audit-event
      elementTypes: [intermediateCatchEvent]
    # All gateways start
    - eventTypes: [start]
      jobType: audit-event
      categories: [gateways]
```

Five listeners instead of eight — `eventTypes` arrays reduce configuration overhead. Multiple listeners can share the same `jobType` (`audit-event`), so a single job worker processes all audit events. The payload includes `elementType` and `eventType` fields for downstream routing.

#### Scalable configuration patterns

Use `categories` to target broad groups of element types without listing them individually. Common patterns:

**Pattern 1: All task types**
```yaml
- eventTypes: [start, end]
  jobType: audit-event
  categories: [tasks]
```
One listener covers all 6 task types for start and end events.

**Pattern 2: All tasks + process lifecycle**
```yaml
# Tasks + process: start and end
- eventTypes: [start, end]
  jobType: audit-event
  categories: [tasks]
  elementTypes: [process]
# Process cancel (only supported on process)
- eventTypes: [cancel]
  jobType: audit-event
  elementTypes: [process]
```
Two listeners cover all tasks + process. `categories` and `elementTypes` are combined as union — so `categories: [tasks]` + `elementTypes: [process]` fires on all tasks and process. `cancel` needs a separate listener because only `process` supports it.

**Pattern 3: Everything**
```yaml
- eventTypes: [start, end]
  jobType: audit-event
  categories: [all]
# Cancel only supported on process
- eventTypes: [cancel]
  jobType: audit-event
  elementTypes: [process]
```
Two listeners for full execution tracing. High volume - use only when you need complete visibility. The engine validates that each `eventTypes` entry is supported per resolved element type.

**Pattern 4: Combining broad + specific scoping**
```yaml
# All tasks, start+end
- eventTypes: [start, end]
  jobType: audit-event
  categories: [tasks]
# Process lifecycle including cancel
- eventTypes: [start, end, cancel]
  jobType: audit-event
  elementTypes: [process]
# Gateways, start only
- eventTypes: [start]
  jobType: audit-event
  categories: [gateways]
```
Three listeners, one job worker. Each listener scoped to specific event types and a target group.

**Key principle**: Combine multiple `eventTypes` per listener when the target element types support all listed events. Split into separate listeners when element types have different event support (e.g., `cancel` only on `process`). Use `categories` or `elementTypes` arrays to group elements.

**Ease-of-setup guidance**: Most users start with `eventTypes: [start, end, cancel]` + `elementTypes: [process]` for low-overhead process lifecycle tracking, then add specific element types or categories as needed. Using `categories: [all]` fires on every element for supported events — powerful but high-volume.

### Execution semantics

- **Blocking**: Global execution listeners are blocking, consistent with BPMN-level execution listeners. The engine waits for the listener job to complete before proceeding.
- **Ordering**: Global listeners run before BPMN-level listeners by default. Set `afterNonGlobal: true` to run after. Among global listeners, `priority` determines order (higher first).
- **Variable access**: Same scoping rules as BPMN-level ELs. Start listeners can read process variables and set local variables. End listeners can read local + output mapping variables and set parent-scope variables. Cancel listeners can read and set variables as long as the element instance is still active (same behavior as end listeners on end events) — consistent with global task listener cancel behavior.
- **Payload**: Job payload includes full variable snapshot visible at the element scope, process instance metadata (key, definition, version), element instance metadata (key, elementId, elementType, flowScopeKey), and `tenantId`.
- **Incidents**: Failed listener jobs raise incidents visible in Operate, consistent with BPMN-level execution listener incidents.
- **Multi-tenancy**: Listener configuration is not per-tenant. Jobs carry `tenantId` for downstream routing.

### Configuration (Self-Managed)

```yaml
camunda:
  listener:
    # Audit process lifecycle (all events in one listener)
    - eventTypes: [start, end, cancel]
      jobType: audit-execution-event
      elementTypes: [process]
      retries: 3
      priority: 100
    # Audit user task execution
    - eventTypes: [start, end]
      jobType: audit-execution-event
      elementTypes: [userTask]
      retries: 3
      priority: 90
    # Audit gateway routing
    - eventTypes: [start]
      jobType: audit-execution-event
      categories: [gateways]
      retries: 3
      priority: 80
    # Monitor service tasks
    - eventTypes: [start]
      jobType: monitor-service-tasks
      elementTypes: [serviceTask]
      retries: 3
      priority: 50
```

Four listeners instead of seven. Multiple listeners can share the same `jobType` (e.g., `audit-execution-event`). A single job worker processes all matching jobs and routes by `elementType` + `eventType` in the payload.

### Performance considerations

**Global execution listeners create significantly more engine load than BPMN-level listeners.** Every registered global listener generates additional blocking jobs for every matching element in every process instance. Teams must understand the volume implications before enabling element-level scoping.

| Scoping configuration                           | Additional jobs per instance (20-element process)     | Risk level                                  |
| ----------------------------------------------- | ----------------------------------------------------- | ------------------------------------------- |
| `elementTypes: [process]` only                  | 2-3 jobs (start, end, cancel)                         | Low — comparable to global task listeners   |
| Process + service tasks                         | 2-3 + 2 per service task                              | Medium                                      |
| `categories: [all]`, start + end                | ~40 jobs                                              | **High — significant engine load increase** |
| Multiple listeners on overlapping element types | Multiplicative (each listener generates its own jobs) | **High**                                    |

- **Start with process-level only**: Recommend `elementTypes: [process]` as the default. Add specific element types or categories incrementally based on need.
- **Blocking impacts latency**: Every global EL job blocks element execution until the job worker completes. Slow job workers directly increase process instance latency.
- **Monitor job queue depth**: At scale (10K+ instances/day), element-level listeners can generate 400K+ additional jobs/day. Monitor partition throughput and job queue depth.
- **Non-blocking mode**: Out of scope for 8.10. For observability-only use cases where blocking is unnecessary, consider exporters (async) as an alternative until non-blocking mode ships.
- **Performance documentation (in scope)**: Ship with explicit volume guidance, a scoping decision tree, and warnings about latency impact of broad element-level scoping.

### Dependencies

- **Cancel execution listener** ([#2768](https://github.com/camunda/product-hub/issues/2768)): The `cancel` event type depends on cancel execution listener support. Assumed to be shipped before or alongside this epic.
- **Admin UI team** (Identity): The Admin UI pages for global execution listeners extend the existing global task listener UI in `identity/client/src/pages/`.
- **Operate frontend**: Adding a Source column to the existing listener tab requires frontend work.

### Scope

**In scope**:
- Engine support for global execution listeners (extend `GlobalListenerType` with `EXECUTION_LISTENER`)
- Configuration file support (Self-Managed)
- Orchestration Cluster REST API (CRUD + Search) for global execution listeners
- Admin UI page for managing global execution listeners
- Scoping by `eventTypes`, `categories`, and `elementTypes`
- Ordering control (`afterNonGlobal`, `priority`)
- Variable read/write (same as BPMN-level ELs)
- Operate visibility: all listeners (global + BPMN-level) in a single tab with a **Source** column to differentiate origin ("Global Listener" / "BPMN Model") and support filtering by source
- Admin UI with categorized element type selection, context-aware event validation (only show supported events per element type), collapsible advanced options (priority, afterNonGlobal), preset templates for common use cases, and empty state with onboarding guidance
- API validation: reject configurations with unsupported event types per element type (see supported events matrix)
- Resource-based permissions for API access
- Documentation and migration guide (C7 process engine plugins → global ELs)
- Performance documentation with volume impact tables, scoping decision tree, and latency warnings

**Out of scope**:
- Non-blocking mode (future enhancement)
- Per-tenant listener configuration (consistent with global task listeners)
- Process-definition-level scoping (e.g., "only for process X") — use BPMN-level listeners for that
- Custom headers on global execution listeners (follow same pattern as BPMN-level ELs — headers are per-element)

## Design Spec

> **Pattern:** Follow the Global Task Listeners implementation (Identity, 8.9) exactly — same components, same layout, same form structure, same interaction patterns. Only the fields and their values differ. When in doubt, be consistent with Global Task Listeners.

> Execution listeners fire on `start` / `end` / `cancel` of BPMN flow elements (activities, gateways, sequence flows, processes). Global execution listeners register at cluster level and apply across all process definitions without modifying them individually.


### Prerequisites

- `@camunda/camunda-composite-components` upgraded to **v0.21.5** in Identity — required for `subElements` support on navbar items (the Listeners dropdown nav pattern)

### User Journey

*A cluster administrator wants to register a global execution listener so that a specific job worker is triggered at the start or end of flow elements across the cluster — without modifying individual process definitions.*

**Steps:**
1. Open Identity and navigate to **Listeners** → **Execution** (navbar dropdown)
2. See existing listeners, or an empty state prompting to create the first one
3. Open the creation form via the **Add listener** button
4. Fill in the required fields and submit
5. Confirm the new listener appears in the list with a success notification
6. Edit a listener to update its configuration
7. Delete a listener that is no longer needed

---

### Navigation

- **Location:** Identity navbar → **Listeners** — top-level nav item with sub-elements, replacing the current flat "Global user task listeners" entry
- **Component:** `C3Navigation` `navbar.elements` with `subElements` (added in `@camunda/camunda-composite-components` v0.21.5). Renders as a dropdown in the nav bar.
- **Sub-elements:**
  - **Task** — links to `/identity/listeners/tasks`
  - **Execution** — links to `/identity/listeners/execution`
- **No `/listeners` landing page** — both sub-elements link directly to their own pages
- **URL redirect:** `/identity/global-task-listeners` → `/identity/listeners/tasks` to preserve existing bookmarks and deep links
- **`isCurrentPage`** set on the active sub-element — the top-level Listeners item highlights automatically when any sub-element is active

---

### Setup Flow — Create

1. User is on the Execution Listeners page (page title: *"Global execution listeners"*, with a docs link to `/docs/components/concepts/global-execution-listeners/` in the `PageHeader` — shown when the list is non-empty; hidden on empty state since `PageEmptyState` shows its own docs link)
2. User clicks **Add listener** — a Carbon `Button` with an `Add` icon, rendered by `EntityList` via `addEntityLabel` / `onAddEntity` props, consistent with all other Identity list pages
3. Modal opens with the creation form (use `FormModal`, same as Task Listeners)
4. User fills in the fields (see Field Reference)
5. User clicks **Create**
6. On success: modal closes, list refreshes, success toast notification shown (*"Global execution listener created"*)
7. On API error: inline error shown inside the modal, modal stays open

#### Event types

A single listener can handle multiple event types (e.g. `start` + `end` + `cancel` on `process` in one listener). Create separate listeners when you need different job workers per event type, or when element types have different event support — for example, `cancel` is only valid on `process`, so it must be a separate listener if other element types are also targeted.

---

### Confirmation

- No separate confirmation step for creation — modal submit is the single action
- Success is confirmed via a toast notification (same pattern as Task Listeners: `enqueueNotification` with `kind: "success"`)

---

### Editing

1. User clicks the **Edit** icon button on the listener row (inline, ghost style — `EntityList` renders up to 2 menu items as inline icon buttons, not an overflow menu)
2. Edit modal opens pre-filled with existing values
3. `id` field is **read-only** — cannot be changed after creation
4. User updates fields and clicks **Save**
5. On success: modal closes, list refreshes, success toast shown
6. On API error: inline error shown inside modal

---

### Deleting

1. User clicks the **Delete** button on the listener row (inline, danger--ghost style)
2. Confirmation modal: *"Delete execution listener [id]? This will remove it from all processes on this cluster."*
3. User confirms → deleted, row removed, success toast shown
4. User cancels → modal closes, no change

---

### Validation

Follows the same validation approach as Task Listeners (`react-hook-form`, `mode: "all"`).

| Field       | Rule                                      | Error message                             |
| ----------- | ----------------------------------------- | ----------------------------------------- |
| ID          | Required                                  | *"ID is required"*                        |
| ID          | Pattern `^[a-zA-Z0-9._-]+$`               | *"Please enter a valid listener ID"*      |
| Job type    | Required                                  | *"Job type is required"*                  |
| Job type    | Pattern `^[a-zA-Z0-9._-]+$`, max 50 chars | *"Please enter a valid job type"*         |
| Event types | At least one required                     | *"Please select at least one event type"* |
| Retries     | Integer ≥ 1, if provided                  | *"Must be a whole number of 1 or more"*   |
| Priority    | Integer ≥ 0, if provided                  | *"Must be a whole number"*                |

Invalid combinations are validated server-side. The API returns a validation error shown as an inline notification at the top of the modal.

---

### States & Behaviors

#### List page states

| State     | UI                                                                                                                                                                                                                                       |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Loading   | Skeleton rows                                                                                                                                                                                                                            |
| Empty     | Empty state + *"Create your first execution listener"* CTA + docs link (`PageEmptyState`, `docsLinkPath="/docs/components/concepts/global-execution-listeners/"`) — Task Listeners tab already has its own empty state and is unaffected |
| Populated | Data table with overflow menus per row                                                                                                                                                                                                   |
| Error     | Inline error notification with **Retry** action                                                                                                                                                                                          |

#### Modal states

| State            | UI                                                  |
| ---------------- | --------------------------------------------------- |
| Default          | Fields empty (create) or pre-filled (edit)          |
| Validation error | Inline field errors                                 |
| Submitting       | Loading spinner on submit button, fields disabled   |
| API error        | Inline notification at modal top, fields re-enabled |

#### Behaviors

- **Sorting:** By `id`, `jobType`, `afterNonGlobal`, `priority`, `source` (same sortable fields as Task Listeners)
- **Search:** By `id`
- **`afterNonGlobal` display:** *"Before non-global listeners"* (false) / *"After non-global listeners"* (true)
- **Event types display:** comma-separated list of selected values (e.g. *"start, end"*)
- **Categories display:** `all` → *"All elements"*; otherwise category name (e.g. *"Tasks"*, *"Gateways"*)
- **Element types display:** comma-separated list; if categories cover all selected types, prefer showing the category label

#### Performance warning

Show an inline warning in the form when `categories` is set to `all` (or both `categories` and `elementTypes` are left empty, which defaults to `all`):

> *"Using all element types generates a high volume of listener jobs and may impact process execution latency. Consider scoping to specific element types. Start with `process` for low-overhead lifecycle tracking."*

Show the warning immediately when `all` is selected — not only on submit. Dismiss if the user changes to a specific element type selection.

#### Source / configuration-managed listeners

> ⚠️ **Open decision**: The product epic specifies that listeners defined via configuration file (Self-Managed) should appear in the Admin UI with a `CONFIGURATION` source badge and be read-only. It is unclear whether this is the right UX — exposing config-managed listeners in the UI could be confusing, since they cannot be edited or deleted there and changes must go through config.

If config-sourced listeners are shown:
- Display a `source` column: *"API"* / *"Configuration"*
- Config-sourced rows: Edit and Delete actions disabled, tooltip on hover: *"This listener is managed via configuration file and cannot be edited here."*

---

### Field Reference

Mirrors the Task Listeners form structure. Default values match Task Listeners where applicable.

> ⚠️ **Assumption only** — fields, types, and constraints below are inferred from the Global Task Listeners pattern and general BPMN execution listener semantics. This table must be updated once the backend API schema is defined.

| Field          | Label         | Component                  | Required | Default | Notes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| -------------- | ------------- | -------------------------- | -------- | ------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`           | Listener ID   | `TextField`                | Yes      | —       | Immutable after creation. Pattern `[a-zA-Z0-9._-]+`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| `jobType`      | Job type      | `TextField`                | Yes      | —       | Job type string. Pattern `[a-zA-Z0-9._-]+`, max 50 chars. Multiple listeners can share the same `jobType` — a single job worker can route by `elementType` + `eventType` from the payload.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `elementScope` | Element scope | `FilterableMultiSelect`    | No       | `all`   | Single field combining `categories` and `elementTypes`. Categories (`all`, `tasks`, `gateways`, `events`) appear as parent checkboxes; individual BPMN element types appear as children underneath. Checking a category checks all its children and sends to the `categories` API field; checking individual items sends to `elementTypes`. Both can be combined — the API takes the union. Defaults to `all` if nothing is selected — shows performance warning. Checking `all` also shows performance warning. **Edit form pre-population:** read both `categories` and `elementTypes` from the API response and translate back — if `categories: ["tasks"]` is set, check the Tasks parent; if `elementTypes: ["serviceTask"]` is set, check that individual child. |
| `eventTypes`   | Event types   | `CheckboxGroup` (vertical) | Yes      | —       | Multiple values: `start`, `end`, `cancel`. Checkboxes not supported by all selected element types are disabled. Helper text on the group explains which events are unavailable (see Dynamic event type behaviour below).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

`retries`, `afterNonGlobal`, and `priority` are grouped under a small `Accordion` labelled *"Advanced options"* at the bottom of the form.

| Field            | Label           | Component     | Required | Default          | Notes                                                                     |
| ---------------- | --------------- | ------------- | -------- | ---------------- | ------------------------------------------------------------------------- |
| `retries`        | Retries         | `NumberInput` | No       | `3`              | Min 1                                                                     |
| `afterNonGlobal` | Execution order | `Dropdown`    | No       | `false` (before) | Options: *"Before non-global listeners"* / *"After non-global listeners"* |
| `priority`       | Priority        | `NumberInput` | No       | `50`             | Min 0. Higher number = higher priority                                    |

#### Dynamic event type behaviour based on element scope

When elements are selected in the Element scope field, `eventTypes` checkboxes not supported by all selected elements are disabled. An event type is enabled only if every selected element type supports it.

**Helper text:** shown on the `eventTypes` CheckboxGroup when one or more checkboxes are disabled. Format: *"[Event] and [Event] are not supported for the selected element types."* When nothing is disabled, no helper text is shown.

**When a previously checked event type becomes disabled** (due to element scope change): deselect it automatically and update the helper text.

For the full event support matrix per element type, see the **Supported events per element type** table in the [Global Execution Listeners epic (product-hub #3125)](https://github.com/camunda/product-hub/issues/3125). That table is the source of truth for which events are valid per element type and should be used to drive the frontend disabling logic.

`cancel` is only supported on `process`. If any non-process element is included in the scope, the `cancel` checkbox is disabled.

---

## Operate

> Updates to the Listeners tab on the process instance page in Operate to support global execution listeners alongside BPMN-level listeners.

### Scope column

Add a **Scope** column to the Listeners table to differentiate global listeners from BPMN model-level listeners.

| Value   | Meaning                                                |
| ------- | ------------------------------------------------------ |
| Global  | Listener was registered as a global execution listener |
| Process | Listener is defined on the BPMN element (model-level)  |

The value comes from the API — not derived on the frontend.

---

### State labels

Use Carbon `Tag` components to show listener [job](https://docs.camunda.io/docs/next/apis-tools/orchestration-cluster-api-rest/specifications/search-jobs/) state in the table. All states come directly from the API job state values.

| API state         | Label           | Tag color          |
| ----------------- | --------------- | ------------------ |
| `COMPLETED`       | Completed       | Green              |
| `FAILED`          | Failed          | Red                |
| `ERROR_THROWN`    | Error thrown    | Red                |
| `TIMED_OUT`       | Timed out       | Orange (Warm gray) |
| `CREATED`         | Created         | Gray               |
| `RETRIES_UPDATED` | Retries updated | Gray               |
| `MIGRATED`        | Migrated        | Gray               |
| `CANCELED`        | Canceled        | High contrast      |

Any state value returned by the API that is not in this table should be displayed as-is (formatted) in a gray tag.

## Research Context

### Study Global Task Listeners reference implementation

Documentation is complete at `plan.md`. Here's the summary:

## Key Findings

**Architecture spans 12 layers** with ~60+ files across protocol, state, engine, REST, config, service, mapper, exporter, and search modules.

**Most important pattern — Versioning + Pinning**:
- 4 RocksDB column families: 2 GLOBAL (listener registry + config pointer) + 2 PARTITION_LOCAL (versioned snapshots + pins)
- Pin at user task creation → snapshot config immutably → unpin at completion/cancellation → GC when no pins remain
- Ensures a task always uses the listener config that existed when it was created

**Dual-source management**: API-created listeners and YAML-config listeners coexist but never interfere — `BatchConfigure` only touches `source=CONFIGURATION` listeners.

**Every CRUD operation emits 2 events**: individual listener change + batch CONFIGURED event (bumps config version).

**No Admin UI exists** — management is REST API + YAML only.

**For execution listener replication**: the pattern catalog in plan.md Section 12 maps each GTL component to its execution listener equivalent, with 5 key differences to consider (pinning scope, event types, ordering context, expansion logic, REST design).

---

### Analyze BPMN element type hierarchy and execution listener support

Here's the structured reference (also saved to session workspace):

## Key Findings

### 25 BpmnElementTypes across 5 categories
- **6 containers** (Process, SubProcess, EventSubProcess, AdHocSubProcess, AdHocSubProcessInnerInstance, MultiInstanceBody)
- **8 tasks** (Service, User, Script, BusinessRule, Send, Receive, Manual, Task)
- **5 events** (Start, IntermediateCatch, IntermediateThrow, Boundary, End)
- **4 gateways** (Exclusive, Parallel, Inclusive, EventBased)
- **2 other** (SequenceFlow, CallActivity)

### Execution Listener Event Support Matrix

| Element Type | `start` | `end` | `cancel` |
|---|:---:|:---:|:---:|
| **Tasks (all 8)** | ✅ | ✅ | ❌ |
| **Process/SubProcess/CallActivity/AdHocSubProcess** | ✅ | ✅ | ❌ |
| **IntermediateCatch/ThrowEvent** | ✅ | ✅ | ❌ |
| **Gateways (all 4)** | ✅ | ❌ | ❌ |
| **StartEvent** | ❌ | ✅ | ❌ |
| **BoundaryEvent** | ❌ | ✅ | ❌ |
| **EndEvent** | ✅ | ❌* | ❌ |

*`end` denied for error end events only.

### Critical Finding: Cancel is NOT implemented (#2768)
- `ZeebeExecutionListenerEventType` has only `start` and `end` — no `cancel`
- During termination, in-flight EL jobs are **canceled silently** — no "cancel" listener fires
- **Task listeners** (user tasks only) DO have `canceling` as a precedent
- The `JobListenerEventType` protocol enum already has `CANCELING` but only for task listeners

### Gateway vs Task Lifecycle
- **Gateways** are synchronous — they auto-complete in one processing cycle, so `end` ELs are denied (no meaningful completion hook)
- **Tasks** are wait-state elements — both `start` and `end` ELs are supported
- Both cancel in-flight EL jobs on termination without triggering any cancel listener

---

### Review Orchestration Cluster API patterns and design

Here's the complete analysis of the REST API patterns:

## Summary

**1. Endpoint Structure** — Standard CRUD+Search with 5 endpoints per resource. Kebab-case plural paths, `{id}` string parameter (not numeric key). Commands get `x-eventually-consistent: false`, queries get `true`.

**2. Request/Response Models** — Layered `allOf` composition: `GlobalListenerBase` → `GlobalTaskListenerBase` → Create/Update/Result schemas. Create includes `id`, Update takes `id` from path, Result marks ALL fields as `required`.

**3. Pagination & Filtering** — Search uses `POST /search` with `filter`, `sort`, and `page`. Filters use **oneOf** pattern (scalar shorthand OR advanced operators like `$eq/$neq/$in/$like/$gt/$lte`). Pagination supports offset-based, limit-only, and cursor-based (forward/backward). Sort uses an explicit field enum + ASC/DESC order.

**4. Error Handling** — All errors use RFC 9457 **ProblemDetail** with `application/problem+json`. Shared responses for 400/401/403/500/503; inline definitions for 404/409 (entity-specific descriptions).

**5. Validation/Compatibility** — Spectral enforces: all properties need descriptions, response arrays must be `required`, `*Key` properties must be `type: string`, command ops cannot be `x-eventually-consistent: true`. Vendor extensions (`x-semantic-type`, `x-polymorphic-schema`) support SDK generation.

**6. Key Insight** — `GlobalListenerBase` is already designed as a shared base for multiple listener types. A new execution listener type would compose it via `allOf`, following the exact same CRUD+Search+filter+sort structure with its own `eventTypes` enum.

Full analysis saved to session artifacts.

---

### Study configuration loading and merging mechanisms

Here's the complete synthesis of all findings:

---

## Global Task Listener Config → Engine Pipeline: Complete Findings

### (1) Config-to-API Merging Strategy

The merge happens in **`GlobalListenerBatchConfigureProcessor.fillConfigurationChangeMetadata()`** (lines 98–143). The rules:

| Scenario | Result |
|----------|--------|
| CONFIGURATION-source listener removed from YAML | **Deleted** |
| API-source listener not in YAML | **Preserved** (untouched) |
| YAML listener has same ID as existing API listener | **Config wins** — overwrites the API listener |
| New ID in YAML, not in state | **Created** with `source=CONFIGURATION` |

Config applies **full replacement semantics** for its own listeners while leaving API listeners alone — *unless there's an ID collision*, in which case config wins.

### (2) Source Tracking

- **`GlobalListenerSource`** enum: `CONFIGURATION` | `API`
- Set at creation time: `GlobalListenerConfiguration.toRecord()` hardcodes `CONFIGURATION`; `GlobalListenerMapper` (gateway-mapping-http) sets `API` for REST requests
- Stored in RocksDB `GLOBAL_LISTENERS` column family per listener
- Exported to search indices (ES/OS/RDBMS) in `GlobalListenerEntity.source`
- Exposed in REST API responses (`GlobalTaskListenerResult.source` field)
- **Filterable and sortable** in search queries

### (3) Priority/Override When Both Defined

- **Config always wins on ID collision.** Line 131 comment: *"the old listener is replaced even if it was API-defined"*
- The delete loop (lines 115–117) only auto-deletes `source == CONFIGURATION` listeners absent from new config — API listeners are immune to config-driven deletion
- Within execution, listeners are ordered by **`afterNonGlobal`** flag first (before/after process-level listeners), then by **priority** (descending), then **ID** (ascending)

### (4) Path from Config Files to Engine State

```
YAML (camunda.cluster.global-listeners.user-task[*])
  → Spring @NestedConfigurationProperty binding → Cluster.globalListeners (GlobalListenersCfg)
    → GlobalListenersCfg.createGlobalListenersConfiguration() → GlobalListenersConfiguration
      → EngineCfg.createEngineConfiguration() → EngineConfiguration.globalListeners
        → GlobalListenersInitializer.onRecovered() [partition 1 only]
          → compares stored config vs YAML config (isSameConfiguration)
          → if different: schedules CONFIGURE command (timestamp 0L)
            → GlobalListenerBatchConfigureProcessor.processNewCommand()
              → fillConfigurationChangeMetadata() [merge logic]
              → emitChangeEvents() → CREATED/UPDATED/DELETED events
              → CommandDistributionBehavior distributes to all partitions
                → EventAppliers update RocksDB state
```

**Config version pinning**: When a user task is created, the current `listenersConfigKey` is pinned to it. This means the task always uses the listener config that was active at creation time, even if config changes later.

### (5) UI Presentation (Read-Only vs Editable)

**Current Identity UI does NOT distinguish by source.** The `List.tsx`, `EditModal.tsx`, and `DeleteModal.tsx` components show edit/delete actions for **all** listeners regardless of source. The `source` column is not displayed in the list view.

However, the **REST API and OpenAPI spec fully expose** the `source` field:
- `GlobalTaskListenerResult` includes `source` as a required response field
- Search supports filtering by `source` (e.g., show only CONFIGURATION listeners)
- The engine processors (`GlobalListenerUpdateProcessor`, `GlobalListenerDeleteProcessor`) do NOT restrict updates/deletes based on source

**Practical implication**: A CONFIGURATION-sourced listener can be modified via API, but the change will be **overwritten on next broker restart** (since `GlobalListenersInitializer` will re-apply YAML config). API-sourced listeners persist across restarts.

---

### Key Insight for Execution Listener Design

The Global Task Listener pattern provides a clean blueprint:
1. **Source enum** distinguishes config-managed vs API-managed
2. **Config wins on ID collision** but API listeners with unique IDs coexist
3. **Version pinning** ensures consistency during task execution
4. **No UI enforcement** of read-only — the transience of config overrides is the enforcement mechanism
5. The `afterNonGlobal` + `priority` ordering model cleanly composes global listeners with process-level ones

---

### Research Identity Admin UI component patterns for global listeners

Here's the comprehensive analysis of the Identity global task listeners UI:

## Summary of Findings

**Component Structure**: `List.tsx` is the main page with `EntityList` (shared table), 3 modals (Add/Edit/Delete), and conditional `PageEmptyState`. Entry point is `index.tsx` which lazy-loads via `PageRoutes`.

**Multi-Select Filtering**: Sophisticated "all" toggle in `handleEventTypeChange` — auto-selects/deselects "all" based on individual selections. On submit, normalizes to `["all"]` or individual types only.

**Dynamic Fields**: Minimal — only `id` is `readOnly` in EditModal. No conditional visibility based on other fields.

**Source Badges**: **Not present** in task listeners. No `Tag`/`Badge` component used. Would need Carbon `<Tag>` if required for execution listeners.

**Delete Confirmation**: Thin wrapper using shared `Modal` with `danger` prop, simple `<p>` text, `useApiCall` without error suppression (errors show as toasts).

**Sorting/Search/Pagination**: `usePaginatedApi` hook handles everything server-side. Sorting defined per-column via `isSortable`. Search by `id`. Carbon `<Pagination>` auto-shown when items exceed page size.

**Notifications**: Two strategies — Add/Edit use `suppressErrorNotification: true` to show inline errors in `FormModal`; Delete lets errors bubble as toasts. All success paths use `enqueueNotification({ kind: "success" })`.

Full documentation saved to session files. To replicate for execution listeners, you'll need **8 new files** + **3 registration point updates** (routePaths, useGlobalRoutes, localization).

---

### Analyze Operate listener visualization and incident filtering

Here's the complete research summary:

## Current Listeners Tab

The **Listeners tab** (`ListenersTab/index.tsx`) queries `POST /v2/jobs/search` filtered by `kind: EXECUTION_LISTENER | TASK_LISTENER`. It shows **6 columns**: Listener type, Listener key, State, Job type, Event, Time. It uses a non-sortable `StructuredList` with scroll-based infinite pagination (50/page).

## Filtering
- A **dropdown filter** appears only when a USER_TASK element is selected, offering: `All listeners`, `Execution listeners`, `User task listeners`.
- No state filter, no text search, no sorting.

## Incidents
- **Listeners tab**: shows a red ⚠️ icon when `state === 'FAILED'` — no error details, no link to incidents.
- **Incidents tab**: has two listener-specific error types (`EXECUTION_LISTENER_NO_RETRIES`, `TASK_LISTENER_NO_RETRIES`) filterable in the error type dropdown. No cross-tab linking.

## Job State Labels
States are formatted via `spaceAndCapitalize()` (e.g., `FAILED` → "Failed"). The `kind` field displays as "Execution Listener" or "Task Listener".

## Critical Gap: No "Scope" Column Possible Today
The `JobEntity` has **no field** to differentiate global vs model-level listeners. Both produce identical job records with `kind: EXECUTION_LISTENER/TASK_LISTENER`. Global listener *definitions* live in a completely separate `GLOBAL_LISTENER` entity/table/index and are **not displayed anywhere in the Operate frontend** currently.

**To add a Scope column**, the engine would need to populate a new `listenerScope` (or `source`) field on `JobRecordValue`/`JobEntity` when creating listener jobs. This is the only reliable approach — heuristic or client-side join approaches are fragile.

Full details saved to `files/listeners-ui-research.md`.

## Refined Requirements

**

---

## Global Execution Listeners — Requirements Summary

### Problem Statement
Camunda 8 lacks cluster-wide execution lifecycle hooks. Execution listeners must be configured per-element in BPMN models, creating consistency gaps for audit/compliance, blocking C7 migration (which relied on `BpmnParseListener` plugins), and imposing operational overhead when cross-cutting concerns change. The existing **Global Task Listeners** infrastructure provides a proven pattern to extend.

### Scope Decisions (made by me as implementation details)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Cancel event type | **Deferred** — `start` and `end` only | Per user direction; #2768 dependency |
| Full-stack scope | **Yes** — protocol through Admin UI | Feature is incomplete without end-to-end |
| Config-sourced in UI | **Show as read-only with badge** | Consistent with spec recommendation; better UX |
| Operate Scope column | **Include** — add `listenerSource` to JobRecord | Foundational for incident triage |
| Git strategy | **One commit per logical phase** | Reviewable, bisectable |
| Phasing | **Bottom-up**: Protocol → Engine → API → Config → Service → Exporters → UI | Each layer builds on prior |

### Acceptance Criteria

1. **CRUD via REST API**: `POST/GET/PUT/DELETE /v2/global-execution-listeners` + `POST /v2/global-execution-listeners/search` — mirrors task listener API pattern
2. **Event types**: `start`, `end` (no `cancel` yet); `all` shorthand
3. **Element scoping**: `elementTypes` list (e.g., `serviceTask`, `userTask`) and/or `categories` (`tasks`, `gateways`, `events`, `containers`, `all`)
4. **Execution order**: Global execution listeners merge with BPMN-level listeners. Default: global runs first. `afterNonGlobal=true` runs after BPMN-level. `priority` for ordering among globals.
5. **Job worker compatibility**: Global EL jobs use `JobKind.EXECUTION_LISTENER`, same payload (variables, metadata) as BPMN-level ELs
6. **Variable setting**: Completing a global EL job can set variables (same as BPMN-level behavior)
7. **Config-file support**: `camunda.listener.execution` YAML list with `id`, `eventTypes`, `type`, `retries`, `elementTypes`, `categories`, `afterNonGlobal`, `priority`
8. **Runtime CRUD**: Create/update/delete at runtime without cluster restart
9. **Admin UI**: New "Execution" sub-page under Listeners nav in Identity app
10. **Operate visibility**: Global vs BPMN-level listener incidents distinguishable via Source column
11. **Version pinning**: Element instances pin the global listener config at activation time
12. **Exporter support**: Global execution listener records exported to ES/OS/RDBMS

### Technical Requirements

**Shared infrastructure** (already exists for task listeners — extend, don't duplicate):
- `GlobalListenerType` enum: add `EXECUTION_LISTENER`
- `GlobalListenerRecord`: add `elementTypes` (List<String>) and `categories` (List<String>) fields
- `GlobalListenerValidator`: add EL-specific validation (valid event types, element types, categories)
- 4 RocksDB column families: **reused** (keyed by listenerType)
- CRUD processors: **reused** (type-agnostic)
- `GlobalListenerBatchConfigureProcessor`: **reused**

**New infrastructure**:
- Engine: merge global ELs into `BpmnStreamProcessor` execution listener pipeline (alongside BPMN-level listeners from `ExecutableFlowNode`)
- Engine: pin global listener config to element instance at activation
- Protocol: `elementTypes` and `categories` on `GlobalListenerRecordValue`
- Protocol: `listenerSource` field on `JobRecordValue` (`GLOBAL` | `MODEL`)
- API spec: `v2/global-execution-listeners.yaml` with 5 endpoints
- Config: `GlobalListenersCfg.execution` list
- Service: extend `GlobalListenerServices` for execution listener type
- Admin UI: ~8 new files mirroring `global-task-listeners/` pages
- Exporters: handle new fields in ES/OS/RDBMS exporters

### Edge Cases

- **Empty elementTypes + empty categories**: Listener applies to nothing (reject at validation)
- **Overlapping categories**: `categories: [tasks, all]` — deduplicate at evaluation time
- **Gateway listeners with `end` event**: Gateways auto-complete; `end` is valid but fires immediately
- **StartEvent/BoundaryEvent with `start` event**: Invalid per BPMN semantics — reject at validation
- **Config + API collision**: Config-sourced listener with same ID as API-sourced → config wins on restart
- **High-cardinality**: `categories: [all]` + `eventTypes: [start, end]` on 20-element process = ~40 additional jobs per instance — document performance impact
- **Concurrent config change during element activation**: Version pinning ensures consistency

### Out of Scope

- `cancel` event type (depends on #2768)
- Global execution listeners for DMN decision evaluation
- Listener conditions/filters (e.g., "only fire if variable X > 10")
- Listener chaining (output of one listener feeds into next)
- MCP tool endpoints for global listeners
- Backward compatibility with C7 process engine plugin API

---

Shall I proceed to create the implementation plan (`plan.md`) with phased todos?

## Engineering Decisions

Good — I now have full context. Let me produce the revised section.

Here is the complete revised **Engineering Decisions** section:

---

## Engineering Decisions

### Decision 1: CALL_ACTIVITY element type is categorized under `tasks` for execution listener purposes

**Context.** Every BPMN element type registered in `BpmnElementProcessors` can have execution listeners (START/END). When exposing listener-related APIs, documentation, or configuration to users, element types must be grouped into human-meaningful categories. The question is which category `CALL_ACTIVITY` belongs to.

**Options considered.**

| Category | Element types currently in this category |
|----------|------------------------------------------|
| `tasks` | SERVICE_TASK, USER_TASK, SCRIPT_TASK, SEND_TASK, RECEIVE_TASK, BUSINESS_RULE_TASK, MANUAL_TASK, TASK (undefined) |
| `containers` | PROCESS, SUB_PROCESS, EVENT_SUB_PROCESS, AD_HOC_SUB_PROCESS, MULTI_INSTANCE_BODY |
| `events` | START_EVENT, END_EVENT, INTERMEDIATE_CATCH_EVENT, INTERMEDIATE_THROW_EVENT, BOUNDARY_EVENT |
| `gateways` | EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, INCLUSIVE_GATEWAY, EVENT_BASED_GATEWAY |

CALL_ACTIVITY could reasonably be placed in `tasks` (it is modeled like a task in BPMN — a single atomic activity from the parent process's perspective) or `containers` (it spawns a child process instance, functioning as a scope).

**Chosen option.** Place `CALL_ACTIVITY` in the **`tasks`** category.

**Rationale.** From the parent process's perspective, a call activity behaves as an atomic task — it activates, runs, and completes as a single step. The child process is an implementation detail. BPMN modeling tools (Camunda Modeler) render it with a task shape, and users think of it as "call this other process like a task." Placing it in `containers` would be surprising because unlike SUB_PROCESS or EVENT_SUB_PROCESS, the call activity does not define inline child elements in the same process definition. The engine already handles it via `CallActivityProcessor`, which follows the same activation/completion lifecycle as task processors, including execution listener support (`BpmnStreamProcessor` invokes `onStartActivation`/`onEndCompletion` identically to tasks).

**Consequences.** Any user-facing grouping (config schema, docs, REST API filters) that categorizes element types must list CALL_ACTIVITY alongside SERVICE_TASK, USER_TASK, etc. in the `tasks` group. If a future need arises for a `containers` grouping that also includes CALL_ACTIVITY, a cross-listing approach (element appears in both) should be considered at that time.

**Acceptance criteria.**
- CALL_ACTIVITY appears in the `tasks` category in any enum, constant set, or documentation that groups element types.
- Execution listeners (START and END) on CALL_ACTIVITY elements create jobs with `JobKind.EXECUTION_LISTENER` and `JobListenerEventType.START`/`END`, identical to the behavior on SERVICE_TASK elements.
- A unit test in the engine verifies that a CALL_ACTIVITY with a START and END execution listener creates and completes the expected listener jobs during activation and completion.

---

### Decision 2: Global execution listeners use the existing `camunda.cluster.globalListeners` configuration path with a new `listenerType` value

**Context.** The engine already supports global *task* listeners configured under `camunda.cluster.globalListeners.userTask` (mapped via `GlobalListenersCfg` → `GlobalListenerCfg` → `GlobalListenerConfiguration`). Each entry has a `listenerType` field backed by the `GlobalListenerType` enum, which currently has a single value: `USER_TASK`. To add global *execution* listeners, we must decide whether to reuse this config path or introduce a separate one.

**Options considered.**

1. **Reuse `camunda.cluster.globalListeners`** — add a new `GlobalListenerType.EXECUTION` enum value. Add a sibling list `globalListeners.execution` alongside `globalListeners.userTask` in `GlobalListenersCfg`. Each entry in the `execution` list uses the same `GlobalListenerCfg` shape (id, type, retries, eventTypes, priority) with `listenerType = EXECUTION`.

2. **New top-level path `camunda.listener.execution`** — introduce a separate configuration namespace entirely, with its own cfg class, state management, and distribution mechanism independent of the global listeners infrastructure.

**Chosen option.** Option 1 — reuse `camunda.cluster.globalListeners` with a new `execution` list and `GlobalListenerType.EXECUTION`.

**Rationale.** The existing global listener infrastructure already solves the hard problems: versioned configuration distribution across partitions (`DbGlobalListenersState` with `GLOBAL_LISTENER_VERSIONED_CONFIG` / `GLOBAL_LISTENER_PINNED_CONFIG` column families), CRUD processors (`GlobalListenerCreateProcessor`, `GlobalListenerUpdateProcessor`, `GlobalListenerDeleteProcessor`), authorization checks, and cross-partition consistency. Introducing a parallel config path would duplicate all of this machinery. The `GlobalListenerCfg` POJO already has a `listenerType` field — it was designed to be extended. The `eventTypes` list naturally maps to execution listener events (`start`, `end`) just as it maps to task listener events (`creating`, `completing`, etc.).

**Consequences.**
- Add `GlobalListenerType.EXECUTION` to the protocol enum (with a new explicit value — never reuse existing ordinals).
- Add `private List<GlobalListenerCfg> execution = new ArrayList<>()` to `GlobalListenersCfg`, with getter/setter.
- Update `GlobalListenersCfg.createGlobalListenersConfiguration()` to include execution listeners in the returned configuration.
- The engine's `BpmnStreamProcessor` must check for global execution listeners at `onStartActivation` and `onEndCompletion` in addition to the per-element execution listeners already defined on `ExecutableFlowNode`.
- State storage reuses the existing `GLOBAL_LISTENERS` column family, keyed by `(GlobalListenerType, listenerId)` — the `EXECUTION` type provides natural namespace separation from `USER_TASK` entries.

**Acceptance criteria.**
- An `execution` list under `camunda.cluster.globalListeners` is accepted by the broker configuration parser without errors.
- A global execution listener configured with `eventTypes: [start]` creates a `JobKind.EXECUTION_LISTENER` job with `JobListenerEventType.START` on every flow node activation across all process definitions.
- A global execution listener configured with `eventTypes: [end]` fires on every flow node completion.
- Global execution listeners and per-element execution listeners coexist: per-element listeners execute first, then global listeners (consistent with the `afterNonGlobal` ordering flag already supported by `GlobalListenerCfg`).
- CRUD operations via the existing `GlobalListenerCreateProcessor`/`UpdateProcessor`/`DeleteProcessor` work for `listenerType = EXECUTION`.
- A test deploys a process with no per-element execution listeners, configures a global execution listener, and verifies listener jobs are created for a SERVICE_TASK activation and completion.

---

### Decision 3: The `JobKind` enum is the sole discriminator between task listener and execution listener jobs — no additional `listenerSource` field is introduced

**Context.** When a job worker receives a listener job, it needs to know what kind of listener triggered it. The question is whether to add a new `listenerSource` field to `JobRecordValue` (e.g., with values like `PROCESS_DEFINITION`, `GLOBAL_CONFIG`, `EXECUTION`, `TASK`) or to rely on the existing `JobKind` enum (`EXECUTION_LISTENER`, `TASK_LISTENER`) combined with `JobListenerEventType`.

**Options considered.**

1. **Add a `listenerSource` field to both execution and task listener jobs** — a new string or enum field on `JobRecord` indicating the origin of the listener (e.g., "Was this listener defined on the BPMN element, or from global config?"). This would appear on all listener job types.

2. **Add `listenerSource` to execution listener jobs only** — same field, but scoped to execution listeners since they are the new addition.

3. **No `listenerSource` field — rely on existing `JobKind` + `JobListenerEventType`** — the current protocol already distinguishes `EXECUTION_LISTENER` vs `TASK_LISTENER` via `JobKind`, and the specific event via `JobListenerEventType` (START, END, CREATING, COMPLETING, etc.). The origin (per-element vs global) is not exposed to the job worker.

**Chosen option.** Option 3 — no `listenerSource` field. Use existing `JobKind` and `JobListenerEventType` as the sole discriminators.

**Rationale.** Job workers should not need to know whether a listener was defined on the BPMN element or in global configuration — the behavior is identical either way. The `JobKind` enum already cleanly separates execution listeners from task listeners from regular BPMN element jobs. Adding `listenerSource` would:
- Expand the protocol's `JobRecord` with a field that has no behavioral impact on job processing.
- Require a new `MsgPack` property on `JobRecord` (breaking the golden file backward compatibility test unless a default is provided).
- Create a new versioned event applier for any state that persists the field.
- Leak an internal concern (where the listener was configured) into the public API, coupling job workers to configuration topology.

If a future debugging or observability need arises for tracking listener origin, it can be addressed via job headers (a `Map<String, String>` already on `JobRecord`) without protocol changes.

**Consequences.**
- No new fields on `JobRecord` or `JobRecordValue`.
- No new protocol enum values beyond the already-planned `GlobalListenerType.EXECUTION`.
- Job workers distinguish listener type via `job.getJobKind()` (returns `EXECUTION_LISTENER` or `TASK_LISTENER`) and the specific event via `job.getListenerEventType()`.
- Global listeners and per-element listeners produce identical job records — the only difference is execution ordering, handled internally by the engine.

**Acceptance criteria.**
- `JobRecord` and `JobRecordValue` have no `listenerSource` field or equivalent.
- An execution listener job (whether from a per-element definition or global config) has `jobKind = EXECUTION_LISTENER` and the appropriate `listenerEventType` (START or END). No other field distinguishes its origin.
- A task listener job has `jobKind = TASK_LISTENER` and the appropriate `listenerEventType` (CREATING, ASSIGNING, UPDATING, COMPLETING, or CANCELING). No other field distinguishes its origin.
- The `RecordGoldenFilesTest` in `zeebe/protocol-impl` passes without changes to `JobRecord`'s golden file.
- A test activates both a per-element and a global execution listener on the same element and verifies both produce `JobKind.EXECUTION_LISTENER` jobs with identical schema (no extra fields on either).

## Design Decisions

Here is the complete revised section:

---

## Design Decisions Summary

Based on codebase analysis and your answers, here's the complete design picture:

### Navigation
- **Expandable parent** "Listeners" in Identity sidebar (non-navigable trigger)
- Two children: **"Task"** (`/listeners/tasks`) and **"Execution"** (`/listeners/execution`)
- Both parent and active child show selected state
- **Parent route redirect:** Navigating directly to `/listeners` (e.g., typing the URL) performs a client-side redirect to `/listeners/tasks` via React Router `<Navigate replace>`. This is the canonical default child. The parent route never renders its own content — it always resolves to a child.
- Permanent client-side redirect: `/global-task-listeners` → `/listeners/tasks`, implemented via React Router `<Navigate replace>` so the old URL is replaced in browser history (no back-button loop)
- Always visible — no feature flag. Nav items render unconditionally; **permissions gate page content**, meaning the routes are always accessible but API calls and page data are subject to standard authorization checks. An unauthenticated or unauthorized user sees the nav items but gets an appropriate error state on the page itself.
- This is a **new pattern** for the Identity sidebar (first nested nav); will require extending the route/sidebar rendering logic

### Q2: Element Types + Categories → **(a) Two independent MultiSelects**
Carbon `MultiSelect` is already the established pattern (used for event types in task listeners). Two separate MultiSelects is the simplest, most accessible approach — no custom grouped component needed. Each gets its own `"all"` shorthand with bidirectional sync logic (described below). Server resolves overlap via **union with deduplication** (see below).

**Element Types — exhaustive option set:**
`serviceTask`, `userTask`, `sendTask`, `receiveTask`, `businessRuleTask`, `scriptTask`, `callActivity`, `subProcess`, `eventSubProcess`, `multiInstanceBody`, `exclusiveGateway`, `inclusiveGateway`, `parallelGateway`, `eventBasedGateway`, `startEvent`, `endEvent`, `intermediateThrowEvent`, `intermediateCatchEvent`, `boundaryEvent`, `all`

**Categories — exhaustive option set:**
`tasks`, `gateways`, `events`, `containers`, `all`

Category-to-element-type mapping (for reference; resolved server-side):
- `tasks` → `serviceTask`, `userTask`, `sendTask`, `receiveTask`, `businessRuleTask`, `scriptTask`
- `gateways` → `exclusiveGateway`, `inclusiveGateway`, `parallelGateway`, `eventBasedGateway`
- `events` → `startEvent`, `endEvent`, `intermediateThrowEvent`, `intermediateCatchEvent`, `boundaryEvent`
- `containers` → `subProcess`, `eventSubProcess`, `callActivity`, `multiInstanceBody`

**Bidirectional `all` sync logic** (same pattern used by event types in task listeners):
1. Selecting every individual item automatically checks `all`.
2. Unchecking any single item automatically unchecks `all`.
3. Selecting `all` checks every individual item.
4. Unchecking `all` unchecks every individual item.

**Overlap semantics:** If a user selects `elementTypes: [serviceTask]` AND `categories: [tasks]`, the server computes the **union** of the resolved element types and deduplicates. The result is the set of all element types covered by either selection. The frontend does **not** warn about overlap or validate against it — both fields operate independently, and redundant selections are harmless. Validation rule: at least one of `elementTypes` or `categories` must be non-empty (reject "applies to nothing").

If `categories: [all]` is selected, `elementTypes` remains enabled (user may want to be explicit for documentation purposes).

### Q3: Config-sourced rows → **Disabled menu items + Source column always visible + Read-only detail view**
- **Source column** added permanently to the list table (Carbon `Tag` component — `type="blue"` for `"API"`, `type="cool-gray"` for `"Configuration"`, matching existing Tag usage in assign modals)
- Config-sourced rows: Edit and Delete menu items are **disabled** (not hidden) — `EntityList` already supports `disabled: boolean` on menu items. Tooltip: *"Managed by configuration file"*
- Config-sourced rows include a **"View" menu item** (enabled) that opens a **read-only detail modal** displaying all listener fields as static text (no editable inputs). This ensures users can inspect the full configuration even when the table truncates long values. The modal title is **"Execution Listener Details"** (or **"Task Listener Details"**) with the listener ID as a subtitle. The modal has a single "Close" button — no save/submit action.
- **Row click behavior:** API-sourced rows navigate to the edit view on click (existing pattern). Config-sourced rows open the **read-only detail modal** on click (same content as the "View" menu item). This provides a consistent "click to inspect" affordance for all rows while preserving the distinction that config-sourced listeners cannot be edited. Both row types remain visually interactive (pointer cursor, hover highlight).
- API-sourced rows retain the existing menu items: Edit, Delete (both enabled), plus the same **"View"** item for consistency — though clicking the row or choosing Edit both open the editable modal for API-sourced rows.

### Q4: Operate Source column → **(c) Both tabs**
- **ListenersTab**: new "Source" column showing `"Global"` or `"Model"` using a Carbon `Tag` component (`type="teal"` for `"Global"`, `type="gray"` for `"Model"`), placed alongside the existing `kind` column
- **IncidentsTable**: new "Source" column using the same Carbon `Tag` treatment — critical for incident triage (the stated goal)
- Consistent with the requirement *"Global vs BPMN-level listener incidents distinguishable"*

### Execution Listener Form (Add/Edit Modal)

**Add modal** ("Add Execution Listener"):
- **Field order:** ID → Type → Event Types → Element Types → Categories → Retries → Execution Order → Priority
- **Event Types MultiSelect**: `start`, `end`, `all` (replaces task listener's creating/assigning/etc.)
- **Element Types MultiSelect**: full option set listed above, with `all` shorthand and bidirectional sync
- **Categories MultiSelect**: full option set listed above, with same sync logic

**Field defaults, ranges, and validation (Add modal):**

| Field | Required | Default | Valid Range | Notes |
|---|---|---|---|---|
| ID | Yes | *(empty — user must provide)* | Non-empty string; no whitespace-only | Must be unique within the tenant |
| Type | Yes | *(empty — user must provide)* | Non-empty string | Worker type identifier |
| Event Types | Yes | *(no selection)* | At least one of: `start`, `end`, `all` | Cannot submit with empty selection |
| Element Types | Conditionally | *(no selection)* | Any subset of the listed element types | At least one of `elementTypes` or `categories` must be non-empty |
| Categories | Conditionally | *(no selection)* | Any subset of the listed categories | At least one of `elementTypes` or `categories` must be non-empty |
| Retries | Yes | `3` | Integer, `0–2147483647` (non-negative 32-bit) | `0` is valid (means no automatic retry) |
| Execution Order | Yes | `0` | Integer, `0–2147483647` (non-negative 32-bit) | Lower values execute first; `0` is default/first |
| Priority | Yes | `0` | Integer, `0–2147483647` (non-negative 32-bit) | Higher values = higher priority; `0` is default |

All numeric fields use Carbon `NumberInput` with `min` and `max` props enforcing the range. Client-side validation shows an inline error below the field for out-of-range or non-integer values.

**Cross-field validation rules (enforced on submit):**
1. `eventTypes` must be non-empty — at least one event type is required.
2. At least one of `elementTypes` or `categories` must be non-empty — a listener that applies to no elements is rejected.

**Edit modal** ("Edit Execution Listener"):
- **ID is read-only after creation** — displayed as a non-editable text field (or static label) at the top of the modal. The user cannot change the listener ID once created.
- **All other fields are editable** (Type, Event Types, Element Types, Categories, Retries, Execution Order, Priority).
- Same defaults, ranges, and validation rules apply to the editable fields as in the Add modal.
- Modal title: **"Edit Execution Listener"** with the listener ID shown as a subtitle or in the read-only ID field.
- Pre-populates all fields with the current listener values on open.

**API payload shape:**
The `all` shorthand is sent as the **literal value `["all"]`**, not expanded to the full list. The server is responsible for interpreting `["all"]` as "all applicable items." This keeps the payload compact and avoids version-coupling (if new element types are added server-side, existing `["all"]` payloads automatically include them). Example payload:
```json
{
  "listenerId": "my-listener",
  "type": "my-worker-type",
  "eventTypes": ["start", "end"],
  "elementTypes": ["all"],
  "categories": ["tasks", "gateways"],
  "retries": 3,
  "executionOrder": 1,
  "priority": 0
}
```

### Carbon Tag `type` Prop Reference

For consistency and to avoid ambiguity, here are the exact Carbon `Tag` `type` prop values used across all new columns:

| Context | Label | `type` prop | Rendered color |
|---|---|---|---|
| Identity Source (API-sourced) | `"API"` | `"blue"` | Blue |
| Identity Source (Config-sourced) | `"Configuration"` | `"cool-gray"` | Cool gray (neutral, distinct from `"gray"` which is warmer) |
| Operate Source (Global listener) | `"Global"` | `"teal"` | Teal |
| Operate Source (Model listener) | `"Model"` | `"gray"` | Gray |

Note: `"cool-gray"` is chosen over `"gray"` for the Identity "Configuration" tag to ensure sufficient visual contrast against the Operate "Model" tag if both ever appear in the same context, and because it better conveys "system-managed" connotation. These values correspond directly to Carbon's `Tag` component `type` prop enumeration.

### Accessibility
- All new controls use standard Carbon components — keyboard navigation, screen reader labels, and focus management come built-in
- Expandable sidebar nav needs `aria-expanded` on the parent trigger
- Disabled menu items: use `aria-disabled="true"` with the action name as `aria-label` (e.g., `aria-label="Edit listener"`). The disabled reason (*"Managed by configuration file"*) is conveyed via `aria-describedby` pointing to the tooltip content — not `aria-label`, which must name the action, not explain why it's unavailable. Carbon's `TooltipDefinition` on disabled items handles this binding automatically.
- Read-only detail modal: uses standard Carbon `Modal` with `aria-labelledby` pointing to the modal title. All field values are rendered as static text (not disabled inputs), ensuring screen readers announce them as content rather than interactive controls.
- Config-sourced rows remain in the tab order as interactive elements (they are clickable to open the read-only detail modal), consistent with API-sourced rows.

## Technical Analysis

---

## Technical Feasibility Assessment — Summary

### Verdict: **Feasible, High Complexity**

**The infrastructure was designed for this.** The `DbGlobalListenersState` comment literally says "pinningElementKey could be either a user task key (when pinning user task listeners) **or an element instance key (when pinning execution listeners)**." The 4 column families, CRUD processors, and batch configure processor are already type-agnostic.

### Key Findings

| Dimension | Assessment |
|-----------|-----------|
| **Complexity** | **High** — 9 layers, ~85-105 files, engine merge logic is novel |
| **Feasibility** | **High** — infrastructure explicitly designed for extension |
| **Risk** | **Medium** — engine merge logic + authorization model are primary concerns |
| **Hardest part** | Merging global ELs into `BpmnStreamProcessor` pipeline (Phase 4) |
| **Easiest parts** | Protocol, config, exporters — all follow established patterns exactly |

### Critical Architecture Decision
The `BpmnStreamProcessor` currently gets execution listeners from `ExecutableFlowNode` (static BPMN model). Global ELs are runtime config. The merge approach should **mirror `BpmnUserTaskBehavior.getTaskListeners()`** (lines 506-538): read pinned config → split into beforeNonGlobal/afterNonGlobal → sandwich BPMN model listeners → iterate unified list. A new `BpmnExecutionListenerBehavior` class encapsulates this.

### Top 3 Risks
1. **Engine merge correctness** — `executionListenerIndex` must stay consistent with merged list across pinned lifetime
2. **Replay determinism** — pinning must happen in event appliers, not processors
3. **Performance** — `categories: [all]` creates ~2× jobs per element per event type

The full plan with 30 phased todos is saved to `plan.md`. Shall I begin implementation?
