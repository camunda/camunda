# Agentic Control Plane — Implementation Plan (Nested Variant)

**Spec**: `agentic-control-plane-technical-spec.md`

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

|   #   |                                            Task                                             | Effort | AI Fit |                                                                             Notes                                                                             |
|-------|---------------------------------------------------------------------------------------------|--------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-0.1 | `AgentInstanceDto` + sub-DTOs (`AgentDefinitionDto`, `AgentMetricsDto`, `AgentToolDto`)     | XS     | ✅      | Give AI `IncidentDto` as pattern reference; generates in minutes                                                                                              |
| N-0.2 | Add `List<AgentInstanceDto> agentInstances` to `ProcessInstanceDto`                         | XS     | ✅      | One field addition; follows existing `incidents` field pattern                                                                                                |
| N-0.3 | `ProcessInstanceIndex` VERSION 8 → 9: `agentInstances` nested mapping + parent token fields | S      | ⚠️     | Give AI the existing `addProperties()` method; critical: `.nested()` vs `.object()` distinction must be correct or ES will auto-map wrong type on first write |

---

## Phase 1 — Import Pipeline

|   #   |                                      Task                                      | Effort | AI Fit |     Depends on      |                                                                                                                                                                                       Notes                                                                                                                                                                                       |
|-------|--------------------------------------------------------------------------------|--------|--------|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-1.1 | Painless merge + re-aggregation script (`createUpdateAgentInstancesScript()`)  | M      | ❌      | N-0.2, N-0.3        | **Highest risk task in this variant.** ES Painless has subtle gotchas: `!= null` not `.empty` in update context; `==` vs `.equals()` for String comparison; array mutation semantics. AI will generate plausible-looking but subtly wrong scripts. Must be validated against a real ES instance with all three intents (CREATED → UPDATED → COMPLETED) and verified token totals. |
| N-1.2 | Wire script into `createProcessInstanceUpdateScript()`                         | XS     | ✅      | N-1.1               | One-line append                                                                                                                                                                                                                                                                                                                                                                   |
| N-1.3 | `ZeebeAgentInstanceFetcher`                                                    | XS     | ✅      | N-0.1               | Give AI `ZeebeIncidentFetcher` as reference; structural copy                                                                                                                                                                                                                                                                                                                      |
| N-1.4 | `ZeebeAgentInstanceImportService`                                              | S      | ⚠️     | N-0.1, N-0.2, N-1.1 | Give AI `ZeebeIncidentImportService` + spec § 3.3 as reference; review intent-to-timestamp mapping logic (`creationDate` from CREATED, `completionDate` from COMPLETED)                                                                                                                                                                                                           |
| N-1.5 | `ZeebeAgentInstanceImportHandler`                                              | XS     | ✅      | N-1.3, N-1.4        | Structural copy of existing handler                                                                                                                                                                                                                                                                                                                                               |
| N-1.6 | `ZeebeAgentInstanceImportMediator` + `ZeebeAgentInstanceImportMediatorFactory` | XS     | ✅      | N-1.5               | Structural copy of existing mediator/factory pair                                                                                                                                                                                                                                                                                                                                 |
| N-1.7 | Spring registration                                                            | XS     | ✅      | N-1.6               | Find `ZeebeIncidentImportMediatorFactory` registration; add alongside                                                                                                                                                                                                                                                                                                             |
| N-1.8 | Import pipeline integration tests                                              | M      | ⚠️     | N-1.4               | AI generates test scaffold; requires real ES + Zeebe infra to run. Verify: metric accumulation across UPDATED events, token totals re-aggregated correctly, COMPLETED sets `completionDate` and `durationInMs`. **Do not skip — Painless bugs only surface here.**                                                                                                                |

---

## Phase 2 — Backend API

All endpoints independent after N-2.1. **Parallelize freely — run all as separate AI agents.**

