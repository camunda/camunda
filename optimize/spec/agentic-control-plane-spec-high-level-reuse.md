# Agentic Control Plane — High-Level Specification (Hybrid Variant: Custom UI + Infrastructure Reuse)

**Module**: `optimize/`
**Phase 1 scope**: L0 (fleet view) + L1 (single process view)
**Companion to**:
- `agentic-control-plane-spec-high-level.md` — bespoke dedicated implementation
- `agentic-control-plane-spec-high-level-full-reuse.md` — zero-endpoints, zero-UI variant
- `use-existing-endpoints.md` — trade-off analysis

> **What this variant aims for: the best of both worlds**
>
> - **Keeps the dedicated REST endpoints and custom dashboard UI** from
>   Variant 1 — pixel-perfect designs, period delta badges, L0/L1 toggle,
>   single-query multi-metric `/summary`, fast page loads.
> - **Uses the new shared report infrastructure** from Variant 3 — view
>   interpreters, groupBys, filters, denormalized fields — as the
>   **building blocks** the custom endpoints assemble.
> - **Pre-seeds a companion saved-report dashboard** alongside the custom
>   UI — gives operators saved-report-backed **alerts for free** without
>   compromising the main dashboard UX.
>
> Result: custom UX where it matters (operator-facing dashboard), shared
> infrastructure where it costs nothing extra, alerts as a bonus capability,
> single coherent codebase. No duplicate filter/view/groupBy logic in
> `AgenticControlPlaneRepository`.

---

## 1. Feature Overview

(Identical to `agentic-control-plane-spec-high-level.md` §1. The dashboard,
levels L0/L1/L2, and Phase 1 boundaries are unchanged.)

In addition to the bespoke dashboard, operators see an **agentic dashboard
template** in the standard Optimize Dashboards list — a secondary view
composed of saved reports. This is **not the primary entry point** for
agentic monitoring, but it enables:

- Alerting on agentic KPIs via the existing `AlertJob` mechanism
- Operator-owned customization (clone, modify, share saved reports)
- Power-user analytical drill-down outside the curated dashboard layout

---

## 2. Data Flow

```
Zeebe engine
  AgentInstanceRecord (CREATED) ──────────────────────────────────────────────┐
  AgentInstanceRecord (COMPLETED) ────────────────────────────────────────────│
                                                                              │
                                       Import Pipeline (Layer 1)              │
                                       ─────────────────────────────────────  │
                                              │ upserts into                  │
                                              ▼                               │
                                   ProcessInstanceIndex                       │
                                   (nested agentInstances +                   │
                                    denormalized parent totals                │
                                    including agentTotalTokens)               │
                                              │                               │
                              ┌───────────────┴───────────────┐               │
                              ▼                               ▼               │
                       Layer 2A —                       Layer 2B —            │
                       Dedicated REST endpoints         Saved reports         │
                       /api/agentic-control-plane/*     in template_agentic   │
                              │                               │               │
                              │ composes via                  │ uses          │
                              ▼                               ▼               │
                       AgenticControlPlaneService     ReportEvaluationHandler │
                              │                               │               │
                              │   both use the SAME           │               │
                              │   shared building blocks      │               │
                              ▼                               ▼               │
                       ┌─────────────────────────────────────────────┐        │
                       │ Shared infrastructure (NEW)                  │       │
                       │  • HasAgentInstancesQueryFilter              │       │
                       │  • AGENT_INSTANCE view entity                │       │
                       │  • INPUT/OUTPUT/MODEL/TOOL/TOTAL_TOKENS      │       │
                       │  • PROCESS_DEFINITION_KEY/VERSION groupBy    │       │
                       │  • AgentAvgTokensPerCall custom interpreter  │       │
                       └─────────────────────────────────────────────┘        │
                                              │                               │
                                              ▼                               │
                                       ProcessQueryFilterEnhancer             │
                                       ProcessViewInterpreterFacade           │
                                       ProcessGroupByInterpreterFacade        │
                                              │                               │
                              ┌───────────────┴───────────────┐               │
                              ▼                               ▼               │
                       Frontend (Layer 3A)              Frontend (Layer 3B)   │
                       Custom dashboard page            Standard Dashboards   │
                       /agentic-control-plane           UI (reused)           │
                                              │                               │
                                              ▼                               │
                                       Operator's browser                     │
```

