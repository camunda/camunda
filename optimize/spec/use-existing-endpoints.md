# Architectural Decision: Agentic Dashboard Implementation Strategy

## Purpose

Decision document comparing three implementation strategies for the
Agentic Control Plane dashboard. Treats all options as **greenfield
implementations** — no assumption that any code already exists. Effort
estimates start from zero.

## Companion specs

- `agentic-control-plane-spec-high-level.md` — **Variant 1**: dedicated
  endpoints, dedicated UI, no shared infrastructure changes
- `agentic-control-plane-spec-high-level-reuse.md` — **Variant 2**:
  dedicated endpoints + dedicated UI **and** saved-report companion,
  built on shared infrastructure
- `agentic-control-plane-spec-high-level-full-reuse.md` — **Variant 3**:
  zero new endpoints, zero new UI, pure saved-report dashboard on
  shared infrastructure

---

## Verified facts about existing Optimize infrastructure

All claims below confirmed against the codebase before writing this
document.

### What already exists (reusable as-is)

| Component | Location | Capability |
|---|---|---|
| `ProcessQueryFilterEnhancerES` / `OS` | `service/db/es,os/.../filter/` | Filter composition chain — 24+ filter types |
| `CompletedInstancesOnlyQueryFilterES` / `OS` | `service/db/es,os/.../filter/` | Completed-only constraint |
| `InstanceStartDateQueryFilterES` / `OS` | `service/db/es,os/.../filter/` | Date range on `startDate` |
| `OpenIncidentFilterDto`, `ResolvedIncidentFilterDto`, `NoIncidentFilterDto` | `optimize-commons/.../filter/` | Incident-based instance filtering |
| `AggregationType.PERCENTILE` | `optimize-commons/.../configuration/` | Percentile aggregation, wired in `AbstractProcessViewMultiAggregationInterpreterES.java:30` |
| `Set<AggregationDto>` on `SingleReportConfigurationDto` | `optimize-commons/.../configuration/SingleReportConfigurationDto.java:31` | Multi-aggregation per report (e.g. AVG + PERCENTILE(50) + PERCENTILE(95) on the same view in one query) |
| `ProcessViewInstancePercentageInterpreterES` | `service/db/es/.../view/process/` | Computes `(filteredDocCount / unfilteredTotalInstanceCount) × 100` — enables incident rate via PERCENTAGE view |
| `ReportEvaluationHandler` | `service/db/report/` | Single-shot report evaluation pipeline |
| `AlertJob` | `service/alert/AlertJob.java:54,97` | Threshold alerts on any saved report — evaluates report, extracts scalar `Double`, compares to threshold |
| `InstantPreviewDashboardService` | `service/dashboard/InstantPreviewDashboardService.java:78` | Loads pre-seeded dashboard templates from `instant_preview_dashboards/*.json` at startup via `@EventListener(ApplicationReadyEvent)` |
| Existing dashboard rendering UI | `client/src/components/Dashboards/`, `client/src/modules/components/DashboardRenderer/` | Renders any saved dashboard from its DTO definition |

### What is missing (needs to be added regardless of variant)

| Missing | Required by which metrics |
|---|---|
| `HasAgentInstancesQueryFilter` (nested-exists on `agentInstances`) | Every agentic query — the "only count instances with at least one agent" constraint |
| `ProcessViewEntity.AGENT_INSTANCE` enum value | Every agentic view |
| `ViewProperty` constants for `INPUT_TOKENS`, `OUTPUT_TOKENS`, `MODEL_CALLS`, `TOOL_CALLS`, `TOTAL_TOKENS` | Token + call metrics |
| `ProcessView` enum entries for each (AGENT_INSTANCE, viewProperty) pair | View interpreter lookup |
| `ProcessGroupBy.PROCESS_DEFINITION_KEY` + DTO + interpreter | `/process-breakdown`, `/charts.avgTokensPerCall`, top-token-consumers tile |
| `ProcessGroupBy.PROCESS_DEFINITION_VERSION` + DTO + interpreter | `/charts.incidentRateByVersion` |
| View interpreters (ES + OS) for the 5 new agent view properties | Token / call aggregations |
| Custom interpreter `ProcessViewAgentAvgTokensPerCall` (ratio of sums) | `avgTokensPerCall` per process — same pattern as `ProcessViewInstancePercentageInterpreter` |
| `agentTotalTokens` parent-level denormalization (= sum of input + output across all agent instances) | `avgTokensPerRun`, `medianTokensPerRun`, token percentile aggregations |

