# 02 — Meters: the arithmetic, and why streaming can't corrupt it

A **meter** is one column of a cube cell. Under the hood it is a tiny state machine with three
operations:

```
add(fact, acc)      fold one fact in           runs at ingest, once per fact
merge(a, b)         combine two accumulators   runs when windows tier up and when
                                               a range read combines many windows
result(acc)         produce the final number   runs at read time
```

This chapter goes meter by meter: what it answers, how it computes, a worked example, and the
proof — in plain words — that nothing about streaming can break it.

## Why proofs at all? The scenario that motivates everything

Facts arrive from several partitions, in whatever order the network delivers them. Machines
crash and replay. Range reads merge dozens of windows in whatever order the store returns them.
So the same data reaches the same cell along wildly different paths:

```
   Monday's reality:                        after Tuesday's crash + replay:

   merge(merge(A,B), merge(C,D))            merge(A, merge(merge(D,C), B))

        ●───┐                                    ●────────────┐
        ●───┴──┐                                 ●───┐        │
        ●───┐  ├──► cell                         ●───┴──┐     ├──► cell
        ●───┴──┘                                 ●──────┴─────┘

   Same four accumulators. Different grouping, different order.
   THE CELL MUST NOT CARE.
```

The property that makes it not care has a name: **merge must be a commutative monoid.** Three
small rules:

```
1. merge(a, b) = merge(b, a)                     order doesn't matter
2. merge(a, merge(b, c)) = merge(merge(a, b), c) grouping doesn't matter
3. merge(a, empty) = a                           an empty accumulator is invisible
```

Why these three suffice: *any* way of combining the same set of accumulators — any tree shape,
any order — is just a sequence of swaps (rule 1) and regroupings (rule 2) away from any other
way. If every swap and regrouping preserves the value, all paths end at the same value. That's
the entire trick. Every proof below is just "check the three rules".

One companion fact: chapter 01's exactly-once machinery guarantees the *same multiset of facts*
reaches the cell (no loss, no duplicates). The monoid guarantees that same multiset always
produces the *same numbers*. Together: replay-proof, order-proof, distribution-proof.

Two more shared notes before the meters:

- **Per-meter filters** (like `count WHERE transition = COMPLETED`) don't affect any proof —
  a filter just shrinks the fact multiset, identically on every path.
- **Numeric meters skip absent fields** (an implicit `NOT NULL` on the measure) — an instance
  without an `amount` contributes *nothing* to a SUM, rather than a phantom 0 that would drag
  averages down.

---

## The exact meters

### COUNT — "how many?"

```
accumulator: one integer n
add:    n + 1                    facts:  ●   ●   ●        windows:  [2] [1]
merge:  n₁ + n₂                          └─2─┘   1        merge:    [3]
result: n
```

**Proof.** Addition of integers: `a+b = b+a`, `(a+b)+c = a+(b+c)`, `a+0 = a`. All three rules
hold. Nothing else to check. ∎

### SUM — "how much in total?" (e.g. business value processed)

Same as COUNT, but adds the fact's measure instead of 1. Same proof — integer addition. ∎

### LEVEL — "how many/much RIGHT NOW?" (active instances, value in flight)

The clever one. Facts carry **signed deltas**: +1 when an instance starts, −1 when it ends
(or +amount / −amount for value). The accumulator just sums them.

```
time      09:00   09:01   09:05   09:07
event     A starts B starts A ends  C starts
delta       +1      +1      −1      +1
level        1       2       1       2        ◄─ at every moment: exactly
                                                 the instances still open
```

**Why the sum means "currently open" — the telescoping proof.** Group the deltas by instance:
every *finished* instance contributed +1 and −1 → net **0**, it vanishes from the sum. Every
*still-open* instance contributed only its +1. So the sum = number of open instances, always,
regardless of how starts and ends interleave. The merge is integer addition again (monoid ∎).

**The one engineering trap** (found by review, worth a slide): the exit delta must equal the
entry delta *exactly*. For value-in-flight, the entry amount is therefore **materialized on the
instance row at start** and subtracted verbatim at the end — if the end re-read the variable and
it had changed mid-flight, +100 −700 = −600 would stay on the gauge forever.

### MIN / MAX — "the extremes"

```
add:    min(m, x)        merge: min(m₁, m₂)        empty = +∞
```

