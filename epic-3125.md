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