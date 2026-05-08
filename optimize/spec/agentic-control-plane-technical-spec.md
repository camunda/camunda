# Agentic Control Plane — Technical Specification (v3)

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
   - 4.6 [A5 — Duration Histogram](#46-a5--duration-histogram)
   - 4.7 [A6 — Incident Rate](#47-a6--incident-rate)
   - 4.8 [A7 — Agents List](#48-a7--agents-list)
5. [Layer 3 — Frontend](#5-layer-3--frontend)
6. [Out of Scope](#6-out-of-scope)
7. [Migration](#7-migration)

---

## 1. Overview

**Goal**: A new _Agentic Control Plane_ dashboard in Optimize that gives operators visibility into AI agent executions embedded inside Zeebe process instances.

**Scope**: Read-only analytics. No write-back to Zeebe.

**Key constraints** from product Q&A:

| Decision | Details |
|---|---|
| Completed runs only | All metrics are computed over process instances with `state = "COMPLETED"` (parent PI level, not individual agent instance level) |
| Reasoning tokens | Out of scope for phase 1 |
| Tool calls | Single KPI stat (`totalToolCalls`), no per-tool breakdown in phase 1 |
| Status badges | Dropped — use KPI stats block only |
| Layer 4 (settings) | Dropped entirely |
| Token trend | Multi-line: top-5 agents by total tokens + "Other" rollup |
| Incident rate at L2 | Ratio of total incidents on the agent element to total activations of that agent element, across all completed process instances for the process |

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
| `CREATE` | Command | Connector | Connector requests creation of a new agent instance |
| `CREATED` | Event | Engine | Engine assigns `agentInstanceKey`, writes initial record |
| `UPDATE` | Command | Connector | Connector reports status transitions, metric deltas, tool list replacement |
| `UPDATED` | Event | Engine | Engine accumulates metric deltas into running totals, emits updated totals |
| `COMPLETE` | Command | Engine (internal) | Engine-initiated terminal command on process instance completion or cancellation |
| `COMPLETED` | Event | Engine | Terminal event. Record removed from primary storage (RocksDB). Retained in secondary storage |

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

> `definition.systemPrompt` is intentionally excluded from secondary storage — derivable from the process definition XML via `DefinitionService(processDefinitionKey, elementId)`. Storing it would duplicate potentially large strings across every agent instance record.

#### AgentInstanceRecord — Metrics fields

`UPDATE` commands carry **deltas** (increments). `UPDATED`/`COMPLETED` events carry engine-aggregated **running totals**.

| Field | Type | Description |
|---|---|---|
| `metrics.inputTokens` | `long` | Total input tokens across all model calls |
| `metrics.outputTokens` | `long` | Total output tokens across all model calls |
| `metrics.modelCalls` | `int` | Total number of LLM calls |
| `metrics.toolCalls` | `int` | Total number of tool calls |

#### AgentInstanceRecord — Status

| Field | Type | Description |
|---|---|---|
| `status` | `AgentInstanceStatus` | Current lifecycle state |

#### AgentInstanceStatus

| Value | Terminal | Description |
|---|---|---|
| `INITIALIZING` | No | Reading BPMN tool schemas. No LLM call yet |
| `TOOL_DISCOVERY` | No | Performing MCP/A2A tool discovery against external tool servers |
| `THINKING` | No | Calling the LLM |
| `TOOL_CALLING` | No | LLM requested tool calls; tools dispatched |
| `IDLE` | No | Initialized and ready but not actively processing |
| `COMPLETED` | Yes | Owning process instance completed or cancelled. Record removed from primary storage |

> **FAILED status**: Not included in this design. Failures surface as incidents on the owning element instance. May be added in a future iteration as a non-terminal state.

#### AgentInstanceRecord — Tools

Mutable. When provided in an UPDATE command, the entire list is replaced (replace semantics, idempotent under retries).

| Field | Type | Description |
|---|---|---|
| `tools[].name` | `String` | Tool name as visible to the LLM (e.g. `MCP_slack___post_message`) |

#### Timestamp derivation

The `AgentInstanceRecord` does not carry timestamp fields. Importers derive them from record event timestamps:

| Optimize field | Derived from |
|---|---|
| `creationDate` | Timestamp of the `CREATED` event |
| `lastUpdatedDate` | Timestamp of the latest `UPDATED` or `COMPLETED` event |
| `completionDate` | Timestamp of the `COMPLETED` event (null while running) |
| `durationInMs` | `completionDate - creationDate` in ms (null while running) |

#### CREATED event example

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
    "definition": {
      "model": "gpt-4o",
      "provider": "openai"
    },
    "limits": { "maxTokens": 8000, "maxModelCalls": 10, "maxToolCalls": 20 },
    "metrics": { "inputTokens": 0, "outputTokens": 0, "modelCalls": 0, "toolCalls": 0 },
    "tools": []
  }
}
```

#### UPDATED event example

```json
{
  "intent": "UPDATED",
  "key": 2251799813685251,
  "timestamp": 1746451203500,
  "value": {
    "status": "TOOL_CALLING",
    "metrics": { "inputTokens": 512, "outputTokens": 148, "modelCalls": 1, "toolCalls": 1 },
    "tools": [
      { "name": "extract_line_items" },
      { "name": "MCP_ocr___scan_document" }
    ]
  }
}
```

#### COMPLETED event example

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
    agentInstanceKey         keyword    // merge key; used in composite agg (A7)
    elementInstanceKey       keyword
    elementId                keyword    // agent dropdown and groupBy=elementId aggregations
    processDefinitionKey     keyword    // required inside nested scope for filter aggregations
    processDefinitionVersion integer    // required for groupBy=processDefinitionVersion
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

One document per Zeebe cluster would hold threshold configuration for incidents, escalation rate, tool call failures, limit hit rate, and stability drift. Deferred to a future iteration.

---

## 3. Layer 1 — Import Pipeline

### 3.1 Index Mapping Changes

**File**: `optimize/util/optimize-commons/src/main/java/io/camunda/optimize/service/db/schema/index/ProcessInstanceIndex.java`

Bump `VERSION = 8` → `VERSION = 9` and add an `agentInstances` nested field.

#### New field constants

```java
// Agent Instance Fields
public static final String AGENT_INSTANCES            = "agentInstances";
public static final String AGENT_INSTANCE_KEY         = "agentInstanceKey";
public static final String AGENT_ELEMENT_INSTANCE_KEY = "elementInstanceKey";
public static final String AGENT_ELEMENT_ID           = "elementId";
public static final String AGENT_STATUS               = "status";
public static final String AGENT_CREATION_DATE        = "creationDate";
public static final String AGENT_COMPLETION_DATE      = "completionDate";
public static final String AGENT_LAST_UPDATED_DATE    = "lastUpdatedDate";
public static final String AGENT_DURATION_IN_MS       = "durationInMs";
public static final String AGENT_DEFINITION_MODEL     = "definition.model";
public static final String AGENT_DEFINITION_PROVIDER  = "definition.provider";
public static final String AGENT_METRICS_INPUT_TOKENS  = "metrics.inputTokens";
public static final String AGENT_METRICS_OUTPUT_TOKENS = "metrics.outputTokens";
public static final String AGENT_METRICS_MODEL_CALLS   = "metrics.modelCalls";
public static final String AGENT_METRICS_TOOL_CALLS    = "metrics.toolCalls";
public static final String AGENT_TOOLS_NAME            = "tools.name";

// Pre-aggregated at PI level — summed over COMPLETED agent instances only
public static final String AGENT_TOTAL_INPUT_TOKENS   = "agentTotalInputTokens";
public static final String AGENT_TOTAL_OUTPUT_TOKENS  = "agentTotalOutputTokens";
```

> Constants inside the nested object use the bare field name (e.g. `"elementId"`, `"status"`). When used in ES queries they are qualified as `agentInstances.elementId`, `agentInstances.status`, etc.

#### Mapping (add to `addProperties()`)

```java
// In ProcessInstanceIndex.addProperties():
.properties(AGENT_TOTAL_INPUT_TOKENS,  p -> p.long_(k -> k))
.properties(AGENT_TOTAL_OUTPUT_TOKENS, p -> p.long_(k -> k))
.properties(
    AGENT_INSTANCES,
    p -> p.nested(n -> n
        .properties(AGENT_INSTANCE_KEY,          np -> np.keyword(k -> k))
        .properties(AGENT_ELEMENT_INSTANCE_KEY,  np -> np.keyword(k -> k))
        .properties(AGENT_ELEMENT_ID,            np -> np.keyword(k -> k))
        .properties(AGENT_STATUS,                np -> np.keyword(k -> k))
        .properties(AGENT_CREATION_DATE,         np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_COMPLETION_DATE,       np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_LAST_UPDATED_DATE,     np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_DURATION_IN_MS,        np -> np.long_(k -> k))
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

> **Note**: Use `.object()` nesting for `definition`, `metrics`, and `tools` sub-objects. Dot-notation in `.properties()` creates a literal dot-named field, not a nested object.

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

    // inner Fields class (same pattern as IncidentDto.Fields)
    public static final class Fields { ... }
}
```

Add `List<AgentInstanceDto> agentInstances = new ArrayList<>()` to `ProcessInstanceDto`.

#### Migration class

```
optimize/upgrade/src/main/java/io/camunda/optimize/upgrade/steps/schema/
  UpdateProcessInstanceIndexMappingStep_8_9.java
```

Implements `UpgradeStep` (same pattern as existing version migration steps in that package).

---

### 3.2 Painless Update Script

**File**: `optimize/backend/src/main/java/io/camunda/optimize/service/db/repository/script/ZeebeProcessInstanceScriptFactory.java`

This is a Java **interface** with static/private static methods (Java 9+). Add a new static method that produces the agent instance upsert script. The script must **not** reference `flowNodesById` (that variable is set by `createUpdateFlowNodeInstancesScript()` and is out of scope here).

```java
static String createUpdateAgentInstancesScript() {
    return
        // 1. Ensure list exists
        "if (ctx._source.agentInstances == null) { ctx._source.agentInstances = []; }\n" +

        // 2. Dedup by agentInstanceKey — insert new, update existing
        "def existingKeys = new HashSet();\n" +
        "for (def ai : ctx._source.agentInstances) { existingKeys.add(ai.agentInstanceKey); }\n" +
        "for (def newAi : params.agentInstances) {\n" +
        "  if (!existingKeys.contains(newAi.agentInstanceKey)) {\n" +
        "    ctx._source.agentInstances.add(newAi);\n" +
        "  } else {\n" +
        "    for (def ai : ctx._source.agentInstances) {\n" +
        "      if (ai.agentInstanceKey == newAi.agentInstanceKey) {\n" +
        "        ai.status           = newAi.status;\n" +
        "        ai.completionDate   = newAi.completionDate;\n" +
        "        ai.lastUpdatedDate  = newAi.lastUpdatedDate;\n" +
        "        ai.durationInMs     = newAi.durationInMs;\n" +
        "        ai.metrics          = newAi.metrics;\n" +
        "        if (newAi.tools != null) { ai.tools = newAi.tools; }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +

        // 3. Re-aggregate parent-level token totals (COMPLETED agents only)
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

> **Painless context note**: This is an **update script** (`ctx._source`). Use `!= null` guards (not `.empty`). `.empty` guards belong in aggregation scripts (`doc[]` context).

Wire into `createProcessInstanceUpdateScript()`:

```java
static String createProcessInstanceUpdateScript() {
    return createUpdateProcessInstancePropertiesScript()
        + createUpdateFlowNodeInstancesScript()
        + createUpdateIncidentsScript()
        + createUpdateAgentInstancesScript();   // append after incidents
}
```

---

### 3.3 Import Pipeline Classes

Five new classes following the `ZeebeIncidentImportService` pattern:

| Class | Extends / Implements |
|---|---|
| `ZeebeAgentInstanceImportHandler` | `AbstractZeebeImportHandler` |
| `ZeebeAgentInstanceFetcher` | `AbstractZeebeRecordFetcher` |
| `ZeebeAgentInstanceImportService` | `ZeebeProcessInstanceSubEntityImportService<AgentInstanceRecord>` |
| `ZeebeAgentInstanceImportMediator` | `AbstractZeebeImportMediator` |
| `ZeebeAgentInstanceImportMediatorFactory` | `AbstractImportMediatorFactory` |

Key implementation notes for `ZeebeAgentInstanceImportService`:

```java
// Import events only (not commands)
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

// Source export index constant (define in ZeebeIndexConstants or equivalent)
// ZEEBE_AGENT_INSTANCE_INDEX_NAME = "zeebe-record-agent-instance"

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

            List<AgentInstanceDto> agents = entry.getValue().stream()
                .map(hit -> mapToAgentInstanceDto(hit.getValue(), hit.getIntent(), hit.getTimestamp()))
                .toList();

            pi.setAgentInstances(agents);
            return pi;
        })
        .toList();
}
```

> `mapToAgentInstanceDto` derives `creationDate`, `completionDate`, `lastUpdatedDate`, and `durationInMs` from `hit.getTimestamp()` based on the intent (`CREATED` → set `creationDate`; `COMPLETED` → set `completionDate` and compute `durationInMs`).

---

## 4. Layer 2 — Backend API

### 4.1 Endpoint Overview

All endpoints live under `/api/agentic-control-plane/` and accept an optional body for filter params.

| ID | Path | Filter Level | Description |
|---|---|---|---|
| A1 | `GET /process-breakdown` | L0 | All processes with agent runs, with KPIs |
| A2 | `GET /agent-elements` | L1 (processKey required) | Distinct agent elementIds for dropdown |
| A3 | `GET /summary` | L0 / L1 / L2 | Summary KPI stats block |
| A4 | `GET /token-trend` | L0 / L1 / L2 | Token usage over time (multi-line) |
| A5 | `GET /duration-histogram` | L0 / L1 | Duration distribution buckets |
| A6 | `GET /incident-rate` | L0 / L1 / L2 | Incident rate |
| A7 | `GET /agents` | L2 (processKey + elementId required) | Paginated agent instance list |

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

**Baseline query**: All endpoints restrict to `state = "COMPLETED"` at the process instance level.

```json
{ "term": { "state": "COMPLETED" } }
```

---

### 4.2 A1 — Process Breakdown

**Purpose**: L0 view — table/grid showing each process that has had at least one agent instance run, with aggregate KPIs.

**ES/OS query**:

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
          "terms": {
            "field": "processDefinitionKey",
            "size": 500
          },
          "aggs": {
            "processInstanceCount": { "value_count": { "field": "processInstanceId" } },
            "avgDuration":          { "avg":         { "field": "duration" } },
            "totalInputTokens":     { "sum":         { "field": "agentTotalInputTokens" } },
            "totalOutputTokens":    { "sum":         { "field": "agentTotalOutputTokens" } },
            "incident_count": {
              "nested": { "path": "incidents" },
              "aggs": {
                "count": { "value_count": { "field": "incidents.id" } }
              }
            }
          }
        }
      }
    }
  }
}
```

> `DURATION` field is pre-computed on import (ms). No custom Painless script needed for avg/sum.
> `FilterLimitedAggregationUtilES.wrapWithFilterLimitedParentAggregation()` can wrap `by_process` if query-level filter limits are needed.

**Response shape**:

```json
{
  "processes": [
    {
      "processDefinitionKey": "myProcess",
      "processInstanceCount": 1420,
      "avgDurationMs": 18340,
      "durationScope": "process",
      "totalInputTokens": 240000,
      "totalOutputTokens": 185000,
      "incidentCount": 34
    }
  ]
}
```

`durationScope`: `"process"` at L0/L1 (using `DURATION` field), `"agent"` at L2 (using `agentInstances.durationInMs`).

---

### 4.3 A2 — Agent Dropdown

**Purpose**: Populate the agent selector for a chosen process. Returns distinct `elementId` values across completed process instances.

**ES/OS query** (uses composite aggregation + scroller):

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

Use `ElasticsearchCompositeAggregationScroller.consumeAllPages()` with `setPathToAggregation("agent_elements", "element_ids")` to collect all element IDs.

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

**Purpose**: The stat block at the top of the dashboard. Values change based on selected filter level.

**L0 / L1 query** (no `elementId` filter):

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
    "agent_stats": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "totalToolCalls": { "sum": { "field": "agentInstances.metrics.toolCalls" } }
      }
    },
    "incident_stats": {
      "nested": { "path": "incidents" },
      "aggs": {
        "count": { "value_count": { "field": "incidents.id" } }
      }
    }
  }
}
```

**L2 query** (`elementId` selected — filter to process instances where that agent ran):

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
          "aggs": {
            "count": { "value_count": { "field": "incidents.id" } }
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
  "totalRuns": 1420,
  "avgDurationMs": 18340,
  "durationScope": "process",
  "totalInputTokens": 240000,
  "totalOutputTokens": 185000,
  "totalToolCalls": 8820,
  "incidentCount": 34
}
```

`durationScope`: `"process"` at L0/L1, `"agent"` at L2.

---

### 4.5 A4 — Token Trend

**Purpose**: Time-series chart of token usage. At L2, shows multi-line breakdown (top-5 agents + "Other"). At L0/L1, shows a single aggregated line using the pre-computed parent-level fields.

**L0 / L1 query** (single line, using parent fields — no nested drill-down needed):

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
      "date_histogram": {
        "field": "endDate",
        "calendar_interval": "1d"
      },
      "aggs": {
        "totalInput":  { "sum": { "field": "agentTotalInputTokens" } },
        "totalOutput": { "sum": { "field": "agentTotalOutputTokens" } }
      }
    }
  }
}
```

**L2 query** (multi-line, top-5 agents by total tokens):

Step 1 — identify top-5 agent elementIds by total tokens within the time range:

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

Step 2 — for each of the top-5 elementIds, run a date histogram:

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

Run top-5 queries in parallel (5 separate requests). "Other" = L1 total minus sum of top-5.

**Response shape**:

```json
{
  "series": [
    {
      "elementId": "Agent_1abc",
      "label": "Agent_1abc",
      "data": [
        { "date": "2025-01-01", "inputTokens": 4200, "outputTokens": 3100 }
      ]
    },
    {
      "elementId": "__other__",
      "label": "Other",
      "data": [
        { "date": "2025-01-01", "inputTokens": 800, "outputTokens": 600 }
      ]
    }
  ]
}
```

---

### 4.6 A5 — Duration Histogram

**Purpose**: Distribution of durations in configurable buckets (e.g., <1s, 1–5s, 5–30s, >30s). Available at L0 and L1 only (process-level duration).

**Query** (uses `DURATION` pre-computed field — no custom Painless needed):

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
    "duration_hist": {
      "histogram": {
        "field": "duration",
        "interval": 5000,
        "min_doc_count": 0
      }
    }
  }
}
```

> Bucket interval and range are configurable as query parameters. The nested filter ensures only process instances that actually ran agents are included.

**Response shape**:

```json
{
  "durationScope": "process",
  "buckets": [
    { "fromMs": 0,     "toMs": 5000,  "count": 42 },
    { "fromMs": 5000,  "toMs": 10000, "count": 118 },
    { "fromMs": 10000, "toMs": null,  "count": 34 }
  ]
}
```

---

### 4.7 A6 — Incident Rate

**Purpose**: KPI showing incident rate scoped to the active filter level.

- **L0 / L1** (process scope): incidents in process / total process instances with agents
- **L2** (agent scope): agent-element incidents / total agent activations for that element

#### L0 / L1 (no specific agent selected)

Incident rate = process instances with ≥1 incident / total process instances (that had agents).

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
      "aggs": {
        "count": { "value_count": { "field": "processInstanceId" } }
      }
    }
  }
}
```

