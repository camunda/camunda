# RDBMS partition state, version 2: load-test results

Step 10 of the [version 2 plan](rdbms-storage-design-v2.md#19-implementation-plan): a load test of
version 2 against version 1 and against RocksDB, with and without the write-behind cache. It ran on
13 November 2026, from commit `94e32ea`, with the same setup as
[version 1's load test](rdbms-storage-load-test-results.md).

## Summary

- **Version 2 against version 1:** version 2 doesn't degrade as the state grows. It completed 21.5
  instances a second in both windows, with 82% backpressure. Version 1 completed 18.9 a second
  while the state was small and 9.9 once it had grown, with 84% and then 93% backpressure. With
  small state, version 2 completed about 14% more than version 1.
- **Version 2 against RocksDB:** the cluster still didn't keep up at 150 instances a second. It
  completed 54% fewer instances than the same-day RocksDB runs, and its brokers used 1.3 of their 3
  cores. Where the time goes isn't measured yet.
- **Cache over RocksDB:** as in version 1's load test, it kept up but added latency as the state
  grew: client p99 was 181 against 68 ms without the cache, in minutes 70–90.
- **Savings:** about the same as version 1's. Each broker used about 0.4 GiB less memory and a
  third to a half of the disk, as it keeps only its log.

**Outcome:** of the two versions, version 2 is the better one to build on. Whether either can carry
the load RocksDB carries depends on where the time goes (see [Next steps](#next-steps)).

## Setup

- Three clusters ran side by side on the benchmark Kubernetes cluster, through the
  `camunda-load-test.yml` workflow, each from an image built from `94e32ea`. Each had 3 brokers with
  3 CPUs and 3 GiB, and 3 partitions with replication factor 3, on spot nodes. Optimize and the read
  benchmarks were off.
- All three used PostgreSQL as secondary storage. In the third configuration, the partition state
  used that database and its settings, as version 2 requires: the run set only
  `primary-storage.type=rdbms`, the cache flag and the heap.
- **Workload,** unchanged from version 1's load test: the `latency` scenario with the starter at
  150 instances a second, one service task per instance and an 18 KB payload. One worker completed
  about 47 a second, oldest jobs first, so the state grew by about 100 instances a second.
- **Windows:** the figures come from the load tests' metrics reports, over two 20-minute windows.
  The first is minutes 10–30 after deploy (about 08:15–08:35 UTC), the second minutes 70–90 (about
  09:15–09:35 UTC).
- Every cluster passed its health check, 10 minutes after deploy, on the first try.

|   Configuration   |                        Partition state                        | Write-behind cache |  Heap   |    RocksDB memory    |
|-------------------|---------------------------------------------------------------|--------------------|---------|----------------------|
| Cache off         | RocksDB                                                       | off                | 512 MiB | 256 MB per partition |
| Cache, RocksDB    | RocksDB                                                       | on                 | 800 MiB | 160 MB per partition |
| Cache, PostgreSQL | PostgreSQL, the secondary storage's own database and settings | on                 | 800 MiB | none                 |

## Results

Each cell shows minutes 10–30, then minutes 70–90. The version 1 column is its run on 12 November.
≥ marks a value at the top of its histogram: 5 s for client requests, 90 s for data availability.

|              Metric               |   Cache off, RocksDB    |    Cache on, RocksDB    |      PostgreSQL, v2       |  PostgreSQL, v1 (12 Nov)  |
|-----------------------------------|-------------------------|-------------------------|---------------------------|---------------------------|
| Instances started /s              | 150.0 → 150.0           | 150.0 → 150.0           | 137.5 → 141.5             | 136.3 → 110.5             |
| Create requests at the gateway /s | 150.0 → 149.9           | 150.0 → 150.4           | 214.8 → 220.2             | 216.6 → 201.0             |
| Instances completed /s            | 46.6 → 46.4             | 47.3 → 44.7             | 21.5 → 21.5               | 18.9 → 9.9                |
| Backpressure, requests rejected   | 0% → 0%                 | 0% → 0%                 | 82.2% → 82.0%             | 84.3% → 92.8%             |
| Records processed /s              | 993 → 961               | 996 → 951               | 509 → 506                 | 454 → 213                 |
| Records exported /s               | 4 621 → 4 472           | 4 641 → 4 416           | 2 329 → 2 316             | 2 085 → 638               |
| Processing latency p50 / p99, ms  | 3.1 / 36 → 3.1 / 44     | 2.9 / 32 → 3.5 / 119    | 199 / 2 273 → 197 / 2 262 | 195 / 2 345 → 191 / 2 438 |
| Exporting latency p50 / p99, ms   | 9.5 / 195 → 7.9 / 961   | 7.2 / 187 → 8.9 / 1 464 | 9.9 / 76 → 9.7 / 92       | 11.1 / 74 → 11.5 / 73     |
| Client request p50 / p99, ms      | 18 / 73 → 17 / 68       | 9.5 / 66 → 19 / 181     | ≥ 5 000 / ≥ 5 000 → same  | ≥ 5 000 / ≥ 5 000 → same  |
| Data availability p50 / p99, s    | 0.50 / 1.0 → 0.50 / 3.3 | 0.49 / 1.0 → 0.52 / 4.0 | 12.2 / 16.6 → 11.9 / 14.9 | 12.3 / 14.9 → 13.6 / ≥ 90 |
| Exporting backlog, records        | 25 → 40                 | 44 → 57                 | 6 → 10                    | 11 → 267 548              |
| Processing backlog, records       | 67 → 103                | 30 → 44                 | 490 → 443                 | 533 → 385                 |
| Broker CPU used p50, cores of 3   | 1.68 → 1.66             | 1.53 → 1.90             | 1.35 → 1.32               | 1.26 → 1.07               |
| Broker CPU throttling             | 1.4% → 1.4%             | 0.8% → 5.2%             | 0.0% → 0.0%               | 0.0% → 0.0%               |
| Broker memory RSS, GiB            | 1.53 → 1.59             | 1.59 → 1.63             | 1.10 → 1.17               | 1.16 → 1.16               |
| Broker heap, GiB                  | 0.47 → 0.46             | 0.73 → 0.76             | 0.55 → 0.56               | 0.58 → 0.57               |
| Broker disk used, GiB             | 4.7 → 6.7               | 4.1 → 6.0               | 2.1 → 2.0                 | 2.1 → 2.5                 |
| Broker write IOPS                 | 4 548 → 4 480           | 4 541 → 4 546           | 2 519 → 2 503             | 2 171 → 970               |

"Instances started" counts the instances the starter created successfully. "Create requests" counts
every create request that reached the gateway, retries after backpressure included. The reports
have no PostgreSQL or cache metrics: their secondary-storage columns are empty.

On 13 November, the cache-off run used more CPU at the same throughput than on 12 November: 1.66–1.68
cores against 1.33–1.47, with client p99 at 68–73 against 48–57 ms. So compare latency and CPU
within one day. Throughput matched across both days.

## What it shows

- **Version 2 against version 1, small state:** a little better. It completed 21.5 instances a
  second against 18.9, and processed 509 records a second against 454, both about 12–14% more,
  with 82% backpressure against 84%.
- **Version 2 against version 1, grown state:** this is where they differ. Version 2 stayed where it
  was: 21.5 completions a second, 82% backpressure and an exporting backlog of 10 records. Version 1
  fell to 9.9 completions a second and 93% backpressure, and its history exporter fell 267 548
  records behind.
- **Version 2 against RocksDB:** still far off. It completed 54% fewer instances than the same-day
  RocksDB runs and processed about half as many records. Its client requests sat at the 5 s cap,
  and its brokers used 1.3 of their 3 cores against 1.5–1.9, without throttling.
- **The cache over RocksDB** repeated what version 1's load test showed: it kept up, but its latency
  grew with the state. In minutes 70–90 its client p99 was 181 against 68 ms without the cache,
  its processing p99 119 against 44 ms, and its CPU throttling reached 5%.
- **What it saved:** 1.10–1.17 GiB of memory per broker against 1.53–1.63 GiB, and about 2 GiB of
  disk against 4.1–6.7 GiB. Part of that comes from processing less.

### Why both versions' median processing latency is near 200 ms

The broker's request flow control defaults to AIMD with a 200 ms request timeout
([`StabilizingAIMDLimit`](../../logstreams/src/main/java/io/camunda/zeebe/logstreams/impl/flowcontrol/StabilizingAIMDLimit.java)),
and the load-test chart keeps that default. Whenever a command takes longer than 200 ms, the limit
on requests in flight goes down, and the rest are rejected. So when commands are slow, flow control
most likely holds processing latency near 200 ms and turns the excess into backpressure. That would
explain version 1's median of 195 ms and version 2's of 198 ms. The medians don't measure the
database; throughput and backpressure do.

## Where the time likely goes

The load tests were run with `gh` only, without access to Prometheus. So these are candidates, not
findings:

- **Read round trips aren't the whole limit.** Version 2 halved them per cache miss, and throughput
  with small state rose by only about 14%.
- **What both versions share:** the writer falling behind, so that commits wait at
  `max-pending-size`, and one PostgreSQL with 3 CPUs serving both the partition state and the
  history exporter.
- **Version 2's steadiness** matches its smaller tables per column family and an exporter that kept
  up. The runs can't separate the two.

## Next steps

1. Find where the time goes. Measure the cache's hit rate, commits waiting at `max-pending-size`,
   the writer's latency, and PostgreSQL's CPU and statement statistics (`pg_stat_statements` is on
   in the load-test chart). Also take a profile of a leader.
2. Give the partition state its own PostgreSQL, or a larger one, to rule out the shared 3 CPUs.
3. Lower the start rate to find the rate version 2 sustains.
4. Measure failover time.

## Runs

One run per configuration, on spot nodes. Pod restarts couldn't be checked without cluster access,
but every window saw 3 brokers. Version 1's run is listed in
[its results](rdbms-storage-load-test-results.md#runs).

|   Configuration   |                                 Load test                                  |                           Metrics, minutes 10–30                           |                           Metrics, minutes 70–90                           |
|-------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Cache off         | [43389282856](https://github.com/camunda/camunda/actions/runs/43389282856) | [43394219146](https://github.com/camunda/camunda/actions/runs/43394219146) | [43402384928](https://github.com/camunda/camunda/actions/runs/43402384928) |
| Cache, RocksDB    | [43389287034](https://github.com/camunda/camunda/actions/runs/43389287034) | [43394429766](https://github.com/camunda/camunda/actions/runs/43394429766) | [43402609496](https://github.com/camunda/camunda/actions/runs/43402609496) |
| Cache, PostgreSQL | [43389285178](https://github.com/camunda/camunda/actions/runs/43389285178) | [43394337106](https://github.com/camunda/camunda/actions/runs/43394337106) | [43402515317](https://github.com/camunda/camunda/actions/runs/43402515317) |

The namespaces were `c8-md-v2-off-20261113`, `c8-md-v2-rocks-20261113` and
`c8-md-v2-pg-20261113`. All three are deleted.
