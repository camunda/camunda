```yaml
---
applyTo: "zeebe/atomix/utils/**"
---
```
# Atomix Utils — Foundational Utilities for Cluster Infrastructure

## Module Purpose

This module (`zeebe-atomix-utils`) provides low-level utility abstractions originally forked from the Atomix project. It supplies Kryo-based serialization, single-threaded execution contexts, typed event dispatching, network address modeling, and generic identity/builder/config interfaces used throughout the Atomix cluster layer (Raft consensus, SWIM membership, Netty messaging) and the broader Zeebe broker/gateway infrastructure.

## Package Layout

- `io.atomix.utils` — Core marker interfaces (`Identifier`, `Named`, `Type`, `Builder`, `Managed`) and `Version`
- `io.atomix.utils.concurrent` — Thread context model (`ThreadContext`, `SingleThreadContext`, `AtomixThread`, `Scheduler`, `OrderedFuture`)
- `io.atomix.utils.serializer` — Kryo serialization framework (`Namespace`, `Serializer`, `Namespaces`, `CompatibleKryoPool`)
- `io.atomix.utils.serializer.serializers` — Custom Kryo serializers for Guava immutable collections, atomics, `ByteBuffer`
- `io.atomix.utils.event` — Observer pattern (`Event`, `EventListener`, `ListenerRegistry`, `AbstractListenerManager`)
- `io.atomix.utils.net` — Network address abstraction (`Address`, `AddressInitializations`)
- `io.atomix.utils.config` — Configuration marker interfaces (`Config`, `NamedConfig`, `TypedConfig`, `Configured`)
- `io.atomix.utils.misc` — Small helpers (`TimestampPrinter`, `StringUtils`, `ArraySizeHashPrinter`)

## Key Abstractions

### Thread Context (`concurrent` package)
- `ThreadContext` extends `Executor` + `Scheduler` + `CloseableSilently`. It represents a single-threaded execution context tied to an `AtomixThread` via `WeakReference`. Use `ThreadContext.currentContext()` to retrieve the context from the current thread.
- `SingleThreadContext` is the primary implementation wrapping a `ScheduledThreadPoolExecutor(1)`. It uses `FatalErrorHandler` and an uncaught exception observer to prevent exceptions from being silently swallowed by the executor.
- All threads in the Atomix layer MUST be `AtomixThread` instances created via `AtomixThreadFactory`. The factory injects the `actor-scheduler` MDC key for structured logging.
- `OrderedFuture<T>` wraps `CompletableFuture` to guarantee FIFO callback ordering (standard `CompletableFuture` uses LIFO). Used extensively in Raft protocol communication.
- `Scheduler` provides one-shot and fixed-rate scheduling plus `retryUntilSuccessful()` for retry-with-backoff patterns using `RetryDelayStrategy` from `zeebe-util`.

### Kryo Serialization (`serializer` package)
- `Namespace` is a pooled Kryo serialization context. It manages thread-safe pools of `Kryo`, `Input`, and `ByteArrayOutput` instances. Registration IDs must be stable across versions for wire compatibility.
- `Namespace.Builder` registers types with explicit IDs via `nextId()` and `register()`. Use `FLOATING_ID` only when ID stability is not required.
- `CompatibleKryoPool` configures Kryo with `CompatibleFieldSerializer` for forward compatibility — unknown fields are skipped during deserialization via chunked encoding.
- `Namespaces.BASIC` is the pre-built namespace registering common JDK and Guava types. User registrations start at `BEGIN_USER_CUSTOM_ID` (500).
- `Serializer` is a thin `encode`/`decode` interface backed by a `Namespace`. Create via `Serializer.using(namespace)` or `Serializer.builder().build()`.

