# Agentic Control Plane — High-Level Specification (Full-Reuse Variant)

**Module**: `optimize/`
**Phase 1 scope**: L0 (fleet view) + L1 (single process view)
**Companion to**:
- `agentic-control-plane-spec-high-level.md` — original dedicated implementation
- `agentic-control-plane-spec-high-level-reuse.md` — partial reuse via report system
- `use-existing-endpoints.md` — trade-off analysis

> **What this variant aims for**
>
> **Zero new REST endpoints.** **Zero new UI components.** Operators interact
> with the agentic dashboard through the **existing Optimize Dashboards UI**
> rendering a pre-seeded `instant_preview_dashboards/template_agentic.json`
> template, evaluated through the **existing `POST /api/report/.../evaluate`**
> pipeline.
>
> All Phase 1 metrics are **fully computable** under this variant with the
> right infrastructure additions and parent-level denormalizations. Period
> deltas are the only metric category that requires accepting a UX
> compromise (operators see absolute numbers per date range, not a
> ±delta badge).

---

## 1. Feature Overview

The Agentic Control Plane is rendered as a **standard Optimize Dashboard**
populated with several agentic report tiles. Operators reach it the same
way they reach any other dashboard: from the **Dashboards** list in the
left-nav, or via a deep link / pinned bookmark.

**What the user sees:**

- A standard Optimize dashboard, identifiable by name (e.g.
  `"Agentic Control Plane"`).
- Multiple report tiles arranged on the dashboard canvas: total runs,
  duration percentiles, token consumption, tool-call frequency,
  incident rate, top processes, time-series trends.
- The standard **dashboard filter bar** for date range and process
  definition. Selecting a process in the filter bar narrows every tile
  to that process (this is the L0 → L1 transition, expressed through
  the existing dashboard filter mechanism instead of a custom toggle).

**Phase 1 scope boundary:**
- ✅ All metrics from the original spec, surfaced as report tiles
- ❌ Period delta badges on KPI cards (not a standard report widget feature; operators see absolute numbers and change date ranges manually for before/after comparison)
- ❌ Bespoke layout matching the PNG designs (uses standard Optimize grid)
- ❌ Hidden "Configure in settings" links / custom incident-rate card styling

---

## 2. Data Flow

```
Zeebe engine
  AgentInstanceRecord (CREATED) ─┐
  AgentInstanceRecord (COMPLETED) │
                                  │
                  Layer 1 — Import Pipeline (UNCHANGED + 1 extra parent field)
                                  │ upserts into
                                  ▼
                       ProcessInstanceIndex
                       (nested agentInstances + denormalized parent totals
                        including new agentTotalTokens field)
                                  │
                                  │ queried via existing
                                  ▼
                       Existing report evaluation pipeline
                       (ReportEvaluationHandler, view/groupBy
                        interpreters, filter enhancer)
                                  │
                       Existing report + dashboard REST endpoints
                       (POST /api/report/{id}/evaluate, etc.)
                                  │
                                  │ rendered by
                                  ▼
                       Existing Optimize Dashboards UI
                       (DashboardRenderer + standard report tiles)
                                  │
                                  ▼
                       Operator's browser
```

---

## 3. Layer 1 — Import Pipeline

**Almost identical to the original spec §3.** Same Zeebe events, same nested
`agentInstances[]` schema, same `ProcessInstanceIndex` version bump.

**One additional parent-level denormalized field:**

| Field | Type | Nullable | Description |
|---|---|---|---|
| `agentTotalTokens` | `long` | No (default 0) | Sum of `metrics.inputTokens + metrics.outputTokens` across all agent instances on this process instance. Re-computed on each import. **Required** to express `avgTokensPerRun`, `medianTokensPerRun`, and token percentile aggregations as standard report aggregations on a single field. |

Painless update script extended to compute and write this field on every
import event.

---

## 4. Layer 2 — Backend Strategy

### 4.1 Shared infrastructure additions

