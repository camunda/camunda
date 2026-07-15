# Implementation spec: statistical duration outliers + variable correlation (task #27)

Status: ready to implement. This spec is self-contained — it names every file, API shape,
formula, edge case, and test. Read it fully before writing code. When this spec conflicts with
what you find in the code, STOP and report the conflict instead of improvising.

## Context (read first, don't skip)

The analytics stack aggregates Zeebe process events into **cubes** (pre-aggregated datasets)
served to a dashboard. You will not touch the write pipeline at all — this feature is
**read-side only** plus two new dataset declarations.

What already exists and is load-bearing for this feature:

- Per-`(bpmnProcessId, elementId)` duration distributions are already captured as
  **KLL quantile sketches** by the `duration` meter (`MeterCatalog.EXECUTION_TIME`) and
  percentile meters (`MeterCatalog.PERCENTILE`) on the `elements` cube —
  `analytics/analytics-serving/src/main/java/io/camunda/analytics/serving/catalog/StandardDatasets.java`
  (`"elements"` declaration, ~line 239).
- A range read merges the persisted per-window KLL accumulators **in-process** (the
  STREAM_MERGE path) and materializes a result via
  `QuantileAggregateFunction.getResult(KllDoublesSketch)` in
  `analytics/analytics-model/src/main/java/io/camunda/analytics/sketch/QuantileAggregateFunction.java`.
  This is true on every backend (H2/RDBMS, Elasticsearch, OpenSearch) — the accumulator bytes are
  fetched and merged locally, so the merged sketch object exists in the serving JVM at read time.
- The result type is `QuantileResult(long count, double min, double max, double[] ranks,
  double[] values)` in `analytics/analytics-model/src/main/java/io/camunda/analytics/sketch/QuantileResult.java`.
  Its `valueAt(rank)` answers **only declared ranks** — that limitation is exactly what step 1 removes.
- Cubes can group by **process variables** directly: see the `"dispute-types"` declaration in
  `StandardDatasets.java` (~line 297) — `dimension("var.type", …)` + `filterNotNull("var.customerId")`.
  This is the pattern Part 2's cubes copy. Only root-scope (start-payload) variables are visible
  to PROCESS_INSTANCE facts — document that in the declaration comment like dispute-types does.
- The dashboard read layer is
  `analytics/analytics-webapp/src/main/java/io/camunda/analytics/webapp/dashboard/DashboardRepository.java`
  (query building via the private `total(...)`/`series(...)` helpers + a per-request memo), DTOs are
  records in the same package, endpoints live in
  `analytics/analytics-webapp/src/main/java/io/camunda/analytics/webapp/dashboard/DashboardController.java`,
  the React client in `analytics/analytics-webapp/client/src/` (`lib/api.ts` types,
  `lib/useMetrics.ts` fetch hook, `components/*.tsx` cards, `pages/PerformancePage.tsx`).

## The method (both parts share it)

Boxplot rule (same as Camunda Optimize): from a duration distribution take
`Q1 = quantile(0.25)`, `Q3 = quantile(0.75)`, `IQR = Q3 − Q1`,
**fence = Q3 + 1.5·IQR**. Everything above the fence is an outlier.

- `outlierShare = 1 − rank(fence)` where `rank(v)` is the sketch's normalized rank of `v`
  (fraction of observations ≤ v). Use `KllDoublesSketch.getRank(v, QuantileSearchCriteria.INCLUSIVE)`.
- `outlierCount = round(outlierShare × n)`.
- Part 2 (correlation): for each value `x` of a variable, `share(x) = 1 − rank_x(fence_overall)` —
  the rank of the **overall** fence inside the **per-value** sketch. `lift(x) = share(x) /
  overallShare`. A value with lift ≫ 1 is over-represented among outliers. This cross-sketch
  query is why step 1 must expose the sketch, not just precomputed numbers.

All numbers are KLL approximations — every user-facing card must carry a short "approximate
(sketch-based)" note in its description text.

## Step 1 — expose the merged sketch on `QuantileResult` (the enabling primitive)

File: `analytics/analytics-model/src/main/java/io/camunda/analytics/sketch/QuantileResult.java`
and `QuantileAggregateFunction.java`.

