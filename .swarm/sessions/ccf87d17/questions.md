# Plan Questions

> Session: ccf87d17
> Request: I want to implement an "Event Bridge" that receives a batch of events and stores them in an append-o...
> Generated: 2026-03-18T20:12:31.665Z

<!-- Fill in your answers below each question. Leave blank to let the agent decide. -->
<!-- When done, run: swarm plan --resume -->

## PM / Requirements (`plan-clarify`)

### Q1
> The `LogStreamWriter.tryWrite()` returns `Either<WriteFailure, Long>` — should the Event Bridge gateway propagate a **write failure (backpressure)** back to the publishing client as an explicit error code, or should it silently retry until success?

**Answer:** It should retry 3 times, and if it still does not succeed, then propogate the failure to the client.


### Q2
> The existing `FlowControl` / `RequestLimiter` in `zeebe-logstreams` is tuned for Zeebe's command semantics. Should the Event Bridge **reuse this flow control**, replace it with a simpler one, or make it configurable?

**Answer:** Implement with its own FlowControl, but can be simpler.


### Q3
> `LogStreamWriter.tryWrite()` accepts a `sourcePosition` (the position of the source record that caused this write). For the Event Bridge, there is no upstream source record — what value should be used here, or should the Sequencer be adapted to make this optional?

**Answer:** it should be -1


### Q4
> The existing `LoggedEvent` / `LogAppendEntry` format carries Zeebe-specific metadata fields (value type, intent, record type, etc.). Should the Event Bridge **define its own entry format** (pure binary payload + minimal header), or reuse the existing SBE-based framing as-is?

**Answer:** it should use the SBE-based framing as is.


## Engineering (`plan-eng-clarify`)

### Q1
> Where should the Event Bridge modules live in the monorepo? As a new top-level directory (e.g., `event-bridge/`) analogous to `zeebe/` and `operate/`, or as sub-modules inside an existing parent (e.g., `gateways/`)?

**Answer:** a new top level directory


### Q2
> What Maven `groupId` and `artifactId` naming should be used for the gateway and broker modules (e.g., `io.camunda:event-bridge-gateway` and `io.camunda:event-bridge-broker`)? And what Java base package (e.g., `io.camunda.eventbridge`)?

**Answer:** yes


### Q3
> Should the distribution be added as a new entry-point inside the existing `dist/` module (alongside `StandaloneBroker`, etc.), or should it be a completely separate Maven module that produces its own tarball?


**Answer:** it should produce it own distribution

> ---
> **API / Transport Protocol**
### Q4
> What transport protocol should the gateway expose for the publish and subscribe APIs — gRPC, HTTP/REST, or a custom TCP framing? Should the client library wrap a gRPC stub, similar to how `zeebe-client` wraps gRPC?

**Answer:** HTTP REST, no gRPC


### Q5
> For the pull-based subscription API, what is the exact request/response contract? Specifically:
>    - Does the consumer specify: `{ consumerGroupId, partitionId, maxRecords, timeoutMs }`?
>    - Does the response include: `{ records: [{position, data}], nextCursor }`?
>    - Is the cursor opaque, or is it an explicit log position (`long`)?

**Answer:** yes, all of that. it should be the explicit log position.


### Q6
> Does publishing a batch block until the events are **committed** (replicated to a RAFT quorum), or does it return as soon as events are **written locally** to the leader's log?

**Answer:** once committed


### Q7
> What is the maximum batch size for publish (in number of events or bytes)? Should the existing `FlowControl` / `Sequencer.canWriteEvents()` limits apply directly, or do new limits need to be defined?
> ---
> **Event Format & Schema**

**Answer:** max 1000 events, or 4 MB,


