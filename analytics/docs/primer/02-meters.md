# 02 — Meters: the arithmetic and why it survives streaming

A **meter** is one column of a cube cell: a streaming aggregation with three operations —

```
add(fact, acc)        fold one fact into an accumulator          (Stage 2, per fact)
merge(a, b)           combine two accumulators                   (window tiers, range reads)
result(acc)           materialize the final number(s)            (read time)
```

## The one proof obligation shared by every meter

Facts reach a cell from several sources, in nondeterministic interleavings, and range reads
merge many windows' accumulators in whatever order the store returns them. So every meter must
satisfy:

```
MERGE IS A COMMUTATIVE MONOID          and          ADD IS ORDER-INSENSITIVE
merge(a, b)          = merge(b, a)                  any arrival order of the same
merge(a, merge(b,c)) = merge(merge(a,b), c)         fact multiset → same accumulator
merge(a, empty)      = a
```

Why this suffices: any parallel/streamed evaluation is just some parenthesization and permutation
of the same multiset of facts. Commutativity + associativity means all parenthesizations and
permutations produce the identical accumulator — so distribution, window tiering, and range
merging can never change a number. Combined with the pipeline's exactly-once fold (chapter 01),
"same multiset" is guaranteed, and correctness follows. Each meter below states its accumulator
and why its merge is a commutative monoid. Per-meter **filters** (SQL `FILTER`-style) don't
change any proof: they only shrink the multiset, identically on every path. Numeric meters also
carry an implicit `NOT_NULL(measure)` filter, so an absent field contributes nothing instead of
a phantom zero.

---

## The exact meters (integer/scalar arithmetic — results are exact)

### COUNT

```
acc: n            add: n+1          merge: n₁+n₂          result: n
```

Proof: (ℕ, +, 0) is a commutative monoid. ∎

### SUM (measure field, e.g. `value`)

```
acc: s            add: s+x          merge: s₁+s₂          result: s
```

Proof: same monoid over integers; addition is commutative/associative. ∎

### LEVEL (signed deltas, e.g. `delta = +1/−1`, `valueDelta = ±amount`)

```
acc: s            add: s+δ          merge: s₁+s₂          result: running level
```

The level at time T = Σ of all deltas with event time ≤ T. Proof of meaning: each entity
contributes +v on entry and −v on exit, so the sum **telescopes** — every completed entity
contributes exactly 0, and the sum equals the total v of entities that entered but have not
exited: precisely "currently in flight". Merge is integer addition (monoid). The balance
guarantee needs one engineering invariant: the exit delta must equal the entry delta — which is
why the entry-time value is materialized on the instance row and subtracted verbatim at the end,
never re-read (a mid-flight change would otherwise leave a permanent residue). ∎

### MIN / MAX

```
acc: m            add: min(m,x)     merge: min(m₁,m₂)     result: m     (dually max)
```

Proof: (ℝ ∪ {+∞}, min) is a commutative idempotent monoid; idempotence additionally makes these
tolerant even of accidental double-folds (defense in depth beyond exactly-once). ∎

### EXECUTION_TIME (composite: count, sum, min, max over a duration field)

```
acc: (n, s, lo, hi)      merge: componentwise (+, +, min, max)      result: avg = s/n, min, max
```

Proof: a product of commutative monoids is a commutative monoid (componentwise). avg is derived
only at result time — never averaged across accumulators, which would be wrong for unequal n. ∎

### STDDEV (population standard deviation of a measure)

```
   acc: (n, mean, M2)   where M2 = Σ(xᵢ − mean)²

   add  (Welford):      n' = n+1;  d = x − mean;  mean' = mean + d/n';  M2' = M2 + d·(x − mean')
   merge (Chan et al.): d = mean₂ − mean₁
                        n = n₁+n₂
                        mean = mean₁ + d·n₂/n
                        M2 = M2₁ + M2₂ + d²·n₁·n₂/n
   result: σ = √(M2/n)
```

Proof sketch: define M2(S) = Σ_{x∈S}(x − mean(S))² for a multiset S. Expanding
M2(S₁ ∪ S₂) algebraically yields exactly the merge formula (the d²·n₁n₂/n term is the
between-groups contribution — the standard parallel-variance identity). Since the formula depends
only on (n, mean, M2) of each side and set union is commutative/associative, the merge is a
commutative monoid up to floating-point rounding; Welford's update is the n₂=1 special case, and
is used precisely because the naive Σx² − (Σx)²/n form is catastrophically cancellation-prone. ∎

