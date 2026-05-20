# Agentic Control Plane — High-Level Specification (Custom UI, No New Endpoints, Hard-Coded Reports)

**Module**: `optimize/`
**Phase 1 scope**: L0 (fleet view) + L1 (single process view)
**Companion to**: `agentic-control-plane-spec-high-level.md` (Variant 1)

> **What this variant changes vs Variant 1**
>
> - **Layer 1 (import pipeline)**: unchanged (with the `agentTotalTokens`
>   denormalization required for token percentile aggregations).
> - **Layer 2 (backend)**: **no new REST endpoints**. The custom UI calls
>   the **existing** `POST /api/report/{id}/evaluate` endpoint against
>   **pre-seeded saved reports with deterministic IDs**. Report
>   definitions live in `instant_preview_dashboards/template4.json`
>   (following the existing incremental naming convention so that
>   `InstantPreviewDashboardService.getCurrentFileChecksums()` discovers
>   it automatically). The reports are created **eagerly at startup** by
>   a **new** `@EventListener(ApplicationReadyEvent)` method added to
>   `InstantPreviewDashboardService` — the existing `@EventListener` at
>   line 325 (`deleteInstantPreviewDashboardsAndEntitiesForChangedTemplates`)
>   only deletes outdated entities and does not create new ones.
>   The import service must be extended to **honor the `id` field from
>   the template JSON** instead of generating a fresh UUID, in order to
>   satisfy the deterministic-ID contract. Reports are **persisted
>   entities with stable IDs**, but their **source of truth is the
>   template file in source code** — checksum detection identifies
>   template drift, and the agentic startup reconciler applies template
>   changes **in place** for existing IDs (instead of delete+recreate) so
>   alert continuity is preserved. A read-only permission
>   lock blocks normal edit/delete/copy attempts at runtime. Filter
>   overrides (date range for both L0/L1; `processDefinitionKey` for L1)
>   are applied via the report-evaluation request body.
>   `AdditionalProcessReportEvaluationFilterDto` must be **extended** to
>   carry both the existing `filter` list and a new `definitions` array,
>   so that L1 calls can scope the evaluation to a single process
>   definition; L0 calls leave `definitions` unset.
> - **Layer 3 (frontend)**: **custom UI implementation preserved** —
>   bespoke React dashboard page at `/agentic-control-plane` with
>   pixel-perfect PNG match, KPI cards with period delta badges, L0/L1
>   toggle. The only change is that the API client calls existing
>   report endpoints with deterministic saved-report IDs instead of
>   calling dedicated agentic endpoints. Period delta computation moves
>   from backend to frontend (two evaluations per delta-bearing metric,
>   subtraction in the client).
>
> Result: same user-facing dashboard as Variant 1, same data
> capabilities, **single backend code path** shared with every other
> Optimize report. To satisfy the requirement for alertability and
> discoverability, this variant publishes agentic reports into a
> dedicated **system-managed collection** (not private instant-preview
> entities), while keeping edit/delete/copy guardrails in place.

---

## 1. Feature Overview

(Identical to `agentic-control-plane-spec-high-level.md` §1.)

The Agentic Control Plane is a dashboard in Optimize that gives operators
visibility into AI agent executions embedded in Zeebe process instances.

**What the user sees:**

- **L0 — Fleet view**: metrics across all agentic process instances in the selected date range
- **L1 — Process view**: same metrics scoped to a single process definition
- **L2 — Agent view**: scoped further to a specific agent element (Phase 2)

**Phase 1 scope boundary:**
- ✅ L0 and L1 dashboard views
- ✅ All charts and KPIs visible in the designs (images 1 and 2)
- ✅ Period delta badges on KPI cards (computed client-side)

---

## 2. Data Flow

```
Zeebe engine
  AgentInstanceRecord (CREATED) ──────────────────────────────────────────┐
  AgentInstanceRecord (COMPLETED) ────────────────────────────────────────│
                                                                          │
                            Layer 1 — Import Pipeline (UNCHANGED)         │
                            ───────────────────────────────────────────── │
                                              │ upserts into              │
                                              ▼                           │
                                   ProcessInstanceIndex                   │
                                   (nested agentInstances +               │
                                    denormalized parent totals incl.      │
                                    agentTotalTokens)                     │
                                              │                           │
                            Layer 2 — Backend (NO new endpoints)          │
                            ───────────────────────────────────────────── │
                                              │                           │
                            template4.json (source-of-truth)              │
                            loaded by InstantPreviewDashboardService      │
                            at @EventListener(ApplicationReadyEvent)      │
                                              │                           │
                            ┌─────────────────┴─────────────────┐         │
                            ▼                                   ▼         │
                   Persisted saved reports          System-managed collection│
                   with deterministic IDs           publication + guardrails │
                   (alertable/discoverable)         (edit/delete/copy block)│
                            ▼                                   │         │
                            └─────────────────┬─────────────────┘         │
                                              ▼                           │
                            Existing POST /api/report/{id}/evaluate       │
                            (saved evaluation with filter overrides)      │
                                              │                           │
                            ReportEvaluationHandler                       │
                            (shared view/groupBy interpreters,            │
                             shared filter chain)                         │
                                              │                           │
                            Layer 3 — Frontend (CUSTOM UI)                │
                            ───────────────────────────────────────────── │
                                              │                           │
                            Bespoke React dashboard                       │
                            /agentic-control-plane                        │
                                              │                           │
                            API client knows the deterministic            │
                            saved-report IDs, fires parallel              │
                            evaluations with date-range + scope           │
                            filter overrides, computes period             │
                            deltas client-side                            │
                                              │                           │
                                              ▼                           │
                                       Operator's browser                 │
```

---

## 3. Layer 1 — Import Pipeline

**Identical to `agentic-control-plane-spec-high-level.md` §3** plus one
additional parent-level denormalized field.

### 3.1 What is imported

Same Zeebe events (`AgentInstanceRecord` CREATED + COMPLETED), same
identity fields, same definition fields, same metrics fields, same tools,
same status. See V1 spec §3.1–§3.2.

### 3.2 Index schema — new fields

Same nested `agentInstances[]` schema as V1 spec §3.3, plus the V1
parent-level scalar fields (`agentTotalInputTokens`,
`agentTotalOutputTokens`, `agentTotalModelCalls`, `agentTotalToolCalls`),
**plus one additional denormalized field**:

| Field | Type | Nullable | Description |
|---|---|---|---|
| `agentTotalTokens` | `long` | No (default 0) | Sum of `metrics.inputTokens + metrics.outputTokens` across all agent instances on this process instance. Re-computed on each import. Required for `avgTokensPerRun`, `medianTokensPerRun`, and per-bucket token percentile aggregations to be expressible as standard report aggregations on a single field. |

Painless update script extended to compute and write this field on every
import event.

---

## 4. Layer 2 — Backend Strategy (No New Endpoints, Hard-Coded Reports)

### 4.1 Existing endpoints used by the custom UI

| Endpoint | Purpose |
|---|---|
| `POST /api/report/{id}/evaluate` | Evaluate the pre-seeded agentic saved report identified by `{id}` with optional filter overrides (date range, processDefinitionKey) in the request body |
| `GET /api/process-definition` (extended with `hasAgentRuns=true`) | Process selector dropdown |

**No new endpoints** under `/api/agentic-control-plane/*`.

The agentic dashboard reads from **pre-seeded persistent saved reports**
loaded at app startup from `template4.json` — see §4.3.

### 4.2 Required shared infrastructure additions

The agentic metrics need new view, view-property, groupBy, and filter
primitives to exist as report-evaluable shapes. These additions live in
shared `optimize-commons` / `service/db/.../report/` packages — the same
code paths used by every other Optimize report.

