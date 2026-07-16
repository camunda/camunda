# 04 — The analysis layer: outliers, correlation, variants

This chapter covers the three mechanisms that turn cube data into *explanations*: the outlier
fence, correlation lift, and the variant signature. Each section ends with the math argument.

---

## 1. Duration outliers: the boxplot fence over a sketch

**Question:** "which executions were abnormally slow — by this element's own standards?"

Every duration-carrying cell stores a KLL sketch (ch. 02). The sketch answers both directions:
rank → value and value → rank. The fence uses both:

```
        the element's own distribution (register, live data)

        │◄────────── the bulk ──────────►│              outliers
   ▂▄▆███████▇▅▃▂▁                        ┆          ▪   ▪ ▪     ▪
   ─────┬──────────┬──────────────────────╂──────────────────────────► duration
        Q1       median=4.4s   Q3=7.3s    ┃ fence = 14.3s
        │◄─── IQR ───►│                   ┗━ Q3 + 1.5 × IQR

   Q1, Q3  = sketch.quantile(0.25 / 0.75)          rank  → value
   share   = 1 − sketch.rank(fence)                value → rank      = 7.9%
   count   = round(share × n)                                        = 20
```

Why Q3 + 1.5·IQR and not a fixed threshold or "top 5%": the fence *adapts to each element's own
normality* — a tight element gets a tight fence, a naturally spread element a generous one — and
unlike "top X%" it can legitimately report **zero** outliers for a well-behaved distribution.
It is the classic Tukey boxplot rule (for a normal distribution the fence sits at ≈ μ + 2.7σ,
i.e. ~0.35% false-positive rate; for heavy-tailed real durations it flags the genuine tail).

**Correctness notes.**
- The share is a *rank* estimate, so its error is KLL's rank error (±ε ≈ 1.7%), independent of
how extreme the values are — the right guarantee for heavy tails.
- Elements with n < 20 are suppressed: quartiles of a handful of points are noise, and a fence
built on noise looks authoritative while meaning nothing.
- Degenerate case IQR = 0 (all durations equal): fence = Q3, share above it = 0 — correctly no
outliers, no NaN, no division anywhere.

---

## 2. Correlation: one fence, many populations, lift

**Question:** "do outliers concentrate WHEN a variable has a particular value?"

```
                                  ONE fence, computed from the OVERALL sketch
                                                   ┃
all instances        ▂▄▆████▇▅▃▂▁                  ┃▪ ▪   ▪       overallShare = 4.6%
                                                   ┃
route=manual only        ▂▄▆████▇▅▃▂▁              ┃▪▪ ▪ ▪ ▪      share(manual) = 5.4%
(its own sketch                                    ┃
 from the corr cube,                               ┃
 same fence line)                    lift = 5.4% / 4.6% = ×1.15
```

Definition and derivation:

```
   share(x)  = P(duration > fence │ var = x)      read from x's sketch: 1 − rank_x(fence)
   overall   = P(duration > fence)                read from the overall sketch
   lift(x)   = share(x) / overall                 = P(outlier │ x) / P(outlier)

   By Bayes:  lift(x) = P(x │ outlier) / P(x)     — the same number read the other way:
   "how over-represented is x among outliers, relative to its base rate."
   lift = 1  ⇔  independence;  lift ≫ 1  ⇔  x predicts outliers.
```

**Guardrails, each with its failure story:**
- `n(x) < 20` skipped — thin slices produce fence-noise (same reason as above).
- **Evidence floor**: if the overall outlier *count* is < 5, the whole card is empty. Without
it, a sketch-roundoff residual share (one stray observation → overall ≈ 0.1%) becomes the
denominator and mints spectacular pure-noise lifts (×50 from nothing).
- Both sides filter identically (COMPLETED, same measure, same grace) — a filter mismatch would
bias every lift, so it's pinned by review and test.

Honest reading of live results: the demo injects slowness *randomly*, so lifts hover near
×1.1–1.2 — weak, and correctly so. A strong lift in this data would have been a bug.

---