|   #    |                          Task                           | Effort | AI Fit |   Depends on   |                                                                                      Notes                                                                                      |
|--------|---------------------------------------------------------|--------|--------|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-2.1  | Controller skeleton + `AgenticControlPlaneFilterParams` | XS     | ✅      | N-0.3          | Give AI an existing Optimize controller as reference; generates in minutes                                                                                                      |
| N-2.2  | A2 — Agent Dropdown                                     | XS     | ✅      | N-2.1          | Spec has exact query; composite scroller pattern already exists                                                                                                                 |
| N-2.3  | A1 — Process Breakdown                                  | S      | ✅      | N-2.1          | Spec has exact query; straightforward terms + nested agg                                                                                                                        |
| N-2.4  | A3 — Summary KPIs                                       | S      | ⚠️     | N-2.1          | WoW parallel query + `medianTokens` Painless percentile; review merge of parallel results                                                                                       |
| N-2.5  | A4 — Token Trend                                        | M      | ⚠️     | N-2.1          | Two-step (top-5 then 5 parallel histograms) + "Other" arithmetic; spec is clear but multi-step orchestration needs review                                                       |
| N-2.6  | A5 — Duration Stats                                     | S      | ✅      | N-2.1          | Spec has exact queries for both L0/L1 and L2; straightforward percentile aggs                                                                                                   |
| N-2.7  | A6 — Incident Rate                                      | S      | ✅      | N-2.1          | L2 two-nested-agg pattern is clearly specced; no reverse_nested                                                                                                                 |
| N-2.8  | A8 — Token Outlier Bands                                | S      | ✅      | N-2.1          | Direct Painless percentile on parent-level fields; spec has exact script                                                                                                        |
| N-2.9  | A9 — Avg Tokens per Agent Call                          | XS     | ✅      | N-2.1          | `terms` + `bucket_script`; spec has exact query                                                                                                                                 |
| N-2.10 | A10 — Failure Rate by Version                           | S      | ✅      | N-2.1          | Two nested aggs per version bucket; spec clear                                                                                                                                  |
| N-2.11 | A7 — Agents List                                        | M      | ⚠️     | N-2.1          | Composite scroller + incident join + merge by `processInstanceId`; pagination correctness must be verified; `successRate = 1 - incidentRate` must be summary-level not per-page |
| N-2.12 | API integration tests                                   | M      | ⚠️     | N-2.2 – N-2.11 | AI generates scaffold; requires real infra. Must cover L0/L1/L2 per endpoint, WoW delta, empty state, cursor correctness for A7. **Run before frontend starts.**                |

---

## Phase 3 — Frontend

All components independent after N-3.1. **Parallelize freely.**

|   #    |                         Task                         | Effort | AI Fit |   Depends on   |                                                                    Notes                                                                     |
|--------|------------------------------------------------------|--------|--------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| N-3.1  | `AgentFilterContext` + L0/L1/L2 level derivation     | S      | ⚠️     | —              | Give AI existing Optimize filter context as reference; L0/L1/L2 state machine must be correct — everything else branches on it               |
| N-3.2  | `ProcessSelector` + `AgentSelector`                  | XS     | ✅      | N-3.1          | Follow existing selector component patterns                                                                                                  |
| N-3.3  | `SummaryKPIs`                                        | XS     | ✅      | N-3.1          | KPI card component; WoW delta display; `durationScope` label swap                                                                            |
| N-3.4  | `TokenTrendChart`                                    | S      | ⚠️     | N-3.1          | Multi-line vs single-line switch on `elementId`; "Other" series construction; most complex frontend component — review series assembly logic |
| N-3.5  | `TokenOutlierBands`                                  | XS     | ✅      | N-3.1          | p5/p50/p95 area chart                                                                                                                        |
| N-3.6  | `AvgTokensPerAgentCall`                              | XS     | ✅      | N-3.1          | Bar chart; L1/L2 visibility guard                                                                                                            |
| N-3.7  | `DurationStats`                                      | XS     | ✅      | N-3.1          | Two KPI cards + trend line; label/tooltip swap                                                                                               |
| N-3.8  | `IncidentRateKPI`                                    | XS     | ✅      | N-3.1          |                                                                                                                                              |
| N-3.9  | `FailureRateByVersion`                               | XS     | ✅      | N-3.1          | Bar chart; L1/L2 visibility guard                                                                                                            |
| N-3.10 | `AgentsList`                                         | S      | ⚠️     | N-3.1          | `search_after` cursor pagination ("load more"); summary-level stats header; no per-row badge                                                 |
| N-3.11 | `ControlPlaneDashboard` — layout + visibility matrix | S      | ✅      | N-3.2 – N-3.10 | Wires context → conditional chart rendering per L0/L1/L2                                                                                     |
| N-3.12 | Frontend integration / E2E tests                     | L      | ⚠️     | N-3.11         | AI generates scaffold; requires running backend. Golden path per filter level; empty state; WoW delta; cursor pagination.                    |

---

## Summary (AI-adjusted)

|   Phase   |    Effort    |                             Critical path item                              |
|-----------|--------------|-----------------------------------------------------------------------------|
| Phase 0   | ~1 day       | N-0.3 (index mapping — `.nested()` vs `.object()` correctness)              |
| Phase 1   | ~1 week      | N-1.1 (Painless script — verify against real ES), N-1.8 (integration tests) |
| Phase 2   | ~1.5 weeks   | N-2.5 (Token Trend orchestration), N-2.11 (Agents List pagination)          |
| Phase 3   | ~1 week      | N-3.4 (Token Trend chart), N-3.12 (E2E tests)                               |
| **Total** | **~4 weeks** |                                                                             |

**Critical path**: N-0.3 → N-1.1 *(human validation required)* → N-1.8 *(infra)* → N-2.1 → [parallel endpoints] → N-2.12 *(infra)* → N-3.1 → [parallel components] → N-3.12

**Highest-risk tasks requiring human ownership** (AI output insufficient alone):
1. **N-1.1** — Painless script correctness; test on real ES before wiring
2. **N-1.8** — Integration tests; only place Painless bugs surface
3. **N-2.11** — Agents List pagination + merge; pagination bugs are subtle
4. **N-2.12** — API integration tests; cross-level filter coverage
