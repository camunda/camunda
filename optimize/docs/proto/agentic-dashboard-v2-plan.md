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

## Decisions

| # | Decision | Why |
|---|---|---|
| D1 | **Agent run duration is the sum of the durations of the agent's element instances.** For an AI Agent Sub-process that is each activation of the ad-hoc sub-process, tools included. For an AI Agent Task it is each activation of the service task. | One agent run can span several element instances, for example a chat agent that is re-entered after a user feedback task. Today's `durationMs` (first record to last activity) counts the time the user took in between. Agreed in the metric-validation BI sync (B2). See Q1 for the AI Agent Task case. |
| D2 | **A run succeeds when it has no incident on the agent element or on one of its tool elements, and hit no limit.** Success rate = successful runs / completed runs. | "Success rate" framing agreed in the metric-validation BI sync (B2). Tool incidents count, so "tool or connector failure" shows up as a failure reason. |
| D3 | **Total tokens = input + output + cache write + cache read.** Reasoning is shown as "of which reasoning" inside output, not added on top. | The connector already counts reasoning inside output (Phase 2 findings). The design's mock adds it on top, which double-counts. |
| D4 | **An agent is identified by process + element** (`agentId = processDefinitionKey:flowNodeId`). Grouping by agent name across processes is optional, later. | Process-level grouping is the main grouping (see the evidence for M9). It also keeps one agent definition per BPMN element, which is how the engine records them. |
| D5 | **The unit is the agent run** (agent instance). Iterations (model calls) and element instances are measures of a run, not separate units. | Keep it simple and use agent instances for volume (MVP-metrics BI sync, B1). Iterations are the run's `modelCalls`, so no agent history import is needed. |
| D6 | **Cost, needs attention and targets are a later phase.** | Cost deferred in the metric-validation BI sync (B2); thresholds and targets need product decisions (Q3, Q4). |
| D7 | **Only completed runs count**, and the date range applies to the run's end date. | Same as the prototype and Phase 1. |

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

Not carried over from the current prototype: standalone incident rate (folded into success rate), adoption rate and the
process donut (the process count moves into the inventory header), tool call totals and top tools across all agents,
tokens by provider (covered by tokens by model), runs over time, and the Phase 1 process tiles (replaced by the process
view). This matches the design's "Left off, and why" list.

## Metrics

Status: **Ready** = works on today's prototype data; **Small** = new report type, column or endpoint field over stored
data; **New** = needs a new stored field or new logic (see Implementation).

Evidence sources:

- **A**: Omran's dashboard artifact (including its validation notes, such as the survey ranking).
- **J**: Phase 2 discovery session with Joyce.
- **B1**: BI daily sync on the MVP metric set (token usage, volume, requests). **B2**: BI daily sync validating the metrics with Omran (cost, success rate, duration, iterations).
- **E**: feedback session with Eric, as summarised in B1.
- **T**: Tally's reply on history migration.

### Overview

| # | Metric | Calculation | Data | Evidence |
|---|---|---|---|---|
| M1 | Total tokens, change vs previous period | Sum of `totalTokens` over completed runs in the period. Previous period = same length directly before. | Small: sum view on agent runs; comparison already exists for Phase 1 tiles, to verify on agent run reports | A: first tile, ranked most important in the survey. J: cost and token usage are the most important metrics for business analysts. B1: token usage/spend is the first MVP metric. |
| M2 | Token breakdown | Sums of `inputTokens`, `outputTokens`, `cacheCreationTokens`, `cacheReadTokens`; `reasoningTokens` shown as part of output (D3) | Small: one view per field | A: breakdown in the total tokens tile. J: input vs output may be useful, importance unclear. B1: cache tokens show cost savings. |
| M3 | Average tokens per agent run | Average of `totalTokens` | Ready | A: subline of the total tokens tile. |
| M4 | Agent runs | Count of completed runs | Ready | A: "across 31,940 agent instances". B1: volume as agent instances. |
| M5 | Success rate, change vs previous period | Runs without an incident on the agent or its tools and without a limit hit, divided by all runs (D2) | New: tool incidents (I2); rate view on a derived `succeeded` flag | A: second tile. B2: success-rate framing agreed. J: success and the "94.2 %" style number work for business users. |
| M6 | Agent run duration: average, p50, p95 | Average and percentiles of `activeDurationMs` = sum of the agent's element instance durations (D1) | New: `activeDurationMs` (I1) | A: third tile (p50, p95). J: make duration more prominent, show average run duration. B2: duration as the sum of element instance durations. |
| M7 | Median tokens per agent run over time, by model | Per week of end date and per model: median of the run's tokens on that model (`tokensByModel.totalTokens`). Annotation at 8.10: cache tokens counted. | New: date grouping with a model breakdown on nested data | A: main chart, normalised per run, with the 8.10 annotation. J: tokens per model well received. B2: the cache-token jump needs explaining to users. |
| M8 | Inventory header: agents, processes, external agents | Distinct `agentId`, distinct `processDefinitionKey`, agents with `agentType = EXTERNAL_AGENT` | Small | A: "18 agents across 11 processes, 2 external". |