| Category | Items |
|---|---|
| **New filter** | `HasAgentInstancesFilterDataDto` + ES/OS filter + Jackson subtype + enhancer registration |
| **New view entity** | `ProcessViewEntity.AGENT_INSTANCE` |
| **New view properties** | `INPUT_TOKENS`, `OUTPUT_TOKENS`, `MODEL_CALLS`, `TOOL_CALLS`, `TOTAL_TOKENS` (for denormalized field) |
| **New `ProcessView` enum values** | One per (AGENT_INSTANCE, viewProperty) pair |
| **New view interpreters** | `ProcessViewAgent{Input,Output,Total}Tokens/ModelCalls/ToolCalls InterpreterES/OS`, all extending `AbstractProcessViewMultiAggregationInterpreterES/OS` |
| **New groupBy** | `PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY` + `PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION` + DTOs + Jackson subtypes + ES/OS interpreters |

### 4.2 No new REST endpoints

The agentic dashboard does **not** expose `/api/agentic-control-plane/*`.
All data is read through:

| Existing endpoint | Used for |
|---|---|
| `POST /api/report/{id}/evaluate` | Each report tile on the dashboard |
| `POST /api/report/evaluate` | Ad-hoc preview during template construction |
| `GET /api/dashboard/{id}` | Loading the agentic dashboard |
| `GET /api/process-definition` (extended with `hasAgentRuns=true`) | Process selector dropdown — reused from existing endpoint |

The `hasAgentRuns=true` extension on `GET /api/process-definition` is the
**only** modification to an existing public endpoint. It reuses the new
`HasAgentInstancesQueryFilter` from §4.1.

### 4.3 Dashboard template

A new template `instant_preview_dashboards/template_agentic.json` is added
to the existing `instant_preview_dashboards/` directory next to
`template1.json`, `template2.json`, `template3.json`. It is loaded at
application startup by the existing `InstantPreviewDashboardService`
(`InstantPreviewDashboardService.java:78`).

The template contains a `DashboardDefinitionRestDto` with grid layout
coordinates and N embedded
`SingleProcessReportDefinitionExportDto` entries — one per report tile,
each carrying a complete `ProcessReportDataDto` (view, groupBy, filters,
aggregation types, visualization).

### 4.4 Verified metric-by-metric computability

Every metric from the original spec is checked against the existing report
infrastructure. Status:
- ✅ **Standard**: works directly with existing views/groupBys/aggs
- 🔧 **With infra**: works after adding the shared infrastructure in §4.1
- 📦 **With denormalization**: works after the `agentTotalTokens` parent
  field in §3 plus shared infra in §4.1
- 🧩 **With Combined Report**: needs Optimize's existing Combined-Report
  feature to plot two report series together
- ⚙️ **With custom interpreter**: needs a small new view interpreter
  following the existing `ProcessViewInstancePercentageInterpreter` pattern
- ❌ **Not deliverable**: requires a UX/feature compromise

#### `/summary` metrics

| Metric | Status | How |
|---|---|---|
| `totalRuns` | ✅ | `PROCESS_INSTANCE / FREQUENCY` + `GROUP_BY_NONE` |
| `avgDurationMs` | ✅ | `PROCESS_INSTANCE / DURATION` + `AggregationType.AVERAGE` |
| `p50DurationMs`, `p95DurationMs` | ✅ | Same view, `Set<AggregationDto>` with `PERCENTILE(50)` + `PERCENTILE(95)` — multi-agg already supported |
| `incidentRate` | ✅ | `PROCESS_INSTANCE / PERCENTAGE` + `OpenIncidentFilter` OR `ResolvedIncidentFilter`. Existing `ProcessViewInstancePercentageInterpreterES:55` computes `filteredDocCount / unfilteredTotalInstanceCount × 100`. |
| `incidentCount` | ✅ | `PROCESS_INSTANCE / FREQUENCY` + incident filter (counts instances with incidents, not incidents themselves) |
| `activationCount` | ✅ | Identical to `totalRuns` at L0/L1 — reserved for L2 |
| `avgTokensPerRun` | 📦 | `AGENT_INSTANCE / TOTAL_TOKENS` + `AggregationType.AVERAGE` on the new `agentTotalTokens` parent field |
| `medianTokensPerRun` | 📦 | Same view + `PERCENTILE(50)` |
| `totalRunsDelta`, `avgDurationMsDelta`, `incidentRateDelta` | ❌ | **Not deliverable**: no period-comparison concept in `ReportEvaluationHandler`. Operators read the absolute number and change the date-range filter manually for before/after comparison. |

