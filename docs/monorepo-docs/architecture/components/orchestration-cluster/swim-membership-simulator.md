# Membership Simulator (SWIM)

import SwimMembershipSimulator from '@site/src/components/SwimMembershipSimulator';

Use the simulator below to explore how Zeebe's SWIM membership protocol propagates cluster state
changes and detects node failures.

<SwimMembershipSimulator />

## How it works

Zeebe uses a variant of the
[SWIM protocol](https://www.cs.cornell.edu/projects/Quicksilver/public_pdfs/SWIM.pdf)
([`SwimMembershipProtocol`](https://github.com/camunda/camunda/blob/main/zeebe/atomix/cluster/src/main/java/io/atomix/cluster/protocol/SwimMembershipProtocol.java))
to maintain cluster membership. Every node periodically gossips membership updates to a random
subset of peers and probes a random peer to detect failures.

## What gets gossiped

Beyond liveness (alive / suspect / dead), SWIM gossips arbitrary `Member#properties`. Zeebe uses
this to propagate `BrokerInfo` — each broker's partition roles, partition health, leader terms,
and cluster topology — and event-service topic subscriptions. Any change to a member's properties
triggers a gossip update.

## Failure detection and indirect probes

_(Text to be revised with the author after implementation.)_

## Parameters

_(Text to be revised with the author after implementation.)_