### Q8
> Each event is "a binary (not JSON)" — but what is the envelope? Does the Event Bridge need to assign and expose any of the following, or are they purely internal?
>    - A **partition-scoped position** (like Zeebe's log position)?
>    - A **key** or **event ID**?
>    - A **timestamp**?
>    - **Headers/metadata** (e.g., source, content-type)?

**Answer:** they are internally.


### Q9
> Should the on-wire binary format for events stored in the log reuse the existing SBE `RecordMetadata` framing from `zeebe/protocol`, or should a new, simpler framing be defined (e.g., just `position | length | payload`)?

**Answer:** it should reuse the existing SBE framing


### Q10
> Should the RAFT log entry format reuse the existing `ApplicationEntry` / `SequencedBatch` / `SequencedBatchSerializer` path from `zeebe-logstreams`, or should a parallel serialization path be created for Event Bridge entries?
> ---
> **Partitioning & Routing**

**Answer:** yes, reuse


### Q11
> Is the partition count static (set at cluster bootstrap) or dynamic? What is the default? Is there a replication factor per partition, and does it follow the same rules as Zeebe's (`1 ≤ replicationFactor ≤ clusterSize`)?

**Answer:** static set on cluster level, there is a replication factor, it should behave the same as in zeebe


### Q12
> The spec says "the gateway routes published events to the broker that is leader for the respective partition." Should the topology dissemination mechanism reuse the existing SWIM-based `BrokerInfo` / `TopologyManagerImpl` path, or should a new Event Bridge-specific topology gossip be built? If reusing, does the existing `BrokerTopologyManagerImpl` in `zeebe/broker-client` get extended, or is a new equivalent created?

**Answer:** it should reuse, but create its own broker-client for the event bridge puposes


### Q13
> If the leader for a partition is unknown or has changed (mid-request), how should the gateway handle it — fail fast, retry with backoff, or return an explicit error to the client with a "not leader" signal?
> ---
> **Broker Architecture & RAFT Integration**

**Answer:** return current no leader, so that the client can retry


### Q14
> Should each Event Bridge broker partition own a dedicated `RaftPartition` / `RaftPartitionServer` (like Zeebe does), meaning a broker with 3 partitions runs 3 independent RAFT groups? Or should a single RAFT group manage all partitions on a broker?

**Answer:** like zeebe does


### Q15
> Should the Event Bridge broker reuse `AtomixLogStorage` as the `LogStorage` implementation, binding to a `ZeebeLogAppender` from `LeaderRole`? Or should a thinner custom `LogStorage` be written that talks directly to the journal?

**Answer:** reuse


### Q16
> Should the `Sequencer` class from `zeebe-logstreams` be reused as-is for sequencing incoming events before they go to `LogStorage`/RAFT? Or should a new sequencer be written (e.g., one that is also an `Actor` rather than using `ReentrantLock`)?

**Answer:** copy the sequence and adjust as neccessary and simplify


### Q17
> When a broker transitions from leader to follower (or vice versa) via `RaftRoleChangeListener.onNewRole()`, what exactly should happen to in-flight publish requests — should they be failed immediately, or drained first?
> ---
> **Consumer Group Management**

**Answer:** they can fail


### Q18
> What exactly does "Broker-0 is the coordinator of a consumer group" mean in practice? What responsibilities does coordination involve:
>     - Assigning which consumer in the group reads from which partition?
>     - Tracking group membership (who is alive based on heartbeats)?
>     - Committing/persisting consumer offsets on behalf of the group?
>     - All of the above?

**Answer:** all of the above


### Q19
> How does a consumer **join** a consumer group? Is there an explicit Join RPC to Broker-0, or does the first heartbeat/fetch implicitly register membership?

**Answer:** there should be an explicit join rpc


### Q20
> Can a single consumer in a group consume from **multiple partitions** simultaneously (as in Kafka), or is the model one consumer ↔ one partition strictly?

**Answer:** can consume from multiple partitions


### Q21
> When a consumer stops sending heartbeats (session timeout), what should happen?
>     - Is the consumer's partition assignment released and reassigned to another member of the group?
>     - Or does the partition simply become "unassigned" until a consumer rejoins?
>     - What is the default heartbeat timeout?

**Answer:** it becomes unassigned, and then released and reassigned, take 5 seconds as heartbeat timeout


### Q22
> How are consumer group committed positions persisted? Options:
>     - In-memory only (lost on broker restart)?
>     - In a `ZeebeDb`/RocksDB column family on Broker-0?
>     - Replicated through RAFT so the coordinator itself is fault-tolerant?
> ---
> **Log Truncation**

**Answer:** replicated through RAFT


### Q23
> The spec states "the Log Stream can be truncated up to the consumed positions." What exactly triggers truncation — a periodic background task, explicit consumer ACK, or snapshot completion? And who decides: each partition's leader, or Broker-0?

**Answer:** let's do not truncate for now.


### Q24
> What happens when a consumer group falls arbitrarily far behind — is there a retention limit (time or bytes) beyond which the broker truncates regardless of consumer position? Or is the consumer expected to always keep up?

**Answer:** for now, keep them always


### Q25
> Should the log compaction use `LogCompactor` (from `zeebe/atomix`) directly, or should a new compaction driver be written that is aware of consumer group positions rather than exporter positions?
> ---
> **Cluster Bootstrap & Startup**

**Answer:** no compaction for now


### Q26
> Should the Event Bridge broker follow the same `StartupStep` pattern as the Zeebe broker (sequential `AbstractBrokerStartupStep` implementations), or is a simpler initialization sequence acceptable?

**Answer:** a simpler approach is okay


### Q27
> How should `initialContactPoints` / cluster membership be configured — reuse the same `ClusterCfg` / `MembershipCfg` configuration structure, or define a new Event Bridge-specific configuration model?

**Answer:** reuse


### Q28
> Should the Event Bridge broker embed a gateway in the same JVM (like Zeebe's embedded gateway mode), or must gateway and broker always be separate processes?
> ---
> **Client Library**

**Answer:** embedded gateway


### Q29
> Should the Event Bridge client be a blocking (synchronous) API, a `CompletableFuture`-based async API, or a reactive (`Flux`/`Mono`) API?

**Answer:** completablefuture based async api (no flux or mono)


### Q30
> What should the client do when the broker it is connected to is not the leader for the target partition — should the client receive a redirect hint (leader address) and retry automatically, or should the application be responsible for routing?

**Answer:** redirect hing


### Q31
> Should the client library ship as a standalone Maven module (e.g., `event-bridge-client`), or should it be part of the same module as the gateway?
> ---
> **Operational / Non-Functional**

**Answer:** standalone maven


### Q32
> Which metrics should be exposed (via Micrometer)? Minimum set expected: publish throughput, fetch latency, consumer lag, partition leader/follower state?

**Answer:** for now just simple metrics, as suggested in the question.


### Q33
> Should Spring Boot Actuator health endpoints be included (liveness/readiness), following the same `HealthConfigurationInitializer` pattern used by the broker?

**Answer:** yes


### Q34
> What is the target test coverage expectation — unit tests only, or also integration tests (single-node cluster in-process, similar to `StandaloneCamundaTest`)? Are there specific test scenarios that must be covered (e.g., leader failover during publish)?

**Answer:** no tests needed


### Q35
> Should the new modules be included in the `CODEOWNERS` file and CI pipelines immediately, or is that out of scope for this iteration?

**Answer:** no


## Design (`plan-design-clarify`)

### Q1
> How many partitions should the Event Bridge support by default, and should partition count be configurable at startup (or only at cluster formation time, like Zeebe)?

**Answer:** everything should be configurable


### Q2
> Should partitions be statically assigned to brokers at configuration time, or dynamically balanced via RAFT membership changes?

**Answer:** following the fixed approach as in zeebe


### Q3
> Should the replication factor be configurable per partition, or uniform across all partitions?

**Answer:** uniform across al partitions


### Q4
> Should partition leadership be deterministic (e.g., broker-0 always prefers partition 1) or purely RAFT-elected with no priority hints?
> **Gateway & Routing**

**Answer:** as in zeebe


### Q5
> Should the gateway be an embedded component within the broker process (like Zeebe's embedded gateway) or always a separate process?

**Answer:** yes


### Q6
> When a client publishes to a partition whose leader is unknown or unavailable, should the gateway: (a) return an error immediately, (b) buffer and retry, or (c) block until a leader is elected?

**Answer:** return an error immediatelly


### Q7
> Should the gateway support fan-out (publish one event to multiple partitions), or strictly one partition per publish call?

**Answer:** to one partition


### Q8
> What transport protocol should the gateway API use — gRPC (like Zeebe), plain HTTP/REST, or a custom TCP protocol?

**Answer:** plain http rest


### Q9
> Should the gateway expose a single unified API for both publish and subscribe, or separate endpoints/services?
> **Event Model**

**Answer:** separate endpoints


### Q10
> What is the maximum allowed size for a single event (binary blob)? Should the broker enforce a hard limit?

**Answer:** in total 4 mb


### Q11
> Should events carry any mandatory metadata beyond the binary payload — e.g., a key, timestamp, producer ID, or content-type? Or is it purely opaque bytes + partition ID?

**Answer:** purely binary, the positions of each event in the batch, and partition id


### Q12
> Should events be assigned a monotonically increasing offset (like Kafka) that clients can use as a cursor? Or is position tracking internal-only?

**Answer:** yes, monotonically increasing


### Q13
> Should the publish API be fire-and-forget (ack after write to leader log) or wait-for-quorum (ack only after replication to majority)?
> **Log Stream & Storage**

**Answer:** wait for quorum


### Q14
> Should the Event Bridge reuse `SegmentedJournal` directly (like the existing `RaftLog`), or wrap it through `LogStream` / `AtomixLogStorage` as Zeebe does?

**Answer:** like zeebe does


### Q15
> Should the log be segment-based with size limits per segment (and what are the defaults), or unbounded until truncation?

**Answer:** like zeebe does, 128 MB per segment by default


### Q16
> What is the unit of truncation — truncate up to the minimum committed offset across all consumer groups, or per consumer group independently?

**Answer:** no truncation at the moment


### Q17
> Should compacted/truncated segments be deleted immediately or archived somewhere?
> **Consumer Groups & Subscriptions**

**Answer:** no truncation/compaction at the moment required


### Q18
> Can a single consumer group have multiple consumers, each assigned a subset of partitions (like Kafka consumer group rebalancing), or does each consumer independently read all partitions?

**Answer:** yes, multiple consumer, and each assigned to a subset of partitions


### Q19
> Who assigns partitions to consumers within a group — Broker-0 as coordinator, or the consumers themselves via a negotiation protocol?

**Answer:** the coordinator


### Q20
> What happens when a consumer in a group crashes (stops sending heartbeats)? Should its partition assignments be immediately reassigned, or held for a grace period?

**Answer:** first unassigned, after a timeout reassigned


### Q21
> What is the heartbeat interval and the timeout after which a consumer is considered dead?

**Answer:** lets say 5 seconds


### Q22
> Should consumer group state (offsets, membership) be persisted in the RAFT log, or maintained in-memory with recovery via client re-registration?

**Answer:** yes, persisted in RAFT log


### Q23
> Should a consumer be able to reset its offset (seek to beginning, seek to end, or seek to a specific offset)?

**Answer:** no


### Q24
> Can a consumer group span multiple partitions — i.e., consumer A reads partition 1 and consumer B reads partition 2, both in the same group?

**Answer:** yes


### Q25
> Is there a maximum number of consumers per group, or per partition?
> **Broker-0 Coordinator**

**Answer:** no


### Q26
> Is Broker-0 always the coordinator, or is the coordinator role itself RAFT-elected (so it can fail over)?

**Answer:** for now yes


### Q27
> What exactly does Broker-0 coordinate — just consumer group membership and offset commits, or also partition assignment decisions?

**Answer:** just consumer groups and offset commits


### Q28
> Should Broker-0's coordinator state be replicated via RAFT to survive broker-0 crashes, or re-built from client re-registrations?

**Answer:** replicated via RAFT


### Q29
> Should consumer group commits be acknowledged synchronously (client waits for durability) or asynchronously?
> **Cluster Membership & SWIM**

**Answer:** async


### Q30
> Should the Event Bridge use the existing `AtomixCluster` / `SwimMembershipProtocol` directly, or configure a separate SWIM cluster instance? (i.e., will Event Bridge nodes ever share a SWIM cluster with Zeebe brokers?)

**Answer:** it should create its own cluster, separate from Zeebe


### Q31
> Should broker metadata (which broker is leader for which partition) be propagated via SWIM `Member.properties()` exactly as Zeebe does with `BrokerInfo`, or through a different mechanism?

**Answer:** same way


### Q32
> Should the gateway maintain a local in-memory topology cache (like `BrokerClusterState`) and update it via SWIM events?
> **Sequencing**

**Answer:** yes


### Q33
> Should incoming events be sequenced (assigned monotonic positions) before being handed to RAFT, or does the RAFT log index serve directly as the event offset?

**Answer:** before handed to raft


### Q34
> Should a Sequencer actor batch multiple incoming publish requests into a single RAFT `appendEntry` call for throughput, or one RAFT entry per event?

**Answer:** one raft entry per batch of events


### Q35
> If batching: what is the maximum batch size (by count and/or by byte size)?
> **Distribution & Deployment**

**Answer:** 1000 items or 4 mb


### Q36
> Should the Event Bridge be added as a new profile/entry point in the existing `dist/` module (sharing the same tarball), or packaged as a completely separate Maven module with its own distribution artifact?

**Answer:** separate distribution and module


### Q37
> Should the broker and gateway be separate processes (each with their own entry point binary) or co-located in a single process (like Zeebe's embedded gateway mode)?

**Answer:** it should be one process/one application


### Q38
> Should there be an all-in-one standalone mode (broker + gateway in one JVM) for development/testing?

**Answer:** yes


### Q39
> What are the minimum JVM/hardware requirements for a single broker node?
> **Client Library**

**Answer:** nothing specific


### Q40
> What language(s) should the Event Bridge client support — Java only, or also other languages (Go, Python, etc.)?

**Answer:** only java


### Q41
> Should the client be a blocking API, async (`CompletableFuture`), or reactive (Reactor/RxJava)?

**Answer:** async


### Q42
> Should the client handle partition leader discovery automatically (via topology from gateway), or require the caller to specify a broker address directly?

**Answer:** automatically


### Q43
> Should the client support connection pooling and automatic reconnection on broker failover?

**Answer:** yes


### Q44
> What should the client do on a partial batch failure (some events accepted, some rejected) — retry the whole batch, retry only failed events, or propagate the partial result to the caller?
> **Testing & Observability**

**Answer:** retry whole batch


### Q45
> Should integration tests use an embedded in-process cluster (like `TestCluster` in Zeebe), or require a real multi-JVM setup?

**Answer:** no


### Q46
> Should the Event Bridge expose Prometheus metrics (via Micrometer) compatible with the existing `ActorScheduler` and RAFT metric conventions?

**Answer:** yes


### Q47
> Should there be a health/readiness endpoint (Spring Boot Actuator) indicating leader status and partition health?

**Answer:** yes


### Q48
> Are there specific SLAs or throughput targets (events/sec, latency p99) the initial implementation should be validated against?
> **Naming & Module Layout**

**Answer:** no


### Q49
> What should the Maven `groupId`/`artifactId` be — under `io.camunda` (like all other modules) or a separate namespace?

**Answer:** the same


### Q50
> Should the new module live under a top-level directory (e.g., `event-bridge/`) or nested under an existing umbrella (e.g., `gateways/gateway-event-bridge/`)?

**Answer:** top-level

