# ADR 0004 — Actor-per-partition processing with async sink commits

- Status: Proposed (concept; ready for implementation)
- Date: 2026-07-06
- Scope: `event-bridge-streaming` runtime (`StreamRuntime` + internals)
- Supersedes: the bespoke lease-pool processing model of
  [ADR 0003](0003-decoupled-fetch-decode-pipeline.md) (the source/decode-ahead stage
  and the per-partition atomic-commit semantics are kept; the `PartitionScheduler` +
  processor-pool concurrency core is replaced)

## Context

ADR 0003 introduced multi-partition parallelism via a bespoke `PartitionScheduler`
(a lease registry + `ReentrantLock`/`Condition`) and a fixed pool of
`PartitionWorker` threads. It works and is tested, but two facts push us to a
different concurrency substrate:

1. **The rest of `event-bridge` already runs on the Zeebe actor scheduler.**
   `event-bridge-broker`, `-stream`, `-messaging`, `-consumer-groups`, and
   `-cluster-metadata` all depend on `zeebe-scheduler`, and `event-bridge-streaming`
   already depends on `zeebe-db`. The lease pool is a *second*, hand-rolled
   concurrency model in a codebase that otherwise gets single-writer execution,
   timers, and async composition from actors. That is exactly what an actor
   provides: an actor's jobs never run concurrently, so per-partition single-writer
   is free — no lease, no lock.

2. **Processors write to blocking DB sinks.** The serving-store sink
   (`DatasetWriter`: `upsertCell`/`upsertRow`/`flush`) is synchronous, backed by
   RDBMS (JDBC/MyBatis — no async driver) and ES/OS. In the lease pool, the
   produce-before-commit barrier calls the sink and `consumer.commitOffset(...).join()`
   **on a processing thread**, so a DB round-trip steals a core from folding. We
   want the commit's blocking I/O off the processing threads while still honouring
   produce-before-commit (offset advances only after the sink write is durable).

## Decision

Process each partition on its **own actor**, driven by the shared Zeebe
`ActorScheduler`, and offload the blocking commit to an IO executor with the offset
advance chained to its completion. The decode-ahead source stage from ADR 0003 is
unchanged.

```
source thread (plain, blocking)         per-partition actor (cpu-bound)         sink IO executor (blocking)
───────────────────────────────         ───────────────────────────────         ───────────────────────────
poll → decode → route to queue   ──▶  onWork: drain queue, process (fold)
(bounded put = back-pressure)          runAtFixedRate: punctuate / commit tick
materialize/revoke partitions          on commit tick, if pending:
                                          offload committer.commit(p, offset) ──▶ flush sink + checkpoint +
                                          actor.runOnCompletion(future, …)  ◀───   consumer.commitOffset  (durable)
                                          → clear pending, resume folding
```

### Delegation, not inheritance

Per the refactor constraint, no runtime class **extends** `Actor`. `PartitionActor`
*has-a* actor, built through the framework's delegation API
(`Actor.newActor().actorStartedHandler(control -> …).build()`), and captures the
`ActorControl` to schedule its own work. `PartitionActor` exposes plain methods
(`offer`, `signalWork`, `requestStop`) and never leaks the actor type. This keeps it
unit-testable and keeps the actor an implementation detail.

### 1. Source stage — unchanged (plain thread)

`SourceLoop` stays a single plain thread driven by `StreamRuntime.run()` on the
calling thread (so the caller's thread is the one blocked in poll and interruptible
on shutdown). It polls, decodes, and routes decoded records into each partition's
bounded `PartitionQueue` with a blocking `put` (back-pressure), and owns everything
touching the fetch cursor (poll, seek, rebuild). It is deliberately **not** an actor:
its poll and bounded-put both block, which an actor must never do.

Instead of a scheduler, it holds a registry `Map<Integer, PartitionActor>`. Routing
a record does `partitionActor.offer(entry)` (blocking put) then
`partitionActor.signalWork()` (an `ActorCondition.signal()` — the thread-safe
foreign→actor trigger). Materialize creates a `PartitionActor`, submits it to the
scheduler (cpu-bound), and registers it; revoke calls `requestStop()` and awaits it.