| Category | Items |
|---|---|
| **Filter** | `HasAgentInstancesFilterDataDto` + Jackson subtype on `ProcessFilterDto` + `HasAgentInstancesQueryFilterES` + `HasAgentInstancesQueryFilterOS` + registration in `ProcessQueryFilterEnhancerES/OS` |
| **View entity** | `ProcessViewEntity.AGENT_INSTANCE` |
| **View properties** | `INPUT_TOKENS`, `OUTPUT_TOKENS`, `MODEL_CALLS`, `TOOL_CALLS`, `TOTAL_TOKENS` |
| **`ProcessView` enum entries** | One per (AGENT_INSTANCE, viewProperty) combination |
| **View interpreters (ES + OS)** | `ProcessViewAgent{Input,Output,Total}Tokens / ModelCalls / ToolCalls InterpreterES/OS`, extending `AbstractProcessViewMultiAggregationInterpreterES/OS` |
| **Custom view interpreter** | `ProcessViewAgentAvgTokensPerCallInterpreterES/OS` — ratio of sums, follows the existing `ProcessViewInstancePercentageInterpreter` pattern |
| **GroupBy** | `PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY` + `PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION` + DTOs + ES/OS interpreters + facade registration |

These primitives are reused implicitly by Optimize's existing query
machinery. They are **not** exposed in the report builder UI — operators
do not see them as user-facing configuration options. They exist solely
to make the hard-coded agentic reports expressible.

### 4.3 Hard-coded report definitions (`template4.json`)

A new template file `instant_preview_dashboards/template4.json` ships
in the existing templates directory alongside `template1.json`,
`template2.json`, `template3.json`. The incremental naming is required
because `InstantPreviewDashboardService.getCurrentFileChecksums()`
discovers templates by incrementing the numeric suffix until the next
file is absent — a non-numeric name like `template_agentic.json` would
never be discovered. The agentic reports are created **eagerly at
startup** by a **new** `@EventListener(ApplicationReadyEvent)` method
added to `InstantPreviewDashboardService` — distinct from the existing
`deleteInstantPreviewDashboardsAndEntitiesForChangedTemplates()` at line
325, which only deletes outdated entities. The new startup method must
also instruct the import pipeline to **preserve the `id` fields** from
the template JSON rather than generating fresh UUIDs, so that the
TypeScript constants remain valid across restarts.

The template contains **N saved reports with deterministic IDs**, plus
optionally a companion dashboard composition (see §4.9 — the dashboard
itself is optional; the custom UI does not depend on it).

#### Why keep a template if the UI uses custom tiles?

The bespoke `/agentic-control-plane` UI does **not** read dashboard tile
layout from Optimize dashboard entities. It renders its own React tiles
and only needs stable report IDs for `POST /api/report/{id}/evaluate`.

However, the backend still needs a server-owned source of truth for the
report definitions because:

1. **Alerts and discoverability require persisted entities** (collection-backed
   reports with stable IDs), not ad-hoc inline evaluations.
2. `POST /api/report/evaluate` evaluates an unsaved definition and does
   not create durable entities; that path cannot satisfy alert/listing
   requirements by itself.
3. Unsaved **management** reports are rejected by `ReportRestService`,
   so the fleet (L0) use case cannot rely on inline unsaved management
   definitions.

Therefore, `template4.json` is primarily a **report-definition seed**
artifact. A companion dashboard block is optional.

Each saved report is a complete `SingleProcessReportDefinitionExportDto`
with a stable `id` field. The IDs are constants known to the custom UI
at build time:

```json
{
  "reports": [
    {
      "id": "agentic-total-runs",
      "name": "Agentic Total Runs",
      "data": {
        "view": { "entity": "processInstance", "properties": ["frequency"] },
        "groupBy": { "type": "none" },
        "filter": [
          { "type": "completedInstancesOnly" },
          { "type": "hasAgentInstances" }
        ]
      }
    },
    {
      "id": "agentic-duration-summary",
      "name": "Agentic Duration Summary",
      "data": {
        "view": { "entity": "processInstance", "properties": ["duration"] },
        "groupBy": { "type": "none" },
        "configuration": {
          "aggregationTypes": [
            { "type": "avg" },
            { "type": "percentile", "value": 50.0 },
            { "type": "percentile", "value": 95.0 }
          ]
        },
        "filter": [ ... ]
      }
    }
    // … one entry per agentic report shape
  ],
  "dashboard": { ... optional companion composition ... }
}
```

#### Immutability — two enforcement layers

1. **Checksum-owned source of truth**: `InstantPreviewDashboardService`
   computes a checksum of each template file. The existing
   `@EventListener` is delete-based, but for agentic reports the new
   startup reconciler (§4.3, §4.8) must use checksum drift as a trigger
   for **in-place reconciliation** of existing deterministic IDs (not
   destructive delete+recreate), so template changes are applied without
   breaking alert bindings.
2. **Permission lock at runtime**: template-managed entities are protected
   by existing management/instant-preview guardrails in `ReportService`:
   update, delete, and copy operations are rejected. Operators can view
   and evaluate these reports, but cannot mutate them directly.

The runtime mutability guardrails prevent UX confusion (operators do not
see edit/copy actions that silently no-op on restart). The checksum
layer provides defense-in-depth for any edge case where these guardrails
are bypassed (e.g., direct database modification).

> **Implementation detail**: Optimize today already enforces
> immutability for management/instant-preview reports in `ReportService`
> (copy/update/delete are rejected) and uses checksum-based drift
> detection. The agentic path reuses these guardrails but adds
> in-place reconciliation for stable IDs.

### 4.4 Catalog of hard-coded report shapes

| Saved-report ID | View | GroupBy | Aggregations | Filters |
|---|---|---|---|---|
| `agentic-total-runs` | `PROCESS_INSTANCE / FREQUENCY` | `NONE` | count | baseline |
| `agentic-duration-summary` | `PROCESS_INSTANCE / DURATION` | `NONE` | `{AVERAGE, PERCENTILE(50), PERCENTILE(95)}` | baseline |
| `agentic-duration-trend` | `PROCESS_INSTANCE / DURATION` | `PROCESS_INSTANCE_START_DATE` (date histogram) | `{PERCENTILE(50), PERCENTILE(95)}` | baseline |
| `agentic-incident-count` | `PROCESS_INSTANCE / FREQUENCY` | `NONE` | count | baseline + `OpenIncidentFilter` OR `ResolvedIncidentFilter` |
| `agentic-incident-rate` | `PROCESS_INSTANCE / PERCENTAGE` | `NONE` | percentage (built-in) | baseline + incident filter |
| `agentic-incident-rate-by-version` | `PROCESS_INSTANCE / PERCENTAGE` | `PROCESS_DEFINITION_VERSION` | percentage per bucket | baseline + incident filter (L1 only) |
| `agentic-tokens-summary` | `AGENT_INSTANCE / TOTAL_TOKENS` | `NONE` | `{AVERAGE, PERCENTILE(50)}` | baseline |
| `agentic-tokens-trend` | `AGENT_INSTANCE / TOTAL_TOKENS` | `PROCESS_INSTANCE_START_DATE` | `{SUM, PERCENTILE(5), PERCENTILE(50), PERCENTILE(95)}` | baseline |
| `agentic-tokens-input-trend` | `AGENT_INSTANCE / INPUT_TOKENS` | `PROCESS_INSTANCE_START_DATE` | `SUM` | baseline |
| `agentic-tokens-output-trend` | `AGENT_INSTANCE / OUTPUT_TOKENS` | `PROCESS_INSTANCE_START_DATE` | `SUM` | baseline |
| `agentic-tokens-by-process` | `AGENT_INSTANCE / TOTAL_TOKENS` | `PROCESS_DEFINITION_KEY` | `SUM`, descending | baseline |
| `agentic-process-instance-count-by-process` | `PROCESS_INSTANCE / FREQUENCY` | `PROCESS_DEFINITION_KEY` | count | baseline |
| `agentic-tool-calls-total` | `AGENT_INSTANCE / TOOL_CALLS` | `NONE` | `SUM` | baseline |
| `agentic-avg-tokens-per-call-by-process` | custom interpreter (ratio of sums) | `PROCESS_DEFINITION_KEY` | ratio (computed by interpreter) | baseline |

Baseline filters = `CompletedInstancesOnlyFilterDto` + `HasAgentInstancesFilterDto`.

For **Phase 1**, tooling is intentionally simplified to a single
`agentic-tool-calls-total` metric (no per-tool breakdown report).

All IDs are stable across releases. The custom UI imports them as
TypeScript constants:

```ts
export const AGENTIC_REPORT_IDS = {
  totalRuns: 'agentic-total-runs',
  durationSummary: 'agentic-duration-summary',
  // …
} as const;
```

### 4.5 Filter override at request time