Key principle: **two consumers, one infrastructure**. The dedicated
endpoints and the saved reports both build ES queries through the same new
view/groupBy/filter primitives. No duplication.

---

## 3. Layer 1 — Import Pipeline

**Identical to `agentic-control-plane-spec-high-level-full-reuse.md` §3.**

Specifically: same as the original spec §3, **plus** the `agentTotalTokens`
parent-level denormalized field (sum of `metrics.inputTokens +
metrics.outputTokens` across all agent instances on the parent process
instance). Required for token percentile aggregations on a single field.

| Field | Type | Description |
|---|---|---|
| `agentTotalTokens` | `long` | Sum of input + output tokens across all agent instances on this process instance. Re-computed on each import. |

---

## 4. Layer 2A — Dedicated Endpoints (Custom UI Backend)

### 4.1 Endpoint surface

**Identical to the original spec §4.** Four endpoints under
`/api/agentic-control-plane/`:

- `GET /summary`
- `GET /process-breakdown`
- `GET /trends`
- `GET /charts`

Same query parameters, same response shapes, same auth model. The
frontend contract is exactly the original spec. **Pixel-perfect PNG match
and period delta badges remain deliverable.**

### 4.2 Internal architecture — built on shared primitives

Unlike Variant 1, the dedicated endpoints do **not** maintain a
self-contained `AgenticControlPlaneRepositoryES/OS` that hand-builds ES
bool queries. Instead, the service layer **composes queries through the
new shared building blocks** from Layer 2C:

```
AgenticControlPlaneRestService
  └─ AgenticControlPlaneService
       ├─ AgenticReportDataBuilder
       │     └─ assembles ProcessReportDataDto with:
       │          • new view (e.g. AGENT_INSTANCE / TOTAL_TOKENS)
       │          • new groupBy (e.g. PROCESS_DEFINITION_KEY)
       │          • filter set: completed + dateRange + hasAgentInstances + tenant
       │
       ├─ ReportEvaluationHandler  ← reused for single-metric queries
       │     └─ shared view/groupBy interpreters, shared filter chain
       │
       ├─ AgenticMultiMetricExecutor  ← runs parallel evaluations for /summary
       │     └─ N parallel ReportEvaluationHandler calls, joins results
       │
       ├─ AgenticPeriodComparisonExecutor  ← runs current + prior in parallel
       │     └─ wraps either ReportEvaluationHandler or custom query path
       │
       └─ AgenticCustomQueryExecutor  ← bypass for nested-only queries
             └─ direct ES query for: incident nested-exists in /summary,
                Phase 2 tool nested aggregation, multi-metric /charts
                response composition
```