### 2. PartitionActor — the single-writer unit (cpu-bound)

One actor per owned partition. On start it captures its `ActorControl` and sets up:

- `onCondition("work", this::onWork)` — the source signals it after routing.
- `runAtFixedRate(punctuationInterval, this::onPunctuationTick)` — freshness tick.
- `runAtFixedRate(commitInterval, this::onCommitTick)` — durable commit tick.

`onWork` drains a bounded batch from the queue and processes it: baseline dedup →
`task.process` → advance pending offset → observe stream time; a carried
`DecodeFailure` is replayed to the `RecordExceptionHandler` (fail-fast stops the
runtime at the exact offset). If more remains, it re-submits itself
(`actorControl.submit`) so timers still interleave. All of this runs on the actor's
thread → **single-writer, no locks**, replacing the lease invariant of ADR 0003.

### 3. Async commit — blocking I/O off the actor thread

The commit is the only place that blocks on the DB, so it is the only thing
offloaded. On the commit tick (or memory-pressure `needsCheckpoint`), if there is
pending work and no commit is already in flight, the actor:

```
committing = true;
final long offset = partition.pending();
sinkExecutor.execute(() -> complete(committed, () -> committer.commit(partition, offset)));
actorControl.runOnCompletion(committed, (ok, err) -> {
    committing = false;
    if (err == null) { partition.clearPending(); partition.markCommitted(now); }
    // else keep pending — retried on the next commit tick
    actorControl.submit(this::onWork);          // resume folding
});
```

While `committing` is true the actor does not fold, punctuate, or start another
commit, so the sink write, RocksDB checkpoint, and offset store on the IO thread have
**exclusive** access to the partition's task — single-writer is preserved by
suspension, not by a lock. The offset advances only inside `committer.commit`, after
the sink write is durable — produce-before-commit is intact, now non-blocking to the
processing threads. `consumer.commitOffset` keeps its own `.join()` inside the
offloaded block (it already runs off the actor thread there).

`committer.commit(partition, offset)` does only the durable work (flush →
owns-durability `commit` **or** shared transaction → `consumer.commitOffset`); all
partition bookkeeping (`pending`, clocks) is mutated only on the actor thread, in the
continuation. The shared runtime-managed durability path keeps its monitor so
concurrent commits across partitions stay correct; owns-durability commits (the
analytics production path) share nothing and run fully parallel.

### 4. Punctuation

`onPunctuationTick`/`onCommitTick` are `runAtFixedRate` timers on the actor — no
scheduler due-time computation, no idle-wake bookkeeping. Punctuation (`flush` +
wall-clock + event-time) runs on the actor thread; it is skipped while a commit is in
flight. Tasks keep the light `flush()` cheap and put durable sink writes in the
commit barrier (`preCommitFlush`/`commit`) — which is where the SPI already draws the
line and where the offload applies.

### 5. Lifecycle

- **Scheduler ownership.** The builder accepts an `ActorScheduler` (so a node can
  share one cpu-bound pool across many runtimes — the thread-count knob is
  `setCpuBoundActorThreadCount`). If none is supplied, the runtime creates and owns a
  private scheduler sized by `processorThreads`, and closes it on stop. The blocking
  `sinkExecutor` is likewise injectable, defaulting to a bounded owned pool.
- **Startup.** Subscribe → register rebalance listener → heartbeat → restore
  (seed baselines + seek) → run `SourceLoop` on the calling thread. Partition actors
  are materialized as partitions are assigned/discovered.
- **Shutdown.** `stop()` unwinds the source loop; each partition actor is asked to
  stop (a stop condition), does a final synchronous commit of its pending work,
  closes its task, and signals done; then the runtime closes the scheduler, the sink
  executor, and the consumer. A commit in flight is drained before finalizing.

### Threading summary

Per node: one cpu-bound actor pool (shared or per-runtime) multiplexing all partition
actors, one bounded sink IO executor for blocking commits, and one source thread per
runtime. Partition count no longer implies thread count — the actor scheduler
multiplexes N partition actors onto its bounded pool, so this scales to many
partitions without many threads. `StreamRuntimeGroup` still composes runtimes for
multi-consumer/multi-node scale-out.

