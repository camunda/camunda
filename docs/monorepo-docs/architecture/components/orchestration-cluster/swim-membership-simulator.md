# Membership Simulator (SWIM)

import SwimMembershipSimulator from '@site/src/components/SwimMembershipSimulator';

Use the simulator below to explore how Zeebe's SWIM membership protocol propagates cluster state
changes and detects node failures.

:::note
This is an educational simulator. It is designed to build intuition about SWIM as implemented in
the Orchestration Cluster, not to be a 1:1 reproduction of the Java implementation. We aim to keep
the behaviour faithful to [`SwimMembershipProtocol`](https://github.com/camunda/camunda/blob/main/zeebe/atomix/cluster/src/main/java/io/atomix/cluster/protocol/SwimMembershipProtocol.java),
but some details may differ.
:::

**How to use:**

- **Tooltips** — every parameter and control has a tooltip; hover over anything to learn what it does.
- **Clock** — the simulator starts paused and slowed down by default. Use the play/pause/step controls
  and the speed selector to move through time at your own pace.
- **Fault injection** — use the Faults row to crash nodes, cut links between nodes, or exclude a
  node from gossip fanout.
- **Event log** — the log shows simulated protocol events alongside the corresponding Java events
  fired by the membership service (e.g. `MEMBER_ADDED`, `REACHABILITY_CHANGED`, `MEMBER_REMOVED`),
  so you can connect what you see visually to what happens internally in the cluster.

## Simulator

<SwimMembershipSimulator />

## How it works

Zeebe uses a variant of the
[SWIM](https://arxiv.org/abs/2004.03461) (Scalable Weakly-consistent Infection-style process
group Membership) protocol
([`SwimMembershipProtocol`](https://github.com/camunda/camunda/blob/main/zeebe/atomix/cluster/src/main/java/io/atomix/cluster/protocol/SwimMembershipProtocol.java))
to maintain cluster membership.

Every node periodically probes a random peer to check reachability, and gossips membership state
changes to a random subset of peers. A failed node is first marked _suspect_, then _dead_ after a
configurable timeout, at which point a `MEMBER_REMOVED` event is fired to all local listeners.

The membership view is used across many subsystems: topology propagation, job streaming, routing
in the `ClusterCommunicationService` (which gates communication such that nodes only talk to known
alive members — preventing messages from being sent to evicted nodes), and any feature that needs
to know which brokers are currently reachable.

SWIM failure detection is completely separate from the Raft consensus algorithm, which has its own
failure detection. They are complementary but test different things: SWIM asks "is this node
generally alive and responsive?", whereas Raft replicas ask "is the leader actually fulfilling its
role?" — which covers cases where a node is alive and responding but the leader has stalled or is
no longer making progress on the log. This also makes SWIM particularly valuable for ephemeral
nodes such as gateways that participate in the cluster but are not part of any Raft partition.

## What gets gossiped

Beyond liveness (alive / suspect / dead), SWIM gossips arbitrary `Member#properties`. Zeebe uses
this to propagate `BrokerInfo` — each broker's partition roles, partition health, leader terms,
and cluster topology — and event-service topic subscriptions. Any change to a member's properties
triggers a gossip update.