**Each endpoint chooses its execution path** based on what fits best:
fast single-query custom aggregation for `/summary` (matches original
spec's 2-query design with parallel period comparison), report-evaluation
composition for `/process-breakdown` and `/trends`, mixed strategy for
`/charts`.

### 4.3 Per-endpoint execution strategy

#### `GET /summary`

**Single custom ES query**, identical to the original spec implementation.
This is the latency-critical endpoint — operators load the dashboard and
this drives the KPI cards above the fold.

Two parallel ES queries (current + prior period for delta computation),
each containing 7 aggregations sharing one doc-iteration pass:
- valueCount (totalRuns)
- avg (avgDurationMs)
- percentiles 50/95 (p50/p95 durationMs)
- sum on `agentTotalInputTokens` (totalInputTokens for avgTokensPerRun)
- sum on `agentTotalOutputTokens` (totalOutputTokens for avgTokensPerRun)
- percentiles 50 on `agentTotalTokens` (medianTokensPerRun)
- filter→nested(incidents) exists (incidentCount)

Filters built via the shared `ProcessQueryFilterEnhancer` chain so tenant
resolution, date semantics, and authorization are inherited from the
standard report infrastructure. **No bespoke `AgentBaselineFilterBuilder`
duplicate** — the same filter classes are used here and in saved reports.

`AgenticPeriodComparisonExecutor` runs current + prior windows in parallel
via `CompletableFuture`. Service layer subtracts to produce delta fields.

#### `GET /process-breakdown`

Single `ReportEvaluationHandler` call:
- view: `AGENT_INSTANCE / TOTAL_TOKENS`
- groupBy: `PROCESS_DEFINITION_KEY` (new)
- aggregation: `SUM`
- sort: descending
- filters: shared baseline set

Service layer translates the report result into the `processes[]` shape.
Separate measurement of `processInstanceCount` per bucket comes from a
second parallel report evaluation with the same groupBy + `FREQUENCY`
view.

#### `GET /trends`

Up to three parallel `ReportEvaluationHandler` calls:
- Report A: date histogram + `AGENT_INSTANCE / TOTAL_TOKENS` + `SUM` +
  `Set<AggregationDto>` with `PERCENTILE(5)`, `PERCENTILE(50)`,
  `PERCENTILE(95)` → token sums and per-run token percentiles per bucket
- Report B: date histogram + `PROCESS_INSTANCE / DURATION` +
  `{PERCENTILE(50), PERCENTILE(95)}` → duration percentiles per bucket
- Report C (optional): date histogram + `AGENT_INSTANCE / INPUT_TOKENS` +
  `OUTPUT_TOKENS` views via two reports + Combined-Report-style merge in
  the service layer, **only if** the frontend needs the input/output split
  rather than a single total line. Otherwise omit Report C and derive
  the total from Report A.

Service layer joins the bucket arrays by `date` key and emits the
original spec's `trend[]` response shape.

#### `GET /charts`

Mixed strategy, conditional on L0 vs L1:

| Section | Execution path |
|---|---|
| `toolFrequency` Phase 1 (synthetic "all_tools") | Single report evaluation: `AGENT_INSTANCE / TOOL_CALLS` + `SUM` + `GROUP_BY_NONE` → wrap result as `[{toolName: "all_tools", totalToolCalls: N}]` in service layer |
| `toolFrequency` Phase 2 (per-tool breakdown) | Custom ES nested-terms query — out of scope for Phase 1 |
| `avgTokensPerCall` per process | Custom interpreter `ProcessViewAgentAvgTokensPerCallInterpreter` (ratio-of-sums) + groupBy `PROCESS_DEFINITION_KEY` — single report evaluation |
| `incidentRateByVersion` (L1 only) | Standard `PROCESS_INSTANCE / PERCENTAGE` + incident filter + groupBy `PROCESS_DEFINITION_VERSION` — single report evaluation |

Service layer composes the three sections into the `/charts` response
shape, with L1 conditionally including `incidentRateByVersion`.

### 4.4 Metric computability — verified

Every Phase 1 metric is traceable to a concrete execution path:

| Metric | Path | Evidence |
|---|---|---|
| `totalRuns` | `/summary` valueCount agg | original implementation pattern |
| `totalRunsDelta` | period-comparison executor subtracts current − prior | `AgenticPeriodComparisonExecutor` |
| `avgDurationMs`, `p50/p95 Duration` | `/summary` avg + percentiles 50/95 in one query | multi-agg ES feature |
| `avgDurationMsDelta` | period-comparison executor | same |
| `incidentRate`, `incidentRateDelta`, `incidentCount` | `/summary` nested-exists filter agg | original implementation pattern |
| `activationCount` | identical to totalRuns at L0/L1 | spec definition |
| `avgTokensPerRun` | `(totalInput + totalOutput) / totalRuns`, computed in service layer | three /summary aggs combined |
| `medianTokensPerRun` | percentile(50) on `agentTotalTokens` | denormalized field + percentile agg |
| `/process-breakdown` metrics | groupBy DEFINITION_KEY + TOTAL_TOKENS SUM + FREQUENCY parallel reports | shared infrastructure |
| `/trends` token metrics | date histogram + TOTAL_TOKENS + percentile multi-agg | shared infrastructure |
| `/trends` duration percentiles | date histogram + DURATION + percentile multi-agg | existing infrastructure |
| `toolFrequency` Phase 1 | TOOL_CALLS SUM + service-layer wrap | shared infrastructure |
| `avgTokensPerCall` per process | custom interpreter (ratio of sums) + groupBy KEY | follows existing percentage interpreter pattern |
| `incidentRateByVersion` rate | INSTANCE_PERCENTAGE view + incident filter + groupBy VERSION | existing percentage interpreter does the division |
| `incidentRateByVersion[].runs` | INSTANCE_FREQUENCY + groupBy VERSION | shared infrastructure |

**No "not deliverable" entries.** Period deltas are computed in the
service layer via the period-comparison executor — they are part of the
custom endpoint contract, unlike Variant 3 where the report-only path
cannot express them.

---

## 4C. Layer 2C — Shared Infrastructure Additions

Same as `agentic-control-plane-spec-high-level-full-reuse.md` §4.1 — the
new building blocks live in shared `optimize-commons` and shared
filter/interpreter chains. Both the custom endpoints (Layer 2A) and the
saved reports (Layer 2B) use them.

| Category | Items |
|---|---|
| **Filter** | `HasAgentInstancesFilterDataDto` + ES/OS query filter + Jackson subtype on `ProcessFilterDto` + enhancer registration |
| **View entity** | `ProcessViewEntity.AGENT_INSTANCE` |
| **View properties** | `INPUT_TOKENS`, `OUTPUT_TOKENS`, `MODEL_CALLS`, `TOOL_CALLS`, `TOTAL_TOKENS` |
| **`ProcessView` enum entries** | One per (AGENT_INSTANCE, viewProperty) pair (~5 entries) |
| **View interpreters (ES + OS)** | `ProcessViewAgent{Input,Output,Total}Tokens / ModelCalls / ToolCalls InterpreterES/OS` extending `AbstractProcessViewMultiAggregationInterpreterES/OS` |
| **Custom view interpreter** | `ProcessViewAgentAvgTokensPerCallInterpreterES/OS` — ratio of sums, follows `ProcessViewInstancePercentageInterpreterES` pattern (~3 days) |
| **GroupBy** | `PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY` + `PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION` + DTOs + ES/OS interpreters + facade registration |
| **GroupBy interpreters (ES + OS)** | Terms agg on `processDefinitionKey` and `processDefinitionVersion` parent fields |

---

## 4B. Layer 2B — Saved Report Companion (Alert Enablement)

A `template_agentic.json` is loaded by the existing
`InstantPreviewDashboardService` at startup. Contains saved reports for
each agentic KPI (using the same view/groupBy/filter primitives as Layer
2A) plus a dashboard arranging them in the standard Optimize grid.

**Purpose**:
1. **Alerts** — operators can configure threshold alerts on any saved
   report via existing Optimize UI. `AlertJob` evaluates the report,
   compares to threshold, sends notifications. Zero new alert code.
2. **Customization** — operators can clone reports, change date ranges,
   add filters, share with colleagues. Power-user analytical workflows.
3. **Discoverability** — the agentic dashboard appears in the standard
   Dashboards list, reachable from anywhere in Optimize.

**Not the primary entry point.** The custom UI (Layer 3A) remains the
operator-facing dashboard. The saved reports are a complementary,
secondary surface for alerting and customization.

Tiles included in `template_agentic.json`:

| Tile                             | View / GroupBy / Aggs | Alertable? |
|----------------------------------|---|---|
| Total Agentic Runs               | `PROCESS_INSTANCE/FREQUENCY` + filter set | ✅ |
| Avg Duration                     | `PROCESS_INSTANCE/DURATION` + AVERAGE | ✅ |
| Duration P50 / P95               | `PROCESS_INSTANCE/DURATION` + PERCENTILE(50,95) | ✅ |
| Incident Rate                    | `PROCESS_INSTANCE/PERCENTAGE` + incident filter | ✅ |
| Incident Count                   | `PROCESS_INSTANCE/FREQUENCY` + incident filter | ✅ |
| Avg Tokens Per Run               | `AGENT_INSTANCE/TOTAL_TOKENS` + AVERAGE | ✅ |
| Median Tokens Per Run            | `AGENT_INSTANCE/TOTAL_TOKENS` + PERCENTILE(50) | ✅ |
| Total Tool Calls                 | `AGENT_INSTANCE/TOOL_CALLS` + SUM | ✅ |
| Token Trend                      | Combined: input + output sums over date histogram | ✅ (each line alertable) |
| Top TokeAln Consumers by Process | groupBy KEY + TOTAL_TOKENS SUM | ✅ |
| Avg Tokens Per Call by Process   | groupBy KEY + custom AvgTokensPerCall interpreter | ✅ |
| Incident Rate by Version         | groupBy VERSION + PERCENTAGE + incident filter | ✅ |

Period delta badges are **not** in the saved-report tiles (limitation of
the report widget system). Operators who want delta visualization use
the custom dashboard.

---

## 5. Layer 3 — Frontend

### 5.1 Layer 3A — Custom dashboard page (primary)

**Identical to the original spec §5.** Bespoke React components, custom
KPI cards with delta badges, L0/L1 toggle, pixel-perfect match to the
PNG designs. Located at `/agentic-control-plane` in the Optimize SPA.

Calls the four custom endpoints from Layer 2A. Single page-load triggers
parallel requests; share the same response across components where
applicable (`/summary` powers KPIs, TokenStats, and DurationStats
scalars; `/trends` powers TokenTrend, TokenOutlierBands, and
DurationStability — same pattern as original spec).

### 5.2 Layer 3B — Saved-report dashboard (secondary)

**No new components.** Uses the existing `Dashboards` page +
`DashboardRenderer` + `DashboardTile` from
`optimize/client/src/components/Dashboards/`. Reached via:
- Dashboards list in the left-nav
- Direct URL `/dashboard/{templateChecksumId}`

Optional: pin the saved-report dashboard to the left-nav next to the
custom dashboard entry, so operators can switch between them.

### 5.3 Process selector

Same as original spec §5.5. Extends `GET /api/process-definition` with
`hasAgentRuns=true` query parameter — single change to an existing
endpoint, used by both Layer 3A (custom selector component) and Layer
3B (standard dashboard filter bar).

---

## 6. Cross-cutting Concerns

| Concern | Handling |
|---|---|
| Multi-tenancy | Custom endpoints: shared `ProcessQueryFilterEnhancer` resolves tenants from session. Saved reports: inherited from `ReportEvaluationHandler`. Single code path. |
| Authorization | Same — both paths flow through standard Optimize auth |
| Date range semantics | `InstanceStartDateFilterDto` applied via shared filter chain |
| Period comparison | Custom endpoints: `AgenticPeriodComparisonExecutor` runs current + prior in parallel. Saved reports: not supported (operators change date range manually). |
| Recency gap | Same as original — only COMPLETED instances are indexed |
| Alert support | ✅ Available for every saved report tile |
| Phase 2 extensibility | New tile = new view/groupBy interpreter + saved report config. Custom endpoints add a section. Both reuse the same primitives. |

---

## 7. Task Breakdown

### Layer 1 — Import Pipeline

| Task | Size | Days |
|---|---|---|
| Bump `ProcessInstanceIndex` version, add nested + parent fields including `agentTotalTokens` | S | 3 |
| Import service for `AgentInstanceRecord` (CREATED + COMPLETED) | M | 7 |
| Painless update script: nested upsert + parent aggregation including agentTotalTokens | M | 5 |
| Validation against live ES/OS instance | S | 3 |

**Layer 1 subtotal: ~18 days**

### Layer 2C — Shared infrastructure (same as full-reuse §4.1)

| Task | Size | Days |
|---|---|---|
| `HasAgentInstancesFilter` (DTO + ES + OS + Jackson + enhancer registration) | S | 3 |
| `ProcessViewEntity.AGENT_INSTANCE` + `ViewProperty` constants | XS | 1 |
| `ProcessView` enum entries | XS | 1 |
| Token view interpreters ES + OS (5 pairs ≈ 10 classes) | M | 6 |
| Register view interpreters in facades | XS | 1 |
| `PROCESS_DEFINITION_KEY` groupBy + DTO + interpreters + registration | S | 3 |
| `PROCESS_DEFINITION_VERSION` groupBy + DTO + interpreters + registration | S | 3 |
| `ProcessViewAgentAvgTokensPerCallInterpreterES/OS` (ratio of sums, follows percentage pattern) | S | 3 |
| Extend `GET /api/process-definition` with `hasAgentRuns=true` | XS | 1 |
| Integration tests for shared infra (ES + OS) | M | 8 |
| Regression test sweep across existing reports | M | 5 |
| Performance regression check on existing reports | S | 3 |

**Layer 2C subtotal: ~38 days**

### Layer 2A — Dedicated endpoints (composition layer)

| Task | Size | Days |
|---|---|---|
| `AgentQueryParams` DTO + period sliding (`forPreviousPeriodWindow`) | XS | 1 |
| `AgenticReportDataBuilder` — assembles `ProcessReportDataDto` from query params | S | 3 |
| `AgenticPeriodComparisonExecutor` — parallel current + prior evaluations | S | 2 |
| `AgenticMultiMetricExecutor` — parallel report evaluations for `/summary` composition | S | 3 |
| `AgenticCustomQueryExecutor` — single-query `/summary` ES path for latency | S | 3 |
| `AgenticControlPlaneService` + `Impl` — orchestrates the above per endpoint | M | 5 |
| `AgenticControlPlaneRestService` + 4 endpoints + DTOs | S | 3 |
| Response DTOs (`SummaryResponse`, `ProcessBreakdownResponse`, `TrendsResponse`, `ChartsResponse`) | XS | 1 |
| Integration tests for 4 endpoints (ES + OS) | M | 7 |
| Performance validation: `/summary` latency vs original spec | S | 3 |

**Layer 2A subtotal: ~31 days**

### Layer 2B — Saved-report companion

| Task | Size | Days |
|---|---|---|
| Author `template_agentic.json` (~12 tile JSON definitions + grid layout) | M | 5 |
| Wire into `InstantPreviewDashboardService` startup | XS | 1 |
| Template deserialization validation against ES + OS | XS | 1 |
| Dashboard discoverability — Dashboards list entry + optional nav pin | S | 2 |
| Validate alert configuration flow end-to-end on one tile | S | 2 |

**Layer 2B subtotal: ~11 days**

### Layer 3A — Custom dashboard frontend

| Task | Size | Days |
|---|---|---|
| API client + TypeScript types + mock responses | S | 3 |
| Filter context, date range picker, process selector dropdown | S | 3 |
| Dashboard routing + L0/L1 level switching + nav entry | S | 4 |
| KPI cards: Total Runs, Avg Duration, Incident Rate (with delta badges) | S | 3 |
| Token stats: Avg Tokens Per Run, Median Tokens Per Run | XS | 1 |
| Duration P50/P95 KPI cards + duration stability trend chart | S | 3 |
| Token trend chart + token outlier bands | S | 3 |
| Top token consumers by process bar chart | S | 3 |
| Tool call frequency bar chart | S | 2 |
| Avg tokens per call by process bar chart | S | 2 |
| Incident rate by version bar chart | S | 2 |
| Dashboard layout + empty states per chart | S | 3 |
| Pixel-level styling polish | M | 5 |

**Layer 3A subtotal: ~37 days**

### Layer 3B — Saved-report frontend

| Task | Size | Days |
|---|---|---|
| Add `hasAgentRuns=true` query parameter to process selector when on agentic dashboard | XS | 1 |
| Verify all standard dashboard widgets render new view properties correctly | S | 3 |

**Layer 3B subtotal: ~4 days**

### Validation

| Task | Size | Days |
|---|---|---|
| E2E smoke test with real Zeebe agent data on custom dashboard | M | 5 |
| E2E test for alert configuration on saved report | S | 2 |
| UX review iteration for custom dashboard | S | 3 |

**Validation subtotal: ~10 days**

### Hybrid total: **~149 days (~30 weeks)**

> The hybrid variant is the most expensive of the three because it ships
> **both** the custom UI (Layer 3A) and the saved-report companion
> (Layer 2B + 3B). It is also the most capable: nothing from the original
> spec is sacrificed, and alert + customization capabilities from the
> full-reuse variant are added.

---

## 8. Variant Comparison

| Dimension | Variant 1 (No-Reuse) | Variant 2 (Hybrid) | Variant 3 (Full-Reuse) |
|---|---|---|---|
| New REST endpoints | 4 | 4 | 0 |
| New UI components | ~10 | ~10 | 0 |
| Saved-report companion | ❌ | ✅ | ✅ (only) |
| Shared infra changes | None | Same as V3 | New filter / views / groupBys / interpreters |
| Period delta badges | ✅ | ✅ | ❌ |
| Bespoke layout matching PNGs | ✅ | ✅ | ❌ |
| Alerts on agentic KPIs | ❌ None | ✅ Free via Layer 2B | ✅ Free via tiles |
| Discoverability in Dashboards list | ❌ | ✅ via Layer 2B | ✅ |
| Per-request ES query count for `/summary` | 2 (parallel) | 2 (parallel — same custom path) | ~14 (one per tile) |
| Total effort from scratch | ~107 days | ~149 days | ~81 days |
| Risk of regression in existing reports | None | Medium (shared infra) | Medium (shared infra) |
| Code delta | ~2,000+ LOC | ~2,500 LOC + JSON | ~700 LOC + JSON |
| Maintenance: parallel code paths | None | One backend code path (shared infra) | One |
| Long-term codebase consolidation | Two parallel paths | One shared infra path | One shared infra path |
| Phase 2 extensibility | Custom per metric | Custom endpoint adds a section + new tile in saved-report dashboard | New tile only |

### Decision matrix

| Hard requirement | Variant to choose |
|---|---|
| Pixel-perfect PNG match | V1 or V2 (not V3) |
| Period delta badges | V1 or V2 (not V3) |
| Alerts on agentic KPIs | V2 or V3 (not V1) |
| Lowest total effort | V3 |
| Lowest risk to other Optimize subsystems | V1 |
| All of the above | V2 |

**Variant 2 (this document) is the right choice when**:
- Custom UI fidelity is non-negotiable
- Alert support is a Phase 1 requirement (or strong Phase 2 expectation)
- Long-term codebase consolidation (single shared infrastructure for all
  process-instance analytical queries) is valued
- Effort budget can absorb the ~150-day investment

**Variant 2 is the wrong choice when**:
- Ship date is the primary constraint (V3 is ~80 days, V1 ~110 days)
- Alert capability is explicitly out of scope and the saved-report
  companion adds no value (drop Layer 2B to save ~11 days, becomes
  V1-with-shared-infra)
- The product team is willing to accept the V3 UX compromises in
  exchange for half the effort

---

## 9. Open Questions

1. **Saved-report dashboard left-nav placement**: pin next to the custom
   dashboard entry, or leave in the Dashboards list only?
2. **Alert pre-configuration**: ship default alerts (e.g. incident rate
   > 5%) along with the saved reports, or leave alert configuration
   entirely to operators?
3. **Combined Report vs separate tiles for token trend**: in Layer 2B,
   use Optimize's Combined Report feature to overlay input/output token
   lines, or two side-by-side tiles? (Layer 2A is unaffected — custom
   chart component.)
4. **`/summary` execution path**: single custom ES query (matches
   original spec, ~2 queries per request) vs report-evaluation
   composition (~14 queries). Recommend custom query for latency.
5. **Per-tool Phase 2**: add as a new tile in Layer 2B and a new chart
   section in Layer 2A simultaneously? Both use the same nested groupBy
   interpreter.

---

## 10. Final Verification Statement

Every Phase 1 metric is traceable to a concrete execution path in **both**
the custom endpoint surface (Layer 2A) and the saved-report companion
(Layer 2B). Period deltas are computed in Layer 2A's service layer (not
in the report engine), preserving the original spec's response contract.
Alerts are enabled via Layer 2B without any new alert code. The shared
infrastructure in Layer 2C is the single source of truth for filter,
view, and groupBy logic — no duplicate implementations between custom
endpoints and saved reports.
