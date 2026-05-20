# Agentic Control Plane — Implementation Plan (Nested Variant, v2)

**Supersedes**: `agentic-control-plane-impl-plan-nested.md`  
**Scope**: Phase 1 only — L0 (fleet) and L1 (single process). No L2, no A2, no A7.  
**Architecture**: `agentInstances` nested inside `ProcessInstanceIndex`.

---

## T-shirt sizes

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

---

## Phase 0 — Foundation (~1 day)

Sequential. Everything else depends on these.

|   #   |                                            Task                                             | Effort | AI Fit |                                                                                                                          Notes                                                                                                                          |
|-------|---------------------------------------------------------------------------------------------|--------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-0.1 | `AgentInstanceDto` + sub-DTOs (`AgentDefinitionDto`, `AgentMetricsDto`, `AgentToolDto`)     | XS     | ✅      | Use `IncidentDto` as pattern reference                                                                                                                                                                                                                  |
| N-0.2 | Add `List<AgentInstanceDto> agentInstances` to `ProcessInstanceDto`                         | XS     | ✅      | One field addition; follows `incidents` field pattern                                                                                                                                                                                                   |
| N-0.3 | `ProcessInstanceIndex` VERSION 8 → 9: `agentInstances` nested mapping + parent token fields | S      | ❌      | **Critical**: wrong `.nested()` vs `.object()` type on first write cannot be fixed without a full reindex. Assert mapping type in an integration test before merge. Include all fields (Phase 1 + Phase 2 reserved) now to avoid a future VERSION bump. |

---

## Phase 1 — Import Pipeline (~1 week)

### Import scope

Import **CREATED** and **COMPLETED** events only for Phase 1.

- `CREATED` → initialises the nested document; sets `creationDate`, `definition`, `limits`, `metrics` baseline.
- `COMPLETED` → terminal state; sets `completionDate`, `durationInMs`, final metric totals, status `COMPLETED`.

> `UPDATED` is not imported in Phase 1 — `COMPLETED` carries final accumulated totals. Add
> `UPDATED` in Phase 2 when intermediate state visibility is introduced.

### Incident approach

Incident Rate (A6) and the Incident Rate KPI in A3 are **process-level metrics**. They use the
existing process incident data already present in `ProcessInstanceIndex` — no join against the
AgentInstance record, no dependency on `FAILED` status. This is the same incident data used by
existing Optimize process reports.

|   #   |                                      Task                                      | Effort | AI Fit |                                                                                                                                         Notes                                                                                                                                          |
|-------|--------------------------------------------------------------------------------|--------|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-1.1 | Painless upsert + re-aggregation script (`createUpdateAgentInstancesScript()`) | M      | ❌      | **Highest risk task.** Handles CREATED (insert new) and COMPLETED (update status/dates/metrics). Null guards required throughout. `'COMPLETED'.equals(ai.status)` for string compare in Painless. Validate against a real ES/OS instance before merge. Do not rely on AI output alone. |
| N-1.2 | Wire script into `createProcessInstanceUpdateScript()`                         | XS     | ✅      | One-line append                                                                                                                                                                                                                                                                        |
| N-1.3 | `ZeebeAgentInstanceFetcher`                                                    | XS     | ✅      | Structural copy of `ZeebeIncidentFetcher`; subscribes to CREATED + COMPLETED intents                                                                                                                                                                                                   |
| N-1.4 | `ZeebeAgentInstanceImportService` (CREATED + COMPLETED)                        | S      | ⚠️     | Use `ZeebeIncidentImportService` as reference. Key mapping: `creationDate` from CREATED timestamp; `completionDate` + `durationInMs` from COMPLETED.                                                                                                                                   |
| N-1.5 | `ZeebeAgentInstanceImportHandler`                                              | XS     | ✅      | Structural copy                                                                                                                                                                                                                                                                        |
| N-1.6 | `ZeebeAgentInstanceImportMediator` + Factory                                   | XS     | ✅      | Structural copy                                                                                                                                                                                                                                                                        |
| N-1.7 | Spring registration                                                            | XS     | ✅      | Alongside `ZeebeIncidentImportMediatorFactory`                                                                                                                                                                                                                                         |
| N-1.8 | Import pipeline integration tests                                              | M      | ⚠️     | Verify: CREATED inserts nested document; COMPLETED sets final metric totals + completionDate; parent `agentTotalInputTokens`/`agentTotalOutputTokens` re-aggregated correctly; `durationInMs` derived correctly. **Painless bugs only surface here.**                                  |

