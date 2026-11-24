# ADR-0012: Distributed Tracing with OpenTelemetry

## Status

Proposed

## Deciders

- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))

## Context

A client request to the orchestration cluster crosses the gateway (gRPC or REST), the broker client,
the SBE transport, the broker's command API, and the partition's stream processor. Today a caller's
trace ends at the gateway, making it hard for customers to see why a request failed or timed out,
and for support engineers to separate gateway overhead, backpressure, processing backlog, RocksDB
state commits, and Raft replication latency.

Two architectural constraints shape the design: the internal transport and log use Zeebe's SBE
protocol, and work moves between actor scheduler threads without thread-local propagation.

## Decision

**Use the OpenTelemetry API directly, wired through Spring Boot's `OpenTelemetry` bean.** Metrics
stay on Micrometer. HTTP and gRPC server spans come from Micrometer Observation (`Spring MVC` and
`ObservationGrpcServerInterceptor`), bridged to OpenTelemetry by `spring-boot-starter-opentelemetry`
so REST and gRPC share one `OTEL_*` configuration path for propagators, sampling, and OTLP export.
A `MeterFilter` denies the five `grpc.server*` observation meters so existing gateway metrics do not
change. The broker client and stream processor take `OpenTelemetry` via dependency injection
(defaulting to `OpenTelemetry.noop()` outside Spring).

**Continue callers' traces by default; sample nothing on our own.** Default configuration sets
`management.tracing.sampling.probability=0.0` with Spring Boot's parent-based sampler, disables OTLP
log export, and disables noisy Spring Security filter-chain observations. Nothing is exported unless
an OTLP endpoint is configured. Support engineers can turn on head sampling (for example
`OTEL_TRACES_SAMPLER_ARG=0.01`) without client changes.

**Propagate W3C `traceparent` through `ExecuteCommandRequest` and `RecordMetadata` (SBE v12).**
Both messages gain a trailing `traceContext` var-data field (`sinceVersion="12"`), populated only for
sampled user commands and kept internal to `RecordMetadata` (not exposed on `Record` or exported to
secondary storage). Untraced records pay 4 bytes for the empty length prefix; `tracestate` is
omitted on the internal hop. Bidirectional rolling upgrades work because older decoders ignore the
trailing field and newer decoders see an empty value for v11 records.

**Keep the actor scheduler unchanged and hand off context explicitly.** Context is captured
explicitly only at the two gateway/client actor boundaries (`BrokerRequestManager`,
`RequestRetryHandler`). On the broker, carrying `traceContext` in `RecordMetadata` avoids any
in-memory cross-actor hand-off registry, bounded queue cap, or per-term lifecycle coupling:
`CommandApiRequestReader` copies `traceContext` onto `RecordMetadata`, and `ProcessingStateMachine`
reads it directly. `ReplayStateMachine` never traces, while unprocessed committed commands
seamlessly continue their trace on the new leader after failover.

**Keep broker span cardinality fixed (O(1) per request).** The broker `SERVER` span starts when the
stream processor begins the command (recording `zeebe.queue.wait_ms` from the log entry timestamp,
`zeebe.log.position`, `zeebe.partition.id`, `zeebe.value_type`, and `zeebe.intent`) and ends right
after responses are handed to the transport (or aborts on step-down). Its coarse stage children are
`zeebe.process` (per batch, not per follow-up command), `zeebe.write`, `zeebe.update_state`, and
`zeebe.await_commit`. Follow-up records do not copy `traceContext` in this first cut, keeping log
overhead and span volume bounded; forwarding can be enabled later for cross-batch or inter-partition
tracing.

## Alternatives considered

- **In-memory `CommandTraceRegistry` between command API and stream processor:** Avoids 4 bytes per
  record in `RecordMetadata`, but requires cross-actor locking, per-leader-term lifecycle wiring, a
  bounded queue cap that drops traces under heavy backlog, and cannot survive leader failover or
  extend to follow-up batches.
- **Piggybacking stage timings on `ExecuteCommandResponse` (`Server-Timing` style):** Avoids OTel in
  the broker and could expose customer-facing HTTP headers, but goes blind on timeouts, Raft quorum
  stalls, and step-downs where no response returns, and cannot grow into RocksDB spans or broker log
  correlation. Can still be added later as a complementary feature.
- **Micrometer Observation API everywhere / custom gRPC interceptor:** Kept Micrometer Observation
  only for HTTP and gRPC server spans so both share Spring Boot's `OTEL_*` pipeline. Can swap the
  gRPC interceptor later if per-call observation overhead on unsampled gRPC traffic matters.
- **`GlobalOpenTelemetry` singleton:** Conflicts with multi-broker in-JVM tests and Spring Boot's
  non-global bean lifecycle.
- **Per-command child spans inside `process` / RocksDB spans now:** Multiplies span volume up to
  `maxCommandsInBatch` (default 100) per request. Deferred to follow-up work.

## Consequences

- Operators enable tracing via `OTEL_EXPORTER_OTLP_ENDPOINT` (and optionally
  `OTEL_TRACES_SAMPLER_ARG`); see `docs/observability/tracing.md`.
- Untraced traffic pays no broker spans, 4 bytes on `ExecuteCommandRequest` and `RecordMetadata` in
  the Raft log (0 bytes in secondary storage/exporters), and zero locks in the stream processor
  (`ObservationGrpcServerInterceptor` still creates a non-recording observation per gRPC call).
- The transport and log record metadata protocol moves to schema version 12.
- Follow-up commands in separate batches, timers, inter-partition commands, and `ActivateJobs` long
  polling are not traced yet and can be built cleanly on this foundation.

