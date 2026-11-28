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

|                         Span                         |   Kind   |                                                                                                                                 Created by                                                                                                                                  |
|------------------------------------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| HTTP request, e.g. `http post /v2/process-instances` | SERVER   | Spring MVC observation (REST gateway)                                                                                                                                                                                                                                       |
| `{rpc.service}/{rpc.method}`                         | SERVER   | Micrometer `ObservationGrpcServerInterceptor` (gRPC gateway)                                                                                                                                                                                                                |
| `zeebe.broker_client <VALUE_TYPE> <INTENT>`          | CLIENT   | Broker client; one per partition attempt (`zeebe.physical_tenant.id`, `zeebe.partition.id`, `zeebe.broker.id`, `zeebe.value_type`, `zeebe.intent`, `zeebe.error.code`, `zeebe.rejection.type`)                                                                              |
| `zeebe.stream_processor <VALUE_TYPE> <INTENT>`       | SERVER   | Broker stream processor; from reading the command until the response is handed to transport (`zeebe.physical_tenant.id`, `zeebe.partition.id`, `zeebe.value_type`, `zeebe.intent`, `zeebe.log.position`, `zeebe.record.key`, `zeebe.queue.wait_ms`, `zeebe.rejection.type`) |
| `zeebe.process`                                      | INTERNAL | Stream processor; engine batch execution (`zeebe.batch.commands`, `zeebe.batch.attempt` on retry)                                                                                                                                                                           |
| `zeebe.write`                                        | INTERNAL | Stream processor; writing follow-up records with backpressure retries (`zeebe.write.records`, `zeebe.write.attempts`, `zeebe.write.failure`)                                                                                                                                |
| `zeebe.update_state`                                 | INTERNAL | Stream processor; committing the RocksDB state transaction                                                                                                                                                                                                                  |
| `zeebe.await_commit`                                 | INTERNAL | Stream processor; waiting for Raft quorum commit before sending the response                                                                                                                                                                                                |

Reading a trace:

- **Gateway overhead**: time inside the gateway `SERVER` span outside the broker client `CLIENT`
  span (authentication, request/response mapping).
- **Backpressure & command API rejections**: `RequestRetryHandler` keeps partition retries under the
  same gateway span. Each rejected attempt shows `zeebe.physical_tenant.id`, `zeebe.partition.id`,
  `zeebe.broker.id`, `zeebe.error.code`, and the exact rejection reason (write limit vs. request
  limit vs. disk space) in the `CLIENT` span status description.
- **Network + log backlog**: gap between the start of the `CLIENT` span and the start of the broker
  `SERVER` span; `zeebe.queue.wait_ms` separates broker log queueing from network latency (computed
  from the command's append timestamp, so values across a leader failover can include clock skew).
- **Rejections**: business rejections are recorded as `zeebe.rejection.type` without setting error
  status.

## Known gaps

- `traceContext` is not yet forwarded onto follow-up records, so follow-up commands processed in
  later batches, timers, and inter-partition commands are not traced.
- For requests whose response is sent by a later batch (such as `CreateProcessInstanceWithResult`),
  the broker span ends after the creation batch commits with the event `zeebe.response_deferred`.
- On leader step-down or stream processor failure while processing/committing, in-flight broker
  spans end with error status and the event `zeebe.leader_changed` or `zeebe.processing_aborted`. If
  the command was already committed before step-down, the new leader continues the trace when it
  processes it.
- Only requests built on `BrokerExecuteCommand` carry `traceContext` across the transport.
  `ActivateJobs` long polling (which runs in gateway actors) is not connected yet.

## Adding instrumentation

Inject `OpenTelemetry` (via `ObjectProvider<OpenTelemetry>` in Spring beans with
`getIfAvailable(OpenTelemetry::noop)`, defaulting to `OpenTelemetry.noop()` outside Spring) and
obtain a `Tracer` from it. Because the actor scheduler does not propagate thread-local context,
capture `Context.current()` explicitly on the calling thread only when
`Span.current().getSpanContext().isSampled()` is true, and never retain contexts in long-lived
state.
