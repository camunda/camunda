# Agentic dashboard v2: plan and metrics proposal

Status: proposal for review, 2026-10-29. Epic: experience-pdp#84.
Builds on the Phase 2 prototype (`agentic-phase2-flat-indices.md`, draft PR camunda/camunda#67198).
Target design: Omran's "Agentic control dashboard" artifact.

## Summary

We can build most of Omran's dashboard on the Phase 2 import we already have: one document per agent run in
`optimize-agent-instance`, one per agent in `optimize-agent-definition`. Two thirds of the metrics below need only
new report types or columns over fields we already store. The rest need four additions to the enrichment step that
already copies process-instance data onto each run:

1. Agent run duration as the sum of the agent's element instance durations.
2. Incidents on the agent's tool elements, not only on the agent element.
3. Each incident's error type and message, for failure reasons.
4. The root process of the run, for agents reached through call activities.

Compared with the design, the proposal adds a process view and a model comparison (both asked for in the discovery
session with Joyce), keeps human steps per run, and moves cost, "needs attention" and targets to a later phase because
each needs a decision we don't have yet.

## Feasibility of the agentic control artifact

This section checks every element of Omran's "Agentic control" artifact (mock data, 29 Oct 2026) against the data
Camunda records and the prototype on `sherrin/agentic-phase2-proto`. Verdicts:

- **Built**: works on the prototype branch, checked against real engine runs.
- **Feasible**: the data exists; it needs a report, column or endpoint field.
- **Needs a decision**: the data exists, but a product decision is missing (a threshold, a price source, a target store).
- **Partly**: only part of it is possible, or only for some agent types.
- **Not feasible**: the data isn't recorded, or only in records we don't import.

The artifact calls a run an "agent instance"; this plan calls it an "agent run". Both mean one agent instance record
(D5). Which word the UI uses should follow the official glossary (open, see the action from the Eric follow-up).

### Dashboard view

|                            Artifact element                             |      Verdict      |                                                                                                                                                    How, or why not                                                                                                                                                    |  Plan  |
|-------------------------------------------------------------------------|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------|
| Total tokens, change vs previous period                                 | Built             | Sum of input, output, cache write and cache read tokens; reasoning is part of output (D3). The artifact adds reasoning on top, which counts it twice.                                                                                                                                                                 | M1     |
| Token split chips (input, cache read, cache write, output, reasoning)   | Built             | One sum per token type, shown as a token breakdown tile.                                                                                                                                                                                                                                                              | M2     |
| Average tokens per agent instance, change, instance count               | Built             | Average tokens per run; runs with comparison.                                                                                                                                                                                                                                                                         | M3, M4 |
| Agent instance duration p50, p95                                        | Built             | Sum of the agent's element instance durations, without user-task tool time (D1, Q2). For an AI Agent Task tool time is excluded (Q1).                                                                                                                                                                                 | M6     |
| "2 agents are slower than their target"                                 | Needs a decision  | Needs a place to store a duration target per agent and who sets it.                                                                                                                                                                                                                                                   | L3     |
| Incident rate "ended in an incident or a limit hit", above target 3%    | Partly            | Same definition (D2): runs with an incident on the agent or its tools, or a limit hit. The prototype still shows it the other way round, as success rate (100 % minus incident rate). The target chip needs targets.                                                                                                  | M5, L3 |
| Needs attention count with reason chips                                 | Needs a decision  | Limit-hit spike and token spike can compare each agent with its previous period or its P95 band, once thresholds are set. Tool looping is partly feasible: repeated calls of the same tool in a run can be counted from the tool elements' instances; whether the calls had the same arguments needs `AGENT_HISTORY`. | L2, L4 |
| Total tokens by model over time, stacked weekly totals, 8.10 annotation | Built, normalised | Built as median tokens per run, one line per model, because totals rise with volume. Stacked totals per model are feasible with the same model split. The 8.10 annotation is not built; it is a fixed date marker.                                                                                                    | M7     |
| "Left off, and why" items                                               | Agree             | The prototype also leaves them off the dashboard. Tool calls and iterations moved to the single agent view (M20, M22).                                                                                                                                                                                                |        |

### Agent inventory view

|                             Artifact element                              |     Verdict      |                                                                          How, or why not                                                                           |    Plan    |
|---------------------------------------------------------------------------|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| Header "18 agents across 11 processes, 2 external"                        | Built            | Distinct agents, distinct processes, agents of type external.                                                                                                      | M8         |
| Columns agent, agent instances, total tokens, duration p50, incident rate | Built            | Plus process, source (Camunda or external), model, tokens per run, P95 duration, human steps per run, last run. The incident rate column still shows success rate. | M12 to M14 |
| Attention column ("Limit hits 6%", "Tokens +38%", "Tool looping")         | Needs a decision | As needs attention above.                                                                                                                                          | L2         |
| Sort by agent instances, row opens the agent                              | Built            |                                                                                                                                                                    |            |
| Debugging continues in Operate                                            | Feasible         | Every run stores its process instance key, so a link per run or a filtered Operate list is possible. Not built.                                                    |            |
| Entry from a Hub project                                                  | Hub              | Hub decides where the link lives. Knowing whether a cluster has agents is a count on the agent definition index.                                                   |            |

### Single agent view

|                         Artifact element                         |     Verdict      |                                                                                                               How, or why not                                                                                                                |    Plan    |
|------------------------------------------------------------------|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------|
| Model and provider                                               | Built            | From the latest run.                                                                                                                                                                                                                         | M15        |
| Version "v7, deployed 12 days ago"                               | Partly           | The process version is built. The deployment date is not imported yet; the process record has it.                                                                                                                                            | M15        |
| Limits                                                           | Built            | Model calls, tool calls and tokens, as configured on the run; unset limits are not shown. The artifact's "8k tokens per request" is per request; the engine records a token limit for the whole run.                                         | M15        |
| "Used in 3 processes"                                            | Needs a decision | An agent is identified by process and element (D4). Counting the same agent across processes needs grouping by name or template.                                                                                                             | L7         |
| Owner                                                            | Not feasible     | No owner is recorded for agents or processes. The artifact marks it unconfirmed too.                                                                                                                                                         |            |
| Tiles: tokens, duration, incident rate, limit hits, with changes | Built            | The same KPI tiles as the dashboard, filtered to the agent; limit-hit rate per agent.                                                                                                                                                        | M15 to M17 |
| "179 stopped at the 10 model call limit"                         | Built            | Runs per limit in the agent view.                                                                                                                                                                                                            | M17        |
| Agent targets (duration 2 m, incident rate 5%)                   | Needs a decision | As targets above.                                                                                                                                                                                                                            | L3         |
| Per agent instance over time, by model: tokens                   | Built, as a band | Tokens per run per week with P5, P50, P95 and average. The per-model lines are feasible with the model split. Version and 8.10 markers are not built; versions are known per run, so a marker at the first run of a new version is feasible. | M18        |
| Per agent instance over time, by model: duration                 | Feasible         | Same as tokens, on the run duration. Not built.                                                                                                                                                                                              |            |
| Per agent instance over time: spend                              | Needs a decision | Needs prices per model and token type.                                                                                                                                                                                                       | L1         |
| Why agent instances failed: limit hit                            | Built            | Runs per limit.                                                                                                                                                                                                                              | M16        |
| Why agent instances failed: tool or connector failure            | Built            | Incidents on the agent's tool elements, with error type and first message.                                                                                                                                                                   | M16        |
| Why agent instances failed: configuration or LLM connection      | Partly           | Incidents on the agent element itself, with error type and message. Telling a configuration error from a connection error depends on the connector's message text; there is no separate category.                                            | M16        |
| Why agent instances failed: model and tool mismatch              | Partly           | Only if the connector raises an incident with a recognisable message. There is no field for it.                                                                                                                                              | M16        |
| Why agent instances failed: wrong decisions                      | Not feasible     | Not detectable from engine data, as the artifact says.                                                                                                                                                                                       |            |
| Open failed agent instances in Operate                           | Feasible         | Process instance keys of failed runs are stored. Not built.                                                                                                                                                                                  |            |
| Compare this agent across models                                 | Built            | Per model: runs, tokens per run, average duration, incident rate (the prototype shows success rate). "Best" markers are a frontend detail.                                                                                                   | M19        |
| Where this agent is used: contains the agent, call activity      | Built            | Runs per root process; an agent reached through a call activity counts for the calling process.                                                                                                                                              | M21        |
| Where this agent is used: message event                          | Not feasible     | A process started by a message has its own root; nothing links it to the process that sent the message.                                                                                                                                      |            |
| Tools panel (left off in the artifact)                           | Built            | Calls per run and failure rate per tool. AI Agent Sub-process only (see below).                                                                                                                                                              | M20        |

### Experimental view

|                   Artifact element                   |   Verdict    |                                                                                                          How, or why not                                                                                                           |
|------------------------------------------------------|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Escalated to a human                                 | Partly       | Runs with at least one user-task tool. Hand-offs modelled outside the agent, such as a user task after it, are not counted (Q5).                                                                                                   |
| Outcomes (cleared, confirmed fraud, sent to analyst) | Not generic  | Outcomes are agent-specific output variables. Possible per agent if the process stores the outcome in a known variable. (L6)                                                                                                       |
| Self-evaluation                                      | Not generic  | Only if the agent writes a score into a variable; not part of the agent records.                                                                                                                                                   |
| Tokens by tool                                       | Not feasible | Tokens are recorded per run and per model, not per tool call. A tool call's arguments and results are only in variables and in `AGENT_HISTORY` records, which the exporter's Optimize filter drops and which carry prompt content. |
| Tokens by tenant                                     | Feasible     | Every run stores its tenant. Not built.                                                                                                                                                                                            |

### Features view

|                                 Feature                                  |                                                              Verdict                                                              |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Environment filter                                                       | Hub: one Optimize per cluster, so Hub picks the cluster.                                                                          |
| Process filter, process version filter, agent filter                     | Built for the tiles. The inventory and agent view tables ignore the version and model filters.                                    |
| Tenant filter                                                            | Feasible: the tenant is on every run; Optimize's tenant filter needs wiring.                                                      |
| Inventory to single agent drill-down                                     | Built.                                                                                                                            |
| Hand-off to Operate                                                      | Feasible, see above.                                                                                                              |
| Link from a Hub project, open from a process definition                  | Hub.                                                                                                                              |
| Export as PDF, screenshot or slide; share with people without Hub access | Optimize dashboards can be shared and exported today; whether that applies to this seeded dashboard is not checked.               |
| Data in BI tools, API or MCP                                             | Partly: every tile is an Optimize report, and Optimize's public API can export report results. The tables are internal endpoints. |
| Targets per agent, thresholds for needs attention                        | Needs a decision (L2, L3).                                                                                                        |
| Choose P50 or P95                                                        | Built: duration tiles show average, P50 and P95; the inventory shows both.                                                        |
| Custom metrics and columns                                               | Not planned, agreed.                                                                                                              |
| Token prices for cost                                                    | Needs a decision (L1).                                                                                                            |
| Audit trail; caps that stop runaway agents                               | Agreed that these live elsewhere: Operate, and the agent's limits.                                                                |

### Assumptions the artifact marks as pending on this work

| #  |                     Assumption                      |                                                                                  Answer                                                                                  |
|----|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| A3 | An agent-level failure can be defined               | Yes: a run fails when it has an incident on the agent or its tools, or hits a limit (D2). Wrong decisions can't be seen.                                                 |
| A4 | Model and version are known for each agent instance | Yes: the run records its model and provider, and runs that switch model record tokens per model. The version is the process version; there is no separate agent version. |
| A5 | Token numbers are comparable over time              | Not across 8.10: from 8.10 cache tokens are counted, so totals step up while usage doesn't. Reasoning tokens are part of output, not extra.                              |
| A8 | The inventory is complete                           | It lists agents from agent definition records plus any agent seen on a run. An agent deployed before agent definition records were exported only appears once it runs.   |

### What works for which agent type

|               Metric               |              AI Agent Sub-process              |                               AI Agent Task                                |          External agent           |
|------------------------------------|------------------------------------------------|----------------------------------------------------------------------------|-----------------------------------|
| Tokens, model calls, limits, model | Yes                                            | Yes                                                                        | As reported by the external agent |
| Run duration                       | Includes tool time                             | Excludes tool time: the tools run in a separate ad-hoc sub-process (Q1)    | The job's element instances       |
| Calls and failure rate per tool    | Yes: tools are elements inside the sub-process | Only if the agent record lists the tool elements; not checked on real runs | No: tools run outside Camunda     |
| Tool failures as failure reasons   | Yes                                            | As tool calls                                                              | No                                |
| Human steps and human wait         | User-task tools inside the sub-process         | User-task tools in the tools sub-process, during the run                   | No                                |

### Data preconditions

- **Camunda version:** agent instance and agent definition records come with 8.10; cache tokens are counted from 8.10.
  Before that, only the Phase 1 agent data nested in process instances exists. It has no limits, reasoning tokens or
  per-model split, and migrated runs show "no data" for those (see Migration).
- **Exporter:** in Optimize mode the Zeebe exporter must pass `AGENT_DEFINITION` records; the branch adds them to the
  Optimize mode filter. Self-managed clusters with Optimize mode turned off export everything already.
- **History:** Optimize keeps what it imported, but it can only import what is still in the Zeebe record indices. SaaS
  keeps Zeebe records for 7 days. Self-managed keeps them forever by default; with retention turned on, the Helm
  default is 30 days. History older than that is only available through the Phase 1 migration.
- **Runs imported before a field existed:** the enrichment only recomputes a run when its process instance is imported
  again, so older runs show "no data" for new fields. Production needs a backfill when the enrichment changes (open).

### Known accuracy limits

- Tool calls per tool count every instance of the tool element in the process instance, not only those of the run.
  Several runs of the same agent element in one process instance therefore share tool calls. Incidents are matched to
  the run's time window but share the same limit once an incident's element instance key has been rewritten to the
  element ID. Optimize doesn't store which sub-process instance an element instance ran in.
- A multi-instance tool is counted once more per call: Optimize stores the multi-instance body under the same element
  ID (from the importer, not checked on a run).
- Tool calls per tool and the engine's tool call total can differ: the total counts calls the model asked for, the
  element count misses calls that never activated and includes tools cancelled when the sub-process completed.
- Long-running process instances can lose later tool calls when the nested document limit is reached and
  `skipDataAfterNestedDocLimitReached` is on.
- Human steps miss user tasks modelled outside the agent (Q5).
- Failure categories come from incident error types and messages; there is no classification field.
- The inventory, agent and failures tables don't apply the version and model filters.

### Feedback from the last discovery sessions

|      From      |                             Ask                              |                                                                                   Status                                                                                    |
|----------------|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Eric (28 Oct)  | Check metric names against the official glossary             | Open.                                                                                                                                                                       |
| Eric           | Context for P95 and whether a value is good or bad           | Tile descriptions explain each metric; good or bad needs targets (L3).                                                                                                      |
| Eric           | Configure limits and KPI targets                             | Limits are configured on the agent and shown; targets need a decision (L3).                                                                                                 |
| Eric           | Hub may not be allowed to show production data               | Open (artifact A1). The dashboard reads Optimize, as the business value dashboard does.                                                                                     |
| Joyce (29 Oct) | Average run duration, compared across models                 | Built: duration tiles and the model comparison.                                                                                                                             |
| Joyce          | Cost per run                                                 | Later (L1).                                                                                                                                                                 |
| Joyce          | See whether an agent improves after a model or prompt change | Partly: per-model comparison is built; version markers on the trend charts are feasible, not built. A prompt change is only visible if it comes with a new process version. |
| Joyce          | Outcomes in a generic way                                    | Not generic (L6).                                                                                                                                                           |

## Decisions

| #  |                                                                                                                      Decision                                                                                                                       |                                                                                                                                                    Why                                                                                                                                                    |
|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| D1 | **Agent run duration is the sum of the durations of the agent's element instances.** For an AI Agent Sub-process that is each activation of the ad-hoc sub-process, tools included. For an AI Agent Task it is each activation of the service task. | One agent run can span several element instances, for example a chat agent that is re-entered after a user feedback task. Today's `durationMs` (first record to last activity) counts the time the user took in between. Agreed in the metric-validation BI sync (B2). See Q1 for the AI Agent Task case. |
| D2 | **A run counts as an incident run when it has an incident on the agent element or on one of its tool elements, or hit a limit.** Incident rate = incident runs / all runs.                                                                          | Matches the design ("ended in an incident or a limit hit"). We show incident rate, not the success-rate framing from the metric-validation BI sync (B2). Tool incidents count, so "tool or connector failure" shows up as a failure reason.                                                               |
| D3 | **Total tokens = input + output + cache write + cache read.** Reasoning is shown as "of which reasoning" inside output, not added on top.                                                                                                           | The connector already counts reasoning inside output (Phase 2 findings). The design's mock adds it on top, which double-counts.                                                                                                                                                                           |
| D4 | **An agent is identified by process + element** (`agentId = processDefinitionKey:flowNodeId`). Grouping by agent name across processes is optional, later.                                                                                          | Process-level grouping is the main grouping (see the evidence for M9). It also keeps one agent definition per BPMN element, which is how the engine records them.                                                                                                                                         |
| D5 | **The unit is the agent run** (agent instance). Iterations (model calls) and element instances are measures of a run, not separate units.                                                                                                           | Keep it simple and use agent instances for volume (MVP-metrics BI sync, B1). Iterations are the run's `modelCalls`, so no agent history import is needed.                                                                                                                                                 |
| D6 | **Cost, needs attention and targets are a later phase.**                                                                                                                                                                                            | Cost deferred in the metric-validation BI sync (B2); thresholds and targets need product decisions (Q3, Q4).                                                                                                                                                                                              |
| D7 | **Only completed runs count**, and the date range applies to the run's end date.                                                                                                                                                                    | Same as the prototype and Phase 1.                                                                                                                                                                                                                                                                        |

## Dashboard structure

Two tabs, as in the design, plus a process level on the dashboard:

- **Dashboard tab**
  - **Overview** (all processes): KPI row, tokens per run by model over time, limit hits.
  - **Process** (one process selected in the process filter): the same tiles for that process, plus where its agents
    fail.
- **Agent inventory tab**: the inventory table, filtered by the same process filter. Selecting an agent opens the
  single agent view.
- **Single agent view**: KPI tiles, tokens against the normal range, failure reasons, tools, model comparison, where
  used. Reached from an inventory row or the agent filter.

A processes table on the overview was considered and left out; process-level grouping comes from the process filter
and the inventory's process column.

Global filters: environment (Hub selects the cluster), period, process, agent. The design's "show validation" toggle
is a frontend-only aid and stays out of the product.

Not carried over from the current prototype: adoption rate and the
process donut (the process count moves into the inventory header), tool call totals and top tools across all agents,
tokens by provider (covered by tokens by model), runs over time, and the Phase 1 process tiles (replaced by the process
view). This matches the design's "Left off, and why" list.

## Metrics

Status: **Ready** = works on today's prototype data; **Small** = new report type, column or endpoint field over stored
data; **New** = needs a new stored field or new logic (see Implementation).

Evidence sources:

- **A**: Omran's dashboard artifact (including its validation notes, such as the survey ranking).
- **J**: Phase 2 discovery session with Joyce.
- **B1**: BI daily sync on the MVP metric set (token usage, volume, requests). **B2**: BI daily sync validating the metrics with Omran (cost, success rate, duration, iterations; we show incident rate instead, D2).
- **E**: feedback session with Eric, as summarised in B1.
- **T**: Tally's reply on history migration.

### Overview

| #  |                        Metric                        |                                                                      Calculation                                                                      |                                                    Data                                                    |                                                                                     Evidence                                                                                     |
|----|------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M1 | Total tokens, change vs previous period              | Sum of `totalTokens` over completed runs in the period. Previous period = same length directly before.                                                | Small: sum view on agent runs; comparison already exists for Phase 1 tiles, to verify on agent run reports | A: first tile, ranked most important in the survey. J: cost and token usage are the most important metrics for business analysts. B1: token usage/spend is the first MVP metric. |
| M2 | Token breakdown                                      | Sums of `inputTokens`, `outputTokens`, `cacheCreationTokens`, `cacheReadTokens`; `reasoningTokens` shown as part of output (D3)                       | Small: one view per field                                                                                  | A: breakdown in the total tokens tile. J: input vs output may be useful, importance unclear. B1: cache tokens show cost savings.                                                 |
| M3 | Average tokens per agent run                         | Average of `totalTokens`                                                                                                                              | Ready                                                                                                      | A: subline of the total tokens tile.                                                                                                                                             |
| M4 | Agent runs                                           | Count of completed runs                                                                                                                               | Ready                                                                                                      | A: "across 31,940 agent instances". B1: volume as agent instances.                                                                                                               |
| M5 | Incident rate, change vs previous period             | Runs with an incident on the agent or its tools, or a limit hit, divided by all runs (D2)                                                             | New: tool incidents (I2); rate view on the incident query                                                  | A: second tile, "incident rate". B2: success-rate framing discussed; we show incident rate instead. J: a single "94.2 %" style rate works for business users.                    |
| M6 | Agent run duration: average, p50, p95                | Average and percentiles of `activeDurationMs` = sum of the agent's element instance durations (D1)                                                    | New: `activeDurationMs` (I1)                                                                               | A: third tile (p50, p95). J: make duration more prominent, show average run duration. B2: duration as the sum of element instance durations.                                     |
| M7 | Median tokens per agent run over time, by model      | Per week of end date and per model: median of the run's tokens on that model (`tokensByModel.totalTokens`). Annotation at 8.10: cache tokens counted. | New: date grouping with a model breakdown on nested data                                                   | A: main chart, normalised per run, with the 8.10 annotation. J: tokens per model well received. B2: the cache-token jump needs explaining to users.                              |
| M8 | Inventory header: agents, processes, external agents | Distinct `agentId`, distinct `processDefinitionKey`, agents with `agentType = EXTERNAL_AGENT`                                                         | Small                                                                                                      | A: "18 agents across 11 processes, 2 external".                                                                                                                                  |

### Process view

|  #  |                                                                  Metric                                                                   |                     Calculation                      |                       Data                        |                                                                                                  Evidence                                                                                                  |
|-----|-------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------|---------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| M9  | ~~Processes table on the overview~~ Dropped after review; process grouping is the process filter (M10) and the inventory's process column | M1, M3, M4, M5, M6 grouped by `processDefinitionKey` | Not built                                         | J: asked about grouping by process, "it might be good to give that by process as well"; her own dashboard reports cost by process. B1: case-level aggregation keeps coming up in interviews. User request. |
| M10 | Process view tiles                                                                                                                        | M1 to M7 filtered to one process                     | Ready once M1 to M7 exist (process filter exists) | As M9.                                                                                                                                                                                                     |
| M11 | Where agents of this process fail                                                                                                         | M16 grouped by agent                                 | New (I2, I3)                                      | J: top failure reasons.                                                                                                                                                                                    |

### Agent inventory tab

|  #  |                                          Column                                           |                                   Calculation                                    |                          Data                           |                                         Evidence                                         |
|-----|-------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|---------------------------------------------------------|------------------------------------------------------------------------------------------|
| M12 | Agent, process, source (Camunda or external), model, last run                             | Agent definition name, process name, `agentType`, latest model, latest run start | Ready (source: Small)                                   | A: inventory columns. B2: inventory confirmed by Walmart's need to find agents to reuse. |
| M13 | Runs, total tokens, tokens per run (average or p50), incident rate, duration (p50 or p95) | M1, M3, M4, M5, M6 per agent                                                     | Ready / Small (incident rate and duration after I1, I2) | A: inventory columns with percentile toggle. J: inventory table well received.           |
| M14 | Human steps per run                                                                       | Average `processUserTaskCount`                                                   | Ready (definition caveat, see Q5)                       | J: important for understanding how often a person is involved. Not in the design.        |

Dropped: Owner (no data source; the design already marks it unconfirmed) and Attention (later phase, L2).

### Single agent view

|  #  |                             Metric                              |                                                                                             Calculation                                                                                              |                                            Data                                             |                                                         Evidence                                                          |
|-----|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| M15 | Header: model and provider, limits, process version, agent type | Latest run's model, provider and limits; agent definition                                                                                                                                            | Ready (deployment date of the version: New, needs the process definition's deployment time) | A: header metadata.                                                                                                       |
| M16 | Failure reasons, ranked                                         | Incident runs (D2) grouped by reason: limit hit (by limit), then incidents by error type and element (agent or which tool), with the top incident messages. Link to the failed instances in Operate. | New (I2, I3)                                                                                | A: "Why agent instances failed". J: top failure reasons with the actual message, e.g. "bearer token no longer available". |
| M17 | Limit hits                                                      | Share of runs with `hitLimit`, and how many stopped at each limit (`limitHit`)                                                                                                                       | Ready                                                                                       | A: limit hits tile ("179 stopped at the 10-call limit"). J: runs hitting limits useful.                                   |
| M18 | Tokens per run against the normal range                         | Per week: p5, p50 and p95 of `totalTokens` (band), plus average                                                                                                                                      | Small (band chart exists for Phase 1 process data)                                          | A: chart with p5 to p95 band and version annotations.                                                                     |
| M19 | Model comparison                                                | Per model (`model` of the run): runs, average tokens, average duration, incident rate                                                                                                                | Small (after I1, I2)                                                                        | J: compare an agent's performance across models (e.g. Haiku vs Sonnet). Not in the design.                                |
| M20 | Tools: calls per run, failure rate                              | Per tool element: sum of `toolCallsByTool.count` / runs; incidents on that tool element / its calls                                                                                                  | Calls Ready; failure rate New (I2). AI Agent Sub-process agents only.                       | A: tools table. J: which tool causes failures.                                                                            |
| M21 | Where this agent is used                                        | Runs and tokens by root process (`rootProcessInstanceId` resolved to its process)                                                                                                                    | New (I4)                                                                                    | A: "Where this agent is used", including call activities.                                                                 |
| M22 | Model calls (iterations) per run                                | Average `modelCalls`, shown next to the model-call limit                                                                                                                                             | Ready                                                                                       | B2: iterations are the requests to the model and matter. A dropped it from the overview, so it lives here.                |

