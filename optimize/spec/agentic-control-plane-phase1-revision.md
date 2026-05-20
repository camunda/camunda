# Agentic Control Plane — Phase 1 Revision

**Status**: Revision  
**Based on**: `agentic-control-plane-technical-spec.md` (nested variant)  
**Do not modify originals** — this document supersedes the nested spec for Phase 1 scope.

---

## Decision Log

|     Decision     |                                                       Resolution                                                        |
|------------------|-------------------------------------------------------------------------------------------------------------------------|
| Architecture     | **Nested index** — `agentInstances` nested field inside `ProcessInstanceIndex`. Confirmed by project alignment.         |
| Phase 1 scope    | **Process-level dashboard only** — L0 (fleet) and L1 (single process). No agent-level filter (L2). No Agents table/tab. |
| Metrics baseline | **Completed process instances only** (`state = "COMPLETED"`).                                                           |
| Incident scope   | **Process-level** — any incident in the process. No agent-element scoping in Phase 1.                                   |
| Agent definition | Deferred — no agent-level filtering until a shared definition of "agent" is established across teams.                   |
| Frontend scope   | **Dashboard view only** — no agent details panel, no status badges.                                                     |

---

## Phase 1 Scope vs Original Spec

### Endpoints

| ID  |              Path              | Original Level |   Phase 1    |                              Notes                              |
|-----|--------------------------------|----------------|--------------|-----------------------------------------------------------------|
| A1  | `GET /process-breakdown`       | L0             | ✅ In         | Unchanged                                                       |
| A2  | `GET /agent-elements`          | L1             | ❌ Deferred   | No agent filter in Phase 1                                      |
| A3  | `GET /summary`                 | L0/L1/L2       | ✅ L0/L1 only | L2 variant deferred                                             |
| A4  | `GET /token-trend`             | L0/L1/L2       | ✅ L0/L1 only | L2 variant deferred; multi-line top-5 retained at L0/L1         |
| A5  | `GET /duration-stats`          | L0/L1/L2       | ✅ L0/L1 only | Process duration only; L2 agent duration deferred               |
| A6  | `GET /incident-rate`           | L0/L1/L2       | ✅ L0/L1 only | Process-level incidents only; L2 agent-element scoping deferred |
| A7  | `GET /agents`                  | L2             | ❌ Deferred   | Entire endpoint deferred                                        |
| A8  | `GET /token-outlier-bands`     | L0/L1/L2       | ✅ L0/L1 only | L2 variant deferred                                             |
| A9  | `GET /tokens-per-agent-call`   | L1/L2          | ❌ Deferred   | Requires agent-level understanding; deferred with L2            |
| A10 | `GET /failure-rate-by-version` | L1/L2          | ✅ L1 only    | L2 agent-scoped variant deferred                                |

### Frontend visibility matrix (Phase 1)

|           Chart / Component           | L0 | L1 |
|---------------------------------------|----|----|
| Summary KPIs (A3)                     | ✅  | ✅  |
| Top token consumers by process (A1)   | ✅  | —  |
| Token trend — multi-line top-5 (A4)   | ✅  | ✅  |
| Token outlier bands (A8)              | ✅  | ✅  |
| Duration P50/P95 KPIs (A5)            | ✅  | ✅  |
| Duration stability trend (A5)         | ✅  | ✅  |
| Incident rate (A6)                    | ✅  | ✅  |
| Failure rate by process version (A10) | —  | ✅  |
| Agent selector (A2)                   | —  | —  |
| Avg tokens per agent call (A9)        | —  | —  |
| Agents list (A7)                      | —  | —  |

---

## Layer 1 — Import Pipeline (Revised)

### What to import

Import **CREATED** and **COMPLETED** events only for Phase 1.

