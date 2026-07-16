# Analytics primer

Plain-language documentation of the analytics engine — what it computes, the math behind it,
and why that math is correct. Written as source material for presentations: every chapter is
self-contained, uses simple words, and carries ASCII visuals that translate directly to slides.

This is deliberately **not** an ADR set. Decisions and their alternatives live in
`analytics/docs/adr/`; this primer explains the system as it stands, plus the designed-but-not-built
roadmap, clearly labeled as such.

|                                   Chapter                                   |                                                                       Contents                                                                       |
|-----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| [01 — The pipeline](01-pipeline.md)                                         | How an event becomes a chart: stages, facts, cubes, event time, exactly-once, snapshots — and why queries stay fast at any volume                    |
| [02 — Meters](02-meters.md)                                                 | Every implemented meter: definition, arithmetic, and the proof that streaming/merging cannot corrupt it                                              |
| [03 — Datasets](03-datasets.md)                                             | All 21 standard datasets: the question each answers, its grain, and how its numbers are produced                                                     |
| [04 — Outliers, correlation, variants](04-outliers-correlation-variants.md) | The analysis layer: boxplot fences over sketches, lift, the variant signature — with derivations                                                     |
| [05 — The insight layer](05-variables-roadmap.md)                           | DESIGN, not yet built: profiler, guarded auto-promotion, stratified sampling, outlier census — two products: day-1 variables AND outlier explanation |
| [06 — Windows, slots, segments](06-windows-slots-segments.md)               | Stage-2 internals: event-time windows and tiers, composite meter slots (one writer per row), segment-stride — the exactly-once shuffle unit          |
| [07 — Correctness boundaries](07-correctness-boundaries.md)                 | Where determinism, idempotence, and equivalence are load-bearing: the boundary map, the mechanic at each hop, and what breaks without it             |

Reading order matters only for 01 → 02 → 03; chapters 04 and 05 stand alone.
