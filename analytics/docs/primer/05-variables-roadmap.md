# 05 — The insight layer: profiler, promotion, strata, census (DESIGN — not yet built)

Everything in chapters 01–04 is implemented and live. This chapter documents the **agreed
design** for the next capability jump. Nothing here is code yet; the natural next step is an ADR
for the profiler + promotion pair.

It is deliberately NOT "a variables feature". It is one design with **two products**:

```
① VARIABLES FROM DAY 1        breakdowns and correlations for variables nobody
                              declared — the discovery/automation product
② EXPLANATION                 "there are outliers — WHEN, and show me" — the
                              root-cause product: multi-variable rules, cohort-
                              relative anomalies, real example instances
```

The two share every mechanism below, because explaining an outlier IS the day-2 problem in its
sharpest form: the attribute that explains it is, almost by definition, one nobody declared.
Chapter 04's built machinery answers "when" for *promoted* variables; this layer extends the
answer to unpromoted variables, variable *combinations*, structural attributes (version, variant,
hour, tenant), and concrete evidence cases.

## The problem, stated honestly

```
variables are:   many per instance, unknown names, unbounded values
storing them raw per instance:   too costly, and queries over raw rows are slow
declaring cubes per variable:    works (corr-* proves it) but needs a human who
                                 KNOWS the variable — the "day 2" problem
and for explanation:             the outlier's cause is usually exactly the
                                 attribute (or combination) nobody anticipated
```

The resolution rests on one observation: **aggregate analytics never needs per-instance variable
rows** — it needs bounded aggregates plus a small, deliberately chosen raw remainder. Four layers:

```
                ┌────────── PROFILER (always on, bounded) ──────────┐
                │ per (process, variableName):                      │
                │ count, distinct (HLL), top-K values,              │
                │ numeric sketch → band edges, per-variant p95      │
                └───┬────────────────┬─────────────────────┬────────┘
     band edges +   │                │ "slow" thresholds   │ powers the
     promotion      ▼                ▼                     ▼ variables panel UI
┌── PROMOTION ─────────┐   ┌── STRATIFIED SAMPLE ──┐    "route: 3 values
│ auto-declares corr-* │   │ the bounded raw layer │     (auto 65 / manual 30…),
│ cubes into the built │   │ (below)               │     customerId: too many"
│ consumers of ch. 04  │   └───────────────────────┘
└──────────────────────┘
```

## Layer 1 — the profiler

Stage 1 already holds every instance's variables transiently (for fact enrichment). The profiler
folds them — **in Stage-1 state, raw values never leave the stage** — into one bounded row per
(process, variableName), emitted periodically as tiny pre-aggregated deltas: O(names), never
O(instances). It is the evidence base for everything else, and its own state is capped
(admission limit per process, name-family collapse for generated names like `item_1…item_442`
→ one `item_*` family row).

## Layer 2 — guarded auto-promotion

A variable *wins a slot* rather than walking through an open door:

```
                  new name observed
                        │
                  ┌───────────┐  ≥N sightings, 2–50 distinct, stable, present on ≥30%
                  │ TRACKING  │ ────────────────────────────────┐
                  └───────────┘                                 ▼
                        │ cardinality keeps growing      ┌────────────┐   auto-declares
                        ▼                                │  PROMOTED  │──► corr-* cubes via
                  ┌────────────┐                         └─────┬──────┘   the provisioning
                  │ SUPPRESSED │ (profiled only —              │          path; the ch. 04
                  └────────────┘  visible in the UI,           ▼ explodes later
                   e.g. customerId)                      ┌────────────┐
                                                         │   CAPPED   │ new values fold into
numerics: promoted with BANDS, edges from                └────────────┘ "(other)" + an alarm
the profile sketch (solves e.g. `amount`)
```

Hard budgets everywhere (≈15 cube-shapes per process; a promotion may cost 1–3 shapes —
duration/variant/branch — earned by profile evidence). Preferred materialization: **one**
`variable-breakdowns` cube with the name as a row dimension, so promotion = allowlist membership,
not schema churn. Everything the promotion manager produces is consumed by machinery that
already exists and already validates its input (the corr-* shape classifier ignores malformed
cubes) — that hardening was done precisely to make machine-generated declarations safe.

Two things promotion deliberately does NOT change: facts still carry only the promoted names
(the existing name-targeted enrichment — the promotion budget IS the capture budget), and
un-promoted history stays unanswered *exactly* — the layers below handle it approximately.

## Layer 3 — the stratified sample (bounded raw, chosen not collected)

One table of kept instances, each row carrying its variant, ALL its variables, and **how many
real instances it stands for** (the weight):

