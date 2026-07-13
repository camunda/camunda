```yaml
---
applyTo: "zeebe/gateway-protocol-impl/**"
---
```
# Zeebe Gateway Protocol Implementation

## Module Purpose

This module generates Java gRPC stubs and protobuf message classes from the `gateway.proto` definition in `zeebe/gateway-protocol/src/main/proto/gateway.proto`. It also ships the `gateway-service-config.json` — the gRPC client-side retry policy configuration consumed by Java client SDKs (`clients/java/`, `clients/java-deprecated/`). The module contains **no hand-written Java source code**; all Java classes are generated at build time by the `protobuf-maven-plugin`.

## Architecture

### Code Generation Pipeline

1. **Source proto**: `zeebe/gateway-protocol/src/main/proto/gateway.proto` (proto package `gateway_protocol`, Java package `io.camunda.zeebe.gateway.protocol`)
2. **Build plugin**: `protobuf-maven-plugin` (configured in `pom.xml`, `protoSourceRoot` points to the sibling module's proto dir)
3. **Generated output** (in `target/generated-sources/protobuf/`):
   - `java/.../GatewayOuterClass.java` — all protobuf message types (~2.9 MB, request/response classes for 22 RPCs)
   - `grpc-java/.../GatewayGrpc.java` — gRPC service descriptor, stubs, and method descriptors

### Generated Key Classes

- **`GatewayOuterClass`** — contains all protobuf message inner classes (e.g., `ActivateJobsRequest`, `CreateProcessInstanceResponse`, `ActivatedJob`)
- **`GatewayGrpc`** — contains the service descriptor and four stub types:
  - `GatewayImplBase` / `AsyncService` — server-side base class (extended by `zeebe/gateway-grpc/.../EndpointManager.java`)
  - `GatewayStub` — async client stub (used by `clients/java/.../CamundaClientImpl.java`)
  - `GatewayBlockingStub` / `GatewayBlockingV2Stub` — synchronous client stubs
  - `GatewayFutureStub` — future-based client stub

### Service Config (Retry Policy)

`src/main/resources/gateway-service-config.json` defines gRPC client retry policies loaded by the Java client SDKs at runtime. It groups the 22 RPC methods into two retry policy tiers:

- **Tier 1** (retries on `UNAVAILABLE`, `RESOURCE_EXHAUSTED`, `DEADLINE_EXCEEDED`): idempotent/read RPCs — `ActivateJobs`, `CompleteJob`, `FailJob`, `Topology`, `StreamActivatedJobs`, etc.
- **Tier 2** (retries on `UNAVAILABLE`, `RESOURCE_EXHAUSTED` only — excludes `DEADLINE_EXCEEDED`): non-idempotent/write RPCs — `CreateProcessInstance`, `DeployResource`, `PublishMessage`, `SetVariables`, etc.

Both tiers use: `maxAttempts: 5`, `initialBackoff: 0.1s`, `maxBackoff: 5s`, `backoffMultiplier: 3.0`, `waitForReady: true`.

## Relationships

- **Depends on**: `zeebe-gateway-protocol` (provided scope — ensures proto files are available and build-ordered)
- **Consumed by**:
  - `clients/java/` and `clients/java-deprecated/` — use `GatewayGrpc.newStub()` and load `gateway-service-config.json`
  - `zeebe/gateway-grpc/` — extends `GatewayImplBase`, uses `GatewayOuterClass` message types
  - `zeebe/gateway/` — gateway assembly module
  - `zeebe/qa/integration-tests/` — integration test infrastructure

## Key Invariants

- **Every RPC method in `gateway.proto` must have a retry policy** in `gateway-service-config.json`. The `ServiceConfigTest` enforces this by iterating `GatewayGrpc.getServiceDescriptor().getMethods()` and asserting each has a matching `retryPolicy` entry.
- **Java 8 source level** — this module targets Java 8 (`version.java=8`) because it ships as a transitive dependency of the public Java client SDK.
- **Apache 2.0 license** — unlike most of the monorepo (Camunda License 1.0), this module uses Apache 2.0 license headers.
- **No hand-written source code** — do not add Java source files under `src/main/java/`. All Java code is generated.

## How to Add a New gRPC RPC Method

1. Add the `rpc` definition and request/response `message` types in `zeebe/gateway-protocol/src/main/proto/gateway.proto`.
2. Add a retry policy entry in `src/main/resources/gateway-service-config.json` — choose Tier 1 (includes `DEADLINE_EXCEEDED`) for idempotent/read operations, Tier 2 for non-idempotent/write operations.
3. Rebuild: `./mvnw -pl zeebe/gateway-protocol-impl -am install -Dquickly -T1C`.
4. Run `ServiceConfigTest` to verify: `./mvnw -pl zeebe/gateway-protocol-impl test -DskipChecks -T1C`.

## Common Pitfalls

- Do not edit generated files in `target/generated-sources/` — they are overwritten on every build.
- Do not forget to add the new RPC to `gateway-service-config.json` — `ServiceConfigTest` will fail.
- Do not add `DEADLINE_EXCEEDED` to retry policies for non-idempotent RPCs (write operations) — this could cause duplicate side effects.
- The SpotBugs exclude filter (`spotbugs/spotbugs-exclude.xml`) suppresses all findings on `GatewayOuterClass` and its inner classes since they are generated code.

## Key Files

| File | Purpose |
|------|---------|
| `pom.xml` | Build config: protobuf-maven-plugin, Java 8 target, Apache 2.0 license |
| `src/main/resources/gateway-service-config.json` | gRPC client retry policies for all 22 RPC methods |
| `src/test/java/.../ServiceConfigTest.java` | Enforces every RPC has a retry policy entry |
| `spotbugs/spotbugs-exclude.xml` | Excludes generated `GatewayOuterClass` from SpotBugs |
| `zeebe/gateway-protocol/src/main/proto/gateway.proto` | Source proto definition (in sibling module) |