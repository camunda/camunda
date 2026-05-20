# Agentic Control Plane — Implementation Summary

Backend implementation for monitoring AI agent runs embedded in Zeebe process instances.
Data flows from Zeebe exporter → Optimize import pipeline → `process-instance-*` index → REST API.

---

## Architecture

Two views:

- **L0 (fleet)** — `processDefinitionKey` omitted; queries the wildcard alias `process-instance-*`
- **L1 (single process)** — `processDefinitionKey` provided; queries the single matching alias

Baseline filter (applied to every query): `state=COMPLETED`, `startDate` in range, `tenantIds`,
`agentInstances` nested exists, optional `processDefinitionKey` term.

---

## Index Schema — ProcessInstanceIndex (VERSION=9)

New fields on `process-instance-{key}`:

| Field | Type | Notes |
|---|---|---|
| `agentInstances` | `nested` | One entry per `AGENT_INSTANCE` lifecycle |
| `agentInstances.agentInstanceId` | `keyword` | Zeebe record key (string) |
| `agentInstances.flowNodeId` | `keyword` | |
| `agentInstances.processDefinitionVersion` | `keyword` | |
| `agentInstances.startDate` | `date` | Set on `CREATED` |
| `agentInstances.startDateEpochMs` | `long` | Used for duration math in Painless |
| `agentInstances.endDate` | `date` | Set on `COMPLETED` |
| `agentInstances.totalDurationInMs` | `long` | Computed on merge |
| `agentInstances.metrics.inputTokens` | `long` | |
| `agentInstances.metrics.outputTokens` | `long` | |
| `agentInstances.metrics.modelCalls` | `long` | |
| `agentInstances.metrics.toolCalls` | `long` | |
| `agentInstances.tools` | `object` | Not nested |
| `agentInstances.tools.name` | `keyword` | |
| `agentTotalInputTokens` | `long` | Rolled-up aggregate, nullValue=0 |
| `agentTotalOutputTokens` | `long` | |
| `agentTotalModelCalls` | `long` | |
| `agentTotalToolCalls` | `long` | |

Upgrade plan: `Upgrade89to810PlanFactory` (8.9 → 8.10.0) enumerates all existing
`process-instance-*` aliases at runtime via `databaseClient.getAliasesForIndexPattern`
and creates one `UpdateIndexStep` per key.

---

## Import Pipeline

Zeebe exports `AGENT_INSTANCE` records (`CREATED`, `COMPLETED` intents) into
`{prefix}-agent-instance` index.

| Class | Role |
|---|---|
| `ZeebeAgentInstanceFetcherES/OS` | Prototype bean; fetches pages from `agent-instance` index |
| `ZeebeAgentInstanceImportIndexHandler` | Tracks position/sequence per partition |
| `ZeebeAgentInstanceImportMediator` | Drives paged import loop |
| `ZeebeAgentInstanceImportMediatorFactory` | Spring `@Component`; auto-collected into `List<AbstractZeebeImportMediatorFactory>` |
| `ZeebeAgentInstanceImportService` | Maps records → `ProcessInstanceDto`, computes per-instance aggregates |

Both factory and handler are auto-wired: the factory via Spring's list injection, the handler via
`ZeebeImportIndexHandlerProvider`'s ClassGraph scan of `PositionBasedImportIndexHandler` subclasses.

### Update Script

`ZeebeProcessInstanceScriptFactory.createProcessInstanceUpdateScript()` includes
`createUpdateAgentInstancesScript()` which:

1. Null-guards `existingInstance.agentInstances`
2. Merges by `agentInstanceId` (upsert)
3. Computes `totalDurationInMs` when both `startDateEpochMs` and `endDate` are present
4. Re-aggregates `agentTotal*` fields over all agents after every merge

---

## REST API

Base path: `GET /api/agentic-control-plane`

All endpoints require authentication (`SessionService.getRequestUserOrFailNotAuthorized`).
Common query params: `startDateFrom` (ISO-8601), `startDateTo` (ISO-8601).
Optional on most: `processDefinitionKey` (omit for L0, set for L1).

### GET /summary

Both L0 and L1. Returns KPIs for current period + delta vs. equal-length previous period
(computed in parallel via `PeriodComparisonExecutor`).

```json
{
  "totalRuns": 1240,
  "totalRunsDelta": 80,
  "avgDurationMs": 3400,
  "avgDurationMsDelta": -120,
  "incidentRate": 0.04,
  "incidentRateDelta": -0.01,
  "incidentCount": 50,
  "activationCount": 1240,
  "avgTokensPerRun": 850,
  "medianTokensPerRun": 720,
  "p50DurationMs": 3100,
  "p95DurationMs": 8900
}
```