#### `/process-breakdown` metrics

| Metric | Status | How |
|---|---|---|
| `processes[].processDefinitionKey` (bucket key) | 🔧 | Requires new `PROCESS_GROUP_BY_PROCESS_DEFINITION_KEY` from §4.1 |
| `processes[].totalInputTokens` | 🔧 | `AGENT_INSTANCE / INPUT_TOKENS` + `SUM` view + groupBy DEFINITION_KEY |
| `processes[].totalOutputTokens` | 🧩 or 📦 | Different view from input tokens — either two side-by-side tiles, or a Combined Report overlay, or use a single tile on `AGENT_INSTANCE / TOTAL_TOKENS` (input + output combined) and show "total" instead of split |
| `processes[].processInstanceCount` | 🔧 | `PROCESS_INSTANCE / FREQUENCY` + groupBy DEFINITION_KEY |
| Sort order: `totalInputTokens + totalOutputTokens` descending | 📦 | Sort the `agentTotalTokens` SUM tile to get a single ranked bar chart — this is the cleanest path |

**Recommendation**: rank the bar chart by `agentTotalTokens` (one report).
Show separate input/output breakdown as a secondary tile or accept the
combined "total tokens by process" presentation.

#### `/trends` metrics (per date bucket)

| Metric | Status | How |
|---|---|---|
| Date histogram bucketing | ✅ | Existing `PROCESS_GROUP_BY_PROCESS_INSTANCE_START_DATE` |
| `inputTokens` per bucket | 🔧 | Date histogram + `AGENT_INSTANCE / INPUT_TOKENS` + `SUM` |
| `outputTokens` per bucket | 🧩 | Combined Report overlay with the input report — or use `AGENT_INSTANCE / TOTAL_TOKENS` SUM for one "total token" line |
| `tokenP5`, `tokenP50`, `tokenP95` per bucket | 📦 | Date histogram + `AGENT_INSTANCE / TOTAL_TOKENS` view + `Set<AggregationDto>` with three PERCENTILE entries (5, 50, 95) |
| `durationP50Ms`, `durationP95Ms` per bucket | ✅ | Date histogram + `PROCESS_INSTANCE / DURATION` + multi-PERCENTILE |

**Tile composition for trends**: 2 reports (one for token sums + percentiles,
one for duration percentiles), each rendered as a separate line chart on
the dashboard. The visual layout matches the original spec's "3 components
sharing /trends" pattern: one chart for tokens-over-time, one for
duration-stability.

#### `/charts` metrics

| Metric | Status | How |
|---|---|---|
| `toolFrequency` Phase 1 (`all_tools` synthetic) | 🔧 | `AGENT_INSTANCE / TOOL_CALLS` + `SUM` + `GROUP_BY_NONE` → single number wrapped as `[{toolName: "all_tools", totalToolCalls: N}]` in the dashboard tile config |
| `toolFrequency` Phase 2 (per-tool breakdown) | ⚙️ | Out of Phase 1 — requires nested groupBy interpreter on `agentInstances.tools.name`, same effort as variant 2 |
| `avgTokensPerCall[].processDefinitionKey` (bucket) | 🔧 | groupBy DEFINITION_KEY |
| `avgTokensPerCall[].avgTokensPerCall` = `(sum(input)+sum(output))/sum(modelCalls)` per process | ⚙️ | **Ratio of sums** — no standard view computes this. Two paths: |
|  |   | (a) Add custom interpreter `ProcessViewAgentAvgTokensPerCallInterpreterES/OS` (~2 days; pattern follows `ProcessViewInstancePercentageInterpreter` which already does a ratio) |
|  |   | (b) Show as two tiles: "tokens by process" + "model calls by process", let operators eyeball the ratio. UX compromise. |
| `avgTokensPerCall[].totalModelCalls` | 🔧 | `AGENT_INSTANCE / MODEL_CALLS` + `SUM` + groupBy DEFINITION_KEY — can be its own tile or part of the bar chart label |
| `incidentRateByVersion[].version` (bucket key) | 🔧 | Requires new `PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION` from §4.1 |
| `incidentRateByVersion[].incidentRate` | ✅ | `PROCESS_INSTANCE / PERCENTAGE` + incident filter + groupBy DEFINITION_VERSION. No custom interpreter needed — the existing percentage view does the division per bucket. |
| `incidentRateByVersion[].runs` | 🔧 | Same groupBy + `PROCESS_INSTANCE / FREQUENCY` — show as a separate tile or display the absolute count alongside the percentage in the same bar chart |