## Rationale

- **Reuse the codebase's concurrency substrate.** Deletes the bespoke
  `PartitionScheduler` + lease + pool (the highest-risk code in ADR 0003) in favour
  of the actor framework already trusted across `event-bridge`. Single-writer,
  timers, and async continuation come from the framework.
- **Async sink commits.** The blocking DB write and offset commit move to an IO
  executor; the actor suspends (not the thread), so folding on other partitions keeps
  the cores busy. Produce-before-commit is preserved because the offset advance is
  chained to the write's completion.
- **Single-writer without locks.** An actor never runs two jobs at once, and a
  partition suspends during its commit, so nothing touches a partition's task
  concurrently — same guarantee as the lease, with less machinery.
- **Delegation keeps it testable and the actor an implementation detail.**

## Consequences

- `event-bridge-streaming` gains a compile dependency on `zeebe-scheduler` (already a
  transitive presence in the subsystem; consistent with sibling modules).
- The `Task` SPI and the `StreamRuntime` builder API are **preserved**; new optional
  builder inputs: `actorScheduler(...)`, sink-executor sizing. The two
  `Analytics*Stage.main()` files switch from `.processorThreads(n)` to supplying (or
  defaulting) an `ActorScheduler` — a small wiring change, not an API break.
- A task's `flush()`/punctuation still runs on the actor (cpu) thread, so tasks must
  keep `flush()` light and place durable sink writes in `preCommitFlush`/`commit`
  (the offloaded barrier) — matching the SPI's existing distinction. Blocking inside
  `process()` is discouraged (it holds a cpu actor thread); the analytics processors
  are CPU + local-RocksDB, so this holds today.
- Memory visibility across the actor thread and the IO executor is carried by the
  `ActorFuture` completion → `runOnCompletion` happens-before; a partition never has
  two commits in flight, so its bookkeeping stays consistent.

## Implementation plan

1. **Dependency.** Add `zeebe-scheduler` to `event-bridge-streaming/pom.xml`.
2. **`PartitionActor`** (delegates to `Actor.newActor()`): conditions + timers +
   `onWork`/`onPunctuationTick`/`onCommitTick` + async `beginCommit` + stop.
3. **`PartitionCommitter.commit(Partition, long offset)`** — durable work only,
   no bookkeeping.
4. **`SourceLoop`** — swap the scheduler for a `PartitionActor` registry;
   `offer`/`signalWork`/`requestStop`; keep poll/decode/route/rebuild.
5. **`StreamRuntime`** — own/inject the `ActorScheduler` and sink executor; wire
   materialize → `submitActor(cpu)`; shutdown ordering.
6. **Remove** `PartitionScheduler`, `PartitionWorker`, and their test.
7. **Config.** `actorScheduler(...)`, sink-executor size; keep
   `partitionQueueCapacity`, `maxProcessBatch`; `processorThreads` sizes the owned
   scheduler.
8. **Tests.** Per-partition ordering; parallel folding across partitions; async
   commit does not block folding (a slow sink on one partition does not stall
   another); produce-before-commit (offset advances only after the sink write);
   fail-fast at the exact offset; idle punctuation via the timer; revoke drains and
   closes; clean shutdown with a final commit; back-pressure via the bounded queue.

## Alternatives considered

- **Keep the ADR 0003 lease pool.** Correct and tested, but a second concurrency
  model in an actor-based codebase, and its commit blocks a processing thread on the
  DB. Superseded.
- **Extend `Actor` directly.** Rejected per the delegation constraint — it would leak
  the actor lifecycle into the domain class and make it harder to test in isolation.
- **A dedicated IO actor (pool) for commits instead of an executor.** Idiomatic, but
  a plain bounded executor + `CompletableActorFuture` completion is simpler, needs no
  second actor lifecycle per partition, and a partition never has two commits in
  flight so no ordering concern. The executor is the one place blocking is allowed.
- **Make `DatasetWriter.flush()` return a future (true async ES/OS).** More power,
  but pushes async into sink authors and does nothing for JDBC (no async driver).
  Deferred until the IO-offload is shown insufficient.

```
```

