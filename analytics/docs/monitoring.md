# Monitoring — every meter the pipeline exposes

One table per concern, primer-audit-table style: name, type, tags, what it means, and what to
alert on. All meters are Micrometer, registered on the embedding application's `MeterRegistry`
(the analytics webapp exposes them under `/actuator/metrics` / `/actuator/prometheus`; the
standalone stage launchers pass a throwaway registry). Tag cardinality is bounded by design:
`stage` (projection|aggregation), `partition` (source/facts partition id), `dataset` (declared
dataset name), `store` (state-store/column-family name), `backend`
(rdbms|elasticsearch|opensearch), `factType`, `tier` (window size ms) — never key, window, or
instance.

## "Am I keeping up" — the lag pack

| meter | type | tags | meaning | alert on |
|---|---|---|---|---|
| `eb.streaming.watermark.lag` | gauge (ms) | stage, partition | `now − operator clock`: the min-of-sources stream-time clock across the partition's cube-tier mergers (stage 2). 0 before the first delta establishes a clock. The clock HOLDS while sources are quiet, so lag also climbs during genuine idle. | lag growth WHILE the source end-offset moves = falling behind; lag growth alone can just be an idle source |
| `eb.streaming.records.processed` | counter | stage, partition | records the task's `process()` loop consumed (folded or skipped). Unit per stage: one Zeebe record (stage 1), one shuffle envelope — a batch of cell deltas (stage 2). | a PARTITION flat while the source topic keeps moving = wedged consumer (the #23 signature); watch per partition, not the stage aggregate |
| `eb.streaming.dedup.skipped` | counter | stage, partition | producer duplicates skipped before the fold: the pre-fold Zeebe-position watermark (stage 1) or the segment-coordinate dedup (stage 2). A spike right after a restart is EXPECTED — it is recovery working. | nonzero rate OUTSIDE a restart window = duplicate source traffic |
| `eb.streaming.segments.sealed` | counter | stage=projection, partition | completed segments sealed into the shuffle (one per seal, not per cell) | flat while records.processed moves = nothing reaching stage 2 |
| `eb.streaming.deltas.merged` | counter | stage=aggregation, partition, tier | deduped cell deltas folded into the tier's running cells — one count per (delta, tier), since a composite delta rolls into every tier; sum across tiers ≠ delta count | a tier flat while stage 1 seals = shuffle or dedup problem |
| `analytics.facts.emitted` | counter | partition, factType | facts the base projection emitted into the dispatch fan-out | a factType at zero that should flow = projection gap |
| `analytics.projection.fact.dropped` | counter | partition | a derivation lost its fact (missing row) — a silent undercount surfaced | any nonzero value |

## "Is state healthy"

| meter | type | tags | meaning | alert on |
|---|---|---|---|---|
| `eb.streaming.store.overlay.entries` | gauge | stage, partition, store | active-overlay entries pinned in the bounded cache until the next checkpoint | sustained growth between cuts = overlay undersized |
| `eb.streaming.store.overlay.bytes` | gauge | stage, partition, store | approximate byte footprint of the cache (active + frozen + clean layers combined; heap-estimate, not serialized-to-measure) | approaching the configured budget = early cuts imminent |
| `eb.streaming.cut.early` | counter | stage, partition | cuts triggered by `needsCheckpoint()` (overlay full) rather than the commit cadence | sustained nonzero rate = overlay undersized for the load |
| `eb.streaming.write.stalls` | counter | partition | entries into the budget-exhausted write stall while a cut was in flight | any sustained rate = folding is blocking on persistence |
| `zeebe.rocksdb.*` | gauges | store (= state directory name), partition via directory | RocksDB property metrics (live-data vs SST sizes, memtable sizes, tombstones, pending compaction) exported by the state-store provider whenever a registry is wired (which the app always does) | tombstone/SST growth without bound = compaction not keeping up |

## "Is ingestion durable" — the commit-cut lifecycle (pre-existing)