1. Add a trailing component to the record: `@JsonIgnore KllDoublesSketch sketch` (nullable).
   Annotate so it NEVER serializes to JSON — `QuantileResult` rides inside report-row measures
   returned to the client, and the JSON shape must not change (assert that in a test).
2. `QuantileAggregateFunction.getResult` passes the merged sketch it already holds;
   `QuantileResult.empty(ranks)` passes `null`.
3. Add methods to `QuantileResult` (all must handle `sketch == null` and empty/NaN cases):
   - `double quantile(double rank)` — arbitrary rank via the sketch
     (`getQuantile(rank, INCLUSIVE)`); falls back to `valueAt(rank)` for declared ranks when the
     sketch is absent; returns `Double.NaN` otherwise.
   - `double shareAbove(double value)` — `1 − getRank(value, INCLUSIVE)`; `NaN` without a sketch.
   - `OutlierStats outlierStats()` — a small record `OutlierStats(long n, double median,
     double q1, double q3, double fence, double share, long count)` computed from the sketch,
     or `null` when the sketch is absent/empty. Put `OutlierStats` in the same package.
4. **Fallout to expect:** other constructions of `QuantileResult` (search for `new QuantileResult(`)
   need the extra `null`/sketch argument — fix each call site, do not add a compat constructor
   unless more than ~5 sites exist. Record `equals` already used array reference equality, so no
   test should depend on whole-record equality; if one does, rewrite it to assert on components.

Constraint: callers of `quantile`/`shareAbove`/`outlierStats` are **server-side only**
(DashboardRepository). Say so in the javadoc.

## Step 2 — Part 1: per-flow-node duration outliers (zero new pipeline state)

1. `DashboardRepository`: new method
   `List<ElementOutlier> elementOutliers(String bpmnProcessId, Long fromWindow, Long toWindow)`.
   - Read the `elements` cube grouped by `elementId`, meters `List.of("completed", "duration")`,
     filtered `bpmnProcessId`, via the existing `total(...)` helper with a fresh memo — copy the
     shape of the existing `elementDurations(...)` method in the same class.
   - CAUTION: check which meter on the `elements` cube is sketch-backed. `duration` is
     `EXECUTION_TIME` — if its result type is `ExecutionTimeResult` (not `QuantileResult`), use the
     cube's percentile meter instead; if `elements` has no `PERCENTILE(durationMs)` meter, add one
     named `duration_p` with ranks `{0.25, 0.5, 0.75, 0.95}` to the declaration (see the
     "dataset changes need a fresh store" warning at the bottom).
   - For each row: `stats = quantileResult.outlierStats()`; skip rows where `stats == null` or
     `stats.n() < 20` (below that, quartile fences are noise — put the threshold in a
     `MIN_OBSERVATIONS = 20` constant with a comment).
   - DTO: `ElementOutlier(String elementId, long n, long medianMs, long q3Ms, long fenceMs,
     double share, long count)` — durations rounded to long ms.
   - Sort by `count` descending. Return empty list when the dataset/meter is missing from the
     catalog (`catalog.byName().containsKey(...)` guard — NEVER let a missing dataset throw: one
     500 blanks the whole dashboard because the client fetches everything in one `Promise.all`).
2. `DashboardController`: `GET /api/dashboard/outliers?process=&from=&to=` → that list. Mirror
   the parameter validation of the neighboring endpoints exactly.
3. Client: type in `lib/api.ts`, fetch in `lib/useMetrics.ts` (add to the existing parallel fetch
   + the `MetricsData` interface — BOTH places, the hook does not compile-check against the server),
   new `components/OutlierTable.tsx` card on `pages/PerformancePage.tsx`: table
   `element | n | median | fence | outliers (count, share%)`, bar on count relative to max,
   empty state "No outliers detected in range (approximate, sketch-based)".

## Step 3 — Part 2: variable correlation without raw instances

