# Broker registration + liveness state machine (implementation brief)

> **STATUS: metadata side IMPLEMENTED** on branch `roman/optimize` (Phase 1 + Phase 2).
> The broker registry is first-class replicated state on the metadata stream (column family
> `BROKER_REGISTRY`); REGISTER/FENCE/DRAIN/DEREGISTER are log commands processed the engine
> way; the heartbeat runs leader-locally over an in-memory `BrokerLivenessMirror`; a
> clock-driven `BrokerEvictionTask` fences lapsed sessions; placement reads only registered,
> unfenced brokers; a clock-driven `PlacementHealTask` re-places topics off fenced/draining
> brokers (minimal-diff), fed to the change-coordinator. Unit-tested (`MetadataBrokerProcessorTest`,
> `PlacementHealTaskTest`). **NOT yet validated on a running cluster.**
>
> **The one remaining piece is the broker-side register/heartbeat client loop** (see
> "Remaining"), which is the only part that makes the FSM live in production and **must be
> validated on a running cluster** (see the `event-bridge-runtime-smoke` note).

## The model: two channels

A broker talks to the metadata leader over two independent channels carrying different
things in opposite directions:

| Channel | Direction | Carries | Transport | Hits the log? |
|---|---|---|---|---|
| **Observe** | leader → broker | the topic registry (assignments) | Raft replication (pull) + replay | yes — it *is* the log |
| **Liveness** | broker → leader | register / heartbeat | coordinate-request RPC | only registration + fence/drain transitions |

A broker learns **which partitions to host** purely by observing the replicated registry and
reconciling (`TopicReconciler` filters `TopicRecord.assignment` by its own node id) — never
from a heartbeat. The liveness channel only answers *"is this broker alive enough to be
assigned partitions?"*. This mirrors KRaft (controllers vote + serve the metadata log;
brokers observe it and separately register/heartbeat) and the consumer-groups
membership/session split.

## The FSM

```
                register(incarnation)          (re-register, same incarnation = retry)
   (absent) ───────────────────────────► ACTIVE ◄───────────────────────────────────┐
       ▲                                  │  ▲                                        │
       │ deregister                       │  │ heartbeat(epoch)                       │ re-register
       │                  session lapses  │  │ within timeout                         │ (bumped epoch)
   (absent) ◄── DEREGISTERED ◄────────────┼──┼──────────────── FENCED ────────────────┘
                    ▲                      │  │                                         
                    │ drained              ▼  │  draining heartbeat                     
                    └──────────────── DRAINING ┘                                        
```

