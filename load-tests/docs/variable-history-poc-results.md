# Variable history PoC: raw results

Working log of the benchmark for [camunda/pdp-3517-variable-trace#53](https://github.com/camunda/pdp-3517-variable-trace/issues/53). Each step is added when it is measured. The plain-language report is written from this file at the end.

## Setup common to all measured runs

- Image `bcan-vh-es-cal-20261026-35352339`, branch `bcan/53-variable-history-poc` at `353523398bd`.
- Cluster: 3 brokers (3 cores, 2 GiB each), 3 partitions, replication 3. Elasticsearch 3 nodes (7 cores, 8 GiB each), 1 shard and 1 replica per index. PostgreSQL 1 instance (3 cores, 8 to 12 GiB).
- Workload: copy of the realistic scenario with large values (see [variable-history-poc.md](variable-history-poc.md)). Non-spot VMs, Optimize off, k6 read benchmark on, write throttling on.
- PostgreSQL runs keep history for 10 minutes (baseline too), so the history cleanup runs inside the window. Elasticsearch archives after 1 minute; its 1-day deletion is not exercised.
- Measurements: 10 or 60 minute windows from the benchmark Prometheus. Latencies are p99. CPU is cores used by the busiest pod (brokers) or the sum of pods (storage).

## Step 0: sanity run and calibration (2026-10-26)

### Sanity run (Elasticsearch, 100%, 1 root instance/s)

Namespace `c8-bcan-vh-sanity-20261026`, older image `bcan-vh-sanity-20261026-4f1543cf`. Stats at 18:53 UTC, about 16 minutes after start.

- 653,492 history records for 944 root instances: about **692 records and 2.1 MB of values per root instance**.
- By actor (bytes): engine 86.8%, client 13.2%, unknown about 0%, user 0% (the load testers authenticate as clients).
- By operation (bytes): created 43.9%, updated 56.1%.
- By variable (bytes):

|                     Variable                     |  Bytes   | Share | Average value |                            Written by                             |
|--------------------------------------------------|----------|-------|---------------|-------------------------------------------------------------------|
| `refundedAmount` (multi-instance result list)    | 1,112 MB | 56%   | 26 KB         | engine                                                            |
| `customer`                                       | 330 MB   | 17%   | 4 KB          | engine (call activity input and copy into child), client at start |
| `refundResult`                                   | 326 MB   | 16%   | 4 KB          | client (worker in child), engine (copy back on child completion)  |
| `disputePosition` (multi-instance input element) | 132 MB   | 7%    | 1 KB          | engine                                                            |
| `disputeDetails`                                 | 97 MB    | 5%    | 51 KB         | client (start and worker update)                                  |

- Index size (with replica) after about 16 minutes: `variable-history` 3.56 GB; `operate-variable` 0.85 GB, `operate-list-view` 0.98 GB, `operate-flownode-instance` 0.23 GB.
- The archiver moved history documents into the dated index as expected.
- Found and fixed: about 0.1% of the records (`loopCounter`, `disputePosition`) had the client as actor, because `VariableBehavior#setLocalVariable` did not set the operation type. Fixed in `353523398bd` before the measured runs.

### Calibration (baseline 0%, rate stepped on running clusters)

| Rate (root instances/s) |     Store     | Completed/s | Backpressure | Data availability p99 | Processing p99 | Broker CPU (of 3) | Broker throttled | Storage CPU |
|-------------------------|---------------|-------------|--------------|-----------------------|----------------|-------------------|------------------|-------------|
| 1                       | Elasticsearch | 1.07        | 5.6%         | 4.86 s                | 0.88 s         | 2.77              | 60%              | 3.08        |
| 1                       | PostgreSQL    | 0.99        | 7.0%         | 2.31 s                | 0.92 s         | 2.78              | 57%              | 0.28        |
| 0.75                    | Elasticsearch | 0.75        | 0.12%        | 3.81 s                | 0.36 s         | 1.91              | 13%              | 2.04        |
| 0.75                    | PostgreSQL    | 0.74        | 0.03%        | 0.99 s                | 0.36 s         | 1.91              | 13%              | 0.21        |
| 0.5                     | Elasticsearch | 0.50        | 0%           | 2.48 s                | 0.16 s         | 1.28              | 2.5%             | 1.35        |
| 0.5                     | PostgreSQL    | 0.50        | 0%           | 0.99 s                | 0.18 s         | 1.26              | 2.8%             | 0.16        |

For comparison, the daily realistic test (small payloads, 1 root instance/s) uses 1.4 to 1.7 broker cores with 0% backpressure. The large payloads make the brokers the bottleneck, before secondary storage.

**Measuring rate: 0.5 root instances/s**, the highest rate with no backpressure and no broker CPU throttling. Each root instance starts 50 called processes, so this is about 25 called processes per second.

The 100% sanity run next to the Elasticsearch baseline (same windows, older image) gave a first hint: at 0.5/s, Elasticsearch CPU 1.78 vs 1.35 cores and exporting latency 0.60 vs 0.42 s; at 0.75/s, 2.76 vs 2.04 cores and data availability 4.53 vs 3.81 s; broker CPU about the same.

## Step 1: every change recorded (0% vs 100%)

Runs dispatched 2026-10-26 20:53 UTC, all ready by 20:57:17 UTC. Warm-up until 21:12:30, measured window 21:12:30 to 22:12:30 UTC.

|            Namespace            |     Store     | Recorded |                                Actions run                                 |
|---------------------------------|---------------|----------|----------------------------------------------------------------------------|
| `c8-bcan-vh-s1-es-0-20261026`   | Elasticsearch | 0%       | [40399607022](https://github.com/camunda/camunda/actions/runs/40399607022) |
| `c8-bcan-vh-s1-es-100-20261026` | Elasticsearch | 100%     | [40399612422](https://github.com/camunda/camunda/actions/runs/40399612422) |
| `c8-bcan-vh-s1-pg-0-20261026`   | PostgreSQL    | 0%       | [40399616607](https://github.com/camunda/camunda/actions/runs/40399616607) |
| `c8-bcan-vh-s1-pg-100-20261026` | PostgreSQL    | 100%     | [40399621819](https://github.com/camunda/camunda/actions/runs/40399621819) |

Metrics workflow reports (window 21:12:30 to 22:12:30 UTC): [es-0](https://github.com/camunda/camunda/actions/runs/40410708561), [es-100](https://github.com/camunda/camunda/actions/runs/40410713978), [pg-0](https://github.com/camunda/camunda/actions/runs/40410719942), [pg-100](https://github.com/camunda/camunda/actions/runs/40410725505).

### Engine and latency (60-minute window)

|              Metric              | ES 0%  | ES 100% |    Change     | PG 0%  | PG 100% |     Change     |
|----------------------------------|--------|---------|---------------|--------|---------|----------------|
| Completed root instances/s       | 0.500  | 0.500   | none          | 0.500  | 0.500   | none           |
| Backpressure                     | 0.005% | 0%      | none          | 0.001% | 0.004%  | none           |
| Request latency p99              | 0.24 s | 0.23 s  | none          | 0.22 s | 0.24 s  | none           |
| Processing latency p99           | 0.22 s | 0.20 s  | none          | 0.18 s | 0.23 s  | +0.05 s        |
| Exporting latency p99            | 0.46 s | 0.61 s  | +33%          | 0.23 s | 0.76 s  | x3.4           |
| Data availability p99            | 2.50 s | 3.76 s  | +1.3 s (+51%) | 0.99 s | 1.25 s  | +0.26 s (+26%) |
| Exporter backlog (max partition) | 430    | 208     | none          | 0      | 2       | none           |
| Broker CPU, busiest pod (of 3)   | 1.32   | 1.21    | none          | 1.27   | 1.39    | +0.12 (+10%)   |
| Broker CPU throttled             | 2.8%   | 2.1%    | none          | 3.5%   | 4.8%    | small          |

### Secondary storage (60-minute window)

|                Metric                 |   ES 0%    |  ES 100%   |    Change    |          PG 0%          |         PG 100%         |               Change               |
|---------------------------------------|------------|------------|--------------|-------------------------|-------------------------|------------------------------------|
| Storage CPU (cores)                   | 1.42       | 2.25       | +0.83 (+58%) | 0.14                    | 0.26                    | +0.12 (+83%)                       |
| Storage CPU throttled                 | 0.3%       | 4.2%       | small        | 0%                      | 0.4%                    | none                               |
| Disk growth in the window             | +5.4 GiB   | +9.8 GiB   | x1.8         | +0.10 GiB               | +0.24 GiB               | steady state, 10 min retention     |
| Exporter flush                        | p99 1.70 s | p99 1.78 s | +5%          | avg 0.028 s, p99 0.11 s | avg 0.051 s, p99 0.19 s | +82% avg                           |
| PostgreSQL statement time since start |            |            |              | 205 s                   | 473 s                   | x2.3, history statements 39% of it |

Elasticsearch index sizes at 22:13 UTC (with replica, about 75 minutes of data):

|                Index                 |  ES 0%  |    ES 100%     |
|--------------------------------------|---------|----------------|
| `variable-history` (runtime + dated) | none    | 6.44 GB        |
| `operate-list-view`                  | 2.20 GB | 2.17 GB        |
| `operate-variable`                   | 1.95 GB | 2.07 GB        |
| `operate-flownode-instance`          | 0.49 GB | 0.53 GB        |
| Total of these                       | 4.63 GB | 11.2 GB (x2.4) |

PostgreSQL at 22:13 UTC (10 minute retention, so about 14 minutes of instances are kept): database 568 MB vs 1,352 MB (x2.4). `variable_history` 773 MB for 412 root instances, about 1.9 MB on disk per root instance. `variable` 398 MB vs 407 MB.

### Archiving and cleanup (60-minute window)

|          Metric           | ES 0%  | ES 100% |        Change         |
|---------------------------|--------|---------|-----------------------|
| Archived root instances/s | 0.498  | 0.499   | keeps up              |
| Archived documents/s      | 2,021  | 2,416   | +20% (history: 381/s) |
| Archiver run, average     | 0.60 s | 0.93 s  | +57%                  |
| Archiver run, longest     | 5.2 s  | 8.2 s   | +58%                  |

|          Metric           | PG 0%  | PG 100% | Change |
|---------------------------|--------|---------|--------|
| Rows deleted by cleanup/s | 835    | 1,232   | +47%   |
| Cleanup run, average      | 0.63 s | 2.77 s  | x4.4   |
| Cleanup run, longest      | 1.06 s | 4.57 s  | x4.3   |

### What was recorded (100% runs)

- Elasticsearch: 1,745,939 records for 2,283 root instances, about **765 records and 2.4 MB of values per root instance**. Engine 87.3% of the bytes, client 12.7%, user 0%. Updates 56% of the bytes, creations 44%.
- PostgreSQL (rows still kept): same split, engine 87.3%, client 12.7%, about 2.4 MB of values per root instance.
- By variable (Elasticsearch): `refundedAmount` 56%, `customer` 17%, `refundResult` 16%, `disputePosition` 6%, `disputeDetails` 4%.
- The tagging fix works: all `disputePosition` and `loopCounter` records now have the engine as actor.

### Step 1 observations

- The engine is not slowed down at this load: same throughput, no backpressure, same request and processing latency. Broker CPU is the same on Elasticsearch and 10% higher on PostgreSQL, where the RDBMS exporter writes the extra rows from the broker.
- The cost lands on secondary storage: 58% (ES) and 83% (PG) more CPU, 2.4 times the storage, 1.8 times the disk growth on ES.
- Data shows up later: +1.3 s at p99 on ES, +0.26 s on PG.
- Archiving and cleanup keep up, but each run takes longer: archiver +57%, PostgreSQL cleanup 4.4 times longer.
- Nothing ran out of resources at 0.5 root instances/s: ES used 2.25 of 21 cores, PostgreSQL 0.26 of 3 cores.

## Step 3: part of the changes recorded (33%, 66%)

Runs dispatched 2026-10-26 21:28 UTC, all ready by 21:32:35 UTC, same image and rate as Step 1. The Step 1 runs keep running, so 0%, 33%, 66% and 100% of each store are measured in one shared window: 22:15:00 to 23:15:00 UTC.

|           Namespace            |     Store     | Recorded |                                Actions run                                 |
|--------------------------------|---------------|----------|----------------------------------------------------------------------------|
| `c8-bcan-vh-s3-es-33-20261026` | Elasticsearch | 33%      | [40404636637](https://github.com/camunda/camunda/actions/runs/40404636637) |
| `c8-bcan-vh-s3-es-66-20261026` | Elasticsearch | 66%      | [40404640744](https://github.com/camunda/camunda/actions/runs/40404640744) |
| `c8-bcan-vh-s3-pg-33-20261026` | PostgreSQL    | 33%      | [40404644544](https://github.com/camunda/camunda/actions/runs/40404644544) |
| `c8-bcan-vh-s3-pg-66-20261026` | PostgreSQL    | 66%      | [40404648664](https://github.com/camunda/camunda/actions/runs/40404648664) |

Metrics workflow reports (window 22:15 to 23:15 UTC), in the order es-0, es-33, es-66, es-100, pg-0, pg-33, pg-66, pg-100: [1](https://github.com/camunda/camunda/actions/runs/40418584897), [2](https://github.com/camunda/camunda/actions/runs/40418590501), [3](https://github.com/camunda/camunda/actions/runs/40418595479), [4](https://github.com/camunda/camunda/actions/runs/40418600594), [5](https://github.com/camunda/camunda/actions/runs/40418605697), [6](https://github.com/camunda/camunda/actions/runs/40418610328), [7](https://github.com/camunda/camunda/actions/runs/40418615329), [8](https://github.com/camunda/camunda/actions/runs/40418620490).

### An infrastructure stall at 23:10 UTC

At 23:10 to 23:11 UTC two runs stalled at the same minute: Elasticsearch 66% (exporter backlog up to 83,593 records, data availability p99 90 s in that 5 minutes, one archiver run of 64 s) and the PostgreSQL **baseline** (backlog 22,218, one cleanup run of 30 s). The baseline's PostgreSQL statement statistics were reset at 23:09:34, so its database process restarted then. The other six runs were not affected. Because a baseline was hit too, this is an infrastructure event and not an effect of recording. The tables below use the clean part of the window, **22:15 to 23:05 UTC (50 minutes)**. The full 60-minute numbers are in the scratch files and the metrics workflow reports.

### Recorded share (check of the sampling)

|                  Store                  |    33% run    |    66% run    | 100% run |
|-----------------------------------------|---------------|---------------|----------|
| Elasticsearch, values per root instance | 0.80 MB (33%) | 1.57 MB (65%) | 2.42 MB  |
| PostgreSQL, values per root instance    | 0.82 MB (34%) | 1.65 MB (69%) | 2.40 MB  |

The actor split is the same in every run: engine 87%, client 13%.

### Elasticsearch, clean window 22:15 to 23:05 UTC

|           Metric            |    0%    |    33%     |     66%     |    100%     |
|-----------------------------|----------|------------|-------------|-------------|
| Completed root instances/s  | 0.500    | 0.499      | 0.500       | 0.500       |
| Backpressure                | 0%       | 0%         | 0%          | 0.005%      |
| Request latency p99         | 0.24 s   | 0.24 s     | 0.24 s      | 0.22 s      |
| Processing latency p99      | 0.23 s   | 0.22 s     | 0.22 s      | 0.21 s      |
| Exporting latency p99       | 0.47 s   | 0.47 s     | 0.48 s      | 0.62 s      |
| Data availability p99       | 2.50 s   | 3.05 s     | 2.50 s      | 4.02 s      |
| Broker CPU, busiest pod     | 1.29     | 1.26       | 1.26        | 1.18        |
| Elasticsearch CPU           | 1.45     | 1.55 (+7%) | 1.59 (+10%) | 2.26 (+56%) |
| Elasticsearch CPU throttled | 0.5%     | 2.0%       | 1.4%        | 5.1%        |
| Disk growth, 60 min         | +5.0 GiB | +6.5 GiB   | +8.3 GiB    | +12.7 GiB   |
| Archived documents/s        | 2,038    | 2,142      | 2,263       | 2,426       |
| Archiver run, average       | 0.62 s   | 0.65 s     | 0.61 s      | 1.02 s      |
| Archiver run, longest       | 5.1 s    | 6.0 s      | 5.3 s       | 8.9 s       |

### PostgreSQL, clean window 22:15 to 23:05 UTC

|                         Metric                         |     0%     |    33%     |    66%     |      100%       |
|--------------------------------------------------------|------------|------------|------------|-----------------|
| Completed root instances/s                             | 0.500      | 0.490      | 0.500      | 0.500           |
| Backpressure                                           | 0.002%     | 0.10%      | 0.006%     | 0.66%           |
| Request latency p99                                    | 0.20 s     | 0.38 s     | 0.24 s     | 0.43 s          |
| Processing latency p99                                 | 0.19 s     | 0.32 s     | 0.23 s     | 0.63 s          |
| Exporting latency p99                                  | 0.22 s     | 0.33 s     | 0.47 s     | 6.58 s          |
| Data availability p99                                  | 0.99 s     | 1.87 s     | 1.00 s     | 9.16 s          |
| Broker CPU, busiest pod                                | 1.25       | 1.29       | 1.31       | 1.40            |
| PostgreSQL CPU                                         | 0.14       | 0.17       | 0.20       | 0.26            |
| WAL written                                            | 1.82 MiB/s | 2.29 MiB/s | 3.13 MiB/s | 3.67 MiB/s (x2) |
| Exporter flush, average                                | 0.028 s    | 0.036 s    | 0.038 s    | 0.052 s         |
| Rows deleted by cleanup/s                              | 837        | 959        | 1,096      | 1,145           |
| Cleanup run, average                                   | 0.57 s     | 0.72 s     | 0.88 s     | 2.44 s          |
| History statements, share of PostgreSQL statement time | 0%         | 17%        | 29%        | 38%             |

### PostgreSQL 100%: stalls around checkpoints

The PostgreSQL 100% run was worse in this window than in Step 1 (data availability p99 9.2 s against 1.25 s, exporting latency 6.6 s against 0.76 s). It had short export stalls at about 22:45, 22:56 to 22:58 (backlog up to 13,763 records) and 23:00. They sit right around checkpoints (22:46, 22:57). With 100% recorded, PostgreSQL writes twice the WAL, so the 4 GB `max_wal_size` fills faster: 5.1 forced checkpoints in 50 minutes against 2 in the baseline. Each history cleanup run takes about 2.4 to 2.7 s every 5 minutes, against about 0.6 s in the baseline.

### Step 3 observations

- The recorded share works as a knob: bytes, documents and WAL grow in proportion to it.
- On Elasticsearch the cost is not linear. 33% and 66% add only 7% to 10% CPU and keep latency close to the baseline. 100% adds 56% CPU, 1.5 s data availability and 65% longer archiver runs.
- On PostgreSQL 33% and 66% stay close to the baseline. 100% gives export stalls around checkpoints, 9 s data availability at p99 and the first backpressure (0.66%).
- With the actor split of this workload, recording users and clients only would be about 13% of the bytes, below the 33% run, so its cost would be smaller than any cost measured here. Engine changes are 87% of the bytes.

## Step 2: more hardware

### Choice of resources (decided after Step 1, 2026-10-26 22:25 UTC)

- At 0.5 root instances/s nothing runs out with 100% recorded. The cost of recording lands on secondary storage (CPU, size, archiving and cleanup time) and shows as later data availability. Broker CPU stays the same on Elasticsearch and grows 10% on PostgreSQL.
- So the hardware step scales secondary storage under the 100% run, at the same rate, to see whether the latency and run-time costs go away:
  - Elasticsearch 5 nodes (+67%) and 6 nodes (+100%), with the history index spread over one primary shard per node. With one shard, new nodes would not take over its writes.
  - PostgreSQL 4.5 cores and 12 GiB (+50%) and 6 cores and 16 GiB (+100%), `shared_buffers` and `effective_cache_size` scaled the same way, on the 8-core node pool.
- Brokers are not scaled: recording does not change their load on Elasticsearch, and they are the bottleneck of this workload with or without recording.
- Runs start after the Step 3 collection, to keep the number of parallel clusters low. The Step 1 runs (0% and 100% on 3 nodes and 3 cores) keep running as references in the same window.
- Added after Step 3: on PostgreSQL the 100% run stalls around forced checkpoints, an I/O effect. On GCP the disk IOPS and throughput grow with the disk size, so the bigger PostgreSQL runs also get bigger disks (384 Gi and 512 Gi instead of 256 Gi). `max_wal_size` stays at 4 GB, so only hardware changes.

### Runs

All record 100% at 0.5 root instances/s, image and workload as in Step 1. Ready by 23:29:08 UTC; window **23:45 to 00:45 UTC**. At that point the hardware runs are as old as the Step 1 runs were in the Step 1 window (16 to 76 minutes), so they are compared with both the Step 1 window (same age) and the Step 1 runs in the same window (same time, older).

|            Namespace             |                                Hardware                                 |                                Actions run                                 |
|----------------------------------|-------------------------------------------------------------------------|----------------------------------------------------------------------------|
| `c8-bcan-vh-s2-es-5n-20261027`   | Elasticsearch 5 nodes (+67%), history index 5 primary shards            | [40419039202](https://github.com/camunda/camunda/actions/runs/40419039202) |
| `c8-bcan-vh-s2-es-6n-20261027`   | Elasticsearch 6 nodes (+100%), history index 6 primary shards           | [40419043117](https://github.com/camunda/camunda/actions/runs/40419043117) |
| `c8-bcan-vh-s2-pg-150b-20261027` | PostgreSQL 4.5 cores, 12 GiB, `shared_buffers` 3 GB, 384 Gi disk (+50%) | [40419605094](https://github.com/camunda/camunda/actions/runs/40419605094) |
| `c8-bcan-vh-s2-pg-200b-20261027` | PostgreSQL 6 cores, 16 GiB, `shared_buffers` 4 GB, 512 Gi disk (+100%)  | [40419609320](https://github.com/camunda/camunda/actions/runs/40419609320) |

Verified on the running clusters: 5 and 6 Elasticsearch pods with the history shards spread over the nodes; PostgreSQL pod resources, node pool, disk size and `shared_buffers` as listed. The first PostgreSQL attempt failed to deploy: `--set postgresql.tolerations[0].value=...` replaced the whole list entry and dropped its key and operator, so the CNPG webhook rejected it. It was redeployed with all four toleration fields.

Metrics workflow reports (window 23:45 to 00:45 UTC), in the order es-0, es-100, es-5n, es-6n, pg-0, pg-100, pg-150b, pg-200b: [1](https://github.com/camunda/camunda/actions/runs/40428499690), [2](https://github.com/camunda/camunda/actions/runs/40428503898), [3](https://github.com/camunda/camunda/actions/runs/40428508069), [4](https://github.com/camunda/camunda/actions/runs/40428512262), [5](https://github.com/camunda/camunda/actions/runs/40428515918), [6](https://github.com/camunda/camunda/actions/runs/40428520475), [7](https://github.com/camunda/camunda/actions/runs/40428525164), [8](https://github.com/camunda/camunda/actions/runs/40428529393).

All eight remaining namespaces were deleted after this collection (2026-10-27 00:55 UTC).

### Elasticsearch, window 23:45 to 00:45 UTC (all at 0.5 root instances/s)

|                            Metric                             | 3 nodes, 0% | 3 nodes, 100% | 5 nodes, 100% | 6 nodes, 100% |
|---------------------------------------------------------------|-------------|---------------|---------------|---------------|
| Backpressure                                                  | 0.002%      | 0.012%        | 0%            | 0%            |
| Processing latency p99                                        | 0.23 s      | 0.20 s        | 0.22 s        | 0.22 s        |
| Exporting latency p99                                         | 0.47 s      | 0.60 s        | 0.48 s        | 0.48 s        |
| Data availability p99, 60 min                                 | 2.97 s      | 3.76 s        | 3.41 s        | 3.21 s        |
| Data availability p99, median of 5-minute slices (whole life) | 2.50 s      | 3.87 s        | 3.31 s        | 2.50 s        |
| Elasticsearch CPU (sum of nodes)                              | 1.42        | 2.16          | 1.87          | 1.78          |
| Elasticsearch CPU throttled                                   | 0.6%        | 5.2%          | 0.4%          | 0.2%          |
| Archiver run, average                                         | 0.59 s      | 0.95 s        | 0.57 s        | 0.55 s        |
| Archiver run, longest                                         | 4.9 s       | 10.3 s        | 5.2 s         | 4.9 s         |
| Broker CPU, busiest pod                                       | 1.29        | 1.17          | 1.31          | 1.30          |

Same age as Step 1 (16 to 76 minutes old): 3 nodes 100% had 3.76 s data availability, 0.61 s exporting latency and 0.93 s archiver runs against 2.50 s, 0.46 s and 0.60 s for the 3-node baseline.

### PostgreSQL, window 23:45 to 00:45 UTC (all at 0.5 root instances/s)

|                       Metric                       |   3 cores, 0%   |  3 cores, 100%  | 4.5 cores, 100% |  6 cores, 100%  |
|----------------------------------------------------|-----------------|-----------------|-----------------|-----------------|
| Backpressure                                       | 0%              | 0.010%          | 0%              | 0%              |
| Processing latency p99                             | 0.17 s          | 0.24 s          | 0.21 s          | 0.21 s          |
| Exporting latency p99                              | 0.22 s          | 0.75 s          | 0.49 s          | 0.47 s          |
| Data availability p99, 60 min                      | 0.99 s          | 1.00 s          | 1.00 s          | 1.00 s          |
| Worst 5-minute data availability p99 in the window | 0.99 s          | 1.83 s          | 2.10 s          | 1.83 s          |
| PostgreSQL CPU                                     | 0.14            | 0.26            | 0.21            | 0.22            |
| Exporter flush, average / p99                      | 0.027 / 0.109 s | 0.050 / 0.181 s | 0.043 / 0.155 s | 0.041 / 0.153 s |
| Cleanup run, average / longest                     | 0.54 / 0.68 s   | 2.43 / 2.82 s   | 0.91 / 1.31 s   | 0.93 / 1.45 s   |
| WAL written                                        | 1.78 MiB/s      | 3.85 MiB/s      | 3.79 MiB/s      | 3.81 MiB/s      |
| Forced checkpoints per hour                        | 3               | 6               | 6               | 6               |
| Broker CPU, busiest pod                            | 1.25            | 1.37            | 1.35            | 1.32            |

### How often data availability got slow (5-minute slices above 5 s at p99, whole life of each run)

|             Run             | Slices |             Above 5 s              | Worst  | Median |
|-----------------------------|--------|------------------------------------|--------|--------|
| PostgreSQL 3 cores, 0%      | 43     | 1 (the 23:10 infrastructure event) | 9.3 s  | 0.99 s |
| PostgreSQL 3 cores, 100%    | 43     | 1 (23:00)                          | 29.4 s | 1.49 s |
| PostgreSQL 4.5 cores, 100%  | 13     | 0                                  | 2.1 s  | 1.00 s |
| PostgreSQL 6 cores, 100%    | 13     | 0                                  | 1.5 s  | 1.00 s |
| Elasticsearch 3 nodes, 0%   | 43     | 0                                  | 4.3 s  | 2.50 s |
| Elasticsearch 3 nodes, 100% | 43     | 0                                  | 4.4 s  | 3.87 s |
| Elasticsearch 5 nodes, 100% | 13     | 0                                  | 4.2 s  | 3.31 s |
| Elasticsearch 6 nodes, 100% | 13     | 0                                  | 4.4 s  | 2.50 s |

The hardware runs lived about 1 hour 15 minutes, against about 3 hours 50 minutes for the 3-node and 3-core runs, so a rare stall is less likely to show up in them.

### Step 2 observations

- Elasticsearch: 5 nodes bring exporting latency, archiver run time and CPU throttling back to the baseline. Data availability gets about half of the way back (median 3.31 s). 6 nodes bring the median back to the baseline (2.50 s); the 60-minute p99 stays 0.24 s above the same-window baseline.
- PostgreSQL: more CPU, memory and disk cut the exporting latency increase in half (0.75 to 0.47 to 0.49 s, baseline 0.22 s) and the cleanup run time by about 60% (2.43 to about 0.9 s, baseline 0.54 s). +50% and +100% give the same result, so the gain comes from the first step and does not grow further.
- More PostgreSQL hardware does not reduce the extra WAL or the forced checkpoints: those come from the amount of data written.
- Brokers do not change with storage hardware, as expected.

## Step 4: more broker CPU and a higher WAL limit (PostgreSQL)

### Question and setup (2026-10-27)

Step 2 left PostgreSQL with full tracing at about twice today's exporting latency (0.47 to 0.49 s against 0.22 s), and more PostgreSQL hardware did not close that gap. Two things were not tested: more broker CPU (the RDBMS exporter runs inside the brokers, which used 10% more CPU) and a higher WAL limit (full tracing doubled the forced checkpoints).

All runs record at 0.5 root instances/s with the Step 1 image and workload, side by side. Dispatched 12:00:59 UTC, ready by 12:05:52, warm-up until 12:22, **window 12:22 to 14:22 UTC** (2 hours).

|             Namespace              |                    Run                    | Recorded | PostgreSQL | `max_wal_size` |            Brokers             |                                Actions run                                 |
|------------------------------------|-------------------------------------------|----------|------------|----------------|--------------------------------|----------------------------------------------------------------------------|
| `c8-bcan-vh-s4-pg-0-20261027`      | A. Today                                  | 0%       | 3 cores    | 4 GB           | 3 cores, 4 core non-spot nodes | [40500448128](https://github.com/camunda/camunda/actions/runs/40500448128) |
| `c8-bcan-vh-s4-pg-hw-20261027`     | B. Bigger PostgreSQL (Step 2, +50%)       | 100%     | +50%       | 4 GB           | 3 cores, 4 core non-spot nodes | [40500453214](https://github.com/camunda/camunda/actions/runs/40500453214) |
| `c8-bcan-vh-s4-pg-wal-20261027`    | C. B + higher WAL limit                   | 100%     | +50%       | 16 GB          | 3 cores, 4 core non-spot nodes | [40500458592](https://github.com/camunda/camunda/actions/runs/40500458592) |
| `c8-bcan-vh-s4-pg-brk-20261027`    | D. B + more broker CPU                    | 100%     | +50%       | 4 GB           | 4.5 cores, 8 core spot nodes   | [40500463583](https://github.com/camunda/camunda/actions/runs/40500463583) |
| `c8-bcan-vh-s4-pg-walbrk-20261027` | E. B + higher WAL limit + more broker CPU | 100%     | +50%       | 16 GB          | 4.5 cores, 8 core spot nodes   | [40500468090](https://github.com/camunda/camunda/actions/runs/40500468090) |

- PostgreSQL +50% is 4.5 cores, 12 GiB, `shared_buffers` 3 GB, `effective_cache_size` 9 GB and a 384 Gi disk on the 8 core pool, as in Step 2.
- 16 GB keeps only the timed checkpoints: PostgreSQL starts a checkpoint once the WAL since the last one reaches about `max_wal_size / (1 + checkpoint_completion_target)`, 2.1 GB at 4 GB. Full tracing writes about 4.5 GB per 20-minute `checkpoint_timeout`.
- More broker CPU needs the 8 core pool: the system pods on a 4 core node leave room for about 3.07 cores. Thread counts stay at 3. The 8 core pool is spot only.
- Verified on the running clusters: pod resources, node pools, `max_wal_size`, `shared_buffers` and disk sizes as listed.

Metrics workflow reports (2-hour window, disturbed slices included): [A](https://github.com/camunda/camunda/actions/runs/40520629158), [B](https://github.com/camunda/camunda/actions/runs/40520638808), [C](https://github.com/camunda/camunda/actions/runs/40520647352), [D](https://github.com/camunda/camunda/actions/runs/40520657547), [E](https://github.com/camunda/camunda/actions/runs/40520665908).

All five namespaces were deleted after the collection (2026-10-27 14:45 UTC).

### Spot node shutdowns during the window

Several spot nodes were shut down during the window (Kubernetes `TaintManagerEviction` and `Killing` events). Each moved a pod and stalled the load for 1 to 3 minutes:

| Run |                                       Moved pods (UTC)                                        |                 Effect                 |
|-----|-----------------------------------------------------------------------------------------------|----------------------------------------|
| A   | none                                                                                          | none                                   |
| B   | 2 workers at 12:27:49                                                                         | one 5-minute slice at 3.3 s            |
| C   | Keycloak and 4 workers at 13:14:59, Keycloak's database at 13:44:08                           | requests down to 5/s, slices 9 s, 36 s |
| D   | broker `camunda-2` at 13:44:35                                                                | slice at 90 s (histogram limit)        |
| E   | Keycloak and workers at 13:27, broker `camunda-0` at 14:05:28, broker `camunda-2` at 14:14:33 | slices at 90 s                         |

The table below therefore drops every 5-minute slice that holds a moved pod, a minute below 60% of the median request rate or a minute with fewer than 3 brokers up, plus the slice after it (catch-up). Kept slices: A 24, B 22, C 20, D 21, E 18 of 24. Quantiles are computed from the summed histogram buckets of the kept slices.

### Results, window 12:22 to 14:22 UTC, disturbed slices dropped

|                     Metric                      |   A. Today    | B. Bigger PG  | C. B + WAL 16 GB | D. B + broker CPU |  E. B + both  |
|-------------------------------------------------|---------------|---------------|------------------|-------------------|---------------|
| Exporting latency p99                           | 0.24 s        | 0.65 s        | 0.49 s           | 0.25 s            | 0.25 s        |
| Exporting latency p50                           | 0.024 s       | 0.058 s       | 0.051 s          | 0.034 s           | 0.034 s       |
| Exporting latency p99, median of slices / worst | 0.24 / 0.24 s | 0.63 / 1.17 s | 0.48 / 1.35 s    | 0.25 / 0.80 s     | 0.24 / 0.37 s |
| Data availability p99                           | 0.99 s        | 1.02 s        | 1.00 s           | 0.99 s            | 0.99 s        |
| Data availability p99, worst slice              | 0.99 s        | 1.99 s        | 1.83 s           | 1.00 s            | 1.00 s        |
| Processing latency p99                          | 0.21 s        | 0.22 s        | 0.22 s           | 0.07 s            | 0.06 s        |
| Request latency p99                             | 0.24 s        | 0.24 s        | 0.24 s           | 0.06 s            | 0.06 s        |
| Exporter flush p99                              | 0.15 s        | 0.17 s        | 0.16 s           | 0.13 s            | 0.13 s        |

### Results, whole window (nothing dropped)

|                 Metric                 |   A. Today    | B. Bigger PG  | C. B + WAL 16 GB | D. B + broker CPU |  E. B + both  |
|----------------------------------------|---------------|---------------|------------------|-------------------|---------------|
| Completed root instances/s             | 0.500         | 0.500         | 0.497            | 0.500             | 0.491         |
| Backpressure                           | 0%            | 0.025%        | 0.31%            | 0.11%             | 0.018%        |
| Broker CPU, busiest pod / average      | 1.27 / 1.25   | 1.39 / 1.33   | 1.39 / 1.36      | 1.19 / 0.99       | 1.06 / 0.92   |
| Broker CPU throttled, busiest pod      | 3.2%          | 5.0%          | 5.9%             | 2.1%              | 0.9%          |
| PostgreSQL CPU                         | 0.23          | 0.26          | 0.21             | 0.20              | 0.21          |
| WAL written                            | 1.77 MiB/s    | 3.71 MiB/s    | 3.16 MiB/s       | 3.73 MiB/s        | 3.12 MiB/s    |
| WAL full page images/s                 | 49            | 211           | 117              | 223               | 118           |
| Checkpoints in 2 hours, forced / timed | 5 / 1         | 12 / 0        | 0 / 6            | 12 / 0            | 0 / 6         |
| Checkpoint buffers written             | 0.40 M        | 1.49 M        | 0.88 M           | 1.59 M            | 0.86 M        |
| WAL on disk, average / largest         | 3.7 / 4.0 GiB | 4.0 / 4.0 GiB | 6.5 / 7.3 GiB    | 4.0 / 4.0 GiB     | 6.6 / 7.3 GiB |
| Cleanup run, average / longest         | 0.76 / 1.0 s  | 1.02 / 1.4 s  | 0.91 / 1.6 s     | 0.95 / 2.5 s      | 0.99 / 2.2 s  |
| Statement time since start             | 538 s         | 774 s         | 625 s            | 614 s             | 624 s         |

### Step 4 observations

- **More broker CPU closes the export gap.** With 4.5 broker cores, full tracing exports as fast as today's cluster at p99 (0.25 s against 0.24 s). The median is still about 40% higher (0.034 s against 0.024 s), because each flush carries more data. Data availability is the same as today.
- **The broker CPU gain is not only for the exporter.** With 4.5 cores, CPU throttling of the busiest broker drops from 3% to 6% (A to C) to 1% to 2% (D and E), and processing and request latency drop from about 0.22 s to 0.06 s, well below today. Today's 3-core brokers are throttled in short bursts even at 1.3 cores on average. So a 0% run with the same brokers would likely be faster than run A; that run was not measured. The brokers also moved from 4 core non-spot to 8 core spot nodes, a second change.
- **The higher WAL limit removes the forced checkpoints** (12 per 2 hours to none, 6 timed instead). It cuts the full page images by 45% and the WAL by 15% (3.7 to 3.2 MiB/s, still 1.8 times today). It lowers the export delay somewhat (p99 0.65 to 0.49 s) but does not bring it back to today. On top of more broker CPU it adds nothing to the delay (0.25 s in D and E).
- **The cost of the higher WAL limit is disk:** up to 7.3 GiB of WAL instead of 4 GiB. A longer crash recovery is expected but not measured.
- Run B was slower than in Step 2 (0.65 s against 0.49 s at p99). Its slow minutes come in short bursts and line up loosely with broker CPU throttling (correlation 0.45 per minute between 12:40 and 13:12 UTC; 0.41 in A, 0.57 in C).
- **Spot nodes are a real risk for this setup:** brokers on the 8 core pool were shut down twice in 2 hours. Elasticsearch and PostgreSQL also run on spot nodes in all load tests; `stable-vms=true` only moves the brokers.

## Step 5: only the added item of a multi-instance result list

### Question and setup (2026-10-29)

The multi-instance result list `refundedAmount` made 56% of the recorded bytes, because every update writes the whole list again: 50 updates of a list that holds 25 KB on average. Step 5 records only the item that each update adds (about 1 KB) and keeps one history entry per update, so the trail of each inner instance stays.

- The engine tags each output collection update with the slot it fills (`collectionIndex` on the variable source, -1 for every other write). Creating the empty list and copying the final list to the parent scope stay full values.
- `camunda.data.audit-log.variable-history-collection-items: true` makes both exporters store only the element in that slot.
- Not stored: the slot itself, which a real design needs to rebuild the list at a point in time. It is one number per record.

Four runs on one image (`bcan-vh-mi-es-items-20261029-00d51d70`, built from `00d51d70d63`), the Step 1 workload at 0.5 root instances/s, side by side. No 0% run: today's values come from Step 1 (Elasticsearch, 2026-10-26) and Step 4 run A (PostgreSQL, 2026-10-27). Dispatched at 10:27 UTC (with the image build) and 10:35 UTC, ready by about 10:40, **window 11:35 to 12:35 UTC**.

|             Namespace             |     Store     |           Recorded           |                                Actions run                                 |
|-----------------------------------|---------------|------------------------------|----------------------------------------------------------------------------|
| `c8-bcan-vh-mi-es-100-20261029`   | Elasticsearch | 100%, full values            | [40858798668](https://github.com/camunda/camunda/actions/runs/40858798668) |
| `c8-bcan-vh-mi-es-items-20261029` | Elasticsearch | 100%, result list items only | [40857710407](https://github.com/camunda/camunda/actions/runs/40857710407) |
| `c8-bcan-vh-mi-pg-100-20261029`   | PostgreSQL    | 100%, full values            | [40858802953](https://github.com/camunda/camunda/actions/runs/40858802953) |
| `c8-bcan-vh-mi-pg-items-20261029` | PostgreSQL    | 100%, result list items only | [40858807005](https://github.com/camunda/camunda/actions/runs/40858807005) |

Metrics workflow reports: [es-100](https://github.com/camunda/camunda/actions/runs/40874589548), [es-items](https://github.com/camunda/camunda/actions/runs/40874595446), [pg-100](https://github.com/camunda/camunda/actions/runs/40874602062), [pg-items](https://github.com/camunda/camunda/actions/runs/40874609279).

No pod was moved or restarted in the window. The pg-100 starter was preempted by another pod at 11:25 UTC and back within 30 s, before the window.

### What was recorded

|                                    |    ES 100%     |  ES items   | PG 100%  | PG items |
|------------------------------------|----------------|-------------|----------|----------|
| History records per root instance  | 765            | 765         | 765      | 765      |
| History values per root instance   | 2,334 KB       | 1,131 KB    | 2,341 KB | 1,121 KB |
| `refundedAmount` per root instance | 1,313 KB (56%) | 100 KB (9%) |          |          |
| `refundedAmount` update, average   | 26 KB          | 1.0 KB      |          |          |
| History on disk per root instance  |                |             | 2.45 MB  | 1.05 MB  |

The 100 KB left for `refundedAmount` are the 50 items (50 KB) and the two full copies (50 KB): the empty list at creation and the final list copied to the parent scope.

### Elasticsearch, window 11:35 to 12:35 UTC

|              Metric              | Today (Step 1) | 100% full | 100% items | Extra cost removed |
|----------------------------------|----------------|-----------|------------|--------------------|
| Storage space, x without history | x1             | x1.88     | x1.31      | 65%                |
| Disk growth in the window        | +5.4 GiB       | +11.3 GiB | +6.9 GiB   | 75%                |
| Elasticsearch CPU (cores)        | 1.42           | 2.43      | 1.61       | 81%                |
| Exporting latency p99            | 0.46 s         | 0.63 s    | 0.49 s     | 81%                |
| Exporting latency p50            |                | 0.024 s   | 0.024 s    |                    |
| Data availability p99            | 2.50 s         | 3.79 s    | 3.22 s     | 44%                |
| Data availability p50            |                | 1.51 s    | 1.48 s     |                    |
| Archived documents/s             | 2,021          | 2,401     | 2,407      | none               |
| Archiver run, average            | 0.60 s         | 0.92 s    | 0.64 s     | 88%                |
| Broker CPU, average of pods      |                | 1.27      | 1.34       |                    |

- Storage space compares all indices with all indices minus the history indices of the same run, so it needs no 0% run.
- Data availability p99 moves in steps of the histogram buckets (2.49, 3.31, 3.87, 4.16 s per 5-minute slice), so its "removed" share is rough. Median of the 5-minute slices: 3.87 s full, 3.31 s items.

### PostgreSQL, window 11:35 to 12:35 UTC

|                     Metric                      | Today (Step 4 A) |   100% full   |  100% items   | Extra cost removed |
|-------------------------------------------------|------------------|---------------|---------------|--------------------|
| Database, x without history                     | x1               | x2.35         | x1.81         | 40% (one snapshot) |
| WAL written                                     | 1.77 MiB/s       | 3.81 MiB/s    | 3.06 MiB/s    | 37%                |
| WAL full page images/s                          | 49               | 237           | 167           | 37%                |
| Checkpoints forced per hour                     | 2.5              | 6             | 5             |                    |
| Checkpoint buffers written per hour             | 0.20 M           | 0.77 M        | 0.51 M        | 45%                |
| Cleanup run, average                            | 0.76 s           | 1.89 s        | 1.45 s        | 39%                |
| Rows deleted by cleanup/s                       |                  | 1,215         | 1,228         | none               |
| Exporting latency p99                           | 0.24 s           | 0.58 s        | 0.48 s        | 30%                |
| Exporting latency p99, median of slices / worst | 0.24 / 0.24 s    | 0.48 / 1.53 s | 0.47 / 0.90 s |                    |
| Exporting latency p50                           | 0.024 s          | 0.045 s       | 0.043 s       |                    |
| Data availability p99                           | 0.99 s           | 1.00 s        | 1.09 s        | none               |
| PostgreSQL CPU                                  | 0.23             | 0.20          | 0.29          | see below          |
| Statement time since start                      |                  | 554 s         | 540 s         |                    |
| Broker CPU, average of pods                     | 1.25             | 1.28          | 1.37          |                    |

- The database ratio is one snapshot at 12:36 UTC. With a 10-minute history, it depends on where each cleanup cycle stands (the history held 320 root instances in the full run and 443 in the items run). History on disk per root instance (2.45 MB to 1.05 MB, -57%) is the steadier number.
- PostgreSQL CPU was higher in the items run for the whole hour (0.29 against 0.20 cores), with the same total statement time. Statements that do not touch the history were also 15% to 30% slower per call there, and its node ran nothing else of note. Earlier runs measured 0.14 to 0.26 cores for the same setups, so this is read as run-to-run noise.
- Broker CPU was 0.07 to 0.09 cores higher in both items runs. Cutting the item out of the list costs microseconds per update (25 updates/s), and smaller documents should cost less, not more. Earlier runs varied by about 0.1 cores between brokers of the same setup.

### Step 5 observations

- **Half of the recorded data goes away.** Recording only the added item cuts the history from about 2.3 MB to 1.1 MB per root instance on both stores. The number of records stays the same (765), so the trail of each inner instance is kept.
- **Elasticsearch gets most of the way back to today.** The extra storage space drops from +88% to +31%, Elasticsearch CPU from +1.0 to +0.2 cores, and the export delay p99 from 0.63 s to 0.49 s (today 0.46 s). The archiver moves as many documents as before, but each run is as fast as today.
- **PostgreSQL gains less.** The extra disk writes drop by about a third (WAL 3.8 to 3.1 MiB/s, today 1.8), the history on disk per root instance by 57%, and cleanup runs get faster. The export delay changes little: Step 4 showed it is limited by broker CPU, which this change does not touch.
- **Unchanged:** the number of records, so archived documents and deleted rows per second stay at the full-tracing level, and Operate's data availability on PostgreSQL, which was already close to today.
- Today's values come from runs on other days; same-day 0% runs were not repeated.