## 3. Variant & branch correlation: pure counting, same lift

For "which value predicts which **path**" no sketches are needed — only a joint count table
(the corr-variant / corr-branch cubes):

```
                       route=auto   route=manual        n(o,x) = joint count
   happy-path             2 900          220            n(x)   = Σ over outcomes
   manual-loop               60        1 380            n(o)   = Σ over values
                                                        N      = grand total

   lift(o,x) = ( n(o,x) / n(x) ) / ( n(o) / N )   =   P(outcome │ x) / P(outcome)
```

Guardrails: MIN_SUPPORT (n(o,x) ≥ 10), zero-denominator skips, marginals computed **inside the
corr cube only** (instances lacking the variable are absent from both numerator and denominator,
so they cannot distort the ratio), and for branches **one probability space per gateway** — with
G gateways per instance, pooling them would divide every share by ~G and skew lifts whenever
gateways sit in loops.

Two signature values worth recognizing on sight (both observed live):

```
   lift = 1 / P(x)      the outcome occurs ONLY under value x
                        (validation: manual-only variants at ×2.38 = 1/0.42 exactly)

   share = 1.0          every x-instance reaching the gateway took this branch
                        — the gateway's decision rule, recovered from behavior alone
```

The second one is a feature, not a triviality: when the chip on a gateway does NOT show ~1.0 on
the variable the model claims to decide on, the process disagrees with its documentation.

---

## 4. The variant signature: identifying "the path an instance took"

**Goal:** a stable 64-bit identity for "which elements ran, with which loop intensity",
insensitive to the arbitrary interleaving of parallel branches.

```
collect   (elementId → activation count) per instance, in Stage-1 state
bucket    count 1 → 1;  2–3 → 2;  4+ → 3          (loops collapse to intensity classes)
hash      h(e) = avalanche( FNV-1a(elementId bytes) ⊕ bucket )
fold      variantHash = h(bpmnProcessId) ⊕ h(e₁) ⊕ h(e₂) ⊕ …
```

**Proof of order-insensitivity:** XOR is commutative and associative, so the fold over the set
{h(e₁)…h(e_k)} is identical for every arrival order — parallel-gateway interleavings and replay
reorderings cannot change the hash. (Validated live: 44 shipping instances with a parallel
fork∥join collapsed to exactly one variant.)

**Why each ingredient is there:**
- *Bucketed counts*: without them, a loop that ran 2 vs 3 times would be different variants and
the variant space would explode; the bucket keeps the loop *signal* (1 vs "a few" vs "many")
while bounding cardinality by the process's structure.
- *Avalanche mixing*: raw FNV concentrates entropy in low bits; XOR-combining many raw values
would correlate. The splitmix64-style finalizer spreads every input bit across the word, making
the XOR of k hashes behave like a random 64-bit value (collision probability ≈ k²/2⁶⁵ across
k variants — negligible at process scale).
- *Process-id seed*: the variant dictionary is keyed by hash alone; two processes with identical
element ids (copied models, default modeler ids) must not collide. Seeding with
h(bpmnProcessId) makes cross-process equality impossible rather than merely unlikely-ish.
- *Recompute-at-end, never patch*: the hash is built from the stored counts when the instance
ends, so a bucket transition mid-flight cannot desynchronize anything.
- *Determinism*: byte-level hashing, no JVM hash, no randomness — the same instance replayed on
any partition yields the same hash, which the exactly-once story requires.

**Terminated instances** fold their partial element set — a real variant ("died after fork,
before join"), deliberately distinguishable from the completed path.

**The display companion** (`variantElements`: sorted, loop-annotated, 512-char-capped string) is
NOT the identity — it exists so humans can read the dictionary; sorting makes it as
order-insensitive as the hash.

---

## 5. What runs where (the cost ledger)

```
at INGEST, per fact:      sketch update, counter bumps          — O(1) each
at READ, per card:        merge range sketches, 2 rank queries,
                          a few divisions over ≤ hundreds of rows — milliseconds
NEVER:                    scanning instances, storing raw durations,
                          recomputing history
```

