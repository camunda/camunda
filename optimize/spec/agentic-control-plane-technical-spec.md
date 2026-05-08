# Agentic Control Plane — Technical Specification (v4)

**Module**: `optimize/`
**Status**: Draft
**Author**: Alexandre Janoni

---

## Table of Contents

1. [Overview](#1-overview)
2. [Data Model](#2-data-model)
   - 2.1 [Zeebe Record Model](#21-zeebe-record-model)
   - 2.2 [Optimize Index Mapping](#22-optimize-index-mapping)
3. [Layer 1 — Import Pipeline](#3-layer-1--import-pipeline)
   - 3.1 [Index Mapping Changes](#31-index-mapping-changes)
   - 3.2 [Painless Update Script](#32-painless-update-script)
   - 3.3 [Import Pipeline Classes](#33-import-pipeline-classes)
4. [Layer 2 — Backend API](#4-layer-2--backend-api)
   - 4.1 [Endpoint Overview](#41-endpoint-overview)
   - 4.2 [A1 — Process Breakdown](#42-a1--process-breakdown)
   - 4.3 [A2 — Agent Dropdown](#43-a2--agent-dropdown)
   - 4.4 [A3 — Summary KPIs](#44-a3--summary-kpis)
   - 4.5 [A4 — Token Trend](#45-a4--token-trend)
   - 4.6 [A5 — Duration Stats](#46-a5--duration-stats)
   - 4.7 [A6 — Incident Rate](#47-a6--incident-rate)
   - 4.8 [A7 — Agents List](#48-a7--agents-list)
   - 4.9 [A8 — Token Outlier Bands](#49-a8--token-outlier-bands)
   - 4.10 [A9 — Avg Tokens per Agent Call](#410-a9--avg-tokens-per-agent-call)
   - 4.11 [A10 — Failure Rate by Process Version](#411-a10--failure-rate-by-process-version)
5. [Layer 3 — Frontend](#5-layer-3--frontend)
6. [Out of Scope](#6-out-of-scope)
7. [Migration](#7-migration)

---

## 1. Overview

**Goal**: A new _Agentic Control Plane_ dashboard in Optimize that gives operators visibility into AI agent executions embedded inside Zeebe process instances.

**Scope**: Read-only analytics. No write-back to Zeebe.

**Non-goals (phase 1)**:

- Reasoning tokens (phase 2 via Zeebe schema change)
- Per-tool call breakdown / distribution (phase 2 when Zeebe provides per-tool data)
- Status badges (Healthy / Degraded / Failing)
- Agent details panel / click-through from Agents tab
- FAILED agent status enum
- Settings page / threshold configuration
- Write-back or incident resolution
- Real-time / live data

**Key decisions from Q&A** (source of truth):

| Decision | Details |
|---|---|
| Run definition | Process instance run = one process start to end. Agent instance run = one agent invocation, creation to completion. Rate denominators follow metric scope. |
| Completed runs only | All metrics computed over `state = "COMPLETED"` process instances. In-progress = partial values; failures covered by incident rate. |
| Reasoning tokens | Phase 1: input + output only. Phase 2 via Zeebe schema change. No UI caveat needed. |
| Duration scope | No agent filter → process duration. Agent selected → that agent's execution time only. Label/tooltip changes with filter. |
| Total Runs with agent filter | Always counts process instance runs where that agent was activated at least once. |
| Token trend multi-line | Multi-line (top-5 agents + "Other") when no agent is filtered (L0/L1). Single line when specific agent is selected (L2). |
| Incident rate scope | Process scope = any incident in the process (existing join). Agent scope = agent-element incidents only, denominator = agent runs. |
| Tool calls | Single `totalToolCalls` KPI in phase 1. Replace with distribution view when Zeebe provides per-tool data. |
| Status badges | Dropped for phase 1. Settings page dropped entirely. |
| Agent details page | Not in scope for phase 1. |

---

## 2. Data Model

### 2.1 Zeebe Record Model

#### New ValueType

| ValueType | Record class | Public interface | Description |
|---|---|---|---|
| `AGENT_INSTANCE` | `AgentInstanceRecord` | `AgentInstanceRecordValue` | Lifecycle, definition, metrics, and status of a single AI agent execution |

#### AgentInstanceIntent

| Intent | Type | Triggered by | Description |
|---|---|---|---|
| `CREATE` | Command | Connector | Requests creation of a new agent instance |
| `CREATED` | Event | Engine | Assigns `agentInstanceKey`, writes initial record |
| `UPDATE` | Command | Connector | Reports status transitions, metric deltas, tool list replacement |
| `UPDATED` | Event | Engine | Accumulates metric deltas into running totals, emits updated totals |
| `COMPLETE` | Command | Engine (internal) | Engine-initiated terminal command on process instance completion or cancellation |
| `COMPLETED` | Event | Engine | Terminal event. Record removed from primary storage (RocksDB) |

The import pipeline consumes **events** only: `CREATED`, `UPDATED`, `COMPLETED`.

#### AgentInstanceRecord — Identity fields

Set once at CREATED. All fields except `elementInstanceKey` are inferred by the engine.

| Field | Type | Description |
|---|---|---|
| `agentInstanceKey` | `long` | Engine-assigned key. Merge key for Optimize upserts |
| `elementInstanceKey` | `long` | Key of the BPMN element instance that spawned this agent instance |
| `elementId` | `String` | BPMN element ID. Used for agent dropdown and groupBy aggregations |
| `processInstanceKey` | `long` | Owning process instance |
| `processDefinitionKey` | `long` | Process definition key |
| `processDefinitionVersion` | `int` | Process definition version number |
| `versionTag` | `String` | User-defined version tag of the process definition |
| `tenantId` | `String` | Tenant ID |

#### AgentInstanceRecord — Definition fields

Immutable after CREATED. Provided by the connector.

| Field | Type | Description |
|---|---|---|
| `definition.model` | `String` | LLM model identifier (e.g. `gpt-4o`, `claude-sonnet-4-20250514`) |
| `definition.provider` | `String` | LLM provider (e.g. `openai`, `anthropic`) |

> `definition.systemPrompt` excluded from secondary storage — derivable from process definition XML. Storing it duplicates potentially large strings across every record.

#### AgentInstanceRecord — Metrics fields

`UPDATE` commands carry **deltas**. `UPDATED`/`COMPLETED` events carry engine-aggregated **running totals**.

| Field | Type | Description |
|---|---|---|
| `metrics.inputTokens` | `long` | Total input tokens across all model calls |
| `metrics.outputTokens` | `long` | Total output tokens across all model calls |
| `metrics.modelCalls` | `int` | Total number of LLM calls |
| `metrics.toolCalls` | `int` | Total number of tool calls |

#### AgentInstanceRecord — Status

| Value | Terminal | Description |
|---|---|---|
| `INITIALIZING` | No | Reading BPMN tool schemas |
| `TOOL_DISCOVERY` | No | Performing MCP/A2A tool discovery |
| `THINKING` | No | Calling the LLM |
| `TOOL_CALLING` | No | LLM requested tool calls; tools dispatched |
| `IDLE` | No | Initialized and ready but not actively processing |
| `COMPLETED` | Yes | Owning process instance completed or cancelled |

> **FAILED status**: Not included in phase 1. Failures surface as incidents on the owning element instance.

#### Timestamp derivation

The importer derives timestamp fields from record event timestamps:

| Optimize field | Derived from |
|---|---|
| `creationDate` | Timestamp of the `CREATED` event |
| `lastUpdatedDate` | Timestamp of the latest `UPDATED` or `COMPLETED` event |
| `completionDate` | Timestamp of the `COMPLETED` event (null while running) |
| `durationInMs` | `completionDate - creationDate` in ms (null while running) |

#### Event examples

**CREATED**:

```json
{
  "intent": "CREATED",
  "key": 2251799813685251,
  "timestamp": 1746451200000,
  "value": {
    "agentInstanceKey": 2251799813685251,
    "elementInstanceKey": 2251799813685249,
    "elementId": "invoice-data-extraction-agent",
    "processInstanceKey": 2251799813685248,
    "processDefinitionKey": 2251799813685100,
    "processDefinitionId": "my-invoice-process",
    "processDefinitionVersion": 3,
    "versionTag": "v1.2",
    "tenantId": "<default>",
    "status": "INITIALIZING",
    "definition": { "model": "gpt-4o", "provider": "openai" },
    "metrics": { "inputTokens": 0, "outputTokens": 0, "modelCalls": 0, "toolCalls": 0 },
    "tools": []
  }
}
```

**UPDATED**:

```json
{
  "intent": "UPDATED",
  "key": 2251799813685251,
  "timestamp": 1746451203500,
  "value": {
    "status": "TOOL_CALLING",
    "metrics": { "inputTokens": 512, "outputTokens": 148, "modelCalls": 1, "toolCalls": 1 },
    "tools": [{ "name": "extract_line_items" }, { "name": "MCP_ocr___scan_document" }]
  }
}
```

**COMPLETED**:

```json
{
  "intent": "COMPLETED",
  "key": 2251799813685251,
  "timestamp": 1746451207200,
  "value": {
    "status": "COMPLETED",
    "metrics": { "inputTokens": 1340, "outputTokens": 490, "modelCalls": 3, "toolCalls": 2 }
  }
}
```

---

### 2.2 Optimize Index Mapping

#### ProcessInstanceIndex — updated (existing index, new nested field)

```
// Parent-level — maintained by Painless script; required for processDefinitionKey-level token aggregations
agentTotalInputTokens    long
agentTotalOutputTokens   long

agentInstances           nested

    // Identity
    agentInstanceKey         keyword    // merge key; composite agg source (A7)
    elementInstanceKey       keyword
    elementId                keyword    // agent dropdown and groupBy=elementId aggregations
    processDefinitionKey     keyword    // required inside nested scope for filter aggregations
    processDefinitionVersion integer    // required for failure rate by version chart
    versionTag               keyword
    tenantId                 keyword    // required inside nested scope for tenant filtering

    // Status
    // INITIALIZING | TOOL_DISCOVERY | THINKING | TOOL_CALLING | IDLE | COMPLETED
    status                   keyword

    // Timestamps (importer-derived from event timestamps)
    creationDate             date
    completionDate           date?      // null while running
    lastUpdatedDate          date
    durationInMs             long?      // null while running; completionDate - creationDate

    // Definition — immutable after CREATED
    definition.model         keyword
    definition.provider      keyword

    // Metrics — always latest accumulated totals from UPDATED/COMPLETED events
    metrics.inputTokens      long
    metrics.outputTokens     long
    metrics.modelCalls       integer
    metrics.toolCalls        integer

    // Tools — latest list; replaced on each UPDATE
    tools.name               keyword    // repeated; one entry per tool
```

#### agentic-control-settings — out of scope (Layer 4 dropped)

Threshold configuration per Zeebe cluster deferred to a future iteration.

---

## 3. Layer 1 — Import Pipeline

### 3.1 Index Mapping Changes

**File**: `optimize/util/optimize-commons/src/main/java/io/camunda/optimize/service/db/schema/index/ProcessInstanceIndex.java`

Bump `VERSION = 8` → `VERSION = 9` and add an `agentInstances` nested field.

#### New field constants

```java
// Agent Instance Fields
public static final String AGENT_INSTANCES             = "agentInstances";
public static final String AGENT_INSTANCE_KEY          = "agentInstanceKey";
public static final String AGENT_ELEMENT_INSTANCE_KEY  = "elementInstanceKey";
public static final String AGENT_ELEMENT_ID            = "elementId";
public static final String AGENT_STATUS                = "status";
public static final String AGENT_CREATION_DATE         = "creationDate";
public static final String AGENT_COMPLETION_DATE       = "completionDate";
public static final String AGENT_LAST_UPDATED_DATE     = "lastUpdatedDate";
public static final String AGENT_DURATION_IN_MS        = "durationInMs";
public static final String AGENT_DEFINITION_MODEL      = "definition.model";
public static final String AGENT_DEFINITION_PROVIDER   = "definition.provider";
public static final String AGENT_METRICS_INPUT_TOKENS  = "metrics.inputTokens";
public static final String AGENT_METRICS_OUTPUT_TOKENS = "metrics.outputTokens";
public static final String AGENT_METRICS_MODEL_CALLS   = "metrics.modelCalls";
public static final String AGENT_METRICS_TOOL_CALLS    = "metrics.toolCalls";
public static final String AGENT_TOOLS_NAME            = "tools.name";

// Pre-aggregated at PI level — summed over COMPLETED agent instances only
public static final String AGENT_TOTAL_INPUT_TOKENS    = "agentTotalInputTokens";
public static final String AGENT_TOTAL_OUTPUT_TOKENS   = "agentTotalOutputTokens";
```

> Constants use bare field names (e.g. `"elementId"`). In ES queries they are qualified as `agentInstances.elementId` etc.

#### Mapping (add to `addProperties()`)

```java
.properties(AGENT_TOTAL_INPUT_TOKENS,  p -> p.long_(k -> k))
.properties(AGENT_TOTAL_OUTPUT_TOKENS, p -> p.long_(k -> k))
.properties(
    AGENT_INSTANCES,
    p -> p.nested(n -> n
        .properties(AGENT_INSTANCE_KEY,         np -> np.keyword(k -> k))
        .properties(AGENT_ELEMENT_INSTANCE_KEY, np -> np.keyword(k -> k))
        .properties(AGENT_ELEMENT_ID,           np -> np.keyword(k -> k))
        .properties(AGENT_STATUS,               np -> np.keyword(k -> k))
        .properties(AGENT_CREATION_DATE,        np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_COMPLETION_DATE,      np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_LAST_UPDATED_DATE,    np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_DURATION_IN_MS,       np -> np.long_(k -> k))
        .properties("definition", np -> np.object(o -> o
            .properties("model",    op -> op.keyword(k -> k))
            .properties("provider", op -> op.keyword(k -> k))))
        .properties("metrics", np -> np.object(o -> o
            .properties("inputTokens",  op -> op.long_(k -> k))
            .properties("outputTokens", op -> op.long_(k -> k))
            .properties("modelCalls",   op -> op.integer(k -> k))
            .properties("toolCalls",    op -> op.integer(k -> k))))
        .properties("tools", np -> np.object(o -> o
            .properties("name", op -> op.keyword(k -> k))))))
```

> Use `.object()` for `definition`, `metrics`, and `tools` sub-objects. Dot-notation in `.properties()` creates a literal dot-named field, not a nested object.

#### DTO

```java
// optimize/util/optimize-commons/src/main/java/io/camunda/optimize/dto/optimize/persistence/AgentInstanceDto.java
public class AgentInstanceDto implements Serializable, OptimizeDto {
    private String agentInstanceKey;
    private String elementInstanceKey;
    private String elementId;
    private String status;              // INITIALIZING | TOOL_DISCOVERY | THINKING | TOOL_CALLING | IDLE | COMPLETED
    private OffsetDateTime creationDate;
    private OffsetDateTime completionDate;
    private OffsetDateTime lastUpdatedDate;
    private Long durationInMs;          // importer-derived: completionDate - creationDate
    private AgentDefinitionDto definition;
    private AgentMetricsDto metrics;
    private List<AgentToolDto> tools = new ArrayList<>();
    @JsonIgnore private String processInstanceId;
    @JsonIgnore private String engineAlias;

    public static final class Fields { ... }
}
```

Add `List<AgentInstanceDto> agentInstances = new ArrayList<>()` to `ProcessInstanceDto`.

#### Migration class

None required. Old process instances carry no agent data; queries on unmapped/missing nested paths return zero results. The VERSION bump ensures all **new** process instance indices are created with the correct `nested` mapping. Without the bump, ES/OS would auto-map `agentInstances` as `object` on first write, breaking nested aggregations.

---

### 3.2 Painless Update Script

**File**: `optimize/backend/src/main/java/io/camunda/optimize/service/db/repository/script/ZeebeProcessInstanceScriptFactory.java`

Java **interface** with static/private static methods (Java 9+). Must not reference `flowNodesById`.

```java
static String createUpdateAgentInstancesScript() {
    return
        "if (ctx._source.agentInstances == null) { ctx._source.agentInstances = []; }\n" +

        "def existingKeys = new HashSet();\n" +
        "for (def ai : ctx._source.agentInstances) { existingKeys.add(ai.agentInstanceKey); }\n" +
        "for (def newAi : params.agentInstances) {\n" +
        "  if (!existingKeys.contains(newAi.agentInstanceKey)) {\n" +
        "    ctx._source.agentInstances.add(newAi);\n" +
        "  } else {\n" +
        "    for (def ai : ctx._source.agentInstances) {\n" +
        "      if (ai.agentInstanceKey == newAi.agentInstanceKey) {\n" +
        "        ai.status          = newAi.status;\n" +
        "        ai.completionDate  = newAi.completionDate;\n" +
        "        ai.lastUpdatedDate = newAi.lastUpdatedDate;\n" +
        "        ai.durationInMs    = newAi.durationInMs;\n" +
        "        ai.metrics         = newAi.metrics;\n" +
        "        if (newAi.tools != null) { ai.tools = newAi.tools; }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +

        // Re-aggregate parent-level token totals (COMPLETED agents only)
        "long totalIn = 0; long totalOut = 0;\n" +
        "for (def ai : ctx._source.agentInstances) {\n" +
        "  if ('COMPLETED'.equals(ai.status)) {\n" +
        "    if (ai.metrics != null) {\n" +
        "      if (ai.metrics.inputTokens  != null) { totalIn  += ai.metrics.inputTokens; }\n" +
        "      if (ai.metrics.outputTokens != null) { totalOut += ai.metrics.outputTokens; }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "ctx._source.agentTotalInputTokens  = totalIn;\n" +
        "ctx._source.agentTotalOutputTokens = totalOut;\n";
}
```

> Update script context (`ctx._source`): use `!= null` guards, not `.empty`.

Wire into `createProcessInstanceUpdateScript()`:

```java
static String createProcessInstanceUpdateScript() {
    return createUpdateProcessInstancePropertiesScript()
        + createUpdateFlowNodeInstancesScript()
        + createUpdateIncidentsScript()
        + createUpdateAgentInstancesScript();
}
```

---

### 3.3 Import Pipeline Classes

| Class | Extends / Implements |
|---|---|
| `ZeebeAgentInstanceImportHandler` | `AbstractZeebeImportHandler` |
| `ZeebeAgentInstanceFetcher` | `AbstractZeebeRecordFetcher` |
| `ZeebeAgentInstanceImportService` | `ZeebeProcessInstanceSubEntityImportService<AgentInstanceRecord>` |
| `ZeebeAgentInstanceImportMediator` | `AbstractZeebeImportMediator` |
| `ZeebeAgentInstanceImportMediatorFactory` | `AbstractImportMediatorFactory` |

```java
// Import events only (not commands)
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

@Override
protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
    List<HitEntity<AgentInstanceRecord>> hits) {

    return hits.stream()
        .filter(hit -> INTENTS_TO_IMPORT.contains(hit.getIntent()))
        .collect(groupingBy(hit -> hit.getValue().getProcessInstanceKey()))
        .entrySet().stream()
        .map(entry -> {
            String processInstanceId = String.valueOf(entry.getKey());
            AgentInstanceRecord first = entry.getValue().get(0).getValue();
            ProcessInstanceDto pi = createSkeletonProcessInstance(
                String.valueOf(first.getProcessDefinitionKey()),
                processInstanceId,
                String.valueOf(first.getProcessDefinitionKey()),
                first.getTenantId()
            );
            pi.setAgentInstances(entry.getValue().stream()
                .map(hit -> mapToAgentInstanceDto(hit.getValue(), hit.getIntent(), hit.getTimestamp()))
                .toList());
            return pi;
        })
        .toList();
}
```

> `mapToAgentInstanceDto` derives `creationDate`, `completionDate`, `lastUpdatedDate`, and `durationInMs` from `hit.getTimestamp()` based on intent.

---

## 4. Layer 2 — Backend API

### 4.1 Endpoint Overview

All endpoints under `/api/agentic-control-plane/`.

| ID | Path | Filter Level | Description |
|---|---|---|---|
| A1 | `GET /process-breakdown` | L0 | Top token consumers by process |
| A2 | `GET /agent-elements` | L1 | Agent element dropdown |
| A3 | `GET /summary` | L0/L1/L2 | Summary KPI stats with WoW deltas |
| A4 | `GET /token-trend` | L0/L1/L2 | Token trend (multi-line at L0/L1, single at L2) |
| A5 | `GET /duration-stats` | L0/L1/L2 | Duration P50/P95 KPIs + stability trend |
| A6 | `GET /incident-rate` | L0/L1/L2 | Incident rate |
| A7 | `GET /agents` | L2 | Paginated agent instance list |
| A8 | `GET /token-outlier-bands` | L0/L1/L2 | Token p5/p50/p95 bands over time |
| A9 | `GET /tokens-per-agent-call` | L1/L2 | Avg tokens per model call, per agent element |
| A10 | `GET /failure-rate-by-version` | L1/L2 | Incident rate broken down by process version |

**Common filter params**:

```json
{
  "processDefinitionKey": "myProcess",
  "elementId": "Agent_1abc",
  "startDateFrom": "2025-01-01T00:00:00Z",
  "startDateTo":   "2025-12-31T23:59:59Z",
  "after": "cursor-opaque-base64"
}
```

**Baseline query**: All endpoints restrict to `state = "COMPLETED"`.

**WoW delta**: Each endpoint that exposes KPI stats runs the same query for `[startDate - 7d, endDate - 7d]` in parallel and returns the delta as `<field>WoW`.

---

### 4.2 A1 — Process Breakdown

**Purpose**: L0 only. Top token consumers by process, with aggregate KPIs per process.

```json
{
  "size": 0,
  "query": { "term": { "state": "COMPLETED" } },
  "aggs": {
    "has_agents": {
      "filter": {
        "nested": {
          "path": "agentInstances",
          "query": { "exists": { "field": "agentInstances.agentInstanceKey" } }
        }
      },
      "aggs": {
        "by_process": {
          "terms": { "field": "processDefinitionKey", "size": 500 },
          "aggs": {
            "processInstanceCount": { "value_count": { "field": "processInstanceId" } },
            "avgDuration":          { "avg":         { "field": "duration" } },
            "totalInputTokens":     { "sum":         { "field": "agentTotalInputTokens" } },
            "totalOutputTokens":    { "sum":         { "field": "agentTotalOutputTokens" } },
            "incident_count": {
              "nested": { "path": "incidents" },
              "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
            }
          }
        }
      }
    }
  }
}
```

**Response shape**:

```json
{
  "processes": [
    {
      "processDefinitionKey": "myProcess",
      "processInstanceCount": 1420,
      "avgDurationMs": 18340,
      "totalInputTokens": 240000,
      "totalOutputTokens": 185000,
      "incidentCount": 34
    }
  ]
}
```

---

### 4.3 A2 — Agent Dropdown

**Purpose**: L1. Distinct `elementId` values for a chosen process.

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
  "aggs": {
    "agent_elements": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "element_ids": {
          "composite": {
            "size": 1000,
            "sources": [
              { "elementId": { "terms": { "field": "agentInstances.elementId" } } }
            ]
          }
        }
      }
    }
  }
}
```

Use `ElasticsearchCompositeAggregationScroller.consumeAllPages()` with `setPathToAggregation("agent_elements", "element_ids")`.

**Response shape**:

```json
{
  "agentElements": [
    { "elementId": "Agent_1abc", "label": "Agent_1abc" },
    { "elementId": "Agent_2xyz", "label": "Agent_2xyz" }
  ]
}
```

---

### 4.4 A3 — Summary KPIs

**Purpose**: Stat block at the top of the dashboard. Includes WoW deltas (prior 7-day window, parallel request).

**L0 / L1 query**:

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "totalRuns":        { "value_count": { "field": "processInstanceId" } },
    "avgDuration":      { "avg":         { "field": "duration" } },
    "totalInputTokens": { "sum":         { "field": "agentTotalInputTokens" } },
    "totalOutputTokens":{ "sum":         { "field": "agentTotalOutputTokens" } },
    "medianTokens": {
      "percentiles": {
        "script": {
          "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0)"
        },
        "percents": [50]
      }
    },
    "agent_stats": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "totalToolCalls": { "sum": { "field": "agentInstances.metrics.toolCalls" } }
      }
    },
    "incident_stats": {
      "nested": { "path": "incidents" },
      "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
    }
  }
}
```

**L2 query** (filter to PIs where selected agent ran):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
      ]
    }
  },
  "aggs": {
    "totalRuns": { "value_count": { "field": "processInstanceId" } },
    "agent_scope": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
          "aggs": {
            "avgDuration":    { "avg": { "field": "agentInstances.durationInMs" } },
            "totalInput":     { "sum": { "field": "agentInstances.metrics.inputTokens" } },
            "totalOutput":    { "sum": { "field": "agentInstances.metrics.outputTokens" } },
            "medianTokens": {
              "percentiles": {
                "script": {
                  "source": "(doc['agentInstances.metrics.inputTokens'].size() > 0 ? doc['agentInstances.metrics.inputTokens'].value : 0) + (doc['agentInstances.metrics.outputTokens'].size() > 0 ? doc['agentInstances.metrics.outputTokens'].value : 0)"
                },
                "percents": [50]
              }
            },
            "totalToolCalls": { "sum": { "field": "agentInstances.metrics.toolCalls" } }
          }
        }
      }
    },
    "incident_scope": {
      "nested": { "path": "incidents" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "incidents.activityId": "<elementId>" } },
          "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
        }
      }
    }
  }
}
```

**Response shape**:

```json
{
  "totalRuns": 1420,
  "totalRunsWoW": 150,
  "avgDurationMs": 18340,
  "avgDurationMsWoW": -700,
  "durationScope": "process",
  "totalInputTokens": 240000,
  "totalOutputTokens": 185000,
  "medianTokens": 2180,
  "totalToolCalls": 8820,
  "incidentCount": 34
}
```

`durationScope`: `"process"` at L0/L1, `"agent"` at L2.

---

### 4.5 A4 — Token Trend

**Purpose**: Token usage over time.

- **L0/L1** (no agent selected): multi-line, top-5 agents by total tokens + "Other" rollup.
- **L2** (agent selected): single line for that agent.

#### L0 / L1 — multi-line (top-5 agents + "Other")

Step 1 — identify top-5 agent `elementId`s by total tokens:

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "agent_tokens": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "by_element": {
          "terms": {
            "field": "agentInstances.elementId",
            "size": 6,
            "order": { "total_tokens": "desc" }
          },
          "aggs": {
            "total_tokens": {
              "sum": {
                "script": {
                  "source": "(doc['agentInstances.metrics.inputTokens'].size() > 0 ? doc['agentInstances.metrics.inputTokens'].value : 0) + (doc['agentInstances.metrics.outputTokens'].size() > 0 ? doc['agentInstances.metrics.outputTokens'].value : 0)"
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

Step 2 — for each top-5 `elementId`, run a date histogram (5 parallel requests):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
      ]
    }
  },
  "aggs": {
    "agent_time": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
          "aggs": {
            "over_time": {
              "date_histogram": {
                "field": "agentInstances.completionDate",
                "calendar_interval": "1d"
              },
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

"Other" = L0/L1 total (using parent `agentTotalInputTokens`/`agentTotalOutputTokens`) minus sum of top-5.

#### L2 — single line

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
      ]
    }
  },
  "aggs": {
    "agent_time": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
          "aggs": {
            "over_time": {
              "date_histogram": {
                "field": "agentInstances.completionDate",
                "calendar_interval": "1d"
              },
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

**Response shape**:

```json
{
  "series": [
    {
      "elementId": "Agent_1abc",
      "label": "Agent_1abc",
      "data": [{ "date": "2025-01-01", "inputTokens": 4200, "outputTokens": 3100 }]
    },
    {
      "elementId": "__other__",
      "label": "Other",
      "data": [{ "date": "2025-01-01", "inputTokens": 800, "outputTokens": 600 }]
    }
  ]
}
```

At L2, `series` contains one entry (the selected agent). No "Other".

---

### 4.6 A5 — Duration Stats

**Purpose**: Duration P50/P95 KPI stats + execution duration stability over time (p50/p95 trend).

Duration field: `duration` (process-level, pre-computed) at L0/L1. `agentInstances.durationInMs` (nested) at L2.

#### L0 / L1 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "exists": { "field": "agentInstances.agentInstanceKey" } }
          }
        }
      ]
    }
  },
  "aggs": {
    "duration_percentiles": {
      "percentiles": {
        "field": "duration",
        "percents": [50, 95]
      }
    },
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "1d" },
      "aggs": {
        "duration_trend": {
          "percentiles": { "field": "duration", "percents": [50, 95] }
        }
      }
    }
  }
}
```

#### L2 query (agent-scoped duration)

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
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
            "duration_percentiles": {
              "percentiles": { "field": "agentInstances.durationInMs", "percents": [50, 95] }
            },
            "over_time": {
              "date_histogram": {
                "field": "agentInstances.completionDate",
                "calendar_interval": "1d"
              },
              "aggs": {
                "duration_trend": {
                  "percentiles": { "field": "agentInstances.durationInMs", "percents": [50, 95] }
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

**Response shape**:

```json
{
  "durationScope": "process",
  "p50Ms": 3800,
  "p50MsWoW": -200,
  "p95Ms": 8700,
  "p95MsWoW": 400,
  "trend": [
    { "date": "2025-01-01", "p50Ms": 3600, "p95Ms": 8200 }
  ]
}
```

---

### 4.7 A6 — Incident Rate

**Purpose**: KPI showing incident rate scoped to the active filter level.

- **L0/L1**: incidents in process / total process instances with agents
- **L2**: agent-element incidents / total agent activations

#### L0 / L1

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "exists": { "field": "agentInstances.agentInstanceKey" } }
          }
        }
      ]
    }
  },
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
```

`incidentRate = has_incidents.count / totalRuns`

#### L2

Denominator = `value_count(agentInstanceKey)` filtered by `elementId`.
Numerator = `value_count(incidents.id)` filtered by `activityId = elementId`.

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
  "aggs": {
    "agent_runs": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
          "aggs": {
            "activationCount": { "value_count": { "field": "agentInstances.agentInstanceKey" } }
          }
        }
      }
    },
    "agent_incidents": {
      "nested": { "path": "incidents" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "incidents.activityId": "<elementId>" } },
          "aggs": { "incidentCount": { "value_count": { "field": "incidents.id" } } }
        }
      }
    }
  }
}
```

`incidentRate = agent_incidents.for_element.incidentCount / agent_runs.for_element.activationCount`

**Response shape**:

```json
{
  "incidentRate": 0.024,
  "incidentRateWoW": -0.001,
  "incidentCount": 34,
  "activationCount": 1420
}
```

---

### 4.8 A7 — Agents List

**Purpose**: L2. Paginated table of individual agent instance activations. Also computes `successRate` per agent element from the incident join.

**Two-request approach**:

1. `consumeAllPages()` scroller — collects all agent instance rows.
2. Separate incident count request — merges `incidentCount` and `successRate` by `processInstanceId`.

`successRate = 1 - (incidentCount / activationCount)` — derivable from A6 L2 result, no FAILED status needed.

**Request 1 — composite scroller**:

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
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
            "by_instance": {
              "composite": {
                "size": 100,
                "after": { "agentInstanceKey": "<cursor>" },
                "sources": [
                  { "agentInstanceKey": { "terms": { "field": "agentInstances.agentInstanceKey" } } }
                ]
              },
              "aggs": {
                "status":          { "terms": { "field": "agentInstances.status",      "size": 1 } },
                "creationDate":    { "min":   { "field": "agentInstances.creationDate" } },
                "completionDate":  { "max":   { "field": "agentInstances.completionDate" } },
                "durationInMs":    { "max":   { "field": "agentInstances.durationInMs" } },
                "inputTokens":     { "sum":   { "field": "agentInstances.metrics.inputTokens" } },
                "outputTokens":    { "sum":   { "field": "agentInstances.metrics.outputTokens" } },
                "modelCalls":      { "sum":   { "field": "agentInstances.metrics.modelCalls" } },
                "toolCalls":       { "sum":   { "field": "agentInstances.metrics.toolCalls" } },
                "processInstance": { "reverse_nested": {} }
              }
            }
          }
        }
      }
    }
  }
}
```

Use `setPathToAggregation("agent_scope", "for_element", "by_instance")`.

**Request 2 — incident counts per PI** (after scroller completes):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
  "aggs": {
    "incidents_scope": {
      "nested": { "path": "incidents" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "incidents.activityId": "<elementId>" } },
          "aggs": {
            "by_pi": {
              "terms": { "field": "incidents.processInstanceId", "size": 10000 },
              "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
            }
          }
        }
      }
    }
  }
}
```

**Response shape**:

```json
{
  "items": [
    {
      "agentInstanceKey":  "2251799813688321",
      "elementId":         "Agent_1abc",
      "processInstanceId": "2251799813685001",
      "status":            "COMPLETED",
      "creationDate":      "2025-01-15T10:23:00Z",
      "completionDate":    "2025-01-15T10:23:18Z",
      "durationMs":        18340,
      "inputTokens":       1240,
      "outputTokens":      980,
      "modelCalls":        3,
      "toolCalls":         7,
      "incidentCount":     0
    }
  ],
  "summary": {
    "activationCount": 685,
    "successRate": 0.976,
    "incidentRate": 0.024
  },
  "nextCursor": "eyJhZ2VudEluc3RhbmNlS2V5IjoiMjI1MTc5OTgxMzY4ODMyMiJ9"
}
```

`successRate` and `incidentRate` are summary-level (all pages), not per-row.

---

### 4.9 A8 — Token Outlier Bands

**Purpose**: p5/p50/p95 of total tokens (`inputTokens + outputTokens`) per time bucket. Shows token consumption distribution over time.

Uses `agentTotalInputTokens + agentTotalOutputTokens` at parent level (L0/L1). At L2, uses nested `metrics.inputTokens + metrics.outputTokens` for the selected agent.

#### L0 / L1 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "1d" },
      "aggs": {
        "token_bands": {
          "percentiles": {
            "script": {
              "source": "(doc['agentTotalInputTokens'].size() > 0 ? doc['agentTotalInputTokens'].value : 0) + (doc['agentTotalOutputTokens'].size() > 0 ? doc['agentTotalOutputTokens'].value : 0)"
            },
            "percents": [5, 50, 95]
          }
        }
      }
    }
  }
}
```

#### L2 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<elementId>" } }
          }
        }
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
              "date_histogram": {
                "field": "agentInstances.completionDate",
                "calendar_interval": "1d"
              },
              "aggs": {
                "token_bands": {
                  "percentiles": {
                    "script": {
                      "source": "(doc['agentInstances.metrics.inputTokens'].size() > 0 ? doc['agentInstances.metrics.inputTokens'].value : 0) + (doc['agentInstances.metrics.outputTokens'].size() > 0 ? doc['agentInstances.metrics.outputTokens'].value : 0)"
                    },
                    "percents": [5, 50, 95]
                  }
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

**Response shape**:

```json
{
  "bands": [
    { "date": "2025-01-01", "p5": 180, "p50": 1700, "p95": 4200 }
  ]
}
```

---

### 4.10 A9 — Avg Tokens per Agent Call

**Purpose**: L1/L2. Avg tokens consumed per model call, grouped by agent element. Shows which agents are most expensive per LLM invocation.

At L1: all agents for selected process. At L2: scoped to selected agent element (single bar).

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } },
        // L2 only:
        {
          "nested": {
            "path": "agentInstances",
            "query": { "term": { "agentInstances.elementId": "<optional-elementId>" } }
          }
        }
      ]
    }
  },
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
                "buckets_path": {
                  "input":  "totalInput",
                  "output": "totalOutput",
                  "calls":  "totalModelCalls"
                },
                "script": "params.calls > 0 ? (params.input + params.output) / params.calls : 0"
              }
            }
          }
        }
      }
    }
  }
}
```