### 4.5 Summary of metric coverage

**Fully computable with infrastructure additions and `agentTotalTokens` denormalization** (no special workarounds):

- `totalRuns`, `avgDurationMs`, `p50DurationMs`, `p95DurationMs`
- `incidentRate`, `incidentCount`, `activationCount`
- `avgTokensPerRun`, `medianTokensPerRun`
- All `/process-breakdown` metrics (with sort-by-total recommendation)
- All `/trends` metrics
- `toolFrequency` Phase 1
- `incidentRateByVersion` (rate + runs)

**Requires a small custom interpreter** (~2 days, follows existing pattern):

- `avgTokensPerCall` ratio-of-sums per process (alternatively: accept the two-tile compromise)

**Not deliverable in this variant** (UX compromise):

- `totalRunsDelta`, `avgDurationMsDelta`, `incidentRateDelta` — operators read absolute numbers and change date ranges manually

### 4.6 Tile inventory (final)

| Tile (visible name) | Visualization | View / GroupBy / Aggs |
|---|---|---|
| Total Agentic Runs | KPI number | `PROCESS_INSTANCE/FREQUENCY` / `NONE` / count |
| Incident Count | KPI number | `PROCESS_INSTANCE/FREQUENCY` / `NONE` + incident filter |
| Incident Rate | KPI percentage | `PROCESS_INSTANCE/PERCENTAGE` / `NONE` + incident filter |
| Avg Duration | KPI number (ms→s) | `PROCESS_INSTANCE/DURATION` / `NONE` / `AVERAGE` |
| P50/P95 Duration | KPI numbers | Same view / `NONE` / `{PERCENTILE(50), PERCENTILE(95)}` |
| Avg Tokens Per Run | KPI number | `AGENT_INSTANCE/TOTAL_TOKENS` / `NONE` / `AVERAGE` |
| Median Tokens Per Run | KPI number | Same view / `NONE` / `PERCENTILE(50)` |
| Tool Call Frequency (Phase 1) | KPI number | `AGENT_INSTANCE/TOOL_CALLS` / `NONE` / `SUM` |
| Token Trend (input + output) | Combined line chart | Combined report: 2 line charts: `INPUT_TOKENS/SUM` and `OUTPUT_TOKENS/SUM` over `START_DATE` histogram |
| Token Outlier Bands (P5/P50/P95) | Line chart with bands | `TOTAL_TOKENS` / `START_DATE` histogram / `{PERCENTILE(5), PERCENTILE(50), PERCENTILE(95)}` |
| Duration Stability (P50/P95) | Line chart with bands | `DURATION` / `START_DATE` histogram / `{PERCENTILE(50), PERCENTILE(95)}` |
| Top Token Consumers by Process | Horizontal bar chart | `TOTAL_TOKENS` / `DEFINITION_KEY` / `SUM`, sorted descending |
| Avg Tokens Per Call by Process | Horizontal bar chart | Custom interpreter `AgentAvgTokensPerCall` / `DEFINITION_KEY` (or two-tile compromise) |
| Incident Rate by Version (L1 only) | Bar chart | `PROCESS_INSTANCE/PERCENTAGE` / `DEFINITION_VERSION` + incident filter |

---

## 5. Layer 3 — Frontend

### 5.1 No new components

The agentic dashboard renders through:
- `optimize/client/src/components/Dashboards/` — existing dashboard page
- `optimize/client/src/modules/components/DashboardRenderer/` — existing dashboard renderer
- `optimize/client/src/modules/components/DashboardRenderer/DashboardTile/` — existing tile renderer

