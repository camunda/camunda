```yaml
---
applyTo: "zeebe/qa/util/**"
---
```
# Zeebe QA Utilities Module

Test infrastructure module providing in-process Spring application wrappers, Feign-based actuator clients, custom AssertJ assertions, and JUnit 5 lifecycle extensions for Zeebe integration tests. This is a **compile-scope library** consumed by `zeebe/qa/integration-tests/`, `qa/acceptance-tests/`, and other IT modules — it is not a test dependency itself.

## Architecture

The module is organized into five sub-packages:

```
io.camunda.zeebe.qa.util
├── cluster/         # In-process Spring application wrappers and cluster builder
├── actuator/        # Feign HTTP clients for Spring Boot Actuator endpoints
├── junit/           # JUnit 5 extension and annotations for lifecycle management
├── topology/        # Custom AssertJ assertions for cluster topology
├── jobstream/       # Custom AssertJ assertions for job stream actuator
├── testcontainers/  # Testcontainers helpers (Docker image defaults, Toxiproxy registry)
io.camunda.zeebe.it.util
├── AuthorizationsUtil    # Authorization + user creation helper for auth IT tests
├── SearchClientsUtil     # Factory for low-level ES/OS search clients in tests
```

## Cluster Package — Core Abstraction

### Type Hierarchy

- `TestApplication<T>` — top-level interface for any test node (start/stop/health/beans/properties). See `cluster/TestApplication.java`.
- `TestGateway<T> extends TestApplication<T>` — adds `grpcAddress()`, `restAddress()`, `newClientBuilder()`, `awaitCompleteTopology()`. See `cluster/TestGateway.java`.
- `TestSpringApplication<T>` — abstract base that boots a real Spring `ConfigurableApplicationContext`. Manages bean overrides via `ContextOverrideInitializer`, property overrides, profile activation, and random port assignment. See `cluster/TestSpringApplication.java`.
- `TestStandaloneApplication<T> extends TestApplication<T>, TestGateway<T>` — interface adding exporter, secondary storage, and security config support. See `cluster/TestStandaloneApplication.java`.
- `TestStandaloneBroker extends TestSpringApplication implements TestGateway, TestStandaloneApplication` — boots a `BrokerModuleConfiguration`. Pre-configures unified config with test-friendly defaults (small segments, fast membership, disabled flushing, random ports via `SocketUtil`). See `cluster/TestStandaloneBroker.java`.
- `TestStandaloneGateway extends TestSpringApplication implements TestGateway` — boots a `GatewayModuleConfiguration`. See `cluster/TestStandaloneGateway.java`.
- `TestRestoreApp extends TestSpringApplication` — boots a `RestoreApp` for backup/restore tests. See `cluster/TestRestoreApp.java`.
- `TestCluster` — aggregates multiple `TestStandaloneBroker` and `TestStandaloneGateway` instances into a cluster with parallel start/shutdown and topology awaiting. See `cluster/TestCluster.java`.
- `TestClusterBuilder` — fluent builder for `TestCluster` with broker count, gateway count, partitions, replication factor, embedded gateway toggle, and per-node configuration callbacks. See `cluster/TestClusterBuilder.java`.

### Configuration Pattern

Configure nodes via the unified config API (`withUnifiedConfig(uc -> ...)`) or Spring properties (`withProperty(key, value)`). Prefer `withUnifiedConfig` for `camunda.*` namespace settings. Use `withBean(qualifier, bean, type)` to inject beans before the application context starts — these are registered via `ContextOverrideInitializer` which calls `beanFactory.registerResolvableDependency()` and `beanFactory.registerSingleton()`.

### Key Enums

- `TestZeebePort` — COMMAND (26501), GATEWAY (26500), CLUSTER (26502), REST (8080), MONITORING (9600). See `cluster/TestZeebePort.java`.
- `TestHealthProbe` — LIVE, READY, STARTED. See `cluster/TestHealthProbe.java`.

## Actuator Package — Feign HTTP Clients

All actuator interfaces follow the same pattern: a Feign-annotated interface with static `of(TestApplication)`, `of(ZeebeNode)`, and `of(String)` factory methods. Each factory builds a Feign client with `JacksonEncoder`/`JacksonDecoder` (with `Jdk8Module` and `JavaTimeModule`) and `Retryer.NEVER_RETRY`.

Available actuators:
- `HealthActuator` (base interface) / `BrokerHealthActuator` / `GatewayHealthActuator` — health probes
- `PartitionsActuator` — query/pause/resume exporting and processing, take snapshots
- `ClusterActuator` — topology queries, broker scaling, partition joins/leaves, purge (uses `ApacheHttpClient` for PATCH support)
- `BackupActuator` — trigger/query/delete backups with custom `ErrorHandler`
- `ExportersActuator` — enable/disable/delete exporters
- `ExportingActuator` — pause/resume exporting
- `ActorClockActuator` — pin/add/reset controllable clock (requires `zeebe.clock.controlled=true`)
- `JobStreamActuator` — list client/remote job streams
- `BanningActuator`, `FlowControlActuator`, `GetFlowControlActuator`, `LoggersActuator`, `PrometheusActuator`, `RebalanceActuator`

