# Report builder UX — a question-shaped, report-first design

Status: accepted direction (Metabase-style), 2026-07-06. Builds on ADR 0005/0006.

## Problem

The first builder UIs exposed the internal `DatasetDeclaration` verbatim — *source fact*, *meters*
with a raw *params* editor, *dimensions* typed `STRING`/`LONG`, *window tiers*, *lateness (ms)*,
`AGGREGATED`/`TABLE` kind, *enrichment timing*. That is the engine's model, not a user's question.
Nobody outside the team can read it.

## Direction

**Report-first, question-shaped, in business language** (like Metabase / Mixpanel). The user answers
one sentence; the system compiles it into a `DatasetDeclaration`, find-or-provisions the cube, and
creates the report. The raw declaration builder is demoted to an **Advanced** surface.

```
Measure [Average duration ▾] of [Process instances ▾]
Group by [Process ▾]  [+ Region (variable) ]
Over [Last 30 days ▾] by [Day ▾]
Where [Process] is [Invoice]        [+ filter]
Show as ( # )( line )( bar )( table )
▸ Advanced (variable timing, SLA target, windows)
```

The scary knobs move into a **semantic layer** (the measure catalog) the backend owns.

## Semantic layer — the measure catalog (backend, single source of truth)

Each **entity** (friendly name for a `FactType`) offers a curated set of **measures** (friendly name
for a meter template) and **group-bys** (friendly name for a dimension). A measure carries the
engine mapping so the client never sees it.

|                 Entity                 |                                                                                                                                       Measures (friendly → engine)                                                                                                                                        |                              Group-bys                               |
|----------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Process instances (`PROCESS_INSTANCE`) | Number of instances→`count`; Average duration→`execution_time_summary(durationMs)`; Duration percentile→`percentile(durationMs)` (param: percentile); % within SLA→`ratio(durationMs, le, target)` (param: SLA target); Currently running→`level`; Distinct process definitions→`distinct(bpmnProcessId)` | Process (`bpmnProcessId`), Version, Tenant, Variable… (`var.<name>`) |
| Flow nodes / tasks (`ELEMENT`)         | Times executed→`count`; Average duration→`execution_time_summary(durationMs)`; Duration percentile→`percentile(durationMs)`                                                                                                                                                                               | Process, Flow node (`elementId`), Variable…                          |
| Incidents (`INCIDENT`)                 | Number of incidents→`count`; Currently open→`level(delta)`                                                                                                                                                                                                                                                | Process, Flow node                                                   |

Params surfaced in friendly terms: *Percentile* (default 95), *SLA target* (a duration, default 5
min). Enrichment timing for variable group-bys defaults to a sensible value and is only shown under
Advanced. Window tiers are derived from the offered granularities, never hand-entered.

## Compiling a question

`question → DatasetDeclaration`:
- `sourceFact` = entity's fact type.
- `meters` = the measure's template, with a **deterministic meter name** (e.g. `avg-duration` measure
→ meter `duration`) so the report can reference it.
- `dimensions` = group-bys (a variable group-by becomes `var.<name>` with the default enrichment).
- `filters` = the where-clauses (EQUALS today).
- `windowSizesMs` = the measure's default tiers (∪ the chosen granularity).

**Dataset reuse (dedup):** before provisioning, look for an existing dataset whose declaration equals
the derived one and reuse it; otherwise provision a new one. Prevents identical questions minting
duplicate cubes. (Full cube consolidation across *different* measures — ADR 0001's planner — is a
later optimization; declaration-equality dedup is the MVP.)

Then create a `ReportDefinition` with one source = {the dataset, the measure's meter(s)}, the shared
group-by, granularity, and viz.

## Endpoints

- `GET /api/measures` → the catalog: `{ entities: [{id,label,description, measures:[{id,label,
  description, unit?, param?}], groupBys:[{id,label,variable?}]}], granularities:[{ms,label}],
  visualizations:[...] }`.
- `POST /api/reports/from-question` → body `{ name, entity, measure, params:{}, groupBy:[{field,
  variable?}], filters:[{field,value}], granularityMs, viz }` → compile + find-or-provision dataset +
  create report → `201 { report }`. (Existing `GET /api/reports/{id}/data?fromMs&toMs` runs it.)
- Existing raw `POST /api/datasets` + the declaration builder stay for the **Advanced** surface.

## Screens

- **Reports** (primary): a gallery of **templates** ("Process throughput", "Cycle time p95",
  "Incident rate", "SLA compliance") + "Blank question"; the question-sentence builder with live
  preview (compiled-question summary + data once saved); the saved-reports list with Run.
- **Advanced → Datasets** (demoted, progressive disclosure): the current declaration builder,
  relabeled, for curating a reusable named dataset directly.

## Copy: rename engine terms in the UI

Fact→**Entity**, Meter→**Measure**, Dimension→**Group by / Attribute**, Params→(named per measure),
Window tier→(derived, hidden), Lateness→(Advanced), AGGREGATED/TABLE→(implied by Measure vs
"Detailed list" show-as), enrichment timing→**When to read the variable** (Advanced).