**Proof.** `min(a,b) = min(b,a)`; `min(min(a,b),c) = min(a,b,c)` however you group it;
`min(a, +∞) = a`. Bonus property — **idempotence**: `min(a,a) = a`, so even an accidental
double-fold (which exactly-once already prevents) couldn't hurt these. Defense in depth. ∎

### EXECUTION_TIME — "count, average, min, max in one accumulator"

```
accumulator: (n, sum, lo, hi)          merge: componentwise (+, +, min, max)
result: avg = sum / n,  min = lo,  max = hi
```

**Proof.** Each component is one of the monoids already proven; combining them side by side
changes nothing (each component merges independently). One subtlety: the **average is computed
only at the very end**, from the merged sum and count. Averaging averages would be wrong:

```
window 1: {2s}            avg 2s ┐   avg of avgs   = (2+10)/2 = 6s   ✘
window 2: {8s, 10s, 12s}  avg 10s┘   merged sum/n  = 32/4     = 8s   ✔
```

∎

### STDDEV — "how spread out are the durations?"

The interesting proof. The naive formula `σ² = Σx²/n − (Σx/n)²` is mergeable but numerically
dangerous (two huge, nearly-equal numbers subtracted → catastrophic loss of precision for tight
distributions around large values — exactly what millisecond timestamps look like). Instead the
accumulator keeps:

```
(n, mean, M2)      where M2 = Σ (xᵢ − mean)²   "spread around the mean, pre-squared"
```

