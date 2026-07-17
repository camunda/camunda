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
            └─ ClusterConfigurationManagementRequestsHandler   picks transformer, calls coordinator  [dynamic-config, root package]
                 └─ ConfigurationChangeCoordinatorImpl         simulate (dry-run) or apply            [dynamic-config, changes/]
                      └─ ConfigurationChangeAppliersImpl       per-op applier executes on broker      [dynamic-config, changes/]
```

The handler and transformers implement contracts declared in `dynamic-config-api`
(`ClusterConfigurationManagementApi`, `ConfigurationChangeCoordinator.ConfigurationChangeRequest`)
but are themselves implementation — request-to-operation dispatch logic, not part of the API
surface — so they live here, not in `-api`. See the
[ADR 0002](docs/adr/0002-extract-cluster-config-api-module.md) amendment.

## Package breakdown

|    Package     |                                                                                                                                                               What lives here                                                                                                                                                               |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `transformer/` | **One class per operation family** (e.g. `AddMembersTransformer`), each implementing the `ConfigurationChangeRequest` contract declared in `dynamic-config-api` — converts a request into a `List<Op>`. Constructed and dispatched by `ClusterConfigurationManagementRequestsHandler`.                                                      |
| `changes/`     | **Appliers** — one class per operation type (e.g. `PartitionJoinApplier`). Each applier executes an op against the live broker and advances `ClusterConfiguration`. Also `ConfigurationChangeCoordinatorImpl`, which implements the `ConfigurationChangeCoordinator` contract (declared in `dynamic-config-api`) and drives the apply loop. |
| `serializer/`  | `ProtoBufSerializer` — encode/decode all request and state types to/from proto bytes, implementing the `ClusterConfigurationRequestsSerializer` interface declared in `dynamic-config-api`. `ClusterConfigurationSerializer` for the persisted/gossiped topology snapshot (gossip-coupled, stays here).                                     |
| `gossip/`      | `ClusterConfigurationGossiper` and friends — Atomix gossip transport for propagating cluster config.                                                                                                                                                                                                                                        |
| root           | Wiring: `ClusterConfigurationManagerImpl` (gossip listener + apply), `ClusterConfigurationManagementRequestsHandler` (picks transformer, calls coordinator), `PersistedClusterConfiguration` (disk persistence), initializers (`PartitionDistributorInitializer`, `RoutingStateInitializer`, …)                                             |

## Operational guides

- [Adding a new operation](./docs/adding_new_operation.md)

