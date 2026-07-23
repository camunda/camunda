# ADR 0003 — Parallel per-partition processing with a decode-ahead pipeline

- Status: Proposed (concept; ready for implementation)
- Date: 2026-07-06
- Scope: `event-bridge-streaming` runtime (`StreamRuntime` + internals)

## Context

`StreamRuntime.run()` drives its whole partition assignment from a **single
thread** that runs the entire cycle in sequence, per iteration:

```
rebalance.apply()
events = consumer.poll(maxPoll, pollTimeout)   // drain in-memory buffer, block up to pollTimeout if empty
group events by partition
for each partition, for each event:
    record = deserializer.deserialize(...)      // CPU: MsgPack/SBE + domain mapping
    task.process(record)                        // user logic
punctuate() on the tick
commitBarrier.commit() on the tick
```

This has two structural limits:

1. **No fetch/decode–process overlap.** Decode (`deserializer.deserialize` —
   MsgPack/SBE + domain mapping) and `task.process` run inline on one thread:
   `decode(N) + process(N)`. Decode of record *n+1* cannot begin until
   `process(n)` returns. When the buffer is empty, `poll()` also parks up to
   `pollTimeout` on that same thread.
2. **No processing parallelism across partitions.** One thread processes *all*
   assigned partitions serially, even though each partition is an independent
   shard (own task, own state, own offset). A runtime assigned P partitions on a
   many-core node still processes them on one core. The only current scale-out is
   `StreamRuntimeGroup` — N runtimes, each with its **own consumer** — which is
   coarse (parallelism is capped by consumer count and pays per-consumer fetch and
   coordinator overhead).

**We need to handle multiple partitions per runtime with real parallelism.** That
is the target this ADR designs for, with fetch/decode-ahead as an inherent part of
the same pipeline (it is what keeps a *single* partition fast, which the pool alone
cannot do).

### What is *already* solved (and must not be re-invented)

The **client** already pipelines the *network* fetch. `ConsumerImpl` owns a
`Prefetcher` + `PrefetchBuffer`: a background long-poll fetch is kept in flight per
owned partition, `poll()` drains an in-memory buffer without hitting the network,
and the next fetch overlaps the caller's processing (`prefetcher.kick()` after
`drain`). So **broker round-trip latency is already off the hot path.** This ADR is
about the *runtime* stage boundary the client's pipeline stops at: decode and
process still share one thread across all partitions.

### How the reference systems do this

- **Kafka Streams (task-executor model)** — a **polling thread** polls,
  **deserializes on the poll phase**, and enqueues *decoded* records into each
  task's input queue (and owns commit coordination); a pool of **`TaskExecutor`**
  threads each **lease** a processable task, run a bounded `process()` batch, and
  release it. A task is held by **at most one executor at a time** → concurrency is
  *across* tasks, single-writer-per-task is preserved, and poll-parallelism is
  decoupled from process-parallelism (one consumer feeds many cores). This is the
  model this ADR adopts.
- **Flink** — a source thread fetches + deserializes and hands off to the task
  thread via a bounded *Handover*; operator chains parallelize across key groups.
- **Samza** — `SystemConsumers` polls/buffers on its own thread; tasks pull from
  bounded per-partition buffers via a `MessageChooser`.
- **Spark Structured Streaming** — the next micro-batch is read while the current
  batch's tasks execute.

Common shape: **poll+decode on the producer side → bounded per-partition queues →
a pool of processors, one partition leased by one processor at a time, all
per-partition state single-writer.**

## Decision

Restructure `StreamRuntime` into three cooperating parts joined by bounded
per-partition queues, keeping **single-writer per partition** as the load-bearing
invariant:

```
Stage 1: fetch/decode (1 thread)         per-partition queues       Stage 2: executor pool (N threads)
────────────────────────────────         ──────────────────         ──────────────────────────────────
consumer.poll(maxPoll, budget)   ──▶  [ p0 | decoded, bounded ] ──▶  executor leases a *runnable* partition
group by partition                    [ p1 | decoded, bounded ]        decode already done upstream
decode each event                     [ p2 | decoded, bounded ]        process a bounded batch
(or wrap as DecodeFailure)            [ .. | ...             ]         punctuate / commit if due
route to that partition's queue                                        release the lease
backpressure on aggregate fill                                    control thread: rebalance apply, lease-coordinated revoke
```

### 1. Stage 1 — fetch / decode / route (one thread)

Polls the consumer (network already prefetched), groups the batch by partition,
**decodes** each event to `R`, and routes it into that partition's bounded input
queue. A decode failure is routed as a `DecodeFailure(offset, cause)` marker, not
handled here (policy stays with the leaseholder — see §7). Touches only the
consumer's `poll` and the deserializer and the queues' producer side — never a
`Task`, offset, or punctuation clock.

Decode lives here (not in the executors) so that a **single** partition still
overlaps decode with process: Stage 1 decodes ahead while the one executor holding
that partition processes. If Stage-1 decode ever becomes the bottleneck under many
heavy-decode partitions, it can be widened to a small decode pool that partitions
work *by partition* (preserving per-partition order) — noted as a tunable, not
built now.

### 2. Per-partition input queues (bounded, back-pressured)

One bounded SPSC-ish queue per owned partition (single producer = Stage 1; single
consumer = whichever executor currently leases it). Bounding gives back-pressure:

- Stage 1 bounds each `poll` to the **aggregate free capacity** across queues
  (`consumer.poll` already takes a `maxRecords`) and parks when full, so it never
  has to hold records it can't place. Full queues ⇒ Stage 1 stops draining the
  client buffer ⇒ the client's `prefetchDepth` stops the network prefetch —
  **end-to-end back-pressure to the broker**, bounded heap.
- Per-partition skew (one hot partition monopolising the budget) is handled by
  generous per-partition bounds now; the precise fix is consumer-level
  **pause/resume per partition** (as KS does), which needs a small client addition
  and is noted as a refinement, not a prerequisite.

### 3. Stage 2 — executor pool + partition leasing (the single-writer invariant)

A fixed pool of executor threads. Each executor loops:

1. **Acquire a runnable partition** — one that is *lease-free* and *due*: queue
   non-empty, **or** punctuation due, **or** pending offset due for commit. (Making
   "due" part of runnability is what lets idle partitions still punctuate/commit —
   see §5.)
2. **Lease** it (mark it `LEASED` by this executor).
3. Do its due work: process a **bounded batch** from its queue (baseline dedup →
   `task.process` → track pending offset → `punctuator.observe`), run its
   punctuation tick if due, run its commit if due (§4).
4. **Release** the lease; signal so the partition can be picked again.

> **Invariant (load-bearing): a partition is leased by at most one executor at any
> instant.** Everything that mutates a partition's task, queue-consumer side,
> pending offset, or stream-time happens under its lease. This preserves the exact
> single-writer-per-partition guarantee the current design gets from being
> single-threaded — no locks inside `Task`, just the lease around it.

`PartitionTasks`, `PunctuationDriver`, and `CommitBarrier` become **per-partition**
(sharded maps guarded by the lease) rather than single-thread-owned. They keep
their responsibilities; only their concurrency contract changes from "run thread
only" to "leaseholder only".

### 4. Commit — per-partition atomic cut, self-committed under the lease

The commit barrier is already **per-partition** (each partition is its own atomic
cut). So the executor that just processed partition *p*, still holding *p*'s lease,
runs *p*'s commit inline when *p*'s commit interval has elapsed: flush → make
output durable → persist state+offset in *p*'s transaction → advance *p*'s source
offset (`consumer.commitOffset`). No cross-thread lease transfer, no global commit
sweep. Each partition commits independently on its own clock, executed by whoever
holds it. `consumer.commitOffset(...).join()` blocks only that one executor, not
the others.

