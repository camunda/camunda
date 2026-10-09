# Spike: dedicated actor thread pool per Physical Tenant

> Input for an implementation session. Context: Define phase of
> [product-hub#3778](https://github.com/camunda/product-hub/issues/3778) (Reduce Noisy Neighbour
> Risks for Physical Tenants). This is a **spike**: the goal is a working, measurable prototype
> behind a flag, not a production-ready feature.

## Hypothesis

Running each Physical Tenant's (PT) partition actors on a dedicated, configurably sized actor
thread pool, and letting the OS scheduler share CPU between pools, is enough to keep one tenant's
spike from degrading its neighbours, without any new rate-limiting algorithm.

## Why this design (decisions already taken)

The full discussion is summarized here so the implementer does not re-open it.

- **Today all PTs share one `ActorScheduler`** (broker-wide, work-stealing across all actors).
  A spiking PT consumes the shared actor threads, and a long-running job (e.g. a slow FEEL
  evaluation) blocks a thread for every tenant.
- **The existing adaptive limiter punishes the victim.** Each partition's AIMD request limiter
  reacts to its own processing latency. When a neighbour saturates the shared threads, the
  victim's latency rises and the victim's limiter backs off. With a dedicated pool, each PT's
  latency reflects only its own load, so the existing limiter starts throttling the right tenant.
- **The OS provides the fairness.** Actor scheduling is cooperative (not preemptive), but threads
  are preempted by the kernel. With one pool per PT:
  - a long job only stalls its own tenant's pool;
  - under host CPU contention the kernel shares CPU equally per runnable thread;
  - under a container CPU limit (cgroup `cpu.max`) every runnable thread runs until the quota is
    spent, so the quota is also split equally per runnable thread;
  - pools are oversubscribed (Σ threads > cores) and idle pools park, so free CPU is used by busy
    tenants (work-conserving).
- **Pool size is the per-tenant cap.** A pool of N threads can never use more than N cores.
- **Differentiation between tenants stays with the existing per-PT `flow-control.write` limits.**
  No new weight knob in this spike.
- **Rejected for now** (documented so they are not re-proposed without new data):
  - A broker-level controller deriving per-tenant write-rate limits from CPU time: too complex,
    and write-rate limits act only after the admitted backlog drains.
  - A user-space CPU budget per pool (cgroup-like quota with debt): only needed for fractional
    caps or unequal CPU weights under a container CPU limit. Revisit if the spike's measurements
    show equal per-thread sharing is not enough.
  - Weights via per-thread `nice`: ineffective inside a container with a CPU limit unless the
    host itself is saturated (verified experimentally: nice only matters with run-queue
    contention).
  - Nested cgroups per tenant: require a writable cgroupfs, not available in normal Kubernetes.

## Scope

### In scope

1. A dedicated `ActorScheduler` (CPU group + IO group) per PT on each broker, used for all actors
   submitted through that PT's partition `ActorSchedulingService`.
2. Configurable pool size, globally and per PT.
3. A feature flag; when disabled, behaviour is identical to today.
4. Lifecycle: the pool is created and started with the PT's partition manager and closed after it.
5. Observability sufficient to attribute CPU per PT.
6. An integration test demonstrating isolation.
7. A benchmark comparison (shared scheduler vs per-PT pools) — at least a first run.

### Out of scope

- Any new rate-limiting or weighting algorithm.
- Moving Raft or the journal: Raft already runs on its own per-partition single-thread contexts in
  Atomix (`atomix/cluster/.../raft/impl/DefaultRaftSingleThreadContextFactory.java`), not on the
  actor scheduler.
- Gateway-side isolation, RocksDB/disk/heap isolation.
- Production hardening (docs, Helm defaults, migration).

## Code pointers

- **Wiring point.** `zeebe/broker/src/main/java/io/camunda/zeebe/broker/partitioning/PartitionManager.java`,
  `createPartitionManager(...)`: passes `brokerStartupContext.getActorSchedulingService()` (the
  broker-wide scheduler) into each PT's `PartitionManagerImpl`. Replacing this argument with a
  per-PT scheduler routes the PT's partition actors to it: `ZeebePartition`,
  `StreamProcessor` (+ `AsyncScheduleServiceContext` actors), `ExporterDirector` (submitted
  `ioBound`), snapshot director/store, backup, inter-partition command sender/receiver, per-partition
  admin/backup/snapshot API handlers. Verify the list while implementing; anything that must stay
  broker-wide should keep using the broker scheduler explicitly.
- **Scheduler construction.** `dist/src/main/java/io/camunda/application/commons/actor/ActorSchedulerConfiguration.java`
  builds the broker scheduler (`setCpuBoundActorThreadCount`, `setIoBoundActorThreadCount`,
  `setSchedulerName`, `setIdleStrategySupplier`, `setActorClock`, `setMeterRegistry`). Per-PT
  schedulers must reuse the **same actor clock** (tests use a controlled clock) and idle strategy.
- **Cross-scheduler interaction already works.** `ActorThreadGroup#submit`
  (`zeebe/scheduler/.../ActorThreadGroup.java`) schedules a task on the task's own group and only
  uses the caller's runner when the caller is in the same group; CPU and IO groups already
  interact this way. Broker-wide actors (health, disk monitor, transport) calling into partition
  actors on another scheduler should therefore work. Timer subscriptions bind to the current
  actor thread's timer queue; check they behave correctly when an actor's group differs from the
  caller's.
