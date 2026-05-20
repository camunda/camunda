# Agentic Control Plane — Implementation Analysis (v4)

**Source documents**
- UI mockups: `agentic-control-plane-1.png` through `agentic-control-plane-6.png`
- Zeebe record design: AgentInstance record (agreed 2026-05-05 with Fabio Paini, Dima Melnychuk, Dmitri Nikonov)
- Schema analysis: metric feasibility table (metrics #25–#33)
- Requirements reference: agent-visibility-metrics-reference.md

**Key architectural premise:** The Zeebe AgentInstance record is the source of truth. Records are exported to Elasticsearch and imported into Optimize. All analysis is grounded in what the record provides.

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

## On Incident Tracking — No Join Required for Phase 1

The dashboard needs two distinct signals, solved differently:

|                                       Signal                                       |               Phase 1 approach               |     Needs join?      |           Schema requirement            |
|------------------------------------------------------------------------------------|----------------------------------------------|----------------------|-----------------------------------------|
| **Failure rate** — runs that stopped and could not complete                        | `count(status = FAILED) / count(all)`        | ❌ No                 | G7: add `FAILED` to status enum         |
| **Any-incident rate** — runs that had an incident, even if recovered and completed | `count(agentIncidentCount > 0) / count(all)` | ❌ No (once G4 added) | G4: add `agentIncidentCount` to metrics |

For phase 1 the dashboard's "Incident Rate" KPI and "Success %" column can be implemented as failure rate using `status = FAILED` — no cross-index join against the incident record index is needed. G4 extends this in a later slice to catch recoverable incidents (agent raised an incident, retried, and still COMPLETED).

---

## Metric Reference

| #  |               Metric               |      Phase-1 feasible?      |                                            Fields used                                            |                                Gaps                                 |
|----|------------------------------------|-----------------------------|---------------------------------------------------------------------------------------------------|---------------------------------------------------------------------|
| 25 | Total runs                         | ✅ Yes                       | `agentInstanceKey`, `processDefinitionKey`                                                        | G10: version breakdown needs `processDefinitionVersion`             |
| 27 | Avg / median tokens per run        | ✅ Yes (input+output only)   | `inputTokens`, `outputTokens`, `processInstanceKey`                                               | G8: reasoning tokens deferred                                       |
| 28 | Token outlier bands (p5/p95)       | ✅ Yes (input+output only)   | `inputTokens`, `outputTokens`, `processInstanceKey`                                               | G8: same                                                            |
| 29 | Token trend over time              | ✅ Yes (input+output only)   | `inputTokens`, `outputTokens`, `creationDate`, `completionDate`, `model`, `provider`, `elementId` | G8: trend excludes reasoning tokens                                 |
| 30 | Avg / p50 / p95 duration           | ✅ Yes (derived)             | `creationDate`, `completionDate`                                                                  | G1: `totalDurationMs` is nice-to-have, not blocking                 |
| 31 | Duration trend                     | ✅ Yes (derived)             | `creationDate`, `completionDate`                                                                  | G1: same                                                            |
| 32 | Failure rate                       | ✅ Phase 1 with G7           | `status` (after adding `FAILED`)                                                                  | G7: blocking for phase 1; G4 extends to recoverable incidents later |
| 33 | Tool call frequency / distribution | ⚠️ Aggregate ✅, per-tool ➡️ | Coarse: `toolCalls`. Per-tool: history index                                                      | G6 + history export for per-tool view                               |

---

## How to read this document

|       Symbol        |                                            Meaning                                            |
|---------------------|-----------------------------------------------------------------------------------------------|
| ✅ Achievable        | All required data is present in the current record; implementable in phase 1                  |
| ⚠️ Partial          | Achievable with a limitation noted (e.g., aggregate only, or optional field must be promoted) |
| ❌ Gap               | Required data cannot be derived from the current record; a schema addition is needed          |
| ➡️ Future iteration | Depends on conversation/history records not yet designed                                      |

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

### KPI Cards

|       Card       | Metric # | Phase-1 |                                                                      Implementation                                                                      |
|------------------|----------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| Total Runs       | #25      | ✅       | Count of COMPLETED AgentInstance records per `processDefinitionKey` in the period. WoW delta = same query for the prior window.                          |
| Avg Run Duration | #30      | ✅       | Mean of (`completionDate` − `creationDate`) across COMPLETED instances. `totalDurationMs` (G1) would make this a first-class metric but is not required. |
| Incident Rate    | #32      | ❌ G7    | `count(status = FAILED) / count(all)`. Self-contained once `FAILED` is added to the status enum. No cross-index join needed.                             |

### Token Usage section

|                     Chart                      | Metric # | Phase-1 |                                                                                  Implementation                                                                                   |
|------------------------------------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Avg Tokens per Run                             | #27      | ✅       | Mean of (`inputTokens` + `outputTokens`) per COMPLETED instance. Input+output only; G8 adds reasoning tokens later.                                                               |
| Median Tokens per Run                          | #27      | ✅       | p50 of the same.                                                                                                                                                                  |
| Token trend                                    | #29      | ✅       | Bucket instances by `creationDate`; sum `inputTokens` and `outputTokens` per bucket; two series. Can be broken down by `definition.model`, `definition.provider`, or `elementId`. |
| Token outlier bands (p0/p50/p95)               | #28      | ✅       | Percentile of (`inputTokens` + `outputTokens`) per instance, per time bucket.                                                                                                     |
| **Top token consumers by process** *(L0 only)* | #27      | ✅       | Group by `processDefinitionKey`; sum `inputTokens` + `outputTokens`; sort descending. Join process definition record for display name. Replaced at L1/L2.                         |

### Reliability & Tool Calls section

|        Chart        | Metric # | Phase-1 |                                                                                           Implementation                                                                                            |
|---------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tool call frequency | #33      | ⚠️      | **Phase 1 (aggregate):** sum `toolCalls` per `elementId` — total calls per agent, not per tool. **Per-tool view:** requires history index export. True per-tool distribution is a future iteration. |

### Duration section

|                       Chart                       | Metric # | Phase-1 |                             Implementation                              |
|---------------------------------------------------|----------|---------|-------------------------------------------------------------------------|
| Duration P50                                      | #30      | ✅       | p50 of (`completionDate` − `creationDate`) across COMPLETED instances.  |
| Duration P95                                      | #30      | ✅       | p95 of the same.                                                        |
| Execution duration stability (p50/p95 line chart) | #31      | ✅       | Same delta bucketed by `creationDate`; two percentile series over time. |

---

## L1 — Process View (Specific Process, All Agents)

*All queries filtered to `processDefinitionKey = <selected>`.*

### Chart changes from L0

|    Change    |                                 Chart                                 |
|--------------|-----------------------------------------------------------------------|
| **Replaced** | "Top token consumers by process" → **"Avg Tokens per Call to Agent"** |
| **Added**    | **"Failure rate by process version"** (does not exist at L0)          |

### Token Usage section

|              Chart               | Metric # | Phase-1 |                                                   Implementation                                                    |
|----------------------------------|----------|---------|---------------------------------------------------------------------------------------------------------------------|
| Token trend                      | #29      | ✅       | Same as L0, filtered to selected process.                                                                           |
| Token outlier bands              | #28      | ✅       | Same as L0, filtered.                                                                                               |
| **Avg Tokens per Call to Agent** | #27      | ✅       | Per instance: `(inputTokens + outputTokens) / modelCalls`; average per `elementId`. Guard against `modelCalls = 0`. |

### Reliability & Tool Calls section

|                Chart                | Metric # | Phase-1 |                                                                                              Implementation                                                                                               |
|-------------------------------------|----------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Failure rate by process version** | #32      | ⚠️ G10  | Group instances by `processDefinitionVersion`; rate = `count(status = FAILED) / count(all)` per version. Requires `processDefinitionVersion` promoted from optional to required. No incident join needed. |
| Tool call frequency                 | #33      | ⚠️      | Same as L0, filtered. Same aggregate-only limitation.                                                                                                                                                     |

### Duration and KPI cards

✅ Same as L0, filtered to the selected process.

---

## L2 — Agent View (Specific Process, Specific Agent)

*All queries further filtered to `elementId = <selected>`. Layout identical to L1 — no charts added or removed.*

|              Chart              |                                                 Change from L1                                                  |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------|
| Avg Tokens per Call to Agent    | Single-agent result; y-axis may pivot to `processDefinitionVersion` to show token evolution across deployments. |
| Failure rate by process version | Filtered to this agent's runs per version. Same implementation.                                                 |
| All others                      | Filtered; same implementation.                                                                                  |

---

## Agents Tab — Agent List

*Independent of Dashboard filter. Image 4.*

**Columns:** AGENT NAME | STATUS | RUNS | SUCCESS | INCIDENTS

|          Column          |            Phase-1            |                                                                                                     Implementation                                                                                                     |
|--------------------------|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Agent Name / Last active | ✅                             | `elementId` → BPMN element name. Last active = latest `creationDate` for that `elementId`.                                                                                                                             |
| Runs                     | ✅                             | Count of COMPLETED records per `elementId` in the period.                                                                                                                                                              |
| Success %                | ❌ G7                          | `count(status = COMPLETED) / count(all)` per `elementId`. Self-contained once `FAILED` is in the enum — a run is successful if it reached COMPLETED and not FAILED.                                                    |
| Incidents                | ❌ G7 (phase 1) / G4 (phase 2) | **Phase 1:** count of `status = FAILED` runs per `elementId` (failed runs as incident proxy). **Phase 2:** sum of `agentIncidentCount` per `elementId` for true per-run incident counts including recovered incidents. |
| Status badge             | ❌ G7                          | See below.                                                                                                                                                                                                             |

### Status Badge Logic

|    Badge     |                      Derivation                      | Needs |
|--------------|------------------------------------------------------|-------|
| **Healthy**  | All recent runs reached COMPLETED; failure rate ≈ 0  | G7    |
| **Degraded** | Some runs reached FAILED but rate is below threshold | G7    |
| **Failing**  | High failure rate or latest run is currently FAILED  | G7    |

All three badge states are derivable from the `status` field alone once `FAILED` is added. No incident index join required.

---

## Required Record Changes

| Gap |                                  Change                                  |                                                Affects                                                |                   Priority                    |
|-----|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| G7  | Add `FAILED` to the status enum                                          | Incident Rate KPI, Success %, Incidents column (phase 1 proxy), Status badge, Failure rate by version | **High — blocks failure visibility entirely** |
| G10 | Promote `processDefinitionVersion` from optional to required in Identity | Failure rate by process version chart (L1/L2)                                                         | Medium                                        |
| G4  | Add `agentIncidentCount` + `toolCallIncidentCount` to metrics            | Extends incident rate to cover recoverable incidents; replaces FAILED-count proxy in Incidents column | Medium (phase 2)                              |
| G8  | Add `reasoningTokens` to metrics                                         | Token KPIs, trend, and outlier bands extended to include reasoning cost                               | Medium (phase 2)                              |
| G1  | Add `totalDurationMs` to metrics                                         | Duration KPIs — eliminates timestamp arithmetic, enables reuse across Operate/Optimize                | Low (not blocking)                            |
| G6  | Add `definition.tools[]` to the record                                   | Tool call frequency per-tool view; "configured vs used" comparison                                    | Low (depends on history export)               |

---

## Full Achievability Summary

|           UI element            |      L0       |      L1      |      L2      | Agents tab |    Blocking gap     |
|---------------------------------|---------------|--------------|--------------|------------|---------------------|
| Total Runs KPI                  | ✅             | ✅            | ✅            | —          | —                   |
| Avg Run Duration KPI            | ✅             | ✅            | ✅            | —          | —                   |
| Incident Rate KPI               | ❌             | ❌            | ❌            | —          | G7                  |
| Avg / Median tokens per run     | ✅             | ✅            | ✅            | —          | —                   |
| Token trend                     | ✅             | ✅            | ✅            | —          | —                   |
| Token outlier bands             | ✅             | ✅            | ✅            | —          | —                   |
| Top consumers by process        | ✅             | *(replaced)* | *(replaced)* | —          | —                   |
| Avg Tokens per Call to Agent    | *(not shown)* | ✅            | ✅            | —          | —                   |
| Tool call frequency (aggregate) | ✅             | ✅            | ✅            | —          | —                   |
| Tool call frequency (per tool)  | ➡️            | ➡️           | ➡️           | —          | History export + G6 |
| Failure rate by process version | *(not shown)* | ⚠️           | ⚠️           | —          | G10                 |
| Duration P50 / P95              | ✅             | ✅            | ✅            | —          | —                   |
| Duration stability chart        | ✅             | ✅            | ✅            | —          | —                   |
| Agent Name / Last active        | —             | —            | —            | ✅          | —                   |
| Status badge                    | —             | —            | —            | ❌          | G7                  |
| Runs                            | —             | —            | —            | ✅          | —                   |
| Success %                       | —             | —            | —            | ❌          | G7                  |
| Incidents count                 | —             | —            | —            | ❌          | G7 (phase 1)        |

