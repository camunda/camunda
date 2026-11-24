# ADR-0012: Distributed Tracing with OpenTelemetry

## Status

Proposed

## Deciders

- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))

## Context

A client request to the orchestration cluster crosses the gateway (gRPC or REST), the broker client,
the SBE transport, and the broker's stream processor. Today the orchestration cluster has no
OpenTelemetry tracing wiring: caller traces stop at the client, making it hard to separate gateway
overhead, partition routing, backpressure retries, and broker round-trip time.

A key architectural constraint is that work moves between Zeebe's actor scheduler threads without
thread-local context propagation.

## Decision

**Use the OpenTelemetry API directly, wired through Spring Boot's `OpenTelemetry` bean.** Metrics
stay on Micrometer. HTTP and gRPC server spans come from Micrometer Observation (`Spring MVC` and
`ObservationGrpcServerInterceptor`), bridged to OpenTelemetry by `spring-boot-starter-opentelemetry`
so REST and gRPC share one `OTEL_*` configuration path for propagators, sampling, and OTLP export.
A `MeterFilter` denies the five `grpc.server*` observation meters so existing gateway metrics do not
change. Internal components such as the broker client take `OpenTelemetry` via dependency injection
(defaulting to `OpenTelemetry.noop()` outside Spring).

**Continue callers' traces by default; sample nothing on our own.** Default configuration sets
`management.tracing.sampling.probability=0.0` with Spring Boot's parent-based sampler, disables OTLP
log export, and disables noisy Spring Security filter-chain observations. Nothing is exported unless
an OTLP endpoint is configured. Support engineers can turn on head sampling (for example
`OTEL_TRACES_SAMPLER_ARG=0.01`) without client changes.

**Keep the actor scheduler unchanged and hand off context explicitly.** Context is captured
explicitly only at the gateway/client actor boundaries (`BrokerRequestManager`,
`RequestRetryHandler`) when the current span is sampled. Untraced requests create no client span and
retain no context in actor state. Each broker client attempt produces a `CLIENT` span
(`<VALUE_TYPE> <INTENT>`) carrying `zeebe.partition.id`, `zeebe.broker.id`, `zeebe.value_type`,
`zeebe.intent`, `zeebe.error.code`, and `zeebe.rejection.type`.

## Alternatives considered

- **Micrometer Observation API everywhere / custom gRPC interceptor:** Kept Micrometer Observation
  only for HTTP and gRPC server spans so both share Spring Boot's `OTEL_*` pipeline. Can swap the
  gRPC interceptor later if per-call observation overhead on unsampled gRPC traffic matters.
- **`GlobalOpenTelemetry` singleton:** Conflicts with multi-broker in-JVM tests and Spring Boot's
  non-global bean lifecycle.
- **Automatic context propagation in `ActorScheduler`:** Adds overhead on every actor task
  submission even when tracing is unused. Explicit capture at sampled boundaries keeps untraced
  actor paths free of context overhead.

## Consequences

- Operators enable tracing via `OTEL_EXPORTER_OTLP_ENDPOINT` (and optionally
  `OTEL_TRACES_SAMPLER_ARG`); see `docs/observability/tracing.md`.
- Untraced traffic pays no broker client spans (`ObservationGrpcServerInterceptor` still creates a
  non-recording observation per gRPC call).
- Transport propagation into the broker and stream processor spans build cleanly on this foundation
  in a follow-up change.