These are **shared infrastructure additions**. Variant 1 does **not** need
them (it hand-builds ES queries directly). Variants 2 and 3 do.

### What does not exist in Optimize and cannot be added cheaply

| Gap | Workaround |
|---|---|
| **Period comparison** in `ReportEvaluationHandler` (delta vs prior period) | Service-layer wrapper that runs current + prior evaluations in parallel via `CompletableFuture`, then subtracts. Possible only outside the report engine. |
| **Multi-metric response shape** — `CommandEvaluationResult<T>` is one-report-one-result | Custom endpoint composes N report evaluations; cannot be a single report |
| **Generic nested-path aggregation** in the report interpreter chain — nested aggs exist for `flowNodeInstances`, `userTasks`, `variables`, `incidents` but each is path-hardcoded | Add new nested groupBy interpreter per new path (e.g. `agentInstances.tools.name` for Phase 2 tool breakdown) |
| **Period delta badges on report tiles** — standard report widgets show one period only | Either custom UI rendering or accept absolute numbers with manual date-range comparison |

---

## Metric-by-metric computability (all three variants)

Verified against the original spec's required outputs.

| Metric | V1 (custom) | V2 (hybrid) | V3 (full reuse) |
|---|---|---|---|
| `totalRuns` | ✅ Custom ES query | ✅ Custom endpoint single query | ✅ Standard report |
| `avgDurationMs` | ✅ | ✅ | ✅ |
| `p50DurationMs`, `p95DurationMs` | ✅ | ✅ | ✅ |
| `incidentRate` | ✅ Custom filter agg | ✅ via PERCENTAGE view + incident filter | ✅ via PERCENTAGE view + incident filter |
| `incidentCount` | ✅ | ✅ | ✅ |
| `activationCount` | ✅ | ✅ | ✅ |
| `avgTokensPerRun` | ✅ Custom service math | ✅ Custom service math via summary endpoint | ✅ AVG on `agentTotalTokens` |
| `medianTokensPerRun` | ✅ Scripted percentile | ✅ Percentile on `agentTotalTokens` | ✅ Percentile on `agentTotalTokens` |
| `totalRunsDelta`, `avgDurationMsDelta`, `incidentRateDelta` | ✅ Period comparison executor | ✅ Same | ❌ **Not deliverable** — operators change date range manually |
| `processes[].totalInputTokens/OutputTokens/processInstanceCount` | ✅ | ✅ via 2 parallel reports | ✅ via 2 reports or combined report |
| `trends[].inputTokens, outputTokens` | ✅ | ✅ | ✅ via Combined Report |
| `trends[].tokenP5/P50/P95` | ✅ Scripted percentile | ✅ Percentile multi-agg on `agentTotalTokens` | ✅ Same |
| `trends[].durationP50Ms/P95Ms` | ✅ | ✅ | ✅ |
| `toolFrequency` Phase 1 (`all_tools` synthetic) | ✅ | ✅ TOOL_CALLS SUM | ✅ Same |
| `toolFrequency` Phase 2 (per-tool) | Custom nested terms agg | Same — Phase 2 work | New nested groupBy interpreter on `agentInstances.tools.name` |
| `avgTokensPerCall` per process (ratio of sums) | ✅ Custom scripted agg | ✅ Custom view interpreter (3 days, follows percentage pattern) | ✅ Same custom interpreter OR show as multi-tile |
| `incidentRateByVersion` | ✅ Custom terms + nested filter | ✅ PERCENTAGE view + groupBy VERSION + incident filter | ✅ Same |

**Summary**:
- **All three variants compute every Phase 1 metric** with the right
  infrastructure additions
- **Period deltas are the only feature category** that splits the
  variants: deliverable in V1 and V2 (custom endpoints have period
  comparison executors), not in V3 (standard report widgets do not
  render deltas)

---

## Architecture characteristics

### Variant 1 — Dedicated endpoints + dedicated UI

- 4 new REST endpoints under `/api/agentic-control-plane/`
- Hand-built `AgenticControlPlaneRepositoryES` + `OS` issuing ES queries
  directly (no use of view/groupBy interpreters)
- Bespoke `AgentBaselineFilterBuilder` for the filter set
- Bespoke React dashboard page at `/agentic-control-plane`
- Period delta badges, pixel-perfect PNG match, single-query `/summary`
- **Zero changes to shared Optimize infrastructure**
- No saved-report tiles → no alert support

