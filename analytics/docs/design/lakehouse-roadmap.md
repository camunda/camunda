# Analytics lakehouse roadmap

Status as of 2026-07-23. The lake engine (`analytics/analytics-lake`) and its serving webapp
(`analytics/analytics-lake-serving`) have reached feature-complete-for-the-showcase: translator-fed
metrics for instances/activities/flows/starts, variant-k1 capture, variable profiles, cohort
(survival) metrics, the OCPM object fabric (sightings, links, relations, lifecycle), the explain
plane, and the order-to-cash showcase driver. This document is the discipline for what comes next:
every item is either **phased** (we will build it, in order) or **triggered** (built only when a
named, observable trigger fires). Nothing lives outside those two lists — an idea without a phase
or a trigger is a discussion, not a plan.

## Phased (will build, roughly in order)

1. **One-backend consolidation.** One process hosts consume→fold→commit and the serving/explain
   backend. The lake side is done (`LakePocApp.start(LakeConfig)` returns a resource-owning
   `Handle`); the serving side (a Spring lifecycle bean in `analytics-lake-serving` hosting the
   handle, config properties, a profile that disables ingest in tests) lands after the explain
   backend merges.
2. **Fresh-stack live validation gate.** The standing correctness bar before any further feature
   work: partials vs. SQL recompute over raw for every entity, variant hashes recomputed via SQL,
   fabric rows vs. the showcase's planted ground truth (see
   `event-bridge/event-bridge-examples/docs/ocpm-showcase.md`) and the dispute driver's organic
   truth, explain tools vs. hand-written SQL, webapp click-through.
3. **Explain plane phases 2–3.** Phase 1 (rung ladder, catalog-derived registry, typed findings)
   ships with the explain backend. Phase 2 = scheduled autopilot sweeps producing standing
   findings; phase 3 = cross-dataset composition (a finding in one dataset cites a cohort in
   another). Same primitives, wider orchestration.
4. **PARTIAL compaction collapse.** Compaction currently rewrites partials without folding rows
   that share a grouping key + slot; collapse them during the rewrite (the algebra is already
   associative — this is deferred bookkeeping, not new machinery).
5. **Retention folded into compaction.** Expired rows are dropped during the compaction rewrite
   that touches the file anyway (predicate on the partition column), not by a separate janitor
   pass — zero extra write amplification. Terminal state per closed day partition: see next item.
6. **Cold-partition finalization.** Once a `days()` partition is behind the settle frontier and can
   receive no late data, compact it to its final layout once (fold partials, apply retention,
   re-sort), record it as finalized in table properties, and never visit it again.
7. **Accuracy gate as declared expectations.** Recast the validation gate's assertions as
   data-quality expectations carried by the dataset declaration (`expect orderId is not null`,
   violation policy = count / quarantine / fail), metered like everything else — the gate becomes a
   standing property of the pipeline rather than a script we remember to run.
8. **Decisions as a first-class entity.** DMN evaluations join instances/activities/objects with
   the same declaration-driven metrics treatment.
9. **Dimension tags.** Declaration-supplied dimension kinds (categorical / continuous / id-like)
   overlaying the catalog-derived registry, so explain's screening chooses tools per dimension
   instead of guessing from types.
10. **`born().when(...)` object birth qualifiers.** Explicit birth rules beyond FIRST_SIGHTING for
    object types whose first sighting is not their creation.
11. **ANSI-merge enforcement.** The merge/finalize SQL the declarations render must stay in the
    ANSI subset the serving backends share; enforce with a rendering-time lint, not review.
12. **Per-dataset target lag.** Replace the single global cut cadence with a declared freshness SLA
    per dataset ("this KPI cube may lag 1m, this audit table 1h"); the flush/cut scheduler meets
    the tightest declared lag per pipeline rather than one global knob.
13. **Snapshot-keyed result cache in serving.** Cache `(normalized query, Iceberg snapshot id) →
    result`; dashboard polling between commits becomes free, and invalidation is exact by
    construction.
14. **Lake operability meters.** Small-files-per-partition (parts pressure) with a warn threshold,
    sort-quality (clustering depth) per partition to tell the compactor when a re-sort pays,
    merge-queue depth / oldest-unmerged-file, exposed on the serving dashboard.
15. **Iceberg metadata maintenance.** Snapshot expiry and manifest rewrite as scheduled compactor
    duties — frequent small commits make these mandatory, not optional hygiene.