- **Broker epoch** — server-assigned, monotonic, bumped on each genuine (re-)registration; a
  heartbeat carrying a stale epoch is told to re-register. Registration is **idempotent per
  incarnation**: a retry from the same incarnation of an `ACTIVE` broker keeps the epoch (so
  the broker's in-flight heartbeats are not fenced); a new incarnation (restart) or a
  fenced/draining broker returning bumps it.
- **ACTIVE** is the only placement-eligible state. **FENCED** (session lapsed) and
  **DRAINING** (controlled shutdown) are excluded, and the change-coordinator moves their
  replicas off.
- A `REGISTERING`/catch-up gate (place only on brokers caught up on the metadata log) is a
  deliberate future refinement — not required for correctness here, since observe-and-
  reconcile converges regardless.

## What's implemented (metadata side)

All in `event-bridge-cluster-metadata`, mirroring the consumer-groups module:

- **Protocol** (`zeebe/protocol`, `event-bridge-protocol`): `EVENT_BRIDGE_BROKER` value type;
  `MetadataIntent` broker intents (REGISTER/BROKER_REGISTERED, FENCE/BROKER_FENCED,
  DRAIN/BROKER_DRAINING, DEREGISTER/BROKER_DEREGISTERED); `REGISTER_BROKER` /
  `BROKER_HEARTBEAT` coordinate-request types; register/heartbeat request+response DTOs.
- **Record + state**: `BrokerRecord`; `DbBrokerState` (immutable `BrokerState` / mutable /
  concrete + `BrokerQueryService` on a private context); pure-writer appliers.
- **Processors** (validate → resolve → append/reject): `RegisterBrokerProcessor` (assigns the
  epoch, idempotent per incarnation); `Fence`/`Drain`/`DeregisterBrokerProcessor` (internal,
  guarded by `BrokerTransitionValidator`, always append an event or a `COMMAND_REJECTION`).
- **Liveness**: `BrokerLivenessMirror` (leader-local, not replicated) + `BrokerHeartbeatHandler`
  (own actor; register writes the command and forwards the committed reply; heartbeat is
  leader-local epoch check + liveness touch; draining → DRAIN_BROKER, and once replicas have
  moved off → DEREGISTER + ack shutdown) + clock-driven `BrokerEvictionTask`.
- **Placement**: `CreateTopic`/`ReassignTopicProcessor` read `BrokerState.activeBrokers()`
  (with a Raft-membership fallback until any broker has registered). A clock-driven
  `PlacementHealTask` (a stream task on its own private contexts, like `BrokerEvictionTask`)
  re-places off non-active brokers: per partition it keeps the surviving committed replicas and
  replaces only the fenced/draining ones (minimal-diff, never moving data off a healthy replica),
  appending a `REGISTER_TOPIC` target that the `MetadataManager` change-coordinator drives
  committed → target. It is stateless/idempotent — it keys off the committed assignment, so a topic
  whose replicas are all active is skipped and the loop self-terminates.

### Safety + termination notes

- **No committed-data loss by construction.** The change-coordinator is grow-before-shrink
  (`ReconfigurationPlanner.nextOp` adds a replica before removing one) and advances `committed`
  only after the per-topic Raft group confirms the step (which requires that group's quorum). A
  new replica joins as a PASSIVE follower and is promoted once caught up (Atomix `join`) before the
  fenced one is removed. If a majority of a partition's replicas are fenced the group loses quorum,
  so the move safely stalls/retries rather than dropping survivors — degraded availability, not
  loss. Never add a force-reconfiguration path.
- **No debounce (deliberate).** The heal scan is over the bounded topic registry (cheap), and the
  10s session timeout is already an implicit debounce against blips, so a due-index debounce was
  considered and not pursued.
- **Fenced tombstones linger.** A permanently-dead broker stays as a harmless `FENCED` entry
  (excluded from placement, bounded by cluster size) until it re-registers or is deregistered.
  GC'ing long-fenced brokers with no remaining replicas is a possible future cleanup.

## Remaining: the broker-side register/heartbeat client loop

The metadata leader serves `REGISTER_BROKER` / `BROKER_HEARTBEAT` coordinate requests
(`MetadataRequestHandler` → `BrokerHeartbeatHandler`). What's missing is the **broker
sending them**:

1. On startup the broker generates a unique `incarnation` (once per process) and sends
   `REGISTER_BROKER(brokerId, incarnation)` to the `event-bridge-metadata` routing group,
   storing the epoch from the reply.
2. Every interval (< the 10s session timeout) it sends `BROKER_HEARTBEAT(brokerId, epoch, …)`.
   A `FENCED_MEMBER_EPOCH` reply means re-register.
3. On graceful shutdown it sets `draining=true` and stops once the reply says
   `shouldShutdown`.

**Transport decision + prerequisite.** This uses the broker client (symmetric with consumer
heartbeats), which the broker does **not** have wired today: the `BrokerClient` is
Spring-auto-configured in the gateway, and `BrokerExecuteCoordinateRequest` + a new
`BrokerRegisterRequest`/`BrokerHeartbeatRequest` wrapper live in `event-bridge-gateway`
(which the broker correctly does not depend on). So the prerequisite is to (a) relocate the
coordinate-request base into a module the broker can use, and (b) construct a `BrokerClient`
on the broker node. This layer is network wiring — it cannot be meaningfully unit-tested and
must be validated on a running cluster.

## Deliberately later (Phase 3)

Dynamic metadata-quorum reconfiguration — promote an observer to voter / demote a dead voter
to keep the controller quorum healthy as brokers churn. The voter cap + observer attachment
already exists (`PartitionBootstrapper.bootstrapMetadata`: RF voters, the rest passive
observers); only runtime voter membership changes are missing, and they depend on this
liveness FSM. Skip unless the metadata voters are meant to be ephemeral.
