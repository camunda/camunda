```yaml
---
applyTo: "zeebe/protocol-test-util/src/**"
---
```
# Zeebe Protocol Test Utilities

## Module Purpose

This module provides test infrastructure for the Zeebe wire protocol (SBE-encoded commands/responses). It has two main capabilities: (1) `ProtocolFactory` — a deterministic, seed-based randomized `Record` generator used by exporters, serialization tests, and protocol compatibility checks; (2) a stub broker and command API client for integration-testing the binary protocol transport layer. This is a **test library** — it lives in `main/` so other test scopes across the monorepo can depend on it.

## Architecture

The module is split into three packages under `io.camunda.zeebe.test.broker.protocol`:

### Root Package — Record Generation
- **`ProtocolFactory`** — Central class. Uses EasyRandom to produce fully-populated, deterministic `Record<T>` instances. Ensures `valueType`, `value`, and `intent` are semantically consistent (e.g., `ValueType.JOB` → `JobRecordValue` + `JobIntent`). Uses classpath scanning (`ClassGraph`) to discover `@ImmutableProtocol`-annotated interfaces and map them to their `@ImmutableProtocol.Type` implementations.
- **`ImplRecordValuePopulator`** — Reflection-based populator for `protocol-impl` record classes (MsgPack property types: `StringProperty`, `LongProperty`, `ArrayProperty`, `EnumProperty`, `DocumentProperty`, `ObjectProperty`, etc.). Called by `ProtocolFactory.generateRecordWithImplValue()` for tests needing concrete implementation classes rather than immutable protocol interfaces.
- **`EnumRandomizer`** — Deterministic enum randomizer that works around a bug in EasyRandom's built-in enum randomizer (cannot exclude values AND be deterministic simultaneously).
- **`MsgPackHelper`** — Jackson ObjectMapper configured with MessagePack format for encoding/decoding `Map<String, Object>` payloads used in the SBE command protocol.

### `brokerapi` Package — Stub Broker
A lightweight in-process broker stub for testing the Atomix/Netty-based transport layer:
- **`StubBroker`** — Starts an `AtomixCluster` + `ServerTransport`, subscribes a `StubRequestHandler` to a partition. Use `onExecuteCommandRequest(ValueType, Intent)` to register response stubs.
- **`StubRequestHandler`** — Decodes incoming SBE `ExecuteCommandRequest` messages, matches them against registered `ResponseStub` instances (most-recently-added wins), and writes back responses.
- **Builder chain**: `ExecuteCommandResponseTypeBuilder` → `ExecuteCommandResponseBuilder` / `ErrorResponseBuilder` → `register()` or `registerControlled()`.
- **`ResponseController`** — Uses `CyclicBarrier(2)` to synchronize test thread and broker response thread for controlled response timing.
- **`MessageBuilder<T>`** — Interface extending `BufferWriter` with `initializeFrom(T)` and `beforeResponse()` hooks.

### `commandapi` Package — Test Client
A command-level client for sending SBE-encoded requests to a real or stub broker:
- **`CommandApiRule`** (JUnit 4 `ExternalResource`) — Connects to an `AtomixCluster`, discovers topology via `BrokerInfo`, and provides `createCmdRequest()` and `partitionClient()`.
- **`PartitionTestClient`** — High-level test API wrapping `CommandApiRule` with convenience methods for deploying processes, creating process instances, activating/completing jobs, publishing messages, resolving incidents, and querying `RecordingExporter` streams.
- **`ExecuteCommandRequest`** (in `commandapi`) — `ClientRequest` implementation that SBE-encodes a command and sends it via `ClientTransport`.
- **`ExecuteCommandResponse`** — Decodes SBE response, extracts MsgPack value, and supports `readInto(BufferReader)` for typed deserialization.

## Key Relationships

