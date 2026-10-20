# zeebe/dynamic-config — Module Map

Manages cluster topology changes: membership, partition placement, exporter state, routing. Changes
flow through a coordinator that serializes requests, computes operations, and applies them one by
one across the cluster.

## Modules

|   Module   |                                                                                                    Purpose                                                                                                     |
|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `api/`     | Interfaces and models other modules compile against: the cluster state model, request/response types, executor interfaces, serializer contracts, `ClusterConfigurationManagerService` and the gateway service. |
| `impl/`    | Implementation: transformers, appliers, coordinator, gossip, request server/handler, the `*Impl` services and `ProtoBufSerializer`. Owns the protobuf definitions, an internal detail.                         |
| `openapi/` | The cluster and exporters OpenAPI specs and the models generated from them, plus the hand-written `BrokerId`.                                                                                                  |
| `rest/`    | The `cluster` and `exporters` actuator endpoints (`ClusterEndpoint`, `ExportersEndpoint`) and their mapping utilities.                                                                                         |

Other modules depend on `api` (and `openapi`/`rest` where needed), never on `impl` at compile time.
The composition roots (broker, dist, restore-standalone) construct the `*Impl` services directly;
debug-cli also reads the raw protobuf.

## Request flow

```
REST controller (rest/)
  └─ ClusterConfigurationManagementRequestSender   sends over Atomix ClusterCommunicationService
       └─ ClusterConfigurationRequestServer        receives, deserializes, dispatches
            └─ ClusterConfigurationManagementRequestsHandler   picks transformer, calls coordinator
                 └─ ConfigurationChangeCoordinatorImpl         simulate (dry-run) or apply
                      └─ *ConfigurationChangeAppliersImpl      per-op applier executes on broker (impl/changes/appliers/)
```

## Modules

The code is split into two Maven/Gradle modules. `api` owns `io.camunda.zeebe.dynamic.config`,
`impl` owns `io.camunda.zeebe.dynamic.config.impl`; the two never share a package, including the
generated protobuf classes (`io.camunda.zeebe.dynamic.config.impl.protocol`):

- `api/` (`zeebe-cluster-config-api`) — contracts and the data model: `state/`, request/response
  types, request sender, executor interfaces, serializer interfaces, the JSON serializer and the
  partition distributors. Modules that only read or send configuration depend on
  this module.
- `impl/` (`zeebe-cluster-config-impl`) — behavior: coordinator, appliers, transformers, request
  server and handler, partition reassigners, gossiper, initializers, `ProtoBufSerializer` and the
  protobuf sources.
  Composition roots (broker, `dist/`, restore, tests) depend on it and construct the
  implementations directly.

A new type belongs in `api/` when consumers outside the module must reference it. Otherwise put it
in `impl/`. `api/` must not depend on `impl/`. Shared test fixtures such as `ClusterTopologyDomain`
live in `api/src/test` and are shared with `impl/` through the `api` test-jar.

## Operational guides

- [Adding a new operation](./docs/adding_new_operation.md)

