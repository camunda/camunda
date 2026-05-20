# Agentic Control Plane — Implementation Plan (Separate Index Variant)

**Spec**: `agentic-control-plane-technical-separated-index-spec.md`

## T-shirt sizes (AI-adjusted)

Estimates assume AI tooling (Claude Code) handles implementation. Human time = guidance, review,
debugging, and running tests against real infrastructure.

| Size | Human effort with AI |
|------|----------------------|
| XS   | < 2 hours            |
| S    | half–1 day           |
| M    | 1–2 days             |
| L    | 3–5 days             |

## AI Fit ratings

|  Rating   |                                                     Meaning                                                     |
|-----------|-----------------------------------------------------------------------------------------------------------------|
| ✅ High    | Spec is complete; AI follows existing pattern; generate + light review                                          |
| ⚠️ Medium | Spec is clear but logic is novel; generate + careful review                                                     |
| ❌ Low     | ES/Painless subtleties, integration infrastructure, or correctness-critical merges; AI helps but human must own |

## Parallelization

Phase 2 endpoints and Phase 3 components are independent — run as many parallel AI agents as
desired after foundation is stable. Do NOT parallelize across phases.

---

## Phase 0 — Foundation

Sequential. Everything else depends on these.

|   #   |                                          Task                                           | Effort | AI Fit |                                                                                                Notes                                                                                                |
|-------|-----------------------------------------------------------------------------------------|--------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| S-0.1 | `AgentInstanceDto` + sub-DTOs (`AgentDefinitionDto`, `AgentMetricsDto`, `AgentToolDto`) | XS     | ✅      | Give AI `IncidentDto` as pattern reference; generates in minutes                                                                                                                                    |
| S-0.2 | `AgentInstanceIndex` — flat mapping, field constants, per-process index name builder    | S      | ✅      | Give AI `ProcessInstanceIndex` as pattern reference; all fields are flat (`.keyword()`, `.long_()`, `.integer()`); only `definition`, `metrics`, `tools` need `.object()` — no `.nested()` anywhere |

> `ProcessInstanceIndex` unchanged. No VERSION bump.

---

## Phase 1 — Import Pipeline

No Painless script. This phase is the clearest win of this variant over nested.

|   #   |                                      Task                                      | Effort | AI Fit |     Depends on      |                                                                                                                                       Notes                                                                                                                                        |
|-------|--------------------------------------------------------------------------------|--------|--------|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| S-1.1 | `AgentInstanceWriter` — index (CREATED) + partial update (UPDATED/COMPLETED)   | S      | ✅      | S-0.1, S-0.2        | Standard ES client: `IndexRequest` for CREATED; `UpdateRequest` with partial `doc` map for UPDATED/COMPLETED. No Painless. Give AI an existing ES writer as reference.                                                                                                             |
| S-1.2 | `ZeebeAgentInstanceFetcher`                                                    | XS     | ✅      | S-0.1               | Structural copy of `ZeebeIncidentFetcher`                                                                                                                                                                                                                                          |
| S-1.3 | `ZeebeAgentInstanceImportService`                                              | S      | ⚠️     | S-0.1, S-0.2, S-1.1 | **Not** `ZeebeProcessInstanceSubEntityImportService` — extends `AbstractImportService` directly; give AI both classes so it doesn't copy the wrong parent. Review intent-to-timestamp mapping (`creationDate` from CREATED, `completionDate` from COMPLETED).                      |
| S-1.4 | `ZeebeAgentInstanceImportHandler`                                              | XS     | ✅      | S-1.2, S-1.3        | Structural copy of existing handler                                                                                                                                                                                                                                                |
| S-1.5 | `ZeebeAgentInstanceImportMediator` + `ZeebeAgentInstanceImportMediatorFactory` | XS     | ✅      | S-1.4               | Structural copy of existing mediator/factory pair                                                                                                                                                                                                                                  |
| S-1.6 | Spring registration of factory + `AgentInstanceIndex` in index registry        | XS     | ✅      | S-1.5               | Two additions alongside existing Zeebe import registrations                                                                                                                                                                                                                        |
| S-1.7 | Import pipeline integration tests                                              | S      | ⚠️     | S-1.3               | AI generates scaffold; requires real ES. Verify: document created on CREATED, fields updated on UPDATED/COMPLETED, `durationInMs` set only on COMPLETED, metric totals are running totals not deltas. Simpler to verify than nested variant (no Painless, doc directly queryable). |

