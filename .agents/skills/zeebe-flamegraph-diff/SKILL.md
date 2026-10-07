---
name: zeebe-flamegraph-diff
description: Parse and compare async-profiler CPU flamegraphs from Camunda/Zeebe brokers — benchmark nodes or production. Use when investigating a CPU regression or outlier with .html flamegraphs (e.g. from dashboard.benchmark.camunda.cloud daily runs, or pulled from a live cluster/customer incident) — attribute a node's CPU to Zeebe subsystems and diff a suspect run against a healthy baseline.
---

# Zeebe flamegraph diff

Tooling to read async-profiler HTML flamegraphs from Camunda 8 / Zeebe brokers
and answer: **"where is this node burning CPU, and what changed vs a healthy
run?"** Works on any pair of comparable flamegraphs — daily load-test captures
(per-node, per-protocol) from `dashboard.benchmark.camunda.cloud`, or ad-hoc
captures pulled from a production/customer cluster during an incident.

## When to use

- A benchmark/load-test run, or a production node/pod, is flagged problematic
  (backpressure, dropped requests, low throughput, high CPU) and you have
  per-node `.html` flamegraphs.
- One node/broker is a CPU outlier vs its peers.
- Confirming whether a CPU/throughput regression is a code change, a config/
  sizing change, or just load — on a benchmark run or in production.

If you don't have a profile yet: benchmark nodes emit one automatically per
daily run; on production you need `async-profiler` attached to the JVM (or an
equivalent continuous-profiling agent) and must capture one yourself — this
skill only covers reading and diffing `.html` output, not attaching/capturing.

Pair with Grafana (`prometheus` datasource) for the metrics half of the story —
the flamegraph tells you *where* CPU goes; metrics tell you *whether it
matters* (see step 4). For benchmark runs, also pair with the `load-test-ops`
skill (triggering/monitoring runs).

## Critical gotchas (read first)

1. **Never diff a gRPC profile against a REST profile.** Each daily run emits
   TWO flamegraphs per node — one while the gateway serves gRPC, one for REST.
   Their request-handling stacks are completely different (REST = Tomcat/coyote +
   Spring Security filter chain + `BearerTokenAuthenticationFilter`; gRPC = Netty
   event loop, no Tomcat). Diffing across protocols produces garbage. Filenames
   do NOT say which is which — detect it (step 1).
2. **Sample counts are not comparable across files.** Different capture windows →
   different totals. Only compare **percentages** (all scripts output %).
3. **Lambda/proxy names and native `.so` files carry per-run random ids**
   (`$$Lambda.0x…7b91000.run`, `librocksdbjni<random>.so`). Raw they look like
   huge diffs for identical code. `fg_diff.py` normalizes these; `grep` over the
   raw HTML will also miss compressed names — always parse, don't grep.

## Workflow

All scripts live in `scripts/` and share `fg_common.py`. Run them from that dir
(or `cd` there) so the import resolves.

### 1. Identify each file's protocol (gRPC vs REST)

```bash
cd scripts
python3 fg_top.py <file.html> 40 | grep -iE "coyote|tomcat|BearerToken|SingleThreadIoEventLoop|FrameworkServlet"
```

Tomcat/coyote/`FrameworkServlet`/`BearerToken` present → **REST**. Only Netty
(`SingleThreadIoEventLoop`) with no Tomcat → **gRPC**. Pick matching protocols on
both sides of a diff.

### 2. Attribute one node's CPU to subsystems

```bash
python3 fg_subsystems.py <file.html>
```

Leaf-based split across Zeebe subsystems (exporter, replay, processing,
rocksdb-state, journal-flush, raft-netty, grpc, gc). ~70-75% coverage is normal
(native/syscall scatter → `other`). See
[`references/zeebe-contributors.md`](references/zeebe-contributors.md) for what
each subsystem is and which frames feed it.

### 3. Diff suspect vs healthy baseline

```bash
python3 fg_diff.py <baseline.html> <candidate.html> [min_delta_pct]
```

Inclusive-% diff, volatile ids normalized. "Grew in candidate" = where the
regressed run spends more; "Shrank" = where the healthy run spent more. Ignore
generic roots that move together (`thread_native_entry`, `start_thread`,
`Thread::call_run`, `Executors$RunnableAdapter.call`, the renamed `.so`) — they
are attribution shuffles, not signal. Focus on named Camunda/RocksDB/journal
frames.

### 4. Confirm with metrics — the efficiency lens (don't stop at the flamegraph)

A flamegraph shows CPU *distribution*, not efficiency. Two runs can have an almost
identical profile shape yet very different throughput. Pull the metrics half from
Grafana (`prometheus` datasource). Namespace/datasource depends on where the
profile came from: daily benchmarks live under
`c8-medic-daily-<date>-<hash>-test`; a production or customer cluster will be on
a different namespace/datasource — confirm you can actually reach it (customer
envs are often not scrapeable from the same Grafana) before assuming the
metric half is available at all.