`incidentRate = has_incidents.count / totalRuns`

#### L2 (specific agent element selected)

Incident rate = total incidents on that agent element / total activations of that agent element, both counted across all completed process instances for the process.

Denominator = `value_count(agentInstanceKey)` filtered by `elementId` (total activations).
Numerator = `value_count(incidents.id)` filtered by `activityId = elementId` (total incidents on that element).

Single query, two separate nested aggs:

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
          "aggs": {
            "incidentCount": { "value_count": { "field": "incidents.id" } }
          }
        }
      }
    }
  }
}
```

`incidentRate = agent_incidents.for_element.incidentCount / agent_runs.for_element.activationCount`

> No `reverse_nested` needed. Outer query stays broad (all completed PIs for that process) — the nested filters inside each agg handle scoping independently.

**Response shape**:

```json
{
  "incidentRate": 0.024,
  "incidentCount": 34,
  "activationCount": 1420
}
```

---

### 4.8 A7 — Agents List

**Purpose**: Paginated table of individual agent instance activations for a selected process + agent element.

**Implementation**: Uses `ElasticsearchCompositeAggregationScroller` (existing utility at `optimize/backend/src/main/java/io/camunda/optimize/service/db/es/ElasticsearchCompositeAggregationScroller.java`).

**Two-request approach** to avoid pagination-correctness bug:

1. **Request 1**: Use `consumeAllPages()` scroller to collect all `agentInstanceKey` values and their metrics for the filter combination. Builds the full in-memory list.
2. **Request 2**: Run incident counts for the collected `elementId` set separately — a single aggregation request with a `terms` filter, not per-page.

> This avoids the pagination-correctness bug where incident counts built from page-1 element IDs would miss elements on later composite pages.

**Request 1 — composite scroller query**:

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
                "status":          { "terms": { "field": "agentInstances.status", "size": 1 } },
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

Use `setPathToAggregation("agent_scope", "for_element", "by_instance")` on the scroller.

**Request 2 — incident counts** (after collecting all `agentInstanceKey` values):

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
              "aggs": {
                "count": { "value_count": { "field": "incidents.id" } }
              }
            }
          }
        }
      }
    }
  }
}
```