### Process view

| # | Metric | Calculation | Data | Evidence |
|---|---|---|---|---|
| M9 | ~~Processes table on the overview~~ Dropped after review; process grouping is the process filter (M10) and the inventory's process column | M1, M3, M4, M5, M6 grouped by `processDefinitionKey` | Not built | J: asked about grouping by process, "it might be good to give that by process as well"; her own dashboard reports cost by process. B1: case-level aggregation keeps coming up in interviews. User request. |
| M10 | Process view tiles | M1 to M7 filtered to one process | Ready once M1 to M7 exist (process filter exists) | As M9. |
| M11 | Where agents of this process fail | M16 grouped by agent | New (I2, I3) | J: top failure reasons. |

### Agent inventory tab

| # | Column | Calculation | Data | Evidence |
|---|---|---|---|---|
| M12 | Agent, process, source (Camunda or external), model, last run | Agent definition name, process name, `agentType`, latest model, latest run start | Ready (source: Small) | A: inventory columns. B2: inventory confirmed by Walmart's need to find agents to reuse. |
| M13 | Runs, total tokens, tokens per run (average or p50), success rate, duration (p50 or p95) | M1, M3, M4, M5, M6 per agent | Ready / Small (success and duration after I1, I2) | A: inventory columns with percentile toggle. J: inventory table well received. |
| M14 | Human steps per run | Average `processUserTaskCount` | Ready (definition caveat, see Q5) | J: important for understanding how often a person is involved. Not in the design. |

Dropped: Owner (no data source; the design already marks it unconfirmed) and Attention (later phase, L2).

### Single agent view

| # | Metric | Calculation | Data | Evidence |
|---|---|---|---|---|
| M15 | Header: model and provider, limits, process version, agent type | Latest run's model, provider and limits; agent definition | Ready (deployment date of the version: New, needs the process definition's deployment time) | A: header metadata. |
| M16 | Failure reasons, ranked | Failed runs (D2) grouped by reason: limit hit (by limit), then incidents by error type and element (agent or which tool), with the top incident messages. Link to the failed instances in Operate. | New (I2, I3) | A: "Why agent instances failed". J: top failure reasons with the actual message, e.g. "bearer token no longer available". |
| M17 | Limit hits | Share of runs with `hitLimit`, and how many stopped at each limit (`limitHit`) | Ready | A: limit hits tile ("179 stopped at the 10-call limit"). J: runs hitting limits useful. |
| M18 | Tokens per run against the normal range | Per week: p5, p50 and p95 of `totalTokens` (band), plus average | Small (band chart exists for Phase 1 process data) | A: chart with p5 to p95 band and version annotations. |
| M19 | Model comparison | Per model (`model` of the run): runs, average tokens, average duration, success rate | Small (after I1, I2) | J: compare an agent's performance across models (e.g. Haiku vs Sonnet). Not in the design. |
| M20 | Tools: calls per run, failure rate | Per tool element: sum of `toolCallsByTool.count` / runs; incidents on that tool element / its calls | Calls Ready; failure rate New (I2). AI Agent Sub-process agents only. | A: tools table. J: which tool causes failures. |
| M21 | Where this agent is used | Runs and tokens by root process (`rootProcessInstanceId` resolved to its process) | New (I4) | A: "Where this agent is used", including call activities. |
| M22 | Model calls (iterations) per run | Average `modelCalls`, shown next to the model-call limit | Ready | B2: iterations are the requests to the model and matter. A dropped it from the overview, so it lives here. |

