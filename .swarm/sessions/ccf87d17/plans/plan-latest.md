# Plan

**Timestamp:** 2026-03-19T07:14:33.309Z

## Original Request

I want to implement an "Event Bridge" that receives a batch of events and stores them in an append-only log, similar to how Kafka does. To implement, existing modules should be used: Log Stream, RAFT, SWIM and everything that requires to work. To break it down:

Event Bridge:

* It consists of two parts: gateway and broker
* The gateway exposes an api to publish events but also to subscribe to events (in a pull-based approach like in Kafka)
* Authentication methods are not required in that iteration
* a broker manages a set of partitions, if a broker is leader for a partition it stores the respective published events
* therefore, the gateway routes the published events to the respective broker that is leader for the respective partition

Technically, it should be built as a standalone component based Spring Boot, reusing RAFT (as a consensus protocol to elect a leader and to replicate events), Atomix, Log Stream, SWIM (to gossip who is part of the cluster, who is leader), the actor scheduler for threading, and something similar to the existing Sequencer to sequence incoming events when writing them to the log stream. To built a distribution follow the same pattern as in the /dist module.

For the subscription part, the broker knows which consumer (group) is at which position. The Log Stream can be truncated up to the consumed positions. partition 1 knows which consumer is on which position. Broker-0 is the coordinator of a consumer group.

Event Bridge Client:

* This allows to publish a batch of events, each event is a binary (not a JSON).
* As a routing parameter, it expects a partition id (keep it simple here, no hashing required yet)
* To subscribe to events, the consumer group concept is used. Each consumer of that groups sends heartbeats to the broker

## Research Context

### Study Log Stream module

Exploring the Log Stream module — background agent launched. I'll report back when it completes. Feel free to ask follow-up questions in the meantime.

---

### Study RAFT consensus implementation

Here's the complete picture of RAFT in Camunda, synthesized from all 4 exploration agents:

---

## RAFT in Camunda: Complete Reference

### 1. Implementation Source

**Custom hard-fork of Apache Atomix** — not an external dependency. Located at `zeebe/atomix/`, it was forked after Atomix migrated to Go. ~50% of the original codebase was removed to keep only RAFT, SWIM, and transport. Two Maven modules:

```
zeebe/atomix/
├── cluster/   → io.camunda:zeebe-atomix-cluster  (138 Java files, the RAFT impl)
└── utils/     → io.camunda:zeebe-atomix-utils
```

All RAFT code lives under `io.atomix.raft.*`.

---

### 2. Module & Package Structure

```
io.atomix.raft
├── RaftServer.java                  # Main interface (647 lines)
├── ElectionTimer.java               # Interface
├── RaftRoleChangeListener.java      # Role change callback
├── RaftCommitListener.java          # Commit index callback
├── impl/
│   ├── RaftContext.java             # Central state (1,371 lines) — term, commitIndex, role
│   ├── DefaultRaftServer.java
│   ├── PriorityElectionTimer.java   # Priority-weighted election timeouts
│   ├── RandomizedElectionTimer.java
│   ├── ReconfigurationHelper.java   # Dynamic membership changes
│   └── LogCompactor.java
├── roles/
│   ├── RaftRole.java (interface)
│   ├── AbstractRole  → InactiveRole → PassiveRole → PromotableRole → ActiveRole
│   ├── FollowerRole.java            # Manages ElectionTimer
│   ├── CandidateRole.java           # Runs elections
│   └── LeaderRole.java              # Implements ZeebeLogAppender, drives replication
├── partition/
│   ├── RaftPartition.java           # Partition abstraction (wraps RaftServer)
│   ├── RaftPartitionConfig.java
│   ├── RaftElectionConfig.java
│   ├── RaftStorageConfig.java
│   └── impl/RaftPartitionServer.java
├── protocol/                        # 36 message types (AppendRequest/Response, VoteRequest/Response, etc.)
├── storage/
│   ├── RaftStorage.java
│   ├── log/
│   │   ├── RaftLog.java
│   │   ├── RaftLogReader.java
│   │   ├── RaftLogCommittedReader.java
│   │   ├── RaftLogUncommittedReader.java
│   │   ├── RaftLogFlusher.java
│   │   └── entry/RaftLogEntry.java
│   └── system/
│       ├── MetaStore.java           # Persists term + votedFor
│       └── Configuration.java      # Persists cluster membership
├── snapshot/                        # Snapshot transfer
├── metrics/                         # 20+ metric classes
└── zeebe/
    ├── ZeebeLogAppender.java        # Functional interface for appending
    └── EntryValidator.java
```

The underlying durable log is in a separate module `zeebe/journal/`, using `SegmentedJournal` / `SegmentedJournalWriter`.

---

### 3. Leader Election

**Flow:** Follower timer expires → Candidate → sends `VoteRequest` to all members → quorum → Leader → notifies listeners.

#### Key files:
| File | Role |
|------|------|
| `roles/FollowerRole.java` | Owns `ElectionTimer`, resets it on each heartbeat |
| `roles/CandidateRole.java` | `startElection()` / `sendVoteRequests()` |
| `roles/ActiveRole.java` | `handleVote()` — validates and grants/rejects votes |
| `impl/RaftContext.java` | `transition(Role)`, `setTerm()`, `setLeader()` |
| `impl/PriorityElectionTimer.java` | Optional priority-weighted timeouts |
| `partition/RaftElectionConfig.java` | `ofDefaultElection()` / `ofPriorityElection(targetPriority, nodePriority)` |
| `protocol/VoteRequest.java` | `{ term, candidate, lastLogIndex, lastLogTerm }` |

#### Vote grant rules (`ActiveRole.handleVote`):
1. Reject if `request.term < currentTerm`
2. Reject if a leader already exists for this term
3. Reject if candidate is unknown
4. Grant if not yet voted and candidate log is at least as up-to-date

#### Role transition (`RaftContext.transition`):
```java
// Stops old role, installs new role, notifies listeners
// For LEADER: waits for initial entries to commit before notifying
this.role.stop()  →  newRole.start()  →  notifyRoleChangeListeners()
```

#### Term management (`RaftContext.setTerm`):
```java
this.term = term;
leader = null;
lastVotedFor = null;
meta.storeTerm(term);    // Durable
meta.storeVote(null);    // Durable
```

---

### 4. Log Replication

**Leader-side:** `LeaderRole` implements `ZeebeLogAppender`. It delegates replication to `LeaderAppender`.

**Follower-side:** `PassiveRole.onAppend()` / `appendEntries()` handles incoming `AppendRequest`.

#### Key files:
| File | Role |
|------|------|
| `roles/LeaderAppender.java` | Builds & sends `AppendRequest`, handles responses, tracks matchIndex/nextIndex |
| `roles/PassiveRole.java` | Receives `AppendRequest`, appends to `RaftLog`, updates commitIndex |
| `protocol/AppendRequest.java` | `{ term, leader, prevLogIndex, prevLogTerm, entries[], commitIndex }` |
| `protocol/AppendResponse.java` | `{ status, succeeded, term, lastLogIndex, lastSnapshotIndex }` |
| `storage/log/RaftLog.java` | Main log abstraction (read/write/commit) |
| `zeebe/journal/SegmentedJournal.java` | Underlying durable segment storage |

#### Per-follower state (`RaftMemberContext`):
- `matchIndex` — highest index confirmed replicated
- Reader position (tracks `nextIndex` implicitly)

#### Commit index update (`RaftContext.setCommitIndex`):
```java
commitIndex = Math.min(requestedIndex, raftLog.getLastIndex());
raftLog.setCommitIndex(commitIndex);
replicationMetrics.setCommitIndex(commitIndex);
// → notifies RaftCommitListener
```

#### Snapshot replication:
- Leader: `LeaderAppender.buildInstallRequest()` streams `PersistedSnapshot` chunks via `InstallRequest`
- Follower: `PassiveRole.onInstall()` receives chunks → `pendingSnapshot.persist()` → resets log
- Key files: `zeebe/snapshot/PersistedSnapshot.java`, `ReceivedSnapshot.java`, `SnapshotChunkReader.java`

---

### 5. Consensus API

#### Appending entries (application → RAFT):
```java
// ZeebeLogAppender.java  (functional interface implemented by LeaderRole)
void appendEntry(ApplicationEntry entry, AppendListener appendListener);

// AppendListener callbacks:
void onWrite(IndexedRaftLogEntry indexed);       // Written locally
void onCommit(long index, long highestPosition); // Replicated + committed
void onWriteError(Throwable error);
void onCommitError(long index, Throwable error);
```

Access the appender only when this node is leader:
```java
Optional<ZeebeLogAppender> appender = raftPartitionServer.getAppender();
// empty() if not leader
```

#### Listening to commits:
```java
// RaftCommitListener.java
void onCommit(long index);  // May skip indices (batch notification)
```

#### Listening to role changes:
```java
// RaftRoleChangeListener.java
void onNewRole(RaftServer.Role newRole, long newTerm);
```

#### Partition-level API:
```java
// RaftPartitionServer.java
CompletableFuture<> bootstrap()           // Initial cluster bootstrap
CompletableFuture<> join()                // Join existing cluster
CompletableFuture<> leave()
CompletableFuture<> forceReconfigure(Map<MemberId, Type>)
void addCommitListener(RaftCommitListener)
void addRoleChangeListener(RaftRoleChangeListener)
RaftLogReader openReader()
```

---

### 6. Zeebe Broker Integration

```
BrokerStartup
  └── PartitionManagerStep
        └── PartitionManagerImpl
              ├── RaftPartitionFactory → RaftPartition → RaftPartitionServer → RaftServer
              └── ZeebePartitionFactory → ZeebePartition
                    implements RaftRoleChangeListener
                    onNewRole(LEADER)   → leaderTransition()   → start StreamProcessor, exporters
                    onNewRole(FOLLOWER) → followerTransition() → start log replay
                    onNewRole(INACTIVE) → transitionToInactive()

Data path:
  Client command
    → ZeebeLogAppender.appendEntry()       (LeaderRole)
    → LeaderAppender replicates to followers
    → RaftCommitListener.onCommit()
    → AtomixLogStorage notifies LogStream   (zeebe/broker/AtomixLogStorage.java)
    → StreamProcessor reads via LogStreamReader
    → ProcessingStateMachine applies to ZeebeDb
```

Key broker files:
| File | Purpose |
|------|---------|
| `broker/partitioning/PartitionManagerImpl.java` | Orchestrates all partitions |
| `broker/partitioning/startup/RaftPartitionFactory.java` | Creates `RaftPartition` with config |
| `broker/partitioning/startup/steps/RaftBootstrapStep.java` | Bootstrap on first start |
| `broker/partitioning/startup/steps/RaftJoinStep.java` | Join on restart |
| `broker/system/partitions/ZeebePartition.java` | RAFT→engine bridge |
| `broker/logstreams/AtomixLogStorage.java` | Implements both `LogStorage` and `RaftCommitListener` |
| `broker/PartitionRaftListener.java` | `onBecameRaftLeader/Follower` callbacks |
| `broker/raft/ZeebeEntryValidator.java` | Custom entry validation |

Configuration (`ClusterCfg`): `replicationFactor` (default 3), `electionTimeout`, `heartbeatInterval`, `maxAppendBatchSize`, `maxAppendsPerFollower`.

---

### 7. Existing Tests

| Test | Location |
|------|----------|
| Core consensus | `zeebe/atomix/cluster/src/test/java/io/atomix/raft/RaftTest.java` |
| Entry append/commit | `…/raft/RaftAppendTest.java` |
| Replication | `…/raft/RaftReplicationTest.java` |
| Randomized fault injection | `…/raft/RandomizedRaftTest.java` |
| Zeebe log appender | `…/raft/zeebe/ZeebeLogAppenderTest.java` |
| Partition factory config | `broker/…/partitioning/RaftPartitionFactoryTest.java` |
| Role transitions | `broker/…/partitioning/RaftRolesTest.java` |
| ZeebePartition lifecycle | `broker/…/partitions/ZeebePartitionTest.java` |

---

### Study SWIM gossip protocol usage

Here's a comprehensive summary of how SWIM is integrated in Camunda:

---

## SWIM in Camunda 8

### 1. Module providing SWIM
**`zeebe/atomix/cluster`** (`io.atomix.cluster.protocol`) — a fork of Atomix embedded in the repo.

Key classes:
- `SwimMembershipProtocol` — core protocol implementation
- `DefaultClusterMembershipService` — wraps the protocol into a service
- `AtomixCluster` — cluster orchestrator; started in `ClusterServicesImpl`

---

### 2. Membership maintenance & propagation

SWIM uses three mechanisms running on an actor thread:
| Mechanism | Default interval | Purpose |
|---|---|---|
| **Probe** (heartbeat) | 1000ms | Detect failures via direct + indirect probes |
| **Gossip** | 250ms, fanout=2 | Push membership updates to random peers |
| **Sync** | 10000ms | Full state reconciliation |

Member state machine: `ALIVE → SUSPECT → DEAD`. State is stored in-memory in a `ConcurrentHashMap<MemberId, SwimMember>`. Membership changes fire `GroupMembershipEvent` (MEMBER_ADDED / MEMBER_REMOVED / METADATA_CHANGED / REACHABILITY_CHANGED).

Broker-specific metadata (topology, partition roles) rides on top of SWIM via **`Member.properties()`**: `BrokerInfo` is base64-serialised (SBE) into a single `"brokerInfo"` property key. When the property changes, SWIM gossips a `METADATA_CHANGED` event to the whole cluster.