Merge incident counts into agent instance rows by `processInstanceId` on the Java side.

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
  "nextCursor": "eyJhZ2VudEluc3RhbmNlS2V5IjoiMjI1MTc5OTgxMzY4ODMyMiJ9"
}
```

`nextCursor`: base64-encoded after-key from the composite aggregation's `after_key`. Null when no more pages.

---

## 5. Layer 3 — Frontend

**Location**: `optimize/client/src/components/AgenticControlPlane/`

**Framework**: React + Carbon Design System (consistent with existing Optimize UI).

### Views

| View | Route | Default filter level |
|---|---|---|
| `ControlPlaneDashboard` | `/agentic-control-plane` | L0 |
| — with process selected | — | L1 |
| — with process + agent | — | L2 |

### Component Structure

```
AgenticControlPlane/
  index.tsx                     Entry / route registration
  ControlPlaneDashboard.tsx     Main dashboard layout
  components/
    ProcessSelector.tsx         Dropdown (uses A1 response)
    AgentSelector.tsx           Dropdown (uses A2 response)
    SummaryKPIs.tsx             Stat block (uses A3)
    TokenTrendChart.tsx         Multi-line chart (uses A4)
    DurationHistogram.tsx       Bar chart (uses A5)
    IncidentRateKPI.tsx         KPI + sparkline (uses A6)
    AgentsList.tsx              Paginated table (uses A7)
  hooks/
    useProcessBreakdown.ts
    useAgentElements.ts
    useSummaryKPIs.ts
    useTokenTrend.ts
    useDurationHistogram.ts
    useIncidentRate.ts
    useAgentsList.ts
