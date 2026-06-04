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

Rework the terminal-state lifecycles so `stop()`/`close()` is followed by a working `start()`:

- `ActorScheduler`: remove the irreversible `TERMINATED` end-state (or provide a reset) so threads
  can be torn down and re-created. **Highest risk** — this is core concurrency machinery.
- `Broker` / `BrokerStartupProcess`: reset `isClosed` and make the startup steps replayable
  (idempotent teardown + re-init for ClusterServices, CommandApi, EmbeddedGateway, RocksDb
  resources, PartitionManagers).
- `AtomixCluster`: support a real stop→start cycle (rebind netty, rejoin membership).
- Expose these as `SmartLifecycle` so Spring's CRaC support drives them automatically.
- **Con:** touches the most correctness-sensitive code; restart of a Raft member must handle term,
  leadership, and clock-jump correctly even for a single-broker topology.

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
