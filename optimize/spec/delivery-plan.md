# Agentic Control Plane — Delivery Plan

**Strategy**: Layers run in parallel. Sherrin builds FE against mocks from day 1. No FE blocked on
BE. Each alpha ships a demo.

**SP sizing**: Fibonacci (1, 2, 3, 5). Max 5 SP per item — break down anything larger.
**Throughput**: Senior ~12–15 SP/iter · Mid ~10–13 SP/iter (2-week iteration, includes meetings,
reviews, incidents).

---

## Assignments

### Layer 1 — Alexandre (primary)

| Task | SP |
|---|---|
| T1 ProcessInstanceIndex bump + field defs | 5 |
| T2 Import service (AgentInstanceRecord) | 5 |
| T3 Painless upsert script | 5 |
| IT smoke (full pipeline) | 5 |
| **Total** | **20** |

### Layer 2 — Helene (primary)

| Task | SP |
|---|---|
| T4 Contracts + shared types | 3 |
| T5 Shared query utilities | 3 |
| T6 GET /summary | 5 |
| T7 GET /process-breakdown | 3 |
| T8 GET /trends | 3 |
| T9 GET /charts | 5 |
| T10 GET /process-definition extension | 5 |
| IT perf (nested aggs at scale) | 3 |
| **Total** | **30** |

### Layer 3 — Sherrin (primary)

| Task | SP |
|---|---|
| FE: API client + TS types + mocks | 3 |
| FE: filter context, date range, process selector | 3 |
| FE: KPI cards (runs / duration / incident + deltas) | 3 |
| FE: token stats (avg + median) | 1 |
| FE: duration stats P50/P95 + chart | 3 |
| FE: token trend + outlier bands | 3 |
| FE: top consumers chart | 2 |
| FE: tool call frequency chart | 2 |
| FE: avg tokens per call chart | 2 |
| FE: incident rate by version chart | 2 |
| FE: dashboard layout + routing + L0↔L1 | 3 |
| FE: i18n | 2 |
| FE: unit + component tests | 3 |
| FE: E2E tests | 5 |
| **Total** | **37** |

**Grand total**: 87 SP

---

## alpha1 — April 28 — PoC only, not delivered

T1–T6 exist as PoC code. Not production-ready. Plan starts from alpha2.

---

## alpha2 — May 26 (~8 working days)

Goal: index schema locked. Contracts defined. Mocked dashboard demo.

| Person | Work | SP |
|---|---|---|
| Alexandre | T1 — index bump, ES + OS mappings, field constants | 5 |
| Helene | T4 contracts + T5 shared utilities | 6 |
| Sherrin | FE API client/mocks + FE KPI cards + token stats (all mocked) | 7 |

**Demo**: Dashboard with KPI cards, token stats, delta badges — all mocked data. Enough to
validate UI direction with PM/design.

**Note**: T5 uses T1 constants. Alexandre and Helene align on field names day 1 to avoid churn.

---

## alpha3 — June 30 (~25 working days, ~2.5 iterations)

Goal: Import pipeline live. Core endpoints shipped. FE wired to real data for summary + trends.

| Person | Work | SP |
|---|---|---|
| Alexandre | T2 import service + T3 Painless script | 10 |
| Helene | T6 GET /summary + T7 GET /process-breakdown + T8 GET /trends | 11 |
| Sherrin | FE filter context/selector + duration stats + token trend + outlier bands + top consumers + dashboard layout + i18n | 16 |

**Demo**: Real agent run data flowing from Zeebe through import pipeline. KPI cards, token stats,
duration trend, token trend, top consumers — all wired to live backend. L0↔L1 switching works.
Process selector dropdown live (T10 still mocked in selector until alpha4).

**Sequencing**: T2 targets week 2 of alpha3 so real data exists for Helene's endpoint integration
tests. Sherrin wires FE to T6/T7/T8 as Helene ships them; mocks stay until then.

---

## alpha4 — July 28 (~20 working days, ~2 iterations)

Goal: All endpoints complete. FE fully wired. Tests passing.

| Person | Work | SP |
|---|---|---|
| Helene | T9 GET /charts + T10 process-definition extension + IT perf | 13 |
| Sherrin | FE tool freq + avg tokens + incident rate (wire T9) + unit/component tests + E2E | 14 |
| Alexandre | IT smoke (full pipeline: Zeebe → import → index → API → FE) | 5 |

**Demo**: Feature complete. All charts live with real data. E2E green. IT smoke passing. IT perf
validated.

**Sequencing**: T9 and T10 (Helene) target week 1–2 of alpha4 so Sherrin has time to wire FE and
run E2E.

**Risk**: T10 touches a shared endpoint — Helene coordinates with DefinitionRestService owners at
alpha3 end, not alpha4 start.

---

## alpha5 — August 25 — buffer

No committed work. Absorbs QA/PM feedback, edge case fixes, perf tuning from IT perf results.

---

## Cross-layer dependencies

```
T1 (Alexandre) ──→ T2 ──→ T3           (L1 sequential)
T1 (Alexandre) ──→ T5 (Helene)          (field constants — align day 1)
T5             ──→ T6, T7, T8, T9       (filter builder prerequisite)
T9             ──→ FE tool freq, avg tokens, incident rate  (Sherrin wires after T9 ships)
T10            ──→ FE process selector  (Sherrin wires after T10 ships)
T2 + T3        ──→ IT smoke             (Alexandre, after pipeline live)
T9             ──→ IT perf              (Helene, after T9 ships)
```