| meter | type | tags | meaning | alert on |
|---|---|---|---|---|
| `eb.streaming.cut.freeze.duration` | timer | partition | wall time of freezing a commit cut on the processing thread (the residual per-commit pause; should read in microseconds) | p99 creeping into milliseconds = freeze doing too much work |
| `eb.streaming.cut.persist.duration` | timer | partition | wall time from persist pickup to full cut completion (transaction + source-offset ack) on the IO pool | growing = sink/DB slowness; compare against the commit interval |
| `eb.streaming.cut.retries` | counter | partition | failed cut persists that merged back for retry | any sustained rate = persistent sink failure |

## "Is the data correct" — correctness alarms (pre-existing unless noted)

| meter | type | tags | meaning | alert on |
|---|---|---|---|---|
| `analytics.aggregation.late.dropped` | counter | partition, dataset, tier | deltas dropped because their window finalized and evicted before they arrived — data missing from a finalized value | any nonzero value (each drop is quantified loss) |
| `analytics.projection.duplicate.skipped` | counter | partition | producer duplicates absorbed by the pre-fold watermark — expected under exporter retries | only in combination: high rate without exporter retries upstream |
| `analytics.projection.fold.row.missing` | counter | partition | a fold met a missing row — must never happen | any nonzero value |
| `analytics.projection.cube.facts.inspected` | function counter | partition, dataset | type-matched, activation-admitted facts the cube's gate inspected | — (context for the two below) |
| `analytics.projection.cube.facts.folded` | function counter | partition, dataset | inspected facts that passed the declared filters and folded | folded=0 while inspected climbs = filters match nothing |
| `analytics.projection.cube.silent` | gauge (0/1) | partition, dataset | 1 while the cube has inspected many admitted facts and folded none | =1 — the dataset is silently empty |
| `analytics.dataset.empty.alarm` | counter (new) | dataset | the silent-empty-cube alarm fired at a commit boundary (the graphable twin of the WARN log; at most once per cube wiring) | any increment |

## "Is serving healthy"

| meter | type | tags | meaning | alert on |
|---|---|---|---|---|
| `analytics.serving.rows.written` | counter | backend, dataset | rows (cells / snapshot rows / table rows) upserted successfully | flat while the pipeline merges = writes not landing |
| `analytics.serving.fenced.rejected` | counter | backend | writes rejected by the version fence — the fence working, not an error | nonzero NOT during a failover/rebalance window = zombie writer |
| `analytics.serving.fenced.writes` | function counter (pre-existing) | stage, partition | the same fence rejections attributed to the rejected writer's stage/partition | same as above; use this one to locate the zombie |
| `analytics.serving.write.duration` | timer | backend | wall time of one serving flush (RDBMS: batch execute + commit; documents: the whole staged flush) | p99 growing = serving store slowness backpressuring cuts |
| `analytics.serving.batch.size` | distribution summary | backend | documents per bulk request (Elasticsearch/OpenSearch only; RDBMS batches via JDBC and records no sizes) | consistently at the 1000-item cap = flushes splitting; consider cut cadence |
| `analytics.query.duration` | timer | dataset | end-to-end wall time of one dashboard read (`DatasetQueryExecutor.execute`: plan + fetch + app-merge + finalize) | p99 over the dashboard budget |

## Alerting story (documented, not implemented)

- **Falling behind**: `eb.streaming.watermark.lag` growing monotonically WHILE the source end-offset moves — the clock holds during genuine idle, so lag alone also climbs when no data flows; gate the alert on source movement (or on `records.processed` still ticking).
- **Wedged** (the #23 signature): a single partition's `eb.streaming.records.processed` flat
  while the source topic's end offset keeps moving — alert per partition; a wedged partition is
  invisible under the healthy partitions' stage aggregate.
- **Duplicate source traffic**: `eb.streaming.dedup.skipped` increasing outside a restart window
  (correlate with process start time / rebalance events).
- **Zombie writer**: `analytics.serving.fenced.rejected` (or `.fenced.writes`) nonzero outside a
  failover/rebalance window.
- **State pressure**: `eb.streaming.cut.early` at a sustained rate, or `write.stalls` moving —
  raise the overlay budget or shorten the commit interval.
- **Silent data loss**: `analytics.aggregation.late.dropped`, `fold.row.missing`,
  `fact.dropped` — all must-stay-zero.
- **Misdeclared dataset**: `analytics.dataset.empty.alarm` fired, or `cube.silent` = 1.