### Variant 2 — Hybrid (dedicated endpoints built on shared infrastructure + saved-report companion)

- Same 4 REST endpoints as Variant 1, same response shapes, same UI
- Endpoint internals **compose results through new shared infrastructure**
  (view interpreters, groupBys, filters) — single source of truth shared
  with Variant 2B
- `/summary` keeps the fast 2-query custom path for latency; other
  endpoints use `ReportEvaluationHandler` composition
- Period comparison executor wraps the report evaluation pipeline
- Companion `template_agentic.json` pre-seeded dashboard of saved reports
  → alerts work for free, dashboard appears in Optimize Dashboards list
- Shared infrastructure additions impact other Optimize reports
  (regression risk)

### Variant 3 — Full reuse (zero new endpoints, zero new UI)

- No new REST endpoints — only extends `GET /api/process-definition` with
  `hasAgentRuns=true`
- No new UI components — uses existing `Dashboards` page +
  `DashboardRenderer` + `DashboardTile`
- Agentic dashboard = `template_agentic.json` pre-seeded via existing
  `InstantPreviewDashboardService`
- Every metric is a saved report
- Alerts free, discoverability free
- Period delta badges not deliverable, pixel-perfect PNG match not
  deliverable

---

## Concrete walk-through — 8 Phase 1 metrics through the Report infrastructure

This section grounds the V3 / hybrid argument in concrete code. For each
metric, the table shows the exact `view + groupBy + aggregations + filters`
combination, what already exists, and what needs to be added.

### The 8 metrics

| # | Metric | Original status |
|---|---|---|
| 25 | Total runs | Exists |
| 27 | Avg / median tokens per run | New |
| 28 | Token outlier bands (P5 / P95) | New |
| 29 | Token trend over time | New |
| 30 | Avg / P50 / P95 duration | Exists |
| 31 | Duration trend | Exists |
| 32 | Incident / failure rate (per version) | Exists |
| 33 | Tool call frequency / distribution | Exists |

### 5 "Exists" metrics — what's missing to make them agentic?

All five already work as standard Optimize process reports. The only thing
preventing them from being valid Agentic Control Plane reports today is
that **none of them can be scoped to "only process instances that contain
at least one agent run"**. Two additional small pieces are needed,
**both reusable across all 8 metrics**:

#### Common addition for all 5: `HasAgentInstancesQueryFilter`

A new filter type that filters to process instances with at least one
nested `agentInstances` entry. Implementation:

- `HasAgentInstancesFilterDataDto` in `optimize-commons/.../filter/data/`
- `HasAgentInstancesFilterDto` (wrapper + Jackson subtype)
- `HasAgentInstancesQueryFilterES` (nested-exists query body)
- `HasAgentInstancesQueryFilterOS` (OpenSearch equivalent)
- Register in `ProcessQueryFilterEnhancerES` + `OS`

Once added, **every** existing process report can opt into "agentic only"
by including this filter. The same filter also powers the
`GET /api/process-definition?hasAgentRuns=true` extension that scopes the
process dropdown.

**Effort: ~3 days (ES + OS + Jackson).**

#### Metric-by-metric breakdown for the 5 "exists"

| # | Metric | View | GroupBy | Aggregations | Filters | Net-new for ACP |
|---|---|---|---|---|---|---|
| 25 | Total runs | `PROCESS_INSTANCE / FREQUENCY` | `NONE` | count (built-in) | completed + dateRange + **hasAgentInstances** | **filter only** |
| 30 | Avg / P50 / P95 duration | `PROCESS_INSTANCE / DURATION` | `NONE` | `{AVERAGE, PERCENTILE(50), PERCENTILE(95)}` (multi-agg via `Set<AggregationDto>`, supported today) | same | **filter only** |
| 31 | Duration trend | `PROCESS_INSTANCE / DURATION` | `PROCESS_INSTANCE_START_DATE` (date histogram, exists) | `{PERCENTILE(50), PERCENTILE(95)}` (or `AVERAGE`) | same | **filter only** |
| 32 | Incident / failure rate (overall) | `PROCESS_INSTANCE / PERCENTAGE` (exists, computes `filteredCount / totalCount × 100` per `ProcessViewInstancePercentageInterpreterES.java:55`) | `NONE` | percentage (built-in) | completed + dateRange + **hasAgentInstances** + `OpenIncidentFilter` OR `ResolvedIncidentFilter` (all exist) | **filter only** |
| 32 | Incident rate per version | same view | **PROCESS_DEFINITION_VERSION groupBy (new)** | percentage per bucket | same | **filter + new groupBy** |
| 33 | Tool call frequency (BPMN heatmap interpretation) | `FLOW_NODE / FREQUENCY` (exists) | `FLOW_NODE` (exists) | count (built-in) | same | **filter only** |

