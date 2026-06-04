# Design: making the broker restartable for CRaC checkpoint/restore

Status: **Proposal / RFC** (foundation for the CRaC fast-start initiative)

## Context

CRaC (Coordinated Restore at Checkpoint) restores a pre-warmed JVM in well under a second,
which would cut repeated cold-start cost (notably in CI, where the distribution is started
hundreds of times a day). An end-to-end spike on GKE proved the mechanism works for the
stateless tier and pinpointed the blocker for the unified `StandaloneCamunda`.

### What the spike established (evidence)

With `org.crac` on the classpath, a CRaC JDK, `spring.context.checkpoint=onRefresh`, and
handlers that close the Elasticsearch transports and stop the `AtomixCluster` transport, the
checkpoint of the unified app got all the way to the CRIU dump and aborted **only** on the
embedded broker's still-open resources:

- ~41× each `timerfd` / `eventpoll` / `eventfd` — the **`ActorScheduler`** threads and remaining
  netty/NIO event loops;
- sockets `:26500` (embedded gateway gRPC server) and `:26501` (broker command-API);
- partition **RocksDB** files (`MANIFEST-*`, `*.sst`, `LOG`, `LOCK`, `*.log`).

The ES + cluster-transport handlers worked; the broker is the wall.

## Why a handler isn't enough

A CRaC checkpoint requires every open fd to be released in `beforeCheckpoint` and re-acquired in
`afterRestore`. For the broker that means stopping it before checkpoint and starting it after. But
the broker stack beans are **single-use** — `close()` is terminal:

| Component | Wiring | Restart on same instance |
|-----------|--------|--------------------------|
| `ActorScheduler` (`dist/.../actor/ActorSchedulerConfiguration.java`) | `@Bean(destroyMethod="close")`, `start()` in the `@Bean` | **No** — terminal `TERMINATED` state ("scheduler is not reusable") |
| `Broker` (`dist/.../zeebe/broker/BrokerModuleConfiguration.java`) | `@Bean(destroyMethod="close")`, `start()` in the `@Bean` | **No** — `isClosed` set on close; `BrokerStartupProcess` steps are one-shot |
| `AtomixCluster` (`dist/.../clustering/AtomixClusterConfiguration.java`) | `@Bean(destroyMethod="stop")` | **No** — observed `IllegalStateException: Cluster instance is shutdown` on restart |
| Embedded gateway | created inside `BrokerStartupProcess` (`EmbeddedGatewayServiceStep`), not a Spring bean | **No** — designed single-use |

None implement `SmartLifecycle`, so Spring's CRaC integration (which stops/starts SmartLifecycle
beans around a checkpoint) doesn't manage them.

## The two viable designs

### Option A — Defer all I/O startup until after the checkpoint (preferred long-term)

Capture the checkpoint as a *warmed JVM with no broker started*, and start the broker exactly once
on restore. Mechanically: nothing that opens an fd runs during context refresh; broker/scheduler/
cluster/gateway and the ES schema init start in a post-refresh phase (an `ApplicationRunner` or a
late `SmartLifecycle` phase that runs after the `onRefresh` checkpoint point).

- **Pro:** no restart needed — every component is started once, in its existing one-shot way; the
  checkpoint is naturally clean.
- **Con:** today many refresh-time beans (`BrokerClient`, the embedded gateway, partition-dependent
  webapp beans) are wired assuming the broker is already started during refresh. This requires
  decoupling bean *construction* (refresh) from runtime *start* (post-refresh) across the broker
  and its consumers. Significant, but architecturally clean and also improves graceful shutdown.

### Option B — Make the broker stack restartable (stop → start same instances)

Rework the terminal-state lifecycles so `stop()`/`close()` is followed by a working `start()`. This
is a multi-increment Zeebe-core initiative; increments 1–2 have **no end-to-end CRaC payoff** until
increment 3 also lands.