## Later phase

| #  |                         Metric                         |                                                                                                                                                                                                            What it needs                                                                                                                                                                                                            |                                                             Evidence                                                             |
|----|--------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| L1 | Cost per run and total spend                           | A price per model and token type (Q3). Cost = tokens by model and type × price; `tokensByModel` already splits tokens by model.                                                                                                                                                                                                                                                                                                     | J: most requested, to catch agents whose costs run away. B2: deferred, price source open (OpenRouter API vs a maintained table). |
| L2 | Needs attention                                        | Thresholds (Q4). Limit-hit spike and token spike can compare each agent with its previous period or its p95 band. Tool looping: see L4.                                                                                                                                                                                                                                                                                             | A: count tile with reason chips, "untested thresholds". B2: which signals are retrievable is open.                               |
| L3 | Targets (incident rate, duration)                      | Where targets are stored and who sets them (Q3).                                                                                                                                                                                                                                                                                                                                                                                    | A: incident rate target 3 % and per-agent duration targets. J: she set SLA targets in her own dashboard.                         |
| L4 | Repeated tool calls (looping)                          | For AI Agent Sub-process agents, each tool call is an element instance with a start time, so the order of calls in a run and repeated calls of the same tool are available. It needs tool calls attributed to the run's time window first (see Known accuracy limits). Whether repeated calls had the same arguments is only in `AGENT_HISTORY` records, which the exporter's Optimize filter drops and which carry prompt content. | A: tools table column.                                                                                                           |
| L5 | Cache hit rate                                         | `cacheReadTokens / (inputTokens + cacheReadTokens)`; the data is there, the question is whether it earns a place.                                                                                                                                                                                                                                                                                                                   | B1: open question, shows cost savings.                                                                                           |
| L6 | Outcome-based metrics (e.g. cost per approval)         | A standard outcome output on agents.                                                                                                                                                                                                                                                                                                                                                                                                | J: useful but agent-specific.                                                                                                    |
| L7 | Agent grouped across processes ("used in 3 processes") | Grouping by agent name or template instead of process + element (D4).                                                                                                                                                                                                                                                                                                                                                               | A: single agent header.                                                                                                          |

