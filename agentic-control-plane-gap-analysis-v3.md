# Agentic Control Plane — Implementation Analysis (v3)

**Source documents**
- UI mockups: `agentic-control-plane-1.png` through `agentic-control-plane-6.png`
- Zeebe record design: AgentInstance record (agreed 2026-05-05 with Fabio Paini, Dima Melnychuk, Dmitri Nikonov)
- Schema analysis: metric feasibility table (metrics #25–#33)
- Requirements reference: agent-visibility-metrics-reference.md

**Key architectural premise:** The Zeebe AgentInstance record is the source of truth. Records are exported to Elasticsearch and imported into Optimize. All analysis is grounded in what the record provides, not the Operate API shape.

---

## Zeebe AgentInstance Record — Current Schema

**Intents:** `CREATE → CREATED`, `UPDATE → UPDATED`, `COMPLETE → COMPLETED`

|                 Section                 |                                                                                 Fields                                                                                 |
|-----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Identity**                            | `agentInstanceKey`, `elementInstanceKey`, `elementId`, `processInstanceKey`, `processDefinitionKey`, `tenantId` — optionally: `processDefinitionVersion`, `versionTag` |
| **Status**                              | `INITIALIZING`, `TOOL_DISCOVERY`, `THINKING`, `TOOL_CALLING`, `IDLE`, `COMPLETED`                                                                                      |
| **Definition** *(set once at creation)* | `model`, `provider`, `systemPrompt`                                                                                                                                    |
| **Limits** *(set once; -1 = unset)*     | `maxTokens`, `maxModelCalls`, `maxToolCalls`                                                                                                                           |
| **Metrics** *(dual semantics)*          | `inputTokens`, `outputTokens`, `modelCalls`, `toolCalls` — connector sends deltas on UPDATE; engine accumulates totals on UPDATED events                               |

**Explicitly out of scope for now (future iteration):** conversation history, per-iteration token breakdown, per-tool call detail.

---

## Metric Reference

This table is the schema analysis backing each UI element. Phase-1 = implementable with the current record on first export slice.

| #  |               Metric               |             Phase-1 feasible?             |                                                        Fields used                                                        |                                               Gaps                                                |
|----|------------------------------------|-------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| 25 | Total runs                         | ✅ Yes                                     | `agentInstanceKey`, `processDefinitionKey` (+ `processDefinitionVersion` later)                                           | G10: without version, counting is per definition key only                                         |
| 27 | Avg / median tokens per run        | ✅ Yes (input+output only)                 | `inputTokens`, `outputTokens`, `processInstanceKey`                                                                       | G8: reasoning tokens need a new field for full cost picture                                       |
| 28 | Token outlier bands (p5/p95)       | ✅ Yes (input+output only)                 | `inputTokens`, `outputTokens`, `processInstanceKey`                                                                       | G8: same as #27                                                                                   |
| 29 | Token trend over time              | ✅ Yes (input+output only)                 | `inputTokens`, `outputTokens`, `creationDate`, `completionDate`, `model`, `provider`, `elementId`                         | G8: trend excludes reasoning tokens until field is added                                          |
| 30 | Avg / p50 / p95 duration           | ✅ Yes (derived from timestamps)           | `creationDate`, `completionDate`                                                                                          | G1: `totalDurationMs` as a first-class metric is a nice-to-have, not a blocker                    |
| 31 | Duration trend                     | ✅ Yes (derived from timestamps)           | `creationDate`, `completionDate`                                                                                          | G1: same as #30                                                                                   |
| 32 | Incident / failure rate            | ⚠️ Partial (requires incident join today) | Today: `agentInstanceKey`, `elementInstanceKey`. Future: `agentIncidentCount`, `toolCallIncidentCount`, `status = FAILED` | G4, G7: without explicit counters + FAILED status, rate is computed by joining the incident index |
| 33 | Tool call frequency / distribution | ⚠️ Two layers: aggregate ✅, per-tool ➡️   | Coarse: `toolCalls`. Per-tool: history index (`toolName`, `elementId`); optionally `definition.tools[]`                   | G6: no per-tool breakdown in AgentInstance; history export needed for full distribution           |

---

## How to read this document

|       Symbol        |                                         Meaning                                          |
|---------------------|------------------------------------------------------------------------------------------|
| ✅ Achievable        | All required data is present in the current record; implementable in phase 1             |
| ⚠️ Partial          | Achievable but requires joining another Zeebe record type or promoting an optional field |
| ❌ Gap               | Required data cannot be derived from the current record; a schema addition is needed     |
| ➡️ Future iteration | Depends on conversation/history records not yet designed                                 |

---

## Dashboard Filter States and Drill-Down Behaviour

The Dashboard tab has two dropdowns — **Process** and **Agent** — that control what is shown. Certain charts appear, disappear, or change their grouping axis depending on the selected filter level.

|         Level         |  Process filter  |  Agent filter  | Mockup  |
|-----------------------|------------------|----------------|---------|
| **L0 — Fleet view**   | All processes    | *(not shown)*  | Image 1 |
| **L1 — Process view** | Specific process | All agents     | Image 2 |
| **L2 — Agent view**   | Specific process | Specific agent | Image 3 |

The Agents tab (image 4) is a separate list view independent of the Dashboard filter state.

---

## L0 — Fleet View (All Processes)

*Filter: Process = "All processes". No Agent filter.*

### KPI Cards

|       Card       | Metric # | Phase-1 |                                                         Implementation                                                          |                                                                  Notes                                                                  |
|------------------|----------|---------|---------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Total Runs       | #25      | ✅       | Count of COMPLETED AgentInstance records per `processDefinitionKey` in the period. WoW delta = same query for the prior window. | Counts per definition key; version breakdown requires G10.                                                                              |
| Avg Run Duration | #30      | ✅       | Mean of (`completionDate` − `creationDate`) across all COMPLETED instances.                                                     | No schema change needed. `totalDurationMs` (G1) would make this a first-class metric but is not required.                               |
| Incident Rate    | #32      | ⚠️      | Join incident index on `elementInstanceKey`; flag instances with ≥1 incident; divide by total runs.                             | Phase-1 needs incident join. Adding `agentIncidentCount` (G4) and `FAILED` status (G7) would make this self-contained in a later slice. |

### Token Usage section

|                     Chart                      | Metric # | Phase-1 |                                                                                  Implementation                                                                                   |                            Notes                            |
|------------------------------------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------|
| Avg Tokens per Run                             | #27      | ✅       | Mean of (`inputTokens` + `outputTokens`) per COMPLETED instance.                                                                                                                  | Input+output only. Reasoning tokens (G8) extend this later. |
| Median Tokens per Run                          | #27      | ✅       | p50 of (`inputTokens` + `outputTokens`) per instance.                                                                                                                             | Same.                                                       |
| Token trend                                    | #29      | ✅       | Bucket instances by `creationDate`; sum `inputTokens` and `outputTokens` per bucket; two series. Can be broken down by `definition.model`, `definition.provider`, or `elementId`. | Reasoning tokens missing until G8 is added.                 |
| Token outlier bands (p0/p50/p95)               | #28      | ✅       | Percentile of (`inputTokens` + `outputTokens`) per instance, per time bucket.                                                                                                     | Same G8 caveat.                                             |
| **Top token consumers by process** *(L0 only)* | #27      | ✅       | Group instances by `processDefinitionKey`; sum `inputTokens` + `outputTokens`; sort descending. Join process definition record for display name.                                  | Replaced at L1/L2.                                          |

### Reliability & Tool Calls section

|        Chart        | Metric # | Phase-1 |                                                                                                  Implementation                                                                                                   |                                           Notes                                           |
|---------------------|----------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| Tool call frequency | #33      | ⚠️      | **Coarse view (phase 1):** sum `toolCalls` per `elementId` across all instances — gives total calls per agent, not per tool. **Per-tool view:** requires history index export (`toolName`, `elementId` per call). | `toolCalls` in the record is aggregate. True per-tool distribution is a future iteration. |

### Duration section

|                      Chart                       | Metric # | Phase-1 |                             Implementation                              | Notes |
|--------------------------------------------------|----------|---------|-------------------------------------------------------------------------|-------|
| Duration P50                                     | #30      | ✅       | p50 of (`completionDate` − `creationDate`) across COMPLETED instances.  |       |
| Duration P95                                     | #30      | ✅       | p95 of same.                                                            |       |
| Execution duration stability (p50/p95 over time) | #31      | ✅       | Same delta bucketed by `creationDate`; two percentile series over time. |       |

---

## L1 — Process View (Specific Process, All Agents)

*Filter: Process = specific. Agent = "All agents". All queries filtered to `processDefinitionKey = <selected>`.*

### Chart changes from L0

Two changes happen when a specific process is selected:

1. **"Top token consumers by process" is replaced** by **"Avg Tokens per Call to Agent"** — the y-axis becomes agent name (`elementId`), the x-axis is average tokens per LLM call.
2. **"Failure rate by process version" appears** — a new bar chart that does not exist at L0.

### Token Usage section

|                            Chart                            | Metric # | Phase-1 |                                                                                    Implementation                                                                                     |                                          Notes                                          |
|-------------------------------------------------------------|----------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|
| Token trend                                                 | #29      | ✅       | Same as L0, filtered to selected process.                                                                                                                                             |                                                                                         |
| Token outlier bands                                         | #28      | ✅       | Same as L0, filtered.                                                                                                                                                                 |                                                                                         |
| **Avg Tokens per Call to Agent** *(replaces Top consumers)* | #27      | ✅       | Per instance: `(inputTokens + outputTokens) / modelCalls`. Group by `elementId`; average across instances. Join BPMN element name for display. `modelCalls` is present in the record. | Requires `modelCalls > 0` guard to avoid divide-by-zero on instances with no LLM calls. |

### Reliability & Tool Calls section

|                       Chart                       | Metric # | Phase-1 |                                                  Implementation                                                   |                                                                        Notes                                                                         |
|---------------------------------------------------|----------|---------|-------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Failure rate by process version** *(new at L1)* | #32      | ⚠️      | Group instances by `processDefinitionVersion`; failure rate = instances with joined incident / total per version. | Requires (a) incident join and (b) `processDefinitionVersion` promoted from optional to required in Identity. Without version, bars cannot be drawn. |
| Tool call frequency                               | #33      | ⚠️      | Same as L0, filtered to this process.                                                                             | Same coarse/future-iteration split.                                                                                                                  |

### Duration, KPI cards

✅ Same as L0, filtered to the selected process. No additional data requirements.

---

## L2 — Agent View (Specific Process, Specific Agent)

*Filter: Process = specific. Agent = specific. All queries further filtered to `elementId = <selected>`.*

The layout is **identical to L1** — no charts are added or removed at this level. All charts from L1 are present and further filtered.

|              Chart              |                                                                       Change from L1                                                                        |
|---------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Avg Tokens per Call to Agent    | Now shows a single-agent result; may pivot y-axis to `processDefinitionVersion` to show evolution across deployments (not shown in mockup, but natural UX). |
| Failure rate by process version | Filtered to this agent's runs per version. Same data requirements as L1.                                                                                    |
| All others                      | Filtered; same implementation.                                                                                                                              |

---

## Agents Tab — Agent List

*Independent of Dashboard filter. Image 4.*

**Columns:** AGENT NAME | STATUS | RUNS | SUCCESS | INCIDENTS

|          Column          | Metric # | Phase-1 |                                                 Implementation                                                  |                              Notes                               |
|--------------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------|
| Agent Name / Last active | —        | ✅       | `elementId` → BPMN element name. Last active = latest `creationDate` for that `elementId`.                      |                                                                  |
| Runs                     | #25      | ✅       | Count of COMPLETED records per `elementId`. Each COMPLETED record = one agent run.                              | Per-agent counting is precise; no process-level fallback needed. |
| Success %                | #32      | ⚠️      | `count(instances with no joined incident) / count(all)` per `elementId`.                                        | Incident join required (see §32).                                |
| Incidents                | #32      | ⚠️      | Sum of joined incident records per `elementId`.                                                                 | Same join.                                                       |
| Status badge             | —        | ⚠️ + ❌  | Healthy/Degraded/Failing derived from incident rate + record status. **FAILED status missing** (G7); see below. |                                                                  |

### Status Badge Logic

|    Badge     |                   Derivation                   |           Phase-1           |
|--------------|------------------------------------------------|-----------------------------|
| **Healthy**  | No joined incidents; all recent runs COMPLETED | ⚠️ Incident join            |
| **Degraded** | Incident rate > 0 but agent still completing   | ⚠️ Incident join            |
| **Failing**  | High incident rate OR `status = FAILED`        | ❌ FAILED status not in enum |

**Gap G7:** `FAILED` is absent from the current status enum. An agent stopped by an unrecoverable error (retries exhausted, limit exceeded) has no terminal error state. Without it, Optimize cannot distinguish a stuck/failed agent from one that simply has not yet completed. The Failing badge cannot be driven by record status alone.

---

## Required Record Changes

| Gap |                            Change                             |                                         Rationale                                         |                  Affected UI                   |            Priority             |
|-----|---------------------------------------------------------------|-------------------------------------------------------------------------------------------|------------------------------------------------|---------------------------------|
| G7  | Add `FAILED` to the status enum                               | Terminal error state; Failing badge needs an API-driven signal                            | Agent list — Failing badge                     | High                            |
| G4  | Add `agentIncidentCount` + `toolCallIncidentCount` to metrics | Incident rate and Success % as first-class agent metrics without an index join            | Incident Rate KPI, Success %, Incidents column | Medium (phase 2)                |
| G10 | Promote `processDefinitionVersion` to required in Identity    | Failure rate by process version chart cannot group instances without it                   | Failure rate by process version                | Medium                          |
| G8  | Add `reasoningTokens` to metrics                              | Full cost picture for token KPIs and trends                                               | Token KPIs, token trend, outlier bands         | Medium (phase 2)                |
| G1  | Add `totalDurationMs` to metrics                              | First-class duration metric reusable across Operate/Optimize without timestamp arithmetic | Duration KPIs (nice-to-have)                   | Low (not blocking)              |
| G6  | Add `definition.tools[]` to the record                        | "Configured vs used" tool comparison; y-axis labels for per-tool chart                    | Tool call frequency (per-tool view)            | Low (depends on history export) |

---

## Full Achievability Summary

|           UI element            |      L0       |      L1      |      L2      | Agents tab |               Gaps               |
|---------------------------------|---------------|--------------|--------------|------------|----------------------------------|
| Total Runs KPI                  | ✅             | ✅            | ✅            | —          | G10 for version breakdown        |
| Avg Run Duration KPI            | ✅             | ✅            | ✅            | —          | G1 nice-to-have                  |
| Incident Rate KPI               | ⚠️            | ⚠️           | ⚠️           | —          | G4, G7 for self-contained metric |
| Avg / Median tokens per run     | ✅             | ✅            | ✅            | —          | G8 for reasoning                 |
| Token trend                     | ✅             | ✅            | ✅            | —          | G8 for reasoning                 |
| Token outlier bands             | ✅             | ✅            | ✅            | —          | G8 for reasoning                 |
| Top consumers by process        | ✅             | *(replaced)* | *(replaced)* | —          | —                                |
| Avg Tokens per Call to Agent    | *(not shown)* | ✅            | ✅            | —          | —                                |
| Tool call frequency (aggregate) | ✅             | ✅            | ✅            | —          | —                                |
| Tool call frequency (per tool)  | ➡️            | ➡️           | ➡️           | —          | G6 + history export              |
| Failure rate by process version | *(not shown)* | ⚠️           | ⚠️           | —          | G10 + incident join              |
| Duration P50 / P95              | ✅             | ✅            | ✅            | —          | G1 nice-to-have                  |
| Duration stability chart        | ✅             | ✅            | ✅            | —          | G1 nice-to-have                  |
| Agent Name / Last active        | —             | —            | —            | ✅          | —                                |
| Status badge                    | —             | —            | —            | ⚠️ + ❌     | G7 (High), G4 (Medium)           |
| Runs                            | —             | —            | —            | ✅          | —                                |
| Success %                       | —             | —            | —            | ⚠️         | G4 for self-contained            |
| Incidents count                 | —             | —            | —            | ⚠️         | G4 for self-contained            |

