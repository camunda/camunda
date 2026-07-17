# zeebe/dynamic-config-api — Module Map

Request/response contracts, the immutable cluster configuration state model, and the generated
wire protocol for dynamic cluster configuration. This module has no dependency on the appliers
engine, gossip transport, or protobuf codec — see
[ADR 0002](../dynamic-config/docs/adr/0002-extract-cluster-config-api-module.md) in
`zeebe/dynamic-config` for why it exists as a separate module and what stayed behind.

Consume this module directly if you only need request/response DTOs or the state model (e.g.
`zeebe-gateway-rest`, `zeebe-broker-client`). Depend on `zeebe-cluster-config` (the sibling impl
module) instead if you need to apply configuration changes, join the gossip protocol, or persist
configuration to disk.

## Package breakdown

|   Package   |                                                                                                                                                                                                                                                                        What lives here                                                                                                                                                                                                                                                                        |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/`      | **Request/response types** (`ClusterConfigurationManagementRequest.*`), **transformers** (one per operation family — convert a request into a `List<Op>`), request sender/server/handler, topics enum, the `ConfigurationChangeCoordinator` contract (implemented by `zeebe-cluster-config`'s `ConfigurationChangeCoordinatorImpl`), the `ClusterConfigurationRequestsSerializer` contract (implemented by `zeebe-cluster-config`'s `ProtoBufSerializer`), and `ClusterConfigurationUpdateNotifier` (implemented by `zeebe-cluster-config`'s wiring classes). |
| `state/`    | **Immutable data model**: `ClusterConfiguration`, `MemberState`, `PartitionState`, `PartitionDistributorConfig`, `ClusterConfigurationChangeOperation` (sealed op hierarchy), `RoutingState`                                                                                                                                                                                                                                                                                                                                                                  |
| `protocol/` | Generated from `requests.proto`/`topology.proto` via `protobuf-maven-plugin` — pure data, no runtime logic.                                                                                                                                                                                                                                                                                                                                                                                                                                                   |

## Operational guides

- [Adding a new operation](../dynamic-config/docs/adding_new_operation.md) — spans this module and
  `zeebe/dynamic-config`.