**Increment 1 — `ActorScheduler` restartable** (`zeebe/scheduler`). Today `ActorScheduler` holds a
`final ActorExecutor` (`ActorScheduler.java:23,28`) whose two `ActorThreadGroup`s own real Java
threads; `stop()` drives `NEW→RUNNING→TERMINATING→TERMINATED` and `ActorExecutor.closeAsync()`
terminates the threads (`ActorExecutor.java:51-58`). Java threads are single-use, so `start()` after
`stop()` must **rebuild** the executor + thread groups from the retained builder config (thread
counts, factory, clock, timer queue), not reuse them — and the state machine must allow
`TERMINATED→(rebuild)→RUNNING`. Unit-testable (`submitActor` works after a stop→start cycle), but a
green unit test does not prove the absence of thread leaks/races under load.

**Increment 2 — `AtomixCluster` restartable** (`zeebe/atomix/cluster`). Real stop→start:
`NettyMessagingService` must rebuild its `EpollEventLoopGroup`/`NioEventLoopGroup` and rebind, and
membership (SWIM) must rejoin. Today `stop()` is effectively terminal (`Cluster instance is shutdown`).

**Increment 3 — `Broker` / `BrokerStartupProcess` replayable** (`zeebe/broker`). Reset `isClosed`
(`Broker.java`) and make every startup step (`BrokerStartupProcess`: ClusterServices, CommandApi,
GatewayBrokerTransport, EmbeddedGateway, JobStream, RocksDbResources, PartitionManagers, …)
idempotently teardown-and-reinit. Must handle RocksDB reopen and Raft term/leadership/clock-jump on
restart, even for a single-broker topology. **Highest risk.**

- Expose increments 1–3 as `SmartLifecycle` beans so Spring's CRaC support drives stop/start.
- **Con:** touches the most correctness-sensitive code in the product (concurrency core + Raft +
  RocksDB). Needs Zeebe-core ownership and extensive testing; not a side change.

### Recommendation

**Pursue Option A**, with a hybrid where the broker stack is created lazily and started by a
post-checkpoint runner. It avoids reopening the terminal-state machinery in Option B, which is the
riskiest part of the codebase, and yields the same outcome: a clean checkpoint of the warmed JVM.
Treat "restartable broker" (Option B) only if a specific component must survive a checkpoint live.

### Realistic benefit either way

CRaC saves the JVM + class-loading + Spring-context + JIT warm-up — identical on every start. It
does **not** save broker startup (RocksDB open, partition recovery, Raft), which runs after restore.
For CI with fresh/empty state, broker startup is comparatively cheap, so the net win is real but
bounded — not "broker boots in milliseconds".

## Incremental delivery plan

1. Land the CRaC enabling foundation (this PR): `org.crac` on the classpath, CRaC JDK base seam,
   the opt-in registration config — already done.
2. Webapp tier first (independent, fully achievable): a `Resource` for the `CamundaClient` gRPC
   channel → functional checkpoint/restore of `StandaloneOperate`/`Tasklist`.
3. Orchestration via Option A: introduce a post-refresh start phase; move broker/scheduler/cluster/
   gateway/ES-schema start into it behind `camunda.crac.enabled`; verify a clean checkpoint, then
   a functional restore.
4. (Optional) Option B for any component that must stay live across a checkpoint.

## Verification

Reuse the spike harness: build a CRaC-JDK image, run `StandaloneCamunda` checkpoint-on-refresh
against a throwaway ES (must be ES ≥ 8.19 — the 8.19 java client requires
`HealthResponse.unassignedPrimaryShards`), and confirm the checkpoint completes with no remaining
open fds, then restore and assert the gateway/REST endpoints become ready and a process can be
deployed and completed.

## Spike results (experimental, GKE) and recommendation

An end-to-end spike implemented the restart machinery and tested checkpoint-on-refresh of the unified
`StandaloneCamunda` on GKE (Azul Zulu CRaC JDK 25 image, throwaway Elasticsearch — note: ES **8.19+**,
since the 8.19 ES Java client requires `HealthResponse.unassignedPrimaryShards`). Findings:

1. **Restart machinery works.** `ActorScheduler` and `AtomixCluster` restart **in place** (rebuild
   their executors / netty event loops + SWIM on `start()`), so consumers that cached
   `getMessagingService()` etc. stay valid (unit-tested: `ActorSchedulerTest.shouldRestartAfterStop`,
   `AtomixClusterTest.shouldRestartAfterStop`). `Broker` / `BrokerClient` recreate their actors on
   `start()` after `close()`. In-JVM broker recreation is independently proven by the existing
   `EmbeddedBrokerRule.restartBroker` / `BrokerRestartTest`.

2. **Drive via `org.crac.Resource`, not `SmartLifecycle`.** Spring Boot's checkpoint-on-refresh only
   invokes registered `org.crac.Resource` beans; it does **not** stop arbitrary `SmartLifecycle`
   beans. The four `SmartLifecycle` bridges never fired; consolidating into a single ordered
   `org.crac.Resource` (`CracBrokerStackResource`) did fire and tore the **whole broker stack** down
   cleanly (broker, RocksDB, gateway, command API, cluster netty, SWIM, scheduler).

3. **The unified checkpoint still aborts — on the webapp tier, not the broker.** After the broker
   stack is fully stopped, ~**41 event-loop fds remain genuinely open** (measured via `/proc/self/fd`;
   they do not drain). A thread dump attributes them to the **webapp/web tier**: the JDK
   `java.net.http.HttpClient` selector managers (ES clients / OIDC / web-modeler), the servlet
   container (Tomcat NIO), and gRPC — not components owned by the broker-stack teardown.

4. **Failure-path caveat:** stopping the scheduler before a *failed* checkpoint makes Spring's context
   teardown hang (`Actor.close()` joins on the dead scheduler), masking the `CheckpointException`.

### Conclusion

Option B (make the broker stack restartable) is **viable and the hard depth-risk — broker/Raft/RocksDB
in-process restart — is solved**. But achieving a *clean* unified checkpoint via B is a **breadth**
problem: every event loop in the process (broker tier ✓ + webapp tier ES/HttpClient/gRPC/servlet)
must be closed synchronously before the dump. That is many components, several behind async shutdown.

**Recommendation: Option A.** Because A captures the checkpoint *before* I/O starts, there are no
event loops to close — it sidesteps the entire breadth problem the spike exposed.

### Option A — concrete design (recommended)

Goal: at the checkpoint point, no I/O has started (0 event loops) → clean checkpoint; on restore,
start everything once.

1. **Move all I/O start out of bean instantiation into a post-refresh phase.** Today the eager
   `@Bean` methods start I/O during context refresh (`ActorScheduler.start()`,
   `AtomixCluster.start()`, `Broker.start()`, `BrokerClient.start()`, ES schema init). The
   checkpoint-on-refresh fires inside `finishRefresh`, after instantiation, so these are already
   running. Start them instead from an `ApplicationRunner`/`CommandLineRunner`, which runs in
   `callRunners()` — *after* `refreshContext()` and therefore *after* the on-refresh checkpoint.

2. **Break the construction-time coupling.** `Broker`'s constructor submits the startup actor to the
   scheduler, so it cannot be constructed before the scheduler is started. True deferral therefore
   needs the broker subgraph created lazily (e.g. `@Lazy` + `ObjectProvider`/holder), with the
   post-refresh runner: start scheduler → start cluster → start broker client → create+start broker
   → run ES schema init. Refresh-time consumers (gateway, partition-dependent webapp beans) must go
   through the holder rather than caching a started instance.

3. **Restore.** On restore the JVM resumes after the checkpoint point and `callRunners()` starts the
   stack fresh against the existing data dir (RocksDB reopen + Raft recovery — the normal restart
   path). No per-component `org.crac.Resource` close handlers are required.

The restartability work from this spike (in-place scheduler/cluster restart, broker/broker-client
actor recreation) is reusable but **not required** for Option A; A's value is precisely that it
avoids needing to stop/restart anything across the checkpoint.
