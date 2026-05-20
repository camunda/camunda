# Agentic Control Plane — Technical Specification (v3)

**Module**: `optimize/`
**Status**: Implementation-ready
**Supersedes**: `agentic-control-plane-technical-spec-v2.md`

---

## Table of Contents

1. [What Changed from v2](#1-what-changed-from-v2)
2. [Architecture Overview](#2-architecture-overview)
3. [Contracts and Interfaces](#3-contracts-and-interfaces)
   - 3.1 [Backend — Java Interfaces](#31-backend--java-interfaces)
   - 3.2 [Frontend — TypeScript Response Types](#32-frontend--typescript-response-types)
4. [Shared Query Patterns](#4-shared-query-patterns)
   - 4.1 [Baseline Filter](#41-baseline-filter)
   - 4.2 [WoW Delta Pattern](#42-wow-delta-pattern)
   - 4.3 [Date Interval Resolver](#43-date-interval-resolver)
5. [Endpoint Specifications](#5-endpoint-specifications)
   - 5.1 [GET /process-breakdown](#51-get-process-breakdown-l0)
   - 5.2 [GET /summary](#52-get-summary-l0l1)
   - 5.3 [GET /token-trend](#53-get-token-trend-l0l1)
   - 5.4 [GET /duration-stats](#54-get-duration-stats-l0l1)
   - 5.5 [GET /token-outlier-bands](#55-get-token-outlier-bands-l0l1)
   - 5.6 [GET /tokens-per-agent-call](#56-get-tokens-per-agent-call-l1)
   - 5.7 [GET /failure-rate-by-version](#57-get-failure-rate-by-version-l1)
   - 5.8 [Phase 2 Endpoints](#58-phase-2-endpoints)
6. [Layer 1 — Import Pipeline](#6-layer-1--import-pipeline)
   - 6.1 [Index Mapping](#61-index-mapping)
   - 6.2 [Painless Script](#62-painless-script)
   - 6.3 [Import Classes](#63-import-classes)
7. [Layer 3 — Frontend](#7-layer-3--frontend)
8. [Parallel Development Tracks](#8-parallel-development-tracks)
9. [Migration](#9-migration)

---

## 1. What Changed from v2

|                       Change                       |                                                  Reason                                                  |
|----------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| **A6 (Incident Rate) merged into A3 (Summary)**    | All KPI cards are one block in the UI — one request. A3 now returns `incidentRate` directly.             |
| **A10 simplified: returns version breakdown only** | With incidentRate in A3, A10 only needs to serve the bar chart. No WoW delta — versions are categorical. |
| **8 Phase 1 endpoints → 7**                        | Removing A6 as a standalone endpoint.                                                                    |
| **Formal interface section added (§3)**            | Single contract for frontend/backend to develop in parallel.                                             |
| **Shared query patterns extracted (§4)**           | `BaselineFilter`, `WoWExecutor`, `DateIntervalResolver` defined once, used everywhere.                   |
| **Parallel development tracks documented (§8)**    | Explicit track boundaries and contracts for team parallelism.                                            |

---

## 2. Architecture Overview

```
Zeebe (AgentInstanceRecord events)
        │
        ▼
ZeebeAgentInstanceImportService       ← Track A (Import)
   CREATED + COMPLETED events only
        │
        ▼
ProcessInstanceIndex (ES/OS)
   agentInstances: nested
   agentTotalInputTokens: long
   agentTotalOutputTokens: long
        │
        ▼
AgenticControlPlaneRepository         ← Track B (ES/OS queries)
        │
        ▼
AgenticControlPlaneService            ← Track B (business logic, WoW, auth)
        │
        ▼
AgenticControlPlaneController         ← Track B (REST)
        │
        ▼ 7 endpoints
React Dashboard (Carbon UI)           ← Track C (Frontend)
```

**Phase 1 scope**: L0 (fleet) + L1 (single process). L2 (agent element) = Phase 2.

---

## 3. Contracts and Interfaces

These are the **primary development contract**. Backend and frontend implement against these
independently. Do not start implementation without finalizing these first.

### 3.1 Backend — Java Interfaces

#### `AgentQueryParams`

Single value object passed to every repository and service method.

```java
/**
 * Immutable query parameters for all Agentic Control Plane endpoints.
 * Built by AgenticControlPlaneController from request params + JWT-resolved tenantIds.
 */
public record AgentQueryParams(
    List<String> tenantIds,           // resolved from JWT; never from client request params
    String processDefinitionKey,      // null = L0 (fleet); non-null = L1
    String agentElementId,            // null = L1; non-null = L2 (Phase 2 only)
    Instant startDateFrom,            // required
    Instant startDateTo               // required
) {
    /** Convenience: returns a copy of this params with the date range shifted back 7 days. */
    public AgentQueryParams forWoWWindow() {
        return new AgentQueryParams(
            tenantIds, processDefinitionKey, agentElementId,
            startDateFrom.minus(7, ChronoUnit.DAYS),
            startDateTo.minus(7, ChronoUnit.DAYS)
        );
    }
}
```

#### `AgenticControlPlaneRepository`

ES/OS query layer. One method per endpoint. Returns raw result objects, no WoW logic.

```java
public interface AgenticControlPlaneRepository {

    /** A1. Top token-consuming processes with aggregate stats. L0 only. */
    ProcessBreakdownResult getProcessBreakdown(AgentQueryParams params);

    /** A3. Aggregated KPIs including incident rate. L0/L1. */
    SummaryResult getSummary(AgentQueryParams params);

    /** A4. Token usage over time, grouped by top-5 agent elements. L0/L1. */
    TokenTrendResult getTokenTrend(AgentQueryParams params);

    /** A5. Duration percentiles (P50/P95) + stability trend over time. L0/L1. */
    DurationStatsResult getDurationStats(AgentQueryParams params);

    /** A8. Token consumption percentile bands (P5/P50/P95) over time. L0/L1. */
    TokenOutlierBandsResult getTokenOutlierBands(AgentQueryParams params);

    /** A9. Average tokens per model call, grouped by agent element. L1 only. */
    AvgTokensPerCallResult getAvgTokensPerCall(AgentQueryParams params);

    /** A10. Incident rate broken down by process definition version. L1 only. */
    FailureRateByVersionResult getFailureRateByVersion(AgentQueryParams params);

    // ── Phase 2 ──────────────────────────────────────────────────────────────

    /** A2. Distinct agent element IDs for a process. L1 → L2 filter. Phase 2. */
    AgentElementsResult getAgentElements(AgentQueryParams params);

    /** A7. Paginated agent instance rows with incident join. L2. Phase 2. */
    AgentInstancesResult getAgentInstances(AgentQueryParams params, String afterCursor);
}
```

#### `AgenticControlPlaneService`

Business logic: WoW delta, authorization, DTO mapping. Depends on the repository.

```java
public interface AgenticControlPlaneService {

    /** Returns A1 response. No WoW. */
    ProcessBreakdownResponse getProcessBreakdown(AgentQueryParams params);

    /** Returns A3 response including WoW deltas for all numeric fields. */
    SummaryResponse getSummary(AgentQueryParams params);

    /** Returns A4 response. No WoW. */
    TokenTrendResponse getTokenTrend(AgentQueryParams params);

    /** Returns A5 response including WoW deltas for P50 and P95. */
    DurationStatsResponse getDurationStats(AgentQueryParams params);

    /** Returns A8 response. No WoW. */
    TokenOutlierBandsResponse getTokenOutlierBands(AgentQueryParams params);

    /** Returns A9 response. No WoW. */
    AvgTokensPerCallResponse getAvgTokensPerCall(AgentQueryParams params);

    /** Returns A10 response. No WoW (version breakdown is categorical, not temporal). */
    FailureRateByVersionResponse getFailureRateByVersion(AgentQueryParams params);
}
```

#### `AgenticControlPlaneController`

REST layer. Resolves tenant IDs from JWT, builds `AgentQueryParams`, delegates to service.

```java
@RestController
@RequestMapping("/api/agentic-control-plane")
public interface AgenticControlPlaneController {

    @GetMapping("/process-breakdown")
    ProcessBreakdownResponse getProcessBreakdown(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/summary")
    SummaryResponse getSummary(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/token-trend")
    TokenTrendResponse getTokenTrend(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/duration-stats")
    DurationStatsResponse getDurationStats(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/token-outlier-bands")
    TokenOutlierBandsResponse getTokenOutlierBands(
        @RequestParam(required = false) String processDefinitionKey,
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/tokens-per-agent-call")
    AvgTokensPerCallResponse getAvgTokensPerCall(
        @RequestParam String processDefinitionKey,   // required at L1
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );

    @GetMapping("/failure-rate-by-version")
    FailureRateByVersionResponse getFailureRateByVersion(
        @RequestParam String processDefinitionKey,   // required at L1
        @RequestParam String startDateFrom,
        @RequestParam String startDateTo
    );
}
```

#### Response DTOs (Java)

The TypeScript types in §3.2 are the single source of truth. These are the Java equivalents.
Use Lombok `@Value` / `@Builder` for immutability.

```java
// A1
public record ProcessBreakdownResponse(List<ProcessBreakdownItem> processes) {}
public record ProcessBreakdownItem(
    String processDefinitionKey, long processInstanceCount,
    long avgDurationMs, long totalInputTokens, long totalOutputTokens, long incidentCount) {}

// A3
public record SummaryResponse(
    long totalRuns, Long totalRunsWoW,
    long avgDurationMs, Long avgDurationMsWoW,
    long totalInputTokens, Long totalInputTokensWoW,
    long totalOutputTokens, Long totalOutputTokensWoW,
    long medianTokens, Long medianTokensWoW,
    long totalToolCalls, Long totalToolCallsWoW,
    double incidentRate, Double incidentRateWoW,
    long incidentCount, long activationCount) {}

// A4
public record TokenTrendResponse(String interval, List<TokenTrendSeries> series) {}
public record TokenTrendSeries(String elementId, String label, List<TokenTrendPoint> data) {}
public record TokenTrendPoint(String date, long inputTokens, long outputTokens) {}

// A5
public record DurationStatsResponse(
    long p50Ms, Long p50MsWoW, long p95Ms, Long p95MsWoW,
    List<DurationTrendPoint> trend) {}
public record DurationTrendPoint(String date, long p50Ms, long p95Ms) {}

// A8
public record TokenOutlierBandsResponse(String interval, List<TokenBandPoint> bands) {}
public record TokenBandPoint(String date, long p5, long p50, long p95) {}

// A9
public record AvgTokensPerCallResponse(List<AvgTokensItem> agents) {}
public record AvgTokensItem(
    String elementId, String label,
    Double avgTokensPerCall,    // null when modelCalls = 0; frontend renders "—"
    long totalModelCalls) {}

// A10
public record FailureRateByVersionResponse(List<VersionFailureRate> versions) {}
public record VersionFailureRate(int version, double failureRate, long runs) {}
```

---

### 3.2 Frontend — TypeScript Response Types

**File**: `optimize/client/src/components/AgenticControlPlane/api/types.ts`

These types define the contract between frontend and backend. Generated from Java records or
maintained in sync manually.

```typescript
export interface AgentQueryParams {
  processDefinitionKey?: string;
  startDateFrom: string;  // ISO 8601
  startDateTo: string;    // ISO 8601
}

// A1 — /process-breakdown
export interface ProcessBreakdownResponse {
  processes: ProcessBreakdownItem[];
}
export interface ProcessBreakdownItem {
  processDefinitionKey: string;
  processInstanceCount: number;
  avgDurationMs: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  incidentCount: number;
}

// A3 — /summary
export interface SummaryResponse {
  totalRuns: number;
  totalRunsWoW: number | null;
  avgDurationMs: number;
  avgDurationMsWoW: number | null;
  totalInputTokens: number;
  totalInputTokensWoW: number | null;
  totalOutputTokens: number;
  totalOutputTokensWoW: number | null;
  medianTokens: number;
  medianTokensWoW: number | null;
  totalToolCalls: number;
  totalToolCallsWoW: number | null;
  incidentRate: number;       // [0, 1]; frontend formats as percentage
  incidentRateWoW: number | null;
  incidentCount: number;
  activationCount: number;    // denominador displayed in tooltip
}

// A4 — /token-trend
export interface TokenTrendResponse {
  interval: '1h' | '1d' | '1w' | '1M';
  series: TokenTrendSeries[];
}
export interface TokenTrendSeries {
  elementId: string;  // "__other__" for the "Other" rollup series
  label: string;
  data: { date: string; inputTokens: number; outputTokens: number }[];
}

// A5 — /duration-stats
export interface DurationStatsResponse {
  p50Ms: number;
  p50MsWoW: number | null;
  p95Ms: number;
  p95MsWoW: number | null;
  trend: { date: string; p50Ms: number; p95Ms: number }[];
}

// A8 — /token-outlier-bands
export interface TokenOutlierBandsResponse {
  interval: '1h' | '1d' | '1w' | '1M';
  bands: { date: string; p5: number; p50: number; p95: number }[];
}

// A9 — /tokens-per-agent-call
export interface AvgTokensPerCallResponse {
  agents: {
    elementId: string;
    label: string;
    avgTokensPerCall: number | null;  // null → render "—"
    totalModelCalls: number;
  }[];
}

// A10 — /failure-rate-by-version
export interface FailureRateByVersionResponse {
  versions: { version: number; failureRate: number; runs: number }[];
}

// Phase 2 types (placeholder — do not implement yet)
export interface AgentElementsResponse { agentElements: { elementId: string; label: string }[]; }
export interface AgentInstancesResponse { /* Phase 2 */ }
```

**API client** (`optimize/client/src/components/AgenticControlPlane/api/client.ts`):

```typescript
const BASE = '/api/agentic-control-plane';

function toParams(p: AgentQueryParams): URLSearchParams {
  const sp = new URLSearchParams({ startDateFrom: p.startDateFrom, startDateTo: p.startDateTo });
  if (p.processDefinitionKey) sp.set('processDefinitionKey', p.processDefinitionKey);
  return sp;
}

export const agentApi = {
  getProcessBreakdown: (p: AgentQueryParams) =>
    fetch<ProcessBreakdownResponse>(`${BASE}/process-breakdown?${toParams(p)}`),
  getSummary: (p: AgentQueryParams) =>
    fetch<SummaryResponse>(`${BASE}/summary?${toParams(p)}`),
  getTokenTrend: (p: AgentQueryParams) =>
    fetch<TokenTrendResponse>(`${BASE}/token-trend?${toParams(p)}`),
  getDurationStats: (p: AgentQueryParams) =>
    fetch<DurationStatsResponse>(`${BASE}/duration-stats?${toParams(p)}`),
  getTokenOutlierBands: (p: AgentQueryParams) =>
    fetch<TokenOutlierBandsResponse>(`${BASE}/token-outlier-bands?${toParams(p)}`),
  getAvgTokensPerCall: (p: AgentQueryParams) =>
    fetch<AvgTokensPerCallResponse>(`${BASE}/tokens-per-agent-call?${toParams(p)}`),
  getFailureRateByVersion: (p: AgentQueryParams) =>
    fetch<FailureRateByVersionResponse>(`${BASE}/failure-rate-by-version?${toParams(p)}`),
};
```

---

## 4. Shared Query Patterns

Define these utilities once. Every repository method uses them — never inline the same logic twice.

### 4.1 Baseline Filter

**File**: `AgentBaselineFilterBuilder.java`

```java
/**
 * Builds the bool filter shared by all Agentic Control Plane ES/OS queries.
 *
 * Applied to every request:
 *   - state = COMPLETED
 *   - startDate within [from, to]
 *   - tenantId IN authorized tenants
 *   - has at least one agent instance (nested exists)
 *   - (L1 only) processDefinitionKey = <key>
 */
public class AgentBaselineFilterBuilder {

    public static Query build(AgentQueryParams params) {
        var filters = new ArrayList<Query>();

        filters.add(term("state", "COMPLETED"));
        filters.add(range("startDate", params.startDateFrom(), params.startDateTo()));
        filters.add(terms("tenantId", params.tenantIds()));
        filters.add(nestedExists("agentInstances", "agentInstances.agentInstanceKey"));

        if (params.processDefinitionKey() != null) {
            filters.add(term("processDefinitionKey", params.processDefinitionKey()));
        }

        // Phase 2: if agentElementId != null, add nested term filter on agentInstances.elementId

        return bool().must(filters).build();
    }

    private static Query nestedExists(String path, String field) {
        return QueryBuilders.nested(n -> n
            .path(path)
            .query(q -> q.exists(e -> e.field(field))));
    }
}
```

### 4.2 WoW Delta Pattern

**File**: `WoWQueryExecutor.java`

```java
/**
 * Executes any repository query twice in parallel: once for the current period,
 * once for the prior 7-day window. Returns both results for delta computation.
 *
 * Usage:
 *   WoWResult<SummaryResult> wow = wowExecutor.execute(params, repo::getSummary);
 *   long deltaRuns = wow.current().totalRuns() - wow.prior().totalRuns();
 */
public class WoWQueryExecutor {

    private final ExecutorService executor; // shared thread pool

    public <T> WoWResult<T> execute(AgentQueryParams params,
                                     Function<AgentQueryParams, T> queryFn) {
        var currentFuture = CompletableFuture.supplyAsync(
            () -> queryFn.apply(params), executor);
        var priorFuture = CompletableFuture.supplyAsync(
            () -> queryFn.apply(params.forWoWWindow()), executor);

        return new WoWResult<>(currentFuture.join(), priorFuture.join());
    }
}

public record WoWResult<T>(T current, T prior) {}
```

Endpoints with WoW: **A3** (all KPIs), **A5** (P50, P95).
Endpoints without WoW: A1, A4, A8, A9, A10 (charts — no delta semantics).

### 4.3 Date Interval Resolver

**File**: `DateIntervalResolver.java`

Used by A4, A5 (trend), A8 to derive the `calendar_interval` from the requested date range.
One implementation, used in all three endpoints.

```java
public class DateIntervalResolver {

    public static CalendarInterval resolve(Instant from, Instant to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 2)   return CalendarInterval.Hour;
        if (days <= 30)  return CalendarInterval.Day;
        if (days <= 180) return CalendarInterval.Week;
        return CalendarInterval.Month;
    }

    public static String toApiString(CalendarInterval interval) {
        return switch (interval) {
            case Hour  -> "1h";
            case Day   -> "1d";
            case Week  -> "1w";
            case Month -> "1M";
            default    -> "1d";
        };
    }
}
```

---

## 5. Endpoint Specifications

All endpoints: `GET /api/agentic-control-plane/<path>`.
All queries start with `AgentBaselineFilterBuilder.build(params)`.
All dates in responses are ISO 8601 UTC strings.

### 5.1 `GET /process-breakdown` (L0)

**Purpose**: Ranked list of processes by total token consumption. Powers the Process Breakdown
chart on the L0 dashboard. The `ProcessSelector` dropdown uses the existing
`/api/process-definition` endpoint, **not this one**.

**Aggregation**: `terms` on `processDefinitionKey` with sub-aggregations for token sums,
duration avg, and incident count.

```json
{
  "aggs": {
    "by_process": {
      "terms": { "field": "processDefinitionKey", "size": 500 },
      "aggs": {
        "processInstanceCount": { "value_count": { "field": "processInstanceId" } },
        "avgDuration":          { "avg":         { "field": "duration" } },
        "totalInputTokens":     { "sum":         { "field": "agentTotalInputTokens" } },
        "totalOutputTokens":    { "sum":         { "field": "agentTotalOutputTokens" } },
        "incidentCount": {
          "nested": { "path": "incidents" },
          "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
        }
      }
    }
  }
}
```

Response: `ProcessBreakdownResponse` — see §3.

---

### 5.2 `GET /summary` (L0/L1)

**Purpose**: All KPI cards in one request: Total Runs, Avg Duration, Total Tokens, Median Tokens,
Total Tool Calls, **Incident Rate**. Includes WoW deltas via `WoWQueryExecutor`.

**Aggregation** (one query, all KPIs including incident rate):

```json
{
  "aggs": {
    "totalRuns":         { "value_count": { "field": "processInstanceId" } },
    "avgDuration":       { "avg":         { "field": "duration" } },
    "totalInputTokens":  { "sum":         { "field": "agentTotalInputTokens" } },
    "totalOutputTokens": { "sum":         { "field": "agentTotalOutputTokens" } },
    "medianTokens": {
      "percentiles": {
        "script": {
          "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0L) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0L)"
        },
        "percents": [50]
      }
    },
    "totalToolCalls": {
      "nested": { "path": "agentInstances" },
      "aggs": { "sum": { "sum": { "field": "agentInstances.metrics.toolCalls" } } }
    },
    "has_incidents": {
      "filter": {
        "nested": {
          "path": "incidents",
          "query": { "exists": { "field": "incidents.id" } }
        }
      },
      "aggs": { "count": { "value_count": { "field": "processInstanceId" } } }
    }
  }
}
```

**Mapping to response fields:**

|   Response field    |                        Derived from                         |
|---------------------|-------------------------------------------------------------|
| `totalRuns`         | `totalRuns.value`                                           |
| `avgDurationMs`     | `avgDuration.value`                                         |
| `totalInputTokens`  | `totalInputTokens.value`                                    |
| `totalOutputTokens` | `totalOutputTokens.value`                                   |
| `medianTokens`      | `medianTokens.values["50.0"]`                               |
| `totalToolCalls`    | `totalToolCalls.sum.value`                                  |
| `incidentRate`      | `has_incidents.count.value / totalRuns.value`               |
| `incidentCount`     | `has_incidents.count.value`                                 |
| `activationCount`   | same as `totalRuns.value` (at L0/L1)                        |
| `*WoW`              | `current.<field> − prior.<field>` (from `WoWQueryExecutor`) |

Response: `SummaryResponse` — see §3.

---

### 5.3 `GET /token-trend` (L0/L1)

**Purpose**: Token consumption over time. Multi-line: top-5 agent elements by total tokens + "Other".

**Two-step approach:**

**Step 1** — identify top-5 `elementId`s (one query):

```json
{
  "aggs": {
    "agent_scope": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "by_element": {
          "terms": {
            "field": "agentInstances.elementId",
            "size": 5,
            "order": { "total_tokens": "desc" }
          },
          "aggs": {
            "total_tokens": {
              "sum": {
                "script": {
                  "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0L) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0L)"
                }
              }
            }
          }
        }
      }
    }
  }
}
```

> Painless runs inside the `nested` context — use bare paths `doc['metrics.inputTokens']`,
> not `doc['agentInstances.metrics.inputTokens']`.

**Step 2** — date histogram per top-5 element + one total (6 parallel requests):

For each `elementId` from Step 1 (plus one "total" request on parent fields for "Other"):

```json
{
  "query": {
    "bool": {
      "must": [
        { "<<baseline-filter>>" },
        { "nested": { "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } } } }
      ]
    }
  },
  "aggs": {
    "agent_scope": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
          "aggs": {
            "over_time": {
              "date_histogram": { "field": "agentInstances.completionDate",
                                  "calendar_interval": "<interval>" },
              "aggs": {
                "inputTokens":  { "sum": { "field": "agentInstances.metrics.inputTokens" } },
                "outputTokens": { "sum": { "field": "agentInstances.metrics.outputTokens" } }
              }
            }
          }
        }
      }
    }
  }
}
```

"Other" total query uses `agentTotalInputTokens` / `agentTotalOutputTokens` on the parent document.
`Other(date) = total(date) − Σ top5(date)` — computed in Java after collecting all results.

Response: `TokenTrendResponse` — see §3. `interval` = `DateIntervalResolver.toApiString(...)`.

---

### 5.4 `GET /duration-stats` (L0/L1)

**Purpose**: Duration P50/P95 KPIs with WoW deltas + stability trend. Uses parent-level `duration`
field (process duration, pre-computed).

```json
{
  "aggs": {
    "duration_percentiles": {
      "percentiles": { "field": "duration", "percents": [50, 95] }
    },
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "<interval>" },
      "aggs": {
        "duration_trend": {
          "percentiles": { "field": "duration", "percents": [50, 95] }
        }
      }
    }
  }
}
```

WoW: applied only to the `duration_percentiles` agg (KPI cards). The trend data does not have
WoW. Run the WoW parallel request without the `date_histogram` agg for efficiency.

Response: `DurationStatsResponse` — see §3.

---

### 5.5 `GET /token-outlier-bands` (L0/L1)

**Purpose**: P5/P50/P95 of total tokens per time bucket. Shows consumption distribution over time.

```json
{
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "<interval>" },
      "aggs": {
        "token_bands": {
          "percentiles": {
            "script": {
              "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0L) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0L)"
            },
            "percents": [5, 50, 95]
          }
        }
      }
    }
  }
}
```

Response: `TokenOutlierBandsResponse` — see §3.

---

### 5.6 `GET /tokens-per-agent-call` (L1)

**Purpose**: Average tokens per LLM model call, grouped by agent element. L1 only — requires
`processDefinitionKey`. Returns 400 if absent.

```json
{
  "aggs": {
    "agent_scope": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "by_element": {
          "terms": { "field": "agentInstances.elementId", "size": 100 },
          "aggs": {
            "totalInput":      { "sum": { "field": "agentInstances.metrics.inputTokens" } },
            "totalOutput":     { "sum": { "field": "agentInstances.metrics.outputTokens" } },
            "totalModelCalls": { "sum": { "field": "agentInstances.metrics.modelCalls" } },
            "tokensPerCall": {
              "bucket_script": {
                "buckets_path": { "input": "totalInput", "output": "totalOutput",
                                  "calls": "totalModelCalls" },
                "script": "params.calls > 0 ? (params.input + params.output) / (double)params.calls : null"
              }
            }
          }
        }
      }
    }
  }
}
```

> `bucket_script` returns `null` when `modelCalls = 0`. Java must pass this through as JSON `null`.
> Frontend renders `null` as `"—"`, never as `"0"`.

Response: `AvgTokensPerCallResponse` — see §3.

---

### 5.7 `GET /failure-rate-by-version` (L1)

**Purpose**: Incident rate per `processDefinitionVersion`. Powers the version breakdown bar chart.
L1 only. No WoW (versions are categorical). Returns 400 if `processDefinitionKey` is absent.

Single query groups by parent-level `processDefinitionVersion`:

```json
{
  "aggs": {
    "by_version": {
      "terms": { "field": "processDefinitionVersion", "size": 100 },
      "aggs": {
        "totalRuns": { "value_count": { "field": "processInstanceId" } },
        "has_incidents": {
          "filter": {
            "nested": {
              "path": "incidents",
              "query": { "exists": { "field": "incidents.id" } }
            }
          },
          "aggs": { "count": { "value_count": { "field": "processInstanceId" } } }
        }
      }
    }
  }
}
```

`failureRate[v] = has_incidents.count / totalRuns` — computed in Java per bucket.

> This uses the same `has_incidents` pattern as A3. Extract `IncidentRateCalculator.compute(
> totalRuns, incidentCount)` as a shared utility to avoid repeating the division-and-null-check
> logic.

Response: `FailureRateByVersionResponse` — see §3.

---

### 5.8 Phase 2 Endpoints

The following endpoints are **not implemented in Phase 1**. Interfaces are defined in §3.1 as
placeholders. Backend and frontend must not expose or render these.

|         Path          |                      Purpose                       |
|-----------------------|----------------------------------------------------|
| `GET /agent-elements` | Agent element dropdown (A2). Powers AgentSelector. |
| `GET /agents`         | Paginated agent instance list (A7). L2 only.       |

When Phase 2 begins, also add `agentElementId` query param to all existing endpoints.
Phase 2 adds L2 query variants using `nested + filter(elementId)` inside aggregations.

---

## 6. Layer 1 — Import Pipeline

Full implementation detail in v2 §3. This section highlights only what is critical for
correct Phase 1 behavior.

### 6.1 Index Mapping

**File**: `ProcessInstanceIndex.java`

Bump `VERSION = 8 → 9`. New field constants (full list in v2 §3.1):

```java
// Parent-level (maintained by Painless script)
public static final String AGENT_TOTAL_INPUT_TOKENS  = "agentTotalInputTokens";
public static final String AGENT_TOTAL_OUTPUT_TOKENS = "agentTotalOutputTokens";

// Nested root
public static final String AGENT_INSTANCES           = "agentInstances";
public static final String AGENT_INSTANCE_KEY        = "agentInstanceKey";
public static final String AGENT_ELEMENT_ID          = "elementId";
public static final String AGENT_BPMN_PROCESS_ID     = "bpmnProcessId";
// ... (full list in v2 §3.1)

// Auxiliary — enables cross-batch durationInMs computation in Painless
public static final String AGENT_CREATION_EPOCH_MS   = "creationDateEpochMs";
```

**Critical points:**

1. Use `.nested()` for `agentInstances`. `.object()` silently breaks all nested aggregations.
2. Add `processDefinitionKey`, `processDefinitionVersion`, `tenantId`, `bpmnProcessId` inside
   the nested mapping now (they are on every event) — avoids a future VERSION bump.
3. `tools` uses `.object()` — adequate for Phase 1 display. Change to `.nested()` in Phase 2
   if per-tool aggregation is needed.

### 6.2 Painless Script

**File**: `ZeebeProcessInstanceScriptFactory.java` — add `createUpdateAgentInstancesScript()`.

Full script in v2 §3.2. Critical correctness points:

1. **String comparison**: use `'COMPLETED'.equals(ai.status)` — null-safe, not `ai.status == 'COMPLETED'`.
2. **`durationInMs`**: computed as `newAi.completionDateEpochMs - ai.creationDateEpochMs` (both are
   longs). `creationDateEpochMs` is stored in the doc on CREATED. `completionDateEpochMs` is passed
   as a transient field in `params` (not stored in the index — annotate `@JsonIgnore` on the DTO field).
3. **Parent-level re-aggregation**: runs on every script execution; sums `metrics.inputTokens` and
   `metrics.outputTokens` for all agents with `status = 'COMPLETED'`.

**Validate the script** against a live ES/OS instance before shipping using `_scripts/painless/execute`.
Painless errors are silent at compile time.

### 6.3 Import Classes

**Critical points:**

```java
// Phase 1: CREATED + COMPLETED only
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.COMPLETED);
```

```java
// Sort by timestamp before groupBy — prevents negative durationInMs in same-batch processing
.sorted(Comparator.comparingLong(HitEntity::getTimestamp))
.collect(groupingBy(hit -> hit.getValue().getProcessInstanceKey()))
```

```java
// bpmnProcessId — third argument to createSkeletonProcessInstance
ProcessInstanceDto pi = createSkeletonProcessInstance(
    String.valueOf(first.getProcessDefinitionKey()),  // processDefinitionKey
    String.valueOf(processInstanceKey),               // processInstanceId
    first.getBpmnProcessId(),                         // ← bpmnProcessId (NOT processDefinitionKey again)
    first.getTenantId()
);
```

> All Zeebe `long` keys (`agentInstanceKey`, `elementInstanceKey`, `processDefinitionKey`) are
> stored as `String` (keyword field type). Convert with `String.valueOf(...)` in the mapper.

Full `mapToAgentInstanceDto` implementation in v2 §3.3.

---

## 7. Layer 3 — Frontend

**Location**: `optimize/client/src/components/AgenticControlPlane/`

### Phase 1 — Components by filter level

|        Component        | L0 |      L1      |                                Data source                                 |
|-------------------------|----|--------------|----------------------------------------------------------------------------|
| `ProcessSelector`       | ✅  | ✅ (selected) | `/api/process-definition` (existing)                                       |
| `SummaryKPIs`           | ✅  | ✅            | `GET /summary` — **one request for all KPI cards including incident rate** |
| `TokenTrendChart`       | ✅  | ✅            | `GET /token-trend`                                                         |
| `DurationStats`         | ✅  | ✅            | `GET /duration-stats`                                                      |
| `TokenOutlierBands`     | ✅  | ✅            | `GET /token-outlier-bands`                                                 |
| `AvgTokensPerAgentCall` | —  | ✅            | `GET /tokens-per-agent-call`                                               |
| `FailureRateByVersion`  | —  | ✅            | `GET /failure-rate-by-version`                                             |

**Not rendered in Phase 1**: `AgentSelector` (A2), `AgentsList` (A7), all L2 chart variants.

### Filter context

```typescript
// AgentFilterContext.ts
interface AgentFilterState {
  processDefinitionKey: string | null;  // null = L0
  dateRange: { from: string; to: string };
  // Phase 2 only:
  // agentElementId: string | null;
}
```

All hooks receive `AgentFilterState` and derive the `AgentQueryParams` from it. Date range comes
from a top-level `DateRangePicker` shared with the rest of the dashboard.

### Hook pattern

Each chart component has a corresponding hook. The hook encapsulates the API call, loading state,
and error handling. This is the DRY boundary for data fetching — never fetch in a component directly.

```typescript
// Example: useSummary.ts
export function useSummary(filter: AgentFilterState) {
  const params: AgentQueryParams = {
    processDefinitionKey: filter.processDefinitionKey ?? undefined,
    startDateFrom: filter.dateRange.from,
    startDateTo: filter.dateRange.to,
  };
  return useQuery(['summary', params], () => agentApi.getSummary(params));
}
```

### Notes

- **Incident Rate card**: rendered from `SummaryResponse.incidentRate`. No separate A6 request.
- **Token trend "Other" series**: `elementId === "__other__"` is the sentinel. Computed in Java
  (not client-side).
- **WoW display**: `null` WoW values → render without delta indicator (not "0%"). This occurs
  when the prior period has no data.
- **`avgTokensPerCall === null`** → render `"—"` (agent had no model calls).
- **No status badges, no settings page** anywhere in Phase 1.

---

## 8. Parallel Development Tracks

Four tracks can progress independently after the contracts in §3 are finalized.

### Track A — Import Pipeline

**Owner**: backend developer
**Dependency**: None (can start immediately)
**Contract delivered to others**: `AgentInstanceDto` shape, `ProcessInstanceIndex` VERSION 9 in staging

|                            Task                            |                   File                   |               Notes               |
|------------------------------------------------------------|------------------------------------------|-----------------------------------|
| Bump `ProcessInstanceIndex.VERSION`                        | `ProcessInstanceIndex.java`              | Must deploy before any import     |
| Add field constants                                        | `ProcessInstanceIndex.java`              | See §6.1                          |
| Add `addProperties()` mapping                              | `ProcessInstanceIndex.java`              | See v2 §3.1                       |
| `AgentInstanceDto` + sub-DTOs                              | new file                                 | See v2 §3.1                       |
| Add `agentInstances` to `ProcessInstanceDto`               | `ProcessInstanceDto.java`                |                                   |
| `createUpdateAgentInstancesScript()`                       | `ZeebeProcessInstanceScriptFactory.java` | See §6.2                          |
| `ZeebeAgentInstanceImportService`                          | new file                                 | See §6.3                          |
| `ZeebeAgentInstanceImportHandler/Fetcher/Mediator/Factory` | new files                                | Follow incident pattern           |
| Painless script validation                                 | —                                        | Against live ES/OS before merging |

### Track B — Backend API

**Owner**: backend developer (can be the same or different person from Track A)
**Dependency**: Track A index mapping deployed (can mock locally with manual ES mapping)
**Contract delivered to others**: REST endpoints returning types from §3.2

|                 Task                 |   File   |           Notes           |
|--------------------------------------|----------|---------------------------|
| `AgentQueryParams` record            | new file | §3.1                      |
| `AgentBaselineFilterBuilder`         | new file | §4.1                      |
| `DateIntervalResolver`               | new file | §4.3                      |
| `WoWQueryExecutor`                   | new file | §4.2                      |
| `IncidentRateCalculator` utility     | new file | Shared by A3 and A10      |
| `AgenticControlPlaneRepository` impl | new file | §5 queries                |
| `AgenticControlPlaneService` impl    | new file | WoW, auth, mapping        |
| `AgenticControlPlaneController` impl | new file | §3.1 controller interface |
| Response DTOs                        | new file | §3.1 Java records         |

### Track C — Frontend

**Owner**: frontend developer
**Dependency**: TypeScript types from §3.2 (can start immediately with mocked responses)
**Mocking strategy**: Create a `mockAgentApi` in `api/mock.ts` returning static fixtures matching
the TypeScript types. Swap for the real `agentApi` when Track B is complete.

|                           Task                            |   File    |                  Notes                  |
|-----------------------------------------------------------|-----------|-----------------------------------------|
| `api/types.ts`                                            | new file  | §3.2 — finalize first                   |
| `api/client.ts`                                           | new file  | §3.2                                    |
| `api/mock.ts`                                             | new file  | Fixtures for all 7 endpoints            |
| `AgentFilterContext.tsx`                                  | new file  | §7                                      |
| `ProcessSelector.tsx`                                     | new file  | Uses existing `/api/process-definition` |
| `SummaryKPIs.tsx` + `useSummary.ts`                       | new files |                                         |
| `TokenTrendChart.tsx` + `useTokenTrend.ts`                | new files |                                         |
| `DurationStats.tsx` + `useDurationStats.ts`               | new files |                                         |
| `TokenOutlierBands.tsx` + `useTokenOutlierBands.ts`       | new files |                                         |
| `AvgTokensPerAgentCall.tsx` + `useAvgTokensPerCall.ts`    | new files | L1 only                                 |
| `FailureRateByVersion.tsx` + `useFailureRateByVersion.ts` | new files | L1 only                                 |
| `ControlPlaneDashboard.tsx`                               | new file  | Wires all components                    |

### Track D — Integration

**Owner**: any
**Dependency**: Track A + Track B complete
**Tasks**: End-to-end test with real Zeebe data, Painless script validation on real ES/OS, load
test nested aggregation performance at high agent invocation volume.

---

## 9. Migration

### Index version bump

`ProcessInstanceIndex.VERSION`: `8 → 9`. No data migration class required.

Old documents carry no `agentInstances` data. Queries on missing nested paths return zero results.
The VERSION bump ensures new indices are created with the correct `nested` mapping. Without the
bump, ES/OS auto-maps `agentInstances` as `object` on first write, silently breaking all nested
aggregations — there is no runtime error, queries just return zeros.

### Nested object limit

Set `index.mapping.nested_objects.limit = 50000` on `ProcessInstanceIndex`. Default is 10,000
across all nested fields per document. Breach is silent — documents truncate without error.

### Import service registration

Register `ZeebeAgentInstanceImportMediatorFactory` alongside `ZeebeIncidentImportMediatorFactory`
in the Spring context.

### Validation checklist before deploying

- [ ] `ProcessInstanceIndex.VERSION = 9` deployed to all environments before import service starts
- [ ] Painless script validated against live ES/OS instance
- [ ] Nested object limit configured on `ProcessInstanceIndex`
- [ ] `INTENTS_TO_IMPORT` contains only `CREATED` and `COMPLETED`
- [ ] `createSkeletonProcessInstance` third argument is `getBpmnProcessId()`, not `getProcessDefinitionKey()`
- [ ] Tenant filter present on every ES/OS query