> **Open question (must resolve before implementation):** Does the COMPLETED event carry the
> full tool list? The spec states "all fields carried on every event once filled" but the
> COMPLETED example omits `tools`. If tools are absent on COMPLETED, the `tools.name` field
> in the mapping will never be populated in Phase 1. This does not block Phase 1 dashboard
> metrics but must be clarified before Phase 2 tool-level widgets are designed.

The UPDATED event is needed for:
- Intermediate status transitions (not shown in Phase 1 dashboard)
- Intermediate tool list updates (Phase 2)
- Metric delta accumulation — however, since COMPLETED carries final accumulated totals,
CREATED + COMPLETED is sufficient for all Phase 1 KPIs.

**Phase 1 import scope: CREATED + COMPLETED.**  
Add UPDATED when Phase 2 introduces intermediate state visibility.

### Index mapping changes (Phase 1)

`ProcessInstanceIndex` VERSION bump: **8 → 9**. Add:

```
// Parent-level pre-aggregated totals — maintained by Painless script
agentTotalInputTokens    long
agentTotalOutputTokens   long

// Nested agent instances — full schema included now for Phase 2 forward-compatibility
agentInstances           nested

    // --- Fields actively used in Phase 1 queries ---
    agentInstanceKey         keyword    // Painless merge key; exists check
    elementId                keyword    // A4 top-5 grouping
    status                   keyword    // Painless COMPLETED filter for token re-aggregation
    completionDate           date       // A4 date histogram axis
    metrics.inputTokens      long       // Painless accumulation; A4 per-element sum
    metrics.outputTokens     long       // Painless accumulation; A4 per-element sum
    metrics.modelCalls       integer    // A4 per-element sum (retained for completeness)
    metrics.toolCalls        integer    // A3 tool call KPI via nested agg

    // --- Fields reserved for Phase 2 (indexed now to avoid future VERSION bump) ---
    elementInstanceKey       keyword
    processDefinitionVersion integer    // A10 L2 agent-scoped failure rate
    versionTag               keyword
    tenantId                 keyword    // L2 tenant scoping inside nested context
    creationDate             date       // A7 Agents List sort
    lastUpdatedDate          date
    durationInMs             long       // A5 L2 agent duration; A7 display
    definition.model         keyword    // Phase 2 model-level grouping
    definition.provider      keyword
    tools.name               keyword    // Phase 2 tool list display
```

> **Note on mapping correctness (critical):** Use `.nested()` for `agentInstances` and
> `.object()` for `definition`, `metrics`, and `tools` sub-objects. Dot-notation in
> `.properties()` creates a literal dot-named field, not a sub-object. Wrong `.nested()`
> vs `.object()` on first write is hard to fix without a full reindex.

### Painless script (Phase 1)

Phase 1 script is simpler than the original spec — it must handle CREATED + COMPLETED only,
with no UPDATED events in Phase 1. The script still needs to handle both for forward-compatibility
when UPDATED is added in Phase 2.

```java
static String createUpdateAgentInstancesScript() {
    return
        // Initialise array if absent
        "if (ctx._source.agentInstances == null) { ctx._source.agentInstances = []; }\n" +

        // Upsert agent instances by agentInstanceKey
        "def existingKeys = new HashSet();\n" +
        "for (def ai : ctx._source.agentInstances) { existingKeys.add(ai.agentInstanceKey); }\n" +
        "for (def newAi : params.agentInstances) {\n" +
        "  if (!existingKeys.contains(newAi.agentInstanceKey)) {\n" +
        "    ctx._source.agentInstances.add(newAi);\n" +
        "  } else {\n" +
        "    for (def ai : ctx._source.agentInstances) {\n" +
        "      if (ai.agentInstanceKey == newAi.agentInstanceKey) {\n" +
        "        ai.status          = newAi.status;\n" +
        "        ai.completionDate  = newAi.completionDate;\n" +
        "        ai.lastUpdatedDate = newAi.lastUpdatedDate;\n" +
        "        ai.durationInMs    = newAi.durationInMs;\n" +
        "        ai.metrics         = newAi.metrics;\n" +
        "        if (newAi.tools != null) { ai.tools = newAi.tools; }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +

        // Re-aggregate parent-level token totals (COMPLETED agents only)
        "long totalIn = 0; long totalOut = 0;\n" +
        "for (def ai : ctx._source.agentInstances) {\n" +
        "  if ('COMPLETED'.equals(ai.status)) {\n" +
        "    if (ai.metrics != null) {\n" +
        "      if (ai.metrics.inputTokens  != null) { totalIn  += ai.metrics.inputTokens; }\n" +
        "      if (ai.metrics.outputTokens != null) { totalOut += ai.metrics.outputTokens; }\n" +
        "    }\n" +
        "  }\n" +
        "}\n" +
        "ctx._source.agentTotalInputTokens  = totalIn;\n" +
        "ctx._source.agentTotalOutputTokens = totalOut;\n";
}
```

