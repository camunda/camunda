# Agentic Control Plane — Technical Specification (v2)

**Module**: `optimize/`
**Status**: Implementation-ready
**Supersedes**: `agentic-control-plane-technical-spec.md` (v4)

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
   - 4.2 [Common Filter Params](#42-common-filter-params)
   - 4.3 [Multi-tenancy](#43-multi-tenancy)
   - 4.4 [A1 — Process Breakdown](#44-a1--process-breakdown)
   - 4.5 [A3 — Summary KPIs](#45-a3--summary-kpis)
   - 4.6 [A4 — Token Trend](#46-a4--token-trend)
   - 4.7 [A5 — Duration Stats](#47-a5--duration-stats)
   - 4.8 [A6 — Incident Rate](#48-a6--incident-rate)
   - 4.9 [A8 — Token Outlier Bands](#49-a8--token-outlier-bands)
   - 4.10 [A9 — Avg Tokens per Agent Call](#410-a9--avg-tokens-per-agent-call)
   - 4.11 [A10 — Failure Rate by Process Version](#411-a10--failure-rate-by-process-version)
   - 4.12 [A2 — Agent Dropdown (Phase 2)](#412-a2--agent-dropdown-phase-2)
   - 4.13 [A7 — Agents List (Phase 2)](#413-a7--agents-list-phase-2)
5. [Layer 3 — Frontend](#5-layer-3--frontend)
6. [Out of Scope](#6-out-of-scope)
7. [Migration](#7-migration)

---

## 1. Overview

**Goal**: A new _Agentic Control Plane_ dashboard in Optimize giving operators visibility into AI
agent executions embedded inside Zeebe process instances.

**Scope**: Read-only analytics. No write-back to Zeebe.

**Architectural approach — nested in `ProcessInstanceIndex`**:

Agent instance data is stored as a `nested` field (`agentInstances`) inside the existing
`ProcessInstanceIndex` document. This is the same pattern used for `flowNodeInstances`,
`incidents`, and `variables`.

Key consequences:

- **Process and agent data are co-located.** Future analytics correlating agent metrics with
  process-level conditions are single-index queries — no cross-index join required.
- **Existing process filters apply automatically.** `tenantId`, `processDefinitionKey`, date
  range, and all process-level filters work against agent data with no additional implementation.
- **`ProcessInstanceIndex` VERSION bump required.** Bump `8 → 9` before activating the import
  pipeline. Auto-mapping `agentInstances` as `object` on first write silently breaks nested
  aggregations — the VERSION bump prevents this.
- **Painless script required.** Agent instance upsert-by-key plus re-aggregation of parent-level
  token totals is the highest-risk task in the implementation.
- **Nested object limit.** ES/OS defaults to 10,000 nested objects per document. Configure
  `index.mapping.nested_objects.limit = 50000` on the ProcessInstanceIndex if high agent
  invocation volume is expected. Monitor for silent data drops.

**Phase 1 scope**: L0 (fleet, no process selected) and L1 (single process selected).

**Phase 2 scope**: L2 (single agent element selected), agent dropdown (A2), agents list (A7).
All Phase 2 items are explicitly marked throughout this document.

**Non-goals (Phase 1)**:

- L2 agent-scoped queries
- Agent dropdown / agent selector (A2)
- Paginated agents list (A7)
- Reasoning tokens (Phase 2 via Zeebe schema change)
- Per-tool call breakdown
- Status badges (Healthy / Degraded / Failing)
- Agent details panel
- FAILED agent status
- Settings page / threshold configuration
- Write-back or incident resolution
- Real-time / live data

**Key decisions**:

|        Decision         |                                                                             Details                                                                             |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Completed runs only     | All metrics computed over `state = "COMPLETED"` process instances.                                                                                              |
| Baseline filter         | All Phase 1 endpoints restrict to process instances that have at least one agent instance (`has_agents` filter). The dashboard is exclusively for agentic runs. |
| Import events           | CREATED + COMPLETED only. UPDATED added in Phase 2 for live agent status.                                                                                       |
| Incident rate scope     | L0/L1 = any incident in the process (existing incident data). L2 = agent-element incidents only, denominator = agent activations.                               |
| Token trend multi-line  | Multi-line (top-5 `elementId`s + "Other") at L0/L1. Single line at L2 (Phase 2).                                                                                |
| Tool calls              | Single `totalToolCalls` KPI in Phase 1. Distribution view when Zeebe provides per-tool data.                                                                    |
| Process name resolution | Frontend resolves process names using the existing process definition endpoint — not returned by A1.                                                            |
| WoW delta window        | Rolling: prior 7-day window = `[startDate − 7d, endDate − 7d]`. Parallel request.                                                                               |
| Duration field          | L0/L1 uses parent-level `duration` (process duration, pre-computed). L2 uses `agentInstances.durationInMs` (Phase 2).                                           |
| `durationScope` field   | Omitted from Phase 1 responses — always "process" at L0/L1. Added in Phase 2.                                                                                   |

---

## 2. Data Model

### 2.1 Zeebe Record Model

#### New ValueType

|    ValueType     |     Record class      |      Public interface      |                                Description                                |
|------------------|-----------------------|----------------------------|---------------------------------------------------------------------------|
| `AGENT_INSTANCE` | `AgentInstanceRecord` | `AgentInstanceRecordValue` | Lifecycle, definition, metrics, and status of a single AI agent execution |

#### AgentInstanceIntent

|   Intent    |  Type   |   Triggered by    |                                          Description                                          |
|-------------|---------|-------------------|-----------------------------------------------------------------------------------------------|
| `CREATE`    | Command | Connector         | Requests creation of a new agent instance                                                     |
| `CREATED`   | Event   | Engine            | Assigns `agentInstanceKey`, writes initial record. **Imported in Phase 1.**                   |
| `UPDATE`    | Command | Connector         | Reports status transitions, metric deltas, tool list replacement                              |
| `UPDATED`   | Event   | Engine            | Accumulates metric deltas into running totals, emits updated totals. **Imported in Phase 2.** |
| `COMPLETE`  | Command | Engine (internal) | Engine-initiated terminal command on process completion or cancellation                       |
| `COMPLETED` | Event   | Engine            | Terminal event. Carries final metric totals and final tool list. **Imported in Phase 1.**     |

Optimize imports **events** only (not commands). Phase 1: `CREATED` + `COMPLETED`.

#### AgentInstanceRecord — Identity fields

Set once at CREATED. All fields except `elementInstanceKey` are inferred by the engine.

|           Field            |   Type   | Default |                                                 Description                                                 |
|----------------------------|----------|---------|-------------------------------------------------------------------------------------------------------------|
| `agentInstanceKey`         | `long`   | `-1`    | Engine-assigned key. Merge key for Optimize upserts.                                                        |
| `elementInstanceKey`       | `long`   | `-1`    | Key of the BPMN element instance that spawned this agent instance                                           |
| `elementId`                | `String` | `""`    | BPMN element ID — used for agent groupBy aggregations                                                       |
| `processInstanceKey`       | `long`   | `-1`    | Owning process instance                                                                                     |
| `processDefinitionKey`     | `long`   | `-1`    | Process definition key                                                                                      |
| `bpmnProcessId`            | `String` | `""`    | BPMN process ID (`id` attribute on `<process>`). Used for name resolution and definition-level correlation. |
| `processDefinitionVersion` | `int`    | `-1`    | Process definition version number                                                                           |
| `versionTag`               | `String` | `""`    | User-defined version tag                                                                                    |
| `tenantId`                 | `String` | `""`    | Tenant ID                                                                                                   |

#### AgentInstanceRecord — Definition fields

Immutable after CREATED.

|         Field         |   Type   |                           Description                            |
|-----------------------|----------|------------------------------------------------------------------|
| `definition.model`    | `String` | LLM model identifier (e.g. `gpt-4o`, `claude-sonnet-4-20250514`) |
| `definition.provider` | `String` | LLM provider (e.g. `openai`, `anthropic`)                        |

> `definition.systemPrompt` is excluded from secondary storage — it is potentially large and
> derivable from the process definition XML.

#### AgentInstanceRecord — Metrics fields

`UPDATE` commands carry **deltas**. `UPDATED` and `COMPLETED` events carry engine-aggregated
**running totals**. Optimize reads totals only (from `COMPLETED` events in Phase 1).

|         Field          |  Type  |                Description                 |
|------------------------|--------|--------------------------------------------|
| `metrics.inputTokens`  | `long` | Total input tokens across all model calls  |
| `metrics.outputTokens` | `long` | Total output tokens across all model calls |
| `metrics.modelCalls`   | `int`  | Total number of LLM calls                  |
| `metrics.toolCalls`    | `int`  | Total number of tool calls                 |

#### AgentInstanceRecord — Status

|      Value       | Terminal |                    Description                    |
|------------------|----------|---------------------------------------------------|
| `INITIALIZING`   | No       | Reading BPMN tool schemas                         |
| `TOOL_DISCOVERY` | No       | Performing MCP/A2A tool discovery                 |
| `THINKING`       | No       | Calling the LLM                                   |
| `TOOL_CALLING`   | No       | LLM requested tool calls; tools dispatched        |
| `IDLE`           | No       | Initialized and ready but not actively processing |
| `COMPLETED`      | Yes      | Owning process instance completed or cancelled    |

> **FAILED status**: Not included in Phase 1. Failures surface as incidents on the owning element
> instance. `IDLE` is used as a workaround for any non-terminal interrupted state. A `FAILED`
> status may be added in Phase 2 as non-terminal (operator can resolve the underlying incident
> and resume).

#### AgentInstanceRecord — Tools

`COMPLETED` events carry the final tool list. When provided in an `UPDATE` command, the entire
list is **replaced** (not merged). Replace semantics are idempotent under retries.

|         Field         |   Type   |                     Description                     |
|-----------------------|----------|-----------------------------------------------------|
| `tools[].name`        | `String` | Tool name as visible to the LLM                     |
| `tools[].description` | `String` | Human-readable description                          |
| `tools[].elementId`   | `String` | BPMN element ID modelling this tool (if applicable) |

#### Timestamp derivation

Optimize derives these fields from Zeebe event timestamps — they are **not** part of the engine
record.

|  Optimize field   |                          Derived from                          |
|-------------------|----------------------------------------------------------------|
| `creationDate`    | Timestamp of the `CREATED` event                               |
| `lastUpdatedDate` | Timestamp of the latest `UPDATED` or `COMPLETED` event         |
| `completionDate`  | Timestamp of the `COMPLETED` event (null while in-progress)    |
| `durationInMs`    | `completionDate − creationDate` in ms (null while in-progress) |

> **Cross-batch duration computation**: Since `CREATED` and `COMPLETED` events typically arrive
> in separate import batches, the import service stores `creationDateEpochMs` (a `long`) in the
> nested doc alongside the ISO `creationDate`. The Painless update script computes `durationInMs`
> from the stored epoch millis when the `COMPLETED` event arrives, without needing to parse date
> strings. See §3.2 for the script and §3.1 for the extra field constant.

#### Event examples

**CREATED** (all identity fields set by engine):

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
    "bpmnProcessId": "my-invoice-process",
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

**COMPLETED** (carries final accumulated totals AND final tool list):

```json
{
  "intent": "COMPLETED",
  "key": 2251799813685251,
  "timestamp": 1746451207200,
  "value": {
    "agentInstanceKey": 2251799813685251,
    "elementInstanceKey": 2251799813685249,
    "elementId": "invoice-data-extraction-agent",
    "processInstanceKey": 2251799813685248,
    "processDefinitionKey": 2251799813685100,
    "bpmnProcessId": "my-invoice-process",
    "processDefinitionVersion": 3,
    "versionTag": "v1.2",
    "tenantId": "<default>",
    "status": "COMPLETED",
    "definition": { "model": "gpt-4o", "provider": "openai" },
    "metrics": { "inputTokens": 1340, "outputTokens": 490, "modelCalls": 3, "toolCalls": 2 },
    "tools": [
      { "name": "extract_line_items", "elementId": "extract-task", "description": "Extracts line items from invoice" },
      { "name": "MCP_ocr___scan_document", "elementId": "MCP_ocr", "description": "OCR scan via MCP" }
    ]
  }
}
```

---

### 2.2 Optimize Index Mapping

#### ProcessInstanceIndex — updated (existing index, new nested field)

```
// Parent-level — maintained by Painless script; required for process-level token aggregations
agentTotalInputTokens    long
agentTotalOutputTokens   long

agentInstances           nested

    // Identity
    agentInstanceKey         keyword    // merge key
    elementInstanceKey       keyword
    elementId                keyword    // groupBy aggregations
    bpmnProcessId            keyword    // Phase 2: process-level correlation
    processDefinitionKey     keyword    // Phase 2: filter aggregations inside nested scope
    processDefinitionVersion integer    // Phase 2: failure rate by version (nested scope)
    versionTag               keyword
    tenantId                 keyword    // Phase 2: tenant filtering inside nested scope

    // Status
    status                   keyword    // INITIALIZING|TOOL_DISCOVERY|THINKING|TOOL_CALLING|IDLE|COMPLETED

    // Timestamps (importer-derived)
    creationDate             date       // ISO format
    completionDate           date       // null while running
    lastUpdatedDate          date
    durationInMs             long       // null while running
    creationDateEpochMs      long       // auxiliary: enables Painless durationInMs computation across batches

    // Definition — immutable after CREATED
    definition.model         keyword
    definition.provider      keyword

    // Metrics — final accumulated totals from COMPLETED event
    metrics.inputTokens      long
    metrics.outputTokens     long
    metrics.modelCalls       integer
    metrics.toolCalls        integer

    // Tools — final list from COMPLETED event
    tools.name               keyword
```

> `processDefinitionKey`, `processDefinitionVersion`, `tenantId`, `bpmnProcessId` inside
> `agentInstances` are Phase 2 reserved. They must be added to the mapping now (they are present
> on every CREATED event) to avoid a future VERSION bump. They are not used in Phase 1 queries.

---

## 3. Layer 1 — Import Pipeline

### 3.1 Index Mapping Changes

**File**: `optimize/util/optimize-commons/src/main/java/io/camunda/optimize/service/db/schema/index/ProcessInstanceIndex.java`

Bump `VERSION = 8` → `VERSION = 9`. Add constants and nested mapping.

#### New field constants

```java
// Parent-level pre-aggregated totals
public static final String AGENT_TOTAL_INPUT_TOKENS    = "agentTotalInputTokens";
public static final String AGENT_TOTAL_OUTPUT_TOKENS   = "agentTotalOutputTokens";

// Nested field root
public static final String AGENT_INSTANCES             = "agentInstances";

// Nested identity
public static final String AGENT_INSTANCE_KEY          = "agentInstanceKey";
public static final String AGENT_ELEMENT_INSTANCE_KEY  = "elementInstanceKey";
public static final String AGENT_ELEMENT_ID            = "elementId";
public static final String AGENT_BPMN_PROCESS_ID       = "bpmnProcessId";
public static final String AGENT_PROCESS_DEFINITION_KEY = "processDefinitionKey";
public static final String AGENT_PROCESS_DEF_VERSION   = "processDefinitionVersion";
public static final String AGENT_VERSION_TAG           = "versionTag";
public static final String AGENT_TENANT_ID             = "tenantId";

// Nested state
public static final String AGENT_STATUS                = "status";

// Nested timestamps
public static final String AGENT_CREATION_DATE         = "creationDate";
public static final String AGENT_COMPLETION_DATE       = "completionDate";
public static final String AGENT_LAST_UPDATED_DATE     = "lastUpdatedDate";
public static final String AGENT_DURATION_IN_MS        = "durationInMs";
public static final String AGENT_CREATION_EPOCH_MS     = "creationDateEpochMs";

// Nested definition
public static final String AGENT_DEFINITION_MODEL      = "definition.model";
public static final String AGENT_DEFINITION_PROVIDER   = "definition.provider";

// Nested metrics
public static final String AGENT_METRICS_INPUT_TOKENS  = "metrics.inputTokens";
public static final String AGENT_METRICS_OUTPUT_TOKENS = "metrics.outputTokens";
public static final String AGENT_METRICS_MODEL_CALLS   = "metrics.modelCalls";
public static final String AGENT_METRICS_TOOL_CALLS    = "metrics.toolCalls";

// Nested tools
public static final String AGENT_TOOLS_NAME            = "tools.name";
```

> Constants use bare field names (e.g. `"elementId"`). In ES/OS queries they are qualified with
> the nested path prefix: `AGENT_INSTANCES + "." + AGENT_ELEMENT_ID` → `"agentInstances.elementId"`.

#### Mapping (add to `addProperties()`)

```java
.properties(AGENT_TOTAL_INPUT_TOKENS,  p -> p.long_(k -> k))
.properties(AGENT_TOTAL_OUTPUT_TOKENS, p -> p.long_(k -> k))
.properties(AGENT_INSTANCES, p -> p.nested(n -> n
    // Identity
    .properties(AGENT_INSTANCE_KEY,            np -> np.keyword(k -> k))
    .properties(AGENT_ELEMENT_INSTANCE_KEY,    np -> np.keyword(k -> k))
    .properties(AGENT_ELEMENT_ID,              np -> np.keyword(k -> k))
    .properties(AGENT_BPMN_PROCESS_ID,         np -> np.keyword(k -> k))
    .properties(AGENT_PROCESS_DEFINITION_KEY,  np -> np.keyword(k -> k))
    .properties(AGENT_PROCESS_DEF_VERSION,     np -> np.integer(k -> k))
    .properties(AGENT_VERSION_TAG,             np -> np.keyword(k -> k))
    .properties(AGENT_TENANT_ID,               np -> np.keyword(k -> k))
    // State
    .properties(AGENT_STATUS,                  np -> np.keyword(k -> k))
    // Timestamps
    .properties(AGENT_CREATION_DATE,       np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
    .properties(AGENT_COMPLETION_DATE,     np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
    .properties(AGENT_LAST_UPDATED_DATE,   np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
    .properties(AGENT_DURATION_IN_MS,      np -> np.long_(k -> k))
    .properties(AGENT_CREATION_EPOCH_MS,   np -> np.long_(k -> k))
    // Definition
    .properties("definition", np -> np.object(o -> o
        .properties("model",    op -> op.keyword(k -> k))
        .properties("provider", op -> op.keyword(k -> k))))
    // Metrics
    .properties("metrics", np -> np.object(o -> o
        .properties("inputTokens",  op -> op.long_(k -> k))
        .properties("outputTokens", op -> op.long_(k -> k))
        .properties("modelCalls",   op -> op.integer(k -> k))
        .properties("toolCalls",    op -> op.integer(k -> k))))
    // Tools
    .properties("tools", np -> np.object(o -> o
        .properties("name",        op -> op.keyword(k -> k))
        .properties("description", op -> op.keyword(k -> k))
        .properties("elementId",   op -> op.keyword(k -> k))))))
```

> Use `.object()` for `definition`, `metrics`, and `tools`. Dot-notation in `.properties()` creates
> a literal dot-named field rather than a nested sub-object hierarchy. `tools` is stored as a flat
> list of objects (not a nested type) — adequate for Phase 1 display. Change to `.nested()` in
> Phase 2 if per-tool aggregation is required.

#### DTO

**File**: `optimize/util/optimize-commons/src/main/java/io/camunda/optimize/dto/optimize/persistence/AgentInstanceDto.java`

```java
public class AgentInstanceDto implements Serializable, OptimizeDto {
    // All keys stored as String (Zeebe longs serialized to String to match keyword field type)
    private String  agentInstanceKey;
    private String  elementInstanceKey;
    private String  elementId;
    private String  bpmnProcessId;
    private String  processDefinitionKey;
    private Integer processDefinitionVersion;
    private String  versionTag;
    private String  tenantId;
    private String  status;

    private OffsetDateTime creationDate;
    private OffsetDateTime completionDate;
    private OffsetDateTime lastUpdatedDate;
    private Long    durationInMs;           // null until completionDate is known
    private Long    creationDateEpochMs;    // auxiliary; enables cross-batch durationInMs

    private AgentDefinitionDto     definition;
    private AgentMetricsDto        metrics;
    private List<AgentToolDto>     tools = new ArrayList<>();

    // Not indexed — used internally by the import pipeline
    @JsonIgnore private String processInstanceId;
    @JsonIgnore private String engineAlias;

    public static final class Fields { /* generated or hand-written field name constants */ }
}
```

Supporting DTOs:

```java
public class AgentDefinitionDto implements Serializable {
    private String model;
    private String provider;
}

public class AgentMetricsDto implements Serializable {
    private Long    inputTokens;
    private Long    outputTokens;
    private Integer modelCalls;
    private Integer toolCalls;
}

public class AgentToolDto implements Serializable {
    private String name;
    private String description;
    private String elementId;
}
```

Add to `ProcessInstanceDto`:

```java
private List<AgentInstanceDto> agentInstances = new ArrayList<>();
```

---

### 3.2 Painless Update Script

**File**: `optimize/backend/src/main/java/io/camunda/optimize/service/db/repository/script/ZeebeProcessInstanceScriptFactory.java`

Add `createUpdateAgentInstancesScript()` as a static method.

```java
static String createUpdateAgentInstancesScript() {
    return
        // Initialise list if absent
        "if (ctx._source.agentInstances == null) { ctx._source.agentInstances = []; }\n" +

        // Build a set of existing keys for O(1) lookup
        "def existingKeys = new HashSet();\n" +
        "for (def ai : ctx._source.agentInstances) { existingKeys.add(ai.agentInstanceKey); }\n" +

        // Upsert each agent instance from params
        "for (def newAi : params.agentInstances) {\n" +
        "  if (!existingKeys.contains(newAi.agentInstanceKey)) {\n" +
        // INSERT: new agent instance
        "    ctx._source.agentInstances.add(newAi);\n" +
        "  } else {\n" +
        // UPDATE: merge mutable fields; preserve identity fields already stored
        "    for (def ai : ctx._source.agentInstances) {\n" +
        "      if (ai.agentInstanceKey == newAi.agentInstanceKey) {\n" +
        "        ai.status          = newAi.status;\n" +
        "        ai.lastUpdatedDate = newAi.lastUpdatedDate;\n" +
        "        if (newAi.metrics  != null) { ai.metrics  = newAi.metrics; }\n" +
        "        if (newAi.tools    != null && !newAi.tools.isEmpty()) { ai.tools = newAi.tools; }\n" +
        // Set completionDate and compute durationInMs from epoch millis (cross-batch safe)
        "        if (newAi.completionDate != null) {\n" +
        "          ai.completionDate = newAi.completionDate;\n" +
        "          if (newAi.completionDateEpochMs != null && ai.creationDateEpochMs != null) {\n" +
        "            ai.durationInMs = newAi.completionDateEpochMs - ai.creationDateEpochMs;\n" +
        "          }\n" +
        "        }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +

        // Re-aggregate parent-level token totals from COMPLETED agent instances only
        "long totalIn = 0L; long totalOut = 0L;\n" +
        "for (def ai : ctx._source.agentInstances) {\n" +
        "  if ('COMPLETED'.equals(ai.status) && ai.metrics != null) {\n" +
        "    if (ai.metrics.inputTokens  != null) { totalIn  += ai.metrics.inputTokens; }\n" +
        "    if (ai.metrics.outputTokens != null) { totalOut += ai.metrics.outputTokens; }\n" +
        "  }\n" +
        "}\n" +
        "ctx._source.agentTotalInputTokens  = totalIn;\n" +
        "ctx._source.agentTotalOutputTokens = totalOut;\n";
}
```

> **String comparison in Painless**: `==` on `String` (Map value type) calls `.equals()` — safe for
> the `agentInstanceKey` comparison. Do not use `.equals()` explicitly; it would fail on null.
> Use `'COMPLETED'.equals(ai.status)` (not `ai.status == 'COMPLETED'`) for null safety.
>
> **`completionDateEpochMs`**: passed in the `params.agentInstances` element for COMPLETED events.
> Not a stored field in the index — only used as an arithmetic input in the script. The stored field
> is `creationDateEpochMs`, set on CREATED and never overwritten.

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

|                   Class                   |                       Extends / Implements                        |
|-------------------------------------------|-------------------------------------------------------------------|
| `ZeebeAgentInstanceImportHandler`         | `AbstractZeebeImportHandler`                                      |
| `ZeebeAgentInstanceFetcher`               | `AbstractZeebeRecordFetcher`                                      |
| `ZeebeAgentInstanceImportService`         | `ZeebeProcessInstanceSubEntityImportService<AgentInstanceRecord>` |
| `ZeebeAgentInstanceImportMediator`        | `AbstractZeebeImportMediator`                                     |
| `ZeebeAgentInstanceImportMediatorFactory` | `AbstractImportMediatorFactory`                                   |

#### Intent filter (Phase 1: CREATED + COMPLETED only)

```java
// Phase 1: import CREATED and COMPLETED events only.
// UPDATED will be added in Phase 2 for live agent status tracking.
private static final Set<AgentInstanceIntent> INTENTS_TO_IMPORT =
    Set.of(AgentInstanceIntent.CREATED, AgentInstanceIntent.COMPLETED);
```

#### `filterAndMapZeebeRecordsToOptimizeEntities`

```java
@Override
protected List<ProcessInstanceDto> filterAndMapZeebeRecordsToOptimizeEntities(
    List<HitEntity<AgentInstanceRecord>> hits) {

    return hits.stream()
        .filter(hit -> INTENTS_TO_IMPORT.contains(hit.getIntent()))
        // Sort by event timestamp ascending: CREATED must be processed before COMPLETED
        // when both appear in the same import batch (avoids negative durationInMs).
        .sorted(Comparator.comparingLong(HitEntity::getTimestamp))
        .collect(groupingBy(hit -> hit.getValue().getProcessInstanceKey()))
        .entrySet().stream()
        .map(entry -> {
            long processInstanceKey = entry.getKey();
            List<HitEntity<AgentInstanceRecord>> groupHits = entry.getValue();
            AgentInstanceRecord first = groupHits.get(0).getValue();

            ProcessInstanceDto pi = createSkeletonProcessInstance(
                String.valueOf(first.getProcessDefinitionKey()),  // processDefinitionKey
                String.valueOf(processInstanceKey),               // processInstanceId
                first.getBpmnProcessId(),                         // bpmnProcessId (String)
                first.getTenantId()
            );

            // Map each event to a DTO; merging is done by the Painless upsert script
            pi.setAgentInstances(groupHits.stream()
                .map(hit -> mapToAgentInstanceDto(
                    hit.getValue(), hit.getIntent(), hit.getTimestamp()))
                .toList());

            return pi;
        })
        .toList();
}
```

#### `mapToAgentInstanceDto`

```java
private AgentInstanceDto mapToAgentInstanceDto(
    AgentInstanceRecord record,
    AgentInstanceIntent intent,
    long eventTimestampMs) {

    AgentInstanceDto dto = new AgentInstanceDto();

    // Identity — all fields present on every event (once set at CREATED)
    dto.setAgentInstanceKey(String.valueOf(record.getAgentInstanceKey()));
    dto.setElementInstanceKey(String.valueOf(record.getElementInstanceKey()));
    dto.setElementId(record.getElementId());
    dto.setBpmnProcessId(record.getBpmnProcessId());
    dto.setProcessDefinitionKey(String.valueOf(record.getProcessDefinitionKey()));
    dto.setProcessDefinitionVersion(record.getProcessDefinitionVersion());
    dto.setVersionTag(record.getVersionTag());
    dto.setTenantId(record.getTenantId());
    dto.setProcessInstanceId(String.valueOf(record.getProcessInstanceKey()));

    // Status — current status at this event
    dto.setStatus(record.getStatus().name());

    // Definition — immutable; set on CREATED, repeated on subsequent events
    if (record.getDefinition() != null) {
        AgentDefinitionDto defDto = new AgentDefinitionDto();
        defDto.setModel(record.getDefinition().getModel());
        defDto.setProvider(record.getDefinition().getProvider());
        dto.setDefinition(defDto);
    }

    // Metrics — final totals on COMPLETED; empty/zero on CREATED
    if (record.getMetrics() != null) {
        AgentMetricsDto metricsDto = new AgentMetricsDto();
        metricsDto.setInputTokens(record.getMetrics().getInputTokens());
        metricsDto.setOutputTokens(record.getMetrics().getOutputTokens());
        metricsDto.setModelCalls(record.getMetrics().getModelCalls());
        metricsDto.setToolCalls(record.getMetrics().getToolCalls());
        dto.setMetrics(metricsDto);
    }

    // Tools — final list on COMPLETED; empty on CREATED
    if (record.getTools() != null) {
        dto.setTools(record.getTools().stream()
            .map(t -> {
                AgentToolDto toolDto = new AgentToolDto();
                toolDto.setName(t.getName());
                toolDto.setDescription(t.getDescription());
                toolDto.setElementId(t.getElementId());
                return toolDto;
            })
            .toList());
    }

    // Timestamps — derived from event timestamp based on intent
    OffsetDateTime eventTime = OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(eventTimestampMs), ZoneOffset.UTC);

    switch (intent) {
        case CREATED -> {
            dto.setCreationDate(eventTime);
            dto.setLastUpdatedDate(eventTime);
            dto.setCreationDateEpochMs(eventTimestampMs);
            // completionDate and durationInMs remain null
        }
        case COMPLETED -> {
            dto.setCompletionDate(eventTime);
            dto.setLastUpdatedDate(eventTime);
            // Expose epoch ms so Painless can compute durationInMs cross-batch
            // (field name: completionDateEpochMs — passed in params, not stored in index)
            dto.setCompletionDateEpochMs(eventTimestampMs);
        }
        // UPDATED handled in Phase 2
    }

    return dto;
}
```

> `completionDateEpochMs` is a transient field on the DTO (annotated `@JsonIgnore` for index
> serialization — it must not be stored in the index). It is only present in the `params` map
> passed to the Painless script. Add a transient/`@JsonIgnore` field `completionDateEpochMs`
> to `AgentInstanceDto` for this purpose.
>
> The Painless script reads `newAi.completionDateEpochMs` from `params.agentInstances` and
> reads `ai.creationDateEpochMs` from the stored document to compute `durationInMs`.

#### Registration

Register `ZeebeAgentInstanceImportMediatorFactory` in the Spring context alongside existing Zeebe
import mediator factories — same location as `ZeebeIncidentImportMediatorFactory`.

---

## 4. Layer 2 — Backend API

### 4.1 Endpoint Overview

All endpoints under `/api/agentic-control-plane/`.

| ID  |              Path              | Phase 1 Level | Phase 2 Level |                 Description                  |
|-----|--------------------------------|---------------|---------------|----------------------------------------------|
| A1  | `GET /process-breakdown`       | L0            | —             | Top token consumers by process               |
| A2  | `GET /agent-elements`          | —             | L1            | Agent element dropdown (**Phase 2**)         |
| A3  | `GET /summary`                 | L0, L1        | L2            | Summary KPI stats with WoW deltas            |
| A4  | `GET /token-trend`             | L0, L1        | L2            | Token trend (multi-line at L0/L1)            |
| A5  | `GET /duration-stats`          | L0, L1        | L2            | Duration P50/P95 + stability trend           |
| A6  | `GET /incident-rate`           | L0, L1        | L2            | Incident rate                                |
| A7  | `GET /agents`                  | —             | L2            | Paginated agent instance list (**Phase 2**)  |
| A8  | `GET /token-outlier-bands`     | L0, L1        | L2            | Token p5/p50/p95 bands over time             |
| A9  | `GET /tokens-per-agent-call`   | L1            | L2            | Avg tokens per model call, per agent element |
| A10 | `GET /failure-rate-by-version` | L1            | L2            | Incident rate by process definition version  |

---

### 4.2 Common Filter Params

**Phase 1 params** (all endpoints):

|         Param          |   Type   | Required |                                  Description                                  |
|------------------------|----------|----------|-------------------------------------------------------------------------------|
| `processDefinitionKey` | `String` | No       | Absent → L0 (fleet). Present → L1 (single process).                           |
| `startDateFrom`        | ISO 8601 | Yes      | Start of the date range filter (applied to `startDate` of process instances). |
| `startDateTo`          | ISO 8601 | Yes      | End of the date range filter.                                                 |

> `tenantId` is **never** a client parameter. It is resolved server-side from the JWT via
> `CamundaCCSMTenantAuthorizationService.getCurrentUserAuthorizedTenants(userId)`.

**Phase 2 params** (add when implementing L2):

|      Param       |   Type   | Required |                                                     Description                                                     |
|------------------|----------|----------|---------------------------------------------------------------------------------------------------------------------|
| `agentElementId` | `String` | No       | Absent → L1. Present → L2. Named `agentElementId` (not `elementId`) to distinguish from the internal ES field name. |
| `after`          | `String` | No       | Opaque cursor for A7 pagination only.                                                                               |

**Baseline filter for all Phase 1 endpoints**:

```json
{
  "bool": {
    "must": [
      { "term":  { "state": "COMPLETED" } },
      { "range": { "startDate": { "gte": "<startDateFrom>", "lte": "<startDateTo>" } } },
      { "terms": { "tenantId": ["<authorized-tenant-1>", "..."] } },
      {
        "nested": {
          "path": "agentInstances",
          "query": { "exists": { "field": "agentInstances.agentInstanceKey" } }
        }
      }
    ],
    "filter": [
      // Conditionally add at L1:
      { "term": { "processDefinitionKey": "<processDefinitionKey>" } }
    ]
  }
}
```

The `has_agents` nested-exists filter is always present for this dashboard — all metrics are
computed exclusively over process instances that had at least one agent activation.

> **Query examples in §4.4–§4.11** omit the `tenantId`, `range`, and `has_agents` clauses for
> readability. All three must be included in every generated query.

---

### 4.3 Multi-tenancy

```
REST controller  →  AgenticControlPlaneService
  →  CamundaCCSMTenantAuthorizationService.getCurrentUserAuthorizedTenants(userId)
  →  inject as terms filter into every ES/OS query
```

Omitting the tenant filter is a **security regression**: users would see data for tenants they are
not authorized for.

---

### 4.4 A1 — Process Breakdown

**Purpose**: L0 only. Top token consumers by process, with aggregate KPIs per process.

Serves the left-hand "process selector" list on the dashboard. Note: this endpoint returns process
definition keys, not human-readable names. The frontend resolves display names using the existing
Optimize `/api/process-definition` endpoint. **Do not use A1 as the data source for the
ProcessSelector dropdown** — use the existing process definition list endpoint instead.

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
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

> `incidentCount` here is the total number of incident records across all PIs for that process
> (raw count, not rate). The Incident Rate card uses A6 independently. Raw count is appropriate
> for the process breakdown table to show relative incident volume.

**Response shape**:

```json
{
  "processes": [
    {
      "processDefinitionKey": "2251799813685100",
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

### 4.5 A3 — Summary KPIs

**Purpose**: Stat block at the top of the dashboard. Five KPI cards: Total Runs, Avg Duration,
Total Tokens, Median Tokens, Total Tool Calls. Incident Rate is served by A6 as a separate card.
Each numeric field includes a WoW delta (`<field>WoW`) from a parallel prior-period request.

**WoW delta**: Run the same query a second time with the date range shifted backward by 7 days.
`<field>WoW = currentValue − priorValue`. Both requests execute in parallel.

#### L0 / L1 query

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
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
      "aggs": {
        "sum": { "sum": { "field": "agentInstances.metrics.toolCalls" } }
      }
    }
  }
}
```

#### L2 query (Phase 2)

L2 scopes metrics to a specific agent element. All aggregations move inside
`nested → agentInstances → filter(elementId)`. See Phase 2 implementation notes in §6.

**Response shape**:

```json
{
  "totalRuns": 1420,
  "totalRunsWoW": 150,
  "avgDurationMs": 18340,
  "avgDurationMsWoW": -700,
  "totalInputTokens": 240000,
  "totalInputTokensWoW": 12000,
  "totalOutputTokens": 185000,
  "totalOutputTokensWoW": 9500,
  "medianTokens": 2180,
  "medianTokensWoW": 80,
  "totalToolCalls": 8820,
  "totalToolCallsWoW": 420
}
```

> `incidentCount` is **not** in the A3 response. The Incident Rate KPI card is served by A6
> independently. Returning a raw incident count here would require a denominator to be useful,
> and A6 already provides the rate with the correct denominator.

---

### 4.6 A4 — Token Trend

**Purpose**: Token usage over time. Multi-line (top-5 agent elements + "Other") at L0/L1.

#### Interval derivation

The `calendar_interval` is derived server-side from the requested date range:

| Date range | Interval |
|------------|----------|
| ≤ 2 days   | `1h`     |
| ≤ 30 days  | `1d`     |
| ≤ 180 days | `1w`     |
| > 180 days | `1M`     |

#### Step 1 — Identify top-5 agent elements by total tokens

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
  "aggs": {
    "agent_scope": {
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

> The Painless script runs inside the `nested` aggregation context. Use bare field names
> `doc['metrics.inputTokens']` — **not** `doc['agentInstances.metrics.inputTokens']`.

Take the first 5 `elementId` values from `by_element.buckets`.

#### Step 2 — Date histogram per top-5 element (5 parallel requests)

```json
{
  "size": 0,
  "query": {
    "bool": {
      "must": [
        { "<<baseline-filter>>" },
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
                "calendar_interval": "<derived-interval>"
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

The parent-level `nested` query filter (in `must`) pre-filters documents to those containing the
target `elementId`. The inner `filter` in `for_element` narrows to that element inside the
aggregation.

#### "Other" series

```
Other(date) = totalTokens(date) − Σ top5(date)
```

Where `totalTokens(date)` comes from a date histogram on parent-level `agentTotalInputTokens +
agentTotalOutputTokens` (a single additional request).

#### L2 — single line (Phase 2)

Identical to Step 2 but with the selected `agentElementId`. Single series returned. No "Other".

**Response shape**:

```json
{
  "interval": "1d",
  "series": [
    {
      "elementId": "invoice-data-extraction-agent",
      "label": "invoice-data-extraction-agent",
      "data": [
        { "date": "2025-01-01T00:00:00Z", "inputTokens": 4200, "outputTokens": 3100 }
      ]
    },
    {
      "elementId": "__other__",
      "label": "Other",
      "data": [
        { "date": "2025-01-01T00:00:00Z", "inputTokens": 800, "outputTokens": 600 }
      ]
    }
  ]
}
```

---

### 4.7 A5 — Duration Stats

**Purpose**: Duration P50/P95 KPI stats + execution duration stability over time (P50/P95 trend).

Duration field at L0/L1: `duration` (parent-level, pre-computed in ms).

#### L0 / L1 query

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
  "aggs": {
    "duration_percentiles": {
      "percentiles": { "field": "duration", "percents": [50, 95] }
    },
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "<derived-interval>" },
      "aggs": {
        "duration_trend": {
          "percentiles": { "field": "duration", "percents": [50, 95] }
        }
      }
    }
  }
}
```

> `endDate` is the existing `ProcessInstanceIndex` field for the process completion timestamp.
> `duration` is the pre-computed duration in milliseconds (end − start). Both are indexed at the
> parent level on all completed process instances.

#### L2 query (Phase 2)

Scopes to `agentInstances.durationInMs` for the selected element. Full query in Phase 2 spec.

**Response shape**:

```json
{
  "p50Ms": 3800,
  "p50MsWoW": -200,
  "p95Ms": 8700,
  "p95MsWoW": 400,
  "trend": [
    { "date": "2025-01-01T00:00:00Z", "p50Ms": 3600, "p95Ms": 8200 }
  ]
}
```

> `durationScope` is omitted from Phase 1 — it is always "process" at L0/L1. It will be added in
> Phase 2 to allow the frontend to update the label/tooltip when switching to agent-scope duration.

---

### 4.8 A6 — Incident Rate

**Purpose**: Incident rate KPI scoped to the active filter level.

- **L0/L1**: `processInstancesWithIncidents / totalAgenticRuns`
- **L2**: `agentElementIncidents / agentActivations` (Phase 2)

#### L0 / L1 query

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
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

> The `has_incidents` filter agg is applied at the **parent** (PI) level. It filters parent
> documents where at least one nested incident exists, then counts the matching parent documents.
> Result: unique process instances with at least one incident — correct denominator for rate.

#### L2 query (Phase 2)

```json
{
  "size": 0,
  "query": { "<<baseline-filter-plus-elementId-nested-filter>>" },
  "aggs": {
    "agent_runs": {
      "nested": { "path": "agentInstances" },
      "aggs": {
        "for_element": {
          "filter": { "term": { "agentInstances.elementId": "<agentElementId>" } },
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
          "filter": { "term": { "incidents.activityId": "<agentElementId>" } },
          "aggs": { "incidentCount": { "value_count": { "field": "incidents.id" } } }
        }
      }
    }
  }
}
```

`incidentRate = agent_incidents.for_element.incidentCount / agent_runs.for_element.activationCount`

> The two `nested` aggregations (`agentInstances` and `incidents`) are siblings at the parent
> level. Each resolves independently against its own nested path — no `reverse_nested` needed
> here. Division is performed in Java after collecting the results.

**Response shape**:

```json
{
  "incidentRate": 0.024,
  "incidentRateWoW": -0.001,
  "activationCount": 1420,
  "incidentCount": 34
}
```

> `activationCount` is process instance count at L0/L1, agent activation count at L2.

---

### 4.9 A8 — Token Outlier Bands

**Purpose**: P5/P50/P95 of total tokens per time bucket. Shows token consumption distribution and
outlier patterns over time.

#### L0 / L1 query

```json
{
  "size": 0,
  "query": { "<<baseline-filter>>" },
  "aggs": {
    "over_time": {
      "date_histogram": { "field": "endDate", "calendar_interval": "<derived-interval>" },
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

> The `has_agents` baseline filter ensures only agentic process runs are included. The
> `agentTotalInputTokens` field is set to 0 for CREATED-only records (agent started but not yet
> completed). P5 of 0 for in-progress agents is excluded because the baseline filter requires
> `state = COMPLETED`.

#### L2 query (Phase 2)

Scopes to `agentInstances.metrics.inputTokens + outputTokens` inside a nested aggregation for the
selected element.

> **Known issue — Phase 2**: Painless scripts inside nested aggregation contexts must use bare
> field names (e.g. `doc['metrics.inputTokens']`), not the full path
> `doc['agentInstances.metrics.inputTokens']`. The Phase 2 query must use the bare path to avoid
> a silent zero-result aggregation.

**Response shape**:

```json
{
  "interval": "1d",
  "bands": [
    { "date": "2025-01-01T00:00:00Z", "p5": 180, "p50": 1700, "p95": 4200 }
  ]
}
```

---

### 4.10 A9 — Avg Tokens per Agent Call

**Purpose**: L1 only (Phase 1). Average tokens consumed per LLM model call, grouped by agent
element. Identifies which agent is the most expensive per invocation.

```json
{
  "size": 0,
  "query": { "<<baseline-filter-with-processDefinitionKey>>" },
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

> `bucket_script` returns `null` when `modelCalls = 0` (rather than `0`). The frontend must
> render `null` as "—" (not as "0 tokens/call"), to distinguish "no model calls made" from
> "zero tokens per call".

**Response shape**:

```json
{
  "agents": [
    {
      "elementId": "invoice-data-extraction-agent",
      "label": "invoice-data-extraction-agent",
      "avgTokensPerCall": 613.0,
      "totalModelCalls": 2050
    },
    {
      "elementId": "approval-classifier-agent",
      "label": "approval-classifier-agent",
      "avgTokensPerCall": null,
      "totalModelCalls": 0
    }
  ]
}
```

---

### 4.11 A10 — Failure Rate by Process Version

**Purpose**: L1 only (Phase 1). Incident rate broken down by `processDefinitionVersion`. Shows
whether reliability degrades or improves across deployments.

> `processDefinitionVersion` is a parent-level field on `ProcessInstanceIndex` (not inside
> `agentInstances`). No new Zeebe schema dependency.

#### L1 query

```json
{
  "size": 0,
  "query": { "<<baseline-filter-with-processDefinitionKey>>" },
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

> Denominator = agentic process runs per version (enforced by the `has_agents` baseline filter).
> Numerator = PIs with at least one incident of any type (process-level, not agent-specific).

#### L2 query (Phase 2)

Agent-element scoped: denominator = agent activations per version, numerator = agent-element
incidents per version. Full query in Phase 2 spec.

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

### 4.12 A2 — Agent Dropdown (Phase 2)

**Purpose**: Returns distinct `elementId` values for a chosen process. Powers the agent selector.

Implementation deferred to Phase 2. Do not implement or expose this endpoint in Phase 1. The
frontend must not render the AgentSelector component.

When implemented: uses `composite` aggregation scroller inside `nested` on `agentInstances.elementId`.
Response: `{ "agentElements": [{ "elementId": "...", "label": "..." }] }`.

---

### 4.13 A7 — Agents List (Phase 2)

**Purpose**: L2. Paginated table of individual agent instance activations with incident join.

Implementation deferred to Phase 2. Requires L2 filter (agent element selected) and cursor-based
pagination via `composite` aggregation scroller.

Key implementation notes for Phase 2:

- Two-request approach: (1) composite scroller for agent instance rows; (2) incident counts per PI
  merged server-side.
- `successRate` shown at summary level only (not per-row).
- `reverse_nested: {}` sub-agg inside the composite is needed to extract `processInstanceId` from
  the parent document context.
- Join key for incident merge: parent document `processInstanceId` matches
  `incidents.processInstanceId`.

---

## 5. Layer 3 — Frontend

**Location**: `optimize/client/src/components/AgenticControlPlane/`

**Framework**: React + Carbon Design System.

### Phase 1 — Component visibility by filter level

|       Component       | L0 |      L1      |                          Description                          |
|-----------------------|----|--------------|---------------------------------------------------------------|
| ProcessSelector       | ✅  | ✅ (selected) | Uses existing `/api/process-definition` endpoint. **NOT A1.** |
| SummaryKPIs           | ✅  | ✅            | A3 + A6 (separate requests)                                   |
| TokenTrendChart       | ✅  | ✅            | A4 multi-line (top-5 + "Other")                               |
| DurationStats         | ✅  | ✅            | A5                                                            |
| IncidentRateKPI       | ✅  | ✅            | A6                                                            |
| TokenOutlierBands     | ✅  | ✅            | A8                                                            |
| AvgTokensPerAgentCall | —  | ✅            | A9 (L1 only)                                                  |
| FailureRateByVersion  | —  | ✅            | A10 (L1 only)                                                 |

**Phase 2 only — do NOT render in Phase 1**:

|         Component         | Level |                  Description                   |
|---------------------------|-------|------------------------------------------------|
| AgentSelector             | L1→L2 | Uses A2. Hidden in Phase 1.                    |
| AgentsList                | L2    | Uses A7. Hidden in Phase 1.                    |
| L2 variants of all charts | L2    | Single-line trend, agent-scoped duration, etc. |

### Component structure

```
AgenticControlPlane/
  index.tsx
  ControlPlaneDashboard.tsx
  components/
    ProcessSelector.tsx          uses /api/process-definition (existing endpoint)
    AgentSelector.tsx            uses A2 — Phase 2, do not render in Phase 1
    SummaryKPIs.tsx              uses A3 + A6
    TokenTrendChart.tsx          uses A4 (multi-line at L0/L1, single-line at L2)
    TokenOutlierBands.tsx        uses A8
    AvgTokensPerAgentCall.tsx    uses A9 — rendered at L1 only
    DurationStats.tsx            uses A5
    IncidentRateKPI.tsx          uses A6
    FailureRateByVersion.tsx     uses A10 — rendered at L1 only
    AgentsList.tsx               uses A7 — Phase 2 only
  hooks/
    useSummaryKPIs.ts
    useTokenTrend.ts
    useDurationStats.ts
    useIncidentRate.ts
    useTokenOutlierBands.ts
    useAvgTokensPerCall.ts
    useFailureRateByVersion.ts
    useProcessBreakdown.ts
    useAgentElements.ts          Phase 2
    useAgentsList.ts             Phase 2
```

### Filter context

`AgentFilterContext` holds:

```typescript
interface AgentFilterState {
  processDefinitionKey: string | null;    // null = L0
  agentElementId: string | null;          // null = L1; non-null = L2 (Phase 2)
  dateRange: { from: string; to: string };
}
```

- Selecting a process: `processDefinitionKey` set → L0 → L1.
- Clearing process: → L0. Components visible only at L1 unmount.
- Phase 2: selecting an agent: `agentElementId` set → L2.

### Notes

- **ProcessSelector** fetches from the existing Optimize process definition list endpoint, not A1.
  A1 (process breakdown) is a separate chart — the ranked token consumer list on the dashboard,
  not the picker.
- **SummaryKPIs**: Incident Rate card fires A6 independently; it is not derived from A3.
- **TokenTrendChart**: "Other" series is computed client-side: subtract named series from total.
  Cap named series at 5. `elementId === "__other__"` is the sentinel value for the "Other" series.
- **AvgTokensPerAgentCall**: Render `null` `avgTokensPerCall` as "—".
- **No status badges anywhere** in Phase 1.
- **No settings page** in Phase 1.
- **`durationScope` label**: In Phase 1, the Duration card always shows "Process duration".
  In Phase 2, toggle label to "Agent execution time" when L2 is active.

---

## 6. Out of Scope

- **Phase 2 — L2 agent-scope queries**: All endpoints support L0/L1 only in Phase 1. L2 adds
  `agentElementId` filter parameter and switches to nested aggregations for agent-scoped metrics.
- **Phase 2 — A2 (Agent Dropdown)**: Requires L2 filter selection. Deferred.
- **Phase 2 — A7 (Agents List)**: Paginated table of individual agent instances. Deferred.
- **Phase 2 — UPDATED event import**: Currently CREATED + COMPLETED only. UPDATED adds live
  status tracking for running agent instances. Requires addition to `INTENTS_TO_IMPORT` and
  Painless script validation.
- **Phase 2 — FAILED agent status**: Failures currently surface as Zeebe incidents. FAILED status
  may be added to `AgentInstanceStatus` and surfaced in Optimize as a non-terminal state.
- **Phase 2 — Reasoning tokens**: Via Zeebe schema change.
- **Phase 2 — Per-tool call breakdown**: When Zeebe provides per-tool data.
- **Phase 2 — Agent details panel**: Click-through from Agents List.
- **Settings page / threshold configuration**: Dropped entirely (no Layer 4).
- **Status badges** (Healthy / Degraded / Failing): Dropped for Phase 1.
- **Write-back / incident resolution**: Read-only.
- **OpenSearch query differences**: Handled at the existing ES/OS abstraction layer (`search/`
  module). No Agentic Control Plane code is ES/OS-specific.

---

## 7. Migration

### Index version bump

`ProcessInstanceIndex.VERSION`: `8` → `9`

No data migration class required. Old process instances carry no agent data. Queries on missing
nested paths return zero results. The VERSION bump ensures all new process instance indices are
created with the correct `nested` mapping for `agentInstances`. Without the bump, ES/OS
auto-maps `agentInstances` as `object` on first write, silently breaking all nested aggregations.

### Nested object limit

Set `index.mapping.nested_objects.limit = 50000` on `ProcessInstanceIndex` if high agent
invocation volume is expected (default ES/OS limit: 10,000 nested objects per document across all
nested fields). Silent data drops occur if the limit is exceeded — monitor with index stats.

### Import service registration

Register `ZeebeAgentInstanceImportMediatorFactory` in the Spring context alongside existing Zeebe
import mediator factories. Follow the same registration pattern as
`ZeebeIncidentImportMediatorFactory`.

### Script validation

The Painless script in §3.2 **must be validated against a running ES/OS instance** before
shipping. Painless syntax errors surface only at runtime, not at compile time. Use the existing
Optimize script testing infrastructure or test directly via the ES/OS `_scripts/painless/execute`
API.
