# ADR 0006 — Reports over multiple datasets

- Status: Accepted
- Date: 2026-07-06
- Scope: `analytics/analytics-query` (report executor), `analytics/analytics-serving` (report spec
  store), `analytics/analytics-store-rdbms` + `analytics/analytics-store-document` (backends),
  `analytics/analytics-webapp` (REST + UI)
- Builds on: ADR 0001 (declarative datasets — Report as a logical view, Phase 5), ADR 0004 (cube
  read path — single-dataset planner/executor)

## Context

ADR 0001 framed a **Report** as a logical view over a pre-aggregated dataset, and ADR 0004 built the
read path for it: `DatasetQueryPlanner.plan(ReportQuery, CompiledDataset)` → `DatasetQueryExecutor
.execute(query, dataset)` → per-backend fetch. That path is **strictly one dataset per query** —
`QueryPlan` holds a single `CompiledDataset`, the executor signature takes one, and the backends fetch
one table / one index. There is no join or union across datasets anywhere.

The requirement now is a report that **references multiple datasets** — e.g. show process throughput
(from `process-instances`) next to open incidents (from `incident-open`) over the same processes and
time range, in one report. Two facts shape the design:

- The **read SPI is already one-fetch-per-dataset** (`DatasetQueryClient`), so multi-dataset can be
  composed *above* the SPI by orchestrating N single-dataset executions and combining their rows —
  no backend change for the common case.
- A **persisted "report" today is a toy**: the webapp's `Report` record has a single `datasetId` and
  a `runReport` that ignores it and hard-codes the `process-instances` cube. It stores to an
  unrelated H2 table, not the backend-neutral metadata plane.

## Decision

### 1. `ReportDefinition` + `ReportSpecStore` — reports are first-class, backend-neutral specs

A report is a durable spec in the **metadata plane**, mirroring `DatasetSpecStore` (not the webapp
H2), so it works across RDBMS / ES / OS:

```
ReportDefinition
  ├─ reportId
  ├─ name
  ├─ sources[]        one per referenced dataset:
  │     ├─ datasetName (or cubeId)
  │     ├─ meters[]     which of that dataset's meters to read
  │     └─ filters[]    dataset-local filters
  ├─ groupBy[]        shared group-by dimensions (⊆ every source's grain)
  ├─ fromMs / toMs / granularityMs   shared time window + tier
  ├─ combination      UNION (v1) | JOIN (deferred)
  └─ viz
```

`ReportSpecStore` (SPI) supports `create` / `read` / `search` / `delete`, with RDBMS (Liquibase
table) and document backends alongside the existing spec/meter-id stores. `MetadataStore` exposes it.

### 2. `ReportExecutor` — union above the single-dataset SPI (v1)

`analytics-query` gains a `ReportExecutor` that, for a `ReportDefinition`:

1. builds a per-source `ReportQuery` (shared `groupBy` / time / granularity + that source's meters +
   its filters),
2. runs the **existing** `DatasetQueryExecutor.execute(query, dataset)` once per source, and
3. **unions** the resulting `ReportRow`s on the shared `(dimensions, bucket)` key, **namespacing each
   meter by its dataset** (`dataset.meter`) so meters from different sources never collide.

Rows present in some sources but not others carry only the meters that produced them (a left/outer
union on the shared key). This reuses the whole ADR 0004 read path unchanged — tiering, pushdown,
streaming — per source; it only adds an app-side merge on the group key, which the executor already
does *within* a dataset.

### 3. Cross-grain JOIN — deferred

A report whose sources have **different grains** aligned on a shared dimension (e.g. per-tenant
distinct joined to per-process throughput) is a genuine join, not a union on a common key. The
backends cannot do it (single table / single index) and it needs an app-side join executor with a
projection to the shared dimension. Deferred behind UNION; the `combination` field reserves the seam.

## Rationale

- **Union above the SPI** is the smallest correct change: it reuses `DatasetQueryExecutor` verbatim
  and adds only a group-key merge the code already performs within one dataset. No planner, backend,
  or storage change for the case that covers most multi-dataset reports.
- **Reports in the metadata plane**, not webapp H2, keeps a report portable across backends and
  consistent with datasets being backend-neutral specs (ADR 0001) — and lets the webapp's `runReport`
  stub and its private H2 tables be retired.
- **Meter namespacing by dataset** is required the moment two sources can expose a same-named meter
  (e.g. `count`); doing it in the executor keeps the row model flat and unambiguous downstream.

## Consequences

- New `ReportDefinition` model + `ReportSpecStore` SPI + two backend impls; `MetadataStore` exposes
  it. A report is a data change, not a redeploy — same property datasets have.
- New `ReportExecutor` in `analytics-query`; the single-dataset `DatasetQueryExecutor`/planner are
  unchanged and remain the building block.
- Webapp report CRUD is rewired to `ReportSpecStore` + `ReportExecutor`; the hard-coded `runReport`
  and the `analytics_report` / `analytics_dataset` H2 tables are removed.
- Result rows carry `dataset.meter`-namespaced measures; the client renders per that convention.

## Revisit triggers

- A report that needs cross-grain alignment — promote the JOIN combination and build the app-side
  join executor.
- A source selection that cannot be answered by one dataset (needs the ADR 0001 tiered ad-hoc
  fallback to fact stream / base projection) — out of scope here.
- Report result volume large enough that per-source app-merge is a bottleneck — push the union into a
  backend view where sources share a table/index.

