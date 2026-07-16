# 03 — The standard datasets: 21 declarations, and the question each answers

A **dataset declaration** is the unit of "a question we decided to answer": which facts to
subscribe to, cube-level filters, the grain (dimensions), the meters (columns), the window
size(s), the lateness budget, and optionally periodic snapshots. Two kinds exist:

```
AGGREGATED (cube)   facts fold into per-window cells      →  charts, KPIs, correlations
TABLE               facts upsert/delete one row per key   →  raw slices, dictionaries
```

The catalog below groups them by the question they serve. Notation: `grain → meters` and the
key filters. Default window is 1 minute with the standard grace; deviations are called out.
A general reading rule for every cube: a **series** read returns one point per window; a
**total** read merges the range's accumulators into one row (chapter 02 explains why merging is
always safe).

---

## Lifecycle & throughput

### `process-instances` — "how much work enters and leaves, when?"

```
grain: bpmnProcessId
meters: activated / completed / terminated   (three COUNTs, per-meter transition filters)
        duration  (EXECUTION_TIME over durationMs)
        duration_bands (HISTOGRAM over durationMs)
```

One unfiltered grain, three transition-filtered counters — so started/ended tiles, the flow-
balance chart (arrivals vs departures, Little's-law intuition), SLA cohorts and duration-band
charts all read the **same rows**, and the render memo collapses them onto one store query.
`ended = completed + terminated` by definition — terminated work must not vanish from totals.

### `active-instances` — "how many are running right now?"

```
grain: bpmnProcessId → active (LEVEL over the ±1 lifecycle delta)
lateness: 1 minute (deliberately tight)   snapshots: every minute
```

The LEVEL telescoping proof (ch. 02) makes the sum equal "currently in flight"; snapshots
materialize it as a point-in-time series. Tight lateness = fresh gauge; the trade is visible,
alarmed late-drops instead of a stale chart.

### `open-instances` — "WHICH instances are running (aging WIP)?"  [TABLE]

```
key: processInstanceKey; row: {bpmnProcessId, startTime}
insert on ACTIVATED, evictWhen(transition ≠ ACTIVATED)  → row exists ⇔ instance is open
```

The eviction rule is the correctness argument: every terminal fact deletes the row, so the table
converges to exactly the open set, and age = now − startTime is computed server-side against one
consistent "now".

---

## Duration & performance

### `process-duration` — "how long do completions take, in distribution?"

```
filter: COMPLETED     grain: bpmnProcessId     windows: 1m AND 1h (tiered)
meters: percentiles (KLL: p50/p75/p90/p99…), duration (EXECUTION_TIME), stddev (STDDEV)
```

The control-chart backbone: percentile trend, avg ± σ spread band, and — because the KLL sketch
travels to the reader — the outlier fence (ch. 04). Two tiers exist because sketches merge
exactly: long ranges read hourly cells, short ranges minute cells, same numbers either way
(that's the tiering-soundness corollary of the monoid proof).

### `elements` — "which flow nodes are slow / hot / reworked?"

```
grain: (bpmnProcessId, elementId)     windows: 1m + 1h
meters: completed, duration, duration_p (all COMPLETED-filtered);
        activations (ACTIVATED count); instances (DISTINCT processInstanceKey on ACTIVATED)
```

One cube feeds the duration heatmap (avg/p50/p90 per node), the element table, rework hotspots
(`rework = max(0, activations − distinct instances)` — an element activated more often than the
instances that touched it looped somewhere), the gateway branch shares (activations of a
gateway's targets ÷ activations of the gateway), and the per-node outlier table (via
`duration_p`'s sketch). The DISTINCT here is HLL — approximate at scale, exact for small counts.

### `region-duration` — "p95 by a business dimension (demo: region)"

```
filters: COMPLETED + NOT_NULL(var.region)     grain: (bpmnProcessId, var.region)
meters: count, p95 (PERCENTILE over durationMs)
```

The template for "slice performance by a variable" — and the reason the NOT_NULL filter matters:
without it, instances lacking the variable would fold as a NULL group and pollute the slice.
Also the origin of the root-visibility rule: the variable must live at the instance's root scope.

---

## Quality & incidents

### `process-quality` — "how often do we meet our promises?"

```
grain: bpmnProcessId
meters: sla_compliance   (RATIO: durationMs ≤ SLA, over COMPLETED)
        no_incident      (RATIO: hadIncident = false, over ended)
        first_time_right (RATIO, matched-predicate form: completed normally AND
                          no incident AND no rework signal)
```

Three ratios, three different denominators, one grain — each meter filters its own population.
The maturing-cohort bound (ch. 02, RATIO) applies to SLA readings of recent windows: the UI
shows the lower/upper band while instances are still running.

### `incidents` — "where do incidents happen, and how many are open?"

```
grain: (bpmnProcessId, elementId)
meters: count (CREATED-filtered — an unfiltered count would double-count resolutions),
        open  (LEVEL over ±1 create/resolve delta)
```

`count` answers "raised in range" (range-scoped); `open` answers "open right now"
(range-independent gauge) — the two toggles of the incident heatmap. Resolution-duration
metrics were consciously dropped in the consolidation; the LEVEL telescoping proof guarantees
`open` = created − resolved regardless of interleaving.

---

## Portfolio & business value

### `tenant-overview` — "how diverse is a tenant's traffic?"

```
grain: tenantId     window: 1h
meters: distinct (HLL over bpmnProcessId), top (TOP_K over bpmnProcessId)
```

The two sketch meters whose merges are idempotent — the safest possible numbers, at hourly grain
because portfolio questions don't need minutes.

### `value-throughput` — "how much business value did we complete?"

```
filter: COMPLETED     grain: bpmnProcessId → processed (SUM over value)
```

`value` is stamped by the engine from the designated variable (`amount` by default) — read once
at activation, materialized on the instance row (ch. 02, LEVEL, explains why). Null result =
"no value-carrying completion in range", rendered as a dash, never a fake zero.

### `value-in-flight` — "how much value is sitting in the pipeline right now?"

```
grain: bpmnProcessId → value (LEVEL over valueDelta ±amount)
lateness 1m, snapshots 1m — the value twin of active-instances
```

Balance proof = LEVEL telescoping + the activation-materialized stamp: every instance adds and
subtracts the *same* number, so the level is exactly the value of open instances.

### `dispute-types` — demo showcase of variable grouping

```
filters: COMPLETED + NOT_NULL(var.customerId)   grain: (bpmnProcessId, var.type)
meters: count, p95
```

Kept as the reference declaration for var.* dimensions and the silent-empty-cube alarm's
true-positive story (it is empty unless the bank-dispute process runs).

---

## Variants & correlation (the analysis layer — full math in chapter 04)

### `process-variants` — "which execution paths exist, how common, how slow?"

```
filter: NOT_NULL(variantHash)   grain: (bpmnProcessId, variantHash LONG)   windows: 1m + 1h
meters: count, duration_p (PERCENTILE with ranks .5/.95)
```

### `variant-catalog` — the hash → human-readable dictionary  [TABLE]

```
key: variantHash; row: {bpmnProcessId, variantElements TEXT}
```

Every end fact carrying a variant upserts the identical row (idempotent by construction); the
process-id seed inside the hash guarantees two processes can never collide on one key.

### `corr-route`, `corr-region` — "do outliers concentrate on a variable value?"

```
filters: COMPLETED + NOT_NULL(var.X)   grain: (bpmnProcessId, var.X)
meters: count, duration_p (KLL — the per-value distribution the lift math needs)
```

### `corr-variant-route`, `corr-variant-region` — "which values predict which path?"

```
filters: NOT_NULL(variantHash) + NOT_NULL(var.X)
grain: (bpmnProcessId, variantHash, var.X) → count      (a pure joint-count table)
```

### `corr-branch-route`, `corr-branch-region` — "which values drive which gateway branch?"

```
fact type: ELEMENT, filters: COMPLETED + NOT_NULL(var.X)
grain: (bpmnProcessId, elementId, var.X) → count
```

COMPLETED-filtered because only completion facts resolve variables up-scope — a documented,
slight undercount versus the activation-based branch card.

The `corr-` prefix is a discovery convention: the readers scan the catalog and **classify by
dimension shape** (one var.* dim + duration sketch → duration correlation; + variantHash →
variant; + elementId → branch). Malformed or ambiguous cubes are ignored, never served — which is
what makes the convention safe for future machine-generated declarations (ch. 05).

---

## Raw slices

### `raw-completed-instances` — bounded exact drill-down  [TABLE]

```
key: processInstanceKey; row: {bpmnProcessId, durationMs, hadIncident, var.region}
```

The existence proof for the TABLE kind and the seed of the sampling roadmap (ch. 05): exact rows
for the retained window, never the primary analytics path.

### `process-definitions` (built-in, written by the definition sink)

Deployed BPMN XML per definition key — feeds the diagram heatmaps and the gateway topology
parser. Highest version wins on redeploy.

---

## Cross-cutting rules every dataset obeys

```
bootstrap        declarations are written into an EMPTY metadata store only; the stored
                 spec is the source of truth afterwards (reconciliation is a known TODO)
provisioning     a dataset declared at runtime hot-reloads into the live pipeline and
                 fills from its activation onward (no backfill yet — ch. 05)
missing data     a read against an absent dataset/meter degrades to an EMPTY answer,
                 never an error — one 500 would blank the whole dashboard render
null vs zero     "no data" is a dash; 0 is a measured zero; the two are never conflated
64-bit values    identifiers that use all 64 bits (variantHash) travel JSON as strings —
                 JS numbers carry 53 bits
```

