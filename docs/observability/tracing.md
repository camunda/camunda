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

## Collecting traces without an OTel collector

When no external OpenTelemetry collector is available, any broker or gateway can coordinate an
on-demand, time-boxed trace capture across the entire cluster via the `traces` management endpoint
on the internal management port (`9600`, reachable in Kubernetes via `kubectl port-forward
pod/<pod> 9600:9600`). When idle, the capture processor performs a single volatile read and
allocates nothing.

Defaults: `duration="60s"` (allowed `1s..15m`), `samplingProbability=0.0` (allowed `0.0..1.0`),
`maxTracesPerSecond=20` **per node** (allowed `1..500`), and `maxSpansPerNode=10000` (allowed
`100..50000`). Capture stops automatically when `duration` elapses, and buffered spans remain
downloadable for **15 minutes** after expiry before being freed automatically from heap.

1. Start a capture window across all cluster members (brokers and gateways). Pass
   `-H 'Content-Type: application/json'` on every `POST`; starting while another capture is active
   returns `409 Conflict`:
   - **Header-only mode** (default `samplingProbability=0.0` — captures only requests sent with a
     sampled W3C `traceparent` header; use a longer duration such as `5m` when manually reproducing
     a slow or timing-out request so in-flight spans finish inside the window):

     ```bash
     curl -X POST http://localhost:9600/actuator/traces \
       -H 'Content-Type: application/json' \
       -d '{"duration":"5m"}'

     # Send a sampled request (the trailing -01 flag marks the trace as sampled):
     TRACE_ID=$(openssl rand -hex 16)
     curl -X POST http://localhost:8080/v2/process-instances \
       -H 'Content-Type: application/json' \
       -H "traceparent: 00-${TRACE_ID}-0000000000000001-01" \
       -d '{"processDefinitionId":"my-process"}'
     ```
   - **Active sampling mode** (samples untraced requests at each gateway without client changes; if
     an external OTLP exporter is also configured, it receives these sampled spans as well):

     ```bash
     curl -X POST http://localhost:9600/actuator/traces \
       -H 'Content-Type: application/json' \
       -d '{"duration":"60s","samplingProbability":0.1,"maxTracesPerSecond":20,"maxSpansPerNode":10000}'
     ```
2. Inspect capture progress across all members (wait for `active: false` or `totalCapturedSpans > 0`
   and empty `errors` before downloading so in-flight traces are complete):

   ```bash
   curl http://localhost:9600/actuator/traces
   ```
3. Download the cluster-wide support ZIP from any node (`traces.otlp.json` + `metadata.json` with
   Camunda version, capture settings, per-node counts, truncation flags, and any unreachable-node
   errors):

   ```bash
   curl -fSL -o camunda-traces.zip http://localhost:9600/actuator/traces/export
   # Optionally filter to one trace across the entire cluster:
   curl -fSL -o camunda-traces.zip "http://localhost:9600/actuator/traces/export?traceId=${TRACE_ID}"
   ```
4. Stop capture and free buffers immediately across all nodes:

   ```bash
   curl -X DELETE http://localhost:9600/actuator/traces
   ```

### Viewing a support ZIP locally

Extract the archive and POST `traces.otlp.json` to a local OTLP/HTTP receiver such as Jaeger v2
(do **not** use Jaeger UI's "Upload JSON" tab, which expects Jaeger JSON rather than OTLP/JSON):

```bash
unzip camunda-traces.zip
docker run --rm -p 16686:16686 -p 4318:4318 jaegertracing/jaeger:2
curl -X POST http://localhost:4318/v1/traces \
  -H 'Content-Type: application/json' \
  --data-binary @traces.otlp.json
```

Open `http://localhost:16686` and select service `camunda`; individual brokers and gateways are
distinguished by the `service.instance.id` resource attribute (set to the node's Atomix member ID
when not configured).

Exported spans omit free-form `status.message` strings and strip sensitive attributes
case-insensitively (`url.query`, `url.full`, `url.path`, `url.fragment`, `http.url`, `http.target`,
`http.request.header.*`, `http.response.header.*`, `rpc.grpc.request.metadata.*`,
`rpc.grpc.response.metadata.*`, `db.statement`, `db.query.text`, `db.query.parameter.*`,
`exception.message`, `exception.stacktrace`, `user.*`, `enduser.id`, `enduser.role`,
`enduser.scope`).

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
