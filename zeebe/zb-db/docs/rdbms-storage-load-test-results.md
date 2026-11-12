# RDBMS partition state: load-test results

Step 12 of the [design's plan](rdbms-storage-design.md#12-implementation-plan): a first load test of
the partition state in PostgreSQL, against RocksDB with and without the write-behind cache. It ran
on 12 November 2026, from commit `9a6814d`.

## Summary

- **PostgreSQL:** the cluster didn't keep up at 150 instances a second. While the state was small,
  it rejected 84% of requests and completed 60% fewer instances than RocksDB. Once the state had
  grown, it rejected 93% and completed almost 80% fewer. Its brokers used 1.1–1.3 of their 3 cores,
  so they were waiting, not computing. Where the time goes isn't measured yet.
- **Cache over RocksDB:** it kept up, but added latency: client p99 was 64 against 48 ms with small
  state, and 138 against 57 ms later. With more state, it completed 15% fewer instances. That
  matches the [write-behind cache's own results](write-behind-cache-results.md).
- **Savings:** with the state in PostgreSQL, each broker used about 0.4 GiB less memory and about
  half the disk, as it keeps only its log. Part of that comes from processing less.

**Outcome:** in this form, the partition state in PostgreSQL can't carry a load that RocksDB carries
with about half its CPU. Before deciding on step 12, we need to find out where the time goes (see
[Next steps](#next-steps)).

## Setup

- Three clusters ran side by side on the benchmark Kubernetes cluster, through the
  `camunda-load-test.yml` workflow, each from an image built from `9a6814d`. Each had 3 brokers with
  3 CPUs and 3 GiB, and 3 partitions with replication factor 3, on spot nodes. Optimize and the read
  benchmarks were off.
- All three used PostgreSQL as secondary storage: a CNPG cluster, PostgreSQL 18.4, with 3 CPUs and
  8–12 GiB. So every configuration depends on one type of database. In the third configuration, the
  partition state went into that same database.
- **Workload,** the same as the write-behind cache's load test: the `latency` scenario with the
  starter at 150 instances a second, one service task per instance and an 18 KB payload. One worker
  completed about 47 a second, oldest jobs first, so the state grew by about 100 instances a second.
- **Windows:** the figures come from the load tests' metrics reports, over two 20-minute windows.
  The first is minutes 10–30 after deploy (18:24–18:44 UTC), the second minutes 70–90 (19:24–19:44
  UTC).
- **Discarded attempts:** two earlier attempts were thrown away. The workload settings copied from
  the cache's load test set `starter.replicas=0`, which turns the starter off. The brokers also
  waited for stable nodes that had no capacity. The measured runs are fresh deploys with the starter
  on and spot nodes.

|   Configuration   |                  Partition state                   | Write-behind cache |  Heap   |    RocksDB memory    |
|-------------------|----------------------------------------------------|--------------------|---------|----------------------|
| Cache off         | RocksDB                                            | off                | 512 MiB | 256 MB per partition |
| Cache, RocksDB    | RocksDB                                            | on                 | 800 MiB | 160 MB per partition |
| Cache, PostgreSQL | PostgreSQL, the same database as secondary storage | on                 | 800 MiB | none                 |

The two RocksDB configurations use the same memory budgets as the write-behind cache's load test:
1 280 MiB of heap plus RocksDB per broker.

## Results

Each cell shows minutes 10–30, then minutes 70–90. ≥ marks a value at the top of its histogram:
5 s for client requests, 90 s for data availability.

|              Metric               |   Cache off, RocksDB    |    Cache on, RocksDB     |   Cache on, PostgreSQL    |
|-----------------------------------|-------------------------|--------------------------|---------------------------|
| Instances started /s              | 150.0 → 150.0           | 150.0 → 150.0            | 136.3 → 110.5             |
| Create requests at the gateway /s | 150.0 → 150.0           | 150.1 → 150.1            | 216.6 → 201.0             |
| Instances completed /s            | 47.5 → 46.6             | 47.5 → 39.7              | 18.9 → 9.9                |
| Backpressure, requests rejected   | 0% → 0%                 | 0% → 0.02%               | 84.3% → 92.8%             |
| Records processed /s              | 990 → 993               | 997 → 957                | 454 → 213                 |
| Records exported /s               | 4 630 → 4 623           | 4 645 → 4 405            | 2 085 → 638               |
| Processing latency p50 / p99, ms  | 2.9 / 24 → 3.1 / 40     | 2.8 / 27 → 3.3 / 76      | 195 / 2 345 → 191 / 2 438 |
| Exporting latency p50 / p99, ms   | 6.8 / 172 → 7.1 / 489   | 6.3 / 162 → 8.1 / 1 602  | 11.1 / 74 → 11.5 / 73     |
| Client request p50 / p99, ms      | 8.1 / 48 → 12 / 57      | 7.5 / 64 → 18 / 138      | ≥ 5 000 / ≥ 5 000 → same  |
| Data availability p50 / p99, s    | 0.49 / 1.0 → 0.49 / 2.3 | 0.48 / 1.0 → 0.58 / ≥ 90 | 12.3 / 14.9 → 13.6 / ≥ 90 |
| Exporting backlog, records        | 36 → 70                 | 69 → 182                 | 11 → 267 548              |
| Processing backlog, records       | 48 → 46                 | 41 → 56                  | 533 → 385                 |
| Broker CPU used p50, cores of 3   | 1.33 → 1.47             | 1.35 → 1.69              | 1.26 → 1.07               |
| Broker CPU throttling             | 0.4% → 0.7%             | 0.6% → 4.1%              | 0.0% → 0.0%               |
| Broker memory RSS, GiB            | 1.53 → 1.56             | 1.59 → 1.69              | 1.16 → 1.16               |
| Broker heap, GiB                  | 0.47 → 0.48             | 0.74 → 0.74              | 0.58 → 0.57               |
| Broker disk used, GiB             | 4.7 → 6.1               | 4.3 → 6.6                | 2.1 → 2.5                 |
| Broker write IOPS                 | 4 410 → 4 560           | 4 519 → 4 373            | 2 171 → 970               |

"Instances started" counts the instances the starter created successfully. "Create requests"
counts every create request that reached the gateway, retries after backpressure included. The
reports have no PostgreSQL or cache metrics: their secondary-storage columns are empty.

## What it shows

- **RocksDB, with and without the cache:** both carried the load in both windows, without
  backpressure. The cache added latency: client p99 was 64 against 48 ms early, and 138 against
  57 ms late. Late on, the cached run completed 15% fewer instances (39.7 against 46.6 a second).
  Its data-availability p99 reached the 90 s cap while its exporting p99 rose to 1.6 s.
- **PostgreSQL, with small state:** 84% backpressure already. Of 217 create requests a second, 136
  got through, and 18.9 instances a second completed against 47.5. Processing latency was 195 ms at
  the median against 2.9 ms with RocksDB, and 2.3 s at p99 against 24–27 ms.
- **PostgreSQL, with more state:** worse again. Backpressure reached 93%, completions fell to 9.9 a
  second and records processed to 213 a second, and the export backlog grew from 11 to 267 548
  records.
- **The brokers waited rather than worked.** They used 1.1–1.3 of their 3 cores and were never
  throttled, against 1.3–1.7 cores in the RocksDB runs. A median processing latency near 190 ms
  means nearly every command waited, not just a few.
- **What it saved:** 1.16 GiB of memory per broker against 1.53–1.69 GiB, and 2.1–2.5 GiB of disk
  against 4.3–6.6 GiB. Part of that comes from processing less.

## Where the time likely goes

The load tests were run with `gh` only, without access to Prometheus. So these are candidates, not
findings:

- **Cache misses** read PostgreSQL on the stream processor's thread. The write-behind cache's
  benchmarks measured 61–72% misses, so most commands would wait for a database round trip.
- **Writes** for a partition run one database transaction at a time, as each one locks the
  partition's row (§7.5 of the design). If the writer falls behind, unwritten state reaches
  `max-pending-size` and commits wait.
- **One PostgreSQL with 3 CPUs** served both the partition state and the history exporter.

## Next steps

1. Find where the time goes. Measure the cache's hit rate, pending bytes, commit waits and writer
   latency, and PostgreSQL's statement statistics (`pg_stat_statements` is on in the load-test
   chart). Also take a profile of a leader.
2. Give the partition state its own PostgreSQL, to separate it from the history exporter.
3. Lower the start rate to find the rate this setup sustains.
4. Measure failover time, the design's second risk (§11).

## Runs

One run per configuration, on spot nodes. Pod restarts couldn't be checked without cluster access,
but every window saw 3 brokers.

|   Configuration   |                                 Load test                                  |                           Metrics, minutes 10–30                           |                           Metrics, minutes 70–90                           |
|-------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Cache off         | [43277328023](https://github.com/camunda/camunda/actions/runs/43277328023) | [43282844528](https://github.com/camunda/camunda/actions/runs/43282844528) | [43293165885](https://github.com/camunda/camunda/actions/runs/43293165885) |
| Cache, RocksDB    | [43277416790](https://github.com/camunda/camunda/actions/runs/43277416790) | [43282884683](https://github.com/camunda/camunda/actions/runs/43282884683) | [43293166475](https://github.com/camunda/camunda/actions/runs/43293166475) |
| Cache, PostgreSQL | [43277336138](https://github.com/camunda/camunda/actions/runs/43277336138) | [43282872865](https://github.com/camunda/camunda/actions/runs/43282872865) | [43293167390](https://github.com/camunda/camunda/actions/runs/43293167390) |

The namespaces were `c8-md-s12s-off-20261112`, `c8-md-s12s-rocks-20261112` and
`c8-md-s12s-pg-20261112`. All three are deleted.
