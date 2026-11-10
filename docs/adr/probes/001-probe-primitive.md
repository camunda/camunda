# Probes: a request from a client to a worker, answered in the same HTTP request, without a process

**DRI**: Mathias Vandaele

**Status**: Accepted (specification validated on 2026-11-10), implementation in progress

**Purpose**: Record why Camunda gets a dedicated probe primitive, what it decides at the level of
the architecture, and where it is known to fall short. Every detail (records, endpoints, states,
error codes, permissions) lives in the [probes specification](probes-specification.md), which is the
source of truth; this ADR cites its decision numbers (`Dn`) rather than repeating them.

**Audience**: Engineers implementing or reviewing probes in the engine, the gateway and REST layer,
the Java client, the exporters, the audit log or Identity; client teams (Hub, Web Modeler, Desktop
Modeler) and the connector runtime; AI agents asked why a probe is not a job.

## Context

A user who configures a connector, for example an AWS credential in Hub or a task in a Modeler,
wants to know whether the configuration works before a process runs: is this credential valid, can
this runtime reach this database. Later uses need the same round trip: dropdown values and schemas
for a Modeler field, and testing one task without deploying (specification section 11).

Phase 1 answers validation with a direct call from the client to the connector runtime. That only
works where the client can reach the runtime. It cannot reach a hybrid runtime or any runtime that
only connects out to the cluster, which is the security design workers rely on: they connect out,
and the cluster never connects to a worker. Desktop Modeler has no path to a runtime at all. And in
a direct call the runtime resolves secrets itself, outside the central secret resolution that the
cluster now owns ([secrets ADR 001](../secrets/001-central-secret-resolution-architecture.md)).

The cluster already has a channel that every worker reaches by connecting out: job activation. What
is missing is a way for a client to put a short request on that channel and wait for its answer in
the same HTTP request, without creating a process instance.

## Options considered

- **A. A generic standalone job** (`POST /v2/jobs/standalone`). Rejected: the result is a job
  without a process, which breaks the job API (see B). The exporters did not see it, so a request
  could run code with secrets and leave almost no trace. And the job goes to the worker's job
  handler, so it can start a connector operation, for example a write to S3. The standalone job POC
  is not used (D8).
- **B. A closed validation API on jobs without a process**
  (`POST /v2/connectors/configurations/validation`, job kind `CONFIGURATION_VALIDATION` on a
  reserved job type). Rejected: every job must name its process, so such a job carries a fake
  `processInstanceKey` of -1 and every consumer must know that -1 means "no process", while the job
  API must not change (D1). Every job consumer must change: exporters, job search and metrics all
  need special code. And the mechanism does not match the contract: it still needs a value type,
  intents, an authorization resource and expiry of its own.
- **C. A direct call from the client to the runtime (phase 1).** Kept for phase 1, not extended:
  it cannot reach hybrid or outbound-only runtimes, cannot serve Desktop Modeler, and resolves
  secrets in the runtime rather than in the cluster.
- **D. A system process instance with a connector task.** Rejected: each validation shows in
  Operate and counts as a process instance in the usage metrics. A failed validation creates an
  incident unless the process models the error. And draft configurations do not fit the rule that
  secret references are static at deployment.
- **E. A dedicated probe primitive. Chosen.** Its own value types, state and endpoints in the
  engine, borrowing the job activation model for transport, secrets and authorization, so that
  workers reach it the way they already reach jobs and the job API stays unchanged (D1).

## Decision

A probe is its own engine primitive: not a job and not a process instance (D1). Where nothing below
says otherwise, a probe behaves as a job does; every divergence from jobs is named in the
specification.

### Routing on an opaque `probeId`

The client sends a probe to a `probeId`, an opaque string that a worker polls for, as a job worker
polls for a job type (D46). The engine matches it by exact string equality and never interprets it.
The convention for Camunda workers is `<operation>/<template>`, for example
`validate-configuration/io.camunda:aws-credential:1`, documented but not enforced. There is no
`operation` field and no closed enum: what the worker should do is part of the input, whose shape
the worker of the `probeId` defines (D11, D37). The `probeId` is the `PROBE` resource ID and the
audit description.

### Transport: REST long polling that mirrors job activation

REST only: `POST /v2/probes` for the client, `POST /v2/probes/activation` and
`POST /v2/probes/{probeKey}/answer` for the worker (D2, D31, D38, D45). No gRPC, no SSE, no stream.
Activation mirrors job activation, with its own long polling handler and its own notification topic
so that a probe never wakes a job poller (specification section 4.2).

One divergence from jobs: one activation request carries a list of up to 100 `probeIds`, and one
probe worker polls all of them (D50, D54). Probes are rare, so almost every poll returns empty; one
poll per ID would make that empty traffic grow with the number of IDs a runtime answers.

### Lifecycle and an absolute deadline

`ACTIVATABLE` → (`WAITING_FOR_SECRET_RESOLUTION`) → `ACTIVATED` → `ANSWERED`, `TIMED_OUT` or
`CANCELED` (D23, D39, specification section 6). The engine keeps the client request with the probe
and answers it when the probe ends. A probe lives on one partition: `CREATE` goes round-robin, a
poll goes to every partition, everything else goes to the owner by key, with no command
distribution (D22).

