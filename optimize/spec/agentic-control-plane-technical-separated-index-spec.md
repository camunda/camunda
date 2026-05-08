# Agentic Control Plane — Technical Specification (Separate Index Variant)

**Module**: `optimize/`
**Status**: Draft
**Author**: Alexandre Janoni
**Variant**: Agent instances stored in a dedicated `AgentInstanceIndex` — separate from `ProcessInstanceIndex`.

> **Base**: `agentic-control-plane-technical-spec.md` (nested variant). This document records all
> sections that differ. Sections not listed here are identical to the base spec.

---

## Table of Contents

1. [Overview](#1-overview)
2. [Data Model](#2-data-model)
   - 2.1 [Zeebe Record Model](#21-zeebe-record-model)
   - 2.2 [Optimize Index Mapping](#22-optimize-index-mapping)
3. [Layer 1 — Import Pipeline](#3-layer-1--import-pipeline)
   - 3.1 [Index Mapping Changes](#31-index-mapping-changes)
   - 3.2 [Upsert Script](#32-upsert-script)
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
8. [Architectural Tradeoffs vs Nested Variant](#8-architectural-tradeoffs-vs-nested-variant)

---

## 1. Overview

**Goal**: A new _Agentic Control Plane_ dashboard in Optimize that gives operators visibility into AI agent executions embedded inside Zeebe process instances.

**Scope**: Read-only analytics. No write-back to Zeebe.

**Architectural approach — dedicated `AgentInstanceIndex`**:

Each agent instance is stored as a flat top-level document in a dedicated per-process-definition
`AgentInstanceIndex` (`optimize-agent-instance-<processDefinitionKey>`). `ProcessInstanceIndex`
is **completely unchanged** — no VERSION bump, no new fields, no Painless script changes.

Key consequences of this choice:

- **`ProcessInstanceIndex` zero blast radius.** No risk of mapping errors or VERSION-bump regressions
  affecting existing process analytics. The new index is purely additive.
- **No Painless script required.** Agent instance upsert is a standard ES/OS `IndexRequest`
  (CREATED) + partial `UpdateRequest` (UPDATED/COMPLETED). No merge scripting, no token
  re-aggregation side effects.
- **Flat documents — simpler queries.** All agent-scoped aggregations use plain terms, date
  histograms, and percentiles on flat fields. No nested aggregation syntax anywhere in the
  agent query path.
- **Cross-index merges required for some metrics.** L2 incident rate, A3 summary at L2, A10
  failure rate by version, and A1 process breakdown each require two ES/OS requests — one to
  `AgentInstanceIndex`, one to `ProcessInstanceIndex` — merged in the Java service layer.
  This is standard Java; AI tooling handles it reliably.
- **Future "filter agents by parent process condition" is not feasible at scale.** Any widget
  that needs to filter agent data based on a process-level value computed at query time
  (e.g. "agent runs where parent process exceeded SLA") would require an application-level
  join that hits the ES/OS 65k terms query limit at scale. This is not needed in phase 1
  but is a constraint to evaluate before committing to this architecture long-term.

See `agentic-control-plane-impl-plan-comparison.md` for the full tradeoff analysis against
the nested variant.

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
| Duration scope | No agent filter → process duration (ProcessInstanceIndex). Agent selected → that agent's execution time only (AgentInstanceIndex). Label/tooltip changes with filter. |
| Total Runs with agent filter | Always counts process instance runs where that agent was activated at least once. |
| Token trend multi-line | Multi-line (top-5 agents + "Other") when no agent is filtered (L0/L1). Single line when specific agent is selected (L2). |
| Incident rate scope | Process scope = any incident in the process (ProcessInstanceIndex). Agent scope = agent-element incidents only, denominator = agent runs (AgentInstanceIndex). |
| Tool calls | Single `totalToolCalls` KPI in phase 1. Replace with distribution view when Zeebe provides per-tool data. |
| Status badges | Dropped for phase 1. Settings page dropped entirely. |
| Agent details page | Not in scope for phase 1. |

---

## 2. Data Model

### 2.1 Zeebe Record Model

_Identical to base spec. See `agentic-control-plane-technical-spec.md` § 2.1._

---

### 2.2 Optimize Index Mapping

#### ProcessInstanceIndex — **unchanged**

No new fields. No VERSION bump. Zero risk to existing functionality.

#### AgentInstanceIndex — new index (one per process definition key)

**Index name pattern**: `optimize-agent-instance-<processDefinitionKey>`

**Index class**: `optimize/util/optimize-commons/.../service/db/schema/index/AgentInstanceIndex.java`

Each document = one agent instance. Flat structure — no nested types.

```
// Identity
agentInstanceKey         keyword    // document ID; primary merge key
elementInstanceKey       keyword
elementId                keyword    // agent dropdown and groupBy aggregations
processInstanceKey       keyword    // join key for cross-index incident lookups
processDefinitionKey     keyword    // required for per-process query scoping
processDefinitionVersion integer    // required for failure rate by version chart
versionTag               keyword
tenantId                 keyword

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

> `definition`, `metrics`, and `tools` are sub-objects mapped with `.object()`. No nested type needed — each document is a single agent instance so no multi-value nesting per document.

#### Field constants

```java
// optimize/.../service/db/schema/index/AgentInstanceIndex.java
public static final int VERSION = 1;

public static final String AGENT_INSTANCE_KEY          = "agentInstanceKey";
public static final String ELEMENT_INSTANCE_KEY        = "elementInstanceKey";
public static final String ELEMENT_ID                  = "elementId";
public static final String PROCESS_INSTANCE_KEY        = "processInstanceKey";
public static final String PROCESS_DEFINITION_KEY      = "processDefinitionKey";
public static final String PROCESS_DEFINITION_VERSION  = "processDefinitionVersion";
public static final String VERSION_TAG                 = "versionTag";
public static final String TENANT_ID                   = "tenantId";
public static final String STATUS                      = "status";
public static final String CREATION_DATE               = "creationDate";
public static final String COMPLETION_DATE             = "completionDate";
public static final String LAST_UPDATED_DATE           = "lastUpdatedDate";
public static final String DURATION_IN_MS              = "durationInMs";
public static final String DEFINITION_MODEL            = "definition.model";
public static final String DEFINITION_PROVIDER         = "definition.provider";
public static final String METRICS_INPUT_TOKENS        = "metrics.inputTokens";
public static final String METRICS_OUTPUT_TOKENS       = "metrics.outputTokens";
public static final String METRICS_MODEL_CALLS         = "metrics.modelCalls";
public static final String METRICS_TOOL_CALLS          = "metrics.toolCalls";
public static final String TOOLS_NAME                  = "tools.name";
```

#### Mapping

```java
@Override
public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(AGENT_INSTANCE_KEY,         p -> p.keyword(k -> k))
        .properties(ELEMENT_INSTANCE_KEY,        p -> p.keyword(k -> k))
        .properties(ELEMENT_ID,                  p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_KEY,        p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_KEY,      p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION,  p -> p.integer(k -> k))
        .properties(VERSION_TAG,                 p -> p.keyword(k -> k))
        .properties(TENANT_ID,                   p -> p.keyword(k -> k))
        .properties(STATUS,                      p -> p.keyword(k -> k))
        .properties(CREATION_DATE,               p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(COMPLETION_DATE,             p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(LAST_UPDATED_DATE,           p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(DURATION_IN_MS,              p -> p.long_(k -> k))
        .properties("definition", p -> p.object(o -> o
            .properties("model",    op -> op.keyword(k -> k))
            .properties("provider", op -> op.keyword(k -> k))))
        .properties("metrics", p -> p.object(o -> o
            .properties("inputTokens",  op -> op.long_(k -> k))
            .properties("outputTokens", op -> op.long_(k -> k))
            .properties("modelCalls",   op -> op.integer(k -> k))
            .properties("toolCalls",    op -> op.integer(k -> k))))
        .properties("tools", p -> p.object(o -> o
            .properties("name", op -> op.keyword(k -> k))));
}
```

#### DTO

Same `AgentInstanceDto` as base spec. No longer holds `@JsonIgnore processInstanceId` as a nested reference — `processInstanceKey` is a first-class indexed field.

---

## 3. Layer 1 — Import Pipeline

### 3.1 Index Mapping Changes

**ProcessInstanceIndex**: no changes. VERSION stays at 8.

**AgentInstanceIndex** (new):

```
optimize/util/optimize-commons/src/main/java/io/camunda/optimize/service/db/schema/index/
  AgentInstanceIndex.java
```

Follows same abstract base hierarchy as other Optimize indices. Index name constructed as:

```java
public static String constructIndexName(final String processDefinitionKey) {
    return AGENT_INSTANCE_INDEX_PREFIX + processDefinitionKey.toLowerCase(Locale.ENGLISH);
}
```

where `AGENT_INSTANCE_INDEX_PREFIX = "optimize-agent-instance-"`.

---

### 3.2 Upsert Script

No Painless merge script needed. Because `UPDATED` and `COMPLETED` events carry engine-accumulated
running totals, the import service can apply a **partial update** by `agentInstanceKey`.

```java
// CREATED: full document index
client.index(IndexRequest.of(r -> r
    .index(agentInstanceIndex.getIndexName())
    .id(dto.getAgentInstanceKey())
    .document(dto)));

// UPDATED / COMPLETED: partial update — only mutable fields
Map<String, Object> partialDoc = new HashMap<>();
partialDoc.put("status",          dto.getStatus());
partialDoc.put("lastUpdatedDate", dto.getLastUpdatedDate());
partialDoc.put("completionDate",  dto.getCompletionDate());   // null on UPDATED
partialDoc.put("durationInMs",    dto.getDurationInMs());     // null on UPDATED
partialDoc.put("metrics",         dto.getMetrics());
if (dto.getTools() != null && !dto.getTools().isEmpty()) {
    partialDoc.put("tools", dto.getTools());
}

client.update(UpdateRequest.of(r -> r
    .index(agentInstanceIndex.getIndexName())
    .id(dto.getAgentInstanceKey())
    .doc(partialDoc)
    .docAsUpsert(false)));
```

> No Painless script. No token pre-aggregation. No changes to `ZeebeProcessInstanceScriptFactory`.

---

### 3.3 Import Pipeline Classes

| Class | Extends / Implements | Notes |
|---|---|---|
| `ZeebeAgentInstanceImportHandler` | `AbstractZeebeImportHandler` | Routes to import service |
| `ZeebeAgentInstanceFetcher` | `AbstractZeebeRecordFetcher` | Fetches from Zeebe export stream |
| `ZeebeAgentInstanceImportService` | `AbstractImportService<AgentInstanceRecord>` | Writes directly to `AgentInstanceIndex`; **not** a `ZeebeProcessInstanceSubEntityImportService` |
| `ZeebeAgentInstanceImportMediator` | `AbstractZeebeImportMediator` | Orchestrates fetch → import |
| `ZeebeAgentInstanceImportMediatorFactory` | `AbstractImportMediatorFactory` | Spring factory |

```java
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.UPDATED, AgentInstanceIntent.COMPLETED);

@Override
protected void persistEntities(final List<AgentInstanceDto> entities) {
    for (AgentInstanceDto dto : entities) {
        if (AgentInstanceIntent.CREATED.name().equals(dto.getIntent())) {
            agentInstanceWriter.createAgentInstance(dto);
        } else {
            agentInstanceWriter.updateAgentInstance(dto);
        }
    }
}
```

`AgentInstanceWriter` wraps the index/partial-update logic from § 3.2.

> No `createSkeletonProcessInstance` call. No process instance writer involvement.

---

## 4. Layer 2 — Backend API

All endpoints under `/api/agentic-control-plane/`.

**Baseline query**: Endpoints scoped to agent data query `AgentInstanceIndex`. Endpoints needing process-level data (duration, incident count) query `ProcessInstanceIndex` in a separate request and merge results in the Java service layer.

**Cross-index join pattern** (used in A1, A3 L0/L1, A6 L2, A10 L2):

```
Request 1 → AgentInstanceIndex  (agent metrics, runs)
Request 2 → ProcessInstanceIndex (duration, incidents)
Merge in Java by processDefinitionKey / processInstanceKey / processDefinitionVersion
```

**WoW delta**: Same as base spec — parallel query for `[startDate - 7d, endDate - 7d]`.

---

### 4.1 Endpoint Overview

| ID | Path | Filter Level | Description | Index |
|---|---|---|---|---|
| A1 | `GET /process-breakdown` | L0 | Top token consumers by process | Both (two requests) |
| A2 | `GET /agent-elements` | L1 | Agent element dropdown | AgentInstanceIndex |
| A3 | `GET /summary` | L0/L1/L2 | Summary KPI stats with WoW deltas | Both (two requests) |
| A4 | `GET /token-trend` | L0/L1/L2 | Token trend | AgentInstanceIndex |
| A5 | `GET /duration-stats` | L0/L1/L2 | Duration P50/P95 + stability trend | ProcessInstanceIndex (L0/L1) / AgentInstanceIndex (L2) |
| A6 | `GET /incident-rate` | L0/L1/L2 | Incident rate | ProcessInstanceIndex (L0/L1) / Both (L2) |
| A7 | `GET /agents` | L2 | Paginated agent instance list | AgentInstanceIndex + ProcessInstanceIndex (incidents) |
| A8 | `GET /token-outlier-bands` | L0/L1/L2 | Token p5/p50/p95 bands over time | AgentInstanceIndex |
| A9 | `GET /tokens-per-agent-call` | L1/L2 | Avg tokens per model call, per agent element | AgentInstanceIndex |
| A10 | `GET /failure-rate-by-version` | L1/L2 | Incident rate by process version | ProcessInstanceIndex (L1) / Both (L2) |

---

### 4.2 A1 — Process Breakdown

Two requests merged in Java by `processDefinitionKey`.

**Request 1 — AgentInstanceIndex** (token totals + activation count):

```json
{
  "size": 0,
  "aggs": {
    "by_process": {
      "terms": { "field": "processDefinitionKey", "size": 500 },
      "aggs": {
        "agentActivations":  { "value_count": { "field": "agentInstanceKey" } },
        "totalInputTokens":  { "sum": { "field": "metrics.inputTokens" } },
        "totalOutputTokens": { "sum": { "field": "metrics.outputTokens" } }
      }
    }
  }
}
```

**Request 2 — ProcessInstanceIndex** (run counts, process duration, incident counts):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "state": "COMPLETED" } },
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
    "by_process": {
      "terms": { "field": "processDefinitionKey", "size": 500 },
      "aggs": {
        "processInstanceCount": { "value_count": { "field": "processInstanceId" } },
        "avgDuration":          { "avg":         { "field": "duration" } },
        "incident_count": {
          "nested": { "path": "incidents" },
          "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
        }
      }
    }
  }
}
```

> Request 2 still uses the existing `agentInstances` nested field for filtering — but that field is
> no longer present in the separate-index variant. Replace the `nested` filter with a terms-lookup
> or a set of `processDefinitionKey` values from Request 1 results. Simplest: just query all
> COMPLETED process instances and group by processDefinitionKey; filter to only keys that appear in
> the AgentInstanceIndex result set at merge time.

**Response shape**: same as base spec.

---

### 4.3 A2 — Agent Dropdown

Single request to `AgentInstanceIndex`. No nested query needed.

```json
{
  "size": 0,
  "query": {
    "term": { "processDefinitionKey": "<processKey>" }
  },
  "aggs": {
    "element_ids": {
      "composite": {
        "size": 1000,
        "sources": [
          { "elementId": { "terms": { "field": "elementId" } } }
        ]
      }
    }
  }
}
```

Use `ElasticsearchCompositeAggregationScroller.consumeAllPages()`.

**Response shape**: same as base spec.

---

### 4.4 A3 — Summary KPIs

#### L0 / L1

Two requests merged in Java.

**Request 1 — AgentInstanceIndex** (token metrics, tool calls):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "totalActivations":  { "value_count": { "field": "agentInstanceKey" } },
    "totalInputTokens":  { "sum": { "field": "metrics.inputTokens" } },
    "totalOutputTokens": { "sum": { "field": "metrics.outputTokens" } },
    "medianTokens": {
      "percentiles": {
        "script": {
          "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0)"
        },
        "percents": [50]
      }
    },
    "totalToolCalls": { "sum": { "field": "metrics.toolCalls" } }
  }
}
```

**Request 2 — ProcessInstanceIndex** (run count, process duration, incident count):

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
    "totalRuns":    { "value_count": { "field": "processInstanceId" } },
    "avgDuration":  { "avg":         { "field": "duration" } },
    "incident_stats": {
      "nested": { "path": "incidents" },
      "aggs": { "count": { "value_count": { "field": "incidents.id" } } }
    }
  }
}
```

#### L2

Two requests merged in Java.

**Request 1 — AgentInstanceIndex** (agent-scoped metrics):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "totalActivations":  { "value_count": { "field": "agentInstanceKey" } },
    "avgDuration":       { "avg": { "field": "durationInMs" } },
    "totalInputTokens":  { "sum": { "field": "metrics.inputTokens" } },
    "totalOutputTokens": { "sum": { "field": "metrics.outputTokens" } },
    "medianTokens": {
      "percentiles": {
        "script": {
          "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0)"
        },
        "percents": [50]
      }
    },
    "totalToolCalls": { "sum": { "field": "metrics.toolCalls" } }
  }
}
```

**Request 2 — ProcessInstanceIndex** (incident count for element):

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

Single request to `AgentInstanceIndex`. No nested required.

#### L0 / L1 — multi-line (top-5 agents + "Other")

Step 1 — identify top-5 `elementId`s by total tokens:

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "by_element": {
      "terms": {
        "field": "elementId",
        "size": 6,
        "order": { "total_tokens": "desc" }
      },
      "aggs": {
        "total_tokens": {
          "sum": {
            "script": {
              "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0)"
            }
          }
        }
      }
    }
  }
}
```

Step 2 — for each top-5 `elementId`, date histogram (5 parallel requests):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<optional-processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "completionDate", "calendar_interval": "1d" },
      "aggs": {
        "inputTokens":  { "sum": { "field": "metrics.inputTokens" } },
        "outputTokens": { "sum": { "field": "metrics.outputTokens" } }
      }
    }
  }
}
```

"Other" = total L0/L1 sum (separate single request) minus sum of top-5.

#### L2 — single line

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "completionDate", "calendar_interval": "1d" },
      "aggs": {
        "inputTokens":  { "sum": { "field": "metrics.inputTokens" } },
        "outputTokens": { "sum": { "field": "metrics.outputTokens" } }
      }
    }
  }
}
```

**Response shape**: same as base spec.

---

### 4.6 A5 — Duration Stats

- **L0 / L1**: query `ProcessInstanceIndex`, same query as base spec (no change).
- **L2**: query `AgentInstanceIndex` — flat fields, no nested.

#### L2 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "duration_percentiles": {
      "percentiles": { "field": "durationInMs", "percents": [50, 95] }
    },
    "over_time": {
      "date_histogram": { "field": "completionDate", "calendar_interval": "1d" },
      "aggs": {
        "duration_trend": {
          "percentiles": { "field": "durationInMs", "percents": [50, 95] }
        }
      }
    }
  }
}
```

**Response shape**: same as base spec.

---

### 4.7 A6 — Incident Rate

#### L0 / L1

Query `ProcessInstanceIndex` only. Same query as base spec.

#### L2

Two requests merged in Java.

**Request 1 — AgentInstanceIndex** (activation count):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "activationCount": { "value_count": { "field": "agentInstanceKey" } }
  }
}
```

