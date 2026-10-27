# Why Hash-Based Sampling Works With Sparse Events

## The Setup

The Zeebe partition log contains ALL records — commands, events, rejections — for all value
types. Positions are sequential integers: 1, 2, 3, ..., 1,000,000, ...

The analytics exporter only cares about a tiny subset. For example, `PI_CREATED` events
might appear at positions like:

```
All log positions:  1  2  3  4  5  ... 1001 1002 ... 2001 ... 3001 ... 99001 ...
PI_CREATED events:  1                   1001            2001     3001     99001
```

100 relevant events scattered among 100,000 total records. The rest are jobs, deployments,
timers, variables, etc.

## The Sampling Check

Sampling happens AFTER the RecordFilter already selected only relevant events. The handler
receives a matched record and asks: "should I sample this?"

```java
boolean shouldSample(long position, double rate) {
    // rate = 0.01 means 1%
    // hash distributes any input uniformly into [0, 9999]
    return (hash(position) % 10000) < (rate * 10000);
}
```

For rate = 0.01 (1%), this checks: `hash(position) % 10000 < 100`.

## Why It Works — The Key Insight

A good bit-mixing function (e.g., Stafford Mix13 or Murmur3) is designed so that
**for any set of distinct inputs, the outputs are approximately uniformly distributed
across the output range.** This is not a mathematical guarantee but holds well in
practice for the sequential/sparse integer inputs we see in log positions.

This means:
- If you feed it {1, 2, 3, ..., 100}, roughly 1% will land in [0, 99] out of [0, 9999]
- If you feed it {1, 1001, 2001, ..., 99001}, roughly 1% will land in [0, 99] out of [0, 9999]
- If you feed it {7, 42, 99999, 123456789}, roughly 1% will land in [0, 99] out of [0, 9999]

**It does not matter how sparse or clustered the input positions are.** The hash function
scrambles any pattern in the inputs.

## Concrete Example

100 PI_CREATED events at positions 1, 1001, 2001, ..., 99001. Rate = 1%.

```
hash(1)     % 10000 = 4217  → 4217 >= 100 → skip
hash(1001)  % 10000 = 0712  → 712  >= 100 → skip
hash(2001)  % 10000 = 0043  → 43   <  100 → SAMPLED ✓
hash(3001)  % 10000 = 8891  → 8891 >= 100 → skip
hash(4001)  % 10000 = 3344  → 3344 >= 100 → skip
...
hash(55001) % 10000 = 0008  → 8    <  100 → SAMPLED ✓
...
hash(99001) % 10000 = 5567  → 5567 >= 100 → skip
```

Result: ~1 out of 100 relevant events sampled. The sparsity of positions (every 1000th)
is irrelevant — the hash output is uniform regardless.

## Why NOT Plain Modulo

Plain modulo (`position % 100 < 1`) does NOT have this property. It depends on the actual
distribution of positions:

```
Positions: 100, 200, 300, 400, 500, ...
100 % 100 = 0  → SAMPLED
200 % 100 = 0  → SAMPLED
300 % 100 = 0  → SAMPLED   ← 100% sampled! All are multiples of 100.
```

```
Positions: 1, 1001, 2001, 3001, ...
1    % 100 = 1  → skip
1001 % 100 = 1  → skip
2001 % 100 = 1  → skip     ← 0% sampled! All have remainder 1.
```

Modulo preserves patterns in the input. Hash destroys them. For sampling, we need uniform
output — hash is required.

## Replay Safety

`hash(position)` is a pure function of the record's log position. No external state needed.

- Same record → same position → same hash → same sampling decision
- Broker crash + restart → same records replayed → same sampling decisions
- Snapshot restore → same records replayed → same sampling decisions
- No metadata dependency for the sampling decision itself

## Probabilistic vs Exact

Hash-based sampling is probabilistic: 1% rate gives approximately 1% of events, not
exactly. Over 100 events you might get 0 or 3. Over 10,000 events you'll get ~100 ± 10.

For analytics-grade data this is fine. The `sample_rate` attribute on each event tells
the server the configured rate, so it can extrapolate totals correctly in aggregate.

## What Hash Function?

Requirements: fast, deterministic, good distribution. No cryptographic strength needed.

A simple bit-mixing function (like Java's `Long.hashCode()` with additional mixing, or
a Murmur3-style finalizer) is sufficient. The input is already a unique 64-bit integer
(log position), so we just need to break its sequential pattern.

Example (Stafford Mix13 variant, used in SplittableRandom):

```java
static long mix(long x) {
    x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
    x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
    x = x ^ (x >>> 31);
    return x;
}
```