> **Validation requirement:** This script must be validated against a real ES/OS instance
> before merging. Test all three intents (CREATED → UPDATED → COMPLETED) even if UPDATED
> is not imported in Phase 1 — Phase 2 will add it and the script must handle it correctly.

---

## Layer 2 — Backend API (Phase 1 L0/L1 only)

All queries restrict to `state = "COMPLETED"`. All queries include `tenantId` terms filter
(omitted from examples for brevity — omitting it is a security regression).

### A1 — Process Breakdown (L0, unchanged)

Uses `agentTotalInputTokens` and `agentTotalOutputTokens` parent fields. No change from
original spec query. See `agentic-control-plane-technical-spec.md` § 4.2.

### A3 — Summary KPIs (L0/L1 only)

L2 variant deferred. L0/L1 query unchanged from original spec § 4.4.

> **Correction vs original spec:** The `medianTokens` percentiles aggregation uses a
> Painless script at the parent level (`agentTotalInputTokens + agentTotalOutputTokens`).
> This is correct for L0/L1. The L2 variant that ran the same script inside a nested
> context (`doc['agentInstances.metrics.inputTokens']`) is incorrect and is deferred — the
> path qualification does not work in nested aggregation Painless context.

### A4 — Token Trend (L0/L1 only)

Multi-line (top-5 agents + "Other") retained at L0/L1. This still requires a nested
aggregation to group by `agentInstances.elementId`.

Step 1 (identify top-5 `elementId`s) and Step 2 (per-element date histograms) unchanged
from original spec § 4.5 L0/L1 queries.

"Other" = `agentTotalInputTokens`/`agentTotalOutputTokens` parent-field total minus sum
of top-5. Uses the pre-aggregated parent fields — no additional query needed.

L2 single-line variant deferred.

### A5 — Duration Stats (L0/L1 only, process duration)

Uses process `duration` field at parent level. Query unchanged from original spec § 4.6
L0/L1. L2 agent duration variant deferred.

### A6 — Incident Rate (L0/L1 only, process-level)

Process-level only: any incident in the process / total process instances with agents.
Query unchanged from original spec § 4.7 L0/L1.

L2 agent-element scoped variant (different denominator) deferred.

### A8 — Token Outlier Bands (L0/L1 only)

Uses `agentTotalInputTokens + agentTotalOutputTokens` Painless script at parent level.
Query unchanged from original spec § 4.9 L0/L1.