---

## Phase 2 — Backend API (~1 week)

All endpoints L0/L1 only. All independent after N-2.1. **Parallelize freely.**

All queries restrict to `state = "COMPLETED"` at the parent process instance level. All include
`tenantId` filter — omitting it is a security regression.

|   #    |                          Task                           | Effort | AI Fit |                                                                                                                                                   Notes                                                                                                                                                   |
|--------|---------------------------------------------------------|--------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-2.1  | Controller skeleton + `AgenticControlPlaneFilterParams` | XS     | ✅      | Use an existing Optimize controller as reference                                                                                                                                                                                                                                                          |
| N-2.2  | A1 — Process Breakdown (L0)                             | S      | ✅      | Uses `agentTotalInputTokens` + `agentTotalOutputTokens` parent fields. Response includes process names — requires a lookup/join against the process definition index for display names; use or extend the existing process definition name resolution pattern in Optimize.                                |
| N-2.3  | A3 — Summary KPIs (L0/L1)                               | S      | ⚠️     | WoW parallel query + `medianTokens` Painless percentile at parent level. Incident Rate KPI in A3 uses `count(status = FAILED)` via nested agg — **dependent on G7**; implement with feature flag or skip field until G7 confirmed.                                                                        |
| N-2.4  | A4 — Token Trend (L0/L1)                                | M      | ⚠️     | Two-step: (1) top-5 `elementId`s by token sum, (2) five parallel date histograms per `elementId`. "Other" = parent-field total minus top-5 sum. Multi-step orchestration; review result assembly.                                                                                                         |
| N-2.5  | A5 — Duration Stats (L0/L1)                             | S      | ✅      | Process `duration` field at parent level. Percentile aggs only; no nested query.                                                                                                                                                                                                                          |
| N-2.6  | A6 — Incident Rate (L0/L1)                              | S      | ✅      | Process-level: incidents on the process instance (using existing incident data in `ProcessInstanceIndex`). No nested AgentInstance query needed; same source as existing Optimize incident reporting.                                                                                                     |
| N-2.7  | A8 — Token Outlier Bands (L0/L1)                        | S      | ✅      | Painless percentile on parent-level `agentTotalInputTokens + agentTotalOutputTokens`. No nested query needed.                                                                                                                                                                                             |
| N-2.8  | A9 — Avg Tokens per Agent Call (L1 only)                | S      | ✅      | `terms` agg on `agentInstances.elementId` + `bucket_script`: `(sumInputTokens + sumOutputTokens) / sumModelCalls`. Guard `modelCalls = 0`. Only visible at L1 (process selected); not shown at L0. **Re-included**: data is fully available from the record; deferral in the original plan was incorrect. |
| N-2.9  | A10 — Failure Rate by Version (L1 only)                 | S      | ✅      | Group by `processDefinitionVersion` at the parent process instance level (already in `ProcessInstanceIndex`). Incident count from existing process incident data. No nested AgentInstance query needed.                                                                                                   |
| N-2.10 | API integration tests                                   | M      | ⚠️     | Cover L0/L1 per endpoint; WoW delta; empty state (no agent data); correct denominator for A6 and A10.                                                                                                                                                                                                     |

> **Tool call frequency**: the designs show per-tool breakdown by name. The AgentInstance record
> provides only aggregate `toolCalls` count in Phase 1. Any "Tool call frequency" chart must be
> implemented as **total calls per `elementId`** (one bar per agent type), not per individual tool
> name. Per-tool detail requires history export and G6 — Phase 2.

---

## Phase 3 — Frontend (~1 week)

**Location**: `optimize/client/src/components/AgenticControlPlane/`

Filter state in Phase 1 is `{ processDefinitionKey?, dateRange }`. Selecting a process moves from
L0 to L1. No L2, no agent selector.

All components independent after N-3.1. **Parallelize freely.**