---

## Phase 2 — Backend API

All endpoints independent after S-2.0. **Parallelize freely — run all as separate AI agents.**

Cross-index endpoints (A1, A3, A6 L2, A10 L2) follow the same two-request + Java merge pattern.
Give AI the merge utility (S-2.0) before generating those endpoints.

|   #    |                                             Task                                             | Effort | AI Fit |   Depends on   |                                                                                                                                    Notes                                                                                                                                    |
|--------|----------------------------------------------------------------------------------------------|--------|--------|----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| S-2.0  | Controller skeleton + `AgenticControlPlaneFilterParams` + `CrossIndexMergeUtil.mergeByKey()` | S      | ✅      | S-0.2          | Give AI existing controller + the spec's cross-index join pattern description; generates skeleton + reusable merge utility                                                                                                                                                  |
| S-2.1  | A2 — Agent Dropdown                                                                          | XS     | ✅      | S-2.0          | Simple `composite` agg on flat `elementId`; no nested                                                                                                                                                                                                                       |
| S-2.2  | A3 — Summary KPIs                                                                            | S      | ⚠️     | S-2.0          | Two parallel ES requests (AgentInstanceIndex + ProcessInstanceIndex) + WoW delta + merge; more requests than nested but each query is simpler; review merge correctness                                                                                                     |
| S-2.3  | A4 — Token Trend                                                                             | M      | ⚠️     | S-2.0          | Two-step (top-5 then 5 parallel histograms) + "Other" arithmetic; spec is clear but multi-step orchestration needs review                                                                                                                                                   |
| S-2.4  | A5 — Duration Stats                                                                          | S      | ✅      | S-2.0          | L0/L1 uses ProcessInstanceIndex unchanged; L2 uses flat `durationInMs` — simpler than nested                                                                                                                                                                                |
| S-2.5  | A6 — Incident Rate                                                                           | S      | ✅      | S-2.0          | L2: two requests (activation count from AgentInstanceIndex + incident count from ProcessInstanceIndex); spec has exact queries; merge is trivial                                                                                                                            |
| S-2.6  | A8 — Token Outlier Bands                                                                     | XS     | ✅      | S-2.0          | Direct percentile on flat `metrics.inputTokens + metrics.outputTokens`; no parent-level fields; no scripted_metric                                                                                                                                                          |
| S-2.7  | A9 — Avg Tokens per Agent Call                                                               | XS     | ✅      | S-2.0          | `terms` + `bucket_script`; flat fields; spec has exact query                                                                                                                                                                                                                |
| S-2.8  | A10 — Failure Rate by Version                                                                | S      | ✅      | S-2.0          | L2: two requests (AgentInstanceIndex per version + ProcessInstanceIndex per version); merge by `processDefinitionVersion`                                                                                                                                                   |
| S-2.9  | A7 — Agents List                                                                             | S      | ⚠️     | S-2.0          | `search_after` on `creationDate` + `agentInstanceKey` tiebreaker; incident join; cursor pagination correctness must be verified; simpler than composite scroller                                                                                                            |
| S-2.10 | A1 — Process Breakdown                                                                       | S      | ✅      | S-2.0          | Two requests (AgentInstanceIndex tokens + ProcessInstanceIndex run counts/duration/incidents); merge by `processDefinitionKey`                                                                                                                                              |
| S-2.11 | API integration tests                                                                        | M      | ⚠️     | S-2.1 – S-2.10 | AI generates scaffold; requires real infra. Cover L0/L1/L2, WoW delta, empty state, cross-index merge correctness. **Include a test where agent data exists in AgentInstanceIndex but corresponding PI has no incidents — verifies merge handles missing right-side keys.** |

