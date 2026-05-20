# Agentic Control Plane — Implementation Analysis (v2)

**Source documents**
- UI mockups: `agentic-control-plane-1.png` through `agentic-control-plane-6.png`
- Zeebe record design: AgentInstance record (agreed 2026-05-05 with Fabio Paini, Dima Melnychuk, Dmitri Nikonov)
- Requirements reference: agent-visibility-metrics-reference.md (supporting context)

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

## How to read this document

|       Symbol        |                                                        Meaning                                                         |
|---------------------|------------------------------------------------------------------------------------------------------------------------|
| ✅ Achievable        | All required data is present in the current record schema                                                              |
| ⚠️ Partial          | Achievable but requires joining with another Zeebe record type (e.g., incident records) or promoting an optional field |
| ❌ Gap               | Required data cannot be derived from the current record; a schema addition is needed                                   |
| ➡️ Future iteration | Depends on conversation/history records not yet designed                                                               |

---

## Dashboard Filter States and Drill-Down Behaviour

The Dashboard tab has two dropdowns — **Process** and **Agent** — that control what is shown. The UI is not a static set of panels; certain charts appear, disappear, or change their grouping axis depending on the selected filter level. There are three distinct states:

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

|       Card       |                              Value shown                               |                            Implementation                            |
|------------------|------------------------------------------------------------------------|----------------------------------------------------------------------|
| Total Runs       | Count of all COMPLETED AgentInstance records in the period             | ✅ Count of COMPLETED records                                         |
| Avg Run Duration | Mean of (COMPLETED timestamp − CREATED timestamp) across all instances | ✅ Event timestamp delta                                              |
| Incident Rate    | Share of instances with at least one incident                          | ⚠️ Requires join with Zeebe incident records on `elementInstanceKey` |

### Token Usage section

**Charts present at L0:**
- Token trend (line chart, input + output tokens over time)
- Token outlier bands (p0 / p50 / p95 bar chart)
- **Top token consumers by process** (horizontal bar, y-axis = process name, x-axis = total tokens)

|          Chart           |                                                                Implementation                                                                 |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---|
| Token trend              | Bucket all instances by CREATED timestamp; sum `inputTokens` + `outputTokens` per bucket; two series (input / output)                         | ✅ |
| Token outlier bands      | Percentile of `inputTokens + outputTokens` per instance, per time bucket                                                                      | ✅ |
| Top consumers by process | Group instances by `processDefinitionKey`; sum `inputTokens + outputTokens`; sort descending; join process definition record for display name | ✅ |

### Reliability & Tool Calls section

**Charts present at L0:**
- Tool call frequency (horizontal bar, y-axis = tool name, x-axis = total calls)

|             Chart              |                                     Implementation                                     |
|--------------------------------|----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tool call frequency (per tool) | `toolCalls` in the record is an aggregate integer per instance — no per-tool breakdown | ➡️ Requires future conversation/history records. Aggregate `toolCalls` summed per `elementId` can produce a simplified version (total calls per agent, not per tool). |

### Duration section

|                    Card / Chart                     |                               Implementation                               |
|-----------------------------------------------------|----------------------------------------------------------------------------|---|
| Duration P50                                        | p50 of (COMPLETED − CREATED) across all instances                          | ✅ |
| Duration P95                                        | p95 of (COMPLETED − CREATED) across all instances                          | ✅ |
| Execution duration stability (p50 / p95 line chart) | Same delta, bucketed by CREATED timestamp, two percentile series over time | ✅ |

---

## L1 — Process View (Specific Process, All Agents)

*Filter: Process = "Invoice Approval" (or any specific process). Agent = "All agents".*

### What changes from L0

All queries are filtered to `processDefinitionKey = <selected>`. The Agent dropdown populates from distinct `elementId` values within that process.

**Token section change:** "Top token consumers by process" is **replaced** by **"Avg Tokens per Call to Agent"** — a horizontal bar where the y-axis is the agent name (`elementId`) and the x-axis is average tokens per call.

**Reliability section addition:** **"Failure rate by process version"** appears — a bar chart with process version on the x-axis and failure rate on the y-axis. This chart does not exist at L0.

### Token Usage section

|              Chart               |  Present at L1?   |                                                                                   Implementation                                                                                   |
|----------------------------------|-------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---|
| Token trend                      | ✅ Yes             | Same as L0 but filtered to the selected process                                                                                                                                    |
| Token outlier bands              | ✅ Yes             | Same as L0 but filtered                                                                                                                                                            |
| Top consumers by process         | ❌ Replaced        | —                                                                                                                                                                                  |
| **Avg Tokens per Call to Agent** | ✅ Yes (new at L1) | Per instance: `(inputTokens + outputTokens) / modelCalls`; then average per `elementId`. Group by `elementId`; join element name from BPMN. `modelCalls` is present in the record. | ✅ |

### Reliability & Tool Calls section

|                Chart                |  Present at L1?   |                                                  Implementation                                                  |
|-------------------------------------|-------------------|------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------|
| **Failure rate by process version** | ✅ Yes (new at L1) | Group instances by `processDefinitionVersion`; failure rate = instances with joined incident / total per version | ⚠️ Requires incident join + `processDefinitionVersion` promoted from optional to required |
| Tool call frequency                 | ✅ Yes             | Same as L0 but filtered to this process                                                                          | ➡️ Same limitation applies                                                                |

### Duration section

✅ Same as L0, filtered to the selected process.

---

## L2 — Agent View (Specific Process, Specific Agent)

*Filter: Process = "Invoice Approval". Agent = "Invoice Data Extraction Agent".*

