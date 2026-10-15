# RocksDB job queue: findings (2026-10)

Measured with `JobQueueWorkload` on one 4-vCPU / 15 GB cloud VM with network block storage. Each
variant ran once, so treat differences below ~1.5x as noise. Every run starts from the same shape:

- 4M small background keys
- 1M aged job lifecycles
- then 45 s of measurement with 20 job types (2 receive jobs, 18 are always empty), 10 polls per
  job, and a target of 100 jobs/s

All latencies are the median over windows. Raw results are in `data/2026-10-09/` (sweep 1–4) and were produced by `run-sweep.sh` with
`variants.txt`, `variants-round2.txt` and `variants-write-amp.txt`.

## Why empty polls are slow

An activation poll seeks to `[JOB_ACTIVATABLE_BY_PRIORITY][type]` and reads forward. Two things
make that seek walk over tombstones:

1. **No upper bound on prefix iterators.** `TransactionalColumnFamily` iterates with
   `prefix_same_as_start` only. The prefix extractor is the 8-byte Zeebe column family id, so
   RocksDB stops only at the end of the whole column family, not at the end of the scanned prefix.
   The Java `startsWith` check only runs on *live* keys. A poll for an empty type therefore skips
   every tombstone between its prefix and the next live key, including other types' tombstones.
   RocksDB never surfaces those tombstones to Java.
2. **Queue-shaped key ranges.** Job keys only increase. Activated jobs are always the oldest, so
   their tombstones sit exactly where the next poll's seek lands, the head of the type's range.
   The deadline column family (job timeout checker), timer due dates and job backoff have the same
   shape: time-ordered, deleted from the head, scanned from the head on every run.

Tombstones are expensive to keep around with the defaults:

- **Memtables:** a partition gets up to 6 memtables of `budget / 6 * 0.85`, about 48 MB each with
  `PARTITION` / 512 MB. A flush waits until 3 immutable memtables can be merged. At a few hundred
  writes per second, tombstones stay for hours in skip lists that every seek walks linearly. In
  the aged baseline, about 150k tombstones were still in memtables.
- **SST files:** leveled compaction drops a tombstone only when it reaches the bottommost level, or
  when nothing older overlaps it.

Iterator creation itself also gets more expensive as state ages. It acquires a super version and
builds a merging iterator over every memtable, every L0 file and each level. Creation alone cost
45 µs per iterator in the aged baseline and 2–3 µs after the fixes. Every `whileEqualPrefix`,
`isEmpty`, `count` and `exists`-by-iteration call pays that cost, and a poll costs 2 of them.

## Results: reads

Job polls (all phases), plus one deadline scan per second:

| Variant | Needs | Poll mean | Poll p99 | Deadline scan | Skipped keys / poll |
|---|---|---|---|---|---|
| baseline | – | 38 ms | 94 ms | 31 ms | 229k |
| wb8+merge1 | config | 3.3 ms | 11 ms | 4.4 ms | 27k |
| wb8+merge1+cod | config + factory | 1.1 ms | 3.7 ms | 1.5 ms | 11k |
| wb16+merge1+cod | config + factory | 1.0 ms | 3.0 ms | 1.7 ms | 9.7k |
| upper-bound | zb-db | 4.0 ms | 46 ms | 32 ms | 21k |
| ub+wb8+merge1 | zb-db + config | 0.35 ms | 4.1 ms | 4.1 ms | 2.8k |
| ub+wb8+merge1+cod | zb-db + config + factory | 0.13 ms | 1.5 ms | 1.9 ms | 1.0k |
| ub+wb16+merge1+cod | zb-db + config + factory | 0.16 ms | 1.9 ms | 2.0 ms | 1.0k |
| ub+sd+wb8+merge1 | zb-db + engine + config | 0.16 ms | 1.8 ms | 4.2 ms | 1.0k |
| ub+sd+wb8+merge1+hint | zb-db + engine + config | 0.09 ms | 0.8 ms | 2.9 ms | 0.5k |