### 5. Punctuation — under the lease; idle partitions via due-runnability

Event-time and wall-clock punctuation mutate task state, so they run **under the
lease**, by the leaseholder, exactly like process. An **idle** partition (empty
queue) is normally never leased — so it becomes *runnable* when its punctuation or
pending-commit tick is due (§3.1). An executor then leases it, runs the wall-clock
tick / event-time advance / pending commit, and releases. This preserves the
current guarantee that idle partitions still finalize windows and flush, with no
separate timer thread. (If the pool is saturated by hot partitions, idle-partition
ticks are delayed by at most the time to free an executor — acceptable and
bounded.)

### 6. Rebalance — control thread, lease-coordinated revoke

Rebalance callbacks still fire on the heartbeat thread and only record deltas. A
small **control thread** applies them (replacing the old run thread's
`rebalance.apply()`):

- **Assign:** register the partition (create its queue, lazily materialize its task
  on first lease, `seekToBeginning` for a stateless shard rebuild — ADR 0002).
  Stage 1 begins routing to it; executors begin leasing it.
- **Revoke:** mark the partition `REVOKING` so **no new lease is granted**; wait
  for any in-flight lease to release; then commit its last work, close its task,
  drop its queue, and stop Stage 1 routing to it. The lease is what makes revoke
  safe under concurrency — it drains the current batch cleanly instead of racing.

### 7. Errors — decode markers, fail-fast stops the pool

Decode failures travel as `DecodeFailure` markers (§1); the leaseholder applies the
existing `RecordExceptionHandler` policy for that partition — `SKIP` advances past
the offset, `FAIL` sets the shared `running=false` so **all** executors and Stage 1
wind down (a restart resumes every partition from its last commit; no silent loss).
Fail-fast stays deterministic at the exact failing offset because the marker is
processed in per-partition offset order by the single leaseholder.

### 8. Shutdown ordering

`stop()` → stop Stage 1 (no new records) → let executors drain in-flight leases and
run a final per-partition commit → close tasks → close consumer. No decoded record
is lost or double-applied across stop.

### Threading summary

Per `StreamRuntime`: **1** Stage-1 thread + **N** executor threads + **1** control
thread (`N` defaults to available cores, capped by assigned partition count — more
executors than partitions is waste). `StreamRuntimeGroup` still composes several
runtimes for **multi-consumer / multi-node** scale-out; within a node, the pool now
gives **multi-core processing off a single consumer**, which the group could only
approximate with more consumers. The two are orthogonal and compose.

## Rationale

- **Parallelism across partitions is the point.** P independent shards can now use
  up to min(P, N) cores off one consumer, instead of one core for all P. This is
  the multi-partition capability we need to prepare for.
- **Leasing, not locking, preserves correctness.** One lease per partition
  reproduces the single-writer guarantee the current code gets from being
  single-threaded, so `Task` implementations stay lock-free and unchanged.
- **Decode-ahead keeps single partitions fast.** Putting decode on Stage 1 (not the
  executors) means even a lone partition overlaps decode with process — the pool
  alone can't do that, since one partition = one lease.
- **Commit and punctuation stay per-partition atomic cuts.** They already are;
  running them under the lease that already exists needs no new coordination and
  keeps each partition an independent recovery unit.
- **Back-pressure by construction.** Bounded per-partition queues chain to the
  client's `prefetchDepth`; nothing on the path is unbounded, and queue depth is a
  natural per-partition lag metric.
- **Idle-partition guarantees preserved** via due-runnability — no separate timer
  machinery.

## Consequences

- The runtime goes from 1 thread to `~N+2` threads. `PartitionTasks`,
  `CommitBarrier`, and `PunctuationDriver` change concurrency contract from
  "run-thread-only" to "leaseholder-only", guarded by the lease registry.