1. Two new standard cubes in `StandardDatasets.java`, copying the dispute-types shape (append at
   the end, comment them `// APPENDED.`):

   ```java
   DatasetDeclaration.builder("corr-route", FactType.PROCESS_INSTANCE)
       .filterEquals("transition", Transition.COMPLETED.name())
       .filterNotNull("var.route")
       .dimension("bpmnProcessId", DimensionType.STRING)
       .dimension("var.route", DimensionType.STRING)
       .meter(Meter.of("count", MeterCatalog.COUNT))
       .meter(Meter.of("duration_p", MeterCatalog.PERCENTILE, "durationMs")) // ranks: see below
       .window(ONE_MINUTE_MS)
       .lateness(GRACE_MS)
       .build(),
   ```

   and the same for `corr-region` (`var.region`). Check how `PERCENTILE` meters declare ranks
   (see the dispute-types `p95` meter and `MeterCatalog`) — declare `{0.25, 0.5, 0.75, 0.95}`;
   the declared ranks matter only for the JSON-visible values, the sketch answers the rest.
   Add a declaration comment: correlation cubes are naming-convention-discovered (`corr-` prefix,
   exactly one `var.*` dimension) and must keep per-variable cardinality low (this is the
   guardrail — document it, do not enforce it in code).

2. `DashboardRepository`: new method
   `List<VariableCorrelation> variableCorrelations(String bpmnProcessId, Long from, Long to)`.
   - Overall fence: read the `process-duration` cube's sketch-backed percentile meter (find the
     meter name in the `"process-duration"` declaration — it is the one whose result is a
     `QuantileResult`; the p50–p99 trend reads it already) for the process over the range;
     `overall = result.outlierStats()`. If `overall == null` or `overall.share() == 0` → return
     empty (no outliers, nothing to correlate).
   - Discover correlation cubes: every catalog dataset whose name starts with `corr-` and whose
     declaration has a `var.*` dimension. For each: read grouped by that dimension, meters
     `count` + `duration_p`, filtered by `bpmnProcessId`.
   - Per row (= per variable value): `share = quantileResult.shareAbove(overall.fence())`;
     skip `n < 20` rows and NaN shares; `lift = share / overall.share()`.
   - DTO: `VariableCorrelation(String variable, String value, long n, double share, double lift)`
     where `variable` is the dimension name without the `var.` prefix.
   - Sort by `lift` descending, cap at 20 rows. Same missing-dataset guard as Part 1 — the
     endpoint serves an empty list on a store without `corr-*` cubes.
3. `DashboardController`: `GET /api/dashboard/variable-correlation?process=&from=&to=`.
4. Client: `components/OutlierCorrelation.tsx` on the Performance page next to the outlier table:
   `variable=value | instances | % above fence | lift` with lift rendered as `×1.8`; description
   text: "Which variable values are over-represented among duration outliers (approximate,
   sketch-based; declared variables only)". Empty state: "No correlation data — no corr-* cube
   or no outliers in range."

## Step 4 — extend correlation to variants and gateway branches (counts only, no sketches)

The variant report and the gateway branch distribution gain a "driver" dimension: which variable
values predict which execution variant / which branch. Both are possible today because the facts
already carry the joint information:

- PROCESS end facts carry `variantHash` AND lazily-resolved `var.*` → a cube grained
  `(bpmnProcessId, variantHash, var.X)` captures the joint variant/value distribution.
- ELEMENT **COMPLETED** facts resolve `var.*` up the scope hierarchy
  (`ElementCompletedDeriver` attaches `.variables(...)`; the ACTIVATED deriver does NOT — do not
  try to condition activations on variables, the dimension would be all-NULL) → a cube grained
  `(bpmnProcessId, elementId, var.X)` captures branch-target counts conditioned on the value.

### Cubes (append to `StandardDatasets`, same `corr-` prefix)

```java
// Which variable values drive which execution variant. COMPLETED and TERMINATED alike —
// variants count both (a terminated retry-storm is exactly the kind of variant a driver
// value should explain).
DatasetDeclaration.builder("corr-variant-route", FactType.PROCESS_INSTANCE)
    .filterNotNull("variantHash")
    .filterNotNull("var.route")
    .dimension("bpmnProcessId", DimensionType.STRING)
    .dimension("variantHash", DimensionType.LONG)
    .dimension("var.route", DimensionType.STRING)
    .meter(Meter.of("count", MeterCatalog.COUNT))
    .window(ONE_MINUTE_MS)
    .lateness(GRACE_MS)
    .build(),

// Which variable values drive which branch target. COMPLETED element facts (they carry
// variables; activations don't) — counts skew low vs the branch card's activation counts
// when an element is terminated mid-flight; document, don't reconcile.
DatasetDeclaration.builder("corr-branch-route", FactType.ELEMENT)
    .filterEquals("transition", Transition.COMPLETED.name())
    .filterNotNull("var.route")
    .dimension("bpmnProcessId", DimensionType.STRING)
    .dimension("elementId", DimensionType.STRING)
    .dimension("var.route", DimensionType.STRING)
    .meter(Meter.of("count", MeterCatalog.COUNT))
    .window(ONE_MINUTE_MS)
    .lateness(GRACE_MS)
    .build(),
```