### What changes from L1

All queries further filtered to `elementId = <selected>`. The layout is identical to L1 — no charts are added or removed at this level.

|                Chart                 |                                 Change from L1                                 |      Implementation      |
|--------------------------------------|--------------------------------------------------------------------------------|--------------------------|
| Token trend                          | Filtered to one agent                                                          | ✅                        |
| Token outlier bands                  | Filtered to one agent                                                          | ✅                        |
| Avg Tokens per Call to Agent         | Now shows one bar (the selected agent) or could pivot to per-version breakdown | ✅                        |
| Failure rate by process version      | Filtered to this agent's runs per version                                      | ⚠️ Same dependency as L1 |
| Tool call frequency                  | Filtered to this agent's runs                                                  | ➡️ Same limitation       |
| Duration P50 / P95 / stability chart | Filtered to this agent                                                         | ✅                        |

> **Note:** At L2 the "Avg Tokens per Call to Agent" bar chart becomes a single-entry chart (one bar for the selected agent). Optimize may want to pivot this chart's y-axis to `processDefinitionVersion` at L2 to show how token consumption evolved across deployments, though the mockup does not show that transformation explicitly.

---

## Agents Tab — Agent List

*Independent of the Dashboard filter state. Filter: Status dropdown ("All statuses").*

**Mockup (image 4) columns:** AGENT NAME | STATUS | RUNS | SUCCESS | INCIDENTS

|    Column    |                             Implementation                              |
|--------------|-------------------------------------------------------------------------|------------------|
| Agent Name   | `elementId` resolved to BPMN element name via process definition        | ✅                |
| Last active  | Latest CREATED or UPDATED event timestamp for that `elementId`          | ✅                |
| Runs         | Count of COMPLETED records per `elementId` in the period                | ✅                |
| Success %    | `count(instances with no joined incident) / count(all)` per `elementId` | ⚠️ Incident join |
| Incidents    | Sum of joined incident records per `elementId`                          | ⚠️ Incident join |
| Status badge | Optimize-computed from incident join + record status (see below)        | ⚠️ + ❌           |

### Status Badge Logic

|    Badge     |                          Derivation                          |
|--------------|--------------------------------------------------------------|
| **Healthy**  | No joined incidents in the period; all runs COMPLETED        |
| **Degraded** | Incident rate > 0 but below threshold; runs still completing |
| **Failing**  | High incident rate OR current status = `FAILED`              |

❌ **Gap: `FAILED` status missing.** The current enum ends at `COMPLETED`. An agent stopped by an unrecoverable error (exhausted retries, limit exceeded) has no terminal error state. Without `FAILED`, Optimize cannot distinguish an errored agent from one that has simply not completed yet. The "Failing" badge cannot be driven by record status alone.

**Proposed addition to the status enum:**

```
FAILED  — set by the engine when it raises an incident on the element instance
          (e.g., maxModelCalls exceeded, job retries exhausted)
```

---

## Required Data Outside the AgentInstance Record

Several UI elements require joining with other Zeebe record types already exported to Elasticsearch. No changes to the AgentInstance record are needed for these — Optimize must index and join them.

|        Zeebe record        |        Join key        |                                                      Drives                                                      |
|----------------------------|------------------------|------------------------------------------------------------------------------------------------------------------|
| Incident records           | `elementInstanceKey`   | Incident Rate KPI, Success %, Incidents column, Status badge (Degraded/Failing), Failure rate by process version |
| Process definition records | `processDefinitionKey` | Process display names in dropdowns and Top consumers chart                                                       |

---

## Required Record Changes

|                                  Change                                  |                                       Rationale                                        |              Affected UI              | Priority |
|--------------------------------------------------------------------------|----------------------------------------------------------------------------------------|---------------------------------------|----------|
| Add `FAILED` to the status enum                                          | No terminal error state exists; Failing badge cannot be API-driven without it          | Agent list — Failing status badge     | High     |
| Promote `processDefinitionVersion` from optional to required in Identity | Without it, the Failure rate by process version chart (L1 / L2) cannot group instances | Failure rate by process version chart | Medium   |

---

## Full Achievability Summary by Filter Level

|           UI element            |      L0       |      L1      |      L2      | Agents tab |
|---------------------------------|---------------|--------------|--------------|------------|
| KPI — Total Runs                | ✅             | ✅            | ✅            | —          |
| KPI — Avg Run Duration          | ✅             | ✅            | ✅            | —          |
| KPI — Incident Rate             | ⚠️            | ⚠️           | ⚠️           | —          |
| Token trend                     | ✅             | ✅            | ✅            | —          |
| Token outlier bands             | ✅             | ✅            | ✅            | —          |
| Top consumers by process        | ✅             | *(replaced)* | *(replaced)* | —          |
| Avg Tokens per Call to Agent    | *(not shown)* | ✅            | ✅            | —          |
| Tool call frequency (per tool)  | ➡️            | ➡️           | ➡️           | —          |
| Failure rate by process version | *(not shown)* | ⚠️           | ⚠️           | —          |
| Duration P50 / P95 KPIs         | ✅             | ✅            | ✅            | —          |
| Duration stability chart        | ✅             | ✅            | ✅            | —          |
| Agent Name / Last active        | —             | —            | —            | ✅          |
| Status badge                    | —             | —            | —            | ⚠️ + ❌     |
| Runs                            | —             | —            | —            | ✅          |
| Success %                       | —             | —            | —            | ⚠️         |
| Incidents count                 | —             | —            | —            | ⚠️         |

