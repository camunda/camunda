# Agentic Control Plane — Nested vs Separate Index Comparison

**Plans compared**:
- Nested: `agentic-control-plane-impl-plan-nested.md`
- Separate index: `agentic-control-plane-impl-plan-separated-index.md`

Estimates assume AI tooling (Claude Code) for implementation.

> **Note on source document**: This comparison incorporates a prior analysis document. Items marked
> ⚠️ *out of scope* apply to `agentHistoryElements` (conversation history) — **not in phase 1**.
> Items marked ⚠️ *not applicable* assume the Optimize report framework
> (`ExecutionPlanExtractor` / `AgentExecutionPlan`) — our spec uses dedicated API endpoints instead,
> so those risks do not apply.

---

## Business Dimensions

|                          Dimension                          |                                 Separate Index                                 |                                       Nested                                        | Weight |                                      Reasoning                                       |
|-------------------------------------------------------------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------|
| Future "filter by agent based on process condition" widgets | ❌ Blocked — runtime cross-index filter not feasible at scale (65k terms limit) | ✅ Unrestricted — process and agent context always co-located                        | High   | Directly impacts ability to ship natural analytics use cases in 12–18 months         |
| Query performance at scale                                  | ✅ Fast — flat documents, simple aggregations                                   | ⚠️ Higher cost at high agent invocation volume due to nested overhead               | High   | Query latency and cost at volume are hard to fix later                               |
| Long-term analytics flexibility                             | ⚠️ Structural join limitation constrains future widget design                  | ✅ Any process + agent correlation is a first-class query                            | High   | Determines how constrained the analytics surface will be over the product's lifetime |
| Phase 1 dashboard deliverability                            | ✅ All 10 endpoints deliverable via two-request merge                           | ✅ All 10 endpoints deliverable via nested aggregations                              | Medium | Current widget set is small; both options cover it                                   |
| Feature iteration speed                                     | ⚠️ Each new widget may need new denormalized fields and new merge paths        | ✅ New widgets inherit existing process filters and groupBy dimensions automatically | Medium | Affects delivery velocity but isn't as critical as scalability                       |
| Independent scaling                                         | ✅ Index settings tunable per record type                                       | ⚠️ Single index absorbs all write pressure                                          | Medium | Can be mitigated with cluster tuning                                                 |
| Risk to existing functionality                              | ✅ `ProcessInstanceIndex` untouched                                             | ⚠️ VERSION bump + new mapping touches all process analytics                         | High   | Blast radius of a mapping bug in nested variant affects all existing reports         |

---

## Effort by Phase

|           Phase           |    Nested    | Separate Index |
|---------------------------|--------------|----------------|
| Phase 0 — Foundation      | ~1 day       | ~1 day         |
| Phase 1 — Import Pipeline | ~1 week      | ~4 days        |
| Phase 2 — Backend API     | ~1.5 weeks   | ~1.5 weeks     |
| Phase 3 — Frontend        | ~1 week      | ~1 week        |
| **Total**                 | **~4 weeks** | **~3.5 weeks** |

---

## Task-Level Implementation Comparison

|              Area              |                                      Nested                                      |                           Separate Index                           |                   Verdict                   |
|--------------------------------|----------------------------------------------------------------------------------|--------------------------------------------------------------------|---------------------------------------------|
| Painless script                | ❌ Required; highest AI failure risk; subtle ES gotchas; needs real ES validation | ✅ Not needed                                                       | **Separate wins**                           |
| `ProcessInstanceIndex` changes | ⚠️ VERSION bump + new nested field + parent-level token fields                   | ✅ Untouched                                                        | **Separate wins**                           |
| Import service parent class    | `ZeebeProcessInstanceSubEntityImportService` (existing pattern)                  | `AbstractImportService` (wrong choice = silent wrong-index writes) | Risk shifts, doesn't disappear              |
| API query complexity           | Nested agg syntax throughout (verbose but AI-known)                              | Flat queries (simpler, fewer lines)                                | **Separate marginally faster** per endpoint |
| Cross-index merges             | Not needed                                                                       | 4 endpoints need two-request Java merge (A1, A3, A6 L2, A10 L2)    | **Nested simpler** for those 4              |
| A7 Agents List pagination      | Composite agg scroller (less common)                                             | `search_after` (standard — AI knows it well)                       | **Separate simpler**                        |
| A8 Token Outlier Bands         | Needs parent-level pre-aggregated fields                                         | Direct percentile on flat fields                                   | **Separate simpler**                        |
| Integration test surface       | Painless correctness + nested query edge cases                                   | No Painless; cross-index merge edge cases                          | **Separate narrower**                       |

