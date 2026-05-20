# Agentic Control Plane — High-Level Specification

**Module**: `optimize/`
**Phase 1 scope**: L0 (fleet view) + L1 (single process view)
**Reference**: For query-level implementation detail see `agentic-control-plane-technical-spec-v4.md`

---

## 1. Feature Overview

The Agentic Control Plane is a dashboard in Optimize that gives operators visibility into AI agent
executions embedded in Zeebe process instances. It is a new top-level section alongside existing
reports and dashboards.

**What the user sees:**

- **L0 — Fleet view**: metrics across all agentic process instances in the selected date range
- **L1 — Process view**: same metrics scoped to a single process definition (selected via a dropdown)
- **L2 — Agent view**: scoped further to a specific agent element within the process (Phase 2)

Users navigate between levels by selecting a process from a dropdown. The date range filter and
process selector are always visible. All charts and KPIs re-fetch when either filter changes.

**Phase 1 scope boundary:**
- ✅ L0 and L1 dashboard views
- ✅ All charts and KPIs visible in the designs (images 1 and 2)

---

## 2. Data Flow

```
Zeebe engine
  AgentInstanceRecord (CREATED event) ─────────────────────────────────┐
  AgentInstanceRecord (COMPLETED event) ──────────────────────────────┐│
                                                                       ││
                                         Import Pipeline (Layer 1)     ││
                                         ─────────────────────────────┘│
                                              │ upserts into            │
                                              ▼                         │
                                   ProcessInstanceIndex                 │
                                   (nested agentInstances)              │
                                              │                         │
                                   REST API (Layer 2)                   │
                                   ─────────────────────────────────────┘
                                              │ 4 endpoints
                                              ▼
                                   Frontend (Layer 3)
                                   Dashboard components
```

**Key constraint**: Only completed process instances are queried. Running instances are not
included in any metric.

---

## 3. Layer 1 — Import Pipeline

### 3.1 What is imported

|                  Zeebe event                  |                                                                  Action                                                                   |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `AgentInstanceRecord` with intent `CREATED`   | Creates a new nested agent instance entry on the parent process instance document. Sets identity, definition, limits, and initial status. |
| `AgentInstanceRecord` with intent `COMPLETED` | Updates the existing nested entry with final metrics, final tool list, status = `COMPLETED`, and timestamps.                              |

`UPDATED` events are **not** imported in Phase 1.

### 3.2 Source fields — AgentInstanceRecord

Fields read from the Zeebe record at import time:

**Identity** (from `CREATED`):

|           Field            |   Type   |                                           Description                                            |
|----------------------------|----------|--------------------------------------------------------------------------------------------------|
| `agentInstanceKey`         | `long`   | Engine-assigned unique key. Used as the merge key when updating the nested entry on `COMPLETED`. |
| `elementInstanceKey`       | `long`   | Key of the BPMN element instance that spawned this agent.                                        |
| `elementId`                | `String` | BPMN element ID (e.g. `invoice-data-extraction-agent`).                                          |
| `processInstanceKey`       | `long`   | Owning process instance — used to locate the parent document.                                    |
| `processDefinitionKey`     | `long`   | Process definition key.                                                                          |
| `bpmnProcessId`            | `String` | BPMN process ID (the `id` attribute on the root `<process>` element).                            |
| `processDefinitionVersion` | `int`    | Version number of the process definition.                                                        |
| `versionTag`               | `String` | User-defined version tag, if set.                                                                |
| `tenantId`                 | `String` | Tenant ID.                                                                                       |

**Definition** (from `CREATED`, immutable):

|         Field         |   Type   |              Description              |
|-----------------------|----------|---------------------------------------|
| `definition.model`    | `String` | LLM model identifier (e.g. `gpt-4o`). |
| `definition.provider` | `String` | LLM provider (e.g. `openai`).         |

**Metrics** (final accumulated totals from `COMPLETED`):

|         Field          |  Type  |          Description          |
|------------------------|--------|-------------------------------|
| `metrics.inputTokens`  | `long` | Total input tokens consumed.  |
| `metrics.outputTokens` | `long` | Total output tokens produced. |
| `metrics.modelCalls`   | `int`  | Total LLM calls made.         |
| `metrics.toolCalls`    | `int`  | Total tool calls made.        |

