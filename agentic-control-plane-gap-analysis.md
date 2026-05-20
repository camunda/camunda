# Agentic Control Plane — Implementation Analysis

**Source documents**
- UI mockups: `agentic-control-plane-1.png` through `agentic-control-plane-6.png`
- Zeebe record design: AgentInstance record (agreed 2026-05-05 with Fabio Paini, Dima Melnychuk, Dmitri Nikonov)
- Requirements reference: agent-visibility-metrics-reference.md (supporting context for priority and gap rationale)

**Key architectural premise:** The Zeebe AgentInstance record is the source of truth, not the Operate API. Records are exported to Elasticsearch and imported into Optimize. All analysis below is grounded in what the record provides, not what the API exposes.

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

|       Symbol        |                                                       Meaning                                                        |
|---------------------|----------------------------------------------------------------------------------------------------------------------|
| ✅ Achievable        | All required data is present in the current record schema                                                            |
| ⚠️ Partial          | Possible but requires joining with other Zeebe record types (e.g., incident records) or the optional identity fields |
| ❌ Gap               | The required data cannot be derived from the current record; a schema addition is needed                             |
| ➡️ Future iteration | Depends on conversation/history records not yet designed                                                             |

---

## 1. Navigation & Filters

**Mockup:** Two dropdowns — **Process** (images 5–6: "All processes", "Invoice Approval", "Contract Review Agent", …) and **Agent** ("All agents", "Invoice Data Extraction Agent", "PO Matching Agent"). Date range picker.

### 1.1 Process filter

✅ **Achievable.** Filter Optimize data by `processDefinitionKey`. Process display names come from the process definition record (already exported). Agent-instance records carry `processDefinitionKey` to join them.

### 1.2 Agent filter

✅ **Achievable.** `elementId` identifies the BPMN AHSP element. Distinct `elementId` values within a process populate the Agent dropdown. No additional record changes needed.

### 1.3 Date range

✅ **Achievable.** All trend and aggregation charts bucket by the timestamp of the CREATED event. The COMPLETE event timestamp defines the end of the run. Both timestamps are part of the standard Zeebe record export envelope.

---

## 2. KPI Header Cards

**Mockup (image 1):** Three cards — **TOTAL RUNS**, **AVG RUN DURATION (AGENTIC PROCESSES)**, **INCIDENT RATE**. Each shows a week-over-week delta badge. A "Configure in settings" link appears under Incident Rate.

### 2.1 Total Runs

✅ **Achievable.** Count of AgentInstance records with status `COMPLETED` (or all records, depending on whether in-progress runs should count) for the selected filters and time window. WoW delta = same query for the previous 30-day window.

### 2.2 Avg Run Duration

✅ **Achievable.** Duration per run = `COMPLETED event timestamp − CREATED event timestamp`. Both timestamps are in the standard Zeebe record export. Optimize can compute p50, p95, and average from these two timestamps without any new fields in the AgentInstance record.

### 2.3 Incident Rate

⚠️ **Partial — requires incident record join.** Incident rate = runs with at least one incident / total runs. Zeebe exports incident records separately; they carry `elementInstanceKey` which matches `AgentInstanceRecord.elementInstanceKey`. Optimize can join on this key to identify which agent instances had incidents.

No change to the AgentInstance record is required, but Optimize must:
1. Index incident records alongside agent-instance records.
2. Join on `elementInstanceKey` to flag incidents at the agent-instance level.

> The "Configure in settings" link (threshold for the red/amber badge) is a UI-only concern.

---

## 3. Token Usage

### 3.1 Avg Tokens per Run / Median Tokens per Run

✅ **Achievable.** `inputTokens + outputTokens` from the COMPLETED (or latest UPDATED) record gives the total token count per run. Average and median are aggregated across all matching instances in Optimize.

### 3.2 Token Trend Chart

✅ **Achievable.** Bucket agent instances by CREATED event timestamp (daily/weekly). Sum `inputTokens` and `outputTokens` per bucket. Plot as two series (input, output) over time.