|   #    |               Task                | Effort | AI Fit |                                                                                                           Notes                                                                                                           |
|--------|-----------------------------------|--------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| N-3.1  | `AgentFilterContext` (L0/L1 only) | S      | ✅      | Simpler than original — only `processDefinitionKey` and `dateRange`; no L2 level derivation                                                                                                                               |
| N-3.2  | `ProcessSelector`                 | XS     | ✅      | Populated from A1 response (top token consumers). If the selector must show all processes rather than just top consumers, a separate unranked process-list endpoint is needed — clarify with product before implementing. |
| N-3.3  | `SummaryKPIs`                     | XS     | ✅      | KPI cards; WoW delta. Incident Rate card is fully implementable in Phase 1 using process incident data.                                                                                                                   |
| N-3.4  | `TokenTrendChart`                 | S      | ⚠️     | Multi-line only (top-5 `elementId`s + "Other" series). No single-line L2 switch. Most complex frontend component — review "Other" series construction.                                                                    |
| N-3.5  | `TokenOutlierBands`               | XS     | ✅      | p5/p50/p95 area chart on parent-level token total                                                                                                                                                                         |
| N-3.6  | `DurationStats`                   | XS     | ✅      | Process duration only; no label swap or `durationScope` toggle                                                                                                                                                            |
| N-3.7  | `IncidentRateKPI`                 | XS     | ✅      | Fully implementable in Phase 1; uses process incident data.                                                                                                                                                               |
| N-3.8  | `AvgTokensPerAgentCall`           | XS     | ✅      | Bar chart per `elementId`; visible at L1 only                                                                                                                                                                             |
| N-3.9  | `FailureRateByVersion`            | XS     | ✅      | Bar chart; L1 only.                                                                                                                                                                                                       |
| N-3.10 | `ControlPlaneDashboard` layout    | S      | ✅      | L0/L1 conditional rendering; visibility matrix below                                                                                                                                                                      |
| N-3.11 | Frontend E2E tests                | L      | ⚠️     | Golden path L0 → L1; WoW delta; empty state; G7-gated components in disabled state                                                                                                                                        |

### Visibility matrix

|              Component              | L0 | L1 |
|-------------------------------------|----|----|
| Summary KPIs — A3                   | ✅  | ✅  |
| Top token consumers by process — A1 | ✅  | —  |
| Token trend — A4                    | ✅  | ✅  |
| Token outlier bands — A8            | ✅  | ✅  |
| Duration stats — A5                 | ✅  | ✅  |
| Incident rate — A6                  | ✅  | ✅  |
| Avg tokens per agent call — A9      | —  | ✅  |
| Failure rate by version — A10       | —  | ✅  |

### Deferred to Phase 2

```
AgentSelector           (A2 — pending agent definition)
AgentsList              (A7 — pending agent definition)
Tool call per-tool view (G6 + history export)
Settings screen         (not yet in any spec — scope decision required before Phase 2)
AgentFilterContext L2
```

---

## Summary

|   Phase   |     Effort     |                        Critical path item                         |
|-----------|----------------|-------------------------------------------------------------------|
| Phase 0   | ~1 day         | N-0.3 — mapping type correctness, assert in test before merge     |
| Phase 1   | ~1 week        | N-1.1 — Painless handles CREATED + COMPLETED; validate on real ES |
| Phase 2   | ~1 week        | N-2.4 — Token Trend two-step orchestration                        |
| Phase 3   | ~1 week        | N-3.4 — Token Trend chart "Other" series construction             |
| **Total** | **~3.5 weeks** |                                                                   |

**Critical path**: N-0.3 → N-1.1 *(human validation required)* → N-1.8 *(real ES infra)* → N-2.1 → [parallel endpoints] → N-2.10 *(infra)* → N-3.1 → [parallel components] → N-3.11

**Tasks requiring human ownership** (AI output insufficient alone):

| Task  |                                           Reason                                           |
|-------|--------------------------------------------------------------------------------------------|
| N-0.3 | Wrong mapping type on first write requires full reindex to fix                             |
| N-1.1 | Painless correctness; subtle null handling and array mutation bugs only surface on real ES |
| N-1.8 | Only place Painless bugs surface; must run against real ES                                 |

**No external blockers.** All Phase 1 data is available in `ProcessInstanceIndex` and the AgentInstance record (CREATED + COMPLETED). Implementation can start immediately.
