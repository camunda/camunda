# Write-behind cache: Phase 7 results

These are the benchmark results for the write-behind cache (see
[write-behind-cache-design.md](write-behind-cache-design.md), Phase 7): local benchmarks on one
machine, and a load test on Kubernetes.

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
- **Load test:** on Kubernetes, with the same total memory, the cache gained no throughput. While
  the cluster was healthy, it doubled processing p99 (55 against 24 ms). Once the state outgrew
  RocksDB's memory, both runs were CPU-bound, and the cached one built a ten times larger export
  backlog.

**Recommendation: no-go.** At these gains, the cache doesn't justify its complexity and heap cost.
The load test with more state than RocksDB keeps in memory didn't change that, although it ran
CPU-bound rather than disk-bound, so it doesn't fully settle that case (see
[Kubernetes load test](#kubernetes-load-test)).

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

## Kubernetes load test

**Setup:**
- An image built from `5e504d42815`, on 3 brokers with 3 partitions and replication factor 3. Each
broker had 3 CPUs and 3 GiB. Elasticsearch was the secondary storage. The brokers ran on spot
nodes, because the stable node pool had no capacity.
- RocksDB used the `PARTITION` strategy, with access metrics at `FINE` and statistics on.
- Both runs had 1 280 MiB of heap plus RocksDB per broker. The baseline had a 512 MiB heap and
256 MB of RocksDB per partition. The cached run had an 800 MiB heap, with room for the caches of
all three partitions on a broker, followers included, and 160 MB of RocksDB per partition.
- **Workload:** one service task per instance, with an 18 KB payload. Instances started at 150/s,
and at 250/s from 16:48 UTC. One polling worker completed about 48 a second, oldest jobs first,
so the state grew and every completion read old state. Both runs went from 16:20 to 23:15 UTC on
11 November 2026.

Windows: A is 16:25–16:48 at 150/s, healthy, with 0.1–0.3 GiB of state per partition. B
(17:20–18:20), C (19:30–20:30) and D (22:00–23:00) are at 250/s, and overloaded in both runs. The
figures come from the load tests' metrics reports.

| Window |  Run   | Created/s | Completed/s | Backpressure | Processing p50 / p99 | Client p99 |   Heap   |   Disk   | Export backlog |
|--------|--------|-----------|-------------|--------------|----------------------|------------|----------|----------|----------------|
| A      | off    | 150.0     | 47.5        | 0%           | 2.9 / 24 ms          | 47 ms      | 0.47 GiB | 4.1 GiB  | 333            |
| A      | cached | 150.0     | 47.3        | 0%           | 2.9 / 55 ms          | 106 ms     | 0.75 GiB | 4.3 GiB  | 494            |
| B      | off    | 250.0     | 39.0        | 14%          | 26 / 560 ms          | 1 639 ms   | 0.47 GiB | 10.9 GiB | 2 253          |
| B      | cached | 245.4     | 33.1        | 32%          | 82 / 664 ms          | ≥ 5 s      | 0.78 GiB | 11.1 GiB | 5 946          |
| C      | off    | 210.2     | 26.5        | 54%          | 168 / 687 ms         | ≥ 5 s      | 0.48 GiB | 18.5 GiB | 1 222          |
| C      | cached | 223.1     | 26.1        | 53%          | 153 / 685 ms         | ≥ 5 s      | 0.78 GiB | 23.3 GiB | 14 465         |
| D      | off    | 227.0     | 26.9        | 53%          | 171 / 642 ms         | ≥ 5 s      | 0.46 GiB | 25.8 GiB | 1 357          |
| D      | cached | 216.3     | 25.9        | 54%          | 159 / 671 ms         | ≥ 5 s      | 0.76 GiB | 34.1 GiB | 14 195         |

The client latency histogram stops at 5 s.

Detailed metrics were only collected during the ramp-up, in the 5 minutes before 16:23 UTC, with
about 0.07 GiB of state per partition. They come from live Prometheus queries:

- **Read cache:** 72% of cache gets missed. RocksDB served 12 000 reads a second at 18 µs on
  average, against 40 000 at 6.3 µs without the cache. Its data-block cache hit rate fell from 94
  to 82%.
- **Writer:** 31% fewer keys were written than committed. At most 0.25 MiB was pending, commits
  never waited, snapshot catch-up took 0.03 ms against a 12.6 ms checkpoint, and nothing failed.
- **Heap:** the live set after GC peaked at 401 MiB against 213 MiB, and the longest GC pause took
  271 ms against 35 ms.

What it shows:

- While the cluster was healthy, the cache didn't change throughput, but it doubled processing and
  client p99, used 0.28 GiB more heap, and paused for GC much longer.
- With large state, in C and D at about 7 GiB per partition on disk, far beyond RocksDB's 53–85 MiB
  block cache and the brokers' roughly 1.2 GiB of page cache, the cache gained no throughput:
  completions were 1.5% and 3.6% lower. The cached run's export backlog was ten times larger and
  its disk use up to 32% higher. A likely reason is that its brokers were throttled for 90% of the
  time instead of 80%, so the exporter fell behind and held back log compaction, but that isn't
  proven.
- From 17:20 UTC both runs were CPU-bound, after the start rate went up to 250/s, so the
  large-state windows don't isolate RocksDB's disk reads. The detailed metrics weren't collected
  for those windows, because access to Prometheus expired. A baseline broker was preempted at about
  17:02 UTC and recovered.

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
6. **The cluster agrees.** On Kubernetes, the cache added latency and heap while the cluster was
   healthy, and gained nothing once the state outgrew RocksDB's memory.

## Not done

- **A disk-bound comparison.** The load test's large-state windows were CPU-bound. A run at a rate
  the cluster sustains, with the detailed metrics collected throughout, would isolate RocksDB's
  disk reads. Nothing so far suggests the cache would win there.
- **The off-heap tier and `mayExist`.** Neither addresses the findings above.
- **An ADR.** It's only planned for a go.