**Tools** (final list from `COMPLETED`):

|         Field         |   Type   |           Description            |
|-----------------------|----------|----------------------------------|
| `tools[].name`        | `String` | Tool name as visible to the LLM. |
| `tools[].description` | `String` | Human-readable description.      |
| `tools[].elementId`   | `String` | BPMN element ID, if applicable.  |

**Status** (from `COMPLETED`):

|  Field   |   Type   |                                        Description                                         |
|----------|----------|--------------------------------------------------------------------------------------------|
| `status` | `String` | Final lifecycle state. Always `COMPLETED` in Phase 1 (only COMPLETED events are imported). |

**Fields intentionally excluded from Phase 1 index** (present in the Zeebe record but not stored):

|                               Field                               |                                                      Reason                                                       |
|-------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------|
| `definition.systemPrompt`                                         | No Phase 1 chart or query uses it. Reserved for Phase 2.                                                          |
| `limits.maxTokens`, `limits.maxModelCalls`, `limits.maxToolCalls` | No Phase 1 chart uses limit values. Reserved for Phase 2.                                                         |
| `versionTag`                                                      | No Phase 1 query groups by version tag. `processDefinitionVersion` (integer) is sufficient. Reserved for Phase 2. |
| `elementInstanceKey`                                              | Identity tracing; not needed for any Phase 1 aggregation.                                                         |

### 3.3 Index schema — new fields

Fields added to `ProcessInstanceIndex`. The index version must be incremented (8 → 9) before any
data arrives.

**Parent document** (three new scalar fields for cross-query aggregations):

|          Field           |  Type  |    Nullable    |                                                         Description                                                          |
|--------------------------|--------|----------------|------------------------------------------------------------------------------------------------------------------------------|
| `agentTotalInputTokens`  | `long` | No (default 0) | Sum of `metrics.inputTokens` across all agent instances on this process instance. Re-computed on each import.                |
| `agentTotalOutputTokens` | `long` | No (default 0) | Sum of `metrics.outputTokens` across all agent instances. Re-computed on each import.                                        |
| `agentTotalModelCalls`   | `long` | No (default 0) | Sum of `metrics.modelCalls` across all agent instances. Re-computed on each import. Used for `avgTokensPerCall` per process. |
| `agentTotalToolCalls`    | `long` | No (default 0) | Sum of `metrics.toolCalls` across all agent instances. Re-computed on each import.                                           |

**Nested object** `agentInstances[]` (one entry per agent instance):

|           Field            |   Type    | Nullable |                                            Description                                             |
|----------------------------|-----------|----------|----------------------------------------------------------------------------------------------------|
| `agentInstanceKey`         | `keyword` | No       | Merge key. Stored as string.                                                                       |
| `elementId`                | `keyword` | No       | BPMN element ID.                                                                                   |
| `processDefinitionVersion` | `integer` | No       | Used for incident rate by version chart.                                                           |
| `status`                   | `keyword` | No       | `COMPLETED` in Phase 1.                                                                            |
| `creationDate`             | `date`    | No       | Timestamp of the `CREATED` event.                                                                  |
| `completionDate`           | `date`    | Yes      | Timestamp of the `COMPLETED` event. Null until completed.                                          |
| `lastUpdatedDate`          | `date`    | No       | Timestamp of the most recent event written.                                                        |
| `durationInMs`             | `long`    | Yes      | `completionDate − creationDate`. Null until completed.                                             |
| `creationDateEpochMs`      | `long`    | No       | Epoch milliseconds of creation. Auxiliary field for computing `durationInMs` in the import script. |
| `definition.model`         | `keyword` | Yes      |                                                                                                    |
| `definition.provider`      | `keyword` | Yes      |                                                                                                    |
| `metrics.inputTokens`      | `long`    | No       |                                                                                                    |
| `metrics.outputTokens`     | `long`    | No       |                                                                                                    |
| `metrics.modelCalls`       | `integer` | No       |                                                                                                    |
| `metrics.toolCalls`        | `integer` | No       |                                                                                                    |
| `tools.name`               | `keyword` | —        | Repeated; one entry per tool.                                                                      |