### Event System (`event` package)
- `Event<T, S>` — timestamped event with type `T` (typically an enum) and subject `S`.
- `EventListener<E>` — functional interface extending `EventFilter` with `isRelevant()` for selective dispatch.
- `ListenerRegistry` uses `CopyOnWriteArraySet` for thread-safe listener storage and catches exceptions per-listener during dispatch.
- `AbstractListenerManager` delegates to `ListenerRegistry` — extend this to create event-emitting components (e.g., `SwimMembershipProtocol`, `NodeDiscoveryService`).

### Network Address (`net` package)
- `Address` encapsulates host + port with lazy DNS resolution via `tryResolveAddress()`. Default port is 5679.
- `AddressInitializations` computes the default advertised host with IPv4/IPv6 preference heuristics using Netty's `NetUtil`.

## Consumer Modules

This module is a transitive dependency for most of the Zeebe cluster infrastructure:
- `zeebe/atomix/cluster` — Raft, SWIM, messaging (heaviest consumer)
- `zeebe/broker` — partition management, inter-partition communication
- `zeebe/gateway`, `zeebe/gateway-grpc` — cluster configuration
- `zeebe/transport` — Atomix-based transport layer
- `zeebe/broker-client` — broker request routing
- `configuration/` — gateway/broker config mapping
- `service/` — topology services

## Design Patterns

- **Object Pooling**: `Namespace` pools Kryo/Input/Output instances via Kryo's `Pool` class. Buffers exceeding `MAX_POOLED_BUFFER_SIZE` (512KB) are discarded rather than returned to pool.
- **Builder Pattern**: `Namespace.Builder` for Kryo registration, `Builder<T>` interface for generic builders.
- **Observer Pattern**: `ListenerRegistry` + `AbstractListenerManager` for typed event dispatch.
- **Thread-per-Context**: One-to-one mapping between `AtomixThread` and `ThreadContext` via `WeakReference`.

## Invariants

- Every thread used in Atomix infrastructure MUST be an `AtomixThread`. `SingleThreadContext` constructor asserts this with `checkState`.
- Kryo registration IDs are part of the wire protocol. Never change existing IDs in `Namespaces.BASIC` or in `RaftNamespaces` — this breaks rolling upgrades.
- `Namespace` uses `registrationRequired(true)` — serializing an unregistered class throws. Always register types before use.
- `CompatibleFieldSerializer` with chunked encoding is the default — this enables forward compatibility but requires that deleted fields use placeholder registrations (see `Void.class` entries in `Namespaces.BASIC`).
- `SingleThreadContext.close()` calls `shutdownNow()` and waits up to 30 seconds. Always close contexts to avoid thread leaks.

## Common Pitfalls

- Adding a new type to `Namespaces.BASIC` without appending at the end shifts subsequent IDs — use `Void.class` placeholders for removed entries instead.
- Forgetting to register a type in the Kryo namespace causes `KryoException` at runtime, not compile time.
- `OrderedFuture` synchronizes on its internal queue — do not perform blocking operations inside callbacks chained on it.
- `Address.equals()` compares host string + port, not resolved IP. Two addresses with different hostnames resolving to the same IP are NOT equal.

## Essential Reference Files

- `src/main/java/io/atomix/utils/concurrent/SingleThreadContext.java` — primary thread context implementation with error handling
- `src/main/java/io/atomix/utils/serializer/Namespace.java` — Kryo namespace with pooling and registration blocks
- `src/main/java/io/atomix/utils/serializer/Namespaces.java` — BASIC namespace with all default type registrations
- `src/main/java/io/atomix/utils/serializer/CompatibleKryoPool.java` — forward-compatible Kryo factory
- `src/main/java/io/atomix/utils/event/ListenerRegistry.java` — thread-safe event dispatch

## Testing

Run scoped tests: `./mvnw -pl zeebe/atomix/utils -am test -DskipITs -DskipChecks -T1C`

This module uses both JUnit 4 and JUnit 5. Use AssertJ for assertions and Awaitility for async assertions. Tests live in `src/test/java/io/atomix/utils/`.