## Implementation

### Import and enrichment (backend)

The enrichment step (`AgentRunEnrichment`) already recomputes each run from its process instance document after every
related import. It gains four fields, all recomputed the same way, so ordering stays safe:

|    |                             Field on the run                             |                                                                                                   Computed from the process instance                                                                                                   |
|----|--------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| I1 | `activeDurationMs`                                                       | Sum of `totalDurationInMs` of the flow node instances in the run's `flowNodeInstanceIds` that have ended. Null until one has ended.                                                                                                    |
| I2 | `toolIncidentCount`, `toolIncidentsByTool` (nested: tool element, count) | Incidents whose element is one of the run's `toolFlowNodeIds`, matched the same way as agent incidents today. A run is an incident run when `hasIncident || toolIncidentCount > 0 || hitLimit`, evaluated as a query (no stored flag). |
| I3 | `failureReasons` (nested: element, error type, message)                  | `incidentType` and `incidentMessage` of the incidents from I2 and the agent element. Messages are truncated, and only the first per element and type is kept.                                                                          |
| I4 | `rootProcessDefinitionKey`                                               | The root process instance's definition key, looked up once per root instance.                                                                                                                                                          |

`optimize-agent-instance` gets the new fields (index version 2 in the prototype; a real migration in production).
Runs imported before these fields exist show "no data" for them, never 0.

