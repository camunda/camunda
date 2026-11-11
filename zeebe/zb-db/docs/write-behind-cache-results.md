# Write-behind cache: Phase 7 results

These are the local benchmark results for the write-behind cache (see
[write-behind-cache-design.md](write-behind-cache-design.md), Phase 7). The realistic load test on
Kubernetes hasn't been run yet.

## Summary

- **Throughput:** the cache gains 4–5% at most. That's in the same range as the difference between
  two runs of the same configuration. On this machine, zb-db isn't on the critical path: RocksDB,
  with its WAL off, already keeps this state in memory.
- **Read cache:** 61–65% of the reads that reach it miss. The engine reads many keys once, or reads
  keys that don't exist, and every load can evict a useful entry.
- **Writer:** grouping and combining writes removes about a fifth of the writes to RocksDB.
  Snapshot catch-up takes under 4 ms, next to a 100–250 ms checkpoint. Back-pressure never
  engaged.
- **Cost:** with small values, the live heap grows by about the configured budget. With 1 MiB
  values, it grows by more than the budget. With small values, allocation goes up by 28% and GC
  pause time more than doubles. Recovery takes 11 ms longer.

**Recommendation: no-go.** At these gains, the cache doesn't justify its complexity and heap cost.
Only a load test with more state than RocksDB keeps in memory could change that, so that's the
scenario to run before closing the experiment.

## Setup

All runs were on one MacBook Pro (M3 Pro, 12 cores, 36 GB), JDK 21, one run after another, with
nothing else running. The cache used its defaults unless stated otherwise: `cacheSize` 64 MiB,
`maxPendingSize` 32 MiB, `batchSize` 1 000 and `maxWriteDelay` 100 ms, per partition.

The benchmarks ran through temporary, uncommitted changes:

- `EngineLargeStatePerformanceTest` passed the `zeebe.db.writeBehind.*` properties to its JMH fork.
  With the cache on, it gave RocksDB 96 MiB less memory (416 instead of 512 MiB), so that both
  runs used the same total.
- `LargeStateControllerPerformanceTest` wrapped its factory in the cache.
- A throwaway IT ran a `TestStandaloneBroker` for 60 s with 8 clients creating process instances
  (one service task each) and a job worker. It took a snapshot every 5 s, and sampled the broker's
  Prometheus metrics every second.

## Engine: `EngineLargeStatePerformanceTest`

One partition with 200 000 instances of state; each operation creates an instance and waits for
its job.

|        Configuration         |    ops/s    |
|------------------------------|-------------|
| off, run 1                   | 1 914 ± 95  |
| on (100 ms, 1 000), run 1    | 1 962 ± 81  |
| off, run 2                   | 1 764 ± 86  |
| on (100 ms, 1 000), run 2    | 1 793 ± 115 |
| on, `maxWriteDelay` 10 ms    | 1 827 ± 109 |
| on, `maxWriteDelay` 1 000 ms | 2 041 ± 63  |
| on, `batchSize` 100          | 2 010 ± 70  |
| on, `batchSize` 10 000       | 1 919 ± 70  |

The two baseline runs differ by 150 ops/s. On average, the runs with the cache are 4.7% faster
than the baseline (1 925 against 1 839 ops/s), and each interleaved pair differs by 2%. No setting
in the sweep stands out from the noise.

## Broker recovery: `LargeStateControllerPerformanceTest`

Recovering a 0.5 GB snapshot into the runtime directory and opening it:

| Configuration | Recoveries/s |
|---------------|--------------|
| off, run 1    | 8.78 ± 0.19  |
| off, run 2    | 8.52 ± 0.36  |
| on, run 1     | 8.05 ± 0.23  |
| on, run 2     | 7.69 ± 0.81  |

That's about 11 ms more per recovery: the cost of starting the cache and its writer.

This benchmark doesn't run on `main`. Its JMH fork lacks
`--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED`, and its state generator never ends,
because RocksDB discards each batch on close when the WAL is off. For these runs, the WAL was on
while generating the state.

## Broker, small values: 1 partition

Instances created in 60 s:

|        Configuration         |           Runs            |  Mean   |
|------------------------------|---------------------------|---------|
| off                          | 129 120, 130 780, 126 220 | 128 707 |
| on (100 ms, 1 000)           | 132 849, 131 486, 137 880 | 134 072 |
| on, `maxWriteDelay` 10 ms    | 134 100                   |         |
| on, `maxWriteDelay` 1 000 ms | 137 244                   |         |

Every run with the cache created more instances than every run without it, but the mean gain
(4.2%) is close to the spread between baseline runs.

What the cache's metrics showed, for a run at the defaults:

- **Writer groups:** at about 2 200 instances per second, groups close on `batchSize`, not on
  `maxWriteDelay`. A storage transaction holds 40 committed transactions and 777 keys on average,
  so `maxWriteDelay` only matters at lower load. With 10 ms, groups shrink to 26 transactions.
- **Combining:** 3.75 M committed keys became 2.90 M storage writes (23% fewer): 1.80 M upserts
  and 1.10 M deletes.
- **Reads:** 4.15 M cache gets and 2.69 M loads (a 65% miss rate), plus 0.75 M reads answered by
  unflushed versions. 0.2% of lock-free reads were retried.
- **Read cache:** it stays at its 64 MiB limit and evicts about 3 M entries a minute.
- **Snapshots:** catch-up averages 0.3–0.6 ms (at most 1.7 ms); the checkpoint takes about
  100 ms.
- **Back-pressure:** never engaged. At most 140 KB and 43 transactions were pending.
- **Locks:** commits waited at most 0.03 ms for the commit lock. Scans held publications back for
  under 0.01 ms on average, and at most 5–28 ms.
- **Heap:** the live set after GC grew from 97 to 185 MB (+88 MB, against a 96 MiB budget).
  Allocation went from 978 to 1 256 MB/s (+28%), and GC pause time from 0.32 to 0.78 s per run.

## Broker, large values: 3 partitions, 1 MiB variables

|           Metric           |     Off      |      On      |
|----------------------------|--------------|--------------|
| Instances created in 60 s  | 5 140, 5 385 | 5 178, 5 293 |
| Live set after GC          | 1.74 GB      | 2.19 GB      |
| Allocation                 | 3 453 MB/s   | 3 582 MB/s   |
| GC pause time per run      | 0.64 s       | 0.91 s       |
| Read cache, all partitions |              | up to 208 MB |
| Pending, all partitions    |              | up to 25 MB  |
| Snapshot catch-up (max)    |              | 2.3–3.3 ms   |
| Checkpoint (mean)          |              | 236–252 ms   |

- Throughput is the same with and without the cache.
- The live set grew by 455 MB, against a budget of 288 MiB (3 × 96 MiB). The read cache (208 MB)
  and the pending versions (up to 25 MB) explain about half of that. The rest isn't attributed
  yet; a heap dump would show it.
- EhCache overshoots its byte limit by one or two entries per partition (about 69 MB against a
  64 MiB, or 67 MB, limit here), since it evicts after inserting.
- Back-pressure never engaged.

## What this means

1. **zb-db isn't the bottleneck.** With the WAL off and enough memory, a RocksDB write is a
   memtable insert, and reads hit the memtables and block cache. A heap cache in front of that
   saves little. This was risk 1 of the design.
2. **The engine's reads don't suit a small read cache.** Most reads that reach the cache miss, and
   every miss, including one for a key that doesn't exist, stores an entry and evicts another. A
   bigger cache only takes heap from RocksDB's block cache, which does the same job off-heap.
3. **Combining works, but saves cheap writes.** About a fifth of the writes disappear, but RocksDB
   writes were already cheap. The many deletes are mostly for keys that lived shorter than a
   group; the `ColumnFamily#mayExist` idea (§4.6) could skip them, but RocksDB deletes are cheap
   tombstones too.
4. **Snapshots and back-pressure aren't a concern.** Catch-up is a rounding error next to the
   checkpoint, and the pending budget was never reached.
5. **The cost is real.** More live heap, more allocation and GC, and a cache that can overshoot its
   limit, all for a gain inside the noise.

## Not done

- **The realistic load test** on Kubernetes, with `accessMetrics: FINE` and `enableStatistics`. It
  is the one scenario that could still change the decision: state larger than RocksDB's memory, so
  that RocksDB reads go to disk.
- **The off-heap tier and `mayExist`.** Neither addresses the findings above.
- **An ADR.** It's only planned for a go.