> **Important**: the nested field must be mapped as `nested` type (not `object`). Without this,
> nested aggregations will produce incorrect results. The index version bump enforces the correct
> mapping on creation.

---

## 4. Layer 2 — REST API

**Base path**: `/api/agentic-control-plane`

All endpoints:
- Require an authenticated session. Tenant IDs are resolved server-side from the session token — never passed by the client.
- Accept only `GET` requests.
- Return `application/json`.
- Return `400 Bad Request` for invalid or missing required parameters.
- Return `401 Unauthorized` when the session is missing or expired.
- Only query **completed** process instances that contain at least one agent instance.

### Common query parameters

|       Parameter        |        Type         | Required |                                                Description                                                |
|------------------------|---------------------|----------|-----------------------------------------------------------------------------------------------------------|
| `startDateFrom`        | `string` (ISO 8601) | Yes      | Start of the date range (inclusive). Applied to `startDate` on process instances.                         |
| `startDateTo`          | `string` (ISO 8601) | Yes      | End of the date range (inclusive).                                                                        |
| `processDefinitionKey` | `string`            | No       | When provided: scopes the query to a single process definition (L1). When absent: fleet-level query (L0). |

### 4.1 `GET /summary`

**Scope**: L0 and L1.

Returns all scalar KPI values. Three fields carry a previous period comparison delta. Period deltas are `null` when there is
insufficient prior data.

**Request:**

```
GET /api/agentic-control-plane/summary
  ?startDateFrom=2025-05-01T00:00:00Z
  &startDateTo=2025-05-07T23:59:59Z
  &processDefinitionKey=2251799813685100    ← optional; omit for L0
```

**Response** `200 OK`:

```json
{
  "totalRuns": 1350,
  "totalRunsDelta": 44,

  "avgDurationMs": 3300,
  "avgDurationMsDelta": -600,

  "incidentRate": 0.0015,
  "incidentRateDelta": -0.001,
  "incidentCount": 2,
  "activationCount": 1350,

  "avgTokensPerRun": 1400,
  "medianTokensPerRun": 1300,

  "p50DurationMs": 3000,
  "p95DurationMs": 6100
}
```

|        Field         |       Type        |         Delta          |                                   Description                                   |
|----------------------|-------------------|------------------------|---------------------------------------------------------------------------------|
| `totalRuns`          | `long`            | ✅ `totalRunsDelta`     | Number of completed agentic process instances in the period.                    |
| `avgDurationMs`      | `long`            | ✅ `avgDurationMsDelta` | Mean process instance duration in milliseconds.                                 |
| `incidentRate`       | `double`          | ✅ `incidentRateDelta`  | `incidentCount / totalRuns`.                                                    |
| `incidentCount`      | `long`            | —                      | Process instances with at least one incident.                                   |
| `activationCount`    | `long`            | —                      | Same as `totalRuns` at L0/L1. Reserved for L2 (agent activations).              |
| `avgTokensPerRun`    | `long`            | —                      | `(totalInputTokens + totalOutputTokens) / totalRuns`.                           |
| `medianTokensPerRun` | `long`            | —                      | P50 of per-run total token consumption.                                         |
| `p50DurationMs`      | `long`            | —                      | Median process instance duration.                                               |
| `p95DurationMs`      | `long`            | —                      | 95th percentile process instance duration.                                      |
| `*Delta` fields      | `Long` (nullable) | —                      | Delta vs equivalent prior period of the same duration. `null` if no prior data. |

---

### 4.2 `GET /process-breakdown`

**Scope**: L0 only. Returns `400` if `processDefinitionKey` is provided.

Returns the top processes ranked by total token consumption. Powers the "Top token consumers by
process" horizontal bar chart.

**Request:**

```
GET /api/agentic-control-plane/process-breakdown
  ?startDateFrom=2025-05-01T00:00:00Z
  &startDateTo=2025-05-07T23:59:59Z
```