Metric cheat-sheet (all rate/histogram over the steady window):

| question | metric |
|---|---|
| CPU per pod / saturated? / outlier? | `container_cpu_usage_seconds_total` |
| backpressure / shed load | `zeebe_dropped_request_count_total`, `zeebe_backpressure_*` |
| processing throughput | `zeebe_stream_processor_records_total{action="processed"}` |
| bytes persisted / append rate | `atomix_journal_append_data_rate_total`, `atomix_journal_append_rate_total` |
| flush count + latency | `atomix_journal_flush_time_seconds_{count,sum,bucket}` |
| per-record cost by type | `zeebe_stream_processor_processing_duration_seconds_bucket` (group by `valueType,intent`) |
| state size (rule out data growth) | `zeebe_rocksdb_live_estimate_live_data_size`, `_num_keys`, `zeebe_rocksdb_sst_total_sst_files_size` |
| GC | `jvm_gc_pause_seconds_*` (STW) vs the `gc` flamegraph bucket (concurrent, off-pause) |

**The key derived signal is work-per-core.** Compute CPU/record and CPU/MB-persisted
on both runs. Interpreting the combination:

- **CPU pinned at the request/limit** → node is *saturated*; a regression then shows
  up as **lower throughput at flat CPU**, NOT higher CPU. Don't expect the CPU line
  to move — divide it by throughput.
- **Flat CPU + throughput down + a subsystem grew in the diff** → *efficiency*
  regression (more CPU per unit work), not more load.
- **Processing-duration median flat but p99 tail up** → usually a *saturation
  queueing* symptom, not a per-record slowdown; and the extra CPU lives *outside*
  `processCommand` (replay/commit/flush/state access, which that metric excludes).
  Break the histogram down by `valueType,intent` to see if one record type
  regressed vs a uniform shift.

### 5. Decide: is it even a hot-path *code* regression?

Before blaming a commit, separate "code got slower" from "code runs more often" from
"config/infra changed":

- `git log -1 --format=%ad -- <path/to/hot/file>` for the top grown frames. **If the
  hot file hasn't changed in the regression window, the code didn't get slower — it's
  being *called more per record*, or a config/flag/sizing changed.** Chase the caller
  or the config, not the frame.
- Rule out data growth: compare RocksDB state size / num_keys. Flat state + more
  state-access CPU = more ops per record, not more data.
- Narrow the window cheaply: for daily benchmarks, one build/day means you can
  **binary-search across days** (is the run between healthy and broken already
  broken?) to shrink the commit range before diffing 100s of commits. For
  production, narrow by deploy history instead — which version/config was live
  at each past-good vs first-bad capture.

## CPU-mode profiling gotchas (async-profiler `-e cpu`)

These bite every flamegraph read, not just regressions:

- **On-CPU only.** CPU-mode samples running threads; **off-CPU time is invisible**
  (blocked on disk, lock, socket, park). So a slower disk / slower ES backend does
  **not** raise CPU samples — it shows as lower throughput with threads parked. Never
  infer "X got slower" from more CPU in X; infer "X did more on-CPU work".
- **Syscall CPU folds onto a userland stub.** Without kernel stacks (needs
  perf_events + `perf_event_paranoid<=1` + kernel symbols), kernel time (e.g.
  `msync`/`fsync` dirty-page scan, socket writes) is attributed to the glibc syscall
  trampoline (`__syscall_cancel_arch`) or the JNI leaf. You then can't split user vs
  kernel from that file — re-profile with kernel stacks, or add a wall-clock/off-CPU
  profile, to see the real split.
- **Concurrent GC ≠ pause.** The `gc` bucket (G1 concurrent marking) burns CPU on GC
  threads without showing as STW pause; cross-check `jvm_gc_pause_seconds`.

## Reading the diff → hypothesis (generic patterns)

| Diff signature | Likely direction |
|---|---|
| `rocksdb-state` / `TransactionalColumnFamily` up, state size flat | more state ops per record (caller change / config), not bigger data |
| `journal-flush` / `msync` up, flush-rate flat or down, p99 flat | heavier work per flush (larger scanned region / segment sizing), not disk stalls |
| `exporter-es-client` up, ES health degraded | exporter blocked/retrying on a slow backend |
| `gc` up, allocation metrics up | allocation-pressure regression |
| whole profile shape ~unchanged, throughput down at flat CPU | efficiency regression — find it by ratio, not by eye |
| one `valueType,intent` tail explodes, others flat | a specific processor/path, not a global slowdown |