L2 nested variant deferred (and its Painless path qualification issue is therefore also
deferred — see inconsistency #6 in the audit).

### A10 — Failure Rate by Version (L1 only)

L1 process-scope query from original spec § 4.11 is unchanged. Groups by
`processDefinitionVersion` at parent level.

L2 agent-scoped variant (denominator = agent activations per version) deferred.

---

## Layer 3 — Frontend (Phase 1)

**Location**: `optimize/client/src/components/AgenticControlPlane/`

No L0 → L1 → L2 level derivation needed in Phase 1. Filter state is:
`{ processDefinitionKey?, dateRange }` — selecting a process triggers L0 → L1.

### Component structure (Phase 1)

```
AgenticControlPlane/
  index.tsx
  ControlPlaneDashboard.tsx
  components/
    ProcessSelector.tsx           uses A1 (process list with token totals)
    SummaryKPIs.tsx               uses A3
    TokenTrendChart.tsx           uses A4 (multi-line at L0/L1 only)
    TokenOutlierBands.tsx         uses A8
    DurationStats.tsx             uses A5
    IncidentRateKPI.tsx           uses A6
    FailureRateByVersion.tsx      uses A10 (L1 only)
  hooks/
    useProcessBreakdown.ts
    useSummaryKPIs.ts
    useTokenTrend.ts
    useTokenOutlierBands.ts
    useDurationStats.ts
    useIncidentRate.ts
    useFailureRateByVersion.ts
```

### Deferred to Phase 2

```
AgentSelector.tsx         (A2)
AgentsList.tsx            (A7)
AvgTokensPerAgentCall.tsx (A9)
useAgentElements.ts
useAvgTokensPerCall.ts
useAgentsList.ts
AgentFilterContext L2 level derivation
```

> **Note on ProcessSelector:** `ProcessSelector.tsx` uses the A1 response to populate a
> process picker. A1 returns a ranked list (top token consumers), not a plain process list.
> If the selector should show all processes (not just top token consumers), a separate
> lightweight process-list endpoint or an unranked A1 variant should be considered.

---

## Inconsistency Audit — Status Under Phase 1 Scope

| #  |                                  Description                                   |                                                                                               Phase 1 Status                                                                                               |
|----|--------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | `processDefinitionId` should be `bpmnProcessId` in CREATED example             | 🔴 **Still critical** — affects import pipeline field mapping                                                                                                                                              |
| 2  | `bpmnProcessId` missing from index mapping                                     | 🟡 **Low priority for Phase 1** — not queried at L0/L1; add to Phase 2 mapping                                                                                                                             |
| 3  | 4 declared nested field constants missing from `addProperties()` builder       | 🟡 **Reduced** — `tenantId`/`processDefinitionKey` inside nested not needed at L0/L1; `processDefinitionVersion` needed for A10 L2 (deferred); but include all fields now to avoid VERSION bump in Phase 2 |
| 4  | No `state = "COMPLETED"` equivalent on `AgentInstanceIndex` queries            | ✅ **Resolved** — nested approach; parent-level `state = "COMPLETED"` filter covers all nested content                                                                                                      |
| 5  | A7 cross-index join key mismatch (`processInstanceId` vs `processInstanceKey`) | ✅ **Deferred** with A7                                                                                                                                                                                     |
| 6  | Painless percentile path invalid inside nested context (A3 L2, A8 L2)          | ✅ **Deferred** with L2                                                                                                                                                                                     |
| 7  | A4 "Other" series undefined in separate index spec                             | ✅ **Resolved** — nested approach uses parent fields; well-defined                                                                                                                                          |
| 8  | A1 `agentActivations` missing from response shape                              | ✅ **Resolved** — separate index spec not used                                                                                                                                                              |
| 9  | A5 implicit `hasAgents` filter creates inconsistent denominator at L0          | 🟡 **Still relevant** — A5 L0/L1 filters to PIs with agents; A3 and A6 do not; denominators differ across widgets. Document this explicitly or align all L0 queries.                                       |
| 10 | Architecture decision ambiguous across spec files                              | ✅ **Resolved** — nested approach confirmed by project alignment                                                                                                                                            |
| 11 | A7 pagination diverges between specs                                           | ✅ **Deferred** with A7                                                                                                                                                                                     |
| 12 | `durationScope` semantics undocumented in L0/L1                                | 🟡 **Still relevant** — Phase 1 is process duration only; label should always read "Process duration"; remove `durationScope` from Phase 1 response or hard-code it                                        |
| 13 | `processInstanceId` / `processInstanceKey` mixed throughout                    | 🟡 **Still relevant** — affects import pipeline DTO and merge logic                                                                                                                                        |
| 14 | `systemPrompt` exclusion only documented in nested spec                        | ✅ **Resolved** — nested spec is authoritative                                                                                                                                                              |
| 15 | `ProcessSelector` maps to A1 (ranked list, not plain selector)                 | 🟡 **Still relevant** — see frontend note above                                                                                                                                                            |

---

## Revised Implementation Plan (Phase 1 only)

Estimates assume AI tooling. Human time = guidance, review, testing.

### Phase 0 — Foundation (~1 day)

|   #   |                                    Task                                    | Effort |   Risk   |                                              Notes                                               |
|-------|----------------------------------------------------------------------------|--------|----------|--------------------------------------------------------------------------------------------------|
| N-0.1 | `AgentInstanceDto` + sub-DTOs                                              | XS     | Low      | Follow `IncidentDto` pattern                                                                     |
| N-0.2 | Add `List<AgentInstanceDto> agentInstances` to `ProcessInstanceDto`        | XS     | Low      | One field, follows `incidents` pattern                                                           |
| N-0.3 | `ProcessInstanceIndex` VERSION 8 → 9: nested mapping + parent token fields | S      | **High** | `.nested()` vs `.object()` must be correct; validate mapping type assertion in test before merge |

### Phase 1 — Import Pipeline (~1 week)

|   #   |                             Task                             | Effort |   Risk   |                                                           Notes                                                            |
|-------|--------------------------------------------------------------|--------|----------|----------------------------------------------------------------------------------------------------------------------------|
| N-1.1 | Painless script (`createUpdateAgentInstancesScript()`)       | M      | **High** | Validate against real ES with CREATED + COMPLETED pair; verify token totals; do not merge without passing integration test |
| N-1.2 | Wire into `createProcessInstanceUpdateScript()`              | XS     | Low      | One-line append                                                                                                            |
| N-1.3 | `ZeebeAgentInstanceFetcher`                                  | XS     | Low      | Structural copy of `ZeebeIncidentFetcher`                                                                                  |
| N-1.4 | `ZeebeAgentInstanceImportService` (CREATED + COMPLETED only) | S      | Medium   | Review intent-to-field mapping: `creationDate` from CREATED timestamp, `completionDate`/`durationInMs` from COMPLETED      |
| N-1.5 | `ZeebeAgentInstanceImportHandler`                            | XS     | Low      | Structural copy                                                                                                            |
| N-1.6 | `ZeebeAgentInstanceImportMediator` + Factory                 | XS     | Low      | Structural copy                                                                                                            |
| N-1.7 | Spring registration                                          | XS     | Low      | Alongside `ZeebeIncidentImportMediatorFactory`                                                                             |
| N-1.8 | Import pipeline integration tests                            | M      | Medium   | Verify: token accumulation correct on COMPLETED, parent fields set, `durationInMs` derived correctly                       |

### Phase 2 — Backend API (~1 week, simplified)

All endpoints are L0/L1 only — no nested query complexity at this level.

|   #   |                          Task                           | Effort |  Risk  |                                 Notes                                 |
|-------|---------------------------------------------------------|--------|--------|-----------------------------------------------------------------------|
| N-2.1 | Controller skeleton + `AgenticControlPlaneFilterParams` | XS     | Low    |                                                                       |
| N-2.2 | A1 — Process Breakdown                                  | S      | Low    | Straightforward terms + parent field sums                             |
| N-2.3 | A3 — Summary KPIs (L0/L1 only)                          | S      | Medium | WoW parallel query; remove `durationScope` or hard-code `"process"`   |
| N-2.4 | A4 — Token Trend (L0/L1 only)                           | M      | Medium | Top-5 step + 5 parallel histograms + "Other" arithmetic; no L2 branch |
| N-2.5 | A5 — Duration Stats (L0/L1 only)                        | S      | Low    | Straightforward percentile; process `duration` only                   |
| N-2.6 | A6 — Incident Rate (L0/L1 only)                         | S      | Low    | Process-level; no agent-element filter                                |
| N-2.7 | A8 — Token Outlier Bands (L0/L1 only)                   | S      | Low    | Parent-level Painless percentile                                      |
| N-2.8 | A10 — Failure Rate by Version (L1 only)                 | S      | Low    | Simple terms + nested incidents per version                           |
| N-2.9 | API integration tests                                   | M      | Medium | L0/L1 coverage per endpoint; WoW delta; empty state                   |

### Phase 3 — Frontend (~1 week)

|   #    |                   Task                    | Effort |  Risk  |                                   Notes                                    |
|--------|-------------------------------------------|--------|--------|----------------------------------------------------------------------------|
| N-3.1  | `AgentFilterContext` (L0/L1 only — no L2) | S      | Low    | Simpler than original; only `processDefinitionKey` and `dateRange`         |
| N-3.2  | `ProcessSelector`                         | XS     | Low    | Populates from A1 — see note on ranked vs full list                        |
| N-3.3  | `SummaryKPIs`                             | XS     | Low    | KPI cards; WoW delta; no `durationScope` swap                              |
| N-3.4  | `TokenTrendChart`                         | S      | Medium | Multi-line only; "Other" series construction is most complex frontend task |
| N-3.5  | `TokenOutlierBands`                       | XS     | Low    | p5/p50/p95 area chart                                                      |
| N-3.6  | `DurationStats`                           | XS     | Low    | Process duration only; no label swap                                       |
| N-3.7  | `IncidentRateKPI`                         | XS     | Low    |                                                                            |
| N-3.8  | `FailureRateByVersion`                    | XS     | Low    | L1 only; bar chart                                                         |
| N-3.9  | `ControlPlaneDashboard` layout            | S      | Low    | L0/L1 conditional rendering                                                |
| N-3.10 | Frontend E2E tests                        | L      | Medium | Golden path L0 → L1; WoW delta; empty state                                |

### Summary

|   Phase   |     Effort     |                 Critical path item                 |
|-----------|----------------|----------------------------------------------------|
| Phase 0   | ~1 day         | N-0.3 — mapping type correctness                   |
| Phase 1   | ~1 week        | N-1.1 — Painless validation on real ES             |
| Phase 2   | ~1 week        | N-2.4 — Token Trend orchestration                  |
| Phase 3   | ~1 week        | N-3.4 — Token Trend chart                          |
| **Total** | **~3.5 weeks** | Reduced from ~4 weeks (no A2, A7, A9, L2 variants) |

**Highest-risk tasks requiring human ownership:**
1. **N-0.3** — mapping type; wrong type on first write requires reindex to fix
2. **N-1.1** — Painless script correctness; only surfaces in integration tests against real ES
3. **N-1.8** — integration tests; primary validation point for Painless

---

## Open Questions (must resolve before Phase 2)

|                                      Question                                       |                                           Impact                                            |
|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| Does COMPLETED carry the full tool list?                                            | Determines whether `tools.name` field is populated; affects Phase 2 tool widgets            |
| Definition of "agent" (AI Agent Task vs AHSP relationship)                          | Required before agent-level filtering (L2), Agents table, and status modeling               |
| A5 denominator alignment — should all L0 endpoints use the same `hasAgents` filter? | Currently A3/A6 include all completed PIs; A5 restricts to PIs with agents. Inconsistent.   |
| ProcessSelector data source — ranked (A1) or full process list?                     | Determines whether a separate process-list endpoint is needed                               |
| UPDATED event import timeline                                                       | Phase 2 will need it for intermediate status visibility; Painless script already handles it |