**Response** `200 OK`:

```json
{
  "processes": [
    {
      "processDefinitionKey": "2251799813685100",
      "totalInputTokens": 45000,
      "totalOutputTokens": 18000,
      "processInstanceCount": 320
    },
    {
      "processDefinitionKey": "2251799813685200",
      "totalInputTokens": 38000,
      "totalOutputTokens": 14000,
      "processInstanceCount": 280
    }
  ]
}
```

|               Field                |   Type   |                           Description                            |
|------------------------------------|----------|------------------------------------------------------------------|
| `processes`                        | `array`  | Ordered by `totalInputTokens + totalOutputTokens` descending.    |
| `processes[].processDefinitionKey` | `string` | Key identifying the process definition.                          |
| `processes[].totalInputTokens`     | `long`   | Sum of input tokens across all agent instances in matching runs. |
| `processes[].totalOutputTokens`    | `long`   | Sum of output tokens.                                            |
| `processes[].processInstanceCount` | `long`   | Number of completed process instances.                           |

> The frontend resolves human-readable process names from the existing
> `GET /api/process-definition` endpoint using `processDefinitionKey`. Process names are not
> included in this response.

---

### 4.3 `GET /trends`

**Scope**: L0 and L1.

Returns all time-series data in a single response. Powers three chart components:
1. **Token trend** — input and output token aggregate lines over time
2. **Token outlier bands** — P5 / P50 / P95 per-run token distribution over time
3. **Execution duration stability** — P50 / P95 process duration over time

The `interval` field in the response indicates the bucket size chosen automatically based on the
date range (e.g. `1h`, `1d`, `1w`, `1M`).

**Request:**

```
GET /api/agentic-control-plane/trends
  ?startDateFrom=2025-04-01T00:00:00Z
  &startDateTo=2025-05-07T23:59:59Z
  &processDefinitionKey=2251799813685100    ← optional; omit for L0
```

**Response** `200 OK`:

```json
{
  "interval": "1d",
  "trend": [
    {
      "date": "2025-05-01T00:00:00Z",
      "inputTokens": 85000,
      "outputTokens": 31000,
      "tokenP5": 420,
      "tokenP50": 1300,
      "tokenP95": 3800,
      "durationP50Ms": 3100,
      "durationP95Ms": 6400
    }
  ]
}
```

|          Field          |        Type         |                                  Description                                  |
|-------------------------|---------------------|-------------------------------------------------------------------------------|
| `interval`              | `string`            | Bucket size: `1h`, `1d`, `1w`, or `1M`. Chosen based on the date range width. |
| `trend`                 | `array`             | One entry per time bucket. Empty array if no data in range.                   |
| `trend[].date`          | `string` (ISO 8601) | Start of the bucket.                                                          |
| `trend[].inputTokens`   | `long`              | Sum of input tokens across all agent instances in the bucket.                 |
| `trend[].outputTokens`  | `long`              | Sum of output tokens.                                                         |
| `trend[].tokenP5`       | `long`              | 5th percentile of total tokens per run in this bucket.                        |
| `trend[].tokenP50`      | `long`              | 50th percentile (median) of total tokens per run.                             |
| `trend[].tokenP95`      | `long`              | 95th percentile of total tokens per run.                                      |
| `trend[].durationP50Ms` | `long`              | Median process instance duration in this bucket.                              |
| `trend[].durationP95Ms` | `long`              | 95th percentile process instance duration.                                    |

---

### 4.4 `GET /charts`

**Scope**: L0 and L1.

Returns bar chart data. `toolFrequency` is always populated. `avgTokensPerCall` is always
populated; at L1 (process selected), returns a single entry for the selected process.
`incidentRateByVersion` is only available at L1 — null at L0.

**Request:**

```
GET /api/agentic-control-plane/charts
  ?startDateFrom=2025-05-01T00:00:00Z
  &startDateTo=2025-05-07T23:59:59Z
  &processDefinitionKey=2251799813685100    ← optional; omit for L0
```

**Response** `200 OK`:

```json
{
  "toolFrequency": [
    { "toolName": "all_tools", "totalToolCalls": 3030 }
  ],
  "avgTokensPerCall": [
    {
      "processDefinitionKey": "2251799813685100",
      "avgTokensPerCall": 690.5,
      "totalModelCalls": 1350
    },
    {
      "processDefinitionKey": "2251799813685200",
      "avgTokensPerCall": 430.2,
      "totalModelCalls": 840
    }
  ],
  "incidentRateByVersion": [
    { "version": 3, "incidentRate": 0.008, "runs": 420 },
    { "version": 4, "incidentRate": 0.004, "runs": 930 }
  ]
}
```

|                   Field                   |       Type       |  L0  | L1 |                                                                                                                                   Description                                                                                                                                    |
|-------------------------------------------|------------------|------|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `toolFrequency`                           | `array`          | ✅    | ✅  | Phase 1: always a single-element list with `toolName = "all_tools"`. Per-tool breakdown is Phase 2 (requires history index).                                                                                                                                                     |
| `toolFrequency[].toolName`                | `string`         |      |    | `"all_tools"` in Phase 1. Will be individual tool names in Phase 2.                                                                                                                                                                                                              |
| `toolFrequency[].totalToolCalls`          | `long`           |      |    | Fleet-wide sum of `agentInstances.metrics.toolCalls` across all matched process instances.                                                                                                                                                                                       |
| `avgTokensPerCall`                        | `array`          | ✅    | ✅  | One entry per process definition. At L0: all agentic processes ranked by avg tokens per call. At L1: single entry for the selected process.                                                                                                                                      |
| `avgTokensPerCall[].processDefinitionKey` | `string`         |      |    | Process definition key.                                                                                                                                                                                                                                                          |
| `avgTokensPerCall[].avgTokensPerCall`     | `double \| null` |      |    | `(totalInputTokens + totalOutputTokens) / totalModelCalls`. `null` when `totalModelCalls = 0`.                                                                                                                                                                                   |
| `avgTokensPerCall[].totalModelCalls`      | `long`           |      |    | Total LLM calls across all agent instances of this process.                                                                                                                                                                                                                      |
| `incidentRateByVersion`                   | `array \| null`  | null | ✅  | Incident rate broken down by process definition version. Only meaningful when a single process is selected. `null` at L0.                                                                                                                                                        |
| `incidentRateByVersion[].version`         | `int`            |      |    | Process definition version number.                                                                                                                                                                                                                                               |
| `incidentRateByVersion[].incidentRate`    | `double`         |      |    | `incidentCount / runs` for this version.                                                                                                                                                                                                                                         |
| `incidentRateByVersion[].runs`            | `long`           |      |    | Total completed runs of this version.                                                                                                                                                                                                                                            |

---

### 4.5 Error responses