When adding a new actuator, follow the existing pattern: define a Feign interface, add `@RequestLine` + `@Headers` annotations, and provide `of(TestApplication)` and `of(String)` static factories.

## JUnit Extension — `@ZeebeIntegration`

- Annotate test class with `@ZeebeIntegration` to register `ZeebeIntegrationExtension`.
- Annotate `TestCluster` or `TestApplication` fields with `@ZeebeIntegration.TestZeebe`.
- Static fields are managed per-class (started once in `beforeAll`); instance fields per-test.
- The extension creates temporary working directories for brokers, manages `RecordingExporter.reset()` before each test, and logs records on failure via `RecordLogger`.
- Annotation attributes: `autoStart`, `awaitReady`, `awaitStarted`, `awaitCompleteTopology`, `clusterSize`, `partitionCount`, `replicationFactor`, `topologyTimeoutMs`, `initMethod`, `purgeAfterEach`.
- `purgeAfterEach=true` (default) triggers a data purge via `ClusterActuator.purge()` between tests on shared (static) instances.
- See `junit/ZeebeIntegration.java` and `junit/ZeebeIntegrationExtension.java`.

## Custom AssertJ Assertions

- `ClusterActuatorAssert` — assert on topology state (broker presence, partition state, completed changes, pending changes). Use `ClusterActuatorAssert.assertThat(actuator)`. See `topology/ClusterActuatorAssert.java`.
- `JobStreamActuatorAssert` — assert on client/remote job streams (job type, worker, connections, consumer count, timeout, fetch variables, tenant filter). Provides nested `ClientJobStreamsAssert` and `RemoteJobStreamsAssert`. See `jobstream/JobStreamActuatorAssert.java` and `jobstream/AbstractJobStreamsAssert.java`.

## Common Pitfalls

- Never configure brokers after calling `TestCluster.start()` — unified config is read at Spring context creation time.
- Always use `withUnifiedConfig()` for `camunda.*` properties instead of `withProperty()` when possible — `withProperty()` sets Spring properties that may not propagate to the unified config beans.
- The `TestStandaloneBroker` constructor disables authorizations and schema creation by default; enable them explicitly with `withAuthorizationsEnabled()` or `withCreateSchema(true)`.
- When testing with Elasticsearch, call `withCamundaExporter(esUrl)` which also registers `SearchEngineConnectProperties` beans and enables schema creation.
- Ports are randomly assigned via `SocketUtil.getNextAddress()` in constructors; do not hardcode port numbers.
- `ClusterActuator` requires `ApacheHttpClient` because the default Feign HTTP client does not support PATCH requests.
- `ContextOverrideInitializer` registers beans before Spring autowiring; beans added after `start()` have no effect.

## Extension Points

- **New actuator**: Create a Feign interface in `actuator/` with static `of()` factories following the `PartitionsActuator` pattern.
- **New assertion**: Extend `AbstractObjectAssert` (single value) or `AbstractCollectionAssert` (collection) in `topology/` or `jobstream/`.
- **New application type**: Extend `TestSpringApplication<T>` and implement `TestApplication<T>` (see `TestRestoreApp` as a minimal example).
- **New cluster configuration**: Add builder methods in `TestClusterBuilder` and corresponding `with*()` methods on `TestStandaloneBroker`/`TestStandaloneGateway`.

## Key Files

| File | Role |
|------|------|
| `cluster/TestApplication.java` | Root interface — start/stop/health/beans/properties contract |
| `cluster/TestStandaloneBroker.java` | Full broker wrapper with unified config, security, exporters |
| `cluster/TestCluster.java` | Multi-node cluster with parallel start and topology awaiting |
| `cluster/TestClusterBuilder.java` | Fluent builder for cluster topology configuration |
| `cluster/TestSpringApplication.java` | Abstract base — Spring context lifecycle and bean/property injection |
| `junit/ZeebeIntegrationExtension.java` | JUnit 5 extension managing lifecycle, directories, and purge |
| `actuator/ClusterActuator.java` | Feign client for cluster topology management actuator |
| `topology/ClusterActuatorAssert.java` | AssertJ assertions for cluster topology state |

## Build and Test

```bash
# Build this module
./mvnw -pl zeebe/qa/util -am install -Dquickly -T1C

# Run the module's own tests
./mvnw -pl zeebe/qa/util -am test -DskipITs -DskipChecks -T1C
```