No new React routes, no new pages, no new components.

### 5.2 Filter and process selector

The standard Optimize dashboard filter bar provides date range picker and
process definition selector. This replaces the original spec's bespoke
L0/L1 dropdown.

The process selector dropdown can be limited to processes with agent runs
via the same `GET /api/process-definition?hasAgentRuns=true` extension as
the original spec. A small frontend tweak passes `hasAgentRuns=true` when
the agentic dashboard is active — ~5 lines, no new component.

### 5.3 UX trade-offs vs the PNG designs

| PNG element | Standard dashboard equivalent | Gap |
|---|---|---|
| KPI cards with green/red delta badges | Single-number tile | No delta badge — see §4.4 period-delta row |
| "Configure in settings" link on Incident Rate card | Generic incident percentage tile | No bespoke styling |
| L0/L1 toggle as part of dashboard chrome | Process filter in standard filter bar | Different mental model, same end result |
| Specific tile arrangement matching the design | Configurable grid layout | Visual match is approximate |
| Avg Tokens Per Call by Agent at L1 | Per-process breakdown only | Per-agent breakdown is Phase 2 (needs nested groupBy) |

All underlying metrics are computable; they don't display in the bespoke
styling the PNGs show.

---

## 6. Cross-cutting Concerns

| Concern | Handling |
|---|---|
| Multi-tenancy | Inherited from `ReportEvaluationHandler` |
| Authorization | Standard report + dashboard auth |
| Date range semantics | `InstanceStartDateFilterDto` applied via the dashboard filter |
| Period comparison | **Not supported** (see §4.4) |
| Recency gap | Same as original — only COMPLETED instances are indexed |

---

## 7. Alert Integration (Significant Bonus)

Every metric is a saved report, so **alerts work out of the box** for the
entire dashboard. Operators can:

1. Open any tile on the agentic dashboard
2. Click "Create alert" (existing Optimize feature)
3. Set a threshold (e.g., "alert when daily incident count > 5")
4. Receive notifications via the standard alert delivery mechanism

Impossible in the original spec without rebuilding alert integration.

---

## 8. Task Breakdown

### Layer 1 — Import Pipeline

| Task | Size | Days |
|---|---|---|
| Bump `ProcessInstanceIndex` version, add nested + parent fields | S | 2 |
| Add new parent denormalized `agentTotalTokens` field | XS | 1 |
| Import service for `AgentInstanceRecord` (CREATED + COMPLETED) | M | 7 |
| Painless update script: nested upsert + parent aggregation including agentTotalTokens | M | 5 |
| Validation against live ES/OS instance | S | 3 |

**Layer 1 subtotal: ~18 days**

### Layer 2 — Shared infrastructure

| Task | Size | Days |
|---|---|---|
| `HasAgentInstancesFilterDataDto` + Jackson subtype on `ProcessFilterDto` | XS | 1 |
| `HasAgentInstancesQueryFilterES` + `OS` + enhancer registration | S | 3 |
| `ProcessViewEntity.AGENT_INSTANCE` | XS | 0.5 |
| `ViewProperty` constants: INPUT_TOKENS, OUTPUT_TOKENS, MODEL_CALLS, TOOL_CALLS, TOTAL_TOKENS | XS | 0.5 |
| `ProcessView` enum entries (~5 combinations) | XS | 1 |
| Token view interpreters ES + OS (5 pairs ≈ 10 classes) on existing multi-aggregation bases | M | 6 |
| Register view interpreters in `ProcessViewInterpreterFacadeES/OS` | XS | 1 |
| `ProcessDefinitionKeyGroupByDto` + Jackson subtype + ES/OS interpreters | S | 3 |
| `ProcessDefinitionVersionGroupByDto` + Jackson subtype + ES/OS interpreters | S | 3 |
| Register groupBy interpreters in facades | XS | 1 |
| Custom `ProcessViewAgentAvgTokensPerCallInterpreterES/OS` (ratio of sums) | S | 3 |
| Extend `GET /api/process-definition` with `hasAgentRuns=true` (reuses new filter) | XS | 1 |
| Integration tests for new views + groupBys + filter + custom interpreter (ES + OS) | M | 8 |
| Regression test sweep across existing reports | M | 5 |
| Performance regression check | S | 3 |

