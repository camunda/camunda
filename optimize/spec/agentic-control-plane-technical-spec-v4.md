# Agentic Control Plane — Technical Specification (v4)

**Module**: `optimize/`
**Status**: Implementation-ready
**Supersedes**: `agentic-control-plane-technical-spec-v3.md`

---

## Table of Contents

1. [What Changed from v3](#1-what-changed-from-v3)
2. [Architecture Overview](#2-architecture-overview)
3. [Contracts and Interfaces](#3-contracts-and-interfaces)
   - 3.1 [Backend — Java Interfaces](#31-backend--java-interfaces)
   - 3.2 [Frontend — TypeScript Response Types](#32-frontend--typescript-response-types)
4. [Shared Query Patterns](#4-shared-query-patterns)
5. [Endpoint Specifications](#5-endpoint-specifications)
   - 5.1 [GET /summary](#51-get-summary-l0l1)
   - 5.2 [GET /process-breakdown](#52-get-process-breakdown-l0)
   - 5.3 [GET /trends](#53-get-trends-l0l1)
   - 5.4 [GET /charts](#54-get-charts-l0l1)
   - 5.5 [Phase 2 Endpoints](#55-phase-2-endpoints)
6. [Layer 1 — Import Pipeline](#6-layer-1--import-pipeline)
7. [Layer 3 — Frontend](#7-layer-3--frontend)
8. [Parallel Development Tracks](#8-parallel-development-tracks)
9. [Migration](#9-migration)

---

## 1. What Changed from v3

|                   Change                    |                                                                             Reason                                                                             |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **7 endpoints → 4**                         | A5 (duration) + A4 (token trend) + A8 (token bands) merged into `/trends` — one date histogram, all sub-aggs. A9 + A10 + tool frequency merged into `/charts`. |
| **Tool call frequency included in Phase 1** | Design confirms it at both L0 and L1. Computed as terms agg on `agentInstances.tools.name`.                                                                    |
| **Only 3 delta fields**                     | Design shows period delta badges on: totalRuns, avgDuration, incidentRate only. Token averages and P50/P95 have no delta.                                      |
| **No "Total Tool Calls" KPI card**          | Tool calls are visualized in the frequency bar chart. Removed from `/summary`.                                                                                 |
| **Duration P50/P95 moved into `/summary`**  | They appear as scalar KPIs in the Duration section, not derived from trend data. One request serves all scalars.                                               |
| **`/l1-charts` renamed to `/charts`**       | Tool frequency is present at L0 too. Endpoint is L0/L1; some fields are null when no processDefinitionKey is provided.                                         |
| **"Configure in settings" link removed**    | Settings page is dropped from Phase 1. Incident Rate KPI card has no action link.                                                                              |

---

## 2. Architecture Overview

```
Zeebe (CREATED + COMPLETED AgentInstanceRecord events)
        │
        ▼
ZeebeAgentInstanceImportService            ← Track A
        │
        ▼
ProcessInstanceIndex (nested agentInstances)
agentTotalInputTokens / agentTotalOutputTokens (parent, re-aggregated by Painless)
        │
        ├── GET /summary           scalar KPIs, one query + period comparison parallel
        ├── GET /process-breakdown L0 ranked bar chart
        ├── GET /trends            one date_histogram, all trend charts
        └── GET /charts            tool frequency + avgTokensPerCall (per process) + (L1) incidentRateByVersion
```

**Phase 1**: L0 (fleet) + L1 (single process). L2 (agent element) = Phase 2.

**Page-load requests (all parallel):**
- L0: `/summary`, `/process-breakdown`, `/trends`, `/charts`
- L1: `/summary`, `/trends`, `/charts`

### Query grouping rationale

Each endpoint maps to one ES round trip. Sub-aggs within each endpoint share the same shard scan and filtered doc set — splitting them would double IO without reducing per-agg cost.

|       Endpoint       |                                                                                                           Why kept together                                                                                                            |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `/summary`           | All parent-level scalars. `has_incidents` uses nested `filter` (bitset, cheap). One scan serves all KPI cards.                                                                                                                         |
| `/process-breakdown` | Top-level `terms` only. No nested traversal. All sub-aggs are cheap.                                                                                                                                                                   |
| `/trends`            | Token sum lines and percentile bands appear in the **same chart area** — bands overlay the lines. Splitting would scan the same docs twice while the UI cannot render one without the other.                                           |
| `/charts`            | `by_tool` (nested) and `by_process`/`by_version` (top-level) are independent aggs sharing one shard scan. `avgTokensPerCall` moved from nested `by_element` to top-level `by_process` — simpler, no nested traversal, available at L0. |

> **Phase 1 note for `/charts` `toolFrequency`**: per-tool breakdown requires a history index
> not available in Phase 1. Phase 1 returns a single `{ toolName: "all_tools", totalToolCalls: N }`
> entry aggregated from `sum(agentInstances.metrics.toolCalls)`. Per-tool view is Phase 2.

---

## 3. Contracts and Interfaces

### 3.1 Backend — Java Interfaces

#### `AgentQueryParams`

```java
/**
 * Immutable query parameters for all Agentic Control Plane endpoints.
 * Constructed by AgenticControlPlaneController from request params + JWT-resolved tenantIds.
 * Never expose tenantIds as a client-facing request parameter.
 */
public record AgentQueryParams(
    List<String> tenantIds,        // resolved from JWT; never from client
    String processDefinitionKey,   // null = L0; non-null = L1
    String agentElementId,         // null = L1; non-null = L2 (Phase 2 only)
    Instant startDateFrom,
    Instant startDateTo
) {
    /** Returns a copy with the date range shifted back by the same duration as the selected range for period comparison computation. */
    public AgentQueryParams forPreviousPeriodWindow() {
        return new AgentQueryParams(tenantIds, processDefinitionKey, agentElementId,
            startDateFrom.minus(7, ChronoUnit.DAYS),
            startDateTo.minus(7, ChronoUnit.DAYS));
    }
}
```

#### `AgenticControlPlaneRepository`

```java
/**
 * ES/OS query layer. One method per endpoint. No period comparison logic — returns raw results only.
 * Every method calls AgentBaselineFilterBuilder.build(params) for its root query.
 */
public interface AgenticControlPlaneRepository {

    /** Scalar KPIs: runs, duration percentiles, token averages, incident rate. L0/L1. */
    SummaryResult getSummary(AgentQueryParams params);

    /** Top processes ranked by total tokens consumed. L0 only. */
    ProcessBreakdownResult getProcessBreakdown(AgentQueryParams params);

    /** All time-series trend data: token lines, token bands, duration bands. L0/L1. */
    TrendsResult getTrends(AgentQueryParams params);

    /**
     * Bar chart data. Always: toolFrequency, avgTokensPerCall (per process).
     * L1 only (when processDefinitionKey != null): incidentRateByVersion.
     */
    ChartsResult getCharts(AgentQueryParams params);

    // ── Phase 2 ──────────────────────────────────────────────────────────────

    /** Distinct agent element IDs for a process. Phase 2. */
    AgentElementsResult getAgentElements(AgentQueryParams params);

    /** Paginated agent instance rows with incident join. L2. Phase 2. */
    AgentInstancesResult getAgentInstances(AgentQueryParams params, String afterCursor);
}
```

#### `AgenticControlPlaneService`

```java
/**
 * Business logic layer: period delta, authorization, mapping from raw results to response DTOs.
 */
public interface AgenticControlPlaneService {
    SummaryResponse        getSummary(AgentQueryParams params);        // runs period comparison
    ProcessBreakdownResponse getProcessBreakdown(AgentQueryParams params); // no delta
    TrendsResponse         getTrends(AgentQueryParams params);         // no delta
    ChartsResponse         getCharts(AgentQueryParams params);         // no delta
}
```

#### `AgenticControlPlaneController`

```java
@RestController
@RequestMapping("/api/agentic-control-plane")
public interface AgenticControlPlaneController {

    @GetMapping("/summary")
    SummaryResponse getSummary(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo);

    @GetMapping("/process-breakdown")
    ProcessBreakdownResponse getProcessBreakdown(
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo);

    @GetMapping("/trends")
    TrendsResponse getTrends(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo);

    @GetMapping("/charts")
    ChartsResponse getCharts(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo);
}
```

#### Response Records (Java)

```java
// /summary
public record SummaryResponse(
    long   totalRuns,         Long   totalRunsDelta,
    long   avgDurationMs,     Long   avgDurationMsDelta,
    double incidentRate,      Double incidentRateDelta,
    long   incidentCount,
    long   activationCount,
    long   avgTokensPerRun,   // (inputTokens + outputTokens) / totalRuns — no delta
    long   medianTokensPerRun,// p50 of per-run total tokens — no delta
    long   p50DurationMs,     // no delta
    long   p95DurationMs      // no delta
) {}

// /process-breakdown
public record ProcessBreakdownResponse(List<ProcessItem> processes) {}
public record ProcessItem(
    String processDefinitionKey,
    long   totalInputTokens,
    long   totalOutputTokens,
    long   processInstanceCount) {}

// /trends
public record TrendsResponse(String interval, List<TrendPoint> trend) {}
public record TrendPoint(
    String date,
    long   inputTokens,   long outputTokens,   // Token trend lines
    long   tokenP5,       long tokenP50,        long tokenP95,  // Token outlier bands
    long   durationP50Ms, long durationP95Ms    // Duration stability
) {}

// /charts
public record ChartsResponse(
    List<ToolFrequencyItem>    toolFrequency,         // always populated (L0 + L1)
    List<AvgTokensItem>        avgTokensPerCall,      // always populated (L0 + L1)
    List<VersionIncidentItem>  incidentRateByVersion  // null at L0
) {}
public record ToolFrequencyItem(String toolName, long totalToolCalls) {}
public record AvgTokensItem(
    String processDefinitionKey,
    Double avgTokensPerCall,  // null when modelCalls = 0; frontend renders "—"
    long   totalModelCalls) {}
public record VersionIncidentItem(int version, double incidentRate, long runs) {}
```

---

### 3.2 Frontend — TypeScript Response Types

**File**: `optimize/client/src/components/AgenticControlPlane/api/types.ts`

```typescript
export interface AgentQueryParams {
  processDefinitionKey?: string;
  startDateFrom: string;  // ISO 8601
  startDateTo:   string;
}

// GET /summary
export interface SummaryResponse {
  totalRuns: number;           totalRunsDelta: number | null;
  avgDurationMs: number;       avgDurationMsDelta: number | null;
  incidentRate: number;        incidentRateDelta: number | null;   // [0,1]; format as %
  incidentCount: number;
  activationCount: number;     // shown in Incident Rate tooltip as denominator
  avgTokensPerRun: number;     // no delta
  medianTokensPerRun: number;  // no delta
  p50DurationMs: number;       // no delta
  p95DurationMs: number;       // no delta
}

// GET /process-breakdown
export interface ProcessBreakdownResponse {
  processes: {
    processDefinitionKey: string;
    totalInputTokens: number;
    totalOutputTokens: number;
    processInstanceCount: number;
  }[];
}

// GET /trends
export type TrendInterval = '1h' | '1d' | '1w' | '1M';
export interface TrendsResponse {
  interval: TrendInterval;
  trend: {
    date: string;
    inputTokens: number;   outputTokens: number;  // Token trend chart
    tokenP5: number;       tokenP50: number;       tokenP95: number; // Outlier bands
    durationP50Ms: number; durationP95Ms: number;  // Duration stability
  }[];
}

// GET /charts
export interface ChartsResponse {
  toolFrequency: { toolName: string; totalToolCalls: number }[];   // always present (L0 + L1)
  avgTokensPerCall: {                                      // always present (L0 + L1)
    processDefinitionKey: string;
    avgTokensPerCall: number | null;  // null → render "—"
    totalModelCalls: number;
  }[];
  incidentRateByVersion: {                                 // null at L0
    version: number;
    incidentRate: number;
    runs: number;
  }[] | null;
}
```

**API client** (`api/client.ts`):

```typescript
const BASE = '/api/agentic-control-plane';

const toParams = (p: AgentQueryParams) => {
  const sp = new URLSearchParams({ startDateFrom: p.startDateFrom, startDateTo: p.startDateTo });
  if (p.processDefinitionKey) sp.set('processDefinitionKey', p.processDefinitionKey);
  return sp.toString();
};

export const agentApi = {
  getSummary:          (p: AgentQueryParams) => get<SummaryResponse>(`${BASE}/summary?${toParams(p)}`),
  getProcessBreakdown: (p: AgentQueryParams) => get<ProcessBreakdownResponse>(`${BASE}/process-breakdown?${toParams(p)}`),
  getTrends:           (p: AgentQueryParams) => get<TrendsResponse>(`${BASE}/trends?${toParams(p)}`),
  getCharts:           (p: AgentQueryParams) => get<ChartsResponse>(`${BASE}/charts?${toParams(p)}`),
};
```

---

## 4. Shared Query Patterns

These utilities are defined once and called by every repository method.

### `AgentBaselineFilterBuilder`

```java
/**
 * Builds the bool filter shared by every Agentic Control Plane query.
 *
 * Always includes:
 *   state = COMPLETED
 *   startDate IN [from, to]
 *   tenantId IN authorized tenants
 *   has at least one agentInstance (nested exists)
 *
 * Conditionally adds:
 *   processDefinitionKey = <key>   when L1
 */
public static Query build(AgentQueryParams params) {
    var must = new ArrayList<Query>();
    must.add(term("state", "COMPLETED"));
    must.add(range("startDate", params.startDateFrom(), params.startDateTo()));
    must.add(terms("tenantId", params.tenantIds()));
    must.add(nestedExists("agentInstances", "agentInstances.agentInstanceKey"));
    if (params.processDefinitionKey() != null) {
        must.add(term("processDefinitionKey", params.processDefinitionKey()));
    }
    return bool(must);
}
```

### `PeriodComparisonExecutor`

```java
/**
 * Runs a query for the current period and the equivalent prior period of the same duration in parallel.
 * The service layer uses this for /summary only (3 delta fields: runs, duration, incidentRate).
 */
public <T> PeriodComparisonResult<T> execute(AgentQueryParams params, Function<AgentQueryParams, T> fn) {
    var current = CompletableFuture.supplyAsync(() -> fn.apply(params), executor);
    var prior   = CompletableFuture.supplyAsync(() -> fn.apply(params.forPreviousPeriodWindow()), executor);
    return new PeriodComparisonResult<>(current.join(), prior.join());
}
public record PeriodComparisonResult<T>(T current, T prior) {}
```

**delta fields** (from design): `totalRuns`, `avgDurationMs`, `incidentRate`.
All others in `/summary` (avgTokensPerRun, medianTokensPerRun, p50DurationMs, p95DurationMs) have
no delta — their values come from the same single query, no parallel request needed.

### `DateIntervalResolver`

```java
public static String resolve(Instant from, Instant to) {
    long days = ChronoUnit.DAYS.between(from, to);
    if (days <=  2) return "1h";
    if (days <= 30) return "1d";
    if (days <= 180)return "1w";
    return "1M";
}
```

Used by `/trends`. Returned as the `interval` field in `TrendsResponse`.

### `IncidentRateHelper`

```java
/** Shared by getSummary and getCharts (incidentRateByVersion). */
public static double computeRate(long incidentCount, long totalRuns) {
    return totalRuns > 0 ? (double) incidentCount / totalRuns : 0.0;
}
```

---

## 5. Endpoint Specifications

**All queries** start with `AgentBaselineFilterBuilder.build(params)`.
`tenantId`, `startDate` range, `has_agents`, and optional `processDefinitionKey` are always present.
The examples below show only the `aggs` section for brevity.

### 5.1 `GET /summary` (L0/L1)

**Purpose**: All KPI cards in one request. Service runs current + previous period in parallel.

```json
{
  "aggs": {
    "totalRuns":  { "value_count": { "field": "processInstanceId" } },
    "avgDuration":{ "avg": { "field": "duration" } },
    "durationPct":{ "percentiles": { "field": "duration", "percents": [50, 95] } },
    "totalInputTokens":  { "sum": { "field": "agentTotalInputTokens" } },
    "totalOutputTokens": { "sum": { "field": "agentTotalOutputTokens" } },
    "medianTokens": {
      "percentiles": {
        "script": {
          "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0L) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0L)"
        },
        "percents": [50]
      }
    },
    "has_incidents": {
      "filter": {
        "nested": { "path": "incidents", "query": { "exists": { "field": "incidents.id" } } }
      },
      "aggs": { "count": { "value_count": { "field": "processInstanceId" } } }
    }
  }
}
```

**Mapping to response**:

|    Response field    |                                 Source                                 |
|----------------------|------------------------------------------------------------------------|
| `totalRuns`          | `totalRuns.value`                                                      |
| `avgDurationMs`      | `avgDuration.value`                                                    |
| `p50DurationMs`      | `durationPct.values["50.0"]`                                           |
| `p95DurationMs`      | `durationPct.values["95.0"]`                                           |
| `avgTokensPerRun`    | `(totalInputTokens.value + totalOutputTokens.value) / totalRuns.value` |
| `medianTokensPerRun` | `medianTokens.values["50.0"]`                                          |
| `incidentRate`       | `has_incidents.count.value / totalRuns.value`                          |
| `incidentCount`      | `has_incidents.count.value`                                            |
| `activationCount`    | same as `totalRuns.value` at L0/L1                                     |
| `*Delta`             | `current.<field> − prior.<field>` from `PeriodComparisonExecutor`      |

---

### 5.2 `GET /process-breakdown` (L0)

**Purpose**: "Top token consumers by process" horizontal bar chart. L0 only — no `processDefinitionKey` filter applied. Returns 400 if `processDefinitionKey` is provided.

```json
{
  "aggs": {
    "by_process": {
      "terms": { "field": "processDefinitionKey", "size": 500,
                 "order": { "totalTokens": "desc" } },
      "aggs": {
        "totalInputTokens":  { "sum": { "field": "agentTotalInputTokens" } },
        "totalOutputTokens": { "sum": { "field": "agentTotalOutputTokens" } },
        "totalTokens": {
          "sum": {
            "script": {
              "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0L) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0L)"
            }
          }
        },
        "processInstanceCount": { "value_count": { "field": "processInstanceId" } }
      }
    }
  }
}
```

> Frontend resolves process names from the existing `/api/process-definition` endpoint using
> `processDefinitionKey`. A1 returns keys only — do not add a name join here.

---

### 5.3 `GET /trends` (L0/L1)

**Purpose**: All three trend charts in one ES round trip.

- **Token trend** (`inputTokens` + `outputTokens` aggregate lines)
- **Token outlier bands** (`tokenP5` / `tokenP50` / `tokenP95`)
- **Execution duration stability** (`durationP50Ms` / `durationP95Ms`)

One `date_histogram` with four sub-aggregations:

```json
{
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "<DateIntervalResolver>" },
      "aggs": {
        "inputTokens":  { "sum": { "field": "agentTotalInputTokens" } },
        "outputTokens": { "sum": { "field": "agentTotalOutputTokens" } },
        "token_bands": {
          "percentiles": {
            "script": {
              "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0L) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0L)"
            },
            "percents": [5, 50, 95]
          }
        },
        "duration_bands": {
          "percentiles": { "field": "duration", "percents": [50, 95] }
        }
      }
    }
  }
}
```

**Mapping to response**:

| `TrendPoint` field |                  Source                   |
|--------------------|-------------------------------------------|
| `inputTokens`      | `over_time.buckets[i].inputTokens.value`  |
| `outputTokens`     | `over_time.buckets[i].outputTokens.value` |
| `tokenP5`          | `token_bands.values["5.0"]`               |
| `tokenP50`         | `token_bands.values["50.0"]`              |
| `tokenP95`         | `token_bands.values["95.0"]`              |
| `durationP50Ms`    | `duration_bands.values["50.0"]`           |
| `durationP95Ms`    | `duration_bands.values["95.0"]`           |

No delta. `interval` = `DateIntervalResolver.resolve(from, to)`.

---

### 5.4 `GET /charts` (L0/L1)

**Purpose**: All bar chart data not covered by `/summary` or `/process-breakdown`.

- `toolFrequency` — always, at L0 and L1
- `avgTokensPerCall` — always, at L0 and L1 (L0: comparative across processes; L1: single entry for selected process)
- `incidentRateByVersion` — L1 only (null at L0; version breakdown is only meaningful for a single process)

**One ES query, conditional aggs**:

```json
{
  "aggs": {
    "agent_scope": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "totalToolCalls": { "sum": { "field": "agentInstances.metrics.toolCalls" } }
      }
    },

    "by_process": {
      "terms": { "field": "processDefinitionKey", "size": 100,
                 "order": { "avgTokensPerCall": "desc" } },
      "aggs": {
        "totalInput":      { "sum": { "field": "agentTotalInputTokens" } },
        "totalOutput":     { "sum": { "field": "agentTotalOutputTokens" } },
        "totalModelCalls": { "sum": { "field": "agentTotalModelCalls" } },
        "avgTokensPerCall": {
          "bucket_script": {
            "buckets_path": { "i": "totalInput", "o": "totalOutput", "c": "totalModelCalls" },
            "script": "params.c > 0 ? (params.i + params.o) / (double)params.c : null"
          }
        }
      }
    },

    "by_version": {
      // L1 only — skip when processDefinitionKey is null
      "terms": { "field": "processDefinitionVersion", "size": 100 },
      "aggs": {
        "totalRuns": { "value_count": { "field": "processInstanceId" } },
        "has_incidents": {
          "filter": {
            "nested": { "path": "incidents", "query": { "exists": { "field": "incidents.id" } } }
          },
          "aggs": { "count": { "value_count": { "field": "processInstanceId" } } }
        }
      }
    }
  }
}
```

> `agent_scope.totalToolCalls` is the fleet-wide sum of `agentInstances.metrics.toolCalls`.
> Phase 1 returns a single `{ toolName: "all_tools", totalToolCalls: N }` entry.
> Per-tool breakdown requires a history index — deferred to Phase 2.

**Mapping to response**:

|          `ChartsResponse` field           |                   Source                   | Condition |
|-------------------------------------------|--------------------------------------------|-----------|
| `toolFrequency[].toolName`                | `"all_tools"` (Phase 1 constant)           | Always    |
| `toolFrequency[].totalToolCalls`          | `agent_scope.totalToolCalls.value`         | Always    |
| `avgTokensPerCall[].processDefinitionKey` | `by_process.buckets[i].key`                | Always    |
| `avgTokensPerCall[].avgTokensPerCall`     | `avgTokensPerCall.value` (null if missing) | Always    |
| `avgTokensPerCall[].totalModelCalls`      | `totalModelCalls.value`                    | Always    |
| `incidentRateByVersion[].version`         | `by_version.buckets[i].key`                | L1 only   |
| `incidentRateByVersion[].incidentRate`    | `IncidentRateHelper.computeRate(...)`      | L1 only   |
| `incidentRateByVersion[].runs`            | `totalRuns.value`                          | L1 only   |

When `processDefinitionKey` is null: `incidentRateByVersion = null`.

No delta.

---

### 5.5 Phase 2 Endpoints

Do not implement or expose in Phase 1.

|         Path          |                             Purpose                             |
|-----------------------|-----------------------------------------------------------------|
| `GET /agent-elements` | Agent dropdown (A2) — distinct `elementId` values for a process |
| `GET /agents`         | Paginated agent instance list (A7) — L2 only                    |

Phase 2 also extends all 4 existing endpoints with `agentElementId` query param (L2 scope).

---

## 6. Layer 1 — Import Pipeline

Full detail in v2 §3 (data model, field constants, Painless script, import classes). This section
restates only the correctness-critical points.

### Index mapping (`ProcessInstanceIndex.java`)

Bump `VERSION = 8 → 9`. Nested mapping on `agentInstances` — must use `.nested()`, not `.object()`.

Critical fields to include in `addProperties()`:

```java
// Parent-level
.properties(AGENT_TOTAL_INPUT_TOKENS,  p -> p.long_(k -> k))
.properties(AGENT_TOTAL_OUTPUT_TOKENS, p -> p.long_(k -> k))
.properties(AGENT_TOTAL_MODEL_CALLS,   p -> p.long_(k -> k))
.properties(AGENT_TOTAL_TOOL_CALLS,    p -> p.long_(k -> k))

// Nested — all fields including Phase 2 reserved ones (avoids future VERSION bump)
.properties(AGENT_INSTANCES, p -> p.nested(n -> n
    .properties(AGENT_INSTANCE_KEY,           ...)  // keyword
    .properties(AGENT_ELEMENT_ID,             ...)  // keyword
    .properties(AGENT_BPMN_PROCESS_ID,        ...)  // keyword — Phase 2 reserved
    .properties(AGENT_PROCESS_DEFINITION_KEY, ...)  // keyword — Phase 2 reserved
    .properties(AGENT_PROCESS_DEF_VERSION,    ...)  // integer — Phase 2 reserved
    .properties(AGENT_TENANT_ID,              ...)  // keyword — Phase 2 reserved
    .properties(AGENT_STATUS,                 ...)  // keyword
    .properties(AGENT_CREATION_DATE,          ...)  // date
    .properties(AGENT_COMPLETION_DATE,        ...)  // date
    .properties(AGENT_LAST_UPDATED_DATE,      ...)  // date
    .properties(AGENT_DURATION_IN_MS,         ...)  // long
    .properties(AGENT_CREATION_EPOCH_MS,      ...)  // long (auxiliary for cross-batch duration)
    .properties("definition", ...)                   // object: model, provider
    .properties("metrics", ...)                      // object: inputTokens, outputTokens, modelCalls, toolCalls
    .properties("tools", ...)))                      // object: name, description, elementId
```

### Painless script (`ZeebeProcessInstanceScriptFactory.java`)

Add `createUpdateAgentInstancesScript()`. Critical correctness points:

1. `'COMPLETED'.equals(ai.status)` — not `ai.status == 'COMPLETED'` (null safety)
2. `durationInMs = newAi.completionDateEpochMs - ai.creationDateEpochMs` — epoch longs, not date strings
3. `creationDateEpochMs` stored in doc on CREATED; `completionDateEpochMs` passed via params on COMPLETED (transient, `@JsonIgnore`, not indexed)
4. Parent totals re-aggregated on every script run

**Validate against a live ES/OS instance before shipping.** Painless bugs are silent at compile time.

### Import classes

```java
// Phase 1: CREATED + COMPLETED only
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.COMPLETED);
```

```java
// Sort by timestamp before groupBy — prevents negative durationInMs in same-batch arrivals
.sorted(Comparator.comparingLong(HitEntity::getTimestamp))
.collect(groupingBy(hit -> hit.getValue().getProcessInstanceKey()))
```

```java
// Third arg is bpmnProcessId — NOT processDefinitionKey again
createSkeletonProcessInstance(
    String.valueOf(first.getProcessDefinitionKey()),
    String.valueOf(processInstanceKey),
    first.getBpmnProcessId(),   // ← String, not key
    first.getTenantId()
);
```

All Zeebe `long` keys stored as `String` in keyword fields: `String.valueOf(key)`.

---

## 7. Layer 3 — Frontend

**Location**: `optimize/client/src/components/AgenticControlPlane/`

### Component map

|        Component        | L0 | L1 |               Endpoint               |                         Notes                         |
|-------------------------|----|----|--------------------------------------|-------------------------------------------------------|
| `ProcessSelector`       | ✅  | ✅  | `/api/process-definition` (existing) | Not `/process-breakdown`                              |
| `SummaryKPIs`           | ✅  | ✅  | `/summary`                           | 3 cards: runs, duration, incident rate                |
| `TokenStats`            | ✅  | ✅  | `/summary`                           | Avg + Median tokens per run. Same request.            |
| `DurationStats`         | ✅  | ✅  | `/summary` + `/trends`               | P50/P95 scalars from summary; trend chart from trends |
| `TokenTrendChart`       | ✅  | ✅  | `/trends`                            | 2 lines: Input Tokens, Output Tokens                  |
| `TokenOutlierBands`     | ✅  | ✅  | `/trends`                            | P5/P50/P95 area chart. Same request as trend.         |
| `TopProcesses`          | ✅  | —  | `/process-breakdown`                 | Horizontal bar chart. L0 only.                        |
| `ToolCallFrequency`     | ✅  | ✅  | `/charts`                            | Horizontal bar chart. Label: "Agent activations"      |
| `AvgTokensPerProcess`   | ✅  | ✅  | `/charts`                            | Horizontal bar chart. `null` → "—"                    |
| `IncidentRateByVersion` | —  | ✅  | `/charts`                            | Vertical bar chart                                    |

**Phase 2 only — do not render in Phase 1**:
- `AgentSelector` (uses A2 `/agent-elements`)
- `AgentsList` (uses A7 `/agents`)
- All L2 scoped chart variants

### `AgentFilterContext`

```typescript
interface AgentFilterState {
  processDefinitionKey: string | null;   // null = L0
  dateRange: { from: string; to: string };
  // Phase 2: agentElementId: string | null;
}
```

Selecting a process sets `processDefinitionKey` → L0 to L1. All hooks re-fetch automatically.

### Hook pattern (one per endpoint)

```typescript
// useSummary.ts
export function useSummary(filter: AgentFilterState) {
  const params = toQueryParams(filter);
  return useQuery(['summary', params], () => agentApi.getSummary(params));
}

// useTrends.ts — shared by TokenTrendChart, TokenOutlierBands, DurationStats trend
export function useTrends(filter: AgentFilterState) {
  const params = toQueryParams(filter);
  return useQuery(['trends', params], () => agentApi.getTrends(params));
}

// useCharts.ts — shared by ToolCallFrequency, AvgTokensPerProcess, IncidentRateByVersion
export function useCharts(filter: AgentFilterState) {
  const params = toQueryParams(filter);
  return useQuery(['charts', params], () => agentApi.getCharts(params));
}
```

> **DRY**: `DurationStats` renders P50/P95 scalars from `useSummary()` (already fetched) and the
> trend chart from `useTrends()` (already fetched by `TokenTrendChart`). No extra requests.
> All three trend chart components share the single `useTrends()` cache entry.
> All three bar chart components share the single `useCharts()` cache entry.

### Display rules

- `incidentRate`: format as percentage, 2 decimal places. period delta badge shown as ±X.XX%
- `avgTokensPerCall === null` → render cell as `"—"`, not `"0"`
- `toolFrequency[].count`: axis label = "Agent activations" (not "Total calls")
- **No "Configure in settings" link** on Incident Rate card — settings page dropped
- **No status badges** anywhere in Phase 1

---

## 8. Parallel Development Tracks

### Contracts to finalize before any track starts

1. `AgentQueryParams` record (§3.1)
2. All TypeScript response types in `api/types.ts` (§3.2)

Once those are locked, all tracks are independent.

### Track A — Import Pipeline

No external dependencies. Can start immediately.

|                                      Task                                      |             Notes              |
|--------------------------------------------------------------------------------|--------------------------------|
| `ProcessInstanceIndex.VERSION = 9`, field constants, `addProperties()`         | Deploy before any data arrives |
| `AgentInstanceDto`, `AgentDefinitionDto`, `AgentMetricsDto`, `AgentToolDto`    | Java records                   |
| Add `agentInstances` field to `ProcessInstanceDto`                             |                                |
| `createUpdateAgentInstancesScript()` in `ZeebeProcessInstanceScriptFactory`    | Validate on live ES/OS         |
| `ZeebeAgentInstanceImportService`, `Handler`, `Fetcher`, `Mediator`, `Factory` | Follow incident pattern        |
| Register `ZeebeAgentInstanceImportMediatorFactory` in Spring context           |                                |

### Track B — Backend API

Can start against a local ES with the correct mapping. Blocked on Track A index mapping in shared environments.

|                                                  Task                                                  |                Notes                 |
|--------------------------------------------------------------------------------------------------------|--------------------------------------|
| `AgentBaselineFilterBuilder`, `PeriodComparisonExecutor`, `DateIntervalResolver`, `IncidentRateHelper` | Shared utilities first               |
| `AgenticControlPlaneRepository` impl (4 methods)                                                       | §5 ES queries                        |
| `AgenticControlPlaneService` impl                                                                      | period comparison, auth, DTO mapping |
| `AgenticControlPlaneController` impl                                                                   |                                      |

### Track C — Frontend

Can start immediately with mocked responses.

```typescript
// api/mock.ts — static fixtures matching TypeScript types; swap for agentApi when Track B ships
export const mockAgentApi: typeof agentApi = {
  getSummary: async () => ({ totalRuns: 4463, totalRunsDelta: 130, ... }),
  getProcessBreakdown: async () => ({ processes: [...] }),
  getTrends: async () => ({ interval: '1w', trend: [...] }),
  getCharts: async () => ({ toolFrequency: [...], avgTokensPerCall: [...], incidentRateByVersion: null }),
};
```

|                                         Task                                          |                Notes                |
|---------------------------------------------------------------------------------------|-------------------------------------|
| `api/types.ts`, `api/client.ts`, `api/mock.ts`                                        | Finalize types first                |
| `AgentFilterContext.tsx`                                                              | Date range + process selector state |
| `useSummary`, `useTrends`, `useCharts`, `useProcessBreakdown` hooks                   | One hook per endpoint               |
| `SummaryKPIs`, `TokenStats`, `DurationStats` (use `useSummary` + `useTrends`)         |                                     |
| `TokenTrendChart`, `TokenOutlierBands` (use `useTrends`, already cached)              |                                     |
| `TopProcesses` (use `useProcessBreakdown`)                                            | L0 only                             |
| `ToolCallFrequency`, `AvgTokensPerProcess`, `IncidentRateByVersion` (use `useCharts`) |                                     |
| `ControlPlaneDashboard.tsx`                                                           | Compose all components              |

### Track D — Integration

Blocked on Track A + B. Tasks: end-to-end smoke test with real Zeebe data, Painless validation,
nested object limit configuration, load test nested aggregation at high invocation volume.

---

## 9. Migration

### Checklist

- [ ] `ProcessInstanceIndex.VERSION = 9` deployed to **all environments** before import service starts
- [ ] `index.mapping.nested_objects.limit = 50000` set on `ProcessInstanceIndex`
- [ ] Painless script validated against live ES/OS (`_scripts/painless/execute`)
- [ ] `INTENTS_TO_IMPORT` = `{CREATED, COMPLETED}` — not UPDATED
- [ ] `createSkeletonProcessInstance` 3rd arg = `getBpmnProcessId()`, not `getProcessDefinitionKey()`
- [ ] Tenant filter present in every ES/OS query (security regression if missing)
- [ ] No "Configure in settings" link rendered in Phase 1
- [ ] `AgentSelector` and `AgentsList` components not rendered in Phase 1

### Notes

No data migration class needed. Old process instances carry no `agentInstances` data; queries on
missing nested paths return zero results. The VERSION bump ensures new indices use `.nested()` —
without it, ES/OS auto-maps to `.object()` on first write, silently breaking all nested aggregations.