### Reports and endpoints

- **Report engine (tiles and charts):** add agent run views for incident rate (D2) and the token components, and percentile
  and average aggregations on `activeDurationMs`. Add a date grouping with a model breakdown for M7 (nested
  `tokensByModel`). Add group-by agent for total tokens.
- **Endpoints (tables and lists that don't fit the report engine):** extend `GET api/dashboard/agentic/agent-inventory`
  (M12 to M14, with the process filter); add `agents/{agentId}` with tools, models, failure reasons and where used
  (M15 to M22). All read the agent run index with aggregations, like the inventory reader.
- **Comparison with the previous period:** reuse the Phase 1 tile comparison; verify it works for agent run reports.

### Frontend

Rebuild `AgenticControlPlane` around two tabs. The dashboard tab shows the KPI row, tokens by model and limit hits for
all processes or the selected one; the inventory tab shows the agent table; both lead to the single agent view. Seeded reports are replaced in
`AgenticControlDashboardService`, as in earlier slices.

### Order of work

1. Enrichment I1 to I4 with unit tests, and the index change.
2. Overview tiles M1 to M8 and the inventory M12 to M14.
3. Process view M10 and M11, and the inventory tab.
4. Single agent view M15 to M22.
5. Frontend rebuild, then an end-to-end check with the generator and a real AI Agent run on the preview.

### Migration

Tally confirmed customers want their existing history (T). Runs migrated from the Phase 1 nested data have their
element instance IDs, incidents and tool elements in the same process instance document, so I1, I2 and I4 can be
computed for them. Limits, reasoning tokens and the per-model split by record do not exist for them; those show "no
data before 8.11".

## Status on the prototype branch (2026-11-03)

Built on `sherrin/agentic-phase2-proto` to the target design in `agentic-flat-indices-overview.md`, except cost, needs
attention and targets (later phase). ES only; OpenSearch has stubs.

- **Schema v2 of `optimize-agent-instance`** (index version 2, re-imported from the zeebe records, no upgrade step):
  - New: `agentType`, `endState`, `failed`, `enriched`, and `kind` / `limit` on `failureReasons`. Enrichment adds one
    `LIMIT` reason per reached limit.
  - `limitHit` holds `MODEL_CALLS`, `TOOL_CALLS`, `TOKENS`. The painless merge script takes these names as a parameter
    from `AgentRunDerivedFields.LIMITS`, and a unit test pins the script's checks to the Java rules.
  - Removed: `durationMs`, `humanWaitDurationMs`, `humanStepCount`, `processUserTaskCount`, `incidentFlowNodeIds`,
    `incidentCount`, `processInstanceState`, `processInstanceEndDate`, with the human wait and human steps views.
- **`optimize-agent-definition`** (index version 2): `model`, `provider`, `max*`, `firstSeen`, `lastSeen` and the per-page
  rollup from runs are removed. Readers take configuration from runs.
- **Incident rate everywhere:** `failed = true` ÷ runs, in the KPI tile (down is good), inventory, failures table and
  model comparison. Success rate is removed.
- **Trends** group by the run's end date (new agent run end-date group-by). Token trend: one weekly line per token type,
  reasoning labelled "of which, in output". Median tokens per model is over each run's `tokensByModel` entry.
- **Process level:** main's process instance tiles (average duration, process incident rate, failure rate by version,
  duration heatmap) plus a tool-call heatmap from the agent index (sum of `toolCalls` per agent element). Process
  instances are restricted with the executed flow node filter on the process's agent elements, added at evaluation
  time; `hasAgentInstances` is no longer used by the dashboard.