Mirror both for `var.region`. Verify the exact filter-builder method names against the existing
declarations before writing (`filterEquals`/`filterNotNull` exist on the builder — see
dispute-types).

### Discovery convention (replaces the Part-2-only rule)

A catalog dataset named `corr-*` is a correlation cube; dispatch on its dimension shape:

- exactly one `var.*` dimension + a `duration_p` percentile meter → **duration** correlation (Part 2);
- `variantHash` + one `var.*` dimension → **variant** correlation;
- `elementId` + one `var.*` dimension → **branch** correlation.

Implement the classification in one small package-private helper in the webapp
(`CorrelationCubes.classify(CompiledDataset)`), unit-tested, so all three endpoints share it.

### Math (pure counts — lift of the outcome given the value)

For outcome `o` (a variant hash, or a branch-target elementId) and variable value `x`, over the
selected range, all within one `bpmnProcessId` and one corr cube:

```
n(o,x)  = joint count            n(x) = Σ_o n(o,x)
n(o)    = Σ_x n(o,x)             N    = Σ n(o,x)
lift(o,x) = ( n(o,x) / n(x) ) / ( n(o) / N )        // P(o|x) / P(o)
```

Marginals come from the corr cube itself (never mix with the variants/elements cubes — the corr
cube only sees instances that HAVE the variable, so its marginals are self-consistent).
Skip pairs with `n(o,x) < MIN_SUPPORT = 10`. For branch correlation, restrict outcomes to the
gateway's outgoing-target elementIds using the same parsed topology `branchDistribution` uses
(`GatewayTopology`), so pass-through elements don't pollute the ranking.

### Endpoints + UI

- `GET /api/dashboard/variant-correlation?process=&from=&to=` →
  `[{variantHash: string, variable, value, n, share, lift}]` — for each variant its TOP value by
  lift (one row per variant×variable), `variantHash` as a **decimal string** (64-bit rule).
- `GET /api/dashboard/branch-correlation?process=&from=&to=` →
  `[{gatewayId, targetId, variable, value, n, share, lift}]` — per branch its top driver.
- UI: no new cards. `TopVariants.tsx` gains a "driver" column rendering the top driver as a chip
  (`route=manual ×2.4`), joined client-side by the `variantHash` string; `GatewayDecisions.tsx`
  gains the same chip per branch row. Both fetches are additive and optional: a missing cube /
  empty response hides the column entirely (never an error state, never a layout jump beyond the
  extra column).

### Extra edge cases for step 4 (tests)

- A variable that is literally the gateway's condition input (claim's `route`) must produce
  near-perfect lift on its branch — that is the desired behavior (it recovers the decision rule),
  assert it in the serving test rather than "fixing" it.
- lift denominator zero (`n(o) == 0` after range filtering) → skip the pair.
- Variant present in the variants card but absent from the corr cube (instance lacked the
  variable) → no chip, row renders unchanged.
- Cross-process hash collisions are already prevented by the process-seeded signature; still
  filter every corr read by `bpmnProcessId`.
- Numeric variables (e.g. `amount`) are OUT OF SCOPE: they need declare-time banding to be a
  dimension. Note this in the declaration comment; do not attempt it.

## Edge cases that MUST be handled (each one is a test)

- Empty sketch / `n == 0` → no row, never NaN in JSON.
- `IQR == 0` (constant durations) → fence == Q3, share is whatever the sketch says above it —
  typically 0; must not divide by zero when `overall.share() == 0` (skip correlation entirely).
- Sketch absent after a hypothetical JSON round-trip → `outlierStats()` returns null, callers skip.
- Missing datasets/meters on an old metadata store → empty responses, HTTP 200 (test via a
  catalog stub without the cube).