---

### 3. Querying current cluster members

```java
// Get the service (broker side)
ClusterMembershipService membership = clusterServicesImpl.getMembershipService();

// All members
Set<Member> members = membership.getMembers();

// Specific member
Member m = membership.getMember(MemberId.from("1"));

// Local node
Member local = membership.getLocalMember();
```

For **broker topology** (higher-level):
```java
// zeebe/broker-client — used by gateway
BrokerClusterState topo = brokerTopologyManager.getTopology();
List<Integer> brokers = topo.getBrokers();
String addr = topo.getBrokerAddress(nodeId);
```

---

### 4. Leader/follower dissemination

1. **Raft** elects a leader per partition (inside `zeebe/atomix/cluster/…/raft/`)
2. `TopologyManagerImpl` (broker) implements `PartitionListener`; when called with `setLeader(term, partitionId)`, it calls `BrokerInfo.setLeaderForPartition(partitionId, term)` then `publishTopologyChanges()` — writing the updated `BrokerInfo` to `Member.properties()`
3. SWIM gossips the `METADATA_CHANGED` event cluster-wide
4. On the **gateway side**, `BrokerTopologyManagerImpl` implements `ClusterMembershipEventListener`, extracts `BrokerInfo.fromProperties(member.properties())` and updates its in-memory `BrokerClientTopologyImpl`
5. Consumers call `getTopology().getLeaderForPartition(partitionId)` → returns the node ID

Full chain:
```
Raft election
  → TopologyManagerImpl.setLeader()
  → BrokerInfo written to Member.properties()
  → SWIM gossips METADATA_CHANGED
  → BrokerTopologyManagerImpl.event() parses BrokerInfo
  → BrokerClusterState updated
  → getLeaderForPartition(id) returns leader
```

---

### 5. Concrete usage examples

| Location | Class | Role |
|---|---|---|
| `zeebe/broker` | `TopologyManagerImpl` | Registers as `ClusterMembershipEventListener`; publishes local leadership via `Member.properties()` |
| `zeebe/broker-client` | `BrokerTopologyManagerImpl` | Gateway-side listener; maintains `BrokerClusterState` from SWIM events |
| `zeebe/gateway` | `HashBasedDispatchStrategy` | Reads topology to route gRPC requests to partition leaders |
| `zeebe/gateway` | `RequestRetryHandler` | Falls back via `topologyManager.getTopology()` on retry |
| `zeebe/broker` | `ClusterServicesImpl` | Wraps `AtomixCluster`; exposes `getMembershipService()` |
| `zeebe/broker` | `ClusterServicesStep` | Bootstrap step that calls `atomixCluster.start()` |

---

### Study existing Sequencer pattern

Running a deep exploration in the background — I'll read the results once complete.

---

### Study /dist distribution pattern

I have enough information to provide a comprehensive summary now.

---

## Distribution Architecture: How It Works

### 1. Directory Structure (`dist/`)

The `dist/` module is **the single Maven module** that produces all distributions. It is `artifactId: camunda-zeebe`, `packaging: jar`.

```
dist/
├── pom.xml                          # The main distribution POM
├── src/main/
│   ├── assembly.xml                 # Maven Assembly descriptor (zip + tar.gz)
│   ├── config/
│   │   ├── defaults.yaml            # AUTO-GENERATED Spring config defaults (env var docs)
│   │   └── log4j2.xml               # Shared logging config (all components)
│   └── java/io/camunda/application/
│       ├── Profile.java             # Central enum of all Spring profile IDs
│       ├── MainSupport.java         # Shared Spring Boot builder utilities
│       ├── ModesAndProfilesProcessor.java  # Runtime profile selection
│       ├── StandaloneCamunda.java   # All-in-one entry point
│       ├── StandaloneBroker.java    # Broker-only entry point
│       ├── StandaloneGateway.java   # Gateway-only entry point
│       ├── StandaloneOperate.java   # Operate-only entry point
│       ├── StandaloneTasklist.java  # Tasklist-only entry point
│       ├── commons/                 # Shared Spring configurations (search, auth, metrics, etc.)
│       └── initializers/            # ApplicationContextInitializer implementations
```

### 2. How Components Are Bundled

**Two-phase build:**

1. **appassembler-maven-plugin** (`package` phase):
   - Assembles `target/camunda-zeebe/` with `bin/`, `config/`, `lib/`
   - Generates shell scripts for each entry point (broker, gateway, operate, tasklist, camunda, schema, restore, cdbg, etc.)
   - Collects all dependencies into `lib/` (flat layout, wildcard classpath)
   - Copies `config/` to the assembly directory (log4j2.xml, defaults.yaml)
   - Each generated script sets classpath to `config:lib/*:driver-lib/*` and calls the appropriate `mainClass`

2. **maven-assembly-plugin** (`package` phase, after appassembler):
   - Wraps `target/camunda-zeebe/` into `camunda-zeebe-<version>.tar.gz` and `.zip`
   - Includes `licenses/` and `NOTICE.txt` from the repo root
   - Excludes Oracle/MySQL JDBC drivers (licensing)

**All components share one fat distribution** — a single `lib/` with all JARs. Spring profiles determine which components activate at runtime.

### 3. Configuration Patterns

| Concern | Mechanism |
|---|---|
| Logging | `config/log4j2.xml` on classpath, referenced via `app.home` sys-prop; env vars: `CAMUNDA_LOG_LEVEL`, `ZEEBE_LOG_LEVEL`, `*_LOG_APPENDER` |
| App defaults | `config/defaults.yaml` — auto-generated from Spring metadata; acts as reference doc with env var names (e.g. `CAMUNDA_API_GRPC_PORT`) |
| Profile activation | `StandaloneXxx.java` sets `.profiles(...)` in the builder, OR `ModesAndProfilesProcessor` handles `camunda.mode={all-in-one,broker,gateway}` |
| Property defaults | Each `StandaloneXxx.main()` calls `.properties(Map)` on the Spring builder — these are lowest-priority defaults |
| Banner | Each entry point sets `spring.banner.location` to a component-specific banner file |
| Module wiring | Each `StandaloneXxx` lists explicit `@SpringBootConfiguration` sources — `UnifiedConfiguration`, `*ModuleConfiguration`, `*PropertiesOverride` beans |

### 4. Dockerfile Patterns

Four patterns exist:

| Dockerfile | Pattern |
|---|---|
| `Dockerfile` (Zeebe) | Multi-stage: `build` (from source) OR `distball` stage → `dist` stage → `app`. Uses `startup.sh` that dispatches on `ZEEBE_STANDALONE_GATEWAY` and `ZEEBE_RESTORE` env vars. Includes jattach. |
| `camunda.Dockerfile` | Same multi-stage structure, copies full dist → `bin/camunda` entrypoint |
| `operate.Dockerfile` | Simpler: `prepare` stage extracts tarball, patches `bin/operate` to print a notice → `app` copies from prepare. No build-from-source stage. |
| `tasklist.Dockerfile` | Same as operate pattern; renames `bin/tasklist-migrate` → `bin/migrate` for SaaS compatibility |

**Common conventions across all Dockerfiles:**
- Base image: `reg.mini.dev/1212/openjre-base:21-dev` (hardened) with fallback `eclipse-temurin:21-jre-noble` via `--build-arg BASE=public`
- User: `uid=1001, gid=1001, group=camunda`
- Home dir: `/usr/local/<component>/` owned `1001:0`, chmod `0775`
- Volumes: `/tmp`, `<HOME>/logs`, component-specific data dirs
- OCI + OpenShift labels on every image
- `EXPOSE 8080`

### 5. Pattern for a New "Event Bridge" Distribution

To add an `event-bridge` component following this pattern:

**Step 1 — Add a Profile** (`Profile.java`):
```java
EVENT_BRIDGE("event-bridge"),
```

**Step 2 — Create the entry point** (`StandaloneEventBridge.java`):
```java
@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneEventBridge {
  public static void main(String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent("spring.banner.location", "classpath:/event-bridge-banner.txt");

    MainSupport.createDefaultApplicationBuilder()
        .sources(UnifiedConfiguration.class, UnifiedConfigurationHelper.class,
                 /* relevant overrides... */
                 CommonsModuleConfiguration.class,
                 EventBridgeModuleConfiguration.class)
        .profiles(Profile.EVENT_BRIDGE.getId(), Profile.STANDALONE.getId())
        .properties(Map.of("management.health.defaults.enabled", false))
        .initializers(new HealthConfigurationInitializer())
        .build(args).run();
  }
}
```

**Step 3 — Register the binary** in `dist/pom.xml` (appassembler `<programs>`):
```xml
<program>
  <id>event-bridge</id>
  <mainClass>io.camunda.application.StandaloneEventBridge</mainClass>
</program>
```

**Step 4 — Add the module dependency** to `dist/pom.xml`:
```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>event-bridge</artifactId>
</dependency>
```

**Step 5 — Add a Dockerfile** (following operate/tasklist pattern):
```dockerfile
# <name>.Dockerfile
FROM base-${BASE} AS prepare
ARG DISTBALL="dist/target/camunda-zeebe-*.tar.gz"
WORKDIR /tmp/event-bridge
COPY ${DISTBALL} event-bridge.tar.gz
RUN tar xzvf event-bridge.tar.gz --strip 1 && rm event-bridge.tar.gz

FROM base-${BASE} AS app
# ... OCI labels, EXPOSE 8080, user setup, etc.
ENV EVENT_BRIDGE_HOME=/usr/local/event-bridge
COPY --from=prepare --chown=1001:0 --chmod=0775 /tmp/event-bridge ${EVENT_BRIDGE_HOME}
USER 1001:1001
ENTRYPOINT ["/usr/local/event-bridge/bin/event-bridge"]
```

**Key insight**: All distributions share one tarball (`camunda-zeebe-*.tar.gz`). Each Dockerfile just extracts the same tarball and uses a different `bin/<component>` entrypoint — no separate build or artifact per component. The Dockerfile-level isolation is only for image metadata, user setup, and which binary to run.

---

### Study actor scheduler and threading model

Both agents complete. Here's a synthesized overview:

---

## ActorScheduler in Camunda/Zeebe

### 1. Core Architecture (`zeebe/scheduler/`)

A **cooperative, non-blocking actor framework** — not based on JVM threads-per-actor. Key classes:

| Class | Role |
|---|---|
| `ActorScheduler` | Entry point; builder-configured; manages lifecycle |
| `ActorExecutor` | Coordinates two thread pools |
| `ActorThreadGroup` → `CpuThreadGroup` / `IoThreadGroup` | Named thread pools |
| `ActorThread` | Worker thread with run loop + job recycling pool (2048 jobs) |
| `Actor` | Base class for all actors; lifecycle callbacks |
| `ActorControl` | API for submitting work *from within* an actor |
| `ActorTask` | Internal wrapper: holds job queues, lifecycle state, subscriptions |
| `ActorJob` | A single unit of work; recycled to reduce GC |
| `WorkStealingGroup` | Load-balancing via work-stealing across thread queues |
| `ActorTaskQueue` | Lock-free doubly-linked queue with 128-byte cache-line padding |

---

### 2. Thread Pool Management

Two pools, configured at build time:

| Pool | Thread name | Default size | For |
|---|---|---|---|
| CPU-bound | `zb-actors` | `max(1, availableProcessors - 2)` | Non-blocking compute |
| I/O-bound | `zb-fs-workers` | `2` | Blocking I/O (snapshots, disk writes) |

Idle strategy (Agrona `BackoffIdleStrategy`):
```
Spin ×10 → Yield ×5 → Park (1µs → 20ms backoff)
```

Submit with a hint: `scheduler.submitActor(actor, SchedulingHints.ioBound())`

---

### 3. Actor/Job Scheduling

**Run loop per `ActorThread`:**
1. Drain external callbacks
2. Update clock → process expired timers
3. `WorkStealingGroup.getNextTask()` — own queue first, then steal from random peer
4. Execute one job batch from the task (`ActorTask.execute(thread)`)
5. If `resubmit=true`, task re-enqueues itself; otherwise transitions to `WAITING`

**Cooperative yield:** No preemption. Actors suspend themselves via subscriptions:
- `actor.run(r)` — fast-lane (head of queue)
- `actor.submit(r)` — normal (tail of queue)
- `actor.schedule(duration, r)` — one-shot timer
- `actor.runAtFixedRate(duration, r)` — repeating timer
- `actor.runOnCompletion(future, cb)` — future chaining
- `actor.onCondition(name, r)` — condition signal

**Actor lifecycle phases:** `STARTING → STARTED → CLOSE_REQUESTED → CLOSING → CLOSED`  
Callbacks: `onActorStarting()`, `onActorStarted()`, `onActorClosing()`, `onActorClosed()`

---

### 4. Spring Boot Integration

**`ActorSchedulerConfiguration`** in `dist/src/main/java/io/camunda/application/commons/actor/`:

```java
@Bean(destroyMethod = "close")           // Spring calls close() on shutdown
public ActorScheduler scheduler() {
    var scheduler = ActorScheduler.newActorScheduler()
        .setSchedulerName("Broker-" + nodeId)
        .setCpuBoundActorThreadCount(cpuThreads)
        .setIoBoundActorThreadCount(ioThreads)
        .setActorClock(actorClockConfiguration.getClock().orElse(null))
        .setMeterRegistry(metricsEnabled ? registry : null)
        .setIdleStrategySupplier(idleStrategySupplier)
        .build();
    scheduler.start();                   // Started eagerly in bean creation
    return scheduler;
}
```

