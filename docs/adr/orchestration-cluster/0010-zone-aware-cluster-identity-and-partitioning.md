# ADR-0010: Zone-Aware Clusters: Composite Broker Identity and Zone-Driven Partitioning

**DRI**: Carlo Sana ([@entangled90](https://github.com/entangled90))

**Status**: Accepted (8.10)

**Purpose**: Defines how brokers are identified in a zone-aware cluster, how partitions and leadership are distributed across zones, and how existing clusters adopt zone awareness.

**Audience**: Engineers working on cluster membership (`zeebe/atomix/cluster`), dynamic cluster configuration (`zeebe/dynamic-config`), the cluster management and topology APIs, and multi-region deployments of the Orchestration Cluster.

## Context

Multi-region and multi-AZ clusters encoded the region in the integer node ID: in a dual-region setup, even IDs live in one region and odd IDs in the other, and partition placement relies on that arithmetic. The number of regions is therefore fixed when the cluster is created, setups with three or more regions are not expressible, and each deployment target (Kubernetes, ECS, bare metal) has to reproduce the numbering scheme by hand.

Zone awareness makes the zone a first-class part of broker identity and partition distribution. A cluster declares its zones, each broker declares the zone it runs in, and replica placement and Raft leadership follow per-zone configuration that can be changed at runtime. It is also the building block for follow-the-sun, regional affinity, and topologies with three or more regions.

## Decision

**D1. A zoned broker is identified by the string `<zone>_<nodeIdx>`; a non-zoned broker keeps the bare `<nodeIdx>`.**
Identity is always reconstructed from configuration (`camunda.cluster.zone` plus the node index) and never read back from disk. Zone names must start with an alphanumeric character, contain only alphanumerics and `-`, and be at most 63 characters, which every major cloud provider's region/AZ names satisfy. This keeps `_` an unambiguous separator and the ID safe to use as a URL path segment.

**D2. A `ZONE_AWARE` partitioning scheme distributes replicas and leadership from an ordered list of zones.**
Each zone declares `name`, `numberOfBrokers`, `numberOfReplicas`, and `priority`. Every partition gets exactly `numberOfReplicas` replicas in each zone. Raft election priorities are assigned in descending zone-priority order, so leaders concentrate in the highest-priority zone and, when that zone is lost, Raft's existing priority-decrement moves leadership to the next zone without dedicated failover logic.

**D3. The partition distribution configuration is part of the dynamic cluster configuration.**
Static configuration only seeds it on first initialization; afterwards the gossiped cluster configuration is the source of truth, shared by every partition group. Adding or removing a zone and re-ordering zone priorities (e.g. a region failover) are cluster configuration changes applied through the cluster management API, not configuration edits followed by restarts.

**D4. Zone awareness is opt-in and backward compatible.**
Non-zoned clusters, including existing single- and dual-region setups, behave as before. Broker-facing APIs (gRPC `BrokerInfo`, REST `/v2/topology`, the cluster management API) expose a string `brokerId` alongside the now-deprecated integer `nodeId`. In a zoned cluster, inputs that identify a broker require the composite ID, since the same node index exists in every zone.

**D5. Existing clusters migrate from bare to zoned identities one zone at a time, by replacing brokers.**
Migration is a cluster operation that, per zone, adds new brokers with zoned identities, moves partitions onto them, and removes the bare brokers they replace. A fixed slot layout maps bare node `n` to zone rank `n % zoneCount` and local index `n / zoneCount`, and a zone-aware configuration with equal zone priorities reproduces the round-robin placement exactly, so each partition replica moves to the zoned broker occupying the same slot and the distribution itself does not change.

**D6. The default coordinator is the member with the lowest ID, ordered by node index and then zone.**
Coordinator selection stays deterministic and independent of zone priority. When a zone is force-removed, a coordinator outside that zone is selected.

## Alternatives considered

- **Keep encoding zones in integer node IDs.** Extending the even/odd scheme to N regions keeps the zone count baked into ID arithmetic, so regions still cannot be added or removed after creation — the limitation this work exists to remove.
- **Keep the partition distribution in static configuration only.** Runtime zone and priority changes need a value every broker agrees on; static configuration can drift per broker and only changes with a restart.
- **Make the coordinator follow the primary (highest-priority) zone.** Co-locating the coordinator with the preferred leaders brings no user-visible benefit and would make coordinator ownership change whenever zone priorities change.

## Consequences

- Zone awareness is opt-in and cannot be inferred: existing clusters keep bare IDs until they explicitly migrate, so both identity forms are supported indefinitely.
- Migration requires roughly twice the broker resources while old and new brokers of a zone are both running, plus the time to replicate partition data to the new brokers.
- Integer `nodeId` remains meaningful only in non-zoned clusters; clients and tooling relying on it must switch to `brokerId` before adopting zones.
- Zones are configured as a list rather than a map keyed by name: a list is much easier to set through environment variables and YAML, and a map would add little. List order is significant, as it drives the migration slot layout.
- The coordinator lives in the zone that sorts first by name among brokers with node index 0, which is not necessarily the highest-priority zone. This is an accepted compromise in exchange for a deterministic, priority-independent coordinator.
- Zone operations are planned across every partition group, building on the multi-group cluster configuration model of [dynamic-config ADR-0001](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/docs/adr/0001-multi-partition-group-cluster-configuration.md).

## Source

- [[EPIC] Cluster Zone Awareness #51412](https://github.com/camunda/camunda/issues/51412) (internal) — primary source; parent epic [#51411](https://github.com/camunda/camunda/issues/51411).
- [Zone-aware clusters](https://docs.camunda.io/docs/next/self-managed/components/orchestration-cluster/zeebe/configuration/zone-aware-clusters/) — user-facing configuration and operations documentation.
- [#51587](https://github.com/camunda/camunda/issues/51587) (internal) — `ZONE_AWARE` distribution scheme. [#54805](https://github.com/camunda/camunda/issues/54805) (internal) — distribution config in the dynamic cluster configuration.
- [#51986](https://github.com/camunda/camunda/issues/51986) (internal) — migration from bare to zoned identities. [#54106](https://github.com/camunda/camunda/issues/54106) (internal) — zone name constraints.
- [#51586](https://github.com/camunda/camunda/issues/51586), [#51998](https://github.com/camunda/camunda/issues/51998), [#57589](https://github.com/camunda/camunda/issues/57589) (internal) — API backward compatibility.
- [#51953](https://github.com/camunda/camunda/issues/51953) (internal) — rejected primary-zone coordinator selection.
- [dynamic-config ADR-0001](https://github.com/camunda/camunda/blob/main/zeebe/dynamic-config/docs/adr/0001-multi-partition-group-cluster-configuration.md) — multi-partition-group cluster configuration.

