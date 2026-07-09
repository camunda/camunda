# Analytics load smoke test — agent runbook

End-to-end validation of the analytics pipeline under the realistic bank-dispute load. Run it
after non-trivial changes to `analytics/*`, `event-bridge/*`, or the exporter. Everything runs
locally from the current branch; total wall time ≈ 60–75 min (build + 30-min load + analysis).

## Reference topology and rates (calibrated 2026-07-08)

- OC: 3 brokers = 3 partitions, RF 1 (`OC_BROKERS=3`). EB: `zeebe-records` 1 partition, RF 3
  (the default). One analytics app (`:8090`) running both stages + serving.
- **Baseline load: 1 instance/s** → ~5,300 records/s arrival. Known-good reference: processed
  tracks arrival within tens/s; backlog oscillates 0–20k (≈4 s worst) on a ~9-min cycle; app
  CPU ≤ ~50% of one core (cputime/etime); RSS < 1 GB; Stage RocksDB ~100 KB; H2 ~35 MB/30 min;
  ZERO `*-deleted` files on any EB node; no BLOCKED threads in dumps; no `Rejoin` storms in the
  app log.
- **Stretch load: 2 instances/s** → ~10,600 records/s. Expect OC adaptive backpressure after
  ~15 min (broker-0 = gateway + partition 1 pins its 2-core cap; arrival sags toward ~7k/s and
  Stage-1 RocksDB legitimately spills to ~40 MB as backpressure stretches scope lifetimes past
  the checkpoint interval — flat afterwards is PASS, monotone growth is FAIL). EB exporter lag
  must stay ~0 (check `zeebe_exporter_last_exported_position` vs
  `zeebe_stream_processor_last_processed_position` on `:9700..9702/actuator/prometheus`).

## Procedure

1. **Build from the branch under test** (never trust incremental state — two false-greens have
   come from it): the stack script builds by default; for the test gate itself use
   `./mvnw clean verify` on the changed modules first. If another Claude session shares this
   machine, do NOT run `./mvnw install` concurrently with it (shared `~/.m2` snapshots).
2. **Cleanup ritual (mandatory, in this order):**
   `pkill -9 -f camunda-load-tester; analytics/run-realistic-load.sh stop; analytics/run-stack-demo.sh stop`
   then verify `pgrep -f 'StandaloneCamunda|EventBridge|AnalyticsWebapp|load-tester'` is empty.
   Stray load-tester JVMs thrash the box and invalidate the run.
3. **Start:** `OC_BROKERS=3 EB_SKIP_DRIVER=1 analytics/run-stack-demo.sh start` (add
   `--skip-build` only if step 1 already built dist+examples+webapp). Wait for it to return,
   then verify: `curl :8088/v2/topology` → 3 brokers; `curl :8080/v1/topics` → both topics
   ACTIVE; `curl :8090/api/dashboard/processes` → 200; `curl :8090/api/datasets` includes
   `dispute-types` (the var-grouping exerciser).
4. **Load:** `WORKER_REPLICAS=4 STARTER_RATE_DURATION=1s analytics/run-realistic-load.sh start 1`
   (rate is the last arg; `STARTER_RATE_DURATION` must be an integer-unit duration).
5. **Measure every 15 s for 30 min** (script it in a scratch dir; do not commit):
   - committed offset: `GET :8080/v1/groups/analytics-stage1/offsets`
     → `.committedOffsets["zeebe-records"].offsets["1"]` (dense: Δ/Δt = records/s processed)
   - high watermark: `GET :8080/v1/topics/zeebe-records/partitions/1/records?offset=<committed>&maxWaitMs=0&maxBytes=1`
     → `.highWatermark` (probe AT the committed offset — offset 0 ages out and 416s)
   - backlog = HWM − committed; app `%cpu`,`rss` via `ps` (use cputime/etime for true CPU —
     spot `%cpu` is a bursty duty cycle); `du` of `/tmp/eb-demo/db/analytics-dataset.mv.db`
     and `/tmp/eb-demo/app/data`; `jstack <app-pid>` every 5 min.
6. **Stability gate at 6 min:** arrival near nominal and backlog oscillating (not monotone).
   If arrival is well under nominal → generation-bound (check broker CPU / spread clients over
   gateways :26500/:26510/:26520); if backlog grows monotonically while arrival holds →
   pipeline regression: STOP and investigate before burning the full window.
7. **Shutdown order matters:** `pkill -f 'spring.profiles.active=starter'` → wait for the
   committed offset to freeze (in-flight instances complete) → `pkill -f 'spring.profiles.active=worker'`.
   Leave the stack up for inspection; H2 is externally readable at
   `jdbc:h2:file:/tmp/eb-demo/db/analytics-dataset;AUTO_SERVER=TRUE` (user `sa`, empty password).
8. **Post-run checks:** thread dumps (no BLOCKED, everything parked between bursts —
   `RocksIterator.seek` frames on partition actors are the known residual, task #19);
   `ls /tmp/eb-cluster/node-*/data/*/partitions/1 | grep -c deleted` must be 0 on ALL nodes;
   app log free of `Rejoin` loops and ERRORs; `dispute-types` cube (DATASET table for it) has
   rows (null `var_type_` bucket is expected — the process rarely sets `type` at the root scope).

## Report format

Table of per-3-min arrival/s, processed/s, backlog, RSS, H2 MB, RocksDB KB; app cputime/etime;
broker CPU medians; dump summary; disk sizes; explicit PASS/FAIL against the reference values
above; any anomaly with its onset time.

## Known hazards

- A machine suspend mid-run invalidates throughput data (and previously triggered a rejoin
  storm — fixed, but rerun anyway).
- NO other Claude/IDE session may be actively working on this machine during the measurement
  window — not just Maven builds; repo-wide exploration alone measurably widens the backlog
  envelope (observed 2026-07-08: reference 0–20k stretched to 0–33k while a second session
  was writing an ADR mid-window).
- `run-stack-demo.sh start` WIPES `/tmp/eb-cluster` and `/tmp/eb-demo` data — copy evidence
  out first if a previous run is under investigation.
- The load generator, not analytics, is the first bottleneck above ~1.5 instance/s on a laptop:
  don't tune worker completion delay (300 ms is benchmark-faithful and shortening it weakens
  the test); add worker threads or spread gateway connections instead.
