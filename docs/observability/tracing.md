# Tracing

## Overview

The orchestration cluster uses [OpenTelemetry](https://opentelemetry.io/) for distributed tracing
(see [ADR-0012](../adr/orchestration-cluster/0012-distributed-tracing-with-opentelemetry.md)).
Metrics stay on Micrometer (see [metrics](./metrics.md)).

## Enabling tracing

By default the cluster exports nothing and never starts traces on its own
(`management.tracing.sampling.probability=0.0` with Spring Boot's parent-based sampler). To collect
traces:

1. Configure an OTLP endpoint via `OTEL_EXPORTER_OTLP_ENDPOINT` (or
   `management.opentelemetry.tracing.export.otlp.endpoint`).
2. Either propagate a sampled W3C `traceparent` from the client (for example via the OpenTelemetry
   Java agent, gRPC `withInterceptors(...)`, or REST `withChainHandlers(...)`), or enable head
   sampling on the cluster with `OTEL_TRACES_SAMPLER_ARG=0.01` (or
   `management.tracing.sampling.probability=0.01`).

Standard `OTEL_*` variables supported by Spring Boot (such as `OTEL_SDK_DISABLED`,
`OTEL_PROPAGATORS`, `OTEL_TRACES_SAMPLER`, and `OTEL_TRACES_SAMPLER_ARG`) map onto `management.*`
properties. OTLP log export is disabled by default (`management.logging.export.otlp.enabled=false`).

## Spans

|                         Span                         |  Kind  |                                                                             Created by                                                                             |
|------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| HTTP request, e.g. `http post /v2/process-instances` | SERVER | Spring MVC observation (REST gateway)                                                                                                                              |
| `{rpc.service}/{rpc.method}`                         | SERVER | Micrometer `ObservationGrpcServerInterceptor` (gRPC gateway)                                                                                                       |
| `zeebe.broker_client <VALUE_TYPE> <INTENT>`          | CLIENT | Broker client; one per partition attempt (`zeebe.partition.id`, `zeebe.broker.id`, `zeebe.value_type`, `zeebe.intent`, `zeebe.error.code`, `zeebe.rejection.type`) |

Reading a trace:

- **Gateway overhead**: time inside the gateway `SERVER` span outside the broker client `CLIENT`
  span (authentication, request/response mapping).
- **Backpressure & command API rejections**: `RequestRetryHandler` keeps partition retries under the
  same gateway span. Each rejected attempt shows `zeebe.partition.id`, `zeebe.broker.id`,
  `zeebe.error.code`, and the exact rejection reason (write limit vs. request limit vs. disk space)
  in the `CLIENT` span status description.
- **Rejections**: business rejections are recorded as `zeebe.rejection.type` without setting error
  status.

## Known gaps

- Trace context is not yet propagated across the SBE transport into the broker and stream processor.
- `ActivateJobs` long polling (which runs in gateway actors) is not connected yet.

## Adding instrumentation

Inject `OpenTelemetry` (via `ObjectProvider<OpenTelemetry>` in Spring beans with
`getIfAvailable(OpenTelemetry::noop)`, defaulting to `OpenTelemetry.noop()` outside Spring) and
obtain a `Tracer` from it. Because the actor scheduler does not propagate thread-local context,
capture `Context.current()` explicitly on the calling thread only when
`Span.current().getSpanContext().isSampled()` is true, and never retain contexts in long-lived
state.