The saved-report definitions carry the baseline filter set
(`completedInstancesOnly` + `hasAgentInstances`). The UI applies
**date range** and (for L1) **processDefinitionKey** as overrides on
each evaluation request via the `POST /api/report/{id}/evaluate`
request body.

> **Backend change required**: `AdditionalProcessReportEvaluationFilterDto`
> must be extended with a `definitions` field (in addition to the existing
> `filter` list) so that L1 calls can scope the evaluation to a single
> process definition. The current DTO only carries `filter`; adding
> `definitions` is a non-breaking additive change.

```http
POST /api/report/agentic-total-runs/evaluate
Content-Type: application/json

{
  "filter": [
    {
      "type": "instanceStartDate",
      "data": { "type": "fixed", "start": "2026-04-19T...", "end": "2026-05-19T..." }
    }
  ]
}
```

For L1 (single process) view, the UI also sets the `definitions` array
in the request body to scope the evaluation to a single process
definition (see §5.3 for the extended request type). For L0 (fleet)
view, `definitions` is omitted and the management report evaluates
across all process definitions the operator has access to.

### 4.6 Period delta computation (client-side)

For each delta-bearing metric (`totalRuns`, `avgDurationMs`,
`incidentRate`):

1. UI calls the corresponding saved report **twice** in parallel via
   `POST /api/report/{id}/evaluate`:
   - Call A: filter override `dateRange = currentPeriod`
   - Call B: filter override `dateRange = priorPeriod`
2. UI extracts the scalar from each response
3. UI computes `delta = current − prior` (or `null` if prior is zero)
4. Delta is rendered as the KPI card badge

Pure client-side, no backend logic. The saved report itself is unchanged
between the two calls — only the filter override in the request body
differs.

### 4.7 `/summary`-equivalent composition (client-side)

The original spec's `/summary` response is assembled by the UI from
parallel evaluations of:

- `agentic-total-runs` (×2 for delta)
- `agentic-duration-summary` (×2 for delta) — single query returns AVERAGE, P50, P95
- `agentic-incident-rate` (×2 for delta)
- `agentic-incident-count` (×1)
- `agentic-tokens-summary` (×1) — single query returns AVERAGE (= `avgTokensPerRun`) and PERCENTILE(50) (= `medianTokensPerRun`)

Total: **~8 HTTP calls** per page load for the summary block,
parallelized via `Promise.all`. Browser HTTP/2 multiplexing handles
this. Each call goes through the standard `ReportEvaluationHandler`
authorization, filter normalization, and ES query planning paths.

### 4.8 Alert support (required)

To make agentic reports alertable, the implementation must **not** rely
on private instant-preview entities. Required backend behavior:

1. Bootstrap (or reuse) a dedicated system collection with a stable ID
   (e.g., `agentic-control-plane`) at startup.
2. Publish agentic reports into that collection (`collectionId != null`)
   so `AlertService.validateAlert(...)` passes.
3. Grant collection roles to operator identities (minimum role required
   by alert creation policy; current behavior requires EDITOR).
4. Keep deterministic report IDs stable across template updates.
5. Avoid delete+recreate churn for existing report IDs, because
   `ReportService` deletion triggers `AlertService.handleReportDeleted`,
   which removes alerts for the report.

### 4.9 Discoverability (required)

Discoverability is achieved via the same collection-backed publication:

1. Published reports appear in standard **collection report listings**
   (`/collection/{id}/reports`) for authorized users.
2. Published dashboards appear in standard **collection dashboard/entity
   listings** (`/collection/{id}/entities` and dashboard lookups).
3. Optional companion dashboard in `template4.json` provides a
   dashboard-surface entry in that same collection.

Note: private/global "My reports" lists continue to exclude
management/instant-preview entities by design. Discoverability for this
feature is collection-scoped.

### 4.10 What is different vs Variant 1

| Capability | V1 | vReuse |
|---|---|---|
| Period delta badges | ✅ backend executor | ✅ client-side compute |
| Single-query `/summary` (1 ES query × 2 periods = 2 ES queries total) | ✅ | ❌ Multiple saved-report evaluations (delta-bearing metrics are evaluated twice): ~8 ES queries for summary block, ~15–16 for full page load |
| `/summary` HTTP round-trip count | 1 | ~8 (parallel) |
| Custom multi-metric response shape | ✅ tightly tuned | Composed in UI |
| Alert support on agentic KPIs | ❌ | ✅ with collection-backed system publication |
| Discoverability via Dashboards / Reports list | ❌ | ✅ in collection-scoped lists (reports/entities) |
| Single backend code path | ❌ — parallel `AgenticControlPlaneRepository` | ✅ — every query through `ReportEvaluationHandler` |
| Risk of regression in existing reports | None | Medium (shared infra changes) |

> No major capability is lost compared to V1. vReuse keeps the same
> dashboard capability with more HTTP round-trips per load and
> shared-infrastructure regression risk.

---

## 5. API Contracts and Mock Fixtures (for independent UI development)

This section gives the frontend team everything needed to build the UI
against mocks while the backend infrastructure work is in flight.

### 5.1 Endpoint surface seen by the UI

The **agentic dashboard runtime** uses exactly two endpoint families:

| Method + Path | Purpose |
|---|---|
| `POST /api/report/{id}/evaluate` | Evaluate one pre-seeded agentic saved report with filter overrides. Called once per tile per render. |
| `GET /api/process-definition?hasAgentRuns=true` | Populate the process selector dropdown with processes that contain at least one agent run. |

### 5.2 Saved-report ID constants

The UI imports these as TypeScript constants. They are stable across
releases and committed to source code.

```ts
export const AGENTIC_REPORT_IDS = {
  totalRuns:                       'agentic-total-runs',
  durationSummary:                 'agentic-duration-summary',
  durationTrend:                   'agentic-duration-trend',
  incidentCount:                   'agentic-incident-count',
  incidentRate:                    'agentic-incident-rate',
  incidentRateByVersion:           'agentic-incident-rate-by-version',
  tokensSummary:                   'agentic-tokens-summary',
  tokensTrend:                     'agentic-tokens-trend',
  tokensInputTrend:                'agentic-tokens-input-trend',
  tokensOutputTrend:               'agentic-tokens-output-trend',
  tokensByProcess:                 'agentic-tokens-by-process',
  processInstanceCountByProcess:   'agentic-process-instance-count-by-process',
  toolCallsTotal:                  'agentic-tool-calls-total',
  avgTokensPerCallByProcess:       'agentic-avg-tokens-per-call-by-process',
} as const;
```

### 5.3 Request body shape

The report-evaluation request body is `AdditionalProcessReportEvaluationFilterDto`,
which currently only contains `filter`. It must be **extended** with a
`definitions` field (see §4.5) to support L1 scoping. After the extension,
the UI sends:

```ts
// AdditionalProcessReportEvaluationFilterDto (extended)
type EvaluateReportRequest = {
  filter?: ProcessFilterDto[];               // appended to the report's baseline filters
  definitions?: ReportDataDefinitionDto[];   // new field — used at L1 to scope to one process
};
```

**L0 example** (fleet view) — date range only:
```http
POST /api/report/agentic-total-runs/evaluate
Content-Type: application/json

{
  "filter": [
    {
      "type": "instanceStartDate",
      "filterLevel": "instance",
      "data": {
        "type": "fixed",
        "start": "2026-04-19T00:00:00+00:00",
        "end":   "2026-05-19T23:59:59+00:00"
      }
    }
  ]
}
```

**L1 example** (single process) — date range + processDefinitionKey override:
```http
POST /api/report/agentic-total-runs/evaluate
Content-Type: application/json

{
  "filter": [
    {
      "type": "instanceStartDate",
      "filterLevel": "instance",
      "data": {
        "type": "fixed",
        "start": "2026-04-19T00:00:00+00:00",
        "end":   "2026-05-19T23:59:59+00:00"
      }
    }
  ],
  "definitions": [
    {
      "identifier": "process",
      "key": "invoice-approval",
      "versions": ["all"],
      "tenantIds": [null]
    }
  ]
}
```

### 5.4 Response shape — `AuthorizedReportEvaluationResponseDto`