|       Status       |                                                             Condition                                                              |
|--------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request`  | Missing required parameter, invalid date format, or `processDefinitionKey` provided to an L0-only endpoint (`/process-breakdown`). |
| `401 Unauthorized` | No valid session.                                                                                                                  |
| `403 Forbidden`    | Session valid but tenant access denied to the requested process.                                                                   |

```json
{ "status": 400, "message": "startDateFrom is required" }
```

---

## 5. Layer 3 — Frontend

### 5.1 Filter state

A single shared filter context drives all components. When the filter changes all hooks re-fetch
automatically.

|      State field       |        Type         |   Default    |                            Description                            |
|------------------------|---------------------|--------------|-------------------------------------------------------------------|
| `processDefinitionKey` | `string \| null`    | `null`       | `null` = L0; non-null = L1. Set by the process selector dropdown. |
| `dateRange.from`       | `string` (ISO 8601) | Last 30 days | Start of the date range.                                          |
| `dateRange.to`         | `string` (ISO 8601) | Now          | End of the date range.                                            |

### 5.2 Component → endpoint mapping

|        Component        |  Level  |        Endpoint        |                                                         Data used                                                          |
|-------------------------|---------|------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `SummaryKPIs`           | L0, L1  | `/summary`             | `totalRuns`, `totalRunsDelta`, `avgDurationMs`, `avgDurationMsDelta`, `incidentRate`, `incidentRateDelta`, `incidentCount` |
| `TokenStats`            | L0, L1  | `/summary`             | `avgTokensPerRun`, `medianTokensPerRun`                                                                                    |
| `DurationStats`         | L0, L1  | `/summary` + `/trends` | P50/P95 scalars from `/summary`; trend chart from `/trends`                                                                |
| `TokenTrendChart`       | L0, L1  | `/trends`              | `trend[].inputTokens`, `trend[].outputTokens`                                                                              |
| `TokenOutlierBands`     | L0, L1  | `/trends`              | `trend[].tokenP5`, `trend[].tokenP50`, `trend[].tokenP95`                                                                  |
| `TopProcesses`          | L0 only | `/process-breakdown`   | `processes[]`                                                                                                              |
| `ToolCallFrequency`     | L0, L1  | `/charts`              | `toolFrequency[]`                                                                                                          |
| `AvgTokensPerProcess`   | L0, L1  | `/charts`              | `avgTokensPerCall[]`                                                                                                       |
| `IncidentRateByVersion` | L1 only | `/charts`              | `incidentRateByVersion[]`                                                                                                  |

> `DurationStats`, `TokenTrendChart`, and `TokenOutlierBands` all share the **same `/trends`
> response** — one request, three components. Similarly `SummaryKPIs`, `TokenStats`, and
> `DurationStats` scalars all share the **same `/summary` response**.

### 5.3 Page-load request pattern

All requests fire in **parallel** on initial load and on every filter change.

| Level |                     Requests fired                     |
|-------|--------------------------------------------------------|
| L0    | `/summary`, `/process-breakdown`, `/trends`, `/charts` |
| L1    | `/summary`, `/trends`, `/charts`                       |

`/process-breakdown` is not called at L1 (the top processes chart is not shown).

### 5.4 Display rules

|               Rule               |                                                Detail                                                |
|----------------------------------|------------------------------------------------------------------------------------------------------|
| `avgTokensPerCall === null`      | Render cell as `"—"` not `"0"`                                                                       |
| `toolFrequency[].totalToolCalls` | Axis label: **"Total tool calls"** (sum of `metrics.toolCalls` from agent instances using this tool) |
| `incidentRate`                   | Format as percentage: `0.0015` → `0.15%`, 2 decimal places                                           |
| `*Delta` badge                   | Show `+44` / `−0.6s` / `−0.10%` badge. Hide badge when value is `null`.                              |
| No data                          | Show empty state per chart, not a global error                                                       |
| No "Configure in settings" link  | Removed from Incident Rate card (settings page not in scope)                                         |

### 5.5 Process selector

The process dropdown is populated by the **existing** `GET /api/process-definition` endpoint —
not by any Agentic Control Plane endpoint. The endpoint must be extended to support a filter for
processes that have at least one agentic run in the selected date range. This is a **new requirement
on an existing endpoint** — a `hasAgentRuns=true` query parameter (or equivalent) that the
Agentic Control Plane frontend will pass when populating the selector.

---

## 6. Cross-cutting Concerns

### Multi-tenancy

- Tenant IDs are always resolved server-side from the authenticated session.
- Every query filters by the resolved tenant set.
- Clients never pass `tenantId` as a request parameter.

### Authorization

- Standard Optimize role-based access applies.
- A user can only see data for processes and tenants they have access to.
- `403` is returned (not `404`) when a process exists but is not accessible.

### Date range semantics

- `startDateFrom` / `startDateTo` apply to the process instance `startDate` field.
- Timezone handling follows the existing Optimize convention (UTC unless the user has configured a timezone).

### Previous period comparison

- Applies to `/summary` only, for three fields: `totalRuns`, `avgDurationMs`, `incidentRate`.
- Prior window = same duration, shifted back by the same duration as the selected range.
- Example: current = May 1–7 → prior = Apr 24–30.
- period delta = `current − prior`. Negative delta = improvement for duration/incidentRate.
- `null` when there are zero runs in the prior window.

---

## 7. Task Breakdown

T-shirt sizing: **XS** (trivial) · **S** (small, focused) · **M** (moderate, multiple moving parts) · **L** (large, cross-cutting or high risk)

Developers are expected to create sub-tasks for any item below.

---

### Layer 1 — Import Pipeline

|                                                Task                                                 | Size |                                                      Notes                                                       |
|-----------------------------------------------------------------------------------------------------|------|------------------------------------------------------------------------------------------------------------------|
| Bump `ProcessInstanceIndex` version, add new parent-level and nested field definitions              | S    | Must be deployed before any agent data arrives. Incorrect nested mapping silently breaks all aggregations.       |
| Import service for `AgentInstanceRecord` — handle `CREATED` and `COMPLETED` intents                 | M    | Follow existing incident import pattern. Sort events by timestamp before processing to avoid negative durations. |
| Painless update script — upsert nested agent entry, re-aggregate parent-level token and call totals | M    | Validate against a live ES/OS instance. No compile-time error detection for Painless.                            |

---

### Layer 2 — Backend API

|                                                           Task                                                            | Size |                                               Notes                                               |
|---------------------------------------------------------------------------------------------------------------------------|------|---------------------------------------------------------------------------------------------------|
| Extend `GET /api/process-definition` to filter by agentic runs                                                            | S    | New `hasAgentRuns` query param required for the process selector. Change to an existing endpoint. |
| Shared query utilities: baseline filter builder, period comparison executor, date interval resolver, incident rate helper | S    | Foundation for all endpoints. Complete before starting individual endpoints.                      |
| `GET /summary` endpoint                                                                                                   | M    | Scalar aggs + parallel previous period query for three delta fields.                              |
| `GET /process-breakdown` endpoint                                                                                         | S    | Top-level terms agg. L0 only.                                                                     |
| `GET /trends` endpoint                                                                                                    | S    | Single date histogram with four sub-aggs.                                                         |
| `GET /charts` endpoint                                                                                                    | M    | Conditional agg structure (L0 vs L1). Nested `by_tool` + top-level `by_process` and `by_version`. |

---

### Layer 3 — Frontend

|                                     Task                                      | Size |                                               Notes                                               |
|-------------------------------------------------------------------------------|------|---------------------------------------------------------------------------------------------------|
| Filter context, date range picker, process selector dropdown                  | S    | Shared state driving all components. Requires `GET /api/process-definition` extension (see §5.5). |
| API client, TypeScript types, and mock responses                              | S    | Complete before building any component. Enables parallel development against mocks.               |
| KPI cards: Total Runs, Avg Duration, Incident Rate (with period delta badges) | S    | Uses `/summary`.                                                                                  |
| Token stats: Avg Tokens Per Run, Median Tokens Per Run                        | XS   | Uses `/summary` — same request as KPI cards, no extra fetch.                                      |
| Duration stats: P50/P95 KPI cards + duration stability trend chart            | S    | Scalars from `/summary`; trend chart from `/trends` (shared with token charts).                   |
| Token trend chart (input + output lines) and token outlier bands              | S    | Both use `/trends` — one request, two components.                                                 |
| Top token consumers by process bar chart                                      | S    | Uses `/process-breakdown`. L0 only.                                                               |
| Tool call frequency bar chart                                                 | S    | Uses `/charts`. L0 + L1.                                                                          |
| Avg tokens per call by process bar chart                                      | S    | Uses `/charts`. Same request as tool frequency.                                                   |
| Incident rate by version bar chart                                            | S    | Uses `/charts`. L1 only.                                                                          |
| Dashboard layout, routing, and level switching (L0 ↔ L1)                      | S    | Compose all components. Handle empty states per chart.                                            |

---

### Integration and Validation

|                           Task                            | Size |                                   Notes                                   |
|-----------------------------------------------------------|------|---------------------------------------------------------------------------|
| End-to-end smoke test with real Zeebe agent instance data | M    | Validates full pipeline: Zeebe record → import → index → API → dashboard. |
| Performance validation of nested aggregations at scale    | S    | Focus on `/charts` `by_tool` nested terms at L0 fleet scope.              |

