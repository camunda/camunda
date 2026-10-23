# Prototype design: agentic dashboard Phase 2 on flat agent indices

Status: approved 2026-10-23 (prototype, ES only, not for merge).
Epic: experience-pdp#84, "Agent-level visibility for agentic processes".
Branch: `sherrin/agentic-phase2-proto`.

## 1. Findings that shape the design

- **Engine records carry everything except history.**
  - `CREATED`, `COMPLETED` and `MIGRATED` carry full data.
  - `UPDATED` always carries `definition.model`/`provider`, `limits` and `metrics` (totals). It omits `tools` and `systemPrompt` unless `changedAttributes` names them (`AgentHistoryBatchBehavior#trimUnchangedContentFields`).
  - Model, provider and limits changes arrive as an `AGENT_INSTANCE UPDATED` emitted when the configuration history item commits (`AgentHistoryCommitProcessor`). The latest model is therefore visible without `AGENT_HISTORY`.
- **`changedAttributes` tells us what moved.**
  - An `UPDATED` record whose metrics changed contains `metrics`.
  - A config-commit `UPDATED` contains `model`, `provider`, `tools` and `max*`.
  - `COMPLETED` is always `["status"]` (`AgentInstanceCompleteProcessor:70`).
  - `modelCalls` and `toolCalls` are derived by the engine: +1 per ASSISTANT history item, + its tool calls.
- **Token semantics (camunda/connectors `main` @ `58e2a06`, `AgentMetrics` javadoc).**
  - `inputTokens` excludes cache read and cache creation on every path that reports them. The OpenAI path subtracts cached tokens explicitly.
  - `outputTokens` already includes reasoning; `reasoningTokens` is an "of which" value.
  - Caveat: the legacy LangChain4j Azure OpenAI and Vertex paths report cache as 0 with cached input inside `inputTokens`, and Vertex drops thinking tokens. Nothing is double-counted; there is just no split, and Vertex undercounts.
- **The exporter already exports `AGENT_DEFINITION`** (`index.agentDefinition = true`). `OptimizeModeFilter` is the only gate.
- **Import mediators are independent per record type and partition.** Agent, incident, user-task and process-instance records arrive in any order. Today there is no cross-entity catch-up in the import path.
- **Optimize flow-node instances have no parent/scope link.** `ZeebeProcessInstanceDataDto` deserializes `flowScopeKey`, but it is not stored. For an AI Agent **Task** (service task), the tools live in a separate ad-hoc sub-process, so a scope link would not point at the agent element instance anyway.
- **Incidents.** `IncidentDto.activityId` holds the element instance key until a later write rewrites it to the element ID (`ZeebeProcessInstanceScriptFactory:143-152`). Enrichment must resolve both forms.
- **Report engine.**
  - A plan is picked by command key `viewEntity_viewProperty_groupBy...`.
  - Index choice and definition query are in `AbstractProcessExecutionPlanInterpreterES` (`getIndexNames`, `createDefinitionQuery`).
  - Date, completed and definition filters work against any index that uses the process-instance field names (`startDate`, `endDate`, `processDefinitionKey`, `processDefinitionVersion`, `tenantId`).

## 2. Naming

Optimize's C7 convention (`optimize/docs/adr/001-c7-naming-conventions.md`) wins over the shape sketched in the brief:

- `...Id` is a unique key.
- `...Key` is a BPMN string.

Mapping:

| Brief | Stored as |
|---|---|
| `agentInstanceKey` | `agentInstanceId` |
| `processInstanceKey` | `processInstanceId` |
| `rootProcessInstanceKey` | `rootProcessInstanceId` |
| `elementId` | `flowNodeId` |
| `elementInstanceKeys` | `flowNodeInstanceIds` |

`processDefinitionKey` (BPMN ID), `processDefinitionId` (definition key) and `agentId` stay as in the brief.

## 3. Documents

