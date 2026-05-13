# Agentic Control Plane — Solution Proposal
## Dedicated AgentInstanceIndex Architecture

**Audience**: Product Owners, Architects
**Author**: Alexandre Janoni
**Technical spec**: `agentic-control-plane-technical-separated-index-spec.md`

---

## Problem

Camunda 8 process instances can now embed AI agent executions — LLM-powered tasks that run
autonomously inside a BPMN process. Operators have no visibility into how those agents perform:
token consumption, execution time, failure rate, and reliability across process versions.

---

## Solution

A new **Agentic Control Plane** dashboard inside Optimize delivering read-only analytics across
three filter levels — fleet (all processes), process (one process), and agent (one agent task
within a process). Metrics cover token usage, duration, incident rate, tool call frequency, and
trend over time. Week-over-week deltas on all top-level KPIs.

---

## Architectural Approach

### Each agent activation is a standalone document in its own index

Agent instance records emitted by Zeebe are imported into Optimize as **flat top-level documents
in a dedicated `AgentInstanceIndex`** — one index per process definition key, following the same
naming convention as the existing process instance indices.
`ProcessInstanceIndex` is not modified in any way.

```
AgentInstanceIndex  (optimize-agent-instance-<processDefinitionKey>)
└── one document per agent activation
    ├── agentInstanceKey, elementId, processInstanceKey
    ├── status, creationDate, completionDate, durationInMs
    ├── definition: model, provider
    └── metrics: inputTokens, outputTokens, modelCalls, toolCalls

ProcessInstanceIndex  — unchanged
```

### Data flow

```
Zeebe engine
  └─► AGENT_INSTANCE records (CREATED / UPDATED / COMPLETED)
        └─► Optimize import pipeline  (standard index + partial update by key)
              └─► AgentInstanceIndex  (one flat document per activation)
                    │
                    │  + ProcessInstanceIndex  (read-only — incidents + process duration)
                    │
                    └─► 10 dashboard API endpoints  (merge in application layer where needed)
                          └─► Agentic Control Plane UI
```

### Application-layer merge

Four of the ten endpoints require data from both indices (process breakdown, summary KPIs at agent
scope, incident rate at agent scope, failure rate by process version at agent scope). Each issues
two lightweight parallel queries and merges the results in the Java service layer before returning
a single response. This is standard application-layer composition — no ES/OS cross-index join.

### Why a dedicated index

**Zero blast radius on existing analytics.** No mapping change, no version bump, no Painless
script modification on `ProcessInstanceIndex`. A deployment failure in the agent import pipeline
cannot affect existing process reports.

**No Painless scripting.** Each agent activation maps to one document. Writes are standard
index/update operations. No merge logic, no aggregate re-computation, no scripting risk.

**Flat document queries.** All agent-scoped aggregations run on flat fields using standard terms,
date histograms, and percentiles — no nested query overhead.

**Independent index tuning.** Shard count, replica count, and refresh interval for agent data can
be set independently of the process instance index.

### Architectural constraint

Because agent data and process data are in separate indices, any future widget that must
**filter agent runs by a process-level value computed at query time** — e.g. "agent runs where the
parent process was flagged as SLA-breached" — requires an application-level join. At scale, this
hits a hard Elasticsearch/OpenSearch limit of 65,000 values per terms query, making such widgets
impractical for large deployments.

This constraint has no impact on the phase 1 dashboard, where all metrics are independently
agent-scoped or process-scoped. It becomes a design blocker if the product roadmap introduces
process-level correlation widgets within 12–18 months.

---

## Key Architectural Risks

| Risk | Severity | Likelihood | Notes |
|---|---|---|---|
| Wrong import service base class | Medium | Low | Using the process-instance sub-entity base class silently writes to the wrong index — no error, just missing data. Caught immediately by import integration tests. |
| Cross-index merge edge cases | Low | Medium | Asymmetric data (agent activations with no matching incidents, or vice versa) must be handled gracefully. Covered by integration tests with intentionally asymmetric fixtures. |
| Future analytics constraint (65k join limit) | High | Medium | No impact on phase 1. Must be evaluated before committing to this architecture if process-agent correlation widgets enter the roadmap. |

---

## Related Documents

| Document | Purpose |
|---|---|
| `agentic-control-plane-technical-separated-index-spec.md` | Full technical specification |
| `agentic-control-plane-impl-plan-separated-index.md` | Implementation task breakdown with effort estimates |
| `agentic-control-plane-impl-plan-comparison.md` | Architectural tradeoff analysis vs nested variant |
| `agentic-control-plane-proposal-nested.md` | Alternative architecture proposal |
