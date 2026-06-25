# Broker registration + liveness state machine (implementation brief)

> **STATUS: metadata side IMPLEMENTED** on branch `roman/optimize` (Phase 1 + Phase 2).
> The broker registry is first-class replicated state on the metadata stream (column family
> `BROKER_REGISTRY`); REGISTER/FENCE/DRAIN/DEREGISTER are log commands processed the engine
> way; the heartbeat runs leader-locally over an in-memory `BrokerLivenessMirror`; a
> clock-driven `BrokerEvictionTask` fences lapsed sessions; placement reads only registered,
> unfenced brokers; a clock-driven `PlacementHealTask` re-places topics off fenced/draining
> brokers (minimal-diff), fed to the change-coordinator. The broker side (`BrokerRegistrar`) is
> implemented too. Unit-tested (`MetadataBrokerProcessorTest`, `PlacementHealTaskTest`) **and
> smoke-tested on a local 3-node cluster** — see "Smoke-test results".
>
> **The one capability still missing is evicting a member from a per-topic Raft group**, which both
> the kill and graceful-drain paths need; it requires the postponed leader-driven remote-removal in
> Atomix (see "Deliberately later"). Everything up to setting the heal target works end-to-end.

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

## Broker-side register/heartbeat client loop (implemented)

`BrokerRegistrar` (a `SmartLifecycle` in the gateway service layer) drives the broker side:

1. On startup it generates a unique `incarnation` and sends `REGISTER_BROKER(brokerId,
   incarnation)` to the metadata routing group (retrying until the leader is up), storing the epoch.
2. Every 3s (< the 10s session timeout) it sends `BROKER_HEARTBEAT(brokerId, epoch, draining)`. A
   `FENCED_MEMBER_EPOCH` reply triggers re-registration.
3. On graceful stop it heartbeats with `draining=true` until the leader acks `shouldShutdown`.

It **reuses the gateway's existing `BrokerClient`** — broker and gateway co-deploy in one
`StandaloneEventBridge` process, so no new client or cross-module move was needed; the two
coordinate-request wrappers (`BrokerRegisterRequest`, `BrokerLivenessHeartbeatRequest`) sit
alongside the others in the gateway. The metadata leader serves these via `MetadataRequestHandler`
→ `BrokerHeartbeatHandler`.

## Smoke-test results (local 3-node cluster)

Validated on `run-local-cluster.sh` (3 `StandaloneEventBridge` nodes):

- ✅ **Registration** — all three brokers register (`BrokerRegistrar`, epoch 1) once the metadata
  leader is up; the retry-until-registered loop handles the startup race.
- ✅ **Placement over registered brokers** — a fresh RF-3 topic places cleanly round-robin over the
  registered set (`[0,1,2]/[1,2,0]/[2,0,1]`).
- ✅ **Fencing** — killing a broker fences it after the session timeout (`BrokerEvictionTask - Broker
  2 session lapsed … fencing at epoch 1`). Surviving per-topic groups keep their leaders at 2/3
  quorum.
- ✅ **Heal is not eager (no spare → no churn)** — with 3 brokers and RF-3, a fenced broker has no
  spare to take over, so `PlacementHealTask` does **nothing**: `Healing topic` and `Reassignment …
  LEAVE member=2` both absent. The broker stays in the topology.
- ✅ **Restart the same broker → it resumes** — restarting the killed node re-registers it (`Broker
  2 registered … at epoch 2`, epoch bumped because it had been fenced) and it **recovers its existing
  partitions** (`Raft partition event-bridge-topic-orders/{1,2,3} bootstrapped`); no reconfiguration
  needed since it was never removed. Topology returns to `[0,1,2]`.
- ⏳ **Expand onto a new broker / graceful drain** — both need a broker to *join* a per-topic group,
  which is blocked by the pre-existing **#18** (`raftPartition.join()` fails with
  `ReconfigurationHelper: "Sent join request to all known members, but all failed."`, even with all
  brokers alive). The heal's expand *decision* is correct and unit-tested (a fenced replica is
  swapped for an available spare); only the live JOIN is blocked.

**Conclusion:** the broker liveness FSM, broker-side loop, placement, fencing, the (non-eager) heal,
and same-broker recovery all work end-to-end (validated on a cluster). The remaining gap is the
**per-topic Raft group reconfiguration** the heal/reassignment feeds: LEAVE of a gone broker needs
the postponed **leader-driven remote removal** (#17), and JOIN/grow is blocked by the pre-existing
`raftPartition.join()` failure (#18). Both live in the reconfiguration/Atomix layer, not this change.

> A latent bug the smoke test caught: `UnifiedRecordValue.fromValueType` (an exhaustive `ValueType`
> switch) didn't handle the new `EVENT_BRIDGE_BROKER`, throwing `MatchException` at stream startup —
> hidden from the build by a stale build-cache entry. Fixed.

## Deliberately later

### Leader-driven member removal (next; unblocks kill + drain)

Evicting a member from a per-topic Raft group needs a **live member (the partition leader) to
drive the config change**, because Atomix's `RaftPartition` exposes only self-membership ops
(`join`/`joinAsPassive`/`leave`/`promote`) — there is no "remove member X". So today the `LEAVE`
step relies on the departing broker removing itself, which fails for a killed broker (can't act)
and for a drained one (it shuts down before the `LEAVE` commits). The fix is two parts:
1. add a leader-driven remote-removal API to Atomix `RaftPartition`/`RaftServer` (the leader
   proposes a configuration change removing the dead member; commits with the surviving quorum) —
   a sensitive consensus-layer change, postponed to its own session;
2. route the `LEAVE` of a gone broker to a **surviving** member (the leader) instead of the
   departing one.

This is the single capability the smoke test showed missing, and it resolves both the kill and the
drain paths. (For drain, with leader-driven removal the broker no longer has to stay alive through
the move.)

### Dynamic metadata-quorum reconfiguration (Phase 3)

Promote an observer to voter / demote a dead voter to keep the controller quorum healthy as brokers
churn. The voter cap + observer attachment already exists
(`PartitionBootstrapper.bootstrapMetadata`: RF voters, the rest passive observers); only runtime
voter membership changes are missing, and they depend on this liveness FSM. Skip unless the metadata
voters are meant to be ephemeral.