- **Thread defaults.** `ActorScheduler.ActorSchedulerBuilder` (CPU threads default
  `availableProcessors - 2`, IO 2); broker config `camunda.system.cpu-thread-count` /
  `io-thread-count` (`configuration/.../System.java`, default 2/2).
- **Per-PT config overrides.** `camunda.physical-tenants.<id>.*` merges into the PT config;
  `configuration/.../physicaltenants/PhysicalTenantOverridePolicyValidation.java` deny-lists
  process-wide keys (`system.cpu-thread-count`, `system.io-thread-count` are deny-listed). The new
  pool keys must be allowed per PT.
- **Existing PT isolation tests.**
  `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/physicaltenant/PhysicalTenantBackpressureIsolationIT.java`,
  `PhysicalTenantFlowControlActuatorIT.java`.

## Requirements

### Configuration

- **C1** Feature flag, default off. Proposal: `camunda.system.physical-tenant-actor-pool.enabled`.
- **C2** Pool size keys, global defaults with per-PT override. Proposal:
  `camunda.system.physical-tenant-actor-pool.cpu-thread-count` (default 2) and
  `...io-thread-count` (default 1), overridable as
  `camunda.physical-tenants.<id>.system.physical-tenant-actor-pool.*`. Key names are a proposal;
  pick names consistent with the unified configuration conventions and keep legacy `zeebe.broker.*`
  mapping out of the spike unless trivial.
- **C3** When the flag is off, no extra schedulers or threads are created.
- **C4** When on, every PT (including the default one) gets its own pool. The broker-wide
  scheduler keeps its configured size for broker-wide actors.

### Behaviour

- **B1** All actors submitted through a PT's partition `ActorSchedulingService` run on that PT's
  pool; CPU-bound and IO-bound hints map to the pool's CPU and IO groups.
- **B2** Broker-wide actors stay on the broker scheduler.
- **B3** The PT pool is started before the PT's partitions and closed after they are stopped,
  including on broker shutdown and on PT removal (keep compatible with the no-restart PT
  lifecycle work, product-hub#3779).
- **B4** No change to flow control, request limits, write-rate limits, or client-visible
  behaviour.

### Observability

- **O1** Thread names include the PT id (e.g. `<scheduler>-<nodeId>-<tenantId>-zb-actors-<n>`),
  so async-profiler flamegraphs, JFR, and thread dumps attribute CPU per tenant.
- **O2** Actor scheduler metrics (when enabled) are distinguishable per PT pool (tag or scheduler
  name).
- **O3** Nice to have: a per-PT gauge of pool CPU time (sum of `ThreadMXBean#getThreadCpuTime`
  over the pool's threads), sampled at metrics-scrape time.

### Tests

- **T1** Unit/wiring test: with the flag on, a PT's partition actors run on threads of that PT's
  pool; with the flag off, on the broker scheduler.
- **T2** Integration test (two PTs, flag on): tenant A runs a workload that keeps its actor
  threads busy (e.g. a long FEEL evaluation or a tight loop of heavy commands); tenant B's
  process instances keep completing within a bound, measured with Awaitility, no `Thread.sleep`.
  The same test with the flag off is expected to show the interference (document the observed
  difference; do not assert on timing in a way that makes the test flaky).
- **T3** Existing physical-tenant ITs pass with the flag on and off.

### Measurement (spike deliverable)

Run with the multi-tenant benchmark harness (note camunda/camunda#64360: with several tenants the
realistic scenario only started workers for the last tenant; make sure the run used has the fix).

- **Arms:** (A) shared scheduler (flag off); (B) per-PT pools (flag on), same total resources.
- **Scenarios:**
  1. One saturating tenant + steady neighbours, host CPU saturated.
  2. Same, with a pod CPU limit that is reached (cgroup throttling, check `cpu.stat`
     `nr_throttled`).
  3. Noisy tenant with heavy FEEL / large payloads.
  4. Many idle tenants (e.g. 20–50) + one busy tenant: idle overhead of parked pools, context
     switches, busy tenant's throughput vs. arm A.
- **Collect:** neighbour p50/p99 process-instance and command latency, throughput per tenant,
  backpressure rejections per tenant, CPU per tenant pool (O1/O3), context switches, Raft health
  (heartbeat delays, leader changes).
- **Decision questions the results must answer:**
  - Does arm B bound neighbour latency degradation compared to arm A?
  - Is equal sharing per thread acceptable, or are unequal CPU weights needed (which would bring
    back the user-space CPU budget)?
  - What is the overhead at high tenant counts?
  - Are there significant per-partition actors that should not move (or should) to the pool?

## Deliverables

1. Branch with the flagged implementation, tests T1–T3 green.
2. A short results write-up (numbers per scenario and arm, the decision-question answers) to feed
   back into the Define phase.

## Constraints for the implementation session

- Follow `AGENTS.md`: module-scoped builds (`zeebe/scheduler`, `zeebe/broker`, `configuration`,
  `dist`), `./mvnw license:format spotless:apply -T1C` before each commit, Conventional Commits.
- Do not change public APIs (REST/gRPC/exported types). New config keys are additive and behind a
  flag.
- Keep the change minimal: no new algorithm, no changes to `FlowControl`.

