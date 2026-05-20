# Agentic Control Plane — Solution Proposal

## Nested-in-ProcessInstance Architecture

**Audience**: Product Owners, Architects
**Author**: Alexandre Janoni
**Technical spec**: `agentic-control-plane-technical-spec.md`

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

### Agent data lives inside the process instance document

Agent instance records emitted by Zeebe are imported into Optimize as **nested sub-documents
inside the existing `ProcessInstanceIndex`** — the same pattern already used for flow node
instances, incidents, and variables.

```
ProcessInstance document
├── processDefinitionKey, state, duration, ...
├── flowNodeInstances  (nested)
├── incidents          (nested)
├── variables          (nested)
└── agentInstances     (nested)  ← new
    ├── elementId, status, durationInMs
    ├── metrics: inputTokens, outputTokens, modelCalls, toolCalls
    └── definition: model, provider
```

Two pre-aggregated token total fields (`agentTotalInputTokens`, `agentTotalOutputTokens`) are
maintained at the document root to support efficient fleet-level token aggregations without
traversing nested documents on every query.

### Data flow

```
Zeebe engine
  └─► AGENT_INSTANCE records (CREATED / UPDATED / COMPLETED)
        └─► Optimize import pipeline  (Painless upsert-by-key into nested array)
              └─► ProcessInstanceIndex  (one document per process instance, agent data nested within)
                    └─► 10 dashboard API endpoints
                          └─► Agentic Control Plane UI
```

### Why co-location matters

Storing agent data inside the process instance document means process context and agent context are
always in the same index. This has two concrete consequences:

**1. Unrestricted future analytics correlation.**
Any widget that filters or groups agent data by a process-level condition — e.g. "agent runs where
the parent process exceeded its SLA", "token spend per process outcome bucket" — is a single
native query. No application-level join, no scale limit.

**2. Automatic filter inheritance.**
Tenant isolation, process definition scoping, date range, and any future process-level filter
dimension work for agent widgets without additional implementation. The filter framework is shared.

### Architectural constraints

- **`ProcessInstanceIndex` VERSION bump required.** The mapping change (version 8 → 9) must be
  deployed before the import pipeline is activated. An incorrect field type on first write cannot
  be corrected without a full reindex.
- **Painless merge script required.** Agent instance upsert-by-key into the nested array and
  re-aggregation of root-level token totals is implemented as an Elasticsearch/OpenSearch Painless
  script. This is the highest-risk implementation task: the script must be validated against a real
  ES/OS instance before deployment.
- **Nested aggregation overhead.** All agent-scoped queries traverse nested documents. At high
  agent invocation volume per process instance, this carries measurable query cost compared to
  flat document queries.
- **Nested object limit.** ES/OS defaults to 10,000 nested objects per document across all nested
  fields. Processes with very high agent invocation counts can approach this limit. The limit is
  configurable but a breach causes silent data drops.

---

## Key Architectural Risks

|                 Risk                  | Severity | Likelihood |                                                                              Notes                                                                              |
|---------------------------------------|----------|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Painless script correctness           | Medium   | Medium     | Subtle scripting errors (type coercion, null handling, array mutation) produce wrong data silently. Requires unit tests + real ES/OS validation before go-live. |
| ProcessInstanceIndex mapping mutation | Medium   | Low        | Wrong mapping type on first write requires full reindex. Validate mapping in integration test before enabling the pipeline.                                     |
| Nested object limit breach            | Medium   | Low        | Configure and monitor `index.mapping.nested_objects.limit` per expected agent invocation volume. Silent drop on breach.                                         |
| Query performance at scale            | Low      | Low        | Benchmark nested aggregation overhead under realistic agent invocation volume before GA.                                                                        |

---

## Related Documents

|                      Document                       |                          Purpose                          |
|-----------------------------------------------------|-----------------------------------------------------------|
| `agentic-control-plane-technical-spec.md`           | Full technical specification                              |
| `agentic-control-plane-impl-plan-nested.md`         | Implementation task breakdown with effort estimates       |
| `agentic-control-plane-impl-plan-comparison.md`     | Architectural tradeoff analysis vs separate index variant |
| `agentic-control-plane-proposal-separated-index.md` | Alternative architecture proposal                         |