16. **Checkpoint-at-cut.** Promoted from the exactly-once discussion (see below): translator state
    checkpointed at the same boundary the lake commits, closing the evict-after-emit window
    systemically. Sequenced with the holistic exactly-once revisit rather than patched piecemeal.

## Triggered (built when the named trigger fires)

| Item | Trigger |
|------|---------|
| Variant k>1 / sequence-preserving variants | A real analysis the k1 signature cannot answer (two flows distinguishable only by order or repetition). |
| SNAPSHOT dataset kind (open-entity views) | A dashboard need for "currently open" state that windowed facts can't serve. Design note: use signed-row collapse (+1 create / −1,+1 update, compaction cancels pairs, reads `SUM(sign)`) rather than in-place mutation. |
| HLL / approximate distinct at scale | A distinct-count dimension whose exact cardinality measurably blows partial sizes. |
| Raw-data sampling (sample the boring, keep the interesting) | Raw retention cost is measured as a real budget problem, not a hypothetical. |
| Tracer tier (object-keyed shuffle for cross-partition objects) | An object type whose instances demonstrably span event-bridge partitions; until then the partition-local assumption holds by construction. |
| OCEL 2.0 export views | An external OCPM tool integration is actually requested; the fabric tables already contain everything OCEL needs, so this is a view layer. |
| LLM finding composer | Explain findings are consumed by people who want prose, and the typed findings prove insufficient as-is. |
| Fetch 416-storm fix (app restart after retention trim) | Revisit when the wedge bites a run that matters; remedy today is a fresh stack. |
| DuckLake evaluation | Iceberg metadata churn shows up as a measured operational burden despite item 15; DuckLake's SQL-database metadata plane is the shaped alternative for an embedded, DuckDB-served lake. |

## Exactly-once: the holistic revisit (parked, deliberately)

The engine currently offers effectively-once through origin-coordinate dedup plus the
snapshot-summary offset authority (offsets committed atomically with data — the same contract
Snowpipe Streaming's channel tokens and Delta's streaming txn versions arrive at independently).
One systemic gap is known and documented rather than patched:

**Evict-after-emit crash window.** The translator deletes an entity's working state (open
instance, variables, variant accumulator, sightings) at the moment it emits the completion fact,
but the fact is only durable at the next commit, up to one flush window later. A crash inside that
window loses completions whose activations predate the last cut: replay re-reads the records, but
the state needed to re-derive the completion was already evicted and re-appended facts for the
same coordinates are deduplicated away.

Two remedies were designed:

- **Defer evictions to descriptor accept** (targeted): keep evicted keys in a pending set until the
  commit that contains their facts is accepted, then delete. Small, local, but adds a second
  lifecycle to every store.
- **Checkpoint-at-cut** (systemic): checkpoint RocksDB at the cut boundary so replay always resumes
  from a state consistent with the offset authority. Heavier, but collapses the whole class of
  emit/durability races, not just this instance — hence its promotion to phased item 16.

Per the 2026-07-20 decision, neither is built piecemeal: the exactly-once story (atomic cuts,
zombie fencing, this window) is revisited as one design pass once the core engine is validated.
Until then the window is accepted and documented, and the validation gate's recompute-from-raw
checks are the detection net.

## Borrowed from the neighbors (provenance for the items above)

Surveyed ClickHouse, Databricks/Delta, and Snowflake against this architecture (2026-07-23).
What they *validate*: insert-time fold into mergeable partials with merge-time re-aggregation and
finalize-at-read (ClickHouse `-State`/`-Merge`, our partials/riders/serving); a memory-resident
batch buffer in front of immutable columnar parts; offsets-inside-the-commit for exactly-once
sinks; metadata through consensus with bulk data out-of-band; a vectorized native read engine
(their Photon, our DuckDB); and — negatively — the industry pain of precomputed gold layers, which
is why the UI serves raw+partials only. What they *contributed*: retention-in-merge and
parts-pressure backpressure (ClickHouse → items 5, 14), force-merge-old-parts (→ item 6),
signed-row collapse (→ the SNAPSHOT trigger's design note), declarative expectations and
change-data-feed table-following (Databricks → item 7 and the derived-facts-topic target
architecture), target lag, snapshot-keyed result caching, and clustering-depth metering
(Snowflake → items 12, 13, 14). Iceberg remains the substrate on purpose: it is the one format
every major engine reads (the product story), our single-committer streaming pattern is its paved
road, and its two real costs — metadata churn and DIY writers — are item 15 and already-written
code respectively.