**Request 2 — ProcessInstanceIndex** (incident count for element):

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

`incidentRate = incidentCount / activationCount`

**Response shape**: same as base spec.

---

### 4.8 A7 — Agents List

No composite aggregation scroller needed. Agent instances are first-class documents — use standard
paginated search on `AgentInstanceIndex`.

**Request 1 — AgentInstanceIndex** (paginated instance rows):

```json
{
  "from": 0,
  "size": 100,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "sort": [
    { "creationDate": { "order": "desc" } }
  ]
}
```

Cursor-based pagination: use `search_after` with `creationDate` + `agentInstanceKey` as tiebreaker.

**Request 2 — ProcessInstanceIndex** (incident counts per PI, after scroller completes):

Same query as base spec — nested incidents grouped by `processInstanceKey`.

**Summary stats** (`activationCount`, `successRate`, `incidentRate`): computed from A6 L2 result.
No separate summary query needed.

**Response shape**: same as base spec.

---

### 4.9 A8 — Token Outlier Bands

Single request to `AgentInstanceIndex`. No parent-level pre-aggregated fields needed — each
document is one agent instance, so percentiles operate at the correct granularity directly.

#### L0 / L1 query

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<optional-processKey>" } }
      ]
    }
  },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "completionDate", "calendar_interval": "1d" },
      "aggs": {
        "token_bands": {
          "percentiles": {
            "script": {
              "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0)"
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
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "completionDate", "calendar_interval": "1d" },
      "aggs": {
        "token_bands": {
          "percentiles": {
            "script": {
              "source": "(doc['metrics.inputTokens'].size() > 0 ? doc['metrics.inputTokens'].value : 0) + (doc['metrics.outputTokens'].size() > 0 ? doc['metrics.outputTokens'].value : 0)"
            },
            "percents": [5, 50, 95]
          }
        }
      }
    }
  }
}
```

**Response shape**: same as base spec.

---

### 4.10 A9 — Avg Tokens per Agent Call

Single request to `AgentInstanceIndex`. No nested required.

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } }
      ]
    }
  },
  "aggs": {
    "by_element": {
      "terms": { "field": "elementId", "size": 100 },
      "aggs": {
        "totalInput":      { "sum": { "field": "metrics.inputTokens" } },
        "totalOutput":     { "sum": { "field": "metrics.outputTokens" } },
        "totalModelCalls": { "sum": { "field": "metrics.modelCalls" } },
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
```