```ts
type EvaluateReportResponse = {
  currentUserRole: 'viewer' | 'editor';
  id: string;
  name: string;
  data: ProcessReportDataDto;                        // report definition echoed back
  result: {
    instanceCount: number;
    instanceCountWithoutFilters: number;
    measures: Measure[];
    pagination?: PaginationDto;
  };
};

type Measure = {
  property: 'frequency' | 'duration' | 'percentage' | 'rawData'
          | 'inputTokens' | 'outputTokens' | 'modelCalls' | 'toolCalls' | 'totalTokens';
  aggregationType?: { type: 'avg' | 'min' | 'max' | 'sum' | 'percentile'; value?: number };
  userTaskDurationTime?: string;
  data: MeasureData;
};

// data shape depends on groupBy:
type MeasureData =
  | number                          // GROUP_BY_NONE → scalar
  | MapResultEntry[]                // GROUP_BY_*_KEY/VERSION/etc → bucketed
  | HyperMapResultEntry[];          // GROUP_BY + distributedBy → bucketed with sub-buckets

type MapResultEntry = {
  key: string;                      // bucket key (e.g. processDefinitionKey, version, date)
  label?: string;
  value: number | null;
};

type HyperMapResultEntry = {
  key: string;
  label?: string;
  value: MapResultEntry[];
};
```

### 5.5 Per-tile contract — concrete fixtures

#### 5.5.1 KPI: Total Runs (with delta)

**Reports called** (in parallel): `agentic-total-runs` twice — once with
current period, once with prior period.

**Mock response — current period**:
```json
{
  "currentUserRole": "viewer",
  "id": "agentic-total-runs",
  "name": "Agentic Total Runs",
  "data": { "...": "echo of report definition" },
  "result": {
    "instanceCount": 1350,
    "instanceCountWithoutFilters": 1402,
    "measures": [
      { "property": "frequency", "data": 1350 }
    ]
  }
}
```

**Mock response — prior period**:
```json
{ "result": { "measures": [ { "property": "frequency", "data": 1306 } ] } }
```

**UI composition**:
```ts
const current = response.result.measures[0].data as number;          // 1350
const prior   = priorResponse.result.measures[0].data as number;     // 1306
const delta   = prior > 0 ? current - prior : null;                  // 44
const kpi = { value: current, delta };                                // { value: 1350, delta: 44 }
```

#### 5.5.2 KPI: Avg / P50 / P95 Duration (single call, three measures)

**Report called**: `agentic-duration-summary`

**Mock response**:
```json
{
  "result": {
    "instanceCount": 1350,
    "measures": [
      { "property": "duration", "aggregationType": { "type": "avg" },        "data": 3300 },
      { "property": "duration", "aggregationType": { "type": "percentile", "value": 50.0 }, "data": 3000 },
      { "property": "duration", "aggregationType": { "type": "percentile", "value": 95.0 }, "data": 6100 }
    ]
  }
}
```

**UI composition**:
```ts
const measures = response.result.measures;
const avgMs = measures.find(m => m.aggregationType?.type === 'avg')!.data as number;
const p50Ms = measures.find(m => m.aggregationType?.value === 50.0)!.data as number;
const p95Ms = measures.find(m => m.aggregationType?.value === 95.0)!.data as number;
// → { avgMs: 3300, p50Ms: 3000, p95Ms: 6100 }
```

For the `avgDurationMs` delta: call `agentic-duration-summary` twice and subtract the AVG measure.

#### 5.5.3 KPI: Incident Rate + Count

**Reports called**: `agentic-incident-rate` (×2 for delta) + `agentic-incident-count` (×1)

**Mock response — incident-rate**:
```json
{
  "result": {
    "instanceCount": 1350,
    "instanceCountWithoutFilters": 1350,
    "measures": [ { "property": "percentage", "data": 0.148 } ]
  }
}
```

`data` is in percent (0.148 → 0.15%).

**Mock response — incident-count**:
```json
{ "result": { "instanceCount": 2, "measures": [ { "property": "frequency", "data": 2 } ] } }
```

#### 5.5.4 KPI: Avg / Median Tokens Per Run (single call, two measures)

**Report called**: `agentic-tokens-summary`

**Mock response**:
```json
{
  "result": {
    "measures": [
      { "property": "totalTokens", "aggregationType": { "type": "avg" },        "data": 1400 },
      { "property": "totalTokens", "aggregationType": { "type": "percentile", "value": 50.0 }, "data": 1300 }
    ]
  }
}
```

#### 5.5.5 Chart: Duration Trend (P50 / P95 over time)

**Report called**: `agentic-duration-trend` — date histogram groupBy.

**Mock response** (truncated to two buckets):
```json
{
  "result": {
    "measures": [
      {
        "property": "duration",
        "aggregationType": { "type": "percentile", "value": 50.0 },
        "data": [
          { "key": "2026-05-01T00:00:00.000+0000", "value": 3100 },
          { "key": "2026-05-02T00:00:00.000+0000", "value": 3000 }
        ]
      },
      {
        "property": "duration",
        "aggregationType": { "type": "percentile", "value": 95.0 },
        "data": [
          { "key": "2026-05-01T00:00:00.000+0000", "value": 6400 },
          { "key": "2026-05-02T00:00:00.000+0000", "value": 6100 }
        ]
      }
    ]
  }
}
```

UI joins the two measure arrays by `key` → `[{date, p50, p95}, ...]`.

#### 5.5.6 Chart: Token Trend (input + output overlaid)

**Reports called** (parallel): `agentic-tokens-input-trend` + `agentic-tokens-output-trend`.

**Mock responses**:
```json
// agentic-tokens-input-trend
{ "result": { "measures": [
  { "property": "inputTokens", "aggregationType": { "type": "sum" }, "data": [
    { "key": "2026-05-01T00:00:00.000+0000", "value": 85000 },
    { "key": "2026-05-02T00:00:00.000+0000", "value": 88000 }
  ]}
]}}
// agentic-tokens-output-trend
{ "result": { "measures": [
  { "property": "outputTokens", "aggregationType": { "type": "sum" }, "data": [
    { "key": "2026-05-01T00:00:00.000+0000", "value": 31000 },
    { "key": "2026-05-02T00:00:00.000+0000", "value": 33000 }
  ]}
]}}
```

UI zips by `key` → `[{date, inputTokens, outputTokens}, ...]`.

#### 5.5.7 Chart: Token Outlier Bands (P5/P50/P95 per bucket)

**Report called**: `agentic-tokens-trend` — single report with SUM + three percentile aggregations on `totalTokens`.

**Mock response**:
```json
{
  "result": {
    "measures": [
      { "property": "totalTokens", "aggregationType": { "type": "sum" },                       "data": [{ "key": "2026-05-01T00:00:00.000+0000", "value": 116000 }] },
      { "property": "totalTokens", "aggregationType": { "type": "percentile", "value": 5.0 },  "data": [{ "key": "2026-05-01T00:00:00.000+0000", "value": 420 }] },
      { "property": "totalTokens", "aggregationType": { "type": "percentile", "value": 50.0 }, "data": [{ "key": "2026-05-01T00:00:00.000+0000", "value": 1300 }] },
      { "property": "totalTokens", "aggregationType": { "type": "percentile", "value": 95.0 }, "data": [{ "key": "2026-05-01T00:00:00.000+0000", "value": 3800 }] }
    ]
  }
}
```

UI joins four measure arrays by `key` → `[{date, totalTokens, p5, p50, p95}, ...]`.

#### 5.5.8 Bar chart: Top Token Consumers by Process

**Reports called** (parallel): `agentic-tokens-by-process` + `agentic-process-instance-count-by-process`.

**Mock response — agentic-tokens-by-process**:
```json
{
  "result": {
    "measures": [
      {
        "property": "totalTokens",
        "aggregationType": { "type": "sum" },
        "data": [
          { "key": "invoice-approval",      "label": "Invoice Approval",      "value": 63000 },
          { "key": "warranty-registration", "label": "Warranty Registration", "value": 52000 },
          { "key": "onboarding-guide",      "label": "Onboarding Guide",      "value": 47000 }
        ]
      }
    ]
  }
}
```

**Mock response — agentic-process-instance-count-by-process**:
```json
{
  "result": {
    "measures": [
      { "property": "frequency", "data": [
        { "key": "invoice-approval",      "value": 320 },
        { "key": "warranty-registration", "value": 280 },
        { "key": "onboarding-guide",      "value": 240 }
      ]}
    ]
  }
}
```