- A **lease registry** (partition → state `FREE`/`LEASED`/`REVOKING` + owner) is
  new shared state and must itself be correct under concurrency (a small, tightly
  scoped lock or lock-free state machine) — it is the one genuinely concurrent
  component and the main place to get right and test hard.
- Two+ threads touch the (thread-safe) `Consumer`: Stage 1 `poll`, executors
  `commitOffset`, control `seek`/`seekToBeginning`. Relies on the documented
  `Consumer` thread-safety (already true).
- Per-partition ordering holds (single producer per queue, single leaseholder per
  partition); cross-partition order remains irrelevant (independent shards), as
  today.
- Per-partition queue skew can, worst case, let one hot partition throttle Stage 1
  polling for others until consumer-level per-partition pause/resume is added.

## Implementation plan

1. **Queues + records.** `internals/PartitionQueue` (bounded, single-producer /
   single-consumer) holding `Decoded(offset, R)` / `DecodeFailure(offset, cause)`;
   a registry `Map<Integer, PartitionQueue>` for owned partitions.
2. **Lease registry.** `internals/PartitionLeases` — per-partition
   `FREE`/`LEASED`/`REVOKING` + owner, with `tryLease(runnable-predicate)`,
   `release`, `beginRevoke`/`awaitRelease`. This is the concurrency core; unit-test
   it in isolation.
3. **Stage 1 `RecordSource`.** Poll → group → decode → route to queues, bounded by
   aggregate free capacity; wrap decode failures as markers.
4. **Executor pool.** N threads running acquire-runnable → lease → process bounded
   batch → punctuate/commit if due → release. Route `DecodeFailure` to
   `onRecordError`.
5. **Per-partition-ize** `PartitionTasks` / `CommitBarrier` / `PunctuationDriver`
   to the leaseholder contract (mostly: their maps already key by partition; the
   change is who may touch them).
6. **Control thread.** Apply rebalance deltas; lease-coordinated revoke (mark →
   await release → commit + close + drop queue).
7. **Lifecycle.** Start Stage 1 + pool after `restore()`; `stop()` per §8.
8. **Config.** `executorThreads` (default = cores, capped by partitions),
   `partitionQueueDepth`; reuse `maxPoll`/`pollTimeout` for Stage 1.
9. **Tests.** Concurrency-critical: per-partition ordering under load; a partition
   is never processed by two executors (lease invariant); commit is a clean
   per-partition atomic cut under the lease; idle-partition wall-clock punctuation
   still fires when its queue is empty; revoke drains the in-flight lease before
   handoff (no race, no re-materialized task); fail-fast stops the whole pool at the
   exact offset via a `DecodeFailure`; back-pressure bounds heap and parks Stage 1;
   many partitions across N executors saturate cores; clean shutdown drains without
   loss or double-apply.

## Alternatives considered

- **Single-processor decode-ahead only (no pool).** Two threads: fetch/decode →
  one processor. Simplest, and overlaps decode with process — but processes all
  partitions on one core, so it does **not** deliver the multi-partition
  parallelism we need. Rejected as the end state; it is effectively the N=1 case of
  this design and a fine intermediate milestone if we want to land the pipeline
  before the pool.
- **Scale only via `StreamRuntimeGroup` (more consumers).** Already exists, but
  parallelism is capped by consumer count and pays per-consumer fetch/coordinator
  overhead; it cannot use more processing threads than consumers. The pool
  decouples the two. The group still composes for multi-node scale-out.
- **Decode inside the executors (raw queues).** Would parallelize decode across
  partitions for free, but re-serializes decode with process on a **single**
  partition (one lease) — losing the single-partition overlap. Rejected; keep
  decode on Stage 1, widen it only if measured to be the bottleneck.
- **Global commit/punctuation sweep on a dedicated thread.** Would need to lease
  every partition away from its executor to commit/punctuate. Rejected: the barrier
  is already per-partition, so self-commit under the existing lease is simpler and
  keeps partitions independent.

```
```