| Dependency | Role |
|---|---|
| `zeebe-protocol` | `Record`, `RecordValue`, `ValueType`, `Intent` interfaces, SBE codecs |
| `zeebe-protocol-impl` | `UnifiedRecordValue`, concrete record classes (`JobRecord`, `DeploymentRecord`) |
| `zeebe-transport` | `ClientTransport`, `ServerTransport`, `RequestHandler` for network layer |
| `zeebe-scheduler` | `ActorScheduler` for async transport operations |
| `zeebe-test-util` | `RecordingExporter`, `SocketUtil`, `MapBuilder`, `MsgPackUtil` |
| `zeebe-atomix-cluster` | `AtomixCluster` for cluster topology in stub broker |
| `easy-random-core` | Randomized object generation engine |
| `classgraph` | Classpath scanning for `@ImmutableProtocol` types |

**Consumers**: `zeebe/broker`, `zeebe/broker-client`, all exporter modules (`elasticsearch-exporter`, `opensearch-exporter`, `rdbms-exporter`, `camunda-exporter`, `app-integrations-exporter`), `zeebe/protocol-jackson`, `qa/acceptance-tests`.

## Design Patterns

- **Deterministic Randomization**: `ProtocolFactory` defaults to seed `0` for reproducible tests. Always log/expose the seed via `getSeed()` for failure reproduction. Use `ProtocolFactory(long)` with `ThreadLocalRandom.nextLong()` for non-deterministic fuzz testing.
- **Builder Pattern with Registration**: Stub responses use a fluent builder chain ending in `register()` (fire-and-forget) or `registerControlled()` (returns `ResponseController` for synchronization). Recent stubs override older ones (prepended to list).
- **SBE Encode/Decode Symmetry**: Request classes use SBE decoders (`ExecuteCommandRequestDecoder`); response writers use SBE encoders (`ExecuteCommandResponseEncoder`). Always pair header + body encoding.

## Extension Points

- **Adding new record value types**: `ProtocolFactory` auto-discovers `@ImmutableProtocol` types via classgraph. Add the new `ValueType` to `ValueTypeMapping` in `zeebe-protocol` and it will be picked up automatically. If the impl class has unusual property types, add a custom randomizer in `registerRandomizers()`.
- **Adding new MsgPack property types**: Extend `ImplRecordValuePopulator.populate()` with a new `else if` branch for the property type. Fail explicitly for unsupported types — never silently skip.
- **Custom randomizers**: Use `factory.registerRandomizer(fieldPredicate, random -> value)` to override generation for specific fields.

## Invariants

- `ProtocolFactory` never generates `ValueType.NULL_VAL`, `ValueType.SBE_UNKNOWN`, `RecordType.NULL_VAL`, or `RecordType.SBE_UNKNOWN` — these are excluded in `registerRandomizers()`.
- All generated `long` values are non-negative (0 to `Long.MAX_VALUE`) because many protocol longs represent timestamps.
- String fields ending with `"Date"` suffix are always formatted as ISO zoned date-time strings.
- `valueType`, `value`, and `intent` on a generated `Record` are always mutually consistent per `ValueTypeMapping`.
- `StubRequestHandler` throws `RuntimeException` if no stub matches an incoming request — never silently drops messages.

## Common Pitfalls

- Do not modify `ProtocolFactory` randomizer registration order — it changes the deterministic output for all downstream tests with seed `0`.
- `ImplRecordValuePopulator` uses reflection to bypass immutability. If a new impl class lacks a no-arg constructor, generation will fail with `IllegalArgumentException`.
- `CommandApiRule` is JUnit 4 (`ExternalResource`). Do not use with JUnit 5 `@ExtendWith` — use `@Rule` annotation.
- `StubBroker.close()` must be called to release the Atomix cluster and server transport — use `AutoCloseable` / try-with-resources.
- The `commandapi.ExecuteCommandRequest` (client-side) and `brokerapi.ExecuteCommandRequest` (server-side) are different classes in different packages — do not confuse them.

## Key Reference Files

- `ProtocolFactory.java` — Core randomized record generator, ~600 lines
- `ImplRecordValuePopulator.java` — Reflection-based MsgPack property populator
- `StubBroker.java` — Lightweight stub broker with Atomix transport
- `PartitionTestClient.java` — High-level test client for command API interactions
- `ProtocolFactoryTest.java` — Comprehensive test demonstrating determinism, value type coverage, and impl class population