Thread counts come from broker properties (`properties.getThreads().getCpuThreadCount()`).  
No `SmartLifecycle` — lifecycle is fully managed by `@Bean(destroyMethod)`.  
`ActorClockConfiguration` supplies a *controlled clock* in tests (time travel support).

---

### 5. Key Actor Examples in Zeebe Broker

| Actor | What it does |
|---|---|
| `BrokerStartupActor` | Orchestrates broker startup steps |
| `ZeebePartition` | Core partition: Raft role transitions, health |
| `StreamProcessor` | Processes the event stream |
| `ExporterDirector` | Drives all exporters; implements `HealthMonitorable` |
| `AsyncSnapshotDirector` | Coordinates async snapshots with commit position |
| `CommandApiServiceImpl` | Handles gRPC command API; submits child actors in `onActorStarting()` |
| `CheckpointSchedulingService` | Schedules backups; also a `ClusterMembershipEventListener` |
| `TopologyManagerImpl` | Manages cluster topology |
| `DiskSpaceUsageMonitorActor` | Polls disk; notifies listeners via `runAtFixedRate` |
| `InterPartitionCommandReceiverActor` | Routes commands across partitions |

**Typical submission patterns:**
```java
// At startup (from Broker.java)
scheduler.submitActor(brokerStartupActor);

// Parent submitting children (from CommandApiServiceImpl)
protected void onActorStarting() {
    scheduler.submitActor(queryHandler);
    scheduler.submitActor(commandHandler);
}

// Bootstrap step waiting on future
concurrencyControl.runOnCompletion(
    scheduler.submitActor(service),
    proceed(() -> startupFuture.complete(ctx), startupFuture));
```

---

### Key Design Properties to Know

- **Single-threaded within an actor** — no synchronization needed inside actor callbacks
- **Work-stealing** — automatic load balancing across CPU threads
- **Lock-free queues** — cache-line padded, Unsafe-based
- **Job recycling** — 2048 pre-allocated `ActorJob`s per thread
- **Cooperative scheduling** — actors must not block; use `SchedulingHints.ioBound()` for any blocking work
- **Future composition** — `ActorFuture<T>` is the async primitive; chain with `runOnCompletion`

---

### Study consumer group management patterns

Launched 4 parallel research agents covering all four areas. I'll read their results as they complete.

## Research Context

### Study LogStream API and truncation semantics

Here's a comprehensive breakdown of the LogStream module:

---

## 1. LogStreamReader & LogStreamWriter APIs

### LogStreamReader (`io.camunda.zeebe.logstreams.log.LogStreamReader`)
Extends `Iterator<LoggedEvent>` + `CloseableSilently`. Pure read-only, append-safe.

| Method | Semantics |
|--------|-----------|
| `seek(long position)` | Seek to exact position (or next ≥ it); returns `true` if exact match |
| `seekToNextEvent(long position)` | Seek to the event **after** position; negative → first event |
| `seekToFirstEvent()` | Jump to log head |
| `seekToEnd()` | Jump past last event; returns last position |
| `getPosition()` | Current position (negative if empty) |
| `peekNext()` | Peek without advancing |
| `hasNext()` / `next()` | Standard iterator |

`LoggedEvent` key fields: `getPosition()`, `getKey()`, `getSourceEventPosition()`, `shouldSkipProcessing()`, `readValue(BufferReader)`, `readMetadata(BufferReader)`.

### LogStreamWriter (`io.camunda.zeebe.logstreams.log.LogStreamWriter`)
Functional interface. Implemented by `Sequencer`.

```java
Either<WriteFailure, Long> tryWrite(WriteContext ctx, List<LogAppendEntry> entries, long sourcePosition)
Either<WriteFailure, Long> tryWrite(WriteContext ctx, LogAppendEntry entry)
boolean canWriteEvents(int eventCount, int batchSize)
```

- Returns **Right(highestPosition)** on success — the position of the last entry in the batch
- Returns **Left(WriteFailure)**: `CLOSED`, `WRITE_LIMIT_EXHAUSTED`, `REQUEST_LIMIT_EXHAUSTED`, `INVALID_ARGUMENT`
- Batch writes are **atomic** — all succeed or all fail
- Thread-safe (internally serialized via `ReentrantLock`)
- `sourcePosition` is a back-pointer to the record triggering these writes

---

## 2. Log Truncation

**The logstreams module has no truncation API.** It is **append-only by design**. Key facts:

- `LogStreamReader` is purely read-only
- `LogStreamWriter`/`Sequencer` only appends
- Truncation is a **storage-layer concern** — the `LogStorage` implementation (e.g., Raft-backed) handles it externally
- The `shouldSkipProcessing()` flag on `LoggedEvent` is the closest in-band concept — entries can be marked "already processed" so consumers skip re-execution without physical removal
- Safe truncation point from the logstreams perspective = entries that are **committed** (replicated via Raft) and whose positions have been signaled as processed via `FlowControl.onProcessed()`

---

## 3. Committed/Flushed Position Tracking

Three position levels tracked in `FlowControl` + `AppendListener`:

| Level | Who Sets It | Meaning |
|-------|-------------|---------|
| **Written** | `AppendListener.onWrite(index, highestPosition)` | Durably written to local disk |
| **Committed** | `AppendListener.onCommit(index, highestPosition)` | Replicated and committed via Raft |
| **Processed** | `FlowControl.onProcessed(position)` | Stream processor finished with entry |
| **Exported** | `FlowControl.onExported(position)` | Exporter finished with entry |

**Notification chain:**
1. `LogStorage` calls `AppendListener.onCommit()` → updates `FlowControl.lastCommittedPosition`
2. `LogStreamImpl` (implements `LogStorage.CommitListener`) receives `onCommit()` → notifies all registered `LogRecordAwaiter` listeners via `onRecordAvailable()`

**To listen for new committed records:**
```java
logStream.registerRecordAvailableListener(() -> {
    // safe to call reader.hasNext() / reader.next() now
});
```

---

## 4. Consumer Offset Tracking & Position Management

**There is no built-in consumer offset registry.** Logstreams is a library, not a broker. The design pushes offset management to the consumer.

The relevant hook is `FlowControl`:

```java
flowControl.onProcessed(position);   // signal entry fully processed
flowControl.onExported(position);    // signal entry exported (updates rate-limit lag)
```

Internally, `FlowControl` uses a **fixed-capacity `RingBuffer`** of `InFlightEntry` objects (default capacity 8K, power-of-2) that tracks the lifecycle: `onAppend → onWrite → onCommit → onProcessed`.

**What this means for implementing consumer group offset tracking:**

| Concern | What Logstreams Provides | What You Must Build |
|---------|--------------------------|---------------------|
| Reading up to a position | `reader.seek(position)` to resume | Persist the checkpoint position externally |
| Knowing safe cleanup boundary | `FlowControl.lastCommittedPosition` | Aggregate min(consumedPosition) across all consumers |
| Signaling consumption | `FlowControl.onProcessed(pos)` | Call this per-entry after processing |
| Compaction/GC | Nothing — append-only | Delegate to `LogStorage` implementation with a safe trim position |
| Multi-consumer fan-out | None | You must maintain per-consumer positions and compute the min |

**Practical pattern for log cleanup based on consumed positions:**
1. Each consumer maintains its own `lastConsumedPosition` (persisted, e.g., in RocksDB or a state snapshot)
2. A coordinator computes `minConsumedPosition = min(pos₁, pos₂, ..., posN)`
3. Entries with `position ≤ minConsumedPosition` AND `position ≤ lastCommittedPosition` are safe to trim
4. Trim is initiated at the `LogStorage` layer — logstreams itself has no trim/delete API

---

### Study event serialization for binary format

Three parallel investigation agents running. I'll read their results as they complete.

---

### Study consumer heartbeat and group coordination patterns

Two of four agents completed. Waiting on the last two — I'll compile all findings once they finish.

## Refined Requirements

## Event Bridge — Requirements Specification

### Problem Statement
Build a standalone, Spring Boot–based "Event Bridge" component in the Camunda monorepo at `event-bridge/`. It receives batches of binary events, persists them in an append-only log per partition, and allows consumer groups to pull events in a Kafka-like fashion. It reuses existing Camunda modules: RAFT (via Atomix), SWIM, LogStream, Actor Scheduler, Sequencer pattern, and Netty transport.

---

### Architecture Overview

```
Event Bridge Client
    │  HTTP (publish / pull)
    ▼
Event Bridge Gateway  ──── Netty internal transport ────► Event Bridge Broker(s)
                                                              └── RAFT partition(s)
                                                              └── LogStream per partition
```

---

### HTTP API Schemas