---

## Phase 3 — Frontend

Identical to nested variant. All components independent after S-3.1. **Parallelize freely.**

|   #    |                       Task                       | Effort | AI Fit |   Depends on   |                                           Notes                                           |
|--------|--------------------------------------------------|--------|--------|----------------|-------------------------------------------------------------------------------------------|
| S-3.1  | `AgentFilterContext` + L0/L1/L2 level derivation | S      | ⚠️     | —              | L0/L1/L2 state machine must be correct — everything else branches on it; review carefully |
| S-3.2  | `ProcessSelector` + `AgentSelector`              | XS     | ✅      | S-3.1          |                                                                                           |
| S-3.3  | `SummaryKPIs`                                    | XS     | ✅      | S-3.1          | KPI cards; WoW delta; `durationScope` label                                               |
| S-3.4  | `TokenTrendChart`                                | S      | ⚠️     | S-3.1          | Multi-line vs single-line switch; "Other" series construction; review series assembly     |
| S-3.5  | `TokenOutlierBands`                              | XS     | ✅      | S-3.1          | p5/p50/p95 area chart                                                                     |
| S-3.6  | `AvgTokensPerAgentCall`                          | XS     | ✅      | S-3.1          | Bar chart; L1/L2 visibility guard                                                         |
| S-3.7  | `DurationStats`                                  | XS     | ✅      | S-3.1          | Two KPI cards + trend line; label/tooltip swap                                            |
| S-3.8  | `IncidentRateKPI`                                | XS     | ✅      | S-3.1          |                                                                                           |
| S-3.9  | `FailureRateByVersion`                           | XS     | ✅      | S-3.1          | Bar chart; L1/L2 visibility guard                                                         |
| S-3.10 | `AgentsList`                                     | S      | ⚠️     | S-3.1          | `search_after` cursor pagination; summary-level stats header                              |
| S-3.11 | `ControlPlaneDashboard`                          | S      | ✅      | S-3.2 – S-3.10 | Conditional rendering per filter level                                                    |
| S-3.12 | Frontend integration / E2E tests                 | L      | ⚠️     | S-3.11         | Requires running backend; golden path per filter level; cursor pagination; empty state    |

---

## Summary (AI-adjusted)

|   Phase   |     Effort     |                          Critical path item                           |
|-----------|----------------|-----------------------------------------------------------------------|
| Phase 0   | ~1 day         | S-0.2 (AgentInstanceIndex mapping)                                    |
| Phase 1   | ~4 days        | S-1.3 (import service parent class choice), S-1.7 (integration tests) |
| Phase 2   | ~1.5 weeks     | S-2.3 (Token Trend orchestration), S-2.11 (integration tests)         |
| Phase 3   | ~1 week        | S-3.4 (Token Trend chart), S-3.12 (E2E tests)                         |
| **Total** | **~3.5 weeks** |                                                                       |

**Critical path**: S-0.2 → S-1.3 → S-1.6 → S-1.7 *(infra)* → S-2.0 → [parallel endpoints] → S-2.11 *(infra)* → S-3.1 → [parallel components] → S-3.12

**Highest-risk tasks requiring human ownership** (AI output insufficient alone):
1. **S-1.3** — Wrong parent class choice (`AbstractImportService` vs `ZeebeProcessInstanceSubEntityImportService`) silently writes to wrong index
2. **S-1.7** — Integration tests; verify all three intents handled correctly
3. **S-2.3** — Token Trend multi-step orchestration; "Other" arithmetic correctness
4. **S-2.11** — Cross-index merge edge cases (missing right-side keys)
5. **S-3.1** — Filter context state machine; wrong L0/L1/L2 derivation breaks all charts

