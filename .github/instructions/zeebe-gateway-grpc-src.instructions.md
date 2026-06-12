```yaml
---
applyTo: "zeebe/gateway-grpc/src/**"
---
```
# zeebe/gateway-grpc — gRPC API Gateway

This module implements the gRPC API gateway for the Zeebe process engine. It translates protobuf-defined gRPC requests into internal broker requests, dispatches them via `BrokerClient`, and maps broker responses back to gRPC protobuf responses. It also handles authentication, health management, interceptor loading, and job streaming.

## Architecture

The request flow is a strict pipeline with four layers:

1. **gRPC Service** (`GatewayGrpcService`) — extends generated `GatewayImplBase`, wraps each `StreamObserver` in `ErrorMappingStreamObserver`, delegates to `EndpointManager`
2. **Endpoint Manager** (`EndpointManager`) — orchestrates request mapping, auth claims injection, broker dispatch, and response mapping for each API operation
3. **Request/Response Mappers** (`RequestMapper`, `ResponseMapper`) — stateless converters between protobuf types and internal broker request/response types
4. **Error Mapping** (`GrpcErrorMapper`) — maps `Throwable` exceptions to gRPC `Status` codes with appropriate logging levels

```
Client → GatewayGrpcService → EndpointManager → RequestMapper → BrokerClient
                                                                      ↓
Client ← ErrorMappingStreamObserver ← ResponseMapper ← BrokerResponse
```

## Key Abstractions

- **`Gateway`** (`Gateway.java`) — lifecycle manager. Builds the Netty gRPC server, configures TLS, creates the ForkJoinPool executor, wires interceptors, and manages `ActivateJobsHandler` and `StreamJobsHandler` actors. Implements `CloseableSilently` with graceful 30s shutdown.
- **`GatewayGrpcService`** (`GatewayGrpcService.java`) — thin delegation layer. Every RPC method wraps the observer in `ErrorMappingStreamObserver.ofStreamObserver()` then calls the corresponding `EndpointManager` method. Never add business logic here.
- **`EndpointManager`** (`EndpointManager.java`) — core dispatch hub. Uses `sendRequest()` for standard broker calls and `sendRequestWithRetryPartitions()` for partition-scoped retries (e.g., `createProcessInstance`). Extracts auth claims from gRPC `Context` via `getClaims()` and attaches them to every `BrokerRequest`.
- **`RequestMapper`** (`RequestMapper.java`) — static methods converting gRPC protobuf requests to `Broker*Request` types. Handles multi-tenancy validation via `ensureTenantIdSet()` with a static `isMultiTenancyEnabled` flag set by `EndpointManager`.
- **`ResponseMapper`** (`ResponseMapper.java`) — static methods converting broker record types to gRPC protobuf responses. Contains `BrokerResponseMapper<B, G>` functional interface used throughout. `toActivateJobsResponse` enforces max response size and splits oversized jobs into `sizeExceedingJobs`.
- **`GrpcErrorMapper`** (`grpc/GrpcErrorMapper.java`) — maps exceptions to `com.google.rpc.Status` using pattern matching. Maps `BrokerErrorException`, `BrokerRejectionException`, `TimeoutException`, `InvalidTenantRequestException`, etc. to appropriate gRPC codes.
- **`ErrorMappingStreamObserver`** (`grpc/ErrorMappingStreamObserver.java`) — decorator around `ServerCallStreamObserver` that intercepts `onError` to map exceptions via `GrpcErrorMapper`, and suppresses cancel exceptions.
- **`ServerStreamObserver`** (`grpc/ServerStreamObserver.java`) — interface combining `StreamObserver` with `ResponseObserver` and cancel detection.
- **`AuthenticationHandler`** (`interceptors/impl/AuthenticationHandler.java`) — sealed interface with `Oidc` and `BasicAuth` implementations. Returns `Either<Status, Context>` — never throws.
- **`AuthenticationInterceptor`** (`interceptors/impl/AuthenticationInterceptor.java`) — gRPC `ServerInterceptor` that extracts the `Authorization` header, delegates to `AuthenticationHandler`, and either denies the call or injects auth context.
- **`InterceptorRepository`** (`interceptors/impl/InterceptorRepository.java`) — loads user-defined interceptors from classpath or external JARs via `ExternalJarRepository`.
- **`StreamJobsHandler`** (`impl/stream/StreamJobsHandler.java`) — an `Actor` managing server-streaming job activation. Registers `ClientStreamConsumer` with the `ClientStreamer`, pushes `ActivatedJob` protobuf to the response observer.
- **`SpringGatewayBridge`** (`impl/SpringGatewayBridge.java`) — Spring `@Component` that bridges non-Spring gateway internals (status, cluster state, job stream client) to Spring-managed beans via supplier registration.

## Interceptor Chain

Interceptors are applied in this order (outermost first):
1. `MetricCollectingServerInterceptor` — Micrometer gRPC metrics
2. `AuthenticationInterceptor` — OIDC/Basic auth (only when `securityConfiguration.isApiProtected()`)
3. `ContextInjectingInterceptor` — injects `QueryApi` into gRPC `Context`
4. User-configured interceptors (loaded by `InterceptorRepository`, wrapped in `DecoratedInterceptor` for classloader isolation)

User interceptors are reversed so the first configured is outermost in the chain. Always add new built-in interceptors after the user interceptor block in `Gateway.applyInterceptors()`.

## Health Management

`GatewayHealthManagerImpl` tracks status via `AtomicReference<Status>` with states: `INITIAL → STARTING → RUNNING → SHUTDOWN`. Once `SHUTDOWN`, status never changes. Maps to gRPC health check `ServingStatus` for the `Gateway` service name. Spring Boot health indicators (`ClusterAwarenessHealthIndicator`, `PartitionLeaderAwarenessHealthIndicator`, `ClusterHealthIndicator`, `StartedHealthIndicator`) use `SpringGatewayBridge` to access gateway state.

## Adding a New gRPC Endpoint

1. Define the RPC in `zeebe/gateway-protocol/src/main/proto/gateway.proto`
2. Add a static mapping method in `RequestMapper` converting the gRPC request to a `Broker*Request`
3. Add a static mapping method in `ResponseMapper` converting the broker response record to the gRPC response
4. Add a method in `EndpointManager` using `sendRequest()` or `sendRequestWithRetryPartitions()`
5. Override the generated method in `GatewayGrpcService`, wrap the observer with `ErrorMappingStreamObserver.ofStreamObserver()`, and delegate to `EndpointManager`
6. Add test class extending `GatewayTest` with a corresponding stub class

## Testing Patterns

- **API tests** use JUnit 4 with `GatewayTest` base class, `StubbedGatewayRule`, `StubbedBrokerClient`, and `StubbedJobStreamer` for in-process gRPC testing. Each command has a `*Test` + `*Stub` pair (e.g., `CreateProcessInstanceTest` + `CreateProcessInstanceStub`).
- **Error mapper tests** (`GrpcErrorMapperTest`) use JUnit 5, verify gRPC status codes and log levels for each exception type.
- **Interceptor tests** (`AuthenticationInterceptorTest`, `InterceptorRepositoryTest`, `DecoratedInterceptorTest`) use JUnit 5 with Mockito.
- **Health indicator tests** use JUnit 5 and verify Spring Boot `HealthIndicator` integration.
- Run scoped: `./mvnw -pl zeebe/gateway-grpc -am test -DskipITs -DskipChecks -Dtest=<ClassName> -T1C`

## Common Pitfalls

- Never add business logic to `GatewayGrpcService` — it is purely a delegation layer.
- `RequestMapper.isMultiTenancyEnabled` is a static mutable field set once by `EndpointManager` constructor. Always call `RequestMapper.setMultiTenancyEnabled()` in tests that exercise tenant validation.
- `ErrorMappingStreamObserver` passes through `StatusException`/`StatusRuntimeException` unchanged. If you throw a pre-mapped status exception from `RequestMapper`, it will not be double-mapped.
- Auth claims are extracted from gRPC `Context.current()` in `EndpointManager.getClaims()`. Context keys are defined in `AuthenticationHandler` — adding new claim propagation requires updating both the handler and `getClaims()`.
- The `toActivateJobsResponse` in `ResponseMapper` enforces max message size by splitting jobs. When modifying job serialization, account for the size-checking loop.
- `InterceptorRepository` mirrors `FilterRepository` in `zeebe-gateway-rest`. Port changes to both.

## Key Files

- `main/.../Gateway.java` — server lifecycle, interceptor wiring, executor config
- `main/.../EndpointManager.java` — central dispatch, auth claims, retry strategies
- `main/.../RequestMapper.java` — gRPC → broker request conversion, tenant validation
- `main/.../ResponseMapper.java` — broker → gRPC response conversion, size-aware job batching
- `main/.../grpc/GrpcErrorMapper.java` — exception → gRPC Status mapping with logging
- `main/.../interceptors/impl/AuthenticationHandler.java` — sealed OIDC/Basic auth
- `main/.../impl/stream/StreamJobsHandler.java` — actor-based job streaming
- `test/.../api/util/GatewayTest.java` — base class for API tests with `StubbedGateway`