```

### Filter propagation

A global `AgentFilterContext` holds `{ processDefinitionKey, elementId, dateRange }`. All hooks read from context. Selecting a process triggers L0→L1 transition; additionally selecting an agent triggers L1→L2.

### Notes

- Token trend chart: render top-5 agent series + "Other" rolled-up series using Carbon `LineChart`.
- Duration histogram: use Carbon `SimpleBarChart` with custom bucket labels.
- Agents list: uses `nextCursor` from A7 response for "load more" pagination (not page-number).
- No status badges rendered — status visible only in agents list table column.

---

## 6. Out of Scope

The following items are explicitly **not** part of this implementation:

- **Reasoning tokens**: Phase 2.
- **Per-tool call breakdown**: Phase 2. Phase 1 delivers only `totalToolCalls` KPI.
- **Layer 4 — Settings page**: Dropped after product review. The `agentic-control-settings` index (threshold configuration per Zeebe cluster) is deferred to a future iteration.
- **Status badges**: Dropped. Agent status visible only in A7 list table.
- **FAILED agent status**: Not included in phase 1. Failures surface as Zeebe incidents on the owning element instance.
- **Write-back / incident resolution**: Read-only.
- **OpenSearch-specific query differences**: Handled at the existing ES/OS abstraction layer (`search/` module) where composite aggregation and nested query builders already have dual implementations.

---

## 7. Migration

### Index version bump

`ProcessInstanceIndex.VERSION`: `8` → `9`

New class:

```
optimize/upgrade/src/main/java/io/camunda/optimize/upgrade/steps/schema/
  UpdateProcessInstanceIndexMappingStep_8_9.java
```

Implements `UpgradeStep`. Adds the new `agentInstances` mapping and the two parent-level token fields (`agentTotalInputTokens`, `agentTotalOutputTokens`) to all existing process instance indices.

No data migration required for existing documents — missing `agentInstances` lists default to empty array; missing token totals default to 0 at aggregation time.

### Import service registration

Register `ZeebeAgentInstanceImportMediatorFactory` in the Spring context alongside existing Zeebe import mediator factories (same location as `ZeebeIncidentImportMediatorFactory`).