## Later phase

| # | Metric | What it needs | Evidence |
|---|---|---|---|
| L1 | Cost per run and total spend | A price per model and token type (Q3). Cost = tokens by model and type × price; `tokensByModel` already splits tokens by model. | J: most requested, to catch agents whose costs run away. B2: deferred, price source open (OpenRouter API vs a maintained table). |
| L2 | Needs attention | Thresholds (Q4). Limit-hit spike and token spike can compare each agent with its previous period or its p95 band. Tool looping needs the order of tool calls (L4). | A: count tile with reason chips, "untested thresholds". B2: which signals are retrievable is open. |
| L3 | Targets (success, duration) | Where targets are stored and who sets them (Q3). | A: target 97 % and per-agent duration targets. J: she set SLA targets in her own dashboard. |
| L4 | Repeated tool calls (looping) | The sequence of tool calls per run, only in `AGENT_HISTORY` records, which the exporter's Optimize filter drops and which carry prompt content. | A: tools table column. |
| L5 | Cache hit rate | `cacheReadTokens / (inputTokens + cacheReadTokens)`; the data is there, the question is whether it earns a place. | B1: open question, shows cost savings. |
| L6 | Outcome-based metrics (e.g. cost per approval) | A standard outcome output on agents. | J: useful but agent-specific. |
| L7 | Agent grouped across processes ("used in 3 processes") | Grouping by agent name or template instead of process + element (D4). | A: single agent header. |

## Implementation

### Import and enrichment (backend)

The enrichment step (`AgentRunEnrichment`) already recomputes each run from its process instance document after every
related import. It gains four fields, all recomputed the same way, so ordering stays safe:

| | Field on the run | Computed from the process instance |
|---|---|---|
| I1 | `activeDurationMs` | Sum of `totalDurationInMs` of the flow node instances in the run's `flowNodeInstanceIds` that have ended. Null until one has ended. |
| I2 | `toolIncidentCount`, `toolIncidentsByTool` (nested: tool element, count) and a derived `succeeded` flag | Incidents whose element is one of the run's `toolFlowNodeIds`, matched the same way as agent incidents today. `succeeded = !hasIncident && toolIncidentCount == 0 && !hitLimit`. |
| I3 | `failureReasons` (nested: element, error type, message) | `incidentType` and `incidentMessage` of the incidents from I2 and the agent element. Messages are truncated, and only the first per element and type is kept. |
| I4 | `rootProcessDefinitionKey` | The root process instance's definition key, looked up once per root instance. |

`optimize-agent-instance` gets the new fields (index version 2 in the prototype; a real migration in production).
Runs imported before these fields exist show "no data" for them, never 0.

### Reports and endpoints

- **Report engine (tiles and charts):** add agent run views for `SUCCESS_RATE` and the token components, and percentile
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

## Status on the prototype branch (2026-10-29)

Built on `sherrin/agentic-phase2-proto`, except cost, needs attention and targets (later phase).

- **Enrichment:** I1 to I4, plus:
  - `humanWaitDurationMs`: time spent in user-task tools during the run. It is excluded from `activeDurationMs` (Q2) and shown in its own KPI tile.
  - `humanStepCount`: the user-task tools of the run, which replaces "every user task of the process instance" (Q5).
- **Index fields:** the new fields are added to `optimize-agent-instance` as new mapping fields, not as index version 2. The schema manager adds them to the existing index on startup, so runs already imported keep their documents. Those runs only get the new fields when their process instance is imported again.
- **Success is a query, not a stored flag:** a run succeeded when it has no incident on the agent, no tool incident (`toolIncidentCount`) and no limit hit. This avoids ordering problems between the agent run import, which sets the limit fields, and the enrichment, which sets the incident fields. A run that isn't enriched yet counts as succeeded.
- **Reports:**
  - New agent run views: success rate, input, output, cache write, cache read, reasoning tokens, and human wait.
  - The duration view now reads `activeDurationMs`.
  - The human steps view now reads `humanStepCount`.
  - New total tokens by agent.
  - New `agentModel` distribution, used by M7. The distribution is now part of the command key for agent run reports.
- **Endpoints:**
  - The inventory takes `processDefinitionKey` and returns a `summary`.
  - New `agents/{agentId}`, covering M15 to M22.
  - New `failures?processDefinitionKey=`, for M11.
- **Dashboard:** 12 seeded tiles in three sections.
  - `kpi`: shown at every level, with the previous-period comparison on total tokens, runs and success rate (Q7).
  - `trends`: on the dashboard tab.
  - `agentTrends`: in the agent view.
- **Frontend:** Dashboard and Agent inventory tabs, plus the single agent view.
- **Gaps found while building:**
  - The inventory, failures and agent endpoints don't take the version and model filters, so their tables can disagree with the tiles while those filters are set.
  - M15 has no deployment date of the version.
  - M16 has no link to the failed instances in Operate.
  - For an AI Agent Task, the run duration excludes tool time (Q1); this is labelled in the duration tile.
  - The agent endpoint's `toolsAvailable` doesn't say why tools are missing.

### Kept but no longer used (flagged for removal, nothing removed)

| What | Where | Why it's unused |
|---|---|---|
| `durationMs` (first record to last activity) | `AgentRunDto`, `AgentRunIndex`, `AgentRunDerivedFields`, writer merge script | Duration tiles and the inventory read `activeDurationMs` (D1). |
| `processUserTaskCount` | enrichment, index | Human steps read `humanStepCount` (Q5). |
| `incidentFlowNodeIds` | enrichment, index | `failureReasons` carries the element per incident. |
| `processInstanceState`, `processInstanceEndDate` | enrichment, index | No tile or endpoint reads them; filters use the run's own `endDate`. |
| Adoption rate view, its plan and the process-instance count in `AgentRunExecutionPlanInterpreterES` | report engine | The adoption tile was dropped (A, "left off"). |
| Incident rate views (`AGENT_RUN_INCIDENT_RATE_*`), incident rate by process | report engine | Folded into success rate. The inventory still returns `incidentRate`. |
| Tool calls by tool, runs by agent, runs over time, tokens over time, tokens by model (bar) plans | report engine | Their tiles were dropped. The agent endpoint covers tools. |
| Phase 1 tiles and builders: completed instances, avg duration, incident rate, tokens, token trend, outlier bands, P50/P95, failure rate by version, tool calls and heatmaps, top token consumers, agent health, volume and cost sections | `AgenticControlDashboardService` | No longer placed on the dashboard. Their reports stay in the database from earlier starts. |
| Nested Phase 1 agent data (`agentInstances` in process instances), its import and the `AGENT_INSTANCE` views | Phase 1 import | The dashboard no longer reads it. Still needed to migrate history (see Migration). |

## Open questions

| # | Question | Proposal |
|---|---|---|
| Q1 | For an AI Agent Task, the tools run in a separate ad-hoc sub-process, so the sum of the task's element instances excludes tool time, while for an AI Agent Sub-process it includes it. Do we accept that difference? | Accepted for now and labelled; to confirm. Why: the AI Agent Task is a service task that only calls the model. When the model asks for tools, the task completes and the process moves on to a separate ad-hoc sub-process that runs the tools, then loops back and activates the task again. The task's element instances therefore cover only model time; the tool time sits in the other sub-process's element instances, and nothing in the agent records links the two. |
| Q2 | A tool that is itself a user task makes the sub-process wait for a person, so its time counts as agent time. Exclude user-task tools from I1? | Decided: excluded from the run duration, shown as "Human wait per run". |
| Q3 | Cost and targets: price source (OpenRouter API, open-source table, our own) and where targets are stored. | Skipped for now (later phase). |
| Q4 | "Needs attention" thresholds. | Open; not implemented. |
| Q5 | Human steps counts every user task in the process instance. Restrict to user tasks between the run's start and end? | Decided: only user tasks the agent ran as tools during the run. Gap: a user task modelled outside the agent (such as a chat feedback task between activations) is not counted. |
| Q6 | ~~Process view vs process filter~~ | Decided: the process filter switches the dashboard to the process level; no processes table. |
| Q7 | Previous-period comparison on every tile, or only the KPI row? | Decided: KPI tiles only. |
