# RocksDB job-queue workload

A plain Java workload (not JMH) that reproduces how Zeebe uses RocksDB for jobs. It opens the DB
through `ZeebeRocksDbFactory`, so the options are exactly the broker's defaults. It then drives the
same primitives `TransactionalColumnFamily` uses:

- one reused `WriteBatchWithIndex` per transaction, committed with WAL disabled
- a fresh base iterator wrapped by the batch for every prefix scan, with `prefix_same_as_start`
- keys prefixed by the 8-byte Zeebe column family id

It doesn't use the typed `ColumnFamily` API. That way, variants the production code doesn't
support yet can be compared against the same state.

It is a long-running process with real I/O, background flushes and compactions. JMH's fork /
iteration model hides exactly the effects we care about here: state aging over time, memtable fill
and flush, compaction debt. So the harness reports one CSV row per time window instead.

## Workload

Per job:

1. **Create**: put `JOBS[jobKey]` and `JOB_ACTIVATABLE_BY_PRIORITY[type, priority 0, jobKey, tenant]`.
2. **Activate**: once a type's backlog exceeds `backlog`, delete the oldest activatable entry, add
   `JOB_DEADLINES[deadline, jobKey]` and update the job.
3. **Complete**: once more than `inflight` jobs are activated, delete the oldest deadline and job.

Between jobs, `pollsPerJob` activation polls run round-robin over `pollTypes` job types. Only
`churnTypes` of them ever receive jobs; the rest are always empty, like most polls in production.
A poll follows `DbJobState#forEachActivatableJobs`: a phase 1 scan from the type prefix, then a
phase 3 seek to the priority-0 range (the legacy phase is treated as drained). Every
`deadlineScanInterval`, the deadline column family is walked from its start until the first future
deadline, as the job timeout checker does.

Job keys are generated like the engine does: `Protocol.encodePartitionId(partitionId, counter)`.
The counter advances by `keysPerJob` per job because other records consume keys too. Job keys are
therefore strictly increasing, and deleted (activated) jobs always sit at the head of their type's
range. That is the queue pattern that makes tombstones expensive in an LSM tree.

`preloadKeys` small entries on unrelated prefixes give the LSM tree the size and shape of an aged
partition. `ageJobs` runs that many job lifecycles, without polls, before measuring, so the
measurement starts with the accumulated tombstones of a long-lived partition.

## Variants

| Argument | What it changes | Settable today? |
|---|---|---|
| `readMode=UPPER_BOUND` | sets `iterate_upper_bound` to the successor of the scanned prefix | no, code change in `TransactionalColumnFamily` |
| `deleteMode=SINGLE_DELETE` | removes write-once index entries (activatable, deadline) with `SingleDelete` | no, code change in the engine state classes |
| `seekHint=true` | seeks from an in-memory low watermark instead of the head of a queue-like range | no, code change in `DbJobState` |
| `compactOnDeletion=1000:500:0` | `windowSize:deletionTrigger:deletionRatio` of RocksDB's compact-on-deletion collector | via `RocksDbConfiguration`, not wired to the broker yet |
| `cf.<option>=<value>` | any RocksDB column family option, passed like the broker's `columnFamilyOptions` | yes |
| `memoryLimit`, `memoryStrategy`, `partitions` | memory budget and `PARTITION` / `BROKER` / `FRACTION` allocation | yes |
| `sstPartitioning=false` | disables the per-prefix SST partitioner | yes |
| `compactAt=PT60S` | runs a manual full `compactRange` mid-run (phase `after-compaction`) | admin only |

All parameters and defaults are in `WorkloadConfig`.

## Running

```bash
# build once (shaded jar, contains the RocksDB native library)
./mvnw package -pl microbenchmarks -DskipTests -Dspotless.check.skip -Dlicense.skip

# single run
java -Xmx1g -cp microbenchmarks/target/benchmarks.jar \
  io.camunda.microbenchmarks.rocksdb.JobQueueWorkload \
  dbDir=/data/bench out=results.csv preloadKeys=4000000 ageJobs=1000000 jobRate=100 duration=PT5M

# sweep over variants.txt; extra args apply to every variant
RESULTS_DIR=/data/results DB_DIR=/data/bench CPUS=2,3 \
  microbenchmarks/rocksdb/run-sweep.sh microbenchmarks/rocksdb/variants.txt \
  preloadKeys=4000000 ageJobs=1000000 jobRate=100 duration=PT5M window=PT15S
```

`run-sweep.sh` uses a fresh DB per variant. It keeps each run's RocksDB `LOG` / `OPTIONS` files
and writes `windows.csv`, `summary.md` and `env.txt` (host, disk, git sha).
`summarize.py windows.csv` aggregates the steady state of each run: the median across the second
half of its windows.

For numbers you want to compare across machines:

- put `DB_DIR` on the same disk type as the brokers
- pin CPUs with `CPUS`
- run each variant at least twice and look at the spread

## Reading the output

| Column | Meaning |
|---|---|
| `poll_*_us` | latency of one full activation poll (all phases) |
| `iter_create_*_us` | creating the batch + base iterator, before any seek |
| `deadline_scan_*_us` | one timeout-checker scan |
| `number_iter_skip` | internal keys (mostly tombstones) RocksDB stepped over in that window; the most direct signal for the problem |
| `num-deletes-*-mem-table` | tombstones still in memtables; memtables are a skip list, so tombstones there are skipped one by one on every seek |
| `sst_deletions` | tombstones in SST files (from table properties) |

With the default `jobRate=0`, the loop is not rate limited, and the throughput columns show how
much a variant can sustain.