- **Agent view:** no header; limit hits per limit table; tools only for AI Agent Sub-process agents (by the runs'
  `agentType`).
- **Inventory:** agent, process, source, model (most runs), runs, total tokens, tokens per run, incident rate, duration
  P50/P95 toggle, last run (latest end date).
- **Enrichment trigger:** the agent run import only reads records up to the position that the process instance and
  incident imports of the partition have persisted (an import whose last empty fetch started a refresh interval after
  the agent fetch counts as passed). A run is enriched once, in the job that writes its `COMPLETED` record, with a
  real-time `mget` of the process instance (`_source` limited to the enrichment fields) and one bulk update. Running
  instances are never enriched. The catch-up listener on process instance imports and its 3 s second pass are removed.
  The incident import re-enriches finished agentic instances only when an incident shows up behind the agent import
  (exporter retry).
- **Data generator:** tool incidents (about 10 % of ad-hoc runs) and one shared position counter in creation order, so
  `COMPLETED` has the highest position of its instance, like the engine log.

### Import measurements (2026-11-03)

Same generated data set (3,000 process instances, seed 42, agent rate 0.6: 901 agent runs, 882 completed, 462
incidents), fresh ES 8.19, Optimize from source, `_stats` per index after the import settled. Before = `c85f58e5de8`,
after = this branch. The watcher that checked progress sent about 70 `_count` queries to `optimize-agent-instance` in
each run, included below.