UI joins by `key` (processDefinitionKey).

#### 5.5.9 Bar chart: Avg Tokens Per Call by Process

**Report called**: `agentic-avg-tokens-per-call-by-process` (custom view interpreter returning ratio).

**Mock response**:
```json
{
  "result": {
    "measures": [
      {
        "property": "avgTokensPerCall",
        "data": [
          { "key": "invoice-approval",      "value": 690.5 },
          { "key": "warranty-registration", "value": 430.2 }
        ]
      }
    ]
  }
}
```

UI renders `value: null` cells as `"—"`.

#### 5.5.10 Bar chart: Incident Rate by Version (L1 only)

**Report called**: `agentic-incident-rate-by-version`

**Mock response**:
```json
{
  "result": {
    "measures": [
      { "property": "percentage", "data": [
        { "key": "3", "value": 0.8 },
        { "key": "4", "value": 0.4 }
      ]}
    ]
  }
}
```

UI parses `key` as int. `instanceCount` per bucket comes from a twin
`agentic-process-instance-count` call grouped by `processDefinitionVersion`
(declared as a separate saved report when the bucket-level `runs` count
is needed in the UI).

#### 5.5.11 KPI line: Total Tool Calls (Phase 1)

PNG mockups show per-tool frequency bars, but Phase 1 intentionally
ships only a single total tool-calls line.

**Report called**: `agentic-tool-calls-total`

**Mock response**:
```json
{
  "result": {
    "measures": [ { "property": "toolCalls", "aggregationType": { "type": "sum" }, "data": 3030 } ]
  }
}
```

UI maps this scalar directly to the "Total tool calls" line in the
Reliability section (no per-tool breakdown in Phase 1).

### 5.6 Cross-process L0 queries (verified in code)

For L0 (fleet) views, the saved reports set `managementReport: true`
and `definitions: []` in their stored definition. The existing
`InstanceIndexUtil.getProcessInstanceIndexAliasNames()`, called by
`AbstractProcessExecutionPlanInterpreterES.getIndexNames()`, routes
management-report queries to `PROCESS_INSTANCE_MULTI_ALIAS`, which
aggregates across every process instance index. No new mechanism is
needed for L0. (Note: the `getMultiIndexAlias()` call at line 153 of
`AbstractProcessExecutionPlanInterpreterES` discards its return value
and is effectively a dead call — the actual routing is performed by
`InstanceIndexUtil.getProcessInstanceIndexAliasNames()` at line 155.)

For L1, the UI overrides `definitions` in the request body with a
single `ReportDataDefinitionDto` scoping the evaluation to one process.

### 5.7 Page-load orchestration

Single shared API client function fires all required reports in parallel:

```ts
async function loadAgenticDashboard(filterState: FilterState, level: 'L0' | 'L1') {
  const baseFilter  = buildBaseFilter(filterState);
  const priorFilter = buildPriorPeriodFilter(filterState);
  const defOverride = level === 'L1' ? buildDefinitionOverride(filterState) : undefined;

  const [
    totalRunsCurr, totalRunsPrior,
    durationCurr,  durationPrior,
    incidentRateCurr, incidentRatePrior,
    incidentCount,
    tokensSummary,
    durationTrend,
    tokensInputTrend, tokensOutputTrend,
    tokensTrend,
    tokensByProcess, processInstanceCount,
    toolCallsTotal,
    avgTokensPerCall,
    incidentRateByVersion,    // only when level === 'L1'
  ] = await Promise.all([
    evaluate(AGENTIC_REPORT_IDS.totalRuns, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.totalRuns, priorFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.durationSummary, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.durationSummary, priorFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.incidentRate, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.incidentRate, priorFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.incidentCount, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.tokensSummary, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.durationTrend, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.tokensInputTrend, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.tokensOutputTrend, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.tokensTrend, baseFilter, defOverride),
    level === 'L0' ? evaluate(AGENTIC_REPORT_IDS.tokensByProcess, baseFilter) : null,
    level === 'L0' ? evaluate(AGENTIC_REPORT_IDS.processInstanceCountByProcess, baseFilter) : null,
    evaluate(AGENTIC_REPORT_IDS.toolCallsTotal, baseFilter, defOverride),
    evaluate(AGENTIC_REPORT_IDS.avgTokensPerCallByProcess, baseFilter, defOverride),
    level === 'L1' ? evaluate(AGENTIC_REPORT_IDS.incidentRateByVersion, baseFilter, defOverride) : null,
  ]);

  return composeDashboardState({ /* …all responses… */ });
}
```

### 5.8 Process definition selector

**Endpoint**: `GET /api/process-definition?hasAgentRuns=true`

**Mock response**:
```json
[
  { "key": "invoice-approval",      "name": "Invoice Approval",      "versions": ["1","2","3"] },
  { "key": "warranty-registration", "name": "Warranty Registration", "versions": ["1","2"] },
  { "key": "onboarding-guide",      "name": "Onboarding Guide",      "versions": ["1"] }
]
```

### 5.9 Mock server during frontend development

Each report ID maps to a fixed JSON file:

```
optimize/client/mocks/agentic/
  agentic-total-runs.json
  agentic-total-runs.prior.json
  agentic-duration-summary.json
  agentic-duration-summary.prior.json
  agentic-incident-rate.json
  agentic-incident-rate.prior.json
  agentic-incident-count.json
  agentic-tokens-summary.json
  agentic-duration-trend.json
  agentic-tokens-input-trend.json
  agentic-tokens-output-trend.json
  agentic-tokens-trend.json
  agentic-tokens-by-process.json
  agentic-process-instance-count-by-process.json
  agentic-tool-calls-total.json
  agentic-avg-tokens-per-call-by-process.json
  agentic-incident-rate-by-version.json
```

The mock server intercepts `POST /api/report/{id}/evaluate` and serves
the corresponding file. For period-delta calls, the mock inspects the
date range in the request body and serves either the `*.json` (current)
or `*.prior.json` (prior) fixture.

**This is sufficient for the frontend team to build the entire dashboard
without any backend dependency.**

---

## 6. Layer 3 — Frontend (Custom UI)

### 6.1 Components

Mostly aligned with V1 spec §5, with one Phase 1 simplification (no
per-tool breakdown). Bespoke React components:
- KPI cards with delta badges
- Token trend chart, token outlier bands chart
- Duration P50/P95 KPI cards + stability chart
- Top processes bar chart
- Total tool calls line, avg tokens per call bar chart, incident rate by version bar chart
- Dashboard layout matching the PNG designs
- L0/L1 toggle navigation

### 6.2 Filter state

Same as V1 §5.1 — shared filter context drives all components, re-fetch
on filter change.

| State field | Type | Default | Description |
|---|---|---|---|
| `processDefinitionKey` | `string \| null` | `null` | `null` = L0; non-null = L1 |
| `dateRange.from` | `string` (ISO 8601) | Last 30 days | Start of date range |
| `dateRange.to` | `string` (ISO 8601) | Now | End of date range |

### 6.3 API client

The API client is a thin wrapper around `POST /api/report/{id}/evaluate`. It:

- Imports the `AGENTIC_REPORT_IDS` TypeScript constants (§5.2)
- Accepts the current filter state and the target level (L0 / L1)
- For each report ID, calls `POST /api/report/{id}/evaluate` passing
  date-range filter + (for L1) `definitions` override in the request body
- Fires evaluations in parallel via `Promise.all`
- Parses the `AuthorizedReportEvaluationResponseDto` into typed shapes
  the React components consume
- Provides composite functions (`fetchSummary()`, `fetchTrends()`,
  `fetchCharts()`) that internally orchestrate multiple evaluations
  and assemble the response

**The API client is the only file that needs to know about the report
data model.** Components remain decoupled from the report engine.

### 6.4 Process selector

Same as V1 §5.5 — uses the extended
`GET /api/process-definition?hasAgentRuns=true` endpoint.

### 6.5 Display rules

Same as V1 §5.4. All formatting rules apply unchanged.

### 6.6 Page-load request pattern

| Level | Inline evaluations fired (parallel) |
|---|---|
| L0 | summary block (~8 calls with delta doubles) + trend reports (~4 calls) + chart reports (~4 calls). Total ~16 calls. |
| L1 | summary block (~8 calls) + trend reports (~4 calls) + chart reports (~3 calls). Total ~15 calls. |

