```yaml
---
applyTo: "zeebe/gateway/src/**"
---
```
# Zeebe Gateway Core Module

## Purpose
The `zeebe-gateway` module provides the shared gateway infrastructure for routing client commands to the correct broker partition, handling job activation with long polling, and managing gateway configuration. It sits between the protocol-specific API layers (`gateway-grpc`, `gateway-rest`) and the broker cluster, serving as the command dispatch and job activation engine. It does NOT contain REST/gRPC endpoint implementations — those live in `zeebe/gateway-rest/` and `zeebe/gateway-grpc/`.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  gateway-grpc / gateway-rest (consumers)            │
└────────────┬───────────────────────┬────────────────┘
             │                       │
   ┌─────────▼──────────┐  ┌────────▼───────────────┐
   │ RequestRetryHandler │  │ ActivateJobsHandler<T> │
   │ (dispatch + retry)  │  │ (long polling + R/R)   │
   └─────────┬──────────┘  └────────┬───────────────┘
             │                       │
   ┌─────────▼──────────────────────▼────────────────┐
   │           BrokerRequest<T> subclasses            │
   │  (Broker*Request → BrokerExecuteCommand<T>)      │
   └─────────────────────┬───────────────────────────┘
                         │
   ┌─────────────────────▼───────────────────────────┐
   │         BrokerClient (zeebe-broker-client)       │
   └─────────────────────────────────────────────────┘
