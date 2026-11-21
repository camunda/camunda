# Agentic dashboard: data model, metrics and migration

Status: draft for review, 2026-11-03. Branch `sherrin/agentic-phase2-proto` (draft PR camunda/camunda#67198).
Related: `agentic-schema-v2-agent-prompt.md` (the work list), `agentic-dashboard-v2-plan.md` (the product plan).

**Words used in this document**

- **Agent run**: one execution of one AI agent inside one process instance. The engine calls it an "agent instance".
- **Agent**: one AI agent element in one process, for example the "Support agent" task in the "Order support" process.
- **Completed run**: a run whose process instance has finished or was cancelled. The dashboard only counts completed runs.
- **Incident run**: a run that had an incident on the agent, an incident on one of its tools, or stopped at a limit.

---

## 1. Schema

Optimize stores agent data in two new collections of records (Elasticsearch indices). Both are shared by all processes
and tenants, so the number of indices stays the same no matter how many agents or processes exist.

### 1.1 Agent runs: `optimize-agent-instance` (one record per agent run)

Fields marked **new** or **changed** are part of schema version 2. Every field not listed in this table is removed in
version 2.

|     Group     |                                  Field                                  |          Type          |                                                                             What it holds                                                                              |
|---------------|-------------------------------------------------------------------------|------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Identity      | `agentInstanceId`                                                       | text                   | Unique ID of the run (record ID)                                                                                                                                       |
|               | `agentDefinitionId`                                                     | text                   | ID of the agent definition the run belongs to                                                                                                                          |
|               | `agentId`                                                               | text                   | The agent: `process ID:element ID`                                                                                                                                     |
|               | `agentType` **new**                                                     | text                   | `AI_AGENT_SUB_PROCESS`, `AI_AGENT_TASK` or `EXTERNAL_AGENT`                                                                                                            |
|               | `flowNodeId`                                                            | text                   | BPMN element ID of the agent                                                                                                                                           |
|               | `flowNodeInstanceIds`                                                   | list of text           | Every activation of the agent element in this run                                                                                                                      |
|               | `processDefinitionKey`                                                  | text                   | BPMN process ID                                                                                                                                                        |
|               | `processDefinitionId`                                                   | text                   | Unique ID of the deployed process version                                                                                                                              |
|               | `processDefinitionVersion`                                              | text                   | Process version number                                                                                                                                                 |
|               | `processInstanceId`                                                     | text                   | The process instance the run belongs to                                                                                                                                |
|               | `rootProcessInstanceId`                                                 | text                   | The top-level process instance (differs when reached through a call activity)                                                                                          |
|               | `rootProcessDefinitionKey`                                              | text                   | BPMN process ID of the top-level process                                                                                                                               |
|               | `tenantId`                                                              | text                   | Tenant                                                                                                                                                                 |
| Configuration | `model`, `provider`                                                     | text                   | Model and provider at the end of the run                                                                                                                               |
|               | `maxModelCalls`, `maxToolCalls`, `maxTokens`                            | number                 | Limits configured on the agent. `-1` means no limit                                                                                                                    |
| Timeline      | `startDate`                                                             | date                   | When the run started                                                                                                                                                   |
|               | `lastActivityDate`                                                      | date                   | Last time the agent reported token or call usage                                                                                                                       |
|               | `endDate`                                                               | date                   | When the run ended (the process instance finished or was cancelled)                                                                                                    |
|               | `status`                                                                | text                   | Last status reported by the engine                                                                                                                                     |
|               | `endState` **new**                                                      | text                   | `COMPLETED` or `CANCELED`, from the process instance                                                                                                                   |
| Usage         | `inputTokens`, `outputTokens`, `cacheCreationTokens`, `cacheReadTokens` | number                 | Token totals for the run                                                                                                                                               |
|               | `reasoningTokens`                                                       | number                 | The part of `outputTokens` spent on reasoning (already inside output)                                                                                                  |
|               | `totalTokens`                                                           | number                 | `input + output + cache write + cache read`                                                                                                                            |
|               | `modelCalls`                                                            | number                 | How many times the agent called the model (iterations)                                                                                                                 |
|               | `toolCalls`                                                             | number                 | How many tool calls the model asked for                                                                                                                                |
|               | `tokensByModel`                                                         | list                   | Tokens per model used in the run: `provider`, `model`, the five token types, `totalTokens`                                                                             |
| Outcome       | `hitLimit`                                                              | yes/no                 | The run reached at least one limit                                                                                                                                     |
|               | `limitHit` **changed**                                                  | list of text           | Which limits: `MODEL_CALLS`, `TOOL_CALLS`, `TOKENS`                                                                                                                    |
|               | `hasIncident`                                                           | yes/no                 | An incident on the agent element during the run                                                                                                                        |
|               | `toolIncidentCount`                                                     | number                 | Incidents on the agent's tools during the run                                                                                                                          |
|               | `failed` **new**                                                        | yes/no                 | `hasIncident` or `toolIncidentCount > 0` or `hitLimit`                                                                                                                 |
|               | `failureReasons` **changed**                                            | list                   | One entry per reason: `kind` (**new**: `LIMIT`, `AGENT_INCIDENT`, `TOOL_INCIDENT`), `limit` (**new**), `flowNodeId`, `toolName`, `errorType`, first `message`, `count` |
| Duration      | `activeDurationMs`                                                      | number                 | Time the agent element was active, without time waiting on user-task tools                                                                                             |
| Tools         | `toolFlowNodeIds`                                                       | list of text           | BPMN element IDs of the agent's tools                                                                                                                                  |
|               | `toolCallsByTool`                                                       | list                   | Per tool: element ID, name, number of calls                                                                                                                            |
|               | `toolIncidentsByTool`                                                   | list                   | Per tool: element ID, name, number of incidents                                                                                                                        |
| Bookkeeping   | `lastRecordPosition`                                                    | number                 | Position of the last engine record applied, so old records are never applied twice                                                                                     |
|               | `lastMetricTotals`                                                      | stored, not searchable | Token totals of the last record applied, used to split tokens by model                                                                                                 |
|               | `enriched` **new**                                                      | yes/no                 | Process data has been copied onto the run                                                                                                                              |
|               | `enrichedAt`                                                            | date                   | When process data was last copied                                                                                                                                      |

**Removed in version 2:** `durationMs`, `humanWaitDurationMs`, `humanStepCount`, `processUserTaskCount`,
`incidentFlowNodeIds`, `incidentCount`, `processInstanceState`, `processInstanceEndDate`.

### 1.2 Agents: `optimize-agent-definition` (one record per agent per tenant)

|            Field            |     Type     |                 What it holds                  |
|-----------------------------|--------------|------------------------------------------------|
| `agentId`                   | text         | `process ID:element ID`                        |
| `tenantId`                  | text         | Tenant                                         |
| `processDefinitionKey`      | text         | BPMN process ID                                |
| `flowNodeId`                | text         | BPMN element ID of the agent                   |
| `name`                      | text         | Agent name from the newest process version     |
| `agentType`                 | text         | Agent type from the newest process version     |
| `agentDefinitionIds`        | list of text | Every agent definition ID seen for this agent  |
| `processDefinitionVersions` | list of text | Every process version that contains this agent |

**Removed in version 2:** `model`, `provider`, `maxModelCalls`, `maxToolCalls`, `maxTokens`, `firstSeen`, `lastSeen`.
The runs already hold these values, and keeping a copy here meant updating the same record for every page of runs.

### 1.3 Process instances: no new field

The process level needs the process instances that ran an agent. Optimize already has a filter for process instances
that ran a given element (the executed flow node filter). The dashboard uses this filter with the agent elements of the
selected process. The agent elements come from `optimize-agent-definition`.

Optimize writes nothing new to the process instance records. This replaces the list of agent runs that Phase 1 stores
inside each process instance.

---

## 2. How the values get there

**Agent runs**

1. The engine writes a record when a run starts, every time the agent reports usage or a configuration change, and
   when the run ends. Each record carries running totals, not increments.
2. Optimize reads these records in order and folds all records of one run into one agent run record:
   - The newest record wins for model, limits, status and token totals.
   - Start date comes from the first record and end date from the last one.
   - Each time the token totals grow, the growth is added to the model in use at that moment. This fills
     `tokensByModel`.
   - `totalTokens`, `hitLimit` and `limitHit` are calculated straight away from the totals and the limits.
3. When the run ends, Optimize copies what it needs from the process instance onto the run, in the same write.
   This is called enrichment and fills:
   - incidents on the agent and on its tools, and the failure reasons
   - tool calls per tool
   - active duration
   - end state
   - agent type
   - top-level process
   - `failed`

   Runs of running process instances are not enriched, because the dashboard only shows completed runs.

**Why the process data is always complete at enrichment**

Optimize reads agent records, process records and incident records in three separate imports. Each import can be
ahead of or behind the others. The design uses three facts:

1. Zeebe keeps all records of one process instance on one partition. This includes child processes of call activities.
2. On a partition, each record has a position number, and the number only goes up.
3. Zeebe writes the last record of a run (`COMPLETED`) only when the process instance ends. All element and incident
   records of that process instance have lower positions.

The rule: **the agent import does not read past the position that the process import and the incident import already
stored for that partition.** So when the agent import reads the last record of a run, Optimize already has the full
process instance. The agent import then reads the process instance once and writes the enriched run. Optimize needs no
search, no timer and no second attempt.

- An import with no new records does not stop the agent import. It counts as up to date when its last empty read
  started after the agent import read the page. This can add up to 30 seconds of delay, because an idle import
  waits up to 30 seconds between reads.
- After a restart, each import continues from its stored position. Optimize does not keep a list of runs in memory.
- Rare case: if Zeebe retries a failed export, an incident can become visible after the run. When the incident import
  stores an incident on a finished process instance with agents, it enriches the runs of that process instance again.
  Enrichment always recalculates everything, so a second enrichment gives the same result.

**Where the prototype differs**

- The top-level process of a run reached through a call activity is looked up with one search per import page, not an
  mget: process instances are stored in one index per process, and the top-level process is what is unknown.
- The rare exporter retry case searches the runs of the affected process instance.
- The agent's model and limits are read from its runs; the inventory shows the model most runs used.
- An import counts as idle when its last read found nothing, every page it read is written, and that read started one
  refresh interval (read from the `zeebe-record-*` index settings) after the agent import read the page.

**Agents**

- The engine writes one agent definition record per agent element when a process version is deployed.
- Optimize keeps one record per agent and updates name and type from the newest version.

**Process instances**

- No change. The process import writes process instances the same way as today.

---

## 3. Metrics

Rules for every metric:

- Only completed runs count.
- The date filter applies to the run's end date.
- Charts over time group by the run's end date, the same as the trend charts on `main`.
- **Incident rate** = incident runs ÷ all runs.

### 3.1 Overview level (all processes)

|                    Metric                     |                                                    How it is calculated                                                    |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| Total tokens, with change vs previous period  | Sum of `totalTokens`. The change compares with the period of the same length right before.                                 |
| Average tokens per run                        | Average of `totalTokens`. Runs without token data are skipped.                                                             |
| Agent runs, with change vs previous period    | Number of runs                                                                                                             |
| Incident rate, with change vs previous period | Runs with `failed` = yes ÷ all runs. Going down is good.                                                                   |
| Run duration: average, P50, P95               | Average and percentiles of `activeDurationMs`                                                                              |
| Token trend                                   | Per week: sum of input, output, cache write, cache read and reasoning tokens. Reasoning is labelled "of which, in output". |
| Median tokens per run by model, weekly        | Per week and per model: median of the tokens each run spent on that model (from `tokensByModel`)                           |
| Limit-hit rate                                | Runs with `hitLimit` = yes ÷ all runs                                                                                      |

### 3.2 Process level (one process selected)

|               Metric               |                                                                                     How it is calculated                                                                                      |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| All overview metrics               | Same calculations, only runs of the selected process                                                                                                                                          |
| Average process run duration       | Same as `main`: average duration of completed process instances that ran an agent element                                                                                                     |
| Process incident rate              | Same as `main`: share of completed process instances that ran an agent element and had a resolved incident                                                                                    |
| Where agents fail, per agent       | Runs; failed runs (`failed` = yes); incident rate (failed runs ÷ runs); top failure reasons from `failureReasons`, grouped by kind, then element, error type or limit, with the first message |
| Failure rate by process version    | Same as `main`: process incident rate per process version                                                                                                                                     |
| Tool calls per flow node (heatmap) | Sum of `toolCalls` per agent element, shown on the diagram                                                                                                                                    |
| Duration per flow node (heatmap)   | Same as `main`: average duration per element of process instances that ran an agent element                                                                                                   |

### 3.3 Agent level (one agent selected)

|                                    Metric                                     |                                                                          How it is calculated                                                                           |
|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Total tokens, average tokens per run, agent runs, incident rate, run duration | Same as the overview, only runs of this agent                                                                                                                           |
| Tokens per run band, weekly                                                   | Per week: P5, P50, P95 and average of `totalTokens`                                                                                                                     |
| Iterations                                                                    | Average of `modelCalls`                                                                                                                                                 |
| Limit-hit rate                                                                | Runs with `hitLimit` = yes ÷ runs                                                                                                                                       |
| Limit hits per limit                                                          | Number of runs per value in `limitHit`                                                                                                                                  |
| Failure reasons                                                               | From `failureReasons`: kind, element (agent or which tool), error type, first message, number of runs                                                                   |
| Tools                                                                         | Calls = sum of tool calls per tool. Calls per run = calls ÷ runs. Failure rate = tool incidents ÷ calls. Only for AI Agent Sub-process agents; other types show a note. |
| Model comparison                                                              | Per model: runs, average `totalTokens`, average `activeDurationMs`, incident rate                                                                                       |
| Where used                                                                    | Per top-level process: runs, share of this agent's runs, sum of `totalTokens`                                                                                           |

### 3.4 Agent inventory

|                Item                |                               How it is calculated                               |
|------------------------------------|----------------------------------------------------------------------------------|
| Summary                            | Number of agents, number of processes, number of agents of type `EXTERNAL_AGENT` |
| Agent, process, source             | From the agent record: name, process, type (Camunda or external)                 |
| Model                              | Model used by most of the agent's runs                                           |
| Runs, total tokens, tokens per run | Number of runs; sum and average of `totalTokens`                                 |
| Incident rate                      | Runs with `failed` = yes ÷ runs                                                  |
| Duration (P50 or P95)              | Percentile of `activeDurationMs`                                                 |
| Last run                           | Latest end date                                                                  |

### 3.5 Known limits

- Tool metrics need the agent's tool elements. This works for AI Agent Sub-process agents, is unverified for AI Agent
  Task, and is not possible for external agents.
- If one process instance runs the same agent element several times, those runs share the tool call count.
- For an AI Agent Task, the tools run in a separate sub-process, so their time is not part of the run duration.

---

## 4. Migration from Phase 1 to the new records

### 4.1 Starting point

- Phase 1 (from 8.10) stores each process instance's agent runs as a list inside the process instance record.
- The new version stores them as separate agent run records.
- Customers want to keep their history.

### 4.2 What the Phase 1 data contains

|                         Available in Phase 1 data                         |                                        Missing in Phase 1 data                                        |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| Run ID, agent element, element activations, start and end dates, status   | Limits, so no limit hits                                                                              |
| Model and provider (latest only)                                          | Reasoning tokens                                                                                      |
| Input, output, cache write and cache read tokens; model calls; tool calls | Tokens per model, for runs that switched model                                                        |
| Tool names                                                                | Tool element IDs, so tools can't be matched to diagram elements unless the name equals the element ID |
| The whole process instance (incidents, element durations, tenant)         | Agent definition ID, agent name and type, top-level process                                           |

### 4.3 Plan

1. **Import what the engine still has.**
   - The new import starts from the oldest engine record that is still kept: 7 days on SaaS; forever by default on
     self-managed, or 30 days with the Helm retention setting.
   - Runs found there get complete data.
2. **Copy the rest from Phase 1.** A one-time background job walks every process instance that has Phase 1 agent data.
   For each run in it:
   - Create the agent run record only if it doesn't exist yet. Never overwrite a record built from engine records,
     which is richer.
   - Fill everything listed as available above. Calculate incidents, tool calls and active duration from the same
     process instance, using the same rules as enrichment.
   - Leave the missing fields empty, not 0, so the dashboard shows "no data" instead of wrong numbers. In particular,
     `hitLimit` must stay empty, or old runs would lower the limit-hit rate.
   - Create agent records from the runs where none exist. Use the element ID as the name until a new process version
     brings the real one.
   - Mark the job as done per process index, so it can stop and continue.
3. **Stop writing Phase 1 data** once the job has finished everywhere.
4. **Remove Phase 1 data** in a later release (see bottleneck 4).

The job writes nothing to the process instance records. The process level finds agentic process instances with the
executed flow node filter, which works for old and new process instances.

### 4.4 Bottlenecks we expect

1. **Reading large process instance records.**
   - Phase 1 data sits inside the process instance records, and these hold every element and incident of the instance.
   - The job has to read each agentic process instance in full, so long-running chat agents are slow to read.
   - Read in pages, read only the fields needed, and limit how fast the job runs.
2. **Upgrade time.**
   - Optimize upgrades run while Optimize is stopped. Doing the copy inside the upgrade could keep Optimize down for
     hours on large installations.
   - The copy should run in the background after start-up, and the dashboard should say that history is still being
     prepared.
3. **Runs that exist twice.**
   - The same run can come from the engine records and from Phase 1.
   - The "create only if missing" rule handles this. The engine import must still win if it arrives after the copy.
4. **Removing Phase 1 data.**
   - Elasticsearch can't drop a field from existing records cheaply. Removing the Phase 1 list means rewriting every
     process instance record again, or rebuilding the process indices.
   - Simplest is to stop writing it, stop reading it, and leave it until a release that rebuilds those indices anyway.
5. **Mixed history.**
   - Charts that cross the migration date mix runs with and without limits, reasoning tokens and per-model tokens.
   - Mark the migration date on the charts, and show "no data before X" where a value is missing.
6. **Load on shared clusters (SaaS).**
   - The job competes with the normal import.
   - Pause it when the import falls behind, and run it with a small batch size.
7. **Phase 1 data that was never stored.**
   - With the nested-records limit, Optimize may have skipped agent data for very long process instances.
   - Those runs can't be recovered from Phase 1, only from engine records that are still kept.