All parallelized via `Promise.all`. Browser HTTP/2 multiplexing handles
this. Each underlying ES query is filter-cache friendly.

---

## 7. Cross-cutting Concerns

| Concern | Handling |
|---|---|
| Multi-tenancy | Inherited from `ReportEvaluationHandler` — tenants resolved from session |
| Authorization | Standard report evaluation authorization (role-based) |
| Date range semantics | Filter override on each report evaluation |
| Period comparison | Computed in UI (§4.6) |
| Recency gap | Same as V1 — only COMPLETED instances are indexed |
| Alert support | ✅ required via collection-backed system publication and stable report IDs (§4.8) |
| Discoverability | ✅ required via collection-scoped report/dashboard listings (§4.9) |
| Operator customization | **Intentionally locked** — template + management/instant-preview guardrails prevent edit/delete/copy. |

---

## 8. Task Breakdown

### Layer 1 — Import Pipeline

#### Task 1 — Bump `ProcessInstanceIndex`, add field definitions

**Story Points:**
- Alexandre: 2
- Sherrin: 2
- Helene: 2

| Sub-task | Notes |
|---|---|
| 1.1 Create `AgentInstanceDto` | New nested DTO following `FlowNodeInstanceDto`. Fields: `agentInstanceId`, `flowNodeId`, `processDefinitionVersion` (String/keyword), `startDate`, `endDate`, `totalDurationInMs`, `startDateEpochMs`, `metrics.toolCalls`, `metrics.inputTokens`, `metrics.outputTokens`, `metrics.modelCalls`, `tools[].name`. |
| 1.2 Add field constants to `ProcessInstanceIndex` | Parent-level constants: `agentTotalInputTokens`, `agentTotalOutputTokens`, `agentTotalModelCalls`, `agentTotalToolCalls`, **`agentTotalTokens` (new for vReuse — sum of input + output)**. Plus nested constants for all `AgentInstanceDto` fields. |
| 1.3 Add mappings in `ProcessInstanceIndexES` and `ProcessInstanceIndexOS`, bump VERSION 8 → 9 | Two implementations required. Nested blocks must use `.nested()` not `.object()`, the wrong type silently breaks all aggregations. Set default 0 for all parent-level `agentTotal*` longs so aggregations return 0 instead of null on documents without agent data. No migration class needed (Optimize re-imports from Zeebe). |
| 1.4 Unit test verifying `agentInstances` is mapped as nested (not object) | |

#### Task 2 — Import service for `AgentInstanceRecord`

**Story Points:**
- Alexandre: 5
- Sherrin: 5
- Helene: 5

| Sub-task | Notes |
|---|---|
| 2.1 `ZeebeAgentInstanceImportIndexHandler` | Extend `PositionBasedImportIndexHandler`, declare `ZEEBE_AGENT_INSTANCE_IMPORT_INDEX_DOC_ID`. ~30 lines; follows `ZeebeIncidentImportIndexHandler` exactly. |
| 2.2 `ZeebeAgentInstanceImportMediator` + `ZeebeAgentInstanceImportMediatorFactory` | Wire handler + import service + position tracking. Follow `ZeebeIncidentImportMediator` pattern. |
| 2.3 `ZeebeAgentInstanceImportService` — core mapping logic | Filter for CREATED/COMPLETED intents only. Sort events by timestamp before processing (prevents negative `totalDurationInMs`). Map Zeebe record fields to `AgentInstanceDto`. Delegate to `ProcessInstanceWriter`. Highest complexity in this layer. |
| 2.4 Register handler in `ZeebeImportIndexHandlerProvider` and factory in mediator registry | |
| 2.5 Integration test: CREATED → COMPLETED normal sequence + out-of-order batch | |

#### Task 3 — Painless upsert script

**Story Points:**
- Alexandre: 5
- Sherrin: 5
- Helene: 5

| Sub-task | Notes |
|---|---|
| 3.1 `createUpdateAgentInstancesScript()` — upsert nested entry by `agentInstanceId`, set `endDate` and compute `totalDurationInMs` on COMPLETED | Upsert pattern: lookup by id in stream, insert if absent, update `endDate` + compute duration using `startDateEpochMs` param (Painless cannot do date arithmetic natively). Follow `createUpdateFlowNodeInstancesScript()`. Guard: `if (existingInstance.agentInstances == null) { existingInstance.agentInstances = new ArrayList(); }` before the upsert loop. |
| 3.2 Parent-level re-aggregation block — sum across `agentInstances` into 5 `agentTotal*` fields incl. new `agentTotalTokens` | Separate concern and separate risk from the upsert block. Runs after upsert. `agentTotalTokens = sum(metrics.inputTokens) + sum(metrics.outputTokens)` per process instance. Guard all five fields: reset to 0 before summing — they may be absent on documents written before this feature, and summing into a missing field throws in Painless. |
| 3.3 Wire `createUpdateAgentInstancesScript()` into `createProcessInstanceUpdateScript()` | One-liner addition. |
| 3.4 Validate full script against a live ES instance | No compile-time error detection. Must cover CREATED-only, COMPLETED-only, and CREATED+COMPLETED cases, plus a document with no `agentInstances` field (backward compatible). |
| 3.5 Validate full script against a live OS instance | Separate ticket — OS has different Painless behaviour. Date fields return `ZonedDateTime`, not Joda; use `.toInstant().toEpochMilli()` instead of `.getMillis()`. |

---

### Layer 2 — Shared Infrastructure

#### Task 4 — `HasAgentInstancesFilter` end-to-end

**Story Points:**
- Alexandre: 3
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 4.1 `HasAgentInstancesFilterDataDto` (empty marker) + Jackson `@JsonSubTypes` entry on `ProcessFilterDto` | Filter polymorphism — adding a new subtype requires updating the parent's `@JsonSubTypes` array. |
| 4.2 `HasAgentInstancesQueryFilterES implements QueryFilterES<HasAgentInstancesFilterDataDto>` | Build nested-exists bool query on `agentInstances.agentInstanceId`. ~30 lines, follows existing filter pattern. |
| 4.3 `HasAgentInstancesQueryFilterOS implements QueryFilterOS<HasAgentInstancesFilterDataDto>` | OpenSearch equivalent. |
| 4.4 Register in `ProcessQueryFilterEnhancerES` + `OS` constructors and `addFilters()` chain | Same pattern as the 24 existing filters. Easy to forget the OS side. |
| 4.5 Integration test: filter applied via standard `POST /api/report/evaluate` | Verify the filter scopes results to instances with at least one agent. |

#### Task 5 — View entity + view properties + view interpreters

**Story Points:**
- Alexandre: 5
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 5.1 Add `ProcessViewEntity.AGENT_INSTANCE` | One enum value in `optimize-commons`. |
| 5.2 Add `ViewProperty` constants `INPUT_TOKENS`, `OUTPUT_TOKENS`, `MODEL_CALLS`, `TOOL_CALLS`, `TOTAL_TOKENS` | `ViewProperty` is a class (not enum) — additive change, no breaking. |
| 5.3 Add `ProcessView` enum entries — one per (AGENT_INSTANCE, viewProperty) combination | ~5 entries. |
| 5.4 `ProcessViewAgent{Input,Output,Total}TokensInterpreterES/OS` (6 classes) | Extend `AbstractProcessViewMultiAggregationInterpreterES/OS`. Each reads a different parent denormalized field (`agentTotalInputTokens`, `agentTotalOutputTokens`, `agentTotalTokens`). |
| 5.5 `ProcessViewAgent{Model,Tool}CallsInterpreterES/OS` (4 classes) | Same pattern, reads `agentTotalModelCalls` / `agentTotalToolCalls`. |
| 5.6 Register all new interpreters in `ProcessViewInterpreterFacadeES/OS` | One line per interpreter per facade. |
| 5.7 Integration test: evaluate each view via `POST /api/report/evaluate` with AVG/SUM/PERCENTILE | Verify ES + OS produce matching results. |

#### Task 6 — Custom `AvgTokensPerCall` view interpreter (ratio of sums)

