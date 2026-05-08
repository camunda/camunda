# Agentic Control Plane — Technical Specification (v3)

**Module**: `optimize/`  
**Status**: Draft  
**Author**: Alexandre Janoni

---

## Table of Contents

1. [Overview](#1-overview)
2. [Layer 1 — Import Pipeline](#2-layer-1--import-pipeline)
   - 2.1 [Index Mapping Changes](#21-index-mapping-changes)
   - 2.2 [Painless Update Script](#22-painless-update-script)
   - 2.3 [Import Pipeline Classes](#23-import-pipeline-classes)
3. [Layer 2 — Backend API](#3-layer-2--backend-api)
   - 3.1 [Endpoint Overview](#31-endpoint-overview)
   - 3.2 [A1 — Process Breakdown](#32-a1--process-breakdown)
   - 3.3 [A2 — Agent Dropdown](#33-a2--agent-dropdown)
   - 3.4 [A3 — Summary KPIs](#34-a3--summary-kpis)
   - 3.5 [A4 — Token Trend](#35-a4--token-trend)
   - 3.6 [A5 — Duration Histogram](#36-a5--duration-histogram)
   - 3.7 [A6 — Incident Rate](#37-a6--incident-rate)
   - 3.8 [A7 — Agents List](#38-a7--agents-list)
4. [Layer 3 — Frontend](#4-layer-3--frontend)
5. [Out of Scope](#5-out-of-scope)
6. [Migration](#6-migration)

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
| Incident rate at L2 | Fraction of completed process-instance runs (where selected agent was activated) that had at least one incident on the agent element |

---

## 2. Layer 1 — Import Pipeline

### 2.1 Index Mapping Changes

**File**: `optimize/util/optimize-commons/src/main/java/io/camunda/optimize/service/db/schema/index/ProcessInstanceIndex.java`

Bump `VERSION = 8` → `VERSION = 9` and add an `agentInstances` nested field.

#### New field constants

```java
// Agent Instance Fields
public static final String AGENT_INSTANCES = "agentInstances";
public static final String AGENT_INSTANCE_KEY         = "agentInstanceKey";
public static final String AGENT_ELEMENT_ID           = "agentElementId";
public static final String AGENT_STATUS               = "agentStatus";
public static final String AGENT_CREATION_DATE        = "agentCreationDate";
public static final String AGENT_COMPLETION_DATE      = "agentCompletionDate";
public static final String AGENT_DURATION_IN_MS       = "agentDurationInMs";
public static final String AGENT_INPUT_TOKENS         = "agentInputTokens";
public static final String AGENT_OUTPUT_TOKENS        = "agentOutputTokens";
public static final String AGENT_TOOL_CALLS           = "agentToolCalls";

// Pre-aggregated at PI level — summed over COMPLETED agent instances only
public static final String AGENT_TOTAL_INPUT_TOKENS   = "agentTotalInputTokens";
public static final String AGENT_TOTAL_OUTPUT_TOKENS  = "agentTotalOutputTokens";
```

#### Mapping (add to `addProperties()`)

```java
// In ProcessInstanceIndex.addProperties():
.properties(AGENT_TOTAL_INPUT_TOKENS,  p -> p.long_(k -> k))
.properties(AGENT_TOTAL_OUTPUT_TOKENS, p -> p.long_(k -> k))
.properties(
    AGENT_INSTANCES,
    p -> p.nested(n -> n
        .properties(AGENT_INSTANCE_KEY,    np -> np.keyword(k -> k))
        .properties(AGENT_ELEMENT_ID,      np -> np.keyword(k -> k))
        .properties(AGENT_STATUS,          np -> np.keyword(k -> k))
        .properties(AGENT_CREATION_DATE,   np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_COMPLETION_DATE, np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(AGENT_DURATION_IN_MS,  np -> np.long_(k -> k))
        .properties(AGENT_INPUT_TOKENS,    np -> np.long_(k -> k))
        .properties(AGENT_OUTPUT_TOKENS,   np -> np.long_(k -> k))
        .properties(AGENT_TOOL_CALLS,      np -> np.long_(k -> k))
        .properties("definition", np -> np.object(o -> o
            .properties("key",     op -> op.keyword(k -> k))
            .properties("version", op -> op.keyword(k -> k))
            .properties("tenantId",op -> op.keyword(k -> k))))
        .properties("metrics", np -> np.object(o -> o
            .properties("totalTokens",      op -> op.long_(k -> k))
            .properties("completionTimeMs", op -> op.long_(k -> k))))))
```

> **Note**: Use `.object()` nesting for `definition` and `metrics` sub-objects. Dot-notation in `.properties()` creates a literal dot-named field, not a nested object.

#### DTO

```java
// optimize/util/optimize-commons/src/main/java/io/camunda/optimize/dto/optimize/persistence/AgentInstanceDto.java
public class AgentInstanceDto implements Serializable, OptimizeDto {
    private String agentInstanceKey;
    private String agentElementId;
    private String agentStatus;          // "ACTIVATED", "COMPLETED", "TERMINATED"
    private OffsetDateTime agentCreationDate;
    private OffsetDateTime agentCompletionDate;
    private Long agentDurationInMs;
    private Long agentInputTokens;
    private Long agentOutputTokens;
    private Long agentToolCalls;
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

### 2.2 Painless Update Script

**File**: `optimize/backend/src/main/java/io/camunda/optimize/service/db/repository/script/ZeebeProcessInstanceScriptFactory.java`

This is a Java **interface** with static/private static methods (Java 9+). Add a new static method that produces the agent instance upsert script. The script must **not** reference `flowNodesById` (that variable is set by `createUpdateFlowNodeInstancesScript()` and is out of scope here).

```java
static String createUpdateAgentInstancesScript() {
    return
        // 1. Ensure list exists
        "if (ctx._source.agentInstances == null) { ctx._source.agentInstances = []; }\n" +

        // 2. Dedup by agentInstanceKey
        "def existingKeys = new HashSet();\n" +
        "for (def ai : ctx._source.agentInstances) { existingKeys.add(ai.agentInstanceKey); }\n" +
        "for (def newAi : params.agentInstances) {\n" +
        "  if (!existingKeys.contains(newAi.agentInstanceKey)) {\n" +
        "    ctx._source.agentInstances.add(newAi);\n" +
        "  } else {\n" +
        // Update existing entry (status and completion fields may arrive later)
        "    for (def ai : ctx._source.agentInstances) {\n" +
        "      if (ai.agentInstanceKey == newAi.agentInstanceKey) {\n" +
        "        ai.agentStatus         = newAi.agentStatus;\n" +
        "        ai.agentCompletionDate = newAi.agentCompletionDate;\n" +
        "        ai.agentDurationInMs   = newAi.agentDurationInMs;\n" +
        "        ai.agentInputTokens    = newAi.agentInputTokens;\n" +
        "        ai.agentOutputTokens   = newAi.agentOutputTokens;\n" +
        "        ai.agentToolCalls      = newAi.agentToolCalls;\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +

        // 3. Re-aggregate parent-level token totals (COMPLETED agents only)
        "long totalIn = 0; long totalOut = 0;\n" +
        "for (def ai : ctx._source.agentInstances) {\n" +
        "  if ('COMPLETED'.equals(ai.agentStatus)) {\n" +
        "    if (ai.agentInputTokens  != null) { totalIn  += ai.agentInputTokens; }\n" +
        "    if (ai.agentOutputTokens != null) { totalOut += ai.agentOutputTokens; }\n" +
        "  }\n" +
        "}\n" +
        "ctx._source.agentTotalInputTokens  = totalIn;\n" +
        "ctx._source.agentTotalOutputTokens = totalOut;\n";
}
```

> **Painless context note**: This is an **update script** (`ctx._source`). Use `!= null` guards (not `.empty`). `.empty` guards belong in aggregation scripts (`doc[]` context), see `DurationScriptUtilES`.

Wire into `createProcessInstanceUpdateScript()`:

```java
static String createProcessInstanceUpdateScript() {
    return createUpdateProcessInstancePropertiesScript()
        + createUpdateFlowNodeInstancesScript()
        + createUpdateIncidentsScript()
        + createUpdateAgentInstancesScript();   // <-- append after incidents
}
```

---

### 2.3 Import Pipeline Classes

Five new classes following the `ZeebeIncidentImportService` pattern:

| Class | Extends / Implements |
|---|---|
| `ZeebeAgentInstanceImportHandler` | `AbstractZeebeImportHandler` |
| `ZeebeAgentInstanceFetcher` | `AbstractZeebeRecordFetcher` |
| `ZeebeAgentInstanceImportService` | `ZeebeProcessInstanceSubEntityImportService<AgentRecord>` |
| `ZeebeAgentInstanceImportMediator` | `AbstractZeebeImportMediator` |
| `ZeebeAgentInstanceImportMediatorFactory` | `AbstractImportMediatorFactory` |

Key implementation notes for `ZeebeAgentInstanceImportService`:

```java
// Intents to import — adjust based on actual Zeebe AgentIntent values
private static final Set<AgentIntent> INTENTS_TO_IMPORT =
    Set.of(AgentIntent.ACTIVATED, AgentIntent.COMPLETED, AgentIntent.TERMINATED);

// Source export index constant (define in ZeebeIndexConstants or equivalent)
// ZEEBE_AGENT_INDEX_NAME = "zeebe-record-agent"

@Override
protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
    List<HitEntity<AgentRecord>> hits) {

    return hits.stream()
        .filter(hit -> INTENTS_TO_IMPORT.contains(hit.getIntent()))
        .collect(groupingBy(hit -> hit.getValue().getProcessInstanceKey()))
        .entrySet().stream()
        .map(entry -> {
            String processInstanceId = String.valueOf(entry.getKey());
            AgentRecord first = entry.getValue().get(0).getValue();

            ProcessInstanceDto pi = createSkeletonProcessInstance(
                String.valueOf(first.getProcessDefinitionKey()),  // definitionKey in Zeebe = long
                processInstanceId,
                String.valueOf(first.getProcessDefinitionKey()),  // processDefinitionId in Optimize
                first.getTenantId()
            );

            List<AgentInstanceDto> agents = entry.getValue().stream()
                .map(hit -> mapToAgentInstanceDto(hit.getValue(), hit.getIntent()))
                .toList();

            pi.setAgentInstances(agents);
            return pi;
        })
        .toList();
}
```

---

## 3. Layer 2 — Backend API

### 3.1 Endpoint Overview

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
  "agentElementId": "Agent_1abc",
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

### 3.2 A1 — Process Breakdown

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

`durationScope`: `"process"` at L0/L1 (using `DURATION` field), `"agent"` at L2 (using `agentDurationInMs`).

---

### 3.3 A2 — Agent Dropdown

**Purpose**: Populate the agent selector for a chosen process. Returns distinct `agentElementId` values across completed process instances.

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
              { "agentElementId": { "terms": { "field": "agentInstances.agentElementId" } } }
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
    { "agentElementId": "Agent_1abc", "label": "Agent_1abc" },
    { "agentElementId": "Agent_2xyz", "label": "Agent_2xyz" }
  ]
}
```

---

### 3.4 A3 — Summary KPIs

**Purpose**: The stat block at the top of the dashboard. Values change based on selected filter level.

**L0 / L1 query** (no `agentElementId` filter):

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
        "totalToolCalls": { "sum": { "field": "agentInstances.agentToolCalls" } }
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

**L2 query** (`agentElementId` selected — filter to process instances where that agent ran):

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
            "query": { "term": { "agentInstances.agentElementId": "<agentElementId>" } }
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
          "filter": { "term": { "agentInstances.agentElementId": "<agentElementId>" } },
          "aggs": {
            "avgDuration":    { "avg": { "field": "agentInstances.agentDurationInMs" } },
            "totalInput":     { "sum": { "field": "agentInstances.agentInputTokens" } },
            "totalOutput":    { "sum": { "field": "agentInstances.agentOutputTokens" } },
            "totalToolCalls": { "sum": { "field": "agentInstances.agentToolCalls" } }
          }
        }
      }
    },
    "incident_scope": {
      "nested": { "path": "incidents" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "incidents.activityId": "<agentElementId>" } },
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

### 3.5 A4 — Token Trend

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
            "field": "agentInstances.agentElementId",
            "size": 6,
            "order": { "total_tokens": "desc" }
          },
          "aggs": {
            "total_tokens": {
              "sum": {
                "script": {
                  "source": "(doc['agentInstances.agentInputTokens'].size() > 0 ? doc['agentInstances.agentInputTokens'].value : 0) + (doc['agentInstances.agentOutputTokens'].size() > 0 ? doc['agentInstances.agentOutputTokens'].value : 0)"
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
            "query": { "term": { "agentInstances.agentElementId": "<elementId>" } }
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
          "filter": { "term": { "agentInstances.agentElementId": "<elementId>" } },
          "aggs": {
            "over_time": {
              "date_histogram": {
                "field": "agentInstances.agentCompletionDate",
                "calendar_interval": "1d"
              },
              "aggs": {
                "inputTokens":  { "sum": { "field": "agentInstances.agentInputTokens" } },
                "outputTokens": { "sum": { "field": "agentInstances.agentOutputTokens" } }
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
      "agentElementId": "Agent_1abc",
      "label": "Agent_1abc",
      "data": [
        { "date": "2025-01-01", "inputTokens": 4200, "outputTokens": 3100 }
      ]
    },
    {
      "agentElementId": "__other__",
      "label": "Other",
      "data": [
        { "date": "2025-01-01", "inputTokens": 800, "outputTokens": 600 }
      ]
    }
  ]
}
```

---

### 3.6 A5 — Duration Histogram

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
        { "nested": {
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

### 3.7 A6 — Incident Rate

**Purpose**: KPI and chart showing the fraction of runs that had at least one incident.

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

Incident rate = distinct process instances with ≥1 incident on `activityId = agentElementId` / total process instances where that agent ran.

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
            "query": { "term": { "agentInstances.agentElementId": "<agentElementId>" } }
          }
        }
      ]
    }
  },
  "aggs": {
    "totalRuns": { "value_count": { "field": "processInstanceId" } },
    "incidents_for_agent": {
      "nested": { "path": "incidents" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "incidents.activityId": "<agentElementId>" } },
          "aggs": {
            "pis_with_incident": {
              "reverse_nested": {},
              "aggs": {
                "count": { "value_count": { "field": "processInstanceId" } }
              }
            }
          }
        }
      }
    }
  }
}
```

`incidentRate = incidents_for_agent.for_element.pis_with_incident.count / totalRuns`

> `reverse_nested.doc_count` gives the count of distinct parent process instance documents that had at least one matching incident — this is the correct numerator for "fraction of activations with an incident".

**Response shape**:

```json
{
  "incidentRate": 0.024,
  "runsWithIncident": 34,
  "totalRuns": 1420
}
```

---

### 3.8 A7 — Agents List

**Purpose**: Paginated table of individual agent instance activations for a selected process + agent element.

**Implementation**: Uses `ElasticsearchCompositeAggregationScroller` (existing utility at `optimize/backend/src/main/java/io/camunda/optimize/service/db/es/ElasticsearchCompositeAggregationScroller.java`).

**Two-request approach** to avoid pagination-correctness bug:

1. **Request 1**: Use `consumeAllPages()` scroller to collect all `agentInstanceKey` values and their metrics for the filter combination. Builds the full in-memory list.
2. **Request 2**: Run incident counts for the collected `agentElementId` set separately — a single aggregation request with a `terms` filter, not per-page.

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
            "query": { "term": { "agentInstances.agentElementId": "<agentElementId>" } }
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
          "filter": { "term": { "agentInstances.agentElementId": "<agentElementId>" } },
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
                "status":           { "terms":       { "field": "agentInstances.agentStatus", "size": 1 } },
                "creationDate":     { "min":         { "field": "agentInstances.agentCreationDate" } },
                "completionDate":   { "max":         { "field": "agentInstances.agentCompletionDate" } },
                "durationInMs":     { "max":         { "field": "agentInstances.agentDurationInMs" } },
                "inputTokens":      { "sum":         { "field": "agentInstances.agentInputTokens" } },
                "outputTokens":     { "sum":         { "field": "agentInstances.agentOutputTokens" } },
                "toolCalls":        { "sum":         { "field": "agentInstances.agentToolCalls" } },
                "processInstance":  { "reverse_nested": {} }
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
          "filter": { "term": { "incidents.activityId": "<agentElementId>" } },
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
      "agentInstanceKey":   "2251799813688321",
      "agentElementId":     "Agent_1abc",
      "processInstanceId":  "2251799813685001",
      "activationCount":    1,
      "agentStatus":        "COMPLETED",
      "creationDate":       "2025-01-15T10:23:00Z",
      "completionDate":     "2025-01-15T10:23:18Z",
      "durationMs":         18340,
      "inputTokens":        1240,
      "outputTokens":       980,
      "toolCalls":          7,
      "incidentCount":      0
    }
  ],
  "nextCursor": "eyJhZ2VudEluc3RhbmNlS2V5IjoiMjI1MTc5OTgxMzY4ODMyMiJ9"
}
```

`nextCursor`: base64-encoded after-key from the composite aggregation's `after_key`. Null when no more pages.

---

## 4. Layer 3 — Frontend

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

A global `AgentFilterContext` holds `{ processDefinitionKey, agentElementId, dateRange }`. All hooks read from context. Selecting a process triggers L0→L1 transition; additionally selecting an agent triggers L1→L2.

### Notes

- Token trend chart: render top-5 agent series + "Other" rolled-up series using Carbon `LineChart`.
- Duration histogram: use Carbon `SimpleBarChart` with custom bucket labels.
- Agents list: uses `nextCursor` from A7 response for "load more" pagination (not page-number).
- No status badges rendered — status visible only in agents list table column.

---

## 5. Out of Scope

The following items are explicitly **not** part of this implementation:

- **Reasoning tokens**: Phase 2.
- **Per-tool call breakdown**: Phase 2. Phase 1 delivers only `totalToolCalls` KPI.
- **Layer 4 — Settings page**: Dropped after product review. No configuration UI.
- **Status badges**: Dropped. Agent status visible only in A7 list table.
- **Write-back / incident resolution**: Read-only.
- **OpenSearch-specific query differences**: Should be handled at the existing ES/OS abstraction layer (`search/` module) where composite aggregation and nested query builders already have dual implementations.

---

## 6. Migration

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