---

## Risk Register

### Separate Index Risks

|                            Risk                             | Severity | Likelihood |                                                                                                                                                                                                             Notes                                                                                                                                                                                                              |
|-------------------------------------------------------------|----------|------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Runtime cross-index filter not feasible at scale**        | High     | Medium     | Any future widget filtering agent data by a process-level condition computed at query time (e.g. "agent runs where parent process took > 10 min") requires a two-step application join. ES/OS 65k terms limit makes this impractical at scale. Not needed for phase 1 — but a plausible product ask within 12–18 months. Mitigation: document constraint explicitly; evaluate denormalization per-widget when the need arises. |
| Import ordering dependency — missing `processDefinitionKey` | Medium   | Low        | If agent record arrives before parent PI is imported, denormalized fields are null. Mitigation: field is on `AgentInstanceRecord` directly — no parent lookup needed. Follow existing skeleton pattern for rare missing cases.                                                                                                                                                                                                 |
| Process-scoped filter replication                           | Low      | Medium     | Filters like `tenantId` and `processDefinitionKey` must be explicitly denormalized and applied in every new agent filter implementation. Missed filters silently return wrong results. Mitigation: enforce in code review; denormalize all expected filter fields at import time.                                                                                                                                              |
| ~~New execution plan path~~                                 | —        | —          | ⚠️ *Not applicable* — spec uses dedicated API endpoints, not `ExecutionPlanExtractor` / `AgentExecutionPlan` enum.                                                                                                                                                                                                                                                                                                             |

### Nested Risks

|                  Risk                   | Severity | Likelihood |                                                                                                                                                                                       Notes                                                                                                                                                                                        |
|-----------------------------------------|----------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Painless script correctness**         | Medium   | Medium     | Multiple scripts on the same document interact if they touch overlapping fields. Agent instance script must be strictly scoped to `ctx._source.agentInstances` only. Null list init, existing key replace, new key append, and token re-aggregation all need unit tests. With AI tooling this is the highest-risk task — AI generates plausible-looking but subtly wrong Painless. |
| Nested object limit                     | Medium   | Low        | ES/OS defaults to 10,000 nested objects per document across all nested fields (`flowNodeInstances`, `variables`, `incidents`, `agentInstances`). High-invocation processes can approach the limit. Breach causes silent data drop via `skipDataAfterNestedDocLimitReached`. Mitigation: raise `index.mapping.nested_objects.limit`; add monitoring; log warning on limit hit.      |
| Document version churn                  | Medium   | Low        | Frequent agent `UPDATED` events on the same PI document under concurrent import exhausts `retryNumberOnConflict = 5`, causing dropped updates silently. Mitigation: verify batch size is adequate; monitor version conflict rates.                                                                                                                                                 |
| `ProcessInstanceIndex` mapping mutation | Medium   | Low        | VERSION bump triggers mapping update across all process instance indices. Wrong `.nested()` vs `.object()` type on first write causes ES to auto-map incorrectly — hard to fix without reindex. Mitigation: assert mapping type in an integration test before merging.                                                                                                             |
| ~~Content field mapping leak~~          | —        | —          | ⚠️ *Out of scope* — specific to `agentHistoryElements.content` (phase 2).                                                                                                                                                                                                                                                                                                          |
| ~~History element version churn~~       | —        | —          | ⚠️ *Out of scope* — specific to `agentHistoryElements` (phase 2).                                                                                                                                                                                                                                                                                                                  |

---

## Recommendation

**Choose separate index for phase 1.**

Time difference is marginal (~0.5 weeks). Risk and long-term flexibility differences are material.

**For phase 1**: Separate index is lower risk (no Painless, no `ProcessInstanceIndex` mutation) and
simpler to implement with AI tooling. All 10 dashboard endpoints are fully deliverable.

**For the long term**: The nested approach has a genuine structural advantage — process and agent
data co-located in one document means any future correlation query is a first-class ES query with
no join overhead. The "filter agents by parent process condition" use case (e.g. "agents where the
parent process was flagged as SLA-breached") is undeliverable at scale with separate indexes. This
is a **real constraint**, not a theoretical one, and should drive a decision before committing.

**Decision trigger**: If the 12–18 month roadmap contains any widget that filters or groups agent
data based on a computed process-level condition, reconsider the architecture now. Migrating from
separate indexes to nested after data is in production is expensive. If the roadmap is purely
agent-scoped metrics (token usage, duration, incident rate), separate index is safe indefinitely.