**add** (Welford's update — fold one value):

```
n' = n+1;   d = x − mean;   mean' = mean + d/n';   M2' = M2 + d·(x − mean')
```

**merge** (the parallel-variance formula):

```
d    = mean₂ − mean₁
n    = n₁ + n₂
mean = mean₁ + d · n₂/n                     the weighted middle
M2   = M2₁ + M2₂ + d² · n₁·n₂/n             spreads add, PLUS a correction for
                                            how far apart the two means were
result: σ = √(M2 / n)
```

**Worked example** — merge {2, 4} with {4, 8}:

```
   left:  n=2, mean=3, M2=(2−3)²+(4−3)² = 2
   right: n=2, mean=6, M2=(4−6)²+(8−6)² = 8

   merge: d=3, n=4, mean = 3 + 3·2/4 = 4.5
          M2 = 2 + 8 + 9·(2·2/4) = 19

   check directly on {2,4,4,8}, mean 4.5:
          6.25 + 0.25 + 0.25 + 12.25 = 19     ✔ identical
```

**Proof in words.** The correction term `d²·n₁n₂/n` is the between-groups spread: even if both
halves were internally tight, their means sitting apart *is* spread, and this term is exactly
how much. Expanding the definition of M2 over a combined multiset algebraically produces
precisely this formula (the standard parallel-variance identity by Chan et al.) — and since the
formula uses only each side's `(n, mean, M2)` and set union is order/grouping-blind, the three
monoid rules hold (up to floating-point rounding, which is why Welford's cancellation-free form
is used for `add`). ∎

### HISTOGRAM — "how many in each duration band?"

One counter per declared band; add bumps the band the value falls into; merge adds vectors.
**Proof:** a row of independent COUNT monoids. ∎

### RATIO — "what fraction met the condition?" (SLA, no-incident, first-time-right)

```
accumulator: (matched, total)
add:    total+1;  matched += (condition holds for this fact)
merge:  componentwise +
result: matched / total          total = 0 → "no data", never a fake 0%
```

Two condition forms: a measure comparison (`durationMs ≤ SLA`) or an arbitrary predicate
conjunction (the "matched" form — how a discovered rule becomes a tracked metric, ch. 05).

**Proof:** two COUNTs side by side; the condition is evaluated per fact *before* folding, so
every path sees the same matched/total increments. ∎

**The honest-interpretation bound** (not an arithmetic issue — a *meaning* issue): for a cohort
whose instances are still running, the final ratio isn't knowable yet, but it is **sandwiched**:

```
   10 started:  6 met SLA ✔   1 breached ✘   3 still running ?

   worst case: all 3 breach  →  6/10  = 60%   lower bound
   best case:  all 3 meet    →  9/10  = 90%   upper bound
```

Proof of the sandwich: each open instance ends as exactly one of met/breached, so the final
numerator lies between "none of them" and "all of them". The UI renders the band for maturing
windows instead of pretending a point value. ∎

---

## The sketch meters — approximate on purpose

Percentiles, distinct counts, and top-k lists over unbounded streams provably cannot be both
exact and constant-memory. These meters choose constant memory and buy back trust with **known,
published error bounds** — and their merges preserve those bounds (that is the "mergeable
sketch" guarantee the DataSketches library is built around). So the monoid obligation holds for
the *contract*: merging two sketches yields a sketch whose answers carry the same error bound as
one built from all the data directly. The bit patterns may differ across merge orders; the
guarantees don't.

### PERCENTILE — KLL quantile sketch (p50/p95/…, and the outlier fence)

```
   accumulator: a KLL sketch, ~few KB, regardless of whether it saw
                one thousand or one billion durations

   what it answers:      quantile(0.95) → "the value at rank 95%"
                         rank(14.3s)    → "the fraction of values ≤ 14.3s"

   the guarantee is on RANK, ±ε ≈ 1.7%:

   you ask for p95 ──►  you get a real value whose true rank is in [93.3%, 96.7%]
```

Why rank error (not value error) is the right contract for durations: real duration
distributions have huge tails. "±5 seconds" would be meaningless noise at the median and
absurdly strict at p99; "within ±1.7% of the population, guaranteed" is meaningful everywhere —
and it's exactly what the outlier fence needs (ch. 04). The exact `n`, `min`, `max` ride
alongside (their own exact monoids), so extremes are never smoothed away.

How to *think about* the internals (the intuition, not the full proof): the sketch keeps a few
hundred carefully chosen sample values in levels; each level up keeps every other value but
counts each survivor double. A value's rank is estimated from the weighted samples around it.
The published KLL theorem does two things we rely on: bounds the rank error at ε, and proves
**merging two sketches keeps the same ε** — which is the property that makes window tiering and
range reads legitimate. ∎ (by citation — this is the one proof this primer imports rather than
re-derives)

### DISTINCT — HyperLogLog ("how many different processes ran?")

```
   intuition: hash every key; look at the longest run of leading zeros ever seen.
   Seeing "8 leading zeros" is a 1-in-256 event — you've probably seen ~256 distinct
   keys. HLL keeps many such observations (registers) and averages them.

   accumulator: array of registers, each holding a MAX
   merge:       register-wise max
   result:      estimate, ±1–2% relative error
```

**Proof of the merge:** max is the idempotent commutative monoid (see MIN/MAX). And idempotence
buys the strongest replay story of any meter: folding the *same key* twice changes nothing at
all — the max is already there. A duplicate-heavy replay literally cannot move the estimate. ∎

### TOP_K — frequent-items sketch ("which processes dominate traffic?")

```
accumulator: a bounded map of counters; when full, all counters are decremented
             and evicted at zero — undercounting every survivor by a KNOWN amount
result:      top k items, each with [lower, upper] count bounds — DETERMINISTIC
             bounds, and the true count is always inside them
```

Guarantee: any item whose true share exceeds the sketch's threshold is guaranteed present, and
its reported interval always contains the truth. Merging adds intervals — still correct, just
wider. The UI shows estimates and treats near-ties at the cut honestly (they genuinely are
ambiguous). ∎ (by citation, same as KLL)

---

## Deprecated: the bundle meters

`execution_time_summary` and `lifecycle_summary` (grab-bag accumulators carrying several
numbers) still *decode* for old stores but can't be declared anymore. The same information is
now composed from the primitive meters above with per-meter filters — one column, one proof,
one filter each.

## The whole chapter on one slide

```
   exact      COUNT SUM LEVEL MIN MAX EXECUTION_TIME STDDEV HISTOGRAM RATIO
              merges are commutative monoids → any order, any grouping,
              any tiering: identical numbers  (each proof: ~3 lines)

   sketch     PERCENTILE (KLL)  DISTINCT (HLL)  TOP_K (frequent items)
              constant memory, published error bounds, merging preserves them

   shared     exactly-once delivers the same fact multiset (ch. 01);
              the monoid makes every path over that multiset agree (this ch.);
              filters shrink the multiset identically everywhere;
              absent measures contribute nothing, never zero
```

