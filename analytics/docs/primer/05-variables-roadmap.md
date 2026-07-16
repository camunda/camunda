# 05 — The insight layer: profiler, promotion, strata, census (DESIGN — not yet built)

Chapters 01–04 describe what is implemented and live. This chapter is the **agreed design** for
the next jump. Nothing here is code yet; the natural next step is an ADR for the profiler +
promotion pair.

## Two user stories, one design

```
   STORY ①  "I deployed a process that sets a `discount` variable this morning.
             I want a breakdown by discount — WITHOUT filing a ticket, editing a
             dataset, or knowing that 'datasets' exist."

   STORY ②  "Claims are slow since Tuesday. WHY? Nobody configured anything about
             the attribute that turns out to be the reason — that's what makes it
             the reason nobody caught."
```

Story ① is the *automation* product (variables from day 1). Story ② is the *explanation*
product (outlier root-cause). They are one design because they share a root cause: **the
interesting attribute is the one nobody declared.** Today's built machinery (ch. 04) answers
"when?" for variables someone hand-declared (`corr-route`, `corr-region` were written by hand
for the demo). This layer removes the hand.

## Where the cost problem comes from (and the way out)

The obvious approach — store every variable of every instance — dies on arithmetic:

```
   1M instances/day × 50 variables  =  50M rows/day, forever, in the analytics store
   and every "break down by X" query scans them                              ✘

   the way out: aggregate analytics never needs per-instance variable rows —
   it needs BOUNDED AGGREGATES  +  a small raw remainder, CHOSEN not collected
```

Four layers deliver that. Overview picture, then each layer with its example:

```
               ┌────────── ① PROFILER (always on, tiny) ──────────┐
               │  knows every variable's shape — never its rows   │
               └───┬──────────────────┬─────────────────┬─────────┘
     promotion     │                  │ "what is slow?" │ powers the
     evidence      ▼                  ▼ thresholds      ▼ variables panel
┌── ② PROMOTION ──────┐   ┌── ③ STRATIFIED SAMPLE ──┐   "route: 3 values…"
│ auto-declares cubes │   │ the chosen raw remainder │
│ (feeds ch. 04's     │   └──────────┬───────────────┘
│  built consumers)   │              ▼
└─────────────────────┘   ④ the outlier CENSUS → "outliers WHEN…" + real examples
```

---

## Layer ① — the profiler: know every variable, store none of them

Stage 1 already keeps each instance's variables in its state store while the instance runs (it
needs them for fact enrichment; they're evicted at the end) — a heap write-cache over RocksDB,
snapshotted into every atomic cut (ch. 06 §4), not memory-only. The profiler taps that — **raw
values never leave Stage 1** — and maintains one summary row per variable name:

```
   profile of claim-process (what the panel shows after a few minutes of traffic):

   ┌────────────┬─────────┬───────────┬───────────────────────────┬──────────────────┐
   │ variable   │ seen on │ distinct  │ top values                │ numeric shape    │
   ├────────────┼─────────┼───────────┼───────────────────────────┼──────────────────┤
   │ route      │  100%   │ 3         │ auto 65 / manual 30 / …   │ —                │
   │ amount     │  100%   │ ~2 100    │ —                         │ p50 2.4k p95 4.8k│
   │ fraudulent │   5%    │ 2         │ false 60 / true 40        │ —                │
   │ customerId │  100%   │ ~4 700    │ —                         │ —                │
   └────────────┴─────────┴───────────┴───────────────────────────┴──────────────────┘
```

Cost check: 10M instances/day with these 4 variables = **4 rows**, updated in place. The
"distinct" column is an HLL sketch, the "numeric shape" a KLL sketch (ch. 02) — so the profile
also yields *band edges* for numbers and *p95 thresholds* per process and per variant, which
layers ② and ③ consume.