**Net-new infrastructure across all 5 existing metrics**: ONE filter +
ONE groupBy (`PROCESS_DEFINITION_VERSION`). The `PROCESS_DEFINITION_VERSION`
groupBy is a single terms-aggregation interpreter on the existing
`processDefinitionVersion` parent field — small, follows the same pattern
as `PROCESS_GROUP_BY_PROCESS_INSTANCE_START_DATE`.

**Combined effort for "exists" metrics**: ~6 days (filter + groupBy + ES + OS + integration tests).

### 3 "New" metrics — implementable as hard-coded reports inside the existing Report infrastructure

The user's question: can these three be done **inside** the report
infrastructure (as saved reports), rather than as a parallel branch?

Yes. All three share **one** new piece of infrastructure: a view interpreter
that aggregates over a denormalized token field. Once it exists, the three
metrics differ only by groupBy and aggregation choice — pure JSON
configuration in `template_agentic.json`.

#### Single shared addition: token view interpreter

- `agentTotalTokens` parent-level denormalized `long` field (= sum of
  `metrics.inputTokens + metrics.outputTokens` across all nested
  `agentInstances`, recomputed in the Painless update script on every
  import event)
- `ProcessViewEntity.AGENT_INSTANCE` enum value (or reuse
  `PROCESS_INSTANCE` if conceptually preferred — the field lives on the
  process instance document)
- `ViewProperty.TOTAL_TOKENS` constant
- `ProcessView.PROCESS_VIEW_AGENT_TOTAL_TOKENS` enum entry
- `ProcessViewAgentTotalTokensInterpreterES` + `OS`, extending
  `AbstractProcessViewMultiAggregationInterpreterES/OS`. This is a single
  small class per backend; the base classes already provide all the
  aggregation strategy plumbing (AVG, SUM, MIN, MAX, PERCENTILE).

#### Metric-by-metric breakdown for the 3 "new"