| | Before | After |
|---|---|---|
| Time from first run imported to every completed run enriched | ~10 s | ~10 s (last 2 runs ~10 s later, held by the gate) |
| Completed runs enriched | 882 / 882 | 882 / 882, all `enriched = true` |
| Queries on `optimize-agent-instance` | 682 | 68 (all from the watcher) |
| Queries on process instance indices | 182 | 0 |
| Write ops on `optimize-agent-instance` | 3,577 | 1,816 |
| Gets on `optimize-agent-instance` / process instance indices | 4,510 / 13,703 | 2,698 / 11,270 |
| Write ops on `optimize-agent-definition` | 123 | 3 (definition import only) |
| Version conflicts logged | 0 | 0 |

Import time on this data set is the same; the saving is in requests, which matters on shared clusters and for
long-running agentic instances. Retried version conflicts are not visible in `_stats`; with the rollups gone the
definition document is written only by the definition import.

### Kept but no longer used

| What | Where | Why it's unused |
|---|---|---|
| Adoption rate view, its plan and the process-instance count in `AgentRunExecutionPlanInterpreterES` | report engine | The adoption tile was dropped. |
| Incident rate by process, tool calls by tool, runs by agent, runs over time, tokens over time, tokens by model (bar) plans and builders | report engine, `AgenticControlDashboardService` | Their tiles were dropped. |
| Phase 1 builders not placed: completed instances, tokens, token trend, outlier bands, P50/P95, total tool calls, top token consumers, agent health, volume and cost sections | `AgenticControlDashboardService` | Only avg duration, incident rate, failure rate by version and the duration heatmap are placed (process level). |
| `HasAgentInstancesFilterDto` and its query filters | filters | Business value dashboard still uses it; the agentic dashboard uses the executed agent element filter. |
| Nested Phase 1 agent data (`agentInstances` in process instances), its import and the `AGENT_INSTANCE` views | Phase 1 import | Needed to migrate history (see Migration). |