Even alone, this changes the product feel: the report builder stops offering a blank
`var.____` text box and instead offers a menu of things that demonstrably exist, with their
observed values. (That menu is how Amplitude/Mixpanel feel effortless — it's their "lexicon".)

## Layer ② — promotion: variables COMPETE for a fixed number of cubes

An open door would explode (processes can carry hundreds of names, some generated). So
promotion is a funnel where every stage is capped:

```
1 300 names seen on the process                     (hostile reality)
     │
     ▼  collapse generated families:   item_1, item_2, … item_442 → "item_*"
     │                                 (strip trailing digits/uuids, cluster)
     ▼  profiler admission cap:        track at most ~200 names;
     │                                 overflow = one "(other names: ~1.1k)" row + alarm
     ▼  scoring: who deserves a cube?
     │
     │     candidate     present on   distinct   stable?   verdict
     │     route            100%          3        yes      ★★★ promote
     │     amount           100%      numeric      yes      ★★★ promote (banded)
     │     debugNote          1%        ~400        no      ✘ suppress
     │     customerId       100%      ~4 700       yes      ✘ suppress (profiled only)
     │
     ▼  promotion slots:  at most ~15 cube-shapes per process; a better variable
        can EVICT a worse one (stop folding, keep old data, mark dormant)
```

What promotion actually *does*: writes the same dataset declarations a human would (the
`corr-*` cubes of ch. 04) through the existing hot-reload provisioning. The consuming side —
classifier, endpoints, chips — needs **zero changes**; it was hardened to ignore malformed cubes
precisely so machine-written ones are safe. Numeric variables get **bands from their own profile
sketch** (`amount → <1k / 1k-2.5k / 2.5k-5k / 5k+`), which is what makes them usable as
dimensions at all.

**The tangible payoff — life of one brand-new variable:**

```
   min 0    first instance with discount=15 completes
   min 2    profile row exists: "discount — numeric, on 92%, p50 12, p95 48"
   min 2    promoted (numeric, present, bandable) → corr-discount + corr-variant-discount
            auto-declared; facts start carrying `discount` (the existing name-targeted
            enrichment — the promotion budget IS the capture budget)
   min ~8   first windows finalize. The Performance page now shows, unasked:

            Outlier correlation:   discount=50+   n=214   lift ×3.4
            Top variants:          manual-loop …  [discount=50+ ×2.8]
```

The honest price: the first ~N instances of a brand-new variable predate its promotion and are
missing from its cubes. Minutes, not days — and layer ③ still has them.

## Layer ③ — the stratified sample: keep the interesting ones, sample the boring ones

The one place raw instances (with ALL their variables) persist — but *chosen*, not collected.
First, why plain sampling fails:

```
   10 000 instances, uniform 1-in-100 sample:

   the boring bulk (9 800)   ●●●●●●●●●●●●●●●  →  ~98 kept   ✔ fine
   very slow        (150)    ▲▲▲              →  1–2 kept   ✘ can't explain anything
   incidents        (40)     ✖                →  ~0 kept    ✘
   rare variant     (10)     ?                →  0 kept     ✘ the ones you'll click on!
```

The fix: sort first, then sample each group at its own rate — and record the rate:

```
instance completes → which stratum?                        from 10 000 instances:
┌──────────────────────────────────────────────┬────────┬─────────────┐
│ had an incident / was terminated             │ keep ALL│  ~50 rows  │ weight 1
│ slower than p95 OF ITS OWN VARIANT           │ keep ALL│ ~150 rows  │ weight 1
│   (threshold: the profiler's rolling sketch; │        │            │
│    a GLOBAL fence would flood on slow        │        │            │
│    variants & miss fast-variant anomalies)   │        │            │
│ variant still new (sketch has < 30 obs)      │ keep ALL│  few      │ weight 1
│ first 5 per variant per day ("exemplars")    │ keep    │  ~15      │ display-only
│ everything else (the bulk)                   │ 1-in-100│  ~98 rows │ weight 100
│   hash(instanceKey) % 100 == 0 — determinis- │        │            │
│   tic, so replays make the SAME decision     │        │            │
└──────────────────────────────────────────────┴────────┴─────────────┘
                                       total: ~300 rows instead of 10 000
```

What a stored row looks like — note the weight, and that it keeps *all* variables:

```
instance │ duration │ stratum  │ weight │ variant     │ variables (ALL of them)
─────────┼──────────┼──────────┼────────┼─────────────┼──────────────────────────────
4711     │ 48s      │ SLOW     │   1    │ manual-loop │ {route:manual, amount:4300,
         │          │          │        │             │  channel:partner, clerk:"j.doe"}
4802     │ 3.1s     │ BULK     │  100   │ happy-path  │ {route:auto, amount:120, …}
```

Query rule (worth engraving): **every count multiplies by weight; error shrinks with √rows.**
Keep-all strata give exact numbers; the bulk gives estimates with visible bars:

```
"which paths do SLOW claims take?"     stratum = SLOW is a CENSUS (weight 1):
    manual-loop  64%   ← exact, every row clickable
"…compared to all claims?"             bulk × 100:
    manual-loop  ~30% ± 4   ← estimate, honest bar
⇒ the loop path is ~2.1× over-represented among slow claims
```

Exemplar rows are for *showing* (click → the real case in Operate), never for counting — they
were selected *because* of their variant, so counting them would bias the estimates.

## Layer ④ — the outlier census: the explanation product

The full pipeline behind "there are outliers — when, prove it, keep watching":

```
① DETECT     fence per element/variant          built (ch. 04)     exact
② QUANTIFY   outlier share per time window      built              exact — a Tuesday
                                                                   deploy shows as a step
③ EXPLAIN-1  one-variable lift chips            built (ch. 04)     exact, promoted vars
④ EXPLAIN-2  multi-variable rules               THIS layer         approx, ANY attribute
⑤ PROVE      exemplars → Operate                THIS layer
   WATCH     rule → RATIO meter                 meter built (ch. 02)
```

Stage ④ works because the slow/incident strata keep **every** outlier with **all** its
variables — a complete "positive class" of a few hundred rows. Finding "outliers when…?" is then
a small contrast search, done interactively at click time:

```
condition                      among outliers   among normal      lift
──────────────────────────────────────────────────────────────────────
route=manual                        78%           29% ± 3         ×2.7
amount > 4 000                      64%           11% ± 2         ×5.8
route=manual AND amount > 4 000     61%            4% ± 1        ×15.3   ◄ the story
     exact (census)                      estimate (bulk×weight)
```

Search is greedy and shallow — best single conditions, then pairs of those; numeric split
points come from the profiler's bands; minimum support on the outlier side. Two conditions max
is a feature: three-way rules on sampled contrasts are where false discoveries live.

What makes this stage special: it sees **every captured attribute** —

```
unpromoted variables      `channel` before anyone promoted it
suppressed ones           `clerk` (4 700 values — never a cube, but over 200 census
                          rows, `clerk=j.doe covers 34% of outliers` is trivial)
structural columns        definition VERSION ("outliers when version=7" = the deploy),
                          variant, start hour, tenant — no variable needed at all
```

And the floor when nothing explains: *"no captured attribute explains these — here are 5 real
cases"* → a human opens them in Operate side by side.

The loop closes with one button:

```
┌─ Duration outliers — claim-process ──────────────────────────────────┐
│ 214 outliers (4.2%, was 1.4% last period ▲)                          │
│ concentrate WHEN:  route=manual AND amount>4 000    ×15 vs normal    │
│ examples: #4711 48s · #4802 51s · #4913 47s   → open in Operate      │
│ [ track this → "outlier rate where manual ∧ >4k" report ]            │
└──────────────────────────────────────────────────────────────────────┘
```

"Track this" creates a **matched-predicate RATIO meter** (already built): the discovered rule
becomes an exact, windowed, period-comparable metric from that moment on. Discovery is
approximate and on-demand; monitoring is exact and always-on; the button moves a question from
the first world to the second. A predictive *unpromoted* variable discovered here likewise
becomes a promotion signal — the demand-side evidence the scoring wants.

---

## Where any question lands (the ladder)

```
cubes            exact,   milliseconds,  forever     anticipated or promoted questions
census/sample    approx,  ~1 second,     ad hoc      questions nobody anticipated
facts replay     exact,   minutes, once              promoting a good question (future)
Operate          exact rows, per instance            drill-down — deliberately NOT ours
```

## Bounds and honesty (the closing slide)

```
profiler          rows = processes × names            capped + family-collapsed
promoted cubes    cells = names × values × windows    slots + "(other)" + alarms
sample            rows = rate × retention + outliers  weights make rates changeable
we give up        EXACT retroactive answers for never-promoted variables;
                  instance search inside analytics (Operate owns the raw rows)
every limit is VISIBLE: suppressed names listed with reasons, capped values,
sampled numbers wear error bars — a guardrail the user can't see is a bug
```

## Precedents (the "is this sane?" slide, one line each)

Datadog Metrics-without-Limits (ingest everything, index few, forward-only config),
ClickHouse JSON (auto-materialize hot paths, overflow bucket), Elasticsearch dynamic mapping +
field limits, Druid rollup (aggregate-only at ingest), Prometheus exemplars (aggregates point at
the raw-owning system), Amplitude/Mixpanel lexicon (the profiler's UX), Honeycomb/BlinkDB
(sampling with error bars). Our one uncommon move: promoting into *semantic cubes* (meters,
windows) rather than storage columns — possible because the declarative-dataset layer exists,
and the reason the profiler ships first: its evidence is what keeps that bet safe.