**Response shape**:

```json
{
  "agents": [
    {
      "elementId": "Agent_1abc",
      "label": "Agent_1abc",
      "avgTokensPerCall": 613,
      "totalModelCalls": 2050
    }
  ]
}
```

---

### 4.11 A10 — Failure Rate by Process Version

**Purpose**: L1/L2. Incident rate broken down by `processDefinitionVersion`. Shows whether agent reliability degrades or improves across deployments.

At L1: any incident in the process per version. At L2: agent-element incidents per version, denominator = agent activations per version.

#### L1 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
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

`failureRate[version] = has_incidents.count / totalRuns`

#### L2 query (agent-scoped per version)

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
  "aggs": {
    "by_version": {
      "terms": { "field": "processDefinitionVersion", "size": 100 },
      "aggs": {
        "agent_runs": {
          "nested": { "path": "agentInstances" },
          "aggs": {
            "for_element": {
              "filter": { "term": { "agentInstances.elementId": "<elementId>" } },
              "aggs": {
                "activationCount": { "value_count": { "field": "agentInstances.agentInstanceKey" } }
              }
            }
          }
        },
        "agent_incidents": {
          "nested": { "path": "incidents" },
          "aggs": {
            "for_element": {
              "filter": { "term": { "incidents.activityId": "<elementId>" } },
              "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
            }
          }
        }
      }
    }
  }
}
```

`failureRate[version] = agent_incidents.for_element.count / agent_runs.for_element.activationCount`

**Response shape**:

```json
{
  "versions": [
    { "version": 1, "failureRate": 0.041, "runs": 380 },
    { "version": 2, "failureRate": 0.028, "runs": 520 },
    { "version": 3, "failureRate": 0.019, "runs": 685 }
  ]
}
```

---

## 5. Layer 3 — Frontend

**Location**: `optimize/client/src/components/AgenticControlPlane/`

**Framework**: React + Carbon Design System.

### Views and chart visibility per filter level

| Chart / Component | L0 | L1 | L2 |
|---|---|---|---|
| Summary KPIs (A3) | ✅ | ✅ | ✅ |
| Top token consumers by process (A1) | ✅ | — | — |
| Token trend — multi-line top-5 (A4) | ✅ | ✅ | — |
| Token trend — single line (A4) | — | — | ✅ |
| Token outlier bands (A8) | ✅ | ✅ | ✅ |
| Avg tokens per agent call (A9) | — | ✅ | ✅ |
| Tool call frequency KPI | ✅ | ✅ | ✅ |
| Failure rate by process version (A10) | — | ✅ | ✅ |
| Duration P50/P95 KPIs (A5) | ✅ | ✅ | ✅ |
| Duration stability trend (A5) | ✅ | ✅ | ✅ |
| Incident rate (A6) | ✅ | ✅ | ✅ |

### Component structure

```
AgenticControlPlane/
  index.tsx
  ControlPlaneDashboard.tsx
  components/
    ProcessSelector.tsx           uses A1
    AgentSelector.tsx             uses A2
    SummaryKPIs.tsx               uses A3
    TokenTrendChart.tsx           uses A4 (multi or single based on filter level)
    TokenOutlierBands.tsx         uses A8
    AvgTokensPerAgentCall.tsx     uses A9 (L1/L2 only)
    DurationStats.tsx             uses A5
    IncidentRateKPI.tsx           uses A6
    FailureRateByVersion.tsx      uses A10 (L1/L2 only)
    AgentsList.tsx                uses A7 (L2 only, Agents tab)
  hooks/
    useProcessBreakdown.ts
    useAgentElements.ts
    useSummaryKPIs.ts
    useTokenTrend.ts
    useTokenOutlierBands.ts
    useAvgTokensPerCall.ts
    useDurationStats.ts
    useIncidentRate.ts
    useFailureRateByVersion.ts
    useAgentsList.ts