### 3.3 Token Outlier Bands (p0 / p50 / p95)

✅ **Achievable.** Compute `inputTokens + outputTokens` per COMPLETED instance. Derive p0, p50, p95 percentiles across instances in each time bucket. The bar chart in the mockup (one bar per period, stacked bands) is fully derivable from per-instance token totals.

### 3.4 Top Token Consumers by Process

✅ **Achievable.** Group instances by `processDefinitionKey`, sum `inputTokens + outputTokens` per group, sort descending. Process display names come from the process definition record join.

### 3.5 Avg Tokens per Call to Agent

✅ **Achievable.** Shown in images 2 and 3 when filtered to a specific process or agent. Formula per instance: `(inputTokens + outputTokens) / modelCalls`. Averaged across instances per agent element. `modelCalls` (LLM call count) accumulates as a total in the record.

---

## 4. Reliability & Tool Calls

### 4.1 Tool Call Frequency (per tool)

➡️ **Future iteration.** The mockup (images 1–3) shows a horizontal bar chart with individual **tool names** on the y-axis and total call counts per tool on the x-axis. `toolCalls` in the current record is a single aggregate integer — total invocations across all tools. Per-tool breakdown requires conversation/history records (not yet designed).

What is achievable now: the aggregate `toolCalls` total per agent instance, which can be summed or averaged to show overall tool usage volume. This can populate a simplified version of the chart (e.g., total tool calls per agent, not per tool).

### 4.2 Failure Rate by Process Version

⚠️ **Partial — requires optional identity fields + incident join.**

This chart (images 2–3) plots failure rate on the y-axis against process version on the x-axis. It requires:

1. **`processDefinitionVersion`** — listed as optional in the current record Identity section. Must be included to group instances by version.
2. **Incident record join** — to determine which instances in each version had failures (see §2.3).

If `processDefinitionVersion` is promoted from optional to required in the Identity fields, and incident records are indexed in Optimize, this chart is fully achievable.

---

## 5. Duration

### 5.1 Duration P50 / P95 KPI Cards

✅ **Achievable.** Compute duration for each instance from CREATED and COMPLETED event timestamps. Derive p50 and p95 across all matching instances. No new record fields needed.

### 5.2 Execution Duration Stability Chart (p50 / p95 over time)

✅ **Achievable.** Same derivation as §5.1, bucketed by time. Plot p50 and p95 as two lines over the date range. The mockup subtitle (*"Widening gap between the lines = standardization lag"*) is a display annotation, not a data requirement.

---

## 6. Agents Tab — Agent List

**Mockup (image 4):** A searchable list with columns: **AGENT NAME**, **STATUS**, **RUNS**, **SUCCESS**, **INCIDENTS**. Status badges are Healthy (green), Degraded (orange), Failing (red).

|             Agent             |  Status  | Runs | Success | Incidents |
|-------------------------------|----------|------|---------|-----------|
| Contract Analysis Agent       | Failing  | 276  | 94.2%   | 5         |
| Invoice Data Extraction Agent | Degraded | 467  | 98.1%   | 1         |
| Ticket Classification Agent   | Healthy  | 675  | 98.6%   | 0         |

### 6.1 Agent Name / Last Active

✅ **Achievable.** Agent name maps to the BPMN element name resolved from `elementId`. Last active = latest CREATED or UPDATED event timestamp for that `elementId`. Both are derivable from existing record fields.

### 6.2 Status Badge (Healthy / Degraded / Failing)

⚠️ **Partial — FAILED status missing from record + incident join needed.**

The three badge states are Optimize-computed health signals derived from record data:

|    Badge     |                                  Derivation                                   |
|--------------|-------------------------------------------------------------------------------|
| **Healthy**  | No incidents joined via `elementInstanceKey`; recent runs completing normally |
| **Degraded** | Some incidents in the period but agent is still completing runs               |
| **Failing**  | High incident rate, or current status = `FAILED`                              |