At L2, add `{ "term": { "elementId": "<elementId>" } }` to the query. Single bucket result.

**Response shape**: same as base spec.

---

### 4.11 A10 — Failure Rate by Process Version

#### L1 — process scope

Query `ProcessInstanceIndex` only. Same query as base spec.

#### L2 — agent scope

Two requests merged in Java by `processDefinitionVersion`.

**Request 1 — AgentInstanceIndex** (activation count per version):

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "term": { "processDefinitionKey": "<processKey>" } },
        { "term": { "elementId": "<elementId>" } }
      ]
    }
  },
  "aggs": {
    "by_version": {
      "terms": { "field": "processDefinitionVersion", "size": 100 },
      "aggs": {
        "activationCount": { "value_count": { "field": "agentInstanceKey" } }
      }
    }
  }
}
```

**Request 2 — ProcessInstanceIndex** (incident count per version for element):

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

`failureRate[version] = incidentCount[version] / activationCount[version]`

**Response shape**: same as base spec.

---

## 5. Layer 3 — Frontend

_Identical to base spec. No frontend changes required by this architectural variant._

---

## 6. Out of Scope

Same as base spec, plus:

- **Parent-level pre-aggregated token fields** (`agentTotalInputTokens`, `agentTotalOutputTokens`): not needed. Each agent instance is a top-level document so percentile aggregations (A8) operate at the correct granularity without pre-aggregation.
- **Painless update script for token re-aggregation**: not needed.

---

## 7. Migration

### ProcessInstanceIndex

No changes. No VERSION bump.

### AgentInstanceIndex

New index. No migration from existing data — agent instance records only appear on clusters running
Zeebe with `AGENT_INSTANCE` ValueType support. Index is created on first write.

Register `AgentInstanceIndex` in the Optimize index registry alongside existing indices.

Register `ZeebeAgentInstanceImportMediatorFactory` in the Spring context (same location as
`ZeebeIncidentImportMediatorFactory`).

---

## 8. Architectural Tradeoffs vs Nested Variant

| Aspect | Nested variant | Separate index variant |
|---|---|---|
| ProcessInstanceIndex changes | VERSION 8 → 9, new nested field, new parent-level fields | **None** |
| Painless script | Required (merge + token re-aggregation on every agent import) | **Not needed** |
| Pre-aggregated token fields | Required for A8 percentiles | **Not needed** |
| Query complexity | Nested aggs throughout; most queries single request | Most queries single request; L2 incident/version metrics = two requests |
| A7 Agents List pagination | Composite agg scroller (aggregation-based) | Standard `search_after` pagination (simpler) |
| A8 Token Outlier Bands | Needs parent-level fields or scripted_metric | Direct percentile on flat doc (simpler) |
| Incident join (L2) | Single query with two nested aggs | Two requests (AgentInstanceIndex + ProcessInstanceIndex) |
| Risk to existing features | Index change touches ProcessInstanceIndex | **Zero — ProcessInstanceIndex unchanged** |
| Storage overhead | Nested docs inside PI doc | One top-level doc per agent instance |
| Index count | Existing PI indices + 1 per-process | New set of per-process AgentInstanceIndex indices |