Legend:

- `wbN` = `write_buffer_size` of N MB
- `merge1` = `min_write_buffer_number_to_merge=1`
- `cod` = compact-on-deletion collector with window 1000 and trigger 500
- `sd` = `SingleDelete` for write-once index entries
- `hint` = seek from an in-memory low watermark instead of the range head

Baseline-heavy variants could not reach the 100 jobs/s target: polls ate the whole loop. They
therefore accumulated *fewer* new tombstones during measurement, which favours them, so the gaps
above are conservative.

## Results: writes

Aging 3M job lifecycles (about 2.5 GB of logical writes) as fast as possible:

| Variant | Flush write | Compaction write | Throughput | Stalls |
|---|---|---|---|---|
| baseline | 197 MB | 6 MB | 94k jobs/s | 0 |
| wb8+merge1 | 411 MB | 37 MB | 89k jobs/s | 0 |
| wb8+merge1+cod | 412 MB | 225 MB | 91k jobs/s | 0 |
| wb16+merge1+cod | 297 MB | 111 MB | 91k jobs/s | 0 |
| cod | 197 MB | 12 MB | 90k jobs/s | 0 |
| sd+wb8+merge1 | 280 MB | 37 MB | 88k jobs/s | 0 |

Large memtables are what keep the baseline's write amplification low: create/delete pairs die
before they are flushed. That same property is what makes reads slow. 16 MB memtables keep almost
all of the read gain at about 2x the baseline's disk writes. That is still a small fraction of the
logical write volume, and it caused no stalls.

## Recommendations

In order of effort:

1. **Config only, today:** `write_buffer_size=16777216` and `min_write_buffer_number_to_merge=1`
   through `columnFamilyOptions`. About 10x on polls in this workload. Watch disk write throughput.
2. **zb-db, no API change:** set `iterate_upper_bound` to the successor of the scanned prefix in
   `TransactionalColumnFamily` for all prefix iterations. It can't be configured: it is a per-read
   `ReadOptions` field, and RocksJava can't plug in a custom prefix extractor. About 10x on its
   own, and a further 2–8x on top of item 1.
3. **Factory:** compact-on-deletion (opt-in `RocksDbConfiguration#setCompactOnDeletion` in this
   branch). It clears SST tombstones completely and cuts poll latency again by 3x on top of the
   memtable change. Next steps are wiring it to the broker configuration and evaluating it as a
   default.
4. **Engine:** start queue scans past the known-dead head. For activatable jobs, keep a per-type
   low watermark (lowered on every insert, raised to the first live key seen by a scan). For
   deadlines, timers and backoff, seek from the previous scan's timestamp. About 2x on top. Also
   use `SingleDelete` for write-once index entries; this reduces flush writes, but adds little to
   reads once compact-on-deletion is on.
5. **Defaults:** the memtable budget (2/3 of the memory per partition, flushed 3 at a time) is
   tuned for write amplification. It doesn't suit a delete-heavy, read-mostly queue workload, so
   reconsider it together with items 2 and 3.
6. **Observability:**
   - Tombstones in memtables are already visible without statistics:
     `rocksdb.num-deletes-active-mem-table` and `num-deletes-imm-mem-tables`.
   - With statistics on, `NUMBER_ITER_SKIP` divided by `NUMBER_DB_SEEK` directly measures this
     problem.

## Caveats

- Single runs on a shared cloud VM; numbers are directional.
- The workload is synthetic: one job value size, two hot types, a fixed tenant.
- The measured phase is short. Compactions and flushes happen mainly during aging, so the read
  table shows the *state* each config leaves behind, while the write table shows the cost of
  producing it.
- Not yet covered: `PARTITION` vs `BROKER` (shared cache and write buffer manager) across several
  partitions in one process, block size, and `periodic_compaction_seconds`.