All client-facing endpoints use `Content-Type: application/json` unless noted. HTTP status codes are stated explicitly per response below; all unlisted success responses return **HTTP 200**. The HTTP server implementation uses **Spring MVC** (Spring Boot's embedded Tomcat).

#### `POST /events/{partitionId}` — Publish batch

**Request body:**
```json
{
  "events": ["<base64-encoded bytes>", "<base64-encoded bytes>"]
}
```
- `events`: required, non-empty array of base64-encoded binary payloads. An empty array is a validation error (see table below).

**Batch-to-log-entry cardinality:** Each call to this endpoint is written as **one RAFT log entry** regardless of how many events the batch contains. Each individual event within that batch is assigned its own unique, monotonically increasing log position by the LogStream sequencer. For example, publishing a batch of three events when the current log tail is at position 1000 produces positions `[1001, 1002, 1003]` and advances the RAFT log by exactly one entry. The snapshot interval counter (`snapshot-interval-entries`) counts **RAFT log entries** (i.e., batches), not individual event positions.

**Response body (success, HTTP 200):**
```json
{
  "positions": [1001, 1002]
}
```
- `positions`: log positions assigned to each published event, in order.

**Response body (error):**
```json
{
  "error": "PARTITION_NOT_FOUND" | "LEADER_UNAVAILABLE" | "INVALID_REQUEST",
  "message": "<human-readable detail>"
}
```

| Error code | HTTP status | Condition |
|---|---|---|
| `PARTITION_NOT_FOUND` | 400 | The specified `partitionId` does not exist |
| `LEADER_UNAVAILABLE` | 503 | All 4 routing attempts exhausted without reaching a leader |
| `INVALID_REQUEST` | 400 | `events` is null or empty |

---

#### `POST /consumers/{groupId}/{consumerId}/subscribe` — Register consumer

No request body.

Registers the consumer with Broker-0 (the coordinator). Broker-0 adds the consumer to the active set and **immediately triggers a rebalance** — including when a previously dead consumer re-subscribes. This behavior is uniform for all subscribe calls regardless of whether the consumer is new or re-joining. The rebalance completes synchronously before the response is sent; the returned `assignedPartitions` reflects the outcome of that rebalance.

**Response body (success, HTTP 200):**
```json
{
  "status": "OK",
  "assignedPartitions": [0, 2]
}
```
- `assignedPartitions`: list of partition IDs assigned to this consumer after the rebalance triggered by this call.

**Response body (coordinator unavailable, HTTP 503):**
```json
{
  "status": "ERROR",
  "error": "COORDINATOR_UNAVAILABLE",
  "message": "<human-readable detail>"
}
```
- Returned when Broker-0 is unreachable. The client should retry with back-off; no automatic retry is built into the client for this call.

---

#### `GET /events/{partitionId}/poll` — Pull next batch

**Query parameters:**
- `groupId` (string, required)
- `consumerId` (string, required)
- `fromPosition` (long, required) — the log position at which to start reading (inclusive). Pass `-1` to start from the first available event in the log (resolved server-side; see position resolution rules below). On subsequent polls, pass the `nextPosition` value from the previous response.
- `maxRecords` (integer, required; must be ≥ 1)
- `serverWaitMs` (integer, optional, default `0`) — long-poll wait time; see `Consumer.poll` semantics and broker long-poll behavior below. Maximum accepted value: **30 000 ms**; the broker silently clamps values above this ceiling to 30 000 ms.

**`fromPosition` resolution rules:**
- `-1`: resolved to the first available log position. If the log has been partially truncated (e.g., oldest retained position is 500), `-1` resolves to 500.
- Any other value: used as-is. If the specified position has already been truncated (i.e., it is below the current log retention boundary), the broker returns HTTP 400, error `POSITION_TRUNCATED` (see error table below).
- After a rebalance (`REBALANCE_IN_PROGRESS` response), the client resumes polling each newly assigned partition using its last committed position for that partition (or `-1` if never committed).

**Response body (normal, HTTP 200):**
```json
{
  "status": "OK",
  "events": [
    { "position": 1001, "payload": "<base64-encoded bytes>" }
  ],
  "nextPosition": 1002
}
```
- `events` may be empty if no new records exist at or after `fromPosition`.
- `nextPosition`: the position to pass as `fromPosition` on the next poll call (exclusive upper bound of this batch). When `events` is empty — because no new records exist at or after `fromPosition`, or because `fromPosition` equals the log tail — `nextPosition` equals the current log tail position (the position at which the next written event would be placed).

**Response body (rebalance in progress, HTTP 200):**
```json
{
  "status": "REBALANCE_IN_PROGRESS",
  "assignedPartitions": [0, 2],
  "events": []
}
```
- `assignedPartitions`: the full list of partitions now assigned to this consumer after rebalance. The client must stop fetching from previously assigned partitions not in this list.
- `events` is always empty when `status` is `REBALANCE_IN_PROGRESS`.
- `nextPosition` is **not present** in this response. The consumer must resume polling its newly assigned partitions using its last committed position for each partition (or `-1` if never committed).

**Response body (error):**
```json
{
  "status": "ERROR",
  "error": "CONSUMER_NOT_REGISTERED" | "PARTITION_NOT_FOUND" | "INVALID_PARAMETER" | "POSITION_TRUNCATED",
  "message": "<human-readable detail>"
}
```

| Error code | HTTP status | Condition |
|---|---|---|
| `CONSUMER_NOT_REGISTERED` | 400 | Consumer has not called `subscribe`, or was marked dead and must re-subscribe |
| `PARTITION_NOT_FOUND` | 400 | The specified `partitionId` does not exist |
| `INVALID_PARAMETER` | 400 | `maxRecords` < 1 |
| `POSITION_TRUNCATED` | 400 | The specified `fromPosition` (other than `-1`) is below the current log retention boundary |

---

#### `POST /events/{partitionId}/commit` — Commit offset

**Query parameters:**
- `groupId` (string, required)
- `consumerId` (string, required)
- `position` (long, required)

**Commit idempotency:** If `position` is ≤ the consumer's current committed offset for the partition, the request is silently accepted (returns `OK`) without updating the stored offset. Commits are idempotent.

**Response body (success, HTTP 200):**
```json
{ "status": "OK" }
```

**Response body (error):**
```json
{
  "status": "ERROR",
  "error": "PARTITION_NOT_FOUND" | "CONSUMER_NOT_REGISTERED",
  "message": "<human-readable detail>"
}
```

| Error code | HTTP status | Condition |
|---|---|---|
| `PARTITION_NOT_FOUND` | 400 | The specified `partitionId` does not exist |
| `CONSUMER_NOT_REGISTERED` | 400 | Consumer has not called `subscribe` or was marked dead |

---

#### `POST /consumers/{groupId}/{consumerId}/heartbeat` — Consumer liveness signal

No request body.

**Response body (success, HTTP 200):**
```json
{ "status": "OK" }
```

**Response body (error):**
```json
{
  "status": "ERROR",
  "error": "COORDINATOR_UNAVAILABLE" | "CONSUMER_NOT_REGISTERED",
  "message": "<human-readable detail>"
}
```

| Error code | HTTP status | Condition |
|---|---|---|
| `COORDINATOR_UNAVAILABLE` | 503 | Broker-0 is not reachable; heartbeat not recorded |
| `CONSUMER_NOT_REGISTERED` | 400 | The consumer was previously marked dead (heartbeat timeout elapsed); the consumer must call `subscribe` again before heartbeats are accepted |

---

### Acceptance Criteria

#### Gateway
- [ ] Exposes the HTTP API described in the schemas above using **Spring MVC** (Spring Boot embedded Tomcat); no auth
- [ ] Routes publish requests to the RAFT leader broker for the given partition using the Netty-based internal transport
- [ ] Makes up to **4 total attempts** (1 initial + 3 retries) to reach the partition leader before returning HTTP 503 to the client; this single policy applies everywhere retry behavior is mentioned in this document
- [ ] Discovers partition leaders via a `BrokerInfo`-style topology propagation layer: each broker periodically broadcasts its partition leadership state as metadata carried over SWIM gossip (`SwimMembershipProtocol` member properties); the gateway consumes these broadcasts and maintains an in-memory partition-leader map. SWIM itself provides only cluster membership; leader assignment is determined by RAFT and disseminated as a separate topology event on top of SWIM — SWIM does not perform or influence leader election.

#### Broker
- [ ] Manages N partitions (configurable; default 1); acts as RAFT leader or follower per partition
- [ ] When leader: sequences incoming events using a Sequencer-like actor, writes to LogStream, replicates via RAFT
- [ ] When follower: replicates log entries received from leader
- [ ] Maintains per-consumer-group, per-partition offset state (in-memory, with snapshot support — see Snapshot Specification below)
- [ ] **Broker-0 is the consumer group coordinator**: handles subscribe requests, heartbeats, auto-assigns partitions to consumers in a group, detects dead consumers (missed heartbeats), reassigns partitions. **Broker-0 coordinator failover is out of scope for this iteration** (see Out of Scope); the known limitation is documented under Edge Cases.
- [ ] Truncates LogStream entries whose position is ≤ `min(lastCommittedPosition)` taken over the set of all consumers currently *alive and assigned* to the partition (see truncation rules below)
- [ ] Consumer initial position `-1`: resolved to the first available log position; if the log has been truncated, this is the oldest retained position (e.g., 500 if positions 0–499 have been removed)
- [ ] **Long-poll implementation**: when `serverWaitMs > 0` and no records are available at `fromPosition`, the broker parks the responding actor and registers a wake-up callback on the partition's LogStream write-notification path. When a new entry is written to the log, all parked poll actors for that partition are woken and respond immediately. If no entry arrives within `serverWaitMs` milliseconds, a scheduled timer wakes the actor and returns an empty response. The maximum accepted value for `serverWaitMs` is **30 000 ms**; the broker silently clamps values exceeding this ceiling. This mechanism must not block any ActorScheduler CPU thread; all park/wake operations use the actor's async scheduling primitives.

#### Consumer Group Coordination (Broker-0)
- [ ] Each consumer sends periodic heartbeats; a consumer with no heartbeat within `event-bridge.consumer.heartbeat-timeout-ms` (default: **5000 ms**) is considered dead
- [ ] A heartbeat received from a consumer that has been marked dead returns `CONSUMER_NOT_REGISTERED` (HTTP 400); the consumer must call `subscribe` again to rejoin
- [ ] Partitions are auto-assigned using a **stable round-robin** algorithm: existing assignments are preserved unchanged where the current assignee is still alive; only orphaned partitions (from dead or departed consumers) are redistributed. Redistribution proceeds round-robin across active consumers sorted lexicographically by `consumerId`, assigning each orphaned partition to the currently least-loaded consumer (tie-broken by sort order). The goal is minimum churn: no live consumer's assignment changes unless necessary.
- [ ] On any subscribe call (new join or re-subscribe after death), Broker-0 **immediately triggers a rebalance** and returns the resulting assignment in the subscribe response. Affected consumers are notified of reassigned partitions via the `REBALANCE_IN_PROGRESS` response on their next poll.
- [ ] A dead consumer that later calls `subscribe` is treated as a new join: it is added to the active set and a rebalance is triggered immediately (see above). Auto-rejoin without an explicit re-subscribe call is not supported.

#### Snapshot Specification

Offset state (in-memory `Map<groupId, Map<consumerId, committedPosition>>`) is persisted to disk via RAFT snapshots:

- **Trigger**: A snapshot is taken automatically whenever the RAFT leader's log grows by more than `event-bridge.raft.snapshot-interval-entries` **RAFT log entries** (i.e., batches — each `POST /events/{partitionId}` call is one entry regardless of how many events it contains) since the previous snapshot (default: **1000 entries**). Snapshots may also be triggered manually for operational purposes.
- **Storage location**: The offset state is written directly into the RAFT `RaftSnapshotWriter` payload for the partition, co-located with the RAFT partition's existing snapshot storage on the broker's local disk. No separate file is used.
- **Data format**: The payload is an SBE-encoded `OffsetSnapshotPayload` (length-prefixed) containing a flat list of `(groupId: String, consumerId: String, committedPosition: long)` triples — one triple per active consumer per partition. The `OffsetSnapshotPayload` SBE schema is defined in `event-bridge-core/src/main/resources/sbe/` alongside the other protocol messages.
- **Recovery**: On broker startup or failover, the broker reads the latest RAFT snapshot, deserializes `OffsetSnapshotPayload`, and reconstructs the in-memory offset map. Consumers whose positions are recorded in the snapshot resume from their last committed position. A consumer not present in the snapshot resumes from `-1` (resolved to the first available log position).

#### LogStream Truncation Rules
- The eligible truncation set is: all consumers that are (a) alive (last heartbeat within timeout) **and** (b) currently assigned to the partition.
- The truncation boundary is `min(lastCommittedPosition)` across that set.
- A dead consumer's last committed position is **excluded** from the minimum calculation once the consumer is marked dead; this prevents a stale/dead consumer from permanently blocking truncation.
- If no consumer is alive and assigned to a partition, no truncation occurs (conservative: retain all log entries).

#### Event Bridge Client (Java library)
- [ ] `CompletableFuture<List<Long>> publishBatch(int partitionId, List<byte[]> events)` — publishes a batch of raw binary events; resolves to the list of log positions assigned to each event in order; completes exceptionally with `EventBridgeException` on HTTP 4xx/5xx
- [ ] `CompletableFuture<Consumer> subscribe(String groupId, String consumerId)` — registers the consumer with Broker-0 and resolves to a `Consumer` handle whose initial `assignedPartitions` reflects the assignment returned by Broker-0; completes exceptionally with `CoordinatorUnavailableException` if Broker-0 returns HTTP 503; must be called (or re-called after a `CONSUMER_NOT_REGISTERED` error) before polling; the `Consumer` handle internally tracks the last `nextPosition` per partition (initially `-1` for all assigned partitions)
- [ ] `Consumer.poll(int maxRecords, Duration timeout)` — pulls the next batch from all currently-assigned partitions in **ascending partition ID order**; `timeout` is the **per-partition** server-side long-poll wait time passed to each broker request as `serverWaitMs`; **callers must be aware that with N partitions and a timeout of T ms, the worst-case wall-clock latency of `poll()` is N × T plus network overhead** (e.g., 4 partitions × 1 000 ms = up to 4 000 ms); the client's HTTP socket timeout must be set to at least `serverWaitMs + network_slack`; each partition's request uses the `nextPosition` tracked internally from the previous response for that partition; returns `List<Event>` where each `Event` carries `long position`, `int partitionId`, and `byte[] payload`; **if any partition returns `status: REBALANCE_IN_PROGRESS`**, the client **immediately discards all events fetched from partitions already iterated in this call** and throws `RebalanceInProgressException(List<Integer> assignedPartitions)` — no partial results are returned; the exception carries the consumer's new full partition assignment; the caller must catch this exception, update its active partition set accordingly (dropping any partitions not in the new assignment), and retry `poll()`
- [ ] `Consumer.commitOffset(int partitionId, long position)` — explicitly commits consumed position for the given partition; returns `CompletableFuture<Void>`; completes exceptionally with `EventBridgeException` on error
- [ ] `Consumer.sendHeartbeat()` — sends liveness signal to Broker-0; returns `CompletableFuture<Void>`; completes exceptionally with `CoordinatorUnavailableException` if Broker-0 is unreachable or returns `COORDINATOR_UNAVAILABLE`; completes exceptionally with `ConsumerNotRegisteredException` if Broker-0 returns `CONSUMER_NOT_REGISTERED` (consumer has been marked dead and must re-subscribe); should be called at an interval shorter than `event-bridge.consumer.heartbeat-timeout-ms`

---

### Technical Requirements

| Concern | Decision |
|---|---|
| Module location | New top-level `event-bridge/` directory; sub-modules: `event-bridge-core`, `event-bridge-broker`, `event-bridge-gateway`, `event-bridge-client` |
| Client-facing HTTP server | **Spring MVC** (Spring Boot embedded Tomcat) |
| Gateway→Broker transport | Existing Netty-based internal transport from `zeebe/atomix/cluster` (transport module) |
| Serialization | SBE framing for internal gateway↔broker messages (schemas at `event-bridge-core/src/main/resources/sbe/*.xml`, code-generated during `event-bridge-core` Maven build); JSON over HTTP for client-facing API |
| Consensus / replication | RAFT via `zeebe/atomix/cluster` (`RaftPartition` / `RaftPartitionServer`) |
| Cluster membership | SWIM via `AtomixCluster` / `SwimMembershipProtocol`; leader topology disseminated on top of SWIM metadata (not by SWIM itself) |
| Log storage | `LogStream` (one per partition, backed by RAFT log storage via `AtomixLogStorage`) |
| Event sequencing | Actor-based Sequencer pattern (mirror `Sequencer` in `zeebe/broker`) |
| Threading | `ActorScheduler` (CPU + IO thread pools); long-poll park/wake must use actor async primitives and must not block CPU threads |
| Batch-to-RAFT-entry mapping | Each `POST /events/{partitionId}` call = one RAFT log entry; each event in the batch receives its own sequential log position |
| Offset tracking | Explicit commit model only; no backpressure credits, no write-ahead throttling, no in-flight window accounting; offsets are persisted in RAFT snapshots (see Snapshot Specification) |
| Fetch position tracking | Client-side only: the `Consumer` handle tracks the last `nextPosition` per partition internally; the broker is stateless with respect to fetch position (it reads from the `fromPosition` supplied on each poll request) |
| Heartbeat timeout | `event-bridge.consumer.heartbeat-timeout-ms`; default **5000 ms** |
| Consumer start position | `-1` = read from the first available log position (oldest retained after any truncation) |
| Long-poll ceiling | `serverWaitMs` maximum: **30 000 ms**; broker silently clamps values above this ceiling |
| Routing retry policy | 4 total attempts (1 initial + 3 retries); HTTP 503 returned on final failure |
| Snapshot interval unit | **RAFT log entries (batches)**; default **1000 entries** |
| SBE schema location | `event-bridge-core/src/main/resources/sbe/*.xml`; code generated during the `event-bridge-core` Maven build phase |
| Distribution | Follow `/dist` pattern: `StandaloneEventBridge.java` entry point starts a **single JVM containing the gateway and all configured broker partitions**; intended for single-node development and testing. For multi-node cluster deployments, each physical node runs its own `StandaloneEventBridge` process with a distinct node ID and address configuration. Binary registered in `dist/pom.xml`. |
| Auth | None in this iteration |

#### New SBE Message Types Required

The following internal protocol messages between gateway and broker must be defined as new SBE schemas in `event-bridge-core/src/main/resources/sbe/`. No existing Zeebe SBE message types are reused for these purposes.

| SBE Message | Direction | Purpose |
|---|---|---|
| `PublishBatchRequest` | Gateway → Broker | Carries `partitionId`, array of event payloads |
| `PublishBatchResponse` | Broker → Gateway | Carries assigned log positions or error code |
| `PollRequest` | Gateway → Broker | Carries `partitionId`, `groupId`, `consumerId`, `fromPosition`, `maxRecords`, `serverWaitMs` |
| `PollResponse` | Broker → Gateway | Carries status, event list (position + payload), rebalance info |
| `CommitOffsetRequest` | Gateway → Broker | Carries `partitionId`, `groupId`, `consumerId`, `position` |
| `CommitOffsetResponse` | Broker → Gateway | Carries status or error code |
| `HeartbeatRequest` | Gateway → Broker-0 | Carries `groupId`, `consumerId` |
| `HeartbeatResponse` | Broker-0 → Gateway | Carries status or error code |
| `SubscribeRequest` | Gateway → Broker-0 | Carries `groupId`, `consumerId` |
| `SubscribeResponse` | Broker-0 → Gateway | Carries status, `assignedPartitions` list, or error code |
| `OffsetSnapshotPayload` | Broker (internal) | Serialized offset state written into the RAFT snapshot; carries a flat list of `(groupId, consumerId, committedPosition)` triples |

---

### Edge Cases
- Publish to a non-existent partition → HTTP 400, error `PARTITION_NOT_FOUND`
- Publish with a null or empty `events` array → HTTP 400, error `INVALID_REQUEST`
- Publish routed to a non-leader (stale topology) → retry; 4 total attempts (1 initial + 3 retries); on exhaustion → HTTP 503, error `LEADER_UNAVAILABLE`
- Poll with `maxRecords` < 1 → HTTP 400, error `INVALID_PARAMETER`
- Poll with `fromPosition = -1` after log truncation → resolved to the oldest retained log position (e.g., 500 if positions 0–499 have been truncated); no error is returned
- Poll with a specific `fromPosition` that has been truncated → HTTP 400, error `POSITION_TRUNCATED`; the consumer should re-subscribe or resume from its last committed position
- Consumer polls at position beyond the log end → HTTP 200, `status: OK`, `events: []`, `nextPosition` = current log tail (the position at which the next written event would be placed)
- Consumer polls when no new records exist and `serverWaitMs = 0` → HTTP 200, `status: OK`, `events: []`, `nextPosition` = current log tail
- Consumer polls with `serverWaitMs > 0` and no records arrive within the wait window → broker parks the response actor until a new log entry is written or the wait window expires, then returns HTTP 200, `status: OK`, `events: []`, `nextPosition` = current log tail; `serverWaitMs` values above 30 000 ms are clamped silently
- Consumer heartbeat timeout → coordinator marks dead, removes from truncation minimum calculation, rebalances remaining consumers; next poll or heartbeat by the dead consumer returns HTTP 400, `status: ERROR`, error `CONSUMER_NOT_REGISTERED`; the consumer must call `subscribe` again to rejoin
- Dead consumer sends a heartbeat → HTTP 400, error `CONSUMER_NOT_REGISTERED`; the consumer must call `subscribe` before heartbeats are accepted
- Dead consumer re-subscribes → Broker-0 adds it to the active set and **immediately triggers a rebalance**; affected consumers are notified via `REBALANCE_IN_PROGRESS` on their next poll
- New consumer joins group → Broker-0 immediately triggers a rebalance; affected consumers notified via `REBALANCE_IN_PROGRESS` (with `nextPosition` absent) on their next poll
- `Consumer.poll()` iterates partitions and encounters `REBALANCE_IN_PROGRESS` on partition K → all events fetched from partitions iterated before K in this call are discarded; `RebalanceInProgressException` is thrown immediately; the caller updates its partition set and retries `poll()`
- Commit position ≤ already-committed offset → silently accepted (returns HTTP 200, `status: OK`); the stored offset is not updated (idempotent commit)
- **Subscribe when Broker-0 is unavailable** → HTTP 503, error `COORDINATOR_UNAVAILABLE`; the client `subscribe()` method completes exceptionally with `CoordinatorUnavailableException`; no automatic retry is built into the client; the caller should retry with back-off
- **Broker-0 (coordinator) unavailable after initial subscribe** → publish and poll to all partitions continue normally (RAFT is unaffected); heartbeat and subscribe requests return HTTP 503, error `COORDINATOR_UNAVAILABLE`; no rebalances are possible while Broker-0 is down; existing partition assignments remain frozen until Broker-0 recovers. This is a known limitation; coordinator high-availability is out of scope for this iteration.
- All replicas down for a partition → publish returns HTTP 503, error `LEADER_UNAVAILABLE`, after 4 total attempts
- No alive consumer assigned to a partition → log truncation is suspended; no entries are removed until at least one consumer is alive and assigned

---

### Out of Scope (this iteration)
- Authentication / authorization
- Partition key hashing (client provides partition ID directly)
- Multi-topic / named streams (single implicit topic per partition)
- Consumer-initiated partition assignment (all assignment is coordinator-driven)
- Schema registry or event type system
- Compacted logs / key-based retention
- Cross-datacenter replication
- Broker-0 coordinator failover / high-availability coordination (Broker-0 is a SPOF; see Edge Cases for degraded-mode behavior)

## Engineering Decisions

## Technical Decisions & Assumptions

### Module Structure
- New top-level `event-bridge/` with four sub-modules: `event-bridge-core`, `event-bridge-broker`, `event-bridge-gateway`, `event-bridge-client`
- Added to root `pom.xml` `<modules>` section; `StandaloneEventBridge` registered in `dist/pom.xml` via `appassembler-maven-plugin`
  - Main class: `io.camunda.eventbridge.StandaloneEventBridge` (plain `main`, not Spring Boot)
  - Assembled artifact name: `event-bridge-standalone` (produces `event-bridge-standalone/bin/event-bridge`)

### SBE Code Generation
- XML schemas in `event-bridge-core/src/main/resources/sbe/*.xml`
- Code-generated via `exec-maven-plugin` at `generate-sources` phase (mirrors `zeebe/journal`)
- Output at `${project.build.directory}/generated-sources/sbe`; added to sources via `build-helper-maven-plugin`

### Batch → Positions Mapping
- `Sequencer.tryWrite(List<LogAppendEntry>)` natively maps N events → N log positions in one `logStorage.append(lo, hi, batch)` call = one RAFT entry. No custom position arithmetic needed.
- **Snapshot interval:** controlled by `event-bridge.broker.snapshot.interval-entry-count` (defined in `BrokerConfig`), defaulting to **10,000**. The counter increments on each committed RAFT log entry index (not LogStream positions). When the counter reaches the configured value it is reset to zero and a snapshot is triggered. Engineers implementing or testing snapshotting must use this property to control trigger frequency.

### Transport
- All gateway↔broker communication uses `NettyMessagingService` — including standalone single-JVM mode (loopback). Uniform code path, no in-process shortcut.
- Messages registered via `registerHandler(type, BiFunction<Address, byte[], CompletableFuture<byte[]>> handler)`; SBE-framed payloads.

#### Message Type Registry
The `type` string passed to `registerHandler` is the sole message discriminator. All type constants are defined in `io.camunda.eventbridge.transport.MessageTypes` (in `event-bridge-core`) and **must not be defined ad hoc elsewhere**. Naming convention: `"eb.<area>.<operation>"`. The canonical set is:

| Constant name | String value | Direction |
|---|---|---|
| `PRODUCE_REQUEST` | `"eb.produce.request"` | Gateway → partition leader |
| `FETCH_REQUEST` | `"eb.fetch.request"` | Gateway → partition leader |
| `SUBSCRIBE_REQUEST` | `"eb.subscribe.request"` | Gateway → coordinator |
| `SUBSCRIBE_RESPONSE` | `"eb.subscribe.response"` | Coordinator → gateway |
| `HEARTBEAT_REQUEST` | `"eb.heartbeat.request"` | Gateway → coordinator |
| `COMMIT_OFFSET_REQUEST` | `"eb.commit-offset.request"` | Gateway → coordinator |
| `FETCH_ASSIGNMENT_REQUEST` | `"eb.fetch-assignment.request"` | Gateway → coordinator |
| `TRUNCATE_REQUEST` | `"eb.truncate.request"` | Coordinator → partition leader |
| `LATEST_POSITION_REQUEST` | `"eb.latest-position.request"` | Gateway → partition leader |

Response types for the last four use the same string with `.response` suffix and follow the same convention. Any new message type must be added to `MessageTypes` before use. Do not introduce string literals in handler registration code.

### Coordinator Broker Identity ("Broker-0")
- The coordinator broker — referred to throughout this document as "Broker-0" — is identified by a **static configuration property** `event-bridge.coordinator.broker-id` (type `String`, e.g. `"broker-0"`). This value is set at cluster-bootstrap time and must match the `memberId` used when the broker registers with `SwimMembershipProtocol`. There is no dynamic election; the coordinator role is always held by the broker whose `memberId` equals the configured value.
- **Coordinator discovery:** the gateway resolves the coordinator's `Address` as follows. On each membership event it scans all `Member.properties()` maps for the key `"eb.coordinator"`. The broker that holds this key writes its own logical `memberId` as the value (e.g., `"broker-0"`). The presence of this key on a `Member` identifies that `Member` as the current coordinator; the gateway routes to `Member.address()` of that member. The value (logical broker ID) is retained in the gateway for logging and diagnostics — specifically so that coordinator identity is visible in logs without requiring a reverse lookup. No DNS or name-lookup step is needed.
- **Permanent removal of the coordinator broker:** if the broker identified by `event-bridge.coordinator.broker-id` is permanently decommissioned, operators must update the configuration property on all remaining brokers and gateways and perform a rolling restart. Automatic coordinator migration is out of scope for this iteration; the coordinator remains a SPOF for consumer group management (see also the Known Limitations note under Consumer Group Coordinator State).

### Consumer Group Operation Routing
- All consumer-group operations (subscribe, heartbeat, commit-offset, fetch-assignment) are routed by the gateway to the coordinator broker.
- **Gateway routing:** the gateway proxies these requests to `coordinatorAddress` (resolved as described in Coordinator Broker Identity). The client always contacts the gateway — it has no direct knowledge of which broker is the coordinator. The gateway's proxy path mirrors the partition-leader proxy path: a `NettyMessagingService` call to `coordinatorAddress` with the appropriate SBE-framed message type.
- **While `coordinatorAddress` is unknown** (e.g., before the first membership event after gateway startup, or during coordinator outage): the gateway immediately returns `COORDINATOR_UNAVAILABLE` to the caller rather than queuing or blocking. The client back-off policy (see Client section) handles retries.

### Leader Topology Dissemination
- Each broker writes partition leadership state into `Member.properties()` (e.g., `"eb.partition.0.leader" = "broker-1"`). The coordinator broker additionally writes `"eb.coordinator" = "<its own memberId>"`.
- `SwimMembershipProtocol` detects property changes and gossips them automatically.
- **Name-to-Address resolution:** on each membership event, the gateway reads both the `"eb.partition.N.leader"` property value (the broker's logical name) and the sending `Member`'s advertised `Member.address()`. The in-memory `Map<partitionId, Address>` is updated with the `Address` obtained directly from that `Member` object — no separate DNS or name-lookup step is needed. The logical name is stored only for logging and diagnostics. The same mechanism resolves `coordinatorAddress` (see Coordinator Broker Identity).
- **`MEMBER_REMOVED` handling:** when the gateway receives a `MEMBER_REMOVED` membership event for a broker, it must:
  1. Remove all `partitionId → Address` entries in the in-memory map whose `Address` matches the removed member's advertised address.
  2. If `coordinatorAddress` matches the removed member's address, atomically clear `coordinatorAddress` (set it to `null`).
  3. Mark all affected partition IDs as **leader-unknown** in the routing table.
  4. While a partition is leader-unknown, produce and fetch requests for that partition are rejected immediately with the error code `LEADER_UNKNOWN`. The caller (gateway HTTP handler) surfaces this as an `HTTP 503` with problem type `leader-unknown`. Clients should treat `LEADER_UNKNOWN` as a transient error and apply back-off before retrying; re-querying topology on the next successful membership event will restore routing automatically.

  Stale `Address` entries must never be retained after `MEMBER_REMOVED`; routing to a removed broker's address will produce a `NettyMessagingService` connection failure and is not an acceptable fallback path.

### Long-Poll Implementation
- Broker registers a `LogRecordAwaiter` on `LogStream.registerRecordAvailableListener()` when `serverWaitMs > 0` and no records available.
- Woken by: (1) new RAFT commit → `AtomixLogStorage.onCommit()` → `LogStreamImpl.onCommit()` → all awaiters, or (2) actor timer expiry at the `serverWaitMs` ceiling.
- **Long-poll ceiling:** controlled by the configuration property `event-bridge.broker.long-poll.max-wait-ms`, defaulting to **30,000 ms**. Any `serverWaitMs` supplied by the client is clamped to this value server-side. The property is not a source constant; operators may lower it under high-partition-count deployments.
- **Rebalance also wakes all parked long-poll actors for the affected partition immediately**, returning `REBALANCE_IN_PROGRESS`.
- All park/wake uses `CompletableActorFuture` + `ActorControl.runOnCompletion()` — no CPU thread blocking.

#### `LogRecordAwaiter` — new type
`LogRecordAwaiter` is a **new interface** to be created in `event-bridge-broker` at `io.camunda.eventbridge.broker.logstream.LogRecordAwaiter`. It is not an existing Zeebe type. Its contract:

```java
/**
 * Notified when at least one new record becomes available on a LogStream partition,
 * or when the broker-side long-poll ceiling elapses.
 * Implementations must be safe to complete from any thread (the RAFT commit thread
 * calls onRecordAvailable() outside the actor context).
 */
public interface LogRecordAwaiter {
  /** Returns a future that completes when records are available or the ceiling elapses. */
  CompletableFuture<Void> awaitRecord(long timeoutMs);

  /** Immediately completes the pending future (if any), waking the parked actor. */
  void onRecordAvailable();
}
```

The `LogStreamImpl` maintains a `List<LogRecordAwaiter>` and calls `onRecordAvailable()` on each entry from its `onCommit()` callback. Awaiters remove themselves from the list when their future completes. Thread-safety of list access is guaranteed by the actor's single-threaded execution model; `onRecordAvailable()` schedules completion on the actor thread via `ActorControl.runOnCompletion()` rather than completing the future directly.

### Consumer Group Coordinator State
- Active consumers, assignments, and heartbeat timers are **in-memory only on the coordinator broker** (the broker whose `memberId` equals `event-bridge.coordinator.broker-id`; see Coordinator Broker Identity).
- Snapshot persists only offset state (committed positions) via `OffsetSnapshotPayload` SBE.
- **After coordinator restart:** the broker returns a `COORDINATOR_UNAVAILABLE` error code on all consumer-group endpoints while it is recovering. The `event-bridge-client` SDK treats this error as a transient failure and transparently re-issues the subscribe request using **exponential back-off** with the following parameters (all are constants in `EventBridgeClientConfig` and are not user-configurable in this iteration):
  - Initial delay: **500 ms**
  - Multiplier: **2.0**
  - Maximum delay: **30,000 ms**

  The retry loop continues until the subscribe request succeeds or the application explicitly closes the `Consumer` handle. The back-off is applied between attempts; no jitter is added in this iteration.
- **`COORDINATOR_UNAVAILABLE` for non-subscribe operations:** heartbeat, commit-offset, and fetch-assignment can also receive `COORDINATOR_UNAVAILABLE` during a coordinator outage. The client applies the **same exponential back-off parameters** (500 ms initial, 2.0 multiplier, 30,000 ms max) for all three operations. Specifically:
  - **heartbeat:** retried silently in the background using back-off. A single missed heartbeat attempt does not propagate to the caller; the session-timeout window on the broker side provides sufficient slack for the coordinator to restart before the session is considered dead. The retry continues until the coordinator responds or the `Consumer` handle is closed.
  - **commit-offset:** retried with back-off. The call does not return to the caller until a successful acknowledgement is received or `Consumer.close()` is called, to avoid silent offset-commit loss.
  - **fetch-assignment:** retried with back-off. The call blocks until a valid assignment response is received or `Consumer.close()` is called.

  In all cases, the retry loop checks the `closed` flag between attempts (see `Consumer.close()` contract below) and exits immediately if the handle has been closed.
- **`Consumer.close()` during retry loop:** `Consumer.close()` sets a volatile `closed` flag. Any retry loop in the client (re-subscription, heartbeat retry, commit-offset retry, fetch-assignment retry) checks this flag **between retry attempts** — that is, after the current in-flight attempt returns (or times out) but before sleeping or issuing the next attempt. When the flag is detected as set, the loop exits immediately without issuing another attempt, and the blocked caller receives `ConsumerClosedException` (unchecked, extends `RuntimeException`). The in-flight network call is not interrupted mid-flight; the `closed` check is at attempt boundaries only. After `close()` returns, all subsequent method calls on the `Consumer` handle throw `ConsumerClosedException`.
- **Per-partition `nextPosition` during re-subscription:** the client's per-partition `nextPosition` values are **fully preserved** across re-subscription. The re-subscribe request carries the current `nextPosition` for each assigned partition, so consumption resumes exactly where it left off. Positions are never reset by the re-subscription path.
- The application-level `Consumer` handle remains valid throughout a coordinator outage (subject to the `close()` contract above); `poll()` calls block inside the re-subscription retry loop until re-subscription succeeds or the handle is closed.
- Assignments are reconstructed from scratch on the broker side after recovery.
- **Producer behavior during coordinator outage:** producers write directly to partition leaders, which are independent of the coordinator broker. Producer writes are **unaffected** by a coordinator outage. Only consumer group management (subscribe, heartbeat, commit-offset, assignment) is degraded.
- Known limitation: the coordinator is a SPOF for consumer group management; automatic coordinator failover is out of scope for this iteration.

### Truncation
- **Ownership:** truncation logic runs **exclusively on the coordinator broker**. The coordinator holds all data required to compute the truncation boundary — heartbeat liveness state and committed offsets — making cross-broker data access unnecessary. When the boundary advances for a given partition, the coordinator issues a `TRUNCATE_REQUEST` (`MessageTypes.TRUNCATE_REQUEST`) to the partition's current leader via `NettyMessagingService`. The partition leader applies the truncation on receipt. Non-coordinator partition brokers do not run truncation timers and do not issue truncation calls.
- **Trigger:** a periodic actor timer fires every `event-bridge.broker.truncation.interval-ms` (default **60,000 ms**) on the coordinator broker's actor. Each firing re-evaluates the truncation boundary for every partition and, for each partition where the boundary has advanced since the last truncation, issues a `TRUNCATE_REQUEST` to the relevant partition leader. The timer is implemented via `ActorControl.runAtFixedRate()` — no external scheduler is involved.
- Eligible set (per partition) = consumers whose last heartbeat was received within `event-bridge.coordinator.session-timeout-ms` (default 30,000 ms; the same timeout that governs consumer liveness for rebalance triggering) **and** who hold an assignment for that partition.
- Boundary = `min(committedPosition)` over the eligible set for that partition.
- Dead consumers (i.e., those outside the session-timeout window) are excluded from the min calculation.
- **No-consumer-alive behavior:** when the eligible set is empty for a partition, no truncation occurs for that partition and the log grows without bound until at least one consumer reconnects and begins committing offsets. This is **accepted behavior** for this iteration. There is no max-retention backstop. Operators must monitor partition log size via the existing `AtomixLogStorage` metrics and ensure at least one consumer remains active in steady state. A retention cap is a known future improvement.

### RAFT Replica Factor
- Configurable via `event-bridge.raft.replication-factor`; default **1** (suitable for single-node dev/test).
- Multi-node deployments set this to 3+ for fault tolerance.

### Client (`event-bridge-client`)
- `Consumer` handle tracks `nextPosition` per partition, initialized to **`-1`**
  - **`nextPosition = -1` semantics:** signals "start from the earliest available offset retained in the log" (i.e., the oldest record not yet truncated). It does **not** mean latest/tail. On the first poll the broker substitutes `nextPosition = -1` with the partition's current `retentionStart` position. Consumers that need tail-start semantics must explicitly pass the position returned by a `getLatestPosition()` call before their first `poll()` (see `getLatestPosition()` below).
- `poll()` iterates partitions in ascending ID order; on `REBALANCE_IN_PROGRESS` from any partition discards all collected events and throws `RebalanceInProgressException`.

- **`RebalanceInProgressException` contract:**
  - Type: **unchecked** (`extends RuntimeException`). Callers are not required to declare or catch it, but are expected to handle it at the `poll()` call site.
  - The `Consumer` handle **remains fully valid** after the exception is thrown. No re-subscribe or handle recreation is needed.
  - **Caller contract:** the caller must retry `poll()` after a short pause. A fixed retry delay of **1,000 ms** is recommended in the SDK Javadoc. The caller must not re-submit events that were discarded (i.e., those collected from earlier partitions in the same `poll()` sweep before the exception was raised); those positions will be re-delivered on the next successful `poll()` call because `nextPosition` is only advanced for a partition after events from that partition are successfully returned to the caller.

- **`Consumer.close()` contract:**
  - `close()` sets a volatile `closed` flag. All internal retry loops (re-subscription, heartbeat, commit-offset, fetch-assignment) check this flag between retry attempts. When detected, the loop exits and the blocked caller receives `ConsumerClosedException` (unchecked). The in-flight network call for the current attempt is not interrupted; the check occurs at attempt boundaries.
  - After `close()` returns, all subsequent method calls on the handle (`poll()`, `commitOffset()`, etc.) throw `ConsumerClosedException` immediately.
  - `close()` is idempotent: calling it multiple times has no additional effect.

- **`getLatestPosition(partitionId)`:**
  - HTTP mapping: `GET /v1/partitions/{partitionId}/latest-position`
  - Returns: a `LatestPositionResponse` JSON object containing a single `long` field `position`, representing the highest committed log position on that partition at the time of the call.
  - **Empty partition behavior:** if no records have ever been written to the partition, the endpoint returns `{ "position": 0 }`. Passing `0` as `nextPosition` on the first `poll()` is equivalent to starting at the tail of an empty log; no records will be returned until new ones are written.
  - The Java client exposes this as `EventBridgeClient.getLatestPosition(int partitionId): long`. There is no bulk variant; callers who need tail-start semantics across multiple partitions must call it once per assigned partition before their first `poll()`.

- **HTTP socket timeout** = `serverWaitMs + 5,000 ms`. The 5,000 ms slack is a fixed constant in the client (`EventBridgeClientConfig.POLL_TIMEOUT_SLACK_MS`) and is not user-configurable. It accounts for network round-trip and broker-side scheduling jitter. The calling application sets `serverWaitMs` via `ConsumerConfig`; the client computes and applies the socket timeout automatically.
- **Worst-case poll latency per `poll()` call:** `N × T`, where N = number of partitions assigned to this consumer and T = `serverWaitMs`. This arises because partitions are polled sequentially and each idle partition parks for up to `serverWaitMs` before returning an empty response. Applications with strict latency budgets should reduce `serverWaitMs` or accept that assigned partition count directly multiplies tail latency.

## Design Decisions

### Summary of design decisions and assumptions

---

**Module structure:** Four sub-modules under a new top-level `event-bridge/` directory:

| Sub-module | Scope |
|---|---|
| `event-bridge-core` | Shared domain model, SBE-encoded protocol types, RAFT command/event records, and shared value objects (positions, group IDs, etc.). |
| `event-bridge-broker` | Broker-side actor state machines, partition assignment logic, offset tracking, snapshot encoding/decoding, and RAFT state transitions. |
| `event-bridge-gateway` | HTTP layer that translates REST requests into broker commands and streams responses back to callers; communicates with the broker in-process via `Actor.call()` / `ActorFuture`-based dispatch (see "In-process gateway–broker communication" below). |
| `event-bridge-client` | Java client library exposing `Producer`, `Consumer`, and `AdminClient` APIs over the HTTP gateway. |

---

**API surface (machine-to-machine, no UI):**

**`POST /events/{partitionId}`** — Publish a batch of events.

- Request body:
  ```json
  {
    "events": [
      { "key": "<string|null>", "payload": "<base64-encoded bytes>" }
    ]
  }
  ```
- Response `200 OK`:
  ```json
  {
    "results": [
      { "index": 0, "position": 1042 }
    ]
  }
  ```
  One entry per input event, in order. A single RAFT entry is written per call. The write is **all-or-nothing**: either every event in the batch is appended and every position is returned, or no event is appended and an error response is returned. There is no partial-success result shape.
- Error responses:
  - `404 Not Found` — `partitionId` does not exist.
  - `400 Bad Request` — batch exceeds the maximum allowed size (default: 1 000 events per request; configurable via `event-bridge.publish.maxBatchSize`; valid range `[1, 10000]`). Body: `{ "error": "BATCH_TOO_LARGE", "maxAllowed": <int> }`.
  - `400 Bad Request` — a single event payload exceeds the maximum allowed byte size, or the total encoded batch body exceeds the maximum allowed byte size (see "Byte-size limits on event payloads" below). Body: `{ "error": "PAYLOAD_TOO_LARGE", "maxEventBytes": <int>, "maxBatchBytes": <int> }`.
  - `503 Service Unavailable` — RAFT leader unavailable or write timed out (leader election in progress, quorum lost, etc.). Clients should retry with backoff.

---

**`POST /consumers/{groupId}/{consumerId}/subscribe`** — Register consumer and trigger immediate rebalance.

- Request body: empty (consumer identity is in the path).
- Response `200 OK`:
  ```json
  {
    "assignedPartitions": [0, 2],
    "generation": 7
  }
  ```
  `generation` increments on every rebalance epoch; clients must include it in subsequent poll and commit calls to detect stale state. The rebalance completes synchronously before the response is returned.
- **Duplicate `consumerId` within a group:** If `subscribe` is called with a `groupId`/`consumerId` pair that is already registered as a live consumer in the broker, the call is treated as a **reconnect**: the prior registration is superseded, the consumer's heartbeat deadline is reset, and a new rebalance is triggered (since the consumer's assigned partitions may need to be redistributed). This allows a crashed-and-restarted consumer to rejoin without the broker waiting for the old heartbeat timeout to expire. The superseded registration's committed offset is preserved (see "Dead consumer re-subscribing" below).
- **Concurrent subscribe behavior:** A per-group rebalance lock is held for the duration of the synchronous rebalance. If a second consumer calls `subscribe` for the same group while a rebalance is in progress, that call is queued and will execute as a new rebalance immediately after the first completes. If the queued call has not been able to start within `event-bridge.consumer.subscribeTimeoutMs` (default: `10000`; valid range `[1000, 60000]`) of the original request arriving, it is rejected with `503 Service Unavailable` and body `{ "error": "REBALANCE_TIMEOUT" }`.

---

**`GET /events/{partitionId}/poll`** — Long-poll pull.

All parameters are **query parameters** (not a request body). A GET with a request body is non-standard and is not used here.

- Query parameters:
  - `fromPosition` (long, required) — the log position from which to start returning records. Use `-1` as a sentinel for "oldest retained position at time of request"; the broker resolves `-1` to the current `oldestAvailablePosition` for the partition at the moment the poll is processed. Any other negative value is rejected with `400 Bad Request`.
  - `generation` (long, required) — the generation value returned by the most recent `subscribe` call. **Stale-generation is checked once, at request-arrival time.** If the generation does not match the broker's current generation for this consumer's group at that moment, the request is rejected immediately with `409 Conflict` and body `{ "error": "STALE_GENERATION", "currentGeneration": <long> }`. This is the gateway's enforcement point for stale-generation rejection. A rebalance that fires *after* the request has been accepted and is parked does not retroactively convert the response to `409`; see "Generation change during an active long-poll wait" below.
  - `maxRecords` (int, default `100`)
  - `serverWaitMs` (int, default `1000`, clamped to `[0, 30000]`)
- Response `200 OK` (records available, or wait elapsed with zero records):
  ```json
  {
    "records": [
      { "position": 1042, "key": "<string|null>", "payload": "<base64-encoded bytes>" }
    ],
    "nextPosition": 1043,
    "generation": 7
  }
  ```
  An empty `records` array with `200` is the normal response when `serverWaitMs` elapses with no new records. `204 No Content` is not used — clients always parse the same envelope. A changed `generation` in the response (relative to the `generation` query parameter) signals that a rebalance occurred during the wait window; the client must re-subscribe before the next poll.
- Response `400 Bad Request` — `fromPosition` is below the current oldest retained position, or `fromPosition` is a negative value other than `-1`. Body for truncated position: `{ "error": "POSITION_TRUNCATED", "oldestAvailablePosition": <long> }`.
- Response `409 Conflict` — `generation` did not match the broker's current generation at request-arrival time (see above).

---

**`POST /events/{partitionId}/commit`** — Idempotent offset commit.

- Request body:
  ```json
  {
    "position": 1042,
    "consumerId": "<string>",
    "groupId": "<string>",
    "generation": 7
  }
  ```
  A commit with a stale `generation` is rejected with `409 Conflict` (not silently accepted) to prevent a rebooted consumer from poisoning the low-watermark with an old offset.
- **Commit to an unassigned partition:** If `partitionId` exists but the consumer's current generation does not assign that partition to the committing consumer, the request is rejected with `403 Forbidden` and body `{ "error": "PARTITION_NOT_ASSIGNED" }`. This is checked after generation validation; a stale generation returns `409` before `403` is considered.
- **Commit with a truncated position:** If `position` falls at or below the current truncation low-watermark, the commit succeeds silently with `204 No Content`. The position has already been superseded; accepting it is safe and preserves idempotent behavior without surfacing a spurious error to the client.
- Response `204 No Content` on success (including the truncated-position case above).
- Response `403 Forbidden` — `partitionId` is not assigned to this consumer under its current generation.
- Response `409 Conflict` — stale `generation`.
- Response `404 Not Found` — `partitionId` does not exist.

---

**`POST /consumers/{groupId}/{consumerId}/heartbeat`** — Liveness signal.

- Request body: empty.
- Response `200 OK` while consumer is live:
  ```json
  { "generation": 7 }
  ```
  A changed `generation` in the response is the signal that a rebalance has occurred since the last heartbeat; the client must re-subscribe.
- Response `404 Not Found` if the timeout has already elapsed and the consumer has been evicted. The client must re-subscribe before polling.

---

**Key behavioral decisions:**

**Partition assignment algorithm:** Sticky round-robin. On each rebalance, existing consumer→partition assignments are preserved where possible; only the minimum number of partition slots are moved to achieve an even distribution across the new consumer set. Formally: sort consumers by consumer ID using **lexicographic Unicode code-point order** (equivalent to `java.lang.String.compareTo`, which compares `char` values as unsigned 16-bit code units); assign unowned partitions to consumers with below-average slot counts; never move a partition that is already owned by an active consumer unless required for balance. This sort contract must be applied identically on every broker restart to guarantee deterministic assignment; implementations must not use locale-sensitive collation. This is equivalent to Kafka's sticky assignor behavior.

**Rebalance trigger on heartbeat timeout:** When a consumer's heartbeat timeout elapses, the broker actor **immediately and proactively** triggers a rebalance for every consumer group that had partitions assigned to the dead consumer. It does not wait for another consumer to call `subscribe` or `heartbeat`. This ensures that unowned partitions are redistributed promptly after a consumer crash, without requiring any surviving consumer to take action first.

**Parked long-poll futures during a rebalance:** When a heartbeat-triggered rebalance fires and the broker actor processes it, any `ActorFuture` completions parked for a partition that is being reassigned are **completed immediately** with the current (new) generation value and an empty `records` array. This causes the waiting poll response to return `200 OK` with `"records": []` and the updated `generation`, signaling to the client that a rebalance has occurred and that it must re-subscribe before the next poll. Parked futures for partitions that are *not* affected by the rebalance (i.e., the partition remains assigned to the same consumer group member) are left parked and continue waiting normally.

**Generation change during an active long-poll wait:** Stale-generation is validated **once, at request-arrival time**. If the generation matches at arrival, the poll is accepted and parked. If a rebalance fires during the wait window, the broker completes the parked future with `200 OK` and the new generation in the response body (as described above). The gateway does **not** retroactively convert this to `409 Conflict`. The client detects the rebalance by comparing the response `generation` to the `generation` it sent, and re-subscribes accordingly.

**Initial `generation` value:** The first successful `subscribe` call for a group that has never existed returns `generation: 1`. The generation counter starts at `0` internally and is incremented by one on every rebalance, including the initial subscribe. A `generation` value of `0` is never returned to clients; it is the broker's internal pre-existence sentinel. All examples using `generation: 7` reflect a group that has rebalanced seven times.

**Retention policy:** Retention is record-count-based. Each partition retains the last *N* records, where *N* defaults to `1 000 000` and is configurable per partition via `event-bridge.retention.maxRecordsPerPartition`. The oldest retained position advances when the partition log exceeds *N* records. A `GET /events/{partitionId}/poll` request with `fromPosition` below the current oldest retained position returns `400 Bad Request` with body `{ "error": "POSITION_TRUNCATED", "oldestAvailablePosition": <long> }`. `fromPosition = -1` is a sentinel for "oldest retained position at time of request" (see poll parameter table above).

**Truncation low-watermark and dead consumers:** The broker tracks a per-partition *truncation minimum* — the lowest committed offset across all live consumers in all groups reading that partition. The log may only truncate records at or below this low-watermark. A consumer is considered dead when its heartbeat timeout elapses. Dead consumers are excluded from the truncation minimum calculation (i.e., their last committed offset no longer blocks log advancement), preventing a stalled or crashed consumer from pinning the log indefinitely.

**Initial state of the truncation low-watermark:** Before any consumer in any group has ever committed an offset on a partition, there are no live-consumer offset entries and therefore no truncation minimum imposed by consumer tracking. In this state the truncation low-watermark is **unbounded** — the log is free to truncate based solely on `maxRecordsPerPartition`. Once at least one consumer commits an offset, the per-partition minimum is recomputed from that commit onward.

**Dead consumer re-subscribing:** When a consumer that was previously evicted (heartbeat timeout elapsed) calls `subscribe` again — or when a live consumer calls `subscribe` and supersedes a prior registration — the broker **restores the consumer's last committed offset** as its floor for the truncation low-watermark calculation. The offset is not reset to zero or treated as absent. This means a rejoining consumer resumes consumption from where it left off (subject to truncation), and its last committed offset immediately re-enters the per-partition minimum calculation. If the consumer has no prior committed offset on record (e.g., it is genuinely new, or was evicted before ever committing), it is treated as a fresh consumer with no committed offset, as in the initial unbounded-watermark case described above.

**Long-poll actor parking:** Long-poll requests are implemented using Zeebe's `Actor`/`ActorFuture` framework. When no records are available for a given partition, the poll actor suspends itself by scheduling a timeout `ActorFuture` for `serverWaitMs`; no OS thread is blocked. **Early wakeup:** the broker maintains a per-partition list of parked `ActorFuture` completions. When the publishing actor appends new records to a partition, it iterates that partition's parked-future list and completes each future immediately, which re-schedules each waiting poll actor before its timeout fires. The parked-future list is owned and mutated exclusively by the broker actor to avoid cross-thread contention.

**In-process gateway–broker communication:** The gateway communicates with the broker using **`Actor.call()` / `ActorFuture`-based dispatch** — the standard Zeebe actor-to-actor mechanism. The gateway submits a command by calling `brokerActor.call(command)`, which enqueues a task onto the broker actor's internal work queue and returns an `ActorFuture<Result>`. The broker actor processes the task on its own thread and completes the future; the gateway actor receives the result asynchronously when the future fires. This means: (a) the gateway never blocks a thread waiting for a broker response; (b) backpressure is natural — the broker actor's queue depth limits in-flight commands; (c) errors are propagated as exceptional future completions and mapped to HTTP error responses by the gateway. No shared mutable state is accessed outside of actor boundaries.

**`RebalanceInProgressException` recovery contract:** If a rebalance begins while a `Consumer.poll()` iteration is in progress, the client throws `RebalanceInProgressException` and discards all partial results from that call. The client **must call `subscribe()` again** before issuing another `poll()`. A bare `poll()` retry without re-subscribing will return `409 Conflict` from the gateway (stale `generation` query parameter). The client library wraps this cycle automatically when using the high-level `Consumer` API with a registered `RebalanceListener`.

**Offset durability trade-off (accepted):** Committed offsets are persisted only via RAFT snapshots. Snapshots are triggered every `event-bridge.raft.snapshotIntervalEntries` RAFT entries (default: `1000`; valid range: `[100, 100000]`; values outside this range are rejected at startup). A broker crash before the next snapshot may cause up to `snapshotIntervalEntries - 1` batches of commit progress to be lost, requiring consumers to re-consume and re-commit those records after recovery. This is an explicit accepted trade-off: adding a WAL/journal entry per commit would halve publish throughput at the target load. Consumers are expected to implement idempotent processing to tolerate redelivery.

**Heartbeat timeout configuration:** The timeout is a **global** broker configuration, not per-group. Configuration key: `event-bridge.consumer.heartbeatTimeoutMs`. Default: `5000`. Valid range: `[1000, 60000]`. Values outside this range are rejected at startup with a descriptive error.

**Byte-size limits on event payloads:** The publish endpoint enforces both a record-count bound and byte-size bounds. The maximum encoded size of a single event payload (the base64-decoded bytes of the `payload` field) is `1 MB` (1 048 576 bytes), configurable via `event-bridge.publish.maxEventBytes` (valid range `[1, 16777216]`). The maximum total encoded body size of an entire publish request is `10 MB` (10 485 760 bytes), configurable via `event-bridge.publish.maxBatchBytes` (valid range `[1, 67108864]`). Both limits are enforced before the RAFT write is attempted. A violation returns `400 Bad Request` with body `{ "error": "PAYLOAD_TOO_LARGE", "maxEventBytes": <int>, "maxBatchBytes": <int> }`. Byte-size enforcement at the HTTP transport layer (e.g., a reverse proxy limit) is out of scope for this module but is expected to be configured at the infrastructure level.

**String field constraints for `groupId`, `consumerId`, and event `key`:**
- `groupId`: 1–256 characters; allowed characters are Unicode letters, digits, hyphens (`-`), underscores (`_`), and dots (`.`). Empty string and strings exceeding 256 characters are rejected at subscribe and commit time with `400 Bad Request` and body `{ "error": "INVALID_GROUP_ID" }`.
- `consumerId`: 1–256 characters; same allowed character set as `groupId`. Violations return `400 Bad Request` with body `{ "error": "INVALID_CONSUMER_ID" }`.
- Event `key`: nullable. When present, maximum 1 024 characters; no character set restriction (any valid UTF-8 string). A `key` exceeding 1 024 characters is rejected with `400 Bad Request` and body `{ "error": "KEY_TOO_LONG", "maxLength": 1024 }`. A `null` key is stored and returned as `null`; the field may be omitted from the JSON object, which is equivalent to `null`.
- These constraints are enforced by the gateway before any broker interaction. The broker does not re-validate them.

**HTTP success codes summary:**

| Endpoint | Success code |
|---|---|
| `POST /events/{partitionId}` | `200 OK` with body |
| `POST /consumers/.../subscribe` | `200 OK` with body |
| `GET /events/{partitionId}/poll` | `200 OK` with body (including empty-records case) |
| `POST /events/{partitionId}/commit` | `204 No Content` |
| `POST /consumers/.../heartbeat` | `200 OK` with body |

**Out of scope (confirmed):** auth, topic routing, consumer-driven partition assignment, coordinator HA/failover, schema registry.

## Technical Analysis

Now I have everything needed to produce a complete, grounded assessment.

---

# Technical Feasibility Assessment: Event Bridge

## Complexity: **HIGH**

This requires building four interconnected Maven modules that deeply integrate with Zeebe's internal infrastructure (RAFT, LogStream, ActorScheduler, SBE, SWIM). Several infrastructure components are more tightly coupled to the Zeebe protocol than the spec assumes, introducing concrete blockers that must be resolved before implementation.

---

## Findings from Codebase Exploration

### Infrastructure confirmed reusable
| Component | Artifact | Location |
|---|---|---|
| RAFT partition | `zeebe-atomix-cluster` | `zeebe/atomix/cluster/…/raft/partition/RaftPartition.java` |
| Cluster + SWIM | `zeebe-atomix-cluster` | `io.atomix.cluster.AtomixCluster` |
| Netty transport | `zeebe-atomix-cluster` | `io.atomix.cluster.messaging.impl.NettyMessagingService` |
| LogStream | `zeebe-logstreams` | `io.camunda.zeebe.logstreams.log.LogStream` |
| Sequencer | `zeebe-logstreams` | `io.camunda.zeebe.logstreams.impl.log.Sequencer` |
| ActorScheduler | `zeebe-scheduler` | `io.camunda.zeebe.scheduler.ActorScheduler` |
| BrokerInfo encoding | `zeebe-protocol-impl` | `io.camunda.zeebe.protocol.impl.encoding.BrokerInfo` |
| SBE tooling | `exec-maven-plugin` + `sbe-tool:1.37.1` | `parent/pom.xml` |
| Dist/entry-point pattern | `appassembler-maven-plugin` | `dist/pom.xml` |

### Topology dissemination (confirmed pattern)
`TopologyManagerImpl` writes a Base64-encoded `BrokerInfo` into `ClusterMember.properties()` under a well-known key. SWIM propagates property changes via `GroupMembershipEvent.Type.METADATA_CHANGED`. The gateway would consume these events and maintain an in-memory `partitionId → leader address` map. This pattern is directly cloneable.

### Long-poll wake mechanism (confirmed)
`LogStream.registerRecordAvailableListener(LogRecordAwaiter)` fires `onRecordAvailable()` when a new entry is written. Combined with `actor.schedule(Duration, Runnable)` for the timeout timer and `actor.submit(Runnable)` to funnel the callback back into the actor, this satisfies the spec's requirement without blocking CPU threads.

### RAFT snapshot mechanism (spec mismatch)
The spec says "written directly into the RAFT `RaftSnapshotWriter` payload." **No such class exists.** The actual API is `TransientSnapshot.take(Consumer<Path>)` — you receive a directory path and write files into it. The offset state would be serialized as an SBE file within that directory. Functionally equivalent but requires adapting the spec's stated API.

### SBE schema reuse
Existing schemas: `protocol.xml`, `broker-protocol.xml`, `stream-protocol.xml`. The generation pipeline (parent POM `exec-maven-plugin` execution `generate-sbe`, bound to `generate-sources`) is fully established and can be replicated as-is in `event-bridge-core/pom.xml`.

---

## Blockers & Risks

### 🔴 BLOCKER 1: `LogAppendEntry` is tightly coupled to the Zeebe protocol
`LogStreamWriter.tryWrite()` accepts `List<LogAppendEntry>`, and `LogAppendEntry` **requires** both `RecordMetadata` (with `ValueType`, `Intent`, `RecordType`) and `UnifiedRecordValue` (msgpack-serialized Zeebe record). These are Zeebe-protocol types with no raw-bytes variant.

**Impact:** Cannot call the `Sequencer` (which is `LogStreamWriter`) with raw event bytes without bridging this gap.

**Resolution path:** Implement `EventRecordValue implements UnifiedRecordValue` that wraps a raw `DirectBuffer`/`byte[]` and returns serialized length, and a stub `RecordMetadata` with neutral type markers (e.g., `ValueType.NULL`). This adds ~2 classes but is non-trivial — `UnifiedRecordValue` has a msgpack serialization contract that must be satisfied. Alternatively, define a completely new low-level write path that bypasses `LogAppendEntry` entirely and writes to `LogStorage` directly, but this bypasses the `Sequencer`'s position-assignment logic. The former approach (stub `UnifiedRecordValue`) is recommended and feasible.

### 🔴 BLOCKER 2: `AtomixLogStorage` lives in `zeebe-broker`, not a reusable library
`AtomixLogStorage` (the bridge between RAFT's `ZeebeLogAppender` and the `LogStorage` interface) is in `io.camunda.zeebe.broker.logstreams`. Depending on `zeebe-broker` from `event-bridge-broker` would pull in the entire Zeebe engine, exporter framework, and stream processor.

**Resolution path:** Implement `EventBridgeLogStorage implements LogStorage, RaftCommitListener` in `event-bridge-broker`, mirroring `AtomixLogStorage`'s ~200-line implementation. This is copy-adapt work rather than novel code, but it must be maintained separately.

### 🟡 RISK 1: LogStream truncation has no high-level API
`LogStream` exposes no `truncate(position)` method. The underlying journal exposes `compact(index)` but the path from a log *position* to a RAFT *index* requires `AtomixLogStorageReader`. The spec's truncation requirement needs to go through the journal directly, bypassing the `LogStream` abstraction.

### 🟡 RISK 2: SBE variable-length repeated groups
`PublishBatchRequest` (array of payloads), `PollResponse` (array of `{position, payload}`), and `OffsetSnapshotPayload` (flat triples) all require SBE `<group>` elements, which generate stateful multi-step encoder/decoder code. The existing schemas (`protocol.xml` uses `<group>` for `ExporterState`) confirm this is done in the codebase, but it's more complex than scalar fields.

### 🟡 RISK 3: Long-poll callback thread safety
`LogRecordAwaiter.onRecordAvailable()` fires on the LogStream's internal thread, not the actor thread. The poll actor must re-enter via `actor.submit(Runnable)` to avoid data races on actor state. The timer cancellation (if data arrives before timeout expires) and callback deregistration from `LogStream` must be carefully sequenced.

### 🟡 RISK 4: `ConsumerGroupCoordinator` on Broker-0 is novel code
Nothing in the existing broker resembles a consumer group coordinator. Heartbeat timeout detection (via `actor.runAtFixedRate`), stable round-robin rebalance with churn-minimization, and `REBALANCE_IN_PROGRESS` notification via poll responses are all bespoke logic with no existing analog to mirror.

### 🟢 LOW RISK: Spring Boot 4.0.3
Spring MVC controllers, `@SpringBootConfiguration`, `@RestController`, `@RequestMapping` all work unchanged. The `MainSupport.createDefaultApplicationBuilder()` pattern in `dist/` is directly reusable.

---

## Affected Files / Modules

### Existing files to modify (3)
| File | Change |
|---|---|
| `pom.xml` (root) | Add `<module>event-bridge</module>` to reactor |
| `dist/pom.xml` | Add `StandaloneEventBridge` program entry in `appassembler`; add `event-bridge-gateway` + `event-bridge-broker` deps |
| `bom/pom.xml` | Add managed version entries for new artifacts (optional but conventional) |

### New modules and key files (~115 new files)

**`event-bridge/pom.xml`** — aggregator with 4 submodules

**`event-bridge/event-bridge-core/`** (~22 files)
- SBE schemas: `PublishBatchRequest.xml`, `PublishBatchResponse.xml`, `PollRequest.xml`, `PollResponse.xml`, `CommitOffsetRequest.xml`, `CommitOffsetResponse.xml`, `HeartbeatRequest.xml`, `HeartbeatResponse.xml`, `SubscribeRequest.xml`, `SubscribeResponse.xml`, `OffsetSnapshotPayload.xml` (11 schema files)
- `EventRecordValue.java` — raw-bytes `UnifiedRecordValue` shim
- `EventLogAppendEntry.java` — `LogAppendEntry` wrapping raw bytes
- `EventBridgeProperties.java` — `@ConfigurationProperties`
- `EventBridgeException.java` + subclasses

**`event-bridge/event-bridge-broker/`** (~35 files)
- `EventBridgeLogStorage.java` — `LogStorage + RaftCommitListener` (mirrors `AtomixLogStorage`)
- `EventBridgePartition.java` — actor bootstrapping RAFT + LogStream per partition
- `PublishActor.java` — sequencing actor; calls `LogStreamWriter.tryWrite(List<EventLogAppendEntry>)`
- `PollActor.java` — reads from `LogStreamReader`, implements long-poll via `LogRecordAwaiter` + `schedule()`
- `OffsetStore.java` — in-memory `Map<groupId, Map<consumerId, Map<partitionId, position>>>`
- `ConsumerGroupCoordinator.java` — subscribe/heartbeat/rebalance; runs on Broker-0 only
- `HeartbeatMonitor.java` — `actor.runAtFixedRate()` scan for dead consumers
- `RebalanceManager.java` — stable round-robin assignment algorithm
- `SnapshotManager.java` — entry counter, `TransientSnapshot.take(path)`, SBE serialize/deserialize
- `TopologyBroadcaster.java` — writes leader state to SWIM member `Properties`
- `BrokerRequestDispatcher.java` — receives SBE messages from gateway via `MessagingService`; routes to actors
- `EventBridgeBroker.java` — Spring `@Component` lifecycle + `ActorScheduler` setup
- Config/factory classes, startup steps

**`event-bridge/event-bridge-gateway/`** (~30 files)
- `PublishController.java`, `PollController.java`, `CommitController.java`, `SubscribeController.java`, `HeartbeatController.java`
- Request/response DTOs (10 classes for all HTTP bodies)
- `GlobalExceptionHandler.java` (`@ControllerAdvice`)
- `TopologyService.java` — SWIM gossip consumer; `partitionId → MemberId` map
- `BrokerRequestRouter.java` — 4-attempt retry loop; `MessagingService.sendAndReceive()`
- `SbeCodec.java` — SBE encode/decode for all message types
- `EventBridgeGatewayApplication.java` — `@SpringBootConfiguration`
- `GatewayModuleConfiguration.java`

**`event-bridge/event-bridge-client/`** (~18 files)
- `EventBridgeClient.java` — `publishBatch()`, `subscribe()`
- `Consumer.java` — `poll()`, `commitOffset()`, `sendHeartbeat()`; internal `Map<partitionId, nextPosition>`
- `Event.java` — `{long position, int partitionId, byte[] payload}`
- Exception hierarchy: `EventBridgeException`, `CoordinatorUnavailableException`, `RebalanceInProgressException`, `ConsumerNotRegisteredException`
- `EventBridgeClientConfig.java`
- HTTP model classes (request/response POJOs)

**`dist/src/main/java/io/camunda/application/`** (~2 new files)
- `StandaloneEventBridge.java`
- `EventBridgeModuleConfiguration.java`

**Tests** (~20–25 test classes)
- Unit tests: `PublishActorTest`, `PollActorTest`, `ConsumerGroupCoordinatorTest`, `RebalanceManagerTest`, `OffsetStoreTest`, `TopologyServiceTest`, `BrokerRequestRouterTest`
- Controller tests (MockMvc): one per controller
- Integration test: `StandaloneEventBridgeIT`

---

## Implementation Strategy

**Phase 1 — `event-bridge-core`**: Define all SBE schemas + generate code. Implement `EventRecordValue` shim (Blocker 1 resolution). Define shared config properties.

**Phase 2 — `event-bridge-broker`**: Build `EventBridgeLogStorage` (Blocker 2 resolution). Establish `RaftPartition` bootstrap via `RaftPartitionFactory` pattern. Implement `PublishActor` + `PollActor` (long-poll). Implement `OffsetStore` + `SnapshotManager` (using `TransientSnapshot` path). Implement `ConsumerGroupCoordinator` with heartbeat + round-robin rebalance. Wire `TopologyBroadcaster` into SWIM `Properties`.

**Phase 3 — `event-bridge-gateway`**: Stand up Spring MVC. Implement `TopologyService` consuming SWIM gossip. Implement `BrokerRequestRouter` with 4-attempt retry over `MessagingService`. Wire all controllers.

**Phase 4 — `event-bridge-client`**: Implement HTTP client, `Consumer` handle with position tracking, `poll()` rebalance-discard semantics, all exception types.

**Phase 5 — `dist`**: `StandaloneEventBridge` entry point combining gateway + all broker partitions in one JVM. Register in `appassembler`.

---

## Estimated Scope Summary

| Category | Count |
|---|---|
| Existing files to modify | 3 |
| New SBE schema files | 11 |
| New Java source files | ~100 |
| New test classes | ~25 |
| **Total files** | **~140** |

The implementation is well-grounded in the codebase — the RAFT bootstrap, SWIM topology gossip, Netty messaging, LogStream write/read, and ActorScheduler patterns are all clearly established and directly reusable. The three items requiring custom bridging code (LogAppendEntry shim, custom LogStorage, TransientSnapshot-based snapshots instead of a non-existent RaftSnapshotWriter) are each ~100–200 lines of focused implementation work, not architectural unknowns. The genuinely novel component is `ConsumerGroupCoordinator` on Broker-0, which has no analog in the codebase and carries the most implementation risk.