❌ **Gap:** `FAILED` is not in the current status enum (`INITIALIZING`, `TOOL_DISCOVERY`, `THINKING`, `TOOL_CALLING`, `IDLE`, `COMPLETED`). An agent that encounters an unrecoverable error (e.g., retries exhausted) has no terminal error state in the record. Without it, Optimize cannot distinguish a stuck/failed agent from one that simply has not completed yet.

**Proposed addition to the record:**

```
Status: ... COMPLETED, FAILED
```

> `FAILED` is set when the engine raises an incident on the element instance (e.g., `maxModelCalls` exceeded, job retries exhausted). Maps directly to the **Failing** badge in the UI.

### 6.3 Runs

✅ **Achievable.** Count of COMPLETED AgentInstance records for that `elementId` within the selected period. For agents inside BPMN loops (re-entry), the count of COMPLETED records naturally reflects each activation as a separate run.

### 6.4 Success Rate

⚠️ **Partial — requires incident record join.** Success % = instances without any joined incident / total instances. Same join as §2.3. Achievable once Optimize indexes incident records alongside agent-instance records.

### 6.5 Incidents Count

⚠️ **Partial — requires incident record join.** Total incident count for the agent in the period = sum of joined incident records by `elementInstanceKey`. Achievable via the same join as §2.3 and §6.4.

---

## Summary

|            UI element            | Status |                            What is needed                             |
|----------------------------------|--------|-----------------------------------------------------------------------|
| Process / Agent filter dropdowns | ✅      | `processDefinitionKey`, `elementId` — both present                    |
| Total Runs KPI                   | ✅      | Count of COMPLETED records                                            |
| Avg Run Duration KPI             | ✅      | CREATED → COMPLETED timestamp delta                                   |
| Incident Rate KPI                | ⚠️     | Join with Zeebe incident records on `elementInstanceKey`              |
| Avg / Median tokens per run      | ✅      | `inputTokens + outputTokens` from record                              |
| Token trend chart                | ✅      | Bucket by CREATED timestamp                                           |
| Token outlier bands (p0/p50/p95) | ✅      | Percentile over `inputTokens + outputTokens`                          |
| Top token consumers by process   | ✅      | Group by `processDefinitionKey`, sum tokens                           |
| Avg tokens per call to agent     | ✅      | `(inputTokens + outputTokens) / modelCalls`                           |
| Tool call frequency (per tool)   | ➡️     | Requires conversation/history records (future iteration)              |
| Tool call frequency (aggregate)  | ✅      | Sum `toolCalls` per agent                                             |
| Failure rate by process version  | ⚠️     | `processDefinitionVersion` (promote to required) + incident join      |
| Duration P50 / P95 KPIs          | ✅      | CREATED → COMPLETED timestamp delta, percentiles                      |
| Duration stability chart         | ✅      | Same timestamps, bucketed over time                                   |
| Agent list — name / last active  | ✅      | `elementId` + latest event timestamp                                  |
| Agent list — status badge        | ⚠️ + ❌ | Incident join for Degraded; `FAILED` status missing for Failing badge |
| Agent list — runs                | ✅      | Count of COMPLETED records per `elementId`                            |
| Agent list — success %           | ⚠️     | Incident join                                                         |
| Agent list — incidents count     | ⚠️     | Incident join                                                         |

### Required record changes

|                                    Change                                    |                       Affected UI                        |     Priority     |
|------------------------------------------------------------------------------|----------------------------------------------------------|------------------|
| Add `FAILED` to the status enum                                              | Agent list Failing badge; reliable stuck-agent detection | High             |
| Promote `processDefinitionVersion` from optional to required in Identity     | Failure rate by process version chart                    | Medium           |
| *(future)* Conversation/history records with per-tool and per-iteration data | Tool call frequency per tool; per-iteration token charts | Future iteration |

