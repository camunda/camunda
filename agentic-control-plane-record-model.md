# AgentInstance — Zeebe Record Model

Derived from the v4 gap analysis. Records are exported to Elasticsearch and consumed by Optimize as the source of truth for the Agentic Control Plane dashboard.

---

## Intents

| Intent (command) | Event produced |                                                            When                                                             |
|------------------|----------------|-----------------------------------------------------------------------------------------------------------------------------|
| `CREATE`         | `CREATED`      | Engine activates the AHSP element; definition and limits are set and immutable from this point.                             |
| `UPDATE`         | `UPDATED`      | Connector reports a delta after one LLM or tool call cycle; engine accumulates totals.                                      |
| `COMPLETE`       | `COMPLETED`    | Agent finishes successfully; `completionDate` is stamped.                                                                   |
| `FAIL`           | `FAILED`       | Engine raises an incident on the element instance (retries exhausted, limit exceeded, unrecoverable error). Terminal state. |

---

## Phase 1 Record — Minimum Required for the Dashboard

This is the record that must be exported for every intent/event for the dashboard to be fully functional.

```
AgentInstanceRecord {

  // ── Identity ──────────────────────────────────────────────────────────────
  agentInstanceKey          long       // unique key for this agent instance
  elementInstanceKey        long       // key of the AHSP element instance in the engine
  elementId                 string     // BPMN element ID of the AHSP
  processInstanceKey        long       // owning process instance
  processDefinitionKey      long       // process definition this agent belongs to
  processDefinitionVersion  int        // version of the process definition (required, not optional)
  versionTag                string?    // user-defined version tag, null if not set
  tenantId                  string     // tenant identifier

  // ── Status ────────────────────────────────────────────────────────────────
  status  AgentInstanceStatus

  // ── Timestamps ────────────────────────────────────────────────────────────
  creationDate    datetime   // stamped on CREATED
  completionDate  datetime?  // stamped on COMPLETED or FAILED; null while running

  // ── Definition (immutable after CREATED) ──────────────────────────────────
  definition {
    model       string   // LLM model identifier, e.g. "gpt-4o"
    provider    string   // LLM provider, e.g. "openai"
    systemPrompt  string
  }

  // ── Limits (immutable after CREATED; -1 = unset) ──────────────────────────
  limits {
    maxTokens      long
    maxModelCalls  int
    maxToolCalls   int
  }

  // ── Metrics (running totals; updated on every UPDATED event) ──────────────
  metrics {
    inputTokens   long   // total input tokens consumed across all LLM calls
    outputTokens  long   // total output tokens produced across all LLM calls
    modelCalls    int    // total number of LLM calls made
    toolCalls     int    // total number of tool invocations made
  }

}
```

### `AgentInstanceStatus` enum

```
INITIALIZING    // agent is starting up
TOOL_DISCOVERY  // agent is discovering available tools
THINKING        // LLM is processing (model call in flight)
TOOL_CALLING    // a tool invocation is in flight
IDLE            // waiting between cycles
COMPLETED       // agent finished successfully  ← terminal
FAILED          // unrecoverable error; incident raised on element instance  ← terminal (NEW)
```

> `FAILED` is the only addition to the status enum required for phase 1. It is set by the engine when it raises an incident on the AHSP element instance. Both `COMPLETED` and `FAILED` are terminal states; `completionDate` is stamped on both.

---

## Metrics Dual-Semantics Contract

The metrics section carries running totals, built from deltas sent by the connector:

```
On UPDATE command:
  connector sends → { inputTokensDelta, outputTokensDelta, modelCallsDelta, toolCallsDelta }
  engine accumulates → metrics.inputTokens  += inputTokensDelta
                       metrics.outputTokens += outputTokensDelta
                       metrics.modelCalls   += modelCallsDelta
                       metrics.toolCalls    += toolCallsDelta

On UPDATED event exported to Elasticsearch:
  all fields reflect current running totals at that point in time.

On COMPLETED / FAILED event:
  all metrics fields reflect final totals for the run.
```

Optimize reads the latest event per `agentInstanceKey` to get final totals.

---

## Phase 2 Extensions

These additions are not required to build the initial dashboard but extend coverage for recoverable incident tracking, cost analysis, and duration as a first-class metric.

```
AgentInstanceRecord (phase 2 additions) {

  metrics {
    ...phase 1 fields...

    // Incident counters (G4) — removes dependency on FAILED-count proxy;
    // captures incidents that were raised and resolved without ending the run.
    agentIncidentCount        int   // total incidents raised on this agent instance
    toolCallIncidentCount     int   // incidents raised specifically on tool call elements

    // Reasoning tokens (G8) — null when the LLM provider does not report separately.
    reasoningTokens  long?

    // Duration (G1) — derived from timestamps in phase 1 but useful as a
    // first-class metric for cross-component reuse (Operate panel headers, etc.)
    totalDurationMs  long?   // null while running; stamped on COMPLETED / FAILED
  }

  definition {
    ...phase 1 fields...

    // Tool definitions (G6) — enables "configured vs used" comparisons and
    // provides tool name labels before history export is available.
    tools  []{
      name    string
      source  enum { AD_HOC, MCP, A2A }
    }
  }

}
```

---

## What Each Dashboard Element Reads

|        Dashboard element        |                               Fields consumed                               |      Phase       |
|---------------------------------|-----------------------------------------------------------------------------|------------------|
| Total Runs KPI                  | `agentInstanceKey` count where `status = COMPLETED`                         | 1                |
| Avg Run Duration KPI            | `completionDate − creationDate`                                             | 1                |
| Incident Rate KPI               | `count(status = FAILED) / count(all)`                                       | 1 (via G7)       |
| Avg / Median tokens per run     | `inputTokens + outputTokens`                                                | 1                |
| Token trend                     | `inputTokens`, `outputTokens`, `creationDate`                               | 1                |
| Token outlier bands             | `inputTokens + outputTokens` percentiles                                    | 1                |
| Top consumers by process        | `inputTokens + outputTokens` grouped by `processDefinitionKey`              | 1                |
| Avg tokens per call to agent    | `(inputTokens + outputTokens) / modelCalls` grouped by `elementId`          | 1                |
| Tool call frequency (aggregate) | `toolCalls` summed per `elementId`                                          | 1                |
| Tool call frequency (per tool)  | History index — not in AgentInstance record                                 | Future           |
| Failure rate by process version | `count(status = FAILED) / count(all)` grouped by `processDefinitionVersion` | 1 (via G7 + G10) |
| Duration P50 / P95              | `completionDate − creationDate` percentiles                                 | 1                |
| Duration stability chart        | same, bucketed over time by `creationDate`                                  | 1                |
| Agent list — Runs               | count of COMPLETED records per `elementId`                                  | 1                |
| Agent list — Success %          | `count(status = COMPLETED) / count(all)` per `elementId`                    | 1 (via G7)       |
| Agent list — Incidents          | count of `status = FAILED` per `elementId` (phase 1 proxy)                  | 1 (via G7)       |
| Agent list — Status badge       | derived from failure rate + current status                                  | 1 (via G7)       |
| Incident Rate (recoverable)     | `agentIncidentCount > 0`                                                    | 2 (G4)           |
| Token cost with reasoning       | `inputTokens + outputTokens + reasoningTokens`                              | 2 (G8)           |