```

### Filter propagation

`AgentFilterContext` holds `{ processDefinitionKey, elementId, dateRange }`. Selecting a process triggers L0→L1; additionally selecting an agent triggers L1→L2. Duration label/tooltip updates dynamically (`"Process duration"` vs `"Agent execution time"`).

### Notes

- Token trend: switch between multi-line (A4 L0/L1) and single-line (A4 L2) based on whether `elementId` is set. Cap multi-line at 5 named series + "Other".
- No status badges anywhere in the UI.
- No settings page or "Configure in settings" link.
- Agents list (`AgentsList.tsx`): cursor-based "load more" pagination. Shows `successRate` at list-header level (summary from A7 response), not per-row.

---

## 6. Out of Scope

- **Reasoning tokens**: Phase 2 via Zeebe schema change.
- **Per-tool call breakdown**: Phase 2 when Zeebe provides per-tool data.
- **Layer 4 — Settings / threshold configuration**: Dropped entirely.
- **Status badges** (Healthy / Degraded / Failing): Dropped for phase 1.
- **Agent details panel** (click-through from Agents tab): Not in scope for phase 1.
- **FAILED agent status**: Not in phase 1. Failures surface as Zeebe incidents. SUCCESS % derived from incident join instead.
- **SUCCESS % per-row in Agents list**: Derivable but shown only at summary level in A7 response (`summary.successRate`).
- **Write-back / incident resolution**: Read-only.
- **OpenSearch query differences**: Handled at existing ES/OS abstraction layer (`search/` module).

---

## 7. Migration

### Index version bump

`ProcessInstanceIndex.VERSION`: `8` → `9`

No migration class needed. Old process instances carry no agent data; ES/OS queries on missing nested paths return zero results. The VERSION bump ensures all new process instance indices are created with the correct `nested` mapping for `agentInstances`.

### Import service registration

Register `ZeebeAgentInstanceImportMediatorFactory` in the Spring context alongside existing Zeebe import mediator factories (same location as `ZeebeIncidentImportMediatorFactory`).