```

### Four layers:
1. **`cmd/`** — Gateway-side exception hierarchy (`ClientException` → `ClientResponseException`, tenant exceptions)
2. **`impl/broker/`** — Request dispatch strategies and retry logic; `impl/broker/request/` contains ~50 `Broker*Request` classes
3. **`impl/job/`** — Job activation pipeline: `ActivateJobsHandler` interface → `RoundRobinActivateJobsHandler` → `LongPollingActivateJobsHandler`
4. **`impl/configuration/`** — POJO configuration hierarchy rooted at `GatewayCfg`

## Key Abstractions

### Broker Request Pattern
Every broker request extends `BrokerExecuteCommand<T>` (from `zeebe-broker-client`). Follow this template exactly when adding new requests:
- Constructor calls `super(ValueType.XXX, XxxIntent.ACTION)`
- Holds a private `final XxxRecord requestDto` field
- Fluent setters that modify `requestDto` and return `this`
- `getRequestWriter()` returns `requestDto`
- `toResponseDto(DirectBuffer)` creates a new record and calls `wrap(buffer)`
- Override `requestDispatchStrategy()` only if the request needs hash-based routing (see `BrokerPublishMessageRequest`, `BrokerCreateProcessInstanceRequest`)

Reference: `impl/broker/request/BrokerActivateJobsRequest.java`, `impl/broker/request/BrokerPublishMessageRequest.java`

### Dispatch Strategies
- `HashBasedDispatchStrategy` — deterministic partition routing by hashing a key (business ID or correlation key). Supports both `RoutingState` (new) and legacy topology.
- `PublishMessageDispatchStrategy` — delegates to `HashBasedDispatchStrategy` with the correlation key.
- Requests with a dispatch strategy are sent to a fixed partition with NO retry on other partitions. Requests without a strategy use round-robin with partition-failover retry.

Reference: `impl/broker/RequestRetryHandler.java` (lines 88–95)

### Job Activation Pipeline
- `ActivateJobsHandler<T>` — interface extending `Consumer<ActorControl>` for actor-based async job activation.
- `RoundRobinActivateJobsHandler<T>` — iterates partitions round-robin per job type; handles message-size limits by failing jobs that exceed `maxMessageSize`.
- `LongPollingActivateJobsHandler<T>` — wraps round-robin handler; keeps requests open when no jobs are available. Subscribes to `jobsAvailable` notifications from broker. Uses probe mechanism to periodically retry blocked requests.
- `InflightActivateJobsRequest<T>` — tracks lifecycle (open/completed/timed-out/aborted/canceled) and sends results via `ResponseObserver<T>`.
- `InFlightLongPollingActivateJobsRequestsState<T>` — per-job-type state tracking active, pending, and to-be-repeated requests with failed-attempt counting.

Reference: `impl/job/LongPollingActivateJobsHandler.java` (includes ASCII flow diagram in Javadoc)

### Configuration
`GatewayCfg` aggregates: `NetworkCfg`, `ClusterCfg`, `ThreadsCfg`, `SecurityCfg`, `LongPollingCfg`, `List<InterceptorCfg>`, `List<FilterCfg>`. All config classes are POJOs with fluent setters. Defaults are centralized in `ConfigurationDefaults`. `InterceptorCfg` and `FilterCfg` extend `BaseExternalCodeCfg` for isolated classloader loading of external JARs.

## Relationships to Other Modules

| Module | Relationship |
|--------|-------------|
| `zeebe-broker-client` | Provides `BrokerClient`, `BrokerExecuteCommand<T>`, `RequestDispatchStrategy`, `PartitionIdIterator` |
| `zeebe-protocol` / `zeebe-protocol-impl` | Provides `ValueType`, intents, and SBE-generated record types (`JobBatchRecord`, `ProcessInstanceCreationRecord`, etc.) |
| `zeebe-gateway-grpc` | Consumes this module; wires `LongPollingActivateJobsHandler` for gRPC streaming |
| `zeebe-gateway-rest` | Consumes this module; uses broker request classes and `RequestRetryHandler` |
| `service/` | Consumes broker request classes for command dispatch |
| `zeebe-scheduler` | Provides `ActorControl`, `ScheduledTimer` for actor-based concurrency |
| `zeebe-dynamic-config` | Provides `RoutingState` consumed by `HashBasedDispatchStrategy` |

## Extension Points

### Adding a new broker request
1. Create `BrokerXxxRequest` in `impl/broker/request/` extending `BrokerExecuteCommand<XxxRecord>`
2. Constructor: `super(ValueType.XXX, XxxIntent.ACTION)`. Use `setPartitionId(Protocol.DEPLOYMENT_PARTITION)` for global operations (users, tenants, roles, groups).
3. Override `requestDispatchStrategy()` returning `Optional.of(new HashBasedDispatchStrategy(...))` only if the request must route to a specific partition by key.
4. The response DTO in `toResponseDto()` must be a fresh instance — never reuse `requestDto`.

### Adding a new configuration property
Add the field with a default to the appropriate `*Cfg` class. Add the default constant to `ConfigurationDefaults`. Update `GatewayCfg` if it is a new top-level config section. Add test coverage in `GatewayCfgTest` using a YAML fixture in `test/resources/configuration/`.

## Invariants

- Never retry requests with an explicit `RequestDispatchStrategy` on other partitions — the routing guarantee (per-partition uniqueness) would be violated.
- `RequestRetryHandler` only retries on `ConnectException`, `PARTITION_LEADER_MISMATCH`, or `RESOURCE_EXHAUSTED` errors.
- Long polling requests with a negative timeout value disable long polling (`isLongPollingDisabled()`).
- Job activation actors must run all state mutations through `actor.run()` or `actor.submit()` for thread safety.
- `toResponseDto()` must always create a new record instance and `wrap(buffer)` — never return the request DTO.

## Common Pitfalls

- Forgetting to override `requestDispatchStrategy()` when a request requires hash-based routing (messages, process instances with business ID).
- Mutating `InflightActivateJobsRequest` state outside the actor thread — all mutations must go through `actor.run()`.
- Reusing the `requestDto` in `toResponseDto()` — this corrupts the request state.
- Adding new config without a default in `ConfigurationDefaults` — tests loading empty YAML will fail.
- Using `ConcurrentHashMap` in `RoundRobinActivateJobsHandler` and `LongPollingActivateJobsHandler` is intentional — these maps are accessed from both actor and external threads.

## Testing Patterns

- Tests use `StubbedBrokerClient` (handler registry pattern) and `StubbedTopologyManager` for topology.
- Dispatch strategy tests verify partition selection via `determinePartition()` with configurable partition counts.
- `RequestRetryHandlerTest` uses `AtomicReference` to capture async callback results.
- Configuration tests load YAML fixtures from `test/resources/configuration/` and compare against expected `GatewayCfg` objects.
- Use `LongPollingMetrics.noop()` in tests to avoid meter registry setup.
- Test module publishes a test-jar (`maven-jar-plugin` test-jar goal) so stubs are reusable by `gateway-grpc` and `gateway-rest`.

## Key Files

- `impl/broker/RequestRetryHandler.java` — core dispatch + retry logic
- `impl/job/LongPollingActivateJobsHandler.java` — long polling state machine with ASCII diagram
- `impl/job/RoundRobinActivateJobsHandler.java` — partition-round-robin job activation
- `impl/broker/HashBasedDispatchStrategy.java` — deterministic hash routing
- `impl/broker/request/BrokerCreateProcessInstanceRequest.java` — exemplary request with optional dispatch strategy
- `impl/configuration/GatewayCfg.java` — root configuration
- `RequestUtil.java` — JSON-to-MsgPack conversion utility