```
instance completes ──► which stratum?
┌────────────────────────────────────────────────────────────────────────────┐
│ INCIDENT / TERMINATED             keep ALL      weight 1   (rare = cheap)  │
│ VARIANT-SLOW                      keep ALL      weight 1                   │
│   duration > p95 OF ITS VARIANT ── cohort-relative: a global fence would   │
│   (rolling sketch from profiler)   flood on slow variants & miss fast-     │
│                                    variant anomalies                       │
│ VARIANT-IMMATURE (sketch n<30)    keep ALL      — doubles as the exemplar  │
│                                     quota for brand-new paths              │
│ VARIANT-EXEMPLAR                  first 5/day   display-only, NO weight    │
│ BULK                              1-in-100      weight 100                 │
│   hash(instanceKey)%100==0 ── deterministic, replay-safe, no randomness    │
└────────────────────────────────────────────────────────────────────────────┘
```

Query rule: every aggregation multiplies by weight; error bars shrink with √(rows behind the
number). Keep-all strata give **exact** numbers ("64% of slow claims are manual-loop — a census,
not a sample"); bulk gives estimates with visible bars. Exemplar rows are for *showing* (click →
Operate), never for counting — mixing them into estimates would bias toward what was kept.

## Layer 4 — the outlier census: the explanation product (product ②)

This layer is why the design is more than a variables feature. The full explanation pipeline —
each stage answering one word of "there ARE outliers, WHEN, here's PROOF, keep WATCHING":

```
① DETECT     fence per cohort (element / variant)      cubes, exact        (built, ch. 04)
② QUANTIFY   outlier share per time window             cubes, exact        (built — the
                                                       literal "when": a deploy shows
                                                       as a step in the series)
③ EXPLAIN-1  single-variable lift                      corr cubes, exact   (built, ch. 04)
④ EXPLAIN-2  multi-variable rules from the census      sample, approx      (THIS layer)
⑤ PROVE      exemplars → real cases in Operate         sample              (THIS layer)
   WATCH     rule → matched-predicate RATIO meter      cubes, exact        (meter built)
```

Stage ④ works because all outliers are kept whole (the VARIANT-SLOW / INCIDENT strata):
multi-variable explanation becomes a small two-class contrast problem — a complete positive
class versus a weighted normal baseline — solvable interactively at click time:

```
condition                     among outliers   among normal    lift
────────────────────────────────────────────────────────────────────
route=manual                       78%             29%         ×2.7
amount > 4 000                     64%             11%         ×5.8
manual AND > 4 000                 61%              4%        ×15.3   ◄ the story
```

Greedy, shallow search (single conditions, then pairs of the best; split points from profile
bands), MIN-support on the outlier side, error bars on the normal side. Crucially it sees
**every captured attribute** — unpromoted variables, suppressed high-cardinality ones
(`clerk=j.doe` over 200 census rows is trivial — cardinality guardrails protect the always-on
layer, and the census is too small to need protecting), and structural columns (definition
version, variant, start hour, tenant) that explain most real incidents without any variable at
all. When even the census finds nothing, the exemplars are the graceful floor: "no captured
attribute explains these — here are 5 real cases", and a human clicks into Operate.

The loop closes with industrialization: a discovered rule becomes a **matched-predicate RATIO
meter** (already built, ch. 02) — exact, windowed, compared period-over-period — and a
predictive unpromoted variable becomes a promotion signal. Discovery is approximate and
on-demand; monitoring is exact and always-on; one button moves a question from the first world
to the second.

## The escalation ladder (where any question lands)

```
cubes          exact,  ms,      forever      questions anticipated or promoted
census/sample  approx, ~1s,     ad hoc       questions nobody anticipated
facts replay   exact,  minutes, once         promoting a good question (backfill: future)
Operate        exact rows, per instance      drill-down — deliberately NOT our storage
```

## Bounds and honesty (the summary slide)

```
profiler        O(processes × names)  rows           capped, family-collapsed
promoted cubes  O(names × values × windows) cells    slots + (other) + alarms
sample          O(rate × retention + outliers)       weights make rates changeable
what we give up EXACT retroactive answers for never-promoted variables,
                and instance search inside analytics (Operate owns raw)
every limit is VISIBLE: suppressed names, capped values, dropped lates,
sampled numbers with bars — a guardrail the user can't see is a bug
```

## Precedents (one line each, for the "is this sane?" slide)

Datadog Metrics-without-Limits (ingest-all / index-few, forward-only config changes),
ClickHouse JSON dynamic subcolumns (auto-materialize hot paths, overflow bucket),
Elasticsearch dynamic mapping + field caps (limits users accept), Druid rollup mode
(aggregate-only at ingest), Prometheus exemplars (aggregates point into the raw-owning system),
Amplitude/Mixpanel property lexicon (the profiler's UX), BlinkDB/Honeycomb (stratified /
dynamic sampling with error bars). The one uncommon piece we add: promotion into *semantic
cubes* (meters, windows) rather than storage columns — enabled by the declarative-dataset layer,
and the reason the profiler must come first: its evidence is what keeps that bet safe.