**Story Points:**
- Alexandre: 3
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 6.1 New `ViewProperty.AVG_TOKENS_PER_CALL` + `ProcessView` enum entry | |
| 6.2 `ProcessViewAgentAvgTokensPerCallInterpreterES` | Follow `ProcessViewInstancePercentageInterpreterES` pattern. Issues two sub-aggs (`sum agentTotalTokens`, `sum agentTotalModelCalls`), computes ratio in `retrieveResult()`. Null when denominator is 0. |
| 6.3 `ProcessViewAgentAvgTokensPerCallInterpreterOS` | OpenSearch equivalent. |
| 6.4 Register in view facades | |
| 6.5 Integration test | Confirm null behavior on zero model calls and correct ratio on real data. |

#### Task 7 — New groupBys: `PROCESS_DEFINITION_KEY` and `PROCESS_DEFINITION_VERSION`

**Story Points:**
- Alexandre: 5
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 7.1 `ProcessDefinitionKeyGroupByDto` + Jackson subtype | Filter polymorphism — `ProcessGroupByDto.@JsonSubTypes`. |
| 7.2 `ProcessDefinitionVersionGroupByDto` + Jackson subtype | Same. |
| 7.3 Add `PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY` + `_VERSION` to `ProcessGroupBy` enum | Two entries. |
| 7.4 `ProcessGroupByProcessDefinitionKeyInterpreterES/OS` | Terms agg on `processDefinitionKey` parent field. |
| 7.5 `ProcessGroupByProcessDefinitionVersionInterpreterES/OS` | Terms agg on `processDefinitionVersion` parent field. |
| 7.6 Register in `ProcessGroupByInterpreterFacadeES/OS` | |
| 7.7 Integration test: groupBy with all standard views + visualisations | Verify multi-bucket responses and sort orders. |

#### Task 7.5 — Extend `AdditionalProcessReportEvaluationFilterDto` with `definitions`

**Story Points:**
- Alexandre: 1
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 7.5.1 Add `definitions` field to `AdditionalProcessReportEvaluationFilterDto` | `List<ReportDataDefinitionDto> definitions = new ArrayList<>()`. Additive, non-breaking. |
| 7.5.2 Thread `definitions` through `ReportEvaluationService.evaluateSavedReportWithAdditionalFilters(...)` into the execution context | When non-empty, the `definitions` override replaces the saved report's stored definition list before evaluation. |
| 7.5.3 Unit test | Verify L1 call with `definitions` override scopes query to the correct `processDefinitionKey`. |

#### Task 8 — Extend `GET /api/process-definition` with `hasAgentRuns=true`

**Story Points:**
- Alexandre: 3
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 8.1 Add `hasAgentRuns` param to `DefinitionRestService.getDefinitions()` | New optional `@RequestParam(required = false) Boolean hasAgentRuns`. Existing behaviour unchanged when null or false. |
| 8.2 `getProcessDefinitionsWithAgentRuns(...)` on `ProcessDefinitionReader` | Reuse `HasAgentInstancesQueryFilter` from Task 4 — single source of truth for the predicate. |
| 8.3 `ProcessDefinitionReaderES/OS` implementations | Terms agg on `processDefinitionKey` scoped by the new filter. Collect bucket keys and filter the definition list. |
| 8.4 Integration test | `hasAgentRuns=true` returns only processes with agent data; `hasAgentRuns=false` returns all; param absent = same as false. |

#### Task 9 — Regression sweep across existing reports

**Story Points:**
- Alexandre: 3
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 9.1 Run full existing report IT suite against the modified shared infra | Smoke-detect any regressions on non-agentic reports caused by new `ProcessView` / `ProcessGroupBy` enum entries, filter chain changes, view facade changes. |
| 9.2 Performance regression check on hot existing reports | Measure latency of top user reports before/after. Investigate if any regress >10%. |

---

### Layer 2 — Hard-coded Report Templates

#### Task 10 — Author report-definition `template4.json`

**Story Points:**
- Alexandre: 8
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 10.1 Author ~14 saved-report definitions with deterministic IDs matching `AGENTIC_REPORT_IDS` constants | One `SingleProcessReportDefinitionExportDto` per ID listed in §5.2. Each carries view, groupBy, aggregations, baseline filter set, visualization. L0 reports set `managementReport: true` and `definitions: []` (see §5.6). |
| 10.2 Optional: companion dashboard composition arranging the reports in a standard Optimize dashboard grid | Recommended when dashboard-surface discoverability is required in the system collection. |
| 10.3 Wire `template4.json` into `InstantPreviewDashboardService` startup + checksum mechanism | Template must follow the incremental naming convention. Add a new `@EventListener(ApplicationReadyEvent)` startup method that eagerly creates/reconciles the agentic reports with deterministic IDs. Extend import/write path to preserve template `id` fields rather than generating new UUIDs. |
| 10.4 Apply read-only/system-owned guardrails to template-managed reports | Reuse existing instant-preview pattern. Direct edit/delete/copy operations must be rejected for template-managed reports. |
| 10.5 Lint check ensuring TypeScript `AGENTIC_REPORT_IDS` constants match template JSON IDs | CI step prevents drift between UI constants and template entity IDs. |
| 10.6 Validate evaluation responses on real ES + OS data | Hit each saved report via `POST /api/report/{id}/evaluate`. Confirm response shape matches §5.4. |
| 10.7 Publish into dedicated system collection (not private import) | Create/ensure stable collection ID, import reports/dashboards into that collection, and configure operator roles for visibility/alert usage. |
| 10.8 Reconcile template updates in-place (no delete+recreate for existing report IDs) | Required for alert continuity. Report deletion currently triggers alert cleanup; update existing report entities by ID instead of destructive replacement. |

#### Task 11 — Alertability and discoverability enablement (collection-backed publication)

**Story Points:**
- Alexandre: 8
- Sherrin: —
- Helene: —

| Sub-task | Notes |
|---|---|
| 11.1 System collection bootstrap service | Ensure collection exists with stable ID and `automaticallyCreated=true`; idempotent startup behavior. |
| 11.2 Collection role synchronization for operator identities | Add configuration-backed role mapping (viewer/editor) for users/groups; synchronize at startup. |
| 11.3 Alert permission policy alignment | Decide and implement: keep existing EDITOR requirement, or allow VIEWER for immutable system reports. |
| 11.4 Alert continuity integration test | Verify configured alerts survive template content changes when report IDs remain stable and entities are reconciled in place. |
| 11.5 Discoverability integration test | Verify published reports/dashboards are visible via collection endpoints for authorized operators. |

---

### Layer 3 — Frontend

| Task | Story Points | Notes |
|---|---|---|
| API client wrapping `POST /api/report/{id}/evaluate` with filter override application + typed responses + period-delta orchestration | SR: 5 / Alexandre: 5 | Implements §5.3 / §5.4 / §5.7. Builds `EvaluateReportRequest` from filter state + delta period offset. Parses `Measure[]` into typed component contracts. |
| Mock fixtures (`optimize/client/mocks/agentic/*.json`) + mock server interception of `POST /api/report/{id}/evaluate` | SR: 2 / Alexandre: 3 | One `.json` per saved report from §5.2 + `.prior.json` twins for delta-bearing reports. Mock-server file layout per §5.9. Enables full frontend development against mocks before backend is ready. |
| Composite functions (summary multi-report assembly, period delta computation, derived metric calculation) | SR: 3 / Alexandre: 3 | `loadAgenticDashboard()` orchestrator per §5.7. Computes `delta = current - prior` for delta-bearing KPIs. |
| Filter context, date range picker, process selector dropdown | SR: 5 / Alexandre: 5 | Shared state driving all components. Requires `GET /api/process-definition?hasAgentRuns=true` extension (Task 8). |
| KPI cards: Total Runs, Avg Duration, Incident Rate (with period delta badges) | SR: 3 / Alexandre: 3 | Composes from §5.5.1, §5.5.2, §5.5.3. |
| Token stats: Avg Tokens Per Run, Median Tokens Per Run | SR: 1 / Alexandre: 2 | Composes from §5.5.4. Single report (`agentic-tokens-summary`) — two measures. |
| Duration stats: P50/P95 KPI cards + duration stability trend chart | SR: 5 / Alexandre: 3 | Scalars from §5.5.2; trend chart from §5.5.5. |
| Token trend chart (input + output lines) + token outlier bands | SR: 3 / Alexandre: 3 | Two reports overlaid (§5.5.6) for trend lines; one report (§5.5.7) for outlier bands. Three components total. |
| Top token consumers by process bar chart | SR: 3 / Alexandre: 3 | Composes from §5.5.8. L0 only. Includes secondary name resolution from `GET /api/process-definition` joined by `processDefinitionKey` client-side. |
| Total tool calls line (no tool breakdown) | SR: 1 / Alexandre: 3 | Composes from §5.5.11. L0 + L1. Phase 1 shows a single total value only. |
| Avg tokens per call by process bar chart | SR: 2 / Alexandre: 2 | Composes from §5.5.9. Render null cells as `"—"`. |
| Incident rate by version bar chart | SR: 2 / Alexandre: 2 | Composes from §5.5.10. L1 only. |
| Dashboard layout, routing, and level switching (L0 ↔ L1) | SR: 5 / Alexandre: 5 | Compose all components. Handle empty states per chart. L1 = `processDefinitionKey` set in filter context. |
| Translation keys — `en.json` + `de.json` | SR: 2 / Alexandre: 2 | Add `agentControlPlane` namespace. |
| Component + unit tests (Jest) | SR: 5 / Alexandre: 3 | API client (all measure shapes), period-delta composition, `DeltaBadge` formatting variants, display rules (`incidentRate` as %, null guards), each chart component render test with mock props + empty state. |
| E2E UI tests | SR: 5 / Alexandre: 5 | 5 user flows: L0 full load; L0→L1 switch; date range change re-fetch; empty state per chart; process selector `hasAgentRuns=true` scoping. |

