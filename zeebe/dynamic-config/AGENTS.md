# zeebe/dynamic-config — Module Map

Manages cluster topology changes: membership, partition placement, exporter state, routing. Changes
flow through a coordinator that serializes requests, computes operations, and applies them one by
one across the cluster.

Request/response contracts, the immutable state model, and the wire protocol live in the sibling
module `zeebe/dynamic-config-api` (artifactId `zeebe-cluster-config-api`), which this module
depends on — see its own [AGENTS.md](../dynamic-config-api/AGENTS.md) and
[ADR 0002](docs/adr/0002-extract-cluster-config-api-module.md) for why the split is where it is.

## Request flow

```
REST controller (dist/)
  └─ ClusterConfigurationManagementRequestSender   sends over Atomix ClusterCommunicationService     [dynamic-config-api]
       └─ ClusterConfigurationRequestServer        receives, deserializes, dispatches                [dynamic-config-api]
            └─ ClusterConfigurationManagementRequestsHandler   picks transformer, calls coordinator  [dynamic-config-api]
                 └─ ConfigurationChangeCoordinatorImpl         simulate (dry-run) or apply            [dynamic-config]
                      └─ ConfigurationChangeAppliersImpl       per-op applier executes on broker      [dynamic-config]
```

## Package breakdown

|    Package    |                                                                                                                                                               What lives here                                                                                                                                                               |
|---------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `changes/`    | **Appliers** — one class per operation type (e.g. `PartitionJoinApplier`). Each applier executes an op against the live broker and advances `ClusterConfiguration`. Also `ConfigurationChangeCoordinatorImpl`, which implements the `ConfigurationChangeCoordinator` contract (declared in `dynamic-config-api`) and drives the apply loop. |
| `serializer/` | `ProtoBufSerializer` — encode/decode all request and state types to/from proto bytes, implementing the `ClusterConfigurationRequestsSerializer` interface declared in `dynamic-config-api`. `ClusterConfigurationSerializer` for the persisted/gossiped topology snapshot (gossip-coupled, stays here).                                     |
| `gossip/`     | `ClusterConfigurationGossiper` and friends — Atomix gossip transport for propagating cluster config.                                                                                                                                                                                                                                        |
| `util/`       | `ZoneAwarePartitionDistributor`, `RoundRobinPartitionDistributor`, `ConfigurationUtil` (build `ClusterConfiguration` from a distribution map)                                                                                                                                                                                               |
| root          | Wiring: `ClusterConfigurationManagerImpl` (gossip listener + apply), `PersistedClusterConfiguration` (disk persistence), initializers (`PartitionDistributorInitializer`, `RoutingStateInitializer`, …)                                                                                                                                     |

## Operational guides

- [Adding a new operation](./docs/adding_new_operation.md)