Delta fields are `null` when previous period has no data.

### GET /process-breakdown

L0 only (`processDefinitionKey` must not be provided). Returns top processes by token consumption.

```json
{
  "processes": [
    { "processDefinitionKey": "invoice", "totalInputTokens": 50000, "totalOutputTokens": 30000, "processInstanceCount": 400 }
  ]
}
```

### GET /trends

Both L0 and L1. Interval auto-resolved from date range (hour/day/week/month).

```json
{
  "interval": "1d",
  "trend": [
    { "date": "2024-01-01T00:00:00Z", "inputTokens": 5000, "outputTokens": 3000,
      "tokenP5": 200, "tokenP50": 800, "tokenP95": 1800,
      "durationP50Ms": 3000, "durationP95Ms": 9000 }
  ]
}
```

### GET /charts

Both L0 and L1. `incidentRateByVersion` is `null` at L0.

```json
{
  "toolFrequency": [{ "toolName": "search", "totalToolCalls": 4200 }],
  "avgTokensPerCall": [{ "processDefinitionKey": "invoice", "avgTokensPerCall": 280.5, "totalModelCalls": 1200 }],
  "incidentRateByVersion": [{ "version": 3, "incidentRate": 0.03, "runs": 200 }]
}
```

`avgTokensPerCall` is `null` when `totalModelCalls == 0`.

### GET /process-definitions

L0 only. Returns all process definitions that have agent runs in the date range,
intersected with definitions the requesting user is authorized to see.

```json
[
  { "key": "invoice", "name": "Invoice Process" }
]
```

Service layer calls `DefinitionService.getFullyImportedDefinitions(PROCESS, userId)` and
intersects with keys found via terms agg on `processDefinitionKey` (size=10000).

---

## Key Classes by Layer

```
REST
  AgenticControlPlaneRestService

Service
  AgenticControlPlaneService (interface)
  AgenticControlPlaneServiceImpl
    └── PeriodComparisonExecutor  (parallel prev/cur queries via agenticQueryExecutor thread pool)
    └── DefinitionService         (authorization filter for /process-definitions)

Repository (ES/OS)
  AgenticControlPlaneRepository (interface)
  AgenticControlPlaneRepositoryES
  AgenticControlPlaneRepositoryOS
    └── AgentBaselineFilterBuilderES/OS  (root filter applied to every query)

DTOs
  AgentQueryParams        (tenantIds, processDefinitionKey, startDateFrom, startDateTo)
  SummaryResult           (internal; period-comparison input)
  SummaryResponse / ProcessBreakdownResponse / TrendsResponse / ChartsResponse
  DefinitionKeyResponseDto  (key + name; /process-definitions)

Import
  ZeebeAgentInstanceRecordDto / ZeebeAgentInstanceDataDto
  AgentInstanceDto  (nested in ProcessInstanceDto)
  ProcessInstanceDto  (agentInstances, agentTotal*)
```

---

## ES vs OS Implementation Notes

| Concern | Elasticsearch | OpenSearch |
|---|---|---|
| `sum().value()` | primitive `double` — cast to `long` | `@Nullable Double` — use `doubleAggToLong()` |
| `StringTermsBucket.key()` | returns `FieldValue` — call `.stringValue()` | returns `String` directly |
| Exception on missing index | `ElasticsearchException` — catch explicitly | generic `Exception` |
| Conditional agg in builder | mutable lambda `s -> { if (isL1) s.aggregations(...); return s; }` | always include all aggs; ignore unwanted results in mapping |
| Nested agg building | fluent chain `.nested(...).aggregations(...)` | explicit `new Aggregation.Builder().nested(...).aggregations(...).build()` |
| Percentile result key | `Map<String, String>` keyed by `"50.0"` | `TDigestPercentilesAggregate` — `agg.values().keyed().get("50.0")` |

---

## Tests

| Test | Type | Covers |
|---|---|---|
| `ZeebeAgentInstanceScriptIT` | IT | Painless upsert/merge script correctness for all CREATED/COMPLETED orderings |
| `AgenticControlPlaneUtilsTest` | Unit | Baseline filter builder (5 filter clauses) |
| `ProcessInstanceIndexMappingTest` | Unit | V9 mapping completeness |
| `ZeebePositionBasedImportIndexIT` | IT | Handler count = 12 (6 handlers × 2 partitions) |