---

### Integration and Validation

| Task | Story Points | Notes |
|---|---|---|
| End-to-end smoke test with real Zeebe agent instance data | Alexandre: 5 / Helene: 6 | Validates full pipeline: Zeebe record → import → index → saved-report evaluation → dashboard. Includes a write-then-evaluate against ES + OS. |
| Latency benchmark — dashboard load time with ~15–16 parallel evaluations | Alexandre: 3 / Helene: 5 | Measure dashboard load p50 / p95 against representative dataset. If outside acceptable threshold, investigate caching or reduce parallel call count. |
| Performance validation of new view interpreters under load | Alexandre: 5 / Helene: 6 | Focus on `agentic-tokens-trend` (single report with SUM + 3 percentiles per bucket) and `agentic-tokens-by-process` (terms agg with multiple sub-aggs). |
| Alert configuration walkthrough | Alexandre: 2 / Helene: 3 | Verify operator can create and receive alerts for published agentic reports. |
| UX review iteration against PNG designs | SR: 3 / Alexandre: 3 | Pixel-level review of custom dashboard. |

---

## 9. Comparison vs Variant 1

| Dimension | Variant 1 (dedicated endpoints) | **Variant vReuse (no endpoints, hard-coded reports)** |
|---|---|---|
| New REST endpoints | 4 | **0** |
| New UI components / pages | ~10 | **~10 (same custom UI)** |
| Shared infra changes | None | **Yes — new filter, view interpreters, groupBys, and collection-backed publication plumbing** |
| Backend code path | Parallel custom `AgenticControlPlaneRepositoryES/OS` | **One shared path — every query goes through `ReportEvaluationHandler`** |
| Period delta badges | ✅ backend executor | ✅ client-side compute |
| Bespoke layout matching PNGs | ✅ | ✅ |
| L0/L1 toggle as bespoke chrome | ✅ | ✅ |
| `/summary`-equivalent latency | 1 ES query × 2 periods (parallel) = 2 ES queries | ~8 parallel report evaluations (~8 ES queries) |
| HTTP round-trips per page load | 4 | ~15–16 |
| **Alert support on agentic KPIs** | ❌ | ✅ via collection-backed publication and stable-ID reconciliation |
| **Discoverability in Reports / Dashboards list** | ❌ | ✅ via collection-scoped report/dashboard listings |
| Operator customization of dashboard reports | ❌ (no entities) | ❌ direct edit/copy blocked |
| Risk of regression in existing reports | None | Medium (shared infra changes) |
| Code delta | ~2,000+ LOC parallel backend + frontend | ~1,900 LOC frontend + shared infra + template + collection publication |
| **Total story points** (Alex / Sherrin / Helene / combined) | **85 / 78 / 52 / 215** | **121 / 67 / 32 / 220** |
| Maintenance burden | Two parallel paths to evolve | One shared path |

### Why choose vReuse over V1

- **No new public REST endpoints** (API governance / API freeze
  compliance)
- **Single shared backend code path** — agentic queries reuse the same
  report engine, filter chain, authorization, and tenant resolution as
  every other Optimize report. No `AgenticControlPlaneRepository` to
  maintain in parallel.
- **Alerting + discoverability can be delivered without new query endpoints** —
  by publishing system-managed reports/dashboards into a dedicated
  collection and reusing existing alert + collection listing flows.
- **Future flexibility ratchets up** — if the product team later
  decides to allow operator customization or expose agentic view
  properties in the report builder, the building blocks already exist.
  Enabling edits/copies would still require an explicit policy change in
  report mutability rules.

### Why vReuse is harder than V1

- **More HTTP round-trips per page load** (~15–16 vs 4); browser handles
  it but operators on slow networks may notice
- **More ES queries for the summary block** (~8 vs 1); each pays the
  standard report-pipeline overhead (authorization, filter
  normalization, query planning)
- **Frontend complexity higher** — composition logic and period delta
  math live in the UI
- **Shared infrastructure changes carry regression risk** for every
  other Optimize report
- **Extra story-point investment** vs V1 in Layer 2 (shared infra +
  template authoring + collection publication/reconciliation); see §8 for per-task breakdown

### Hard requirements that rule out vReuse

- **Single-query `/summary` for sub-100ms p95 latency** — vReuse's ~8
  parallel evaluations will likely sit at 150–400ms; if the latency
  budget is tighter, V1's bespoke single-query design is the only
  option

---

## 10. Open Questions

1. **`avgTokensPerCall` strategy**: ship the custom view interpreter
   (Task 6), or compose client-side from two parallel reports
   (`agentic-tokens-by-process` + `agentic-model-calls-by-process`)?
2. **Latency budget**: validate ~15–16 parallel evaluations complete within
   the operator's acceptable dashboard-load latency (typically <2s
   p95). If not acceptable, fall back to V1.
3. **Alert permission model**: keep existing EDITOR requirement for
   alert creation, or allow VIEWER for immutable system-managed reports?
4. **Permission flag mechanism**: confirm Optimize's existing
   instant-preview permission lock pattern can be applied to the
   agentic template, or specify the new mechanism if not.
5. **ID stability across releases**: the import service must be extended
   to preserve `id` fields from the template JSON (see §4.3). Once
   implemented, verify deterministic-ID continuity across template
   checksum changes with in-place reconciliation (no destructive
   delete/recreate for existing IDs).

---

## 11. Final Verification Statement

Every Phase 1 metric from `agentic-control-plane-spec-high-level.md` §4
is delivered by Variant vReuse:

- All `/summary` KPIs (including period deltas) — composed client-side
  from `agentic-total-runs`, `agentic-duration-summary`,
  `agentic-incident-rate`, `agentic-incident-count`,
  `agentic-tokens-summary` saved reports
- All `/process-breakdown` data — from `agentic-tokens-by-process` +
  `agentic-process-instance-count-by-process`
- All `/trends` data — from `agentic-duration-trend`,
  `agentic-tokens-trend`, `agentic-tokens-input-trend`,
  `agentic-tokens-output-trend`
- All `/charts` data — from `agentic-tool-calls-total`,
  `agentic-avg-tokens-per-call-by-process`,
  `agentic-incident-rate-by-version`
- Tooling panel is intentionally simplified in Phase 1: total tool
  calls only (no per-tool frequency breakdown).

No metric is dropped. Period delta badges are preserved (computed in UI
from twin evaluations). The frontend contract (KPI shapes, chart data
structures) stays aligned with V1 except the intentional Phase 1
tooling simplification above.

The reports themselves are persisted saved-report entities locked by
the template source-of-truth + mutability guardrails. Operators **cannot
modify or delete them**. In this variant, they are published into a
dedicated system collection with deterministic IDs, making them
alertable and discoverable through collection-scoped listings while
remaining system-managed.
