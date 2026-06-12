```yaml
---
applyTo: "zeebe/broker-client/src/**"
---
```
# Zeebe Broker Client Module

## Purpose

This module provides the gateway-to-broker communication layer. It enables gateways (REST, gRPC, MCP) and the service layer to send commands to the correct broker partition leader, handle responses (success, rejection, error), manage cluster topology awareness, and collect request/topology metrics. It is consumed by `service/`, `zeebe/gateway-rest/`, `zeebe/gateway-grpc/`, `zeebe/broker/`, `authentication/`, and `dist/`.

## Architecture

The module has a strict **api/impl** split across two packages:

- `api/` — Public interfaces, exceptions, DTOs, and metrics contracts. Consumers depend only on this package.
- `impl/` — Internal implementations using the Zeebe actor model (`extends Actor`). Never reference `impl` types from outside this module.

### Data Flow

1. Consumer calls `BrokerClient.sendRequest(BrokerRequest<T>)` or `sendRequestWithRetry(...)`.
2. `BrokerClientImpl` delegates to `BrokerRequestManager` (an Actor).
3. `BrokerRequestManager.determineBrokerNodeIdProvider()` resolves the target broker:
   - If `request.getBrokerId()` is set → direct broker routing.
   - If `request.addressesSpecificPartition()` → route to that partition's leader.
   - If `request.requiresPartitionId()` → use `RequestDispatchStrategy` (default: round-robin) to pick a partition, then set it on the request.
   - Otherwise → random broker.
4. Request is serialized via SBE (`request.serializeValue()`) and sent over `ClientTransport` (Atomix messaging).
5. Response buffer is decoded by `BrokerRequest.getResponse()` into `BrokerResponse<T>`, `BrokerErrorResponse`, or `BrokerRejectionResponse`.
6. `BrokerRequestManager.handleResponse()` completes the `CompletableFuture` or wraps errors as `BrokerErrorException` / `BrokerRejectionException`.

### Topology Management

`BrokerTopologyManagerImpl` extends `Actor` and implements both `ClusterMembershipEventListener` (Atomix gossip) and `ClusterConfigurationUpdateListener` (dynamic config). It maintains a `BrokerClientTopologyImpl` record composed of:

- `LiveClusterState` — partition leaders/followers/inactive nodes, broker addresses, health, aggregated from `BrokerInfo` gossip properties.
- `ConfiguredClusterState` — cluster size, partition count, replication factor, cluster ID, sourced from `ClusterConfiguration`.

Leader election uses **term-based comparison**: a new leader is accepted only if its term is ≥ the current term for that partition (see `LiveClusterState.setPartitionLeader()`).

## Key Abstractions

| Type | Role |
|------|------|
| `BrokerClient` | Main entry point interface. Sends requests, exposes topology manager, subscribes to job notifications. |
| `BrokerRequest<T>` | Abstract base for all broker requests. Handles SBE serialization, response parsing, error detection. Subclass `BrokerExecuteCommand<T>` for command requests. |
| `BrokerResponse<T>` | Polymorphic response: check `isResponse()`, `isRejection()`, `isError()`. Subtypes: `BrokerErrorResponse`, `BrokerRejectionResponse`. |
| `BrokerTopologyManager` | Topology access and listener management. Returns `BrokerClusterState` (live) and `ClusterConfiguration` (configured). |
| `RequestDispatchStrategy` | Strategy interface for partition selection. Must be thread-safe. Default: `RoundRobinDispatchStrategy`. |
| `BrokerRequestManager` | Actor that orchestrates request dispatch, retry logic, timeout handling, and metrics recording. |
| `BrokerClientTopologyImpl` | Immutable record combining `LiveClusterState` + `ConfiguredClusterState`. Implements `BrokerClusterState`. |

## Exception Hierarchy

All exceptions extend `BrokerClientException` (unchecked). Use the specific type to distinguish failure modes:

- `BrokerErrorException` — broker returned an `ErrorCode` (e.g., `INTERNAL_ERROR`, `PARTITION_LEADER_MISMATCH`).
- `BrokerRejectionException` — command was rejected with a `RejectionType` and reason.
- `PartitionNotFoundException` — partition ID not found in known topology.
- `PartitionInactiveException` — partition has inactive nodes and no leader.
- `NoTopologyAvailableException` — gateway has no topology yet (startup transient).
- `RequestRetriesExhaustedException` — all retry attempts failed.
- `BrokerResponseException` / `IllegalBrokerResponseException` / `UnsupportedBrokerResponseException` — malformed or unexpected response.

## Retry Semantics

- `sendRequestWithRetry` uses `ClientTransport.sendRequestWithRetry()` with a response validation callback. Only `PARTITION_LEADER_MISMATCH` errors trigger retries; all other errors are returned immediately (see `BrokerRequestManager.responseValidation()`).
- `sendRequest` (without retry) sends exactly once.
- `RESOURCE_EXHAUSTED` errors are not counted as failed requests in metrics.

## Metrics

Metrics use Micrometer and are documented in `BrokerClientMetricsDoc`:

- `zeebe.gateway.request.latency` (Timer) — round-trip latency, tagged by `requestType` and `partition`.
- `zeebe.gateway.total.requests` (Counter) — total request count.
- `zeebe.gateway.failed.requests` (Counter) — failed requests, tagged with `error` code.
- `zeebe.gateway.topology.partition.roles` (Gauge) — partition role per broker (0=Follower, 3=Leader).

`BrokerClientRequestMetrics` and `BrokerClientTopologyMetrics` lazily register meters using `Table`/`Map3D` from `zeebe-util`.

## Extension Points

- **Custom dispatch strategy**: Implement `RequestDispatchStrategy` and return it from `BrokerRequest.requestDispatchStrategy()`. The strategy receives `BrokerTopologyManager` for topology-aware partition selection.
- **New command types**: Extend `BrokerExecuteCommand<T>` with a specific `ValueType` and `Intent`. Implement `toResponseDto(DirectBuffer)` to deserialize the response.
- **Topology listeners**: Implement `BrokerTopologyListener` and register via `BrokerTopologyManager.addTopologyListener()`.

## Invariants

- `BrokerRequestManager` runs as a single-threaded Actor — never call blocking operations inside it.
- `BrokerClientTopologyImpl` is volatile-published and rebuilt as a new immutable record on every topology change; never mutate existing instances.
- `RequestDispatchStrategy` implementations must be thread-safe (called from actor thread).
- Partition leader updates are term-guarded: a leader with a lower term than the current known leader is ignored.
- Always call `request.serializeValue()` before sending (done automatically by `BrokerRequestManager`).

## Common Pitfalls

- Do not reference `impl` package types from outside this module; depend only on `api` interfaces.
- When adding new `ErrorCode` handling in `BrokerRequestManager`, ensure the error is registered in metrics via `registerFailure()` with the correct `AdditionalErrorCodes` or protocol `ErrorCode`.
- The `BrokerAddressProvider.get()` may return `null` if topology is unavailable — transport layer handles this.
- Tests use `StubBroker` (from `zeebe-protocol-test-util`) and `ControlledActorSchedulerExtension` (from `zeebe-scheduler`) for deterministic actor scheduling. Use `TestTopologyManager` for unit tests that don't need the full actor lifecycle.

## Key Reference Files

- `api/BrokerClient.java` — primary consumer-facing interface
- `impl/BrokerRequestManager.java` — core request dispatch, routing, retry, and metrics logic
- `impl/BrokerTopologyManagerImpl.java` — topology aggregation from gossip and cluster config
- `impl/BrokerClientTopologyImpl.java` — immutable topology state record with `LiveClusterState`/`ConfiguredClusterState`
- `impl/RoundRobinDispatchStrategy.java` — default partition selection with routing-state-aware partition ring