# zeebe/dynamic-config — Module Map

Manages cluster topology changes: membership, partition placement, exporter state, routing. Changes
flow through a coordinator that serializes requests, computes operations, and applies them one by
one across the cluster.

## Request flow

```
REST controller (dist/)
  └─ ClusterConfigurationManagementRequestSender   sends over Atomix ClusterCommunicationService
       └─ ClusterConfigurationRequestServer        receives, deserializes, dispatches
            └─ ClusterConfigurationManagementRequestsHandler   picks transformer, calls coordinator
                 └─ ConfigurationChangeCoordinatorImpl         simulate (dry-run) or apply
                      └─ ConfigurationChangeAppliersImpl       per-op applier executes on broker
```

## Package breakdown

|    Package    |                                                                                                               What lives here                                                                                                                |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/`        | **Request/response types** (`ClusterConfigurationManagementRequest.*`), **transformers** (one per operation family — convert a request into a `List<Op>`), request sender/server/handler, topics enum                                        |
| `changes/`    | **Appliers** — one class per operation type (e.g. `PartitionJoinApplier`). Each applier executes an op against the live broker and advances `ClusterConfiguration`. Also `ConfigurationChangeCoordinator(Impl)` which drives the apply loop. |
| `state/`      | **Immutable data model**: `ClusterConfiguration`, `MemberState`, `PartitionState`, `PartitionDistributorConfig`, `ClusterConfigurationChangeOperation` (sealed op hierarchy), `RoutingState`                                                 |
| `serializer/` | `ProtoBufSerializer` — encode/decode all request and state types to/from proto bytes. `ClusterConfigurationSerializer` for the persisted topology snapshot.                                                                                  |
| `util/`       | `ZoneAwarePartitionDistributor`, `RoundRobinPartitionDistributor`, `ConfigurationUtil` (build `ClusterConfiguration` from a distribution map)                                                                                                |
| root          | Wiring: `ClusterConfigurationManagerImpl` (gossip listener + apply), `PersistedClusterConfiguration` (disk persistence), initializers (`PartitionDistributorInitializer`, `RoutingStateInitializer`, …)                                      |

## Operational guides

- [Adding a new operation](./docs/adding_new_operation.md)

