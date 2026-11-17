# ADR-0010: Zone-Aware Clusters: Composite Broker Identity and Zone-Driven Partitioning

**DRI**: Carlo Sana ([@entangled90](https://github.com/entangled90))

**Status**: Accepted (8.10)

**Purpose**: Defines broker identity in a zone-aware cluster and the distribution of partitions and leadership across zones. It also defines how existing clusters migrate to zone awareness.

**Audience**: Engineers working on cluster membership (`zeebe/atomix/cluster`), dynamic cluster configuration (`zeebe/dynamic-config`), the cluster management and topology APIs, and multi-region deployments of the Orchestration Cluster.

## Context

Multi-region and multi-AZ clusters encoded the region in the integer node ID. In a dual-region setup, even IDs live in one region and odd IDs live in the other. Partition placement relies on that arithmetic. As a result, the number of regions cannot change after cluster creation, and setups with three or more regions are not possible. Each deployment target (Kubernetes, ECS, bare metal) must also reproduce the numbering scheme by hand.

Zone awareness adds the zone to broker identity and to partition distribution. A cluster declares its zones, and each broker declares the zone it runs in. Replica placement and Raft leadership follow a per-zone configuration that operators can change at runtime. Future work such as follow-the-sun, regional affinity, and topologies with three or more regions builds on this.

## Decision

**D1. A zoned broker has the ID `<zone>_<nodeIdx>`. A non-zoned broker keeps the bare `<nodeIdx>`.**
The broker always builds its ID from configuration (`camunda.cluster.zone` plus the node index). It never reads the ID back from disk. A zone name must start with an alphanumeric character, contain only alphanumerics and `-`, and have at most 63 characters. The region and AZ names of every major cloud provider satisfy these rules. The rules keep `_` an unambiguous separator and make the ID safe to use as a URL path segment.

**D2. A `ZONE_AWARE` partitioning scheme distributes replicas and leadership from a list of zones.**
Each zone declares `name`, `numberOfBrokers`, `numberOfReplicas`, and `priority`. Every partition gets exactly `numberOfReplicas` replicas in each zone. The distributor assigns Raft election priorities in descending zone-priority order, so leaders concentrate in the highest-priority zone. When that zone fails, the existing Raft priority decrement moves leadership to the next zone. No dedicated failover logic is necessary.

**D3. The partition distribution configuration is part of the dynamic cluster configuration.**
Static configuration only seeds this value at first initialization. After that, the gossiped cluster configuration is the source of truth for every partition group. Operators add a zone, remove a zone, or change zone priorities (for example, during a region failover) through the cluster management API. These are cluster configuration changes, not configuration edits that need a restart.

**D4. Zone awareness is opt-in and backward compatible.**
Non-zoned clusters, including existing single- and dual-region setups, behave as before. gRPC `BrokerInfo` and REST `/v2/topology` expose a string `brokerId` next to the deprecated integer `nodeId`. The cluster management API (`/actuator/cluster`) uses a single broker ID. This ID is an integer in a non-zoned cluster and the composite string in a zoned cluster. In a zoned cluster, every input that identifies a broker requires the composite ID, because the same node index exists in every zone.

**D5. Existing clusters migrate from bare to zoned IDs one zone at a time, by replacing brokers.**
Migration is a cluster operation that runs one zone at a time. For each zone, it adds new brokers with zoned IDs, moves partitions onto them, and removes the bare brokers that they replace. A fixed slot layout maps bare node `n` to zone rank `n % zoneCount` and local index `n / zoneCount`. With equal zone priorities, the zone-aware scheme reproduces the round-robin placement exactly. Each partition replica therefore moves to the zoned broker in the same slot, and the distribution does not change.

**D6. The default coordinator is the member with the lowest ID, ordered by node index and then by zone.**
Coordinator selection is deterministic and does not depend on zone priority. When an operator force-removes a zone, the cluster selects a coordinator outside that zone.

## Alternatives considered

- **Keep encoding zones in integer node IDs.** An even/odd scheme extended to N regions still puts the zone count into the ID arithmetic. Operators then still cannot add or remove regions after creation, and this work exists to remove that limit.
- **Keep the partition distribution in static configuration only.** Runtime changes to zones and priorities need a value that every broker agrees on. Static configuration can differ between brokers and changes only with a restart.
- **Make the coordinator follow the primary (highest-priority) zone.** A coordinator in the zone of the preferred leaders gives users no visible benefit. It would also move the coordinator each time zone priorities change.

## Consequences

- Zone awareness is opt-in, and the cluster does not infer it. Existing clusters keep bare IDs until operators explicitly migrate them, so the code must support both ID forms indefinitely.
- Migration needs approximately twice the broker resources while the old and new brokers of a zone run together. It also takes the time to replicate partition data to the new brokers.
- Integer broker IDs stay meaningful only in non-zoned clusters. Before they adopt zones, clients and tools that use `nodeId` must switch to `brokerId`. Callers of the cluster management API must switch to the composite ID.
- Zones are a list in the configuration, not a map keyed by name. A list is much easier to set through environment variables and YAML, and a map adds little value. List order matters in two cases: while bare members remain during migration, and when zones have equal priorities. In both cases, the distributor places replicas round-robin over the zones in list order.
- The coordinator is often not in the highest-priority zone. This is an accepted compromise for a deterministic coordinator that does not depend on priority.
- Zone operations apply to every partition group. They build on the multi-group cluster configuration model of [dynamic-config ADR-0001](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/docs/adr/0001-multi-partition-group-cluster-configuration.md).

## Source

- [[EPIC] Cluster Zone Awareness #51412](https://github.com/camunda/camunda/issues/51412): primary source. Parent epic: [#51411](https://github.com/camunda/camunda/issues/51411).
- [Zone-aware clusters](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/zeebe/configuration/zone-aware-clusters/): user documentation for configuration and operations.
- [#51587](https://github.com/camunda/camunda/issues/51587): `ZONE_AWARE` distribution scheme. [#54805](https://github.com/camunda/camunda/issues/54805): distribution configuration in the dynamic cluster configuration.
- [#51986](https://github.com/camunda/camunda/issues/51986): migration from bare to zoned IDs. [#54106](https://github.com/camunda/camunda/issues/54106): zone name rules.
- [#51586](https://github.com/camunda/camunda/issues/51586), [#51998](https://github.com/camunda/camunda/issues/51998), [#57589](https://github.com/camunda/camunda/issues/57589): API backward compatibility.
- [#51953](https://github.com/camunda/camunda/issues/51953): rejected coordinator selection by primary zone.
- [dynamic-config ADR-0001](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/docs/adr/0001-multi-partition-group-cluster-configuration.md): multi-partition-group cluster configuration.