### `optimize-agent-instance_v1` (one doc per agent run, doc id = `agentInstanceId`)

| Group | Field | Notes |
|---|---|---|
| Identity | `agentInstanceId`, `agentDefinitionId` (record's `agentDefinitionKey`; today Optimize drops it), `agentId` = `processDefinitionKey:flowNodeId`, `flowNodeId`, `flowNodeInstanceIds[]`, `processDefinitionKey`, `processDefinitionId`, `processDefinitionVersion`, `processInstanceId`, `rootProcessInstanceId`, `tenantId` | keyword |
| Config (latest) | `model`, `provider`, `maxModelCalls`, `maxToolCalls`, `maxTokens` | Taken from the record with the highest position. Limits keep `-1` = no limit. |
| Lifecycle | `startDate` (CREATED ts), `lastActivityDate`, `endDate` (COMPLETED ts), `status`, `durationMs` = `lastActivityDate − startDate` | `endDate` = agent completion = process end/cancel (ADR 0012 D9) |
| Metrics (totals) | `inputTokens`, `outputTokens`, `reasoningTokens`, `cacheCreationTokens`, `cacheReadTokens`, `totalTokens`, `modelCalls`, `toolCalls` | `-1` is stored as null |
| Limits | `hitLimit` (bool), `limitHit[]` (`maxModelCalls` / `maxToolCalls` / `maxTokens`) | computed at import |
| Tools | `toolFlowNodeIds[]`, `toolCallsByTool` (nested: `toolFlowNodeId`, `toolName`, `count`) | |
| Per model | `tokensByModel` (nested: `provider`, `model`, `inputTokens`, `outputTokens`, `cacheCreationTokens`, `cacheReadTokens`, `totalTokens`) | see below |
| Bookkeeping (deltas) | `lastMetricTotals` (the totals of the last metrics record folded in) | not queried |
| Enrichment | `processInstanceState`, `processInstanceEndDate`, `hasIncident`, `incidentCount`, `incidentFlowNodeIds[]`, `processUserTaskCount` | null until known |
| Bookkeeping | `lastRecordPosition` (per partition, for "latest wins"), `enrichedAt` | |

**Formulas**

- `totalTokens = inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens`.
  - Null components count as 0.
  - Reasoning is excluded because it is already inside output.
  - If every component is null, `totalTokens` is null.
- `hitLimit` is true when any limit `> 0` is reached:
  - `modelCalls >= maxModelCalls`
  - `toolCalls >= maxToolCalls`
  - `totalTokens >= maxTokens`

  All limits `-1` means false.

**`tokensByModel`**

- Records arrive as running totals. For each metrics-changing record, in position order:
  - delta = record totals − `lastMetricTotals`
  - the delta is added to the `tokensByModel` entry for that record's `provider`/`model`
- `CREATED` counts its own totals (usually 0) against the creation model.
- All records of one agent instance sit on one partition and are imported in position order, so deltas are safe across import batches. The painless merge skips any record at or below `lastRecordPosition`.
- A run that switches Bedrock → OpenAI mid-run splits its tokens between both models.
- Accuracy: a model change takes effect when its configuration item commits (job completion). Tokens reported in the same `UPDATED` as the change go to the new model, so the split is accurate to one activation.

**`lastActivityDate`**

- It is the max record timestamp over `CREATED` and every `UPDATED` whose `changedAttributes` contains `metrics`.
- `COMPLETED` (`["status"]`) and config-only `UPDATED` records never move it.
- It is merged with `max()`, so batches can arrive in any order.

### `optimize-agent-definition_v1` (one doc per agent, doc id = `tenantId:processDefinitionKey:flowNodeId`)

- **Identity from `AGENT_DEFINITION CREATED`:**
  - `agentId`, `tenantId`, `processDefinitionKey`, `flowNodeId`
  - `name`, `agentType` (from the latest version seen)
  - `agentDefinitionIds[]`, `processDefinitionVersions[]`
- **Rollups from agent instances:**
  - `model`, `provider`, `maxModelCalls`, `maxToolCalls`, `maxTokens` (from the run with the latest `lastActivityDate`)
  - `firstSeen` (min `startDate`), `lastSeen` (max `lastActivityDate`)
- **Upsert rules:**
  - Either side may create the doc first.
  - `DELETED` is ignored in the prototype.

## 4. Import and enrichment (backend)

**Slice 1: filter and indices**
- `OptimizeModeFilter`: add `case AGENT_DEFINITION -> intent == CREATED` (own commit, plus a unit test).
- `AgentInstanceIndex` and `AgentDefinitionIndex` (abstract, in `optimize-commons`), with `...IndexES` and minimal `...IndexOS` twins.
- Register them in `ElasticSearchSchemaManager`, `OpenSearchSchemaManager` and `IndexLookupUtil`. Both are static shared indices, created on startup by `createIndexIfMissing`.

**Slice 2: agent definition import**

Uses the `ZeebeProcessDefinitionImportService` shape, not the process-instance sub-entity base:
- `ZeebeAgentDefinitionRecordDto` and `ZeebeAgentDefinitionDataDto`
- `ZeebeAgentDefinitionFetcher`, plus `...FetcherES` and an OS stub
- `ZeebeAgentDefinitionImportIndexHandler`
- `ZeebeAgentDefinitionImportMediator` (rank `DEFINITION`) and its `...MediatorFactory`
- `ZeebeAgentDefinitionImportService` and `AgentDefinitionDatabaseImportJob`
- `AgentDefinitionWriter`: a DB-agnostic `ImportRequestDto` UPDATE with upsert and a painless merge script

**Slice 3: agent instance import**
- Add `agentDefinitionKey`, `limits` and the full metrics to the Zeebe DTO. They are dropped or `@JsonIgnore`d today.
- A second import service on the same fetched batch: `ZeebeAgentInstanceFlatImportService`. It is a new mediator and factory with its own position index (`zeebeAgentInstanceFlatImportIndex`), so the Phase 1 nested import stays untouched and both read `zeebe-record-agent-instance`.
- It groups records by `agentInstanceId` and folds them in position order into one partial doc.
- `AgentInstanceWriter` upserts with a painless merge:
  - newer `lastRecordPosition` wins for config, metrics and status
  - `startDate` = min, `lastActivityDate` = max
  - `endDate` is set once
  - tool IDs are a union
  - derived fields are recomputed (`totalTokens`, `durationMs`, `hitLimit`)
- In the same job it updates the agent-definition rollups.

**Slice 4: enrichment and catch-up, one service for both paths**
- `AgentInstanceEnrichmentService.enrich(processInstanceIds)`:
  1. Search `optimize-agent-instance` for the runs of those process instances.
  2. `mget` the process-instance docs (per-definition alias; real-time get).
  3. Recompute and partially update each run:
     - **Process state and end date** from the process instance.
     - **Incidents:** `activityId` ∈ `flowNodeInstanceIds`. If `activityId` was already rewritten to an element ID, it is mapped back through `flowNodeInstances`.
     - **User task count:** `flowNodeType == userTask` in the process instance.
     - **Tool calls:** `toolCallsByTool` = count of `flowNodeInstances` with `flowNodeId` ∈ `toolFlowNodeIds`.
- **Main path:** the flat agent import job calls it for its own process instances after writing.
- **Catch-up path:** one hook in `ProcessInstanceDatabaseImportJob` after `persistEntities`.
  - Every sub-entity importer (process instance/flow nodes, incidents, user tasks, variables) goes through it, so late incidents, user tasks, tool flow nodes and process completion all re-enrich.
  - It is a cheap terms query on the agent index, a no-op for non-agentic process instances.
- **Idempotent:** a full recompute from the process-instance doc each time, so ordering doesn't matter.
- **Remaining race:** two concurrent enrich calls for the same process instance can write in either order. Both read real-time process-instance state, so the later write is at worst one import cycle stale. A refresh-lag window on the catch-up search is covered by the main path's own `mget`.

## 5. Report engine (slice 5)

**New pieces**
- **View entity:** `ProcessViewEntity.AGENT_RUN`, a new id so command keys don't collide with Phase 1's `AGENT_INSTANCE`.
- **New `ViewProperty` values:** `MODEL_CALLS`, `USER_TASKS`.
- **Reused `ViewProperty` values:** `FREQUENCY`, `PERCENTAGE`, `DURATION`, `TOTAL_TOKENS`, `TOOL_CALLS`.
- **New group-by types:** `agentRunAgent` (terms `agentId`, labelled from the agent-definition `name`), `agentRunModel` (terms on `provider` + `model`) and `agentRunTool` (nested terms on `toolCallsByTool.toolFlowNodeId`, sum of `count`).
- **Reused group-bys:** `none` and `startDate` (date histogram on the agent doc's `startDate`).
- **`AgentRunExecutionPlanInterpreterES`** (extends `AbstractProcessExecutionPlanInterpreterES`):
  - `getIndexNames` → `optimize-agent-instance`.
  - `createDefinitionQuery` is made overridable so it uses `AgentInstanceIndex` field names.
  - Plans are not added to the Generic set.
- **Filters:**
  - Existing `instanceEndDate`, `instanceStartDate` and `completedInstancesOnly` work unchanged, because the agent doc reuses `startDate`, `endDate` and the definition field names.
  - New `AgentRunFilterDto` (`agentIds[]`, `models[]`, `providers[]`, `hasIncident`, `hitLimit`) with an ES bean. It is a no-op on the process-instance index.
- **OS stubs:** only where wiring requires them; marked `// prototype: ES only`.

**Metric → query** (every report has filter `completedInstancesOnly`, i.e. `endDate` exists; base query = definitions, versions, tenants and the dashboard date range on `endDate`)

| # | Tile | View / group-by | Query |
|---|---|---|---|
| 1 | Agent incident rate | AGENT_RUN/PERCENTAGE, none | count(filter `hasIncident`) ÷ unfiltered run count (existing baseline mechanism) |
| 2 | Runs hitting a limit | AGENT_RUN/PERCENTAGE, none | count(filter `hitLimit`) ÷ run count |
| 3 | Run duration P50/P95 | AGENT_RUN/DURATION, none | percentiles on `durationMs` (aggregation types P50, P95) |
| 4 | Avg iterations per run | AGENT_RUN/MODEL_CALLS, none | avg `modelCalls` (null runs excluded; the spec's "sum ÷ runs" equals avg over non-null) |
| 5 | Human steps per run | AGENT_RUN/USER_TASKS, none | avg `processUserTaskCount` |
| 6a | Agent runs over time | AGENT_RUN/FREQUENCY, startDate | date histogram on `startDate` |
| 6b | Agent runs by agent | AGENT_RUN/FREQUENCY, agentRunAgent | terms `agentId` |
| 7 | Tool calls per tool | AGENT_RUN/TOOL_CALLS, agentRunTool | nested terms → sum `count` |
| 8 | Agentic adoption rate | AGENT_RUN/ADOPTION (new property), none | cardinality(`processInstanceId`) on the agent index ÷ count on the process-instance index (two searches in the interpreter) |
| 9 | Avg tokens per run | AGENT_RUN/TOTAL_TOKENS, none | avg `totalTokens` |
| 10 | Token usage over time | AGENT_RUN/[TOTAL_TOKENS, FREQUENCY], startDate | date histogram: sum `totalTokens` + doc count (two measures, one LINE tile) |
| 11 | Token usage per model | AGENT_RUN/TOTAL_TOKENS, agentRunModel | nested `tokensByModel` → terms `provider`/`model` → sum `totalTokens` (tokens split by the model in effect when they were reported, not by the run's final model) |

**Adoption (#8) in detail**
- The denominator is completed process instances, with the same definitions, versions, tenants and date range.
- It is restricted to process definitions that have an agent definition (from `optimize-agent-definition`).
- Agent and model filters apply to the numerator only.

## 6. Dashboard and frontend (slice 6)

**Backend**
- `AgenticControlDashboardService` seeds 12 reports (metric 6 = two tiles) in sections `agentHealth`, `volume` and `costAndUsage`.
- It uses new deterministic IDs and deletes the Phase 1 tile reports on reconcile.
- New endpoint `GET api/dashboard/agentic/filter-options?processDefinitionKey=…`. It is used for filter dropdowns only, not tiles:
  - agents come from the agent-definition index
  - models and providers come from a terms aggregation on the agent-instance index

**`AgenticControlPlane.tsx`**
- Three sections replace the four.
- The token-trend loader is reused for `groupByDateUnit` on the time tiles.

**`FilterBar.tsx`**
- Adds **Agent** (multi-select) and **Model / provider** (multi-select) next to process, version and date.
- Selections go to `evaluateReport` as an extra `agentRun` filter in `filter`.
- `useComparisonData` keeps them.

## 7. Data generator (slice 7)

**`AGENT_DEFINITION`**
- `AGENT_DEFINITION CREATED` per process version, for the three agents, written to `zeebe-record-agent-definition`.
- The `agentType` values are `AI_AGENT_SUB_PROCESS`, `AI_AGENT_TASK` and `EXTERNAL_AGENT`.

**`AGENT_INSTANCE` records**
- New fields: `agentDefinitionKey`, `rootProcessInstanceKey` (= `processInstanceKey`, since the demo has no call activities), `elementInstanceKeys[]`.
- Correct `processDefinitionVersion`; it is hard-coded to 1 today.
- Limits mix: about 40% all `-1`, the rest real. About 10% of runs are driven to reach one, with `maxModelCalls` hit most often.
- Reasoning and cache tokens:
  - about 30% of runs have no cache
  - about 20% are cache-heavy
  - reasoning is only on reasoning-capable models, and stays ≤ output
- `changedAttributes` per `UPDATED`:
  - `["metrics","status"]` for model/tool rounds
  - `["model","provider"]` config commits, with about 8% of runs switching Bedrock → OpenAI mid-run
- Timestamps sit inside the agent element's window. The last metrics `UPDATED` falls well before process end, and `COMPLETED` is at process end.

**Tool flow nodes**
- Ad-hoc sub-process children are emitted only for tools the run actually called, with uneven per-tool counts.
- The total matches `toolCalls`.

**Incidents**
- About 12% of runs get an incident on the agent element instance.
- About a third of those are created after the agent's last activity, to exercise catch-up.

**User tasks and docs**
- User tasks are already present in the agentic processes; their count is kept.
- README drift is fixed for the touched options.

## 8. Testing

- **Unit tests:**
  - folding records into an agent doc: latest model, `lastActivityDate` rules, null vs. 0, `totalTokens`, `hitLimit`
  - enrichment computation from a `ProcessInstanceDto`
  - `OptimizeModeFilter`
  - each new view / group-by interpreter's aggregation
  - the dashboard seeding test updated
- No new ITs.
- Phase 1 tile ITs (`Agentic*KpiTilesIT`) will no longer match the dashboard and are not run or updated in the prototype.
- End to end:
  - run ES + Optimize locally with generated data
  - screenshot the dashboard
  - hand-check every tile with a raw ES query

## 9. Decisions (reviewed 2026-10-23) and risks

- **Completed only:** same as today's `completedInstancesOnly`. Agreed.
- **Agent-element incidents only.** Agreed.
- **Token usage per model** is grouped by model across runs via `tokensByModel` (§3). It is not attributed to the run's final model.
- **ITs are ignored** for the prototype.
- **Adoption denominator** is restricted to process definitions with ≥1 agent definition. Agreed.

1. **Tool-call scoping:** same as Phase 1's heatmap, matching tool flow nodes by `flowNodeId` within the process instance.
   - This is exact when one run per agent element per process instance, which is true for the demo and for typical models.
   - It over-counts when a multi-instance or re-entered agent element creates several runs in one process instance.
   - Scoping by ad-hoc sub-process element instance would need `flowScopeKey` added to the nested `flowNodeInstances` (mapping + import change on the Phase 1 process-instance index). Even then it only helps sub-process agents, not AI Agent Tasks.
   - Recommendation: element-ID matching now, documented.
2. **"Completed process instances only"** = `endDate` exists. This includes cancelled process instances, matching Phase 1's `completedInstancesOnly`. The alternative is `processInstanceState == COMPLETED`.
3. **Adoption denominator** restricted to process definitions with ≥1 agent definition (otherwise non-agentic processes dilute it in the all-processes view).
4. **Model per run is the latest model.** A run that switches model attributes all tokens to the final model in tile 11. A per-model split needs `AGENT_HISTORY`, which is out of scope.
5. **Agent incident scope** = incidents on the agent element instance(s) only. Incidents on tool elements inside the sub-process are not counted.
6. **Legacy Azure/Vertex token paths:** cache is not split out, and Vertex misses thinking tokens. This is a connector issue, not an Optimize one.
7. **Production gaps:**
   - OpenSearch twins
   - ITs
   - backfill of the flat indices from existing `zeebe-record` data; the new position index starts at 0, so it backfills naturally while records are retained
   - `DELETED` agent definitions
   - multi-tenant authorization on the filter-options endpoint
   - a migration plan for removing the nested model

## 10. Prototype results (2026-10-23)

### Setup

- 2,000 generated process instances, `--agent-rate 0.3`, which gives 282 agent runs (275 in finished process instances).
- Optimize was run from source against ES 8.19, with Keycloak and Identity.
- Every tile was evaluated through `POST api/report/{id}/evaluate` without a date filter. The results were compared with an independent recomputation from the raw `zeebe-record-*` indices.

### Results

| Tile | Optimize | Raw-record check |
|---|---|---|
| Agent incident rate | 10.91 % | 10.91 % |
| Runs hitting a limit | 13.09 % | 13.09 % |
| Run duration P50 / P95 | 27,560,920 / 82,010,906 ms | same |
| Avg iterations per run | 3.55 | 3.55 |
| Human steps per run | 1.21 | 1.21 |
| Agent runs | 275 | 275 |
| Agentic adoption rate | 28.5 % | 28.5 % |
| Agent runs by agent | 98 / 97 / 80 | same |
| Tool calls per tool | 179 / 110 / 48 / 47 | same |
| Avg tokens per run | 7,444.89 | 7,444.89 |
| Token usage over time (sum of buckets) | 2,047,345 | 2,047,345 |
| Token usage per model | 547,278 / 507,679 / 407,230 / 311,747 / 273,411 | same |

The filters were also checked against ES:

- agent filter: 97 runs
- model filter `o3-mini`: 47 runs
- last 30 days: 45 runs

### Findings

- **Refresh-lag race.** The race described in §4 happened in practice: 9 of 282 runs stayed without process state. It was fixed by repeating each catch-up after 3 s (`53f2355`).
- **Tool elements.** Only the ad-hoc sub-process template has tool elements. Tool calls of AI Agent Task and External Agent runs therefore only show in the run's `toolCalls` total, not in "Tool calls per tool".
- **Adoption with an agent filter.** The agent/model selection narrows the numerator only, so "adoption" for one agent is that agent's share of all agentic process instances.