- `from >= to` handled identically to neighboring endpoints (mirror, don't invent).
- Rows below `MIN_OBSERVATIONS` are excluded — boundary test at exactly 20.

## Tests (all mandatory, use existing patterns)

Follow repo conventions: JUnit 5, AssertJ, `should…` names, `// given // when // then`. NEVER
inline fully-qualified names — use static imports (repo rule).

1. `QuantileResultTest` (analytics-model): `quantile()` arbitrary rank vs declared-rank fallback;
   `shareAbove` on a known distribution (feed 1..100 into a real KLL sketch: fence math is
   deterministic enough to assert with `Offset.offset(…)` tolerances); `outlierStats` on empty
   sketch → null; JSON shape unchanged (serialize with Jackson, assert no `sketch` key).
2. `QuantileAggregateFunctionTest` (extend existing): `getResult` carries the sketch;
   merged-accumulator result answers `shareAbove`.
3. `DashboardOutliersServingTest` (analytics-webapp, copy the style of
   `DashboardVariantsServingTest` — it shows how to seed cube rows through the fixture): seed a
   skewed element-duration distribution → outlier table returns the hot element first with sane
   fence/share; below-threshold element excluded; missing cube → empty list.
4. `DashboardCorrelationServingTest`: seed overall + per-value sketches where one value is
   heavily outlier-biased → that value tops the lift ranking; `overall.share == 0` → empty;
   missing `corr-*` cubes → empty.
5. Controller test for all four endpoints (copy the compare-endpoint tests' validation assertions).
6. `CorrelationCubesTest`: the shape classifier maps duration/variant/branch cubes correctly and
   rejects malformed ones (two var.* dims, no var.* dim).
7. `DashboardVariantCorrelationServingTest` + `DashboardBranchCorrelationServingTest`: seed joint
   counts where one value is heavily variant-biased / branch-biased → correct top driver + lift
   (hand-compute the expected lift in the test comment); MIN_SUPPORT exclusion; missing cube →
   empty 200.

## Gates (run all, in this order, before every commit)

```bash
./mvnw license:format spotless:apply -T1C
./mvnw verify -pl analytics/analytics-model -DskipTests=false -DskipITs -Dquickly
./mvnw verify -pl analytics/analytics-webapp -DskipTests=false -DskipITs -Dquickly
cd analytics/analytics-webapp/client && npx tsc --noEmit   # compare error COUNT to baseline before your change — zero NEW errors
npx vite build
./mvnw install -Dquickly -T1C                               # full-repo compile gate
```

Commits: conventional, no scopes, ≤120-char header, separate `feat:` commits per part
(step 1+2 can be one commit, step 3 a second), explain WHY in the body.

## Warnings from prior sessions (do not rediscover these)

- **Dataset declarations only bootstrap into an EMPTY metadata store.** Adding `duration_p` to
  `elements` or the `corr-*` cubes will NOT appear on an existing demo store — the smoke
  validation needs a fresh stack (state wipe). Note this in your final report; do not try to
  hot-patch stored spec JSON.
- 64-bit longs must not ride JSON as numbers if the client echoes them back (JS doubles carry
  53 bits). Durations here are safe magnitudes; hashes are not — irrelevant for this feature but
  do not introduce new long identifiers in responses.
- `useMetrics.ts` assembles `MetricsData` by hand — forgetting the interface field compiles fine
  under vite and crashes at runtime. Add the field AND the fetch AND check the page renders the
  loading/empty state.
- Null vs 0 semantics: an empty range must render as an empty state, not as zeros.
- The client fetches ~30 endpoints in one `Promise.all`: one 500 blanks every page. Missing
  data must always be an empty 200.

## Definition of done

- All gates green; zero new tsc errors; all listed tests present and passing.
- Correlation chips render live: claim-process's `route` drives both the fraud/manual variants
  and the triage branches with lift ≫ 1 (the driver weights routes 65/30/5, and amount > 4000
  forces manual — visible as `route=manual` dominating the loop variant).
- Both cards render live: outlier table shows slow-tail elements (the demo driver's ~15%
  SLA-breach fat tail guarantees outliers on worker elements), correlation ranks `route=manual`
  (claim-process's retry loop makes it slower) above `route=auto` with lift > 1.
- A short final report: what was built per step, deviations (with justification), test counts,
  and the fresh-store requirement called out.