Removed on this branch: `durationMs`, `processUserTaskCount`, `incidentFlowNodeIds`, `incidentCount`,
`processInstanceState`, `processInstanceEndDate`, human wait / human steps, success rate, the catch-up listener, the
definition rollups.

## Open questions

| #  |                                                                                                       Question                                                                                                       |                                                                                                                                                                                                                                   Proposal                                                                                                                                                                                                                                   |
|----|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Q1 | For an AI Agent Task, the tools run in a separate ad-hoc sub-process, so the sum of the task's element instances excludes tool time, while for an AI Agent Sub-process it includes it. Do we accept that difference? | Accepted for now and labelled; to confirm. Why: the AI Agent Task is a service task that only calls the model. When the model asks for tools, the task completes and the process moves on to a separate ad-hoc sub-process that runs the tools, then loops back and activates the task again. The task's element instances therefore cover only model time; the tool time sits in the other sub-process's element instances, and nothing in the agent records links the two. |
| Q2 | A tool that is itself a user task makes the sub-process wait for a person, so its time counts as agent time. Exclude user-task tools from I1?                                                                        | Decided: excluded from the run duration, shown as "Human wait per run".                                                                                                                                                                                                                                                                                                                                                                                                      |
| Q3 | Cost and targets: price source (OpenRouter API, open-source table, our own) and where targets are stored.                                                                                                            | Skipped for now (later phase).                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Q4 | "Needs attention" thresholds.                                                                                                                                                                                        | Open; not implemented.                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Q5 | Human steps counts every user task in the process instance. Restrict to user tasks between the run's start and end?                                                                                                  | Decided: only user tasks the agent ran as tools during the run. Gap: a user task modelled outside the agent (such as a chat feedback task between activations) is not counted.                                                                                                                                                                                                                                                                                               |
| Q6 | ~~Process view vs process filter~~                                                                                                                                                                                   | Decided: the process filter switches the dashboard to the process level; no processes table.                                                                                                                                                                                                                                                                                                                                                                                 |
| Q7 | Previous-period comparison on every tile, or only the KPI row?                                                                                                                                                       | Decided: KPI tiles only.                                                                                                                                                                                                                                                                                                                                                                                                                                                     |