### HISTOGRAM (fixed bands over a measure, e.g. duration buckets)

```
acc: (c₁, …, c_k) one counter per declared band     add: c_band(x) += 1     merge: vector +
```

Proof: a product of COUNT monoids. Bands are declared, so every accumulator has the same shape. ∎

### RATIO (matched / total, two forms)

```
   form A: matched = facts where measure ⟨op⟩ threshold      (e.g. durationMs ≤ SLA)
   form B: matched = facts satisfying a predicate conjunction (measure-less, e.g. STP)

   acc: (m, t)        add: t+1, m += [condition]        merge: (m₁+m₂, t₁+t₂)
   result: m/t        (t = 0 → "no data", never 0%)
```

Proof: product of two COUNT monoids; the condition is evaluated per fact before folding, so it
commutes with everything. One subtlety is *interpretation*, not arithmetic: for maturity-bound
cohorts (an SLA cohort whose instances are still running), m/t over completed-only facts is a
**lower bound** of the final ratio, and (m + open)/t an **upper bound** — the true final ratio is
sandwiched because each still-open instance can only end up matched or not. The UI renders the
band instead of pretending precision. ∎

---

## The sketch meters (sublinear space — results are approximate with known error)

These trade exactness for constant memory. Their accumulators are **mergeable sketches** from
Apache DataSketches; mergeability here means: the sketch of S₁ ∪ S₂ built by merging equals (in
distribution of its error) a sketch built from S₁ ∪ S₂ directly — so the monoid obligation holds
*for the guarantee*, not bit-for-bit.

### PERCENTILE (KLL quantiles sketch over a measure)

```
   acc: KLL sketch (~few KB)      add: sketch.update(x)      merge: KLL merge
   result: value at each declared rank (p50, p95, …), plus n/min/max (exact)

   guarantee: RANK error, not value error —
   the value returned for rank q has true rank within q ± ε, ε ≈ 1.7% at default size
```

Why rank error is the right contract for durations: "p95" means "faster than ~95% ± ε of
instances" — robust even for heavy-tailed distributions where value error would be meaningless.
KLL's proof (compactor sampling with geometrically decreasing survival) is in the literature;
what we rely on is the published mergeability theorem: merging preserves the ε bound. min/max/n
ride alongside exactly (their own monoids), so extremes are never smoothed away. ∎

### DISTINCT (HyperLogLog over a key, e.g. distinct processes per tenant)

```
acc: HLL sketch      add: sketch.update(key)      merge: HLL union (register-wise max)
result: estimate ± relative standard error (~1–2% at configured precision)
```

Proof of merge: HLL registers store maxima of hashed leading-zero counts; max is a commutative
idempotent monoid, so union is order-insensitive AND replay-tolerant (adding the same key twice
is a no-op by construction — the strongest dedup story of all meters). ∎

### TOP_K (frequent-items sketch, e.g. top processes by traffic)

```
acc: frequent-items sketch (bounded map + error counters)     merge: sketch merge
result: top k items, each with count bounds [lower, upper]
```

Guarantee: any item whose true count exceeds ε·N is present, and reported bounds always contain
the true count (deterministic, not probabilistic). Merging adds the error bounds — still sound,
wider. The UI shows estimates; ties near the cut are honestly ambiguous. ∎

---

## Deprecated bundles

`execution_time_summary` and `lifecycle_summary` (multi-number bundle accumulators) still decode
for old stores but are closed to new declarations — the same information is now composed from the
primitive meters above with per-meter filters, which keeps every column independently provable
and independently filterable.

## One-slide summary

```
exact meters      COUNT SUM LEVEL MIN MAX EXECUTION_TIME STDDEV HISTOGRAM RATIO
                  → commutative-monoid merges: no ordering, no distribution,
                    no tiering can change a number
sketch meters     PERCENTILE(KLL) DISTINCT(HLL) TOP_K(frequent-items)
                  → constant memory, published error bounds, merge preserves them
shared contract   same fact multiset in ⇒ same numbers out — guaranteed by
                  exactly-once (ch. 01) + the monoid property (this chapter)
```