**Layer 2 subtotal: ~40 days**

### Layer 2 — Dashboard template

| Task | Size | Days |
|---|---|---|
| Author `template_agentic.json` (~14 tile JSON definitions + grid layout) | M | 5 |
| Wire into `InstantPreviewDashboardService` startup + checksum mechanism | XS | 1 |
| Validate template against ES + OS deserialization | XS | 1 |
| Dashboard discoverability (left-nav entry or pinned dashboard) | S | 2 |
| Validate dashboard rendering on real data | S | 3 |

**Template subtotal: ~12 days**

### Layer 3 — Frontend

| Task | Size | Days |
|---|---|---|
| Add `hasAgentRuns=true` query parameter to process selector when on agentic dashboard | XS | 1 |
| Verify all standard dashboard widgets render new view properties correctly | S | 3 |

**Layer 3 subtotal: ~4 days**

### Validation

| Task | Size | Days |
|---|---|---|
| E2E smoke test with real Zeebe agent data | M | 5 |
| Operator UX walkthrough — confirm "good enough" vs PNG designs | S | 2 |

**Validation subtotal: ~7 days**

### Variant 3 grand total: ~81 days (~16 weeks)

---

## 9. Variant Comparison (Updated)

| Dimension | Original (Variant 1) | Full Reuse (Variant 3) |
|---|---|---|
| New REST endpoints | 4 | **0** |
| New UI components / pages | ~10 | **0** |
| Shared infra changes | None | New filter / views / groupBys / interpreters / 1 custom interpreter |
| Period delta badges | ✅ Yes | ❌ Not deliverable |
| Bespoke layout matching PNGs | ✅ Yes | ❌ Standard dashboard grid |
| Per-request ES query count | 2 (parallel) | ~14 (one per tile, paralleled by dashboard renderer) |
| Alerts on agentic KPIs | ❌ None | ✅ **Free** for every tile |
| Discoverability via Dashboards list | ❌ No | ✅ Yes |
| Total effort from scratch | ~107 days | ~81 days |
| Risk of regression in existing reports | None | Medium |
| Code delta | ~2,000+ LOC | ~700 LOC + JSON template |

### When to choose this variant

- **Strict no-new-endpoints policy**
- **Alerts on agentic KPIs are a hard requirement**
- **Standard Optimize dashboard UX is acceptable for Phase 1**
- **Long-term codebase consolidation valued**

### When this variant is the wrong choice

- **Pixel-perfect match to the PNG designs is non-negotiable** — period deltas, custom card styling, bespoke L0/L1 navigation are not deliverable
- **Latency budget is tight** — one report per tile means more ES queries per page load
- **Operators expect a dedicated nav item with a distinct page** — only the original spec delivers that natively

---

## 10. Open Questions

1. **Dashboard discoverability**: pinned top-level nav entry, or just appear
   in the Dashboards list with a notable name + icon?
2. **Period delta**: confirm with product that absolute numbers + manual
   date-range comparison is acceptable. If not, this variant is off the
   table.
3. **`avgTokensPerCall` strategy**: custom interpreter (+3 days) for exact
   ratio-of-sums semantics, or two-tile compromise (no extra code)?
4. **`processes[].totalInputTokens` vs `totalOutputTokens`**: split into two
   tiles or merge into total via `agentTotalTokens`?
5. **Phase 2 per-tool breakdown**: requires nested groupBy interpreter on
   `agentInstances.tools.name`. Adds another tile.
6. **Template lifecycle**: existing instant-preview templates are
   immutable. If operators customize the agentic dashboard, copy to a
   writeable dashboard or keep templated?

---

## 11. Final Verification Statement

Every metric in the original `/summary`, `/process-breakdown`, `/trends`,
and `/charts` response shapes is **traceable to a concrete implementation
path** in this variant. The only metric category with no implementation
path is the three period-delta fields, which are explicitly out of scope
(see §4.4). All others have either a verified standard report path, a
denormalization-based path, or a small custom interpreter (one case only,
following an existing pattern in the codebase).