| # | Metric | View | GroupBy | Aggregations | Filters | Net-new for ACP |
|---|---|---|---|---|---|---|
| 27 | Avg / median tokens per run | `AGENT_INSTANCE / TOTAL_TOKENS` (new) | `NONE` | `{AVERAGE, PERCENTILE(50)}` | completed + dateRange + hasAgentInstances | **filter + view** |
| 28 | Token outlier bands (P5/P95) | `AGENT_INSTANCE / TOTAL_TOKENS` (same) | `NONE` (KPI) or `START_DATE` (over time) | `{PERCENTILE(5), PERCENTILE(95)}` | same | **filter + view (shared with #27)** |
| 29 | Token trend over time | `AGENT_INSTANCE / TOTAL_TOKENS` (same) | `PROCESS_INSTANCE_START_DATE` (date histogram, exists) | `SUM` (total per bucket) or `PERCENTILE` (distribution per bucket) | same | **filter + view (shared with #27)** |

**Net-new infrastructure across all 3 new metrics**: ONE view property
+ ONE view interpreter + ONE denormalized field. Everything else is
saved-report configuration in JSON.

**Combined effort for "new" metrics**: ~5 days (view interpreter ES + OS + denormalized
field + Painless update + integration tests).

### Aggregate net-new infrastructure for ALL 8 metrics

| Addition | Lines of code estimate | Days |
|---|---|---|
| `HasAgentInstancesQueryFilter` + DTO + Jackson + ES/OS + registration | ~120 | 3 |
| `PROCESS_GROUP_BY_PROCESS_DEFINITION_VERSION` groupBy + DTO + ES/OS interpreters | ~150 | 3 |
| `AGENT_INSTANCE` view entity + `TOTAL_TOKENS` view property + `ProcessView` enum entry | ~30 | 1 |
| `ProcessViewAgentTotalTokensInterpreterES` + `OS` | ~80 | 3 |
| `agentTotalTokens` parent denormalized field + Painless update | ~40 | 2 |
| Integration tests (ES + OS) for new pieces | n/a | 5 |
| Regression sweep across existing reports | n/a | 3 |
| **Subtotal: shared infrastructure** | **~420 LOC** | **~20 days** |
| `template_agentic.json` (8 saved-report configs + dashboard layout) | JSON only | 5 |
| Wire into `InstantPreviewDashboardService` startup | minimal | 1 |
| Validate against ES + OS deserialization | — | 2 |
| Frontend `hasAgentRuns=true` tweak on process selector | ~5 LOC | 1 |
| Validate dashboard rendering on real data | — | 3 |
| **Subtotal: template + frontend tweak** | **JSON + ~5 LOC** | **~12 days** |
| **Grand total (excluding Layer 1 import pipeline)** | **~420 LOC + JSON** | **~32 days** |

Layer 1 (import pipeline including `agentTotalTokens` denormalization) is
~18 days. **End-to-end Phase 1 effort for V3 with this 8-metric scope:
~50 days.**

> Earlier V3 estimate (~81 days) covered the broader original-spec scope,
> including `/process-breakdown`, the `avgTokensPerCall` ratio-of-sums
> custom interpreter, `PROCESS_DEFINITION_KEY` groupBy, and separate
> input/output token view properties. Narrowing scope to these 8 metrics
> drops ~30 days of work.

### What this means for the architectural argument

The user's framing — "hard-coded reports inside the existing Report
infrastructure" — describes exactly this approach:

- **No parallel code branch.** Every Phase 1 metric runs through the
  same `ReportEvaluationHandler` that powers every other report in
  Optimize.
- **Single source of truth.** Filters, views, groupBys live in shared
  facades. When Optimize improves tenant handling, query optimization,
  caching, or alerting, the agentic dashboard improves with it.
- **Users get familiar capabilities for free.** Every tile is alertable
  (existing `AlertJob`), shareable (existing share-link mechanism),
  exportable (existing CSV export), embeddable (existing embed feature).
  No documentation work to explain "why this dashboard is different from
  other dashboards."
- **Mid/long-term flexibility ratchets up.** Today the agentic tiles are
  hard-coded in `template_agentic.json`; later, exposing the new
  `ViewProperty.TOTAL_TOKENS` in the report builder UI lets users author
  custom token reports themselves. Same code path; UX surface gradually
  expands.

### Caveat: what does not fit

Three things from the original spec do **not** fit this model:

1. **Period delta badges** — standard report widgets show one period.
   Operators read absolute numbers and change date ranges manually for
   before/after comparison. Acceptable for V3; not acceptable for V1/V2.
2. **`avgTokensPerCall` ratio-of-sums per process** — requires a small
   custom view interpreter (~3 days, follows
   `ProcessViewInstancePercentageInterpreter` pattern). Not in the user's
   8-metric list, so out of scope here.
3. **Pixel-perfect PNG layout** — standard dashboard grid is approximate.
   Out of scope here.

---

## Effort comparison (from scratch)

All three variants share Layer 1 (import pipeline). Layer 1 differs only
in whether the `agentTotalTokens` denormalized parent field is added (V2
and V3 require it; V1 does not but can opt-in for simpler scripted
percentile).

| Phase | Variant 1 | Variant 2 (Hybrid) | Variant 3 |
|---|---|---|---|
| Layer 1 — Import pipeline | 17 days | 18 days (+1 for `agentTotalTokens`) | 18 days |
| Layer 2 — Shared infrastructure | 0 | 38 days | 40 days |
| Layer 2 — Custom endpoints | 45 days | 31 days (lighter — composes on infra) | 0 |
| Layer 2 — Saved-report template | 0 | 11 days | 12 days |
| Layer 3 — Custom dashboard frontend | 37 days | 37 days | 0 |
| Layer 3 — Saved-report frontend tweaks | 0 | 4 days | 4 days |
| Validation | 8 days | 10 days | 7 days |
| **Total** | **~107 days** | **~149 days** | **~81 days** |
| Code delta | ~2,000+ LOC custom backend + frontend | ~2,500 LOC + JSON template | ~700 LOC + JSON template |
| Per-request ES query count for `/summary` | 2 (parallel) | 2 (parallel — custom path retained) | ~14 (one per tile, paralleled) |
| Risk of regression in existing reports | None | Medium (shared infra changes) | Medium (same) |

### Effort drivers

- **V3 saves most on frontend** (~37 days) by reusing the standard
  dashboard renderer; **adds back ~12 days on shared infrastructure** and
  ~12 days on template authoring. Net savings vs V1: ~26 days.
- **V2 spends the most** because it ships **both** the custom UI **and**
  the saved-report companion. ~42 days more than V1 because it builds the
  shared infrastructure that V1 skips, plus the saved-report template
  that V1 doesn't have.
- **V1 is cheapest if you only count days**, but locks you into a
  parallel code path with no alert support.

### Risk-adjusted view

| Variant | Best case | Expected | Worst case (regressions or scope changes) |
|---|---|---|---|
| V1 | 95 days | 107 days | 120 days |
| V2 | 130 days | 149 days | 175 days |
| V3 | 70 days | 81 days | 105 days |

V2's wider distribution reflects the fact that it depends on shared
infrastructure landing cleanly AND custom UI shipping AND saved-report
template authoring — three things in series.

---

## Capabilities matrix

| Capability | V1 | V2 | V3 |
|---|---|---|---|
| Pixel-perfect PNG match | ✅ | ✅ | ❌ |
| Period delta badges | ✅ | ✅ | ❌ |
| Alerts on agentic KPIs | ❌ | ✅ free | ✅ free |
| Discoverability in Dashboards list | ❌ | ✅ via companion | ✅ |
| Operator-owned customization (clone reports, change filters) | ❌ | ✅ via companion | ✅ |
| Lowest total effort | ❌ | ❌ | ✅ |
| Zero risk to other Optimize reports | ✅ | ❌ | ❌ |
| Single shared backend code path | ❌ — parallel | ✅ — one infra | ✅ — one infra |
| `/summary` latency at scale | Fast (2 queries) | Fast (2 queries — custom path retained) | Slower (~14 queries paralleled) |
| Phase 2 per-tool breakdown extensibility | Custom per metric | New tile + new interpreter | New tile + new interpreter |

---

## Decision drivers

| Hard requirement | Choose |
|---|---|
| Pixel-perfect PNG match | V1 or V2 |
| Period delta badges | V1 or V2 |
| Alerts on agentic KPIs (Phase 1 or expected in Phase 2) | V2 or V3 |
| Single shared backend code path (long-term maintenance) | V2 or V3 |
| Lowest effort / fastest ship | V3 |
| Zero risk to other Optimize subsystems | V1 |
| All of the above | V2 |
| Compromise on UX in exchange for speed and alerts | V3 |
| Compromise on alerts in exchange for risk isolation | V1 |

---

## Recommendation

**Variant 2 (Hybrid)** is the best choice when:
- The product team requires pixel-perfect UI fidelity and period deltas
- Alert support is a Phase 1 requirement or expected by Phase 2
- Effort budget can absorb ~150 days
- Long-term codebase consolidation matters (single shared
  infrastructure for all process-instance analytical queries)

**Variant 3 (Full Reuse)** is the right choice when:
- The product team accepts standard Optimize dashboard UX
- Period deltas are negotiable (operators read absolute numbers, change
  date range manually for comparison)
- Ship date matters
- Alerts are valuable from day one

**Variant 1 (No Reuse)** is the right choice when:
- Custom UI must ship fast (no shared infrastructure delay)
- Other Optimize teams cannot absorb regression risk on shared report
  infrastructure now
- Alerts are explicitly deferred to Phase 2+
- Maintaining a parallel code path for agentic queries is acceptable

The author's recommendation is **Variant 2** if the effort budget exists,
otherwise **Variant 3**. Variant 1 ships fastest among the "pixel-perfect
UI" options but creates a parallel code path that will be expensive to
consolidate later when alert support inevitably becomes a requirement.

---

## Open questions (any variant)

1. **Period deltas**: hard requirement, or nice-to-have? If nice-to-have,
   V3 becomes much more attractive.
2. **Alert priority**: Phase 1 or Phase 2+? If Phase 1, V1 is off the
   table.
3. **PNG fidelity tolerance**: pixel-perfect or approximate? If
   approximate is acceptable, V3 becomes attractive.
4. **Shared infrastructure regression budget**: do other Optimize teams
   have bandwidth to handle regression risk on shared report
   infrastructure? If no, V1 is the safer path.
5. **Phase 2 per-tool breakdown timing**: if Phase 2 is far in the future,
   V1's parallel code path is less painful; if Phase 2 is near-term,
   V2/V3 amortize the shared infrastructure cost across more features.