The gateway computes the deadline when it receives the request (`now + requestTimeout`, capped at
30 s) and sends it as an absolute time; the engine never recomputes it (D18, D49). A probe that no
poll collected by then ends with `404 NO_WORKER`, one that was collected but not answered with
`504 WORKER_DID_NOT_ANSWER` (D40). A delivery the gateway could not complete is yielded back to
`ACTIVATABLE` (D41).

### Secrets: detected at `CREATE`, injected at collection

At `CREATE` the engine detects references in the input as it does for input mappings, and rejects
imprecise ones (D28, D60). The records carry placeholders, never values. When a poll collects the
probe, the engine reads the cache only, as `JobBatchCollector` does: all cached, the values go into
the activation response only; one missing, the probe is parked and resolved through the existing
resolution chain (D12, D39). A resolution failure ends the probe with
`400 SECRET_RESOLUTION_ERROR` instead of raising an incident, since there is no process (D13).

### Records, export and audit

Two value types, `PROBE` and `PROBE_BATCH`, as `JOB` and `JOB_BATCH` (D32, specification
section 7). `ANSWERED` never carries `data`: the client's response is written from the `ANSWER`
command, which exporters skip, so a metadata answer never reaches exported records (D42). The
Elasticsearch and OpenSearch templates are off by default (D56); the Camunda and RDBMS exporters
write audit log entries only (D16), with the mapping in D70.

### Authorization

A new resource type `PROBE`, with `CREATE` for the client and `ANSWER` for the worker, on the
`probeId` or `*` (D14). The client also needs `SECRET:READ` on each referenced secret, direct or
reached through a cluster variable, because the worker receives values without any secret
permission (D67); no check per cluster variable (D68). `CREATE` checks permissions before it parses
or evaluates (D66). A poll silently skips the `probeIds` the poller may not answer (D52). The
Connectors role gets `PROBE:ANSWER` on `*` by default (D69).

## Consequences and known limits

- **A probe may run more than once.** A `CREATE` retried on another partition after a lost
  response, or a yield, can hand the same request to two workers, and there is no idempotency key.
  Probes are therefore read-only in phase 2; task testing must revisit this (D57, OQ33).
- **First poll wins between a SaaS and a hybrid runtime.** When both register the same `probeId`,
  the answer depends on which one polled first. A hybrid runtime that must answer alone registers
  its own `probeId`; a worker selector comes later (D47).
- **Clock skew above about 400 ms between gateway and broker** brings back a
  `504 DEADLINE_EXCEEDED` from the gateway before the engine's own answer, and under heavy
  backpressure the engine can still miss the 500 ms margin (D49).
- **No feature flag.** Probes are available as soon as a release ships them, behind alpha
  endpoints. During a rolling update, a `CREATE` or a poll that reaches an old broker is rejected;
  workers back off (D43, D51).
- **No worker means a full wait.** `NO_WORKER` is decided at the deadline, so a client with no
  worker waits its whole `requestTimeout` (D40).
- **`PROBE:ANSWER` grants secrets.** Since `probeId` is a free string, a worker with `ANSWER` on `*`
  receives every probe's secrets. Grant it only to the runtimes that serve those IDs (specification
  section 8).
- **Secret values can be up to the cache TTL stale**, as for jobs (D12).
- **A raw secret typed into a literal field** ends up in `CREATED`; the engine cannot detect it.
  Clients mitigate with the secret picker (specification section 7).
- **The `PROBE` resource type comes from `camunda-security-library`**, which must be released before
  any step that checks a permission (specification section 12).

## Known POC divergences

The POC ([camunda/camunda#68078](https://github.com/camunda/camunda/pull/68078)) is a reference, not
a base. Where it differs from the specification, the specification wins. Code copied from the POC
must fix:

- The `NO_WORKER` detail names only "no worker polls this probe ID"; it must also name a missing
  `PROBE:ANSWER` (D52).
- The audit log gives `TIMED_OUT` and `CANCELED` the default result `SUCCESS`; it must be `FAIL`
  (D70).

## Open question

- **OQ30: which roles get `PROBE:CREATE` by default?** Owners: Security and Product management.
  Proposal: users who manage connections get `PROBE:CREATE` on `*` and `SECRET:READ`, and Admin
  gets all (specification section 8).

## Source

- [Probes specification](probes-specification.md), the source of truth: decisions D1 to D70, open
  and discussed questions.
- [Epic: Connection Validation phase 2 (camunda/product-hub#3433)](https://github.com/camunda/product-hub/issues/3433)
- [Write the ADR for probes (camunda/camunda#68861)](https://github.com/camunda/camunda/issues/68861)
- [Probe specification tab of the Phase 2 doc (Google Doc)](https://docs.google.com/document/d/1omtaMu3HWFpPakfCU4aDQtZSTGpjzbqQ-y7M9o71PDc/edit?tab=t.5y3t70xg24uo),
  with the acceptance tests AT1 to AT24 and the success metrics.
- [POC: probes (camunda/camunda#68078)](https://github.com/camunda/camunda/pull/68078), a reference
  only.
- [Central secret resolution (secrets ADR 001)](../secrets/001-central-secret-resolution-architecture.md)

