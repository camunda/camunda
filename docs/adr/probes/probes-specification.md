# Probes – specification

Specification for Connector Validation Phase 2, Option E, validated on 2026-11-10. This file is the
source of truth for probes: a change found during implementation updates this file first, with a
decision number. The Probe specification tab of the Phase 2 doc is its shared summary. The decision
record is [ADR 001](001-probe-primitive.md).

Status markers:

- **AGREED** – we decided it in discussion.
- **PROPOSED** – a proposal by the DRI, not discussed yet.
- **SUPERSEDED** – replaced by a later decision; kept struck through so the history stays readable.

Revised on 2026-11-03 after the Define kickoff (2 Nov 2026). The kickoff moved probes from a push
stream to long polling, the same model as job activation (D38 to D45). On the same day, the routing
key became one opaque `probeId` (D46), as the Zeebe team proposed, instead of the job type (D36). The POC then
fixed the deadline (D49) and the poll shape (D50). Revised again on 2026-11-03 after an audit for
implementation (D51 to D66). Revised on 2026-11-04 after a review of the authorization with Security
(D67 to D69). Detailed on 2026-11-10 for implementation without context: audit log (D70),
activation response, physical-tenant paths, known POC divergences. Revised on 2026-11-12 after the review of the ADR
(camunda/camunda#68884): rolling-update risk (D71), secrets in answers (D72), injected-value pointers for the SDK (D73), connector runtime (section 5.2) agreed with Connectors, acceptance tests
published in section 13. Open question left: OQ30, the default role for `PROBE:CREATE`. The questions we discussed, with the reasoning behind each decision, are in
[Questions discussed](#questions-discussed).

This spec is the source of truth for the implementation. The POC (draft PR #68078) is a reference,
not a base: where they differ, the spec wins, and a change found during implementation updates the
spec first, with a decision number.

Known POC divergences, so that code copied from the POC is fixed:

- The `NO_WORKER` detail names only "no worker polls this probe ID"; it must also name a missing
  `PROBE:ANSWER` (D52).
- The audit log gives `TIMED_OUT` and `CANCELED` the default result `SUCCESS`; it must be `FAIL`
  (D70).

## 1. Purpose

A probe is a short request from a client to a worker. The client waits for the answer in the same
HTTP request. A probe has no process instance and is not a job.

The client sends the probe to a `probeId`, an opaque string that a worker registers, as a job
worker registers a job type. By convention it is `<operation>/<template>`, for example
`validate-configuration/io.camunda:aws-credential:1`. The worker fetches probes the way it fetches
jobs: by long polling. The engine does not interpret the `probeId` or the input.

The first use is configuration validation ("Is this AWS credential valid?"). Metadata retrieval
(dropdown values, schemas) and task testing are later epics (section 11). The mechanism must support
them without engine changes.

Workers keep their security design: they connect out to the cluster. The cluster never connects to a
worker.

## 2. Terms

|         Term         |                                                                                                        Meaning                                                                                                        |
|----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Probe                | One request from a client to a worker, and its answer.                                                                                                                                                                |
| Probe ID (`probeId`) | The routing key of a probe: an opaque string that a worker polls for, as a job worker polls a job type. Convention: `<operation>/<template>`, for example `validate-configuration/io.camunda:aws-credential:1` (D46). |
| Input                | One FEEL expression that evaluates to a context. It carries what the worker needs (D11).                                                                                                                              |
| Probe worker         | A worker that polls for probes of one or more `probeId`s, in one request (D50). A connector runtime opens one probe worker for all the `probeId`s it answers.                                                         |

## 3. Decisions

| ID  |                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             Decision                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |           Status            |
|-----|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| D1  | A probe is its own engine primitive, with its own value type. It is not a job and not a process instance. The job API does not change.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D2  | One generic client endpoint: `POST /v2/probes`. ~~The operation is a typed field of the request.~~ Headers never select the behavior.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | AGREED, refined by D37      |
| D3  | ~~A probe worker subscribes without writing a type by hand. The SDK builds the subscriptions from the registered handlers.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | SUPERSEDED by D44           |
| D4  | ~~The engine sends a probe only to a subscription with a matching capability. If no subscription matches, the engine answers at once with `NO_WORKER`.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | SUPERSEDED by D40           |
| D5  | A worker that collected a probe but cannot answer this input answers `UNSUPPORTED`. The engine sends this to the client. It does not try another worker.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | AGREED                      |
| D6  | ~~A worker can declare the capability `*`.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | SUPERSEDED by D26           |
| D7  | Size is bounded by `maxMessageSize` (default 4 MB). The real limit for an answer is lower, because the `ANSWER` command and the internal `RESPOND` command both carry `data` and go into one batch with the event (D74).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | AGREED, refined by D74      |
| D8  | Built from `main`. The standalone job POC is not used.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D9  | The answer has two levels. `outcome` (`OK`, `UNSUPPORTED`, `ERROR`) tells whether the worker produced an answer. `data` holds the result, whose shape the worker of the `probeId` defines. For credential validation, `data` is the connector SDK's `ConfigurationValidationResult`. Extended by D20 (`worker`) and D30 (`message` for `ERROR`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | AGREED                      |
| D10 | ~~Probes are push only, with no polling. Phase 2 uses a gRPC stream.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | SUPERSEDED by D38           |
| D11 | The request has no `parameters` field. Everything the worker needs is in `input`, one FEEL expression that mixes literal values and references (`camunda.vars.*`, `camunda.secrets.*`). The worker of the `probeId` defines the shape of the evaluated input.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D12 | The engine injects secret values when a poll collects the probe, as for jobs. It reads the cache first; on a miss it parks the probe and resolves through the existing resolution chain. We accept that a value can be up to 20 minutes stale (`camunda.secrets.cache.ttl`). The worker needs no secret permission and resolves nothing from the cluster. See section 9.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | AGREED, refined by D39      |
| D13 | A secret resolution failure mirrors jobs: one answer for every reason, with the same message as the job incident. Where a job gets a `SECRET_RESOLUTION_ERROR` incident and stays parked, a probe ends and its client gets `400 SECRET_RESOLUTION_ERROR`. A probe still parked at its deadline gets `504 SECRET_RESOLUTION_TIMEOUT`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | AGREED                      |
| D14 | New resource type `PROBE` with `CREATE` (client) and `ANSWER` (worker). The resource ID is the `probeId` (D46). The client also needs `SECRET:READ` on each referenced secret, also through a cluster variable (D67). No check per cluster variable (D68). See section 8.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | AGREED, refined by D46      |
| D15 | ~~`operation` is a closed enum owned by Camunda.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | SUPERSEDED by D37           |
| D16 | `CREATED` contains the evaluated input with placeholders. ~~`ANSWERED` contains `data` only if the operation allows it.~~ Elasticsearch and OpenSearch store the input but do not index it. The Camunda and RDBMS exporters write only audit log entries, through `zeebe/exporter-common`. See section 7.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | AGREED, refined by D42      |
| D17 | ~~A push that fails yields the probe; one re-dispatch, then `NO_WORKER`.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | SUPERSEDED by D41           |
| D18 | The client sets `requestTimeout` in milliseconds, as in create-with-result. 0 or absent means the cluster's request timeout (default 15 s). The cap is 30 s. The engine's deadline expires before the gateway stops waiting, and the gateway before the client. See section 10.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | AGREED                      |
| D19 | `NO_WORKER` is HTTP 404, title `NO_WORKER`. ~~At `CREATE` (F1)~~ When no worker collected the probe before its deadline (D40). Not 503: in Camunda it means "cluster unavailable" or backpressure. Confirmed at the kickoff.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | AGREED, refined by D40      |
| D20 | When several workers poll the same `probeId`, the first poll that reaches the probe's partition collects it, as for jobs. The answer envelope names the worker that answered (`worker`, added to D9). See section 6.2.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED, refined by D41      |
| D21 | ~~`template` is required for every operation.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | SUPERSEDED by D36, then D46 |
| D22 | `CREATE` goes round-robin across the physical tenant's partitions, as expression evaluation, and is retried on another partition when not accepted. A poll goes to every partition, as job activation. Everything else goes to the partition that owns the probe, found from the probe key. No command distribution. See section 6.4.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | AGREED, refined by D38      |
| D23 | Lifecycle states `ACTIVATABLE` (waiting for a poll), `WAITING_FOR_SECRET_RESOLUTION` (collected by a poll, a secret not cached) and `ACTIVATED` (handed to a worker). `TIMED_OUT` carries a reason. See section 6.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | AGREED, refined by D39      |
| D24 | Any holder of `PROBE:ANSWER` on the probe's `probeId` and tenant may answer an `ACTIVATED` probe with its key, as for jobs. There is no check that the sender is the worker that collected it. The `worker` in the answer (D20) is the worker name of the poll that collected the probe.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | AGREED                      |
| D25 | ~~The catch-all capability `*`.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | SUPERSEDED by D26           |
| D26 | No catch-all, as for jobs: a poll names exact `probeId`s (D50). `*` in this spec only means the authorization wildcard (section 8).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | AGREED                      |
| D27 | ~~One stream per capability.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | SUPERSEDED by D34, then D38 |
| D28 | At `CREATE`, the engine detects references in `input` as for input mappings: `SecretReference.parse` and `ClusterVariableReference.parse` with JSON pointers, then `ClusterVariableJobSecretResolver` for secrets reached through cluster variables. It rejects imprecise references with 400, as the deploy validator does. See section 6.5.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D29 | A refused `CREATE` gets no audit log entry, as no audit configuration records a `FORBIDDEN` refusal today. The rejection record stays in the record stream. The Phase 2 doc's "100% of validations have an audit entry" covers accepted validations, created by a user with the default actor configuration (D70).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | AGREED                      |
| D30 | The connector runtime maps validation as phase 1 does (`ConfigurationValidationService`): every result, and every failure with a fixed message, is `outcome: OK` with the result in `data`. In the Java client, a handler that throws gives `outcome: ERROR` with a `message`: the exception's message with the probe's injected secret values removed, cut to 500 characters; the provider is configurable. See section 5.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED, refined by D73      |
| D31 | The worker answers over REST: `POST /v2/probes/{probeKey}/answer` with `outcome`, `data` and `message`. Responses mirror job completion: 204, 400, 403, 404 (also for a probe of another tenant), 409, 503. `ProbeAnswer` has the factories `ok(data)`, `unsupported()` and `error(message)`. See section 4.1.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | AGREED                      |
| D32 | Intent names follow the job vocabulary. `PROBE` commands: `CREATE`, `ANSWER`, `YIELD`, `TIME_OUT`, and the internal `RESPOND` (D74); events: `CREATED`, `YIELDED`, `ANSWERED`, `TIMED_OUT`, `CANCELED`. Polling uses a `PROBE_BATCH` value type with `ACTIVATE` and `ACTIVATED`, as `JOB_BATCH`. See section 7.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | AGREED, refined by D39, D74 |
| D33 | ~~Worker API with `capability(operation, template, handler)`.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | SUPERSEDED by D44           |
| D34 | ~~One stream per worker, declaring every capability as a list.~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | SUPERSEDED by D38           |
| D35 | ~~Push over a gRPC stream and a REST stream (SSE).~~                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | SUPERSEDED by D38           |
| D36 | ~~A probe is routed on a job type, field `jobType`: the type of the worker. For credential validation, the input carries the credential type.~~ Credentials are used by many connectors, each with its own job type, so a client validating a credential on its own has no job type to send.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | SUPERSEDED by D46           |
| D37 | No `operation` field. What the worker should do is part of the input, whose shape the worker of the `probeId` defines. The engine does not know about operations: there is no closed enum, no permission per operation, and no check of the shape of `data`. Kickoff. Supersedes D15.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | AGREED                      |
| D38 | REST only, with long polling: workers fetch probes with `POST /v2/probes/activation`, which mirrors `POST /v2/jobs/activation` (same long polling in the gateway, same notification when work is available). No gRPC, no SSE, no stream. Kickoff Q9. Supersedes D10, D34, D35.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | AGREED                      |
| D39 | The lifecycle mirrors jobs: `CREATED`, then `ACTIVATABLE` in engine state until a poll collects it, then `ACTIVATED` until the answer. At collection the engine checks the secrets, as `JobBatchCollector` does: all cached, the values go into the activation response only; one missing, the probe is parked and its resolution requested, and it becomes `ACTIVATABLE` again when the resolution completes.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | AGREED                      |
| D40 | `NO_WORKER` is decided at the deadline. A probe that no poll collected before its deadline ends with `404 NO_WORKER`. A probe that was collected but not answered ends with `504 WORKER_DID_NOT_ANSWER`. One timer, no pickup timeout: when no worker exists, the client waits for its whole `requestTimeout`. Supersedes D4.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D41 | If the gateway cannot hand an activated probe to the worker (the poll's connection broke), it sends `YIELD`, and the probe becomes `ACTIVATABLE` again for the next poll, as the gateway reactivates jobs it could not deliver. No limit on the number of yields: the deadline bounds the probe. Supersedes D17.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | AGREED                      |
| D42 | `ANSWERED` never carries `data`: only `outcome`, `worker`, and `message` for `ERROR`. The response to the client is written by the internal `RESPOND` command that `ANSWER` appends with the `data`; the exporters skip both (D74). Metadata answers (business names, possibly large) stay off the exported records. The audit entry for a validation loses the validation code. Supersedes the `data` part of D16.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | AGREED, refined by D74      |
| D43 | A probe worker that gets an error on its poll, including a 404 from a cluster without probes, retries with exponential backoff (`backoffSupplier`), as the job worker does. It does not stop. Kickoff Q5.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | AGREED                      |
| D44 | The Java worker API mirrors the job worker: `newProbeWorker().probeId(probeId, handler)`, repeated for each `probeId` the worker answers, then options, then `open()`. One probe worker polls all its `probeId`s in one request (D50). See section 5.1. Supersedes D3, D33.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED, refined by D46, D50 |
| D45 | Names kept by the kickoff: the resource is `probes`, the worker action is `answer` (`POST /v2/probes/{probeKey}/answer`). Kickoff Q3 and Q8.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | AGREED                      |
| D46 | A probe is routed on `probeId`, one opaque string. The client sends it in the request; a worker polls for it, as for a job type; the engine matches it by exact string equality and never interprets it. It is a free string: any worker may register any `probeId`, so probes are a general synchronous channel to workers. Convention for Camunda workers, documented but not enforced: `<operation>/<template>`, for example `validate-configuration/io.camunda:aws-credential:1` or `fetch-field-options/io.camunda.connectors.Slack.v1`. The `PROBE` resource ID and the audit description are the `probeId`. Proposed by the Zeebe team on 2026-11-03. Supersedes D36.                                                                                                                                                                                                                                                                                                                                                                                                                      | AGREED                      |
| D47 | When a SaaS and a hybrid runtime register the same `probeId`, the first poll that reaches the partition collects the probe, as for jobs with the same type. Documented limitation. A hybrid runtime that must answer alone registers its own `probeId`. A worker selector comes later.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D48 | The engine checks only the envelope of an answer: `outcome` and the length of `message`. `data` is opaque, because the engine does not know what a `probeId` means. D42 stays: `ANSWERED` never carries `data`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | AGREED                      |
| D49 | The gateway computes the probe's deadline when it receives the request (`now + requestTimeout`) and sends it to the engine as an absolute time. The engine never recomputes it from its own processing time; a `CREATE` processed after its deadline ends at once with `TIMED_OUT` (reason `NO_WORKER`). The gateway keeps waiting `T + 500 ms` and the Java client `T + 1 s`. With long polling, every probe without a worker ends exactly at its deadline, so a relative deadline let the gateway answer `504 DEADLINE_EXCEEDED` before the engine's `404 NO_WORKER` under load (seen on the POC). The engine schedules its time-out check at most 400 ms ahead while a probe is pending (below half of `scheduledTaskCheckInterval`), because the platform runs tasks due later on a 1 s tick, up to 1 s after their time. Known limits: clock skew between gateway and broker above about 400 ms brings back the 504; the hop depends on the platform interval and is derived from it; under heavy backpressure the engine can still miss the 500 ms margin, as for any command. Closes OQ16. | AGREED                      |
| D50 | One activation request carries a list of `probeIds`, and one probe worker polls all of them. This diverges from jobs, where a poll names one type. Probes are rare, so almost every poll returns empty: with one poll per `probeId`, that empty traffic grows with the number of IDs (for example, 100 IDs, 3 partitions and a 10 s poll timeout give about 30 empty activation commands per second per runtime). With a list, it is one poll per runtime. The engine checks `PROBE:ANSWER` per ID and collects only probes of the IDs the poller may answer (D14). A held poll wakes on a notification for any of its IDs. `maxProbesToActivate` and `maxProbesActive` count across all IDs. Closes OQ34.                                                                                                                                                                                                                                                                                                                                                                                        | AGREED                      |
| D51 | No feature flag: probes are available as soon as the release ships them; the endpoints are alpha (section 4.3). Known limit during a rolling update: until every broker runs the new version, a `CREATE` or a poll that reaches an old broker is rejected, and the client gets an error instead of an answer. Workers back off (D43).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             | AGREED, refined by D71      |
| D52 | A poll silently skips the `probeIds` the poller has no `PROBE:ANSWER` on, as job activation skips what the worker may not activate. Clients then get `404 NO_WORKER` at the deadline, so the `NO_WORKER` detail names both causes: no worker polls the `probeId`, or the worker lacks `PROBE:ANSWER` on it.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED                      |
| D53 | A request too large for one log batch (`EXCEEDED_BATCH_RECORD_SIZE`) gets `413 PAYLOAD_TOO_LARGE`: on `CREATE`, the client gets it at once; on `ANSWER`, the worker gets it, and the Java client then sends `ProbeAnswer.error("The answer is too large.")`, so the client gets `200` with `outcome: ERROR` instead of waiting for the deadline. Closes OQ14.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D54 | A `probeId` is 1 to 256 characters, without whitespace, and not `*` (the authorization wildcard, section 8). A poll lists 1 to 100 distinct `probeIds`. Anything else gets `400 INVALID_ARGUMENT`, on `CREATE` and on activation. The Java builder rejects a duplicate `probeId` with `IllegalArgumentException`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | AGREED                      |
| D55 | When sending an answer fails with `503` or a network error, the Java worker retries with its `backoffSupplier` until the probe's deadline, then logs and gives up. `400`, `403`, `404` and `409` are never retried; `413` follows D53.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D56 | The Elasticsearch and OpenSearch exporters ship a `PROBE` and a `PROBE_BATCH` template, both off by default (`index.probe`, `index.probeBatch` = `false`). The audit log does not depend on them (section 7).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D57 | A probe may run more than once: a `CREATE` retried on another partition after a lost response (D22) or a yield (D41) can hand the same request to two workers. No idempotency key. So probes are read-only in phase 2; task testing must revisit this (OQ33).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | AGREED                      |
| D58 | Phase 2 delivers the core in `camunda/camunda` first: records, engine, gateway and REST, OpenAPI, Java client, exporters, audit log, and the `PROBE` resource in the Identity UI. The connector runtime (section 5.2) follows in `camunda/connectors`; its design was agreed with Connectors on 2026-11-12.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED                      |
| D59 | The answer is a union of three shapes, with a discriminator on `outcome` (OpenAPI `oneOf`). Worker request: `OK` → `{outcome, data}`, `data` required; `UNSUPPORTED` → `{outcome}`; `ERROR` → `{outcome, message}`, `message` required. Any other combination gets `400 INVALID_ARGUMENT`. The client's 200 response has the same three shapes, plus `worker`. Closes OQ23.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED                      |
| D60 | The engine finds references by FEEL detection, as for input mappings (D28). The request has no explicit `secretRefs` or `clusterVars` lists; they come back only if parsing a large input proves too slow. Closes OQ32.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | AGREED                      |
| D61 | A hybrid runtime's probe handler replaces the legacy `{{secrets.X}}` syntax from the runtime's own configuration, as it does for its jobs. The engine passes this syntax through as text. Connector runtime work (D58). Closes OQ35.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | AGREED                      |
| D62 | Probes use 504 (`WORKER_DID_NOT_ANSWER`, `SECRET_RESOLUTION_TIMEOUT`, `DEADLINE_EXCEEDED`), documented in the OpenAPI, although the guidelines' response-code table has no 504. Closes OQ24.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | AGREED                      |
| D63 | Metadata epic: the connector runtime caps a metadata answer (for example 1,000 items and 256 KB) and pages longer lists with a page token. Not phase 2. Closes OQ13.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | AGREED                      |
| D64 | Metadata epic: read permission on a `SECRET_REFERENCE` cluster variable covers its secrets, so a Modeler user who may use a saved connection can fill a dropdown without `SECRET:READ` on each secret. Validation keeps `SECRET:READ` (D14). Not phase 2. Closes OQ17.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D65 | Clients build the input in one place, with a helper that writes user-entered values as FEEL string literals, so a value never becomes an expression. Hub work. Closes OQ20.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       | AGREED                      |
| D66 | `CREATE` checks permissions before it evaluates: (1) `PROBE:CREATE` on the `probeId` and the tenant, else 403; (2) the input parses as FEEL with no imprecise reference, else 400; (3) `SECRET:READ` on each secret found, else 403; (4) the input evaluates to a context, else 400. A caller without `PROBE:CREATE` never makes the engine parse or evaluate.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | AGREED                      |
| D67 | The client needs `SECRET:READ` on each referenced secret, not a reveal permission. Confirmed with Security on 2026-11-04. Closes OQ29.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D68 | `CREATE` does not check `CLUSTER_VARIABLE:READ` on referenced cluster variables: `PROBE:CREATE` covers them, as `EXPRESSION:EVALUATE` does for the expression endpoint. Secrets reached through a cluster variable still need `SECRET:READ` (D67). Confirmed with Security on 2026-11-04. Closes OQ31.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | AGREED                      |
| D69 | The Connectors role gets `PROBE:ANSWER` on `*` by default. Confirmed with Security on 2026-11-04. The default role for `PROBE:CREATE` stays open (OQ30).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | AGREED                      |
| D70 | Audit log mapping: `CREATED` → `CREATE`, `ANSWERED` → `COMPLETE`, `TIMED_OUT` and `CANCELED` → `CANCEL` with result `FAIL`; category `ADMIN`; entity type `PROBE`; `entityKey` = the probe key; `entityDescription` = the `probeId`. The existing actor configuration applies unchanged, as for jobs: by default only user actors are logged (`camunda.auditLog.user` logs every category, `client` and `unknown` none). So a probe created with a user's token gets its `CREATE` entry; a probe created by an M2M client, the `ANSWERED` entry (the worker is a client) and the `TIMED_OUT` entry (written by the engine, no actor) are logged only when the operator enables those actors. The Phase 2 metric "every validation has an audit entry" holds for user-created probes with the default configuration.                                                                                                                                                                                                                                                                               | AGREED                      |
| D71 | Rolling update, in addition to D51: the new `PROBE` and `PROBE_BATCH` events are unknown to a broker of the previous version. An old follower that must replay one is unhealthy until it runs the new version; Raft replication goes on meanwhile. If leadership moves back to an old broker that must replay a probe event, or process a probe command that is still pending, the partition stalls until that broker is updated. Worker backoff (D43) does not help there. The risk is accepted, as for any new record (`docs/zeebe/rolling_updates.md`). From the review of camunda/camunda#68884.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | AGREED                      |
| D72 | The cluster never writes a secret value to a record: the engine keeps placeholders and puts values only into the activation response (D12). What a worker sends in `data` and `message` is its own: the `PROBE:ANSWER` command is written to the log as sent, and skipping it in the exporters (D42) does not keep it off the replicated log. A worker must not put injected values into its answer. The Java client's default `ERROR` message removes them (D30); `data` is not checked (D48). From the review of camunda/camunda#68884.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | AGREED                      |
| D73 | The activation response carries `secretPointers`: the JSON pointers of the input leaves into which the engine injected a secret value, taken from the references the engine found at `CREATE` (D28). No secret names and no values. The Java client removes the values of these leaves from `ActivatedProbe.toString()` and from the default `ERROR` message (D30). A leaf that mixes a secret with other text is removed whole; a message that holds only part of such a leaf is not caught, so a handler must not build messages from secret values. Divergence from jobs, which have no such field: only the probe SDK promises this removal. From the review of camunda/camunda#68884.                                                                                                                                                                                                                                                                                                                                                                                                        | AGREED                      |
| D74 | A processed command carries one response, and `ANSWER` has two requesters: the worker and the client. So `ANSWER` answers the worker, writes `ANSWERED`, and appends the internal command `RESPOND`, which carries `data` and answers the client held since `CREATE`. A client that sends `RESPOND` is rejected. The exporters skip `RESPOND` as `ANSWER` (D42). The POC did this since 2026-10-26; the specification missed it. Consequence: `data` is in the batch twice (`ANSWER` and `RESPOND`), so the largest answer is about half of what D7 implied; an answer too large gets `413` and becomes an `ERROR` answer (D53). From the implementation of camunda/camunda#68865, 2026-11-12.                                                                                                                                                                                                                                                                                                                                                                                                    | AGREED                      |
| D75 | A probe is parked on a secret as a job is. `SECRET_REFERENCE` records get `probeKeys`, next to `jobKeys`: the probes a `RESOLUTION_REQUESTED` parks. Two partition-local column families mirror the job ones: `SECRET_REFERENCES_BY_PROBE` and `PROBES_BY_SECRET_REFERENCE`, emptied when the probe becomes `ACTIVATABLE` again or ends (`ANSWERED`, `TIMED_OUT`, `CANCELED`). The `RESOLUTION_REQUESTED` and `RESOLUTION_COMPLETED` appliers shipped in 8.10.0, so each gets a version 2 that also parks or releases probes; version 1 is kept. `RESOLUTION_COMPLETED` makes a parked probe `ACTIVATABLE` when none of its references is still pending, and `RESOLUTION_FAILED` cancels the parked probes, both without the batched follow-up commands jobs use: only probes in flight wait for a reference. The POC had these; the specification did not. From the implementation of camunda/camunda#68866, 2026-11-13.                                                                                                                                                                         | AGREED                      |

## 4. Client API

`POST /v2/probes`

Request:

|      Field       |    Type    |      Required      |                                                                                                                            Description                                                                                                                            |
|------------------|------------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `probeId`        | string     | Yes                | The routing key, as the worker registered it, for example `validate-configuration/io.camunda:aws-credential:1` (D46). 1 to 256 characters, no whitespace, not `*` (D54).                                                                                          |
| `input`          | string     | Yes                | A FEEL expression that evaluates to a context. The worker that registered the `probeId` defines the shape of this context (D11). References to `camunda.vars.*` and `camunda.secrets.*` follow the input-mapping rules; an imprecise reference is rejected (D28). |
| `tenantId`       | string     | With multi-tenancy | The tenant.                                                                                                                                                                                                                                                       |
| `requestTimeout` | int64 (ms) | No                 | How long to wait for an answer. 0 or absent: the cluster's request timeout. Maximum 30 s (D18).                                                                                                                                                                   |

Example, credential validation:

```json
{
  "probeId": "validate-configuration/io.camunda:aws-credential:1",
  "input": "={region: \"eu-west-1\", accessKey: camunda.secrets.awsKey, secretKey: camunda.secrets.awsSecret}"
}
```

With a saved connection, the input is the cluster variable: `"input": "=camunda.vars.env.awsProd"`.

Answer: `{"outcome": "OK", "worker": "connectors-saas", "data": {"status": "FAILURE", "code": "UNAUTHORIZED", "message": "AWS rejected the credential."}}`.

Examples for later uses are in section 11.

Responses:

| HTTP |            Title            |                                                                                                                                    Case                                                                                                                                    |
|------|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 200  |                             | A worker answered. Body: the answer envelope (D9), one of three shapes (D59): `{outcome: OK, worker, data}`, `{outcome: UNSUPPORTED, worker}`, `{outcome: ERROR, worker, message}`. `worker` is the worker that answered (D20).                                            |
| 400  | `INVALID_ARGUMENT`          | Invalid input: expression not valid, not a context, imprecise reference (D28), input blank, `probeId` not valid (D54), `requestTimeout` negative or above 30 s (D18).                                                                                                      |
| 400  | `SECRET_RESOLUTION_ERROR`   | A referenced secret could not be resolved, or its value could not be injected into the input (D13).                                                                                                                                                                        |
| 401  | `UNAUTHORIZED`              | Not authenticated.                                                                                                                                                                                                                                                         |
| 403  | `FORBIDDEN`                 | Not authorized: no `PROBE:CREATE` on the `probeId`, no `SECRET:READ` on a referenced secret, or not assigned to the tenant (section 8).                                                                                                                                    |
| 404  | `NO_WORKER`                 | No worker collected the probe before its deadline (D40). Detail example: "No worker for `validate-configuration/io.camunda:aws-credential:1` collected the probe within 10000 ms. Check that a worker polls this probe ID and holds PROBE:ANSWER on it, then retry." (D52) |
| 413  | `PAYLOAD_TOO_LARGE`         | The request does not fit in one log batch (D53).                                                                                                                                                                                                                           |
| 500  | `INTERNAL`                  | Unexpected error in the gateway or the broker.                                                                                                                                                                                                                             |
| 503  | `RESOURCE_EXHAUSTED`        | Backpressure on the partition, as for jobs.                                                                                                                                                                                                                                |
| 504  | `WORKER_DID_NOT_ANSWER`     | A worker collected the probe but did not answer in time.                                                                                                                                                                                                                   |
| 504  | `SECRET_RESOLUTION_TIMEOUT` | The deadline passed while the probe waited for its secrets (D13).                                                                                                                                                                                                          |
| 504  | `DEADLINE_EXCEEDED`         | The gateway timed out before the engine answered, for example after a leader change.                                                                                                                                                                                       |

### 4.1 Answer endpoint (worker)

AGREED (D31, D45).

`POST /v2/probes/{probeKey}/answer`

Request:

|   Field   |             Required              |                                                Description                                                |
|-----------|-----------------------------------|-----------------------------------------------------------------------------------------------------------|
| `outcome` | Yes                               | `OK`, `UNSUPPORTED` or `ERROR` (D9).                                                                      |
| `data`    | With `OK` only, required there    | A JSON object: the result, as the worker of the `probeId` defines it. The engine does not check it (D48). |
| `message` | With `ERROR` only, required there | At most 500 characters (D30).                                                                             |

The body is one of three shapes, chosen by `outcome` (D59): `{outcome: OK, data}`,
`{outcome: UNSUPPORTED}`, `{outcome: ERROR, message}`.

There is no worker name in the body: the engine uses the worker of the poll that collected the
probe (D24).

Responses, mirroring job completion:

|           HTTP           |                                                   Case                                                   |                     For jobs                     |
|--------------------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| 204                      | Accepted.                                                                                                | Job completion answers 204.                      |
| 400 `INVALID_ARGUMENT`   | Invalid body: unknown outcome, a field that does not fit the outcome (D59), `message` too long (D48).    | Invalid variables.                               |
| 401 `UNAUTHORIZED`       | Not authenticated.                                                                                       | Same.                                            |
| 403 `FORBIDDEN`          | No `PROBE:ANSWER` on the `probeId` (D14, D24).                                                           | `UPDATE_PROCESS_INSTANCE` missing.               |
| 404 `NOT_FOUND`          | Probe not found: already ended or expired (F4), or it belongs to a tenant the worker is not assigned to. | Job not found, also for a job of another tenant. |
| 409 `INVALID_STATE`      | The probe exists but is not `ACTIVATED` (`ACTIVATABLE` or `WAITING_FOR_SECRET_RESOLUTION`).              | Job not activated.                               |
| 413 `PAYLOAD_TOO_LARGE`  | The answer does not fit in one log batch. The Java client then sends a short `ERROR` answer (D53).       | Not mapped for jobs today.                       |
| 500 `INTERNAL`           | Unexpected error.                                                                                        | Same.                                            |
| 503 `RESOURCE_EXHAUSTED` | Backpressure on the partition.                                                                           | Same.                                            |

The REST layer first rejects a malformed body with 400. The engine then checks in this order: the
probe exists, the permission and the tenant, the envelope, then the state. So a late answer with a
well-formed body always gets 404.

### 4.2 Probe activation (worker)

AGREED (D38). Long polling, as job activation.

`POST /v2/probes/activation`

Request, the job activation request without the fields that have no meaning for probes:

|            Field            | Required |                                                                        Description                                                                         | In `JobActivationRequest` |
|-----------------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| `probeIds`                  | Yes      | The probe IDs to collect probes for: 1 to 100 distinct values, each valid as in D54 (D46, D50). IDs the poller has no `PROBE:ANSWER` on are skipped (D52). | `type` (one value)        |
| `worker`                    | No       | The worker name, returned in the answer envelope (D20).                                                                                                    | `worker`                  |
| `maxProbesToActivate`       | Yes      | The most probes this call returns.                                                                                                                         | `maxJobsToActivate`       |
| `requestTimeout`            | No       | How long the gateway holds the poll open when no probe is waiting. Same default and meaning as for jobs.                                                   | `requestTimeout`          |
| `tenantIds`, `tenantFilter` | No       | As for jobs.                                                                                                                                               | Same                      |

Left out: `timeout` (the client sets the probe's deadline, D18) and `fetchVariable` (the input is
always complete).

Response: `{"probes": [ActivatedProbe…]}`, empty when the poll timed out. `ActivatedProbe` carries
`probeKey`, `probeId`, `worker`, `deadline`, `tenantId`, `physicalTenantId` and `input`, with the
secret values injected (D12), and `secretPointers` (D73). All are required, as in
`ActivatedJobResult`.

|       Field        |        Type         |                                                           Meaning                                                            |
|--------------------|---------------------|------------------------------------------------------------------------------------------------------------------------------|
| `probeKey`         | `ProbeKey` (string) | The key to answer with (`POST /v2/probes/{probeKey}/answer`).                                                                |
| `probeId`          | string              | The probe ID, so a worker that polls several IDs picks the handler (D50).                                                    |
| `worker`           | string              | The worker name of this poll, returned to the client in the answer (D20).                                                    |
| `deadline`         | integer, epoch ms   | The absolute deadline (D49). An answer after it gets 404.                                                                    |
| `tenantId`         | `TenantId`          | The tenant of the probe.                                                                                                     |
| `physicalTenantId` | string              | The physical tenant of the probe, as for jobs.                                                                               |
| `input`            | object              | The evaluated input, secret values injected.                                                                                 |
| `secretPointers`   | array of strings    | The JSON pointers of the `input` leaves the engine injected a secret value into, empty when none. No names, no values (D73). |

Example, captured from the POC (secret value masked), with `secretPointers` added (D73):

```json
POST /v2/probes/activation
{
  "probeIds": ["validate-configuration/io.camunda:aws-credential:1",
               "validate-configuration/io.camunda:slack:1"],
  "worker": "connectors-runtime",
  "maxProbesToActivate": 10,
  "requestTimeout": 0
}

200
{
  "probes": [
    {
      "probeKey": "2251799813685464",
      "probeId": "validate-configuration/io.camunda:aws-credential:1",
      "worker": "connectors-runtime",
      "deadline": 1794232249291,
      "tenantId": "<default>",
      "physicalTenantId": "default",
      "input": { "accessKey": "<secret value>", "region": "eu-west-1" },
      "secretPointers": ["/accessKey"]
    }
  ]
}
```

How it works, as job activation:

1. The gateway sends `PROBE_BATCH:ACTIVATE` to every partition of the physical tenant, round-robin
   (`RoundRobinActivateJobsHandler`).
2. Each partition collects `ACTIVATABLE` probes with one of these `probeIds` that the poller may answer (D14),
   checks their secrets (D39), writes `PROBE_BATCH:ACTIVATED`, and responds with the input
   injected into the response only.
3. If no partition returned a probe, the gateway holds the request (`LongPollingActivateJobsHandler`)
   until a partition announces a probe with one of these `probeIds`, or until `requestTimeout`.
4. A partition announces a probe when it becomes `ACTIVATABLE`: at `CREATED`, after `YIELDED`, and
   when its secrets are resolved. The notification uses its own topic,
   `<physicalTenant>-probesAvailable`, so a probe does not wake job pollers.
5. If the gateway cannot send the response to the worker, it sends `YIELD` for each probe in it
   (D41), as it reactivates jobs it could not deliver.

Errors mirror job activation (400, 401, 403, 500, 503). 403 means only that the worker asked for a
tenant it is not assigned to, as for jobs; `probeIds` it may not answer are skipped (D52). A cluster without probes answers 404; the
Java client retries with backoff (D43).

The gateway has its own `LongPollingActivateProbesHandler`; the job handlers do not change. A held
poll is registered under `(physical tenant, probeId)` for each of its `probeIds`, wakes on a
notification for any of them, and is removed from every key when it is woken, times out or fails
(D50).

### 4.3 OpenAPI

Required by the [REST API endpoint guidelines](https://github.com/camunda/camunda/blob/51b0d787e4f705c563655a5a79794729e862ed3c/docs/rest-api-endpoint-guidelines.md),
checked on 2026-10-30. Not a decision: Spectral fails the build without these.

- A new `probes.yaml` in `zeebe/gateway-protocol/src/main/proto/v2/`, tag `Probe`. Its three paths
  are `$ref`'d in `rest-api.yaml`, sorted alphabetically (§2.1, §2.3).
- `x-scope: physical-tenant` (§2.20): each path is also served under
  `/physical-tenants/{physicalTenantId}/v2/…`, for example
  `/physical-tenants/{physicalTenantId}/v2/probes`; the path without prefix goes to the default
  physical tenant (ADR `docs/adr/management/003`). A probe stays in its physical tenant: only polls
  to that physical tenant collect it.
- Every operation has these fields (§2.3):

  |           Field           |                                    `createProbe`                                     |     `activateProbes`      |          `answerProbe`           |
  |---------------------------|--------------------------------------------------------------------------------------|---------------------------|----------------------------------|
  | Path                      | `POST /probes`                                                                       | `POST /probes/activation` | `POST /probes/{probeKey}/answer` |
  | `x-eventually-consistent` | `false`                                                                              | `false`                   | `false`                          |
  | `x-scope`                 | `physical-tenant`                                                                    | `physical-tenant`         | `physical-tenant`                |
  | `x-added-in-version`      | The release that ships it                                                            | Same                      | Same                             |
  | `x-required-permissions`  | `{PROBE, CREATE}` and `{dynamic: true, note: SECRET:READ on each referenced secret}` | `{PROBE, ANSWER}`         | `{PROBE, ANSWER}`                |

- The `PROBE` pairs (`CREATE`, `ANSWER`) are added to `resource-permissions.json` (§2.19).

- Alpha (§2.8): each summary ends with `(alpha)`, and each description contains the exact two lines
  "This endpoint is an alpha feature and may be subject to change / in future releases."

- `ProbeKey` in `keys.yaml`: `type: string`, extends `LongKey` (§2.4).

- The field of `ActivatedProbeResult` is `probeKey`, not `key` (§2.2).

- `tenantId` references `TenantId` in `identifiers.yaml`.

- Separate request and response schemas (§2.6): `ProbeCreationRequest`, `ProbeResult`,
  `ProbeActivationRequest`, `ProbeActivationResult`, `ActivatedProbeResult`, `ProbeAnswerRequest`.

- Every error response is `application/problem+json`, with the shared responses in
  `common-responses.yaml` (§2.9).

- Semantic graph (§2.18): `activateProbes` establishes `ProbeKey`; `answerProbe` requires it.

- Controller (§4.2): `@CamundaRestController` and `@CamundaPostMapping`.

Points not covered by the guidelines, decided: the answer union with a discriminator on `outcome`
(D59) for `ProbeAnswerRequest` and `ProbeResult`, and 504 (D62).

## 5. Worker API

AGREED (D44). There are two layers. The Java client knows nothing about connectors. The connector
runtime adapts the connector SDK to the Java client.

### 5.1 Java client (`clients/java`)

#### `newProbeWorker()`

A step builder, as the job worker's `newWorker().jobType(type).handler(handler)…open()`. One probe
worker polls all its `probeId`s in one request, each with its own handler (D44, D50):

```java
client.newProbeWorker()
    .probeId("validate-configuration/io.camunda:aws-credential:1", awsCredentialHandler)
    .probeId("validate-configuration/io.camunda:slack:1", slackHandler)
    .name("connectors-saas")                            // options, as for jobs
    .tenantIds("tenant-a")
    .maxProbesActive(8)
    .open();
```

Options taken from the job worker, all required in the implementation:

|                                       Probe worker                                        |            Job worker            |
|-------------------------------------------------------------------------------------------|----------------------------------|
| `name`                                                                                    | `name`                           |
| `tenantIds`, `tenantFilter`                                                               | `tenantIds`, `tenantFilter`      |
| `maxProbesActive`: how many probes the worker handles at once                             | `maxJobsActive`                  |
| `pollInterval`, `requestTimeout`                                                          | `pollInterval`, `requestTimeout` |
| `backoffSupplier`: retry after a failed poll, including on a cluster without probes (D43) | `backoffSupplier`                |
| `probeExceptionHandler`: provides the `ERROR` message (D30)                               | `jobExceptionHandler`            |
| `metrics`                                                                                 | `metrics`                        |

Left out, because they have no meaning for probes: `timeout` (the client sets the deadline, D18),
`fetchVariables` (the input is always complete), `withLease`, and the stream options
(`streamEnabled`, `streamTimeout`, `streamInactivityTimeout`: probes do not stream, D38).

#### `ProbeHandler`

```java
@FunctionalInterface
public interface ProbeHandler {
  ProbeAnswer handle(ActivatedProbe probe) throws Exception;
}
```

Divergence from `JobHandler` (`void handle(JobClient client, ActivatedJob job)`): a probe has one
terminal command (`ANSWER`, D9) and a short deadline. Returning the answer makes two mistakes
impossible: forgetting to answer and answering twice. The SDK sends the returned answer.

#### `ActivatedProbe`

`ActivatedJob`'s fields, without the process:

|                                Kept, same name as `ActivatedJob`                                 |                                                                              Probe-specific                                                                               |                                                                                 Dropped from `ActivatedJob`                                                                                 |
|--------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `getKey()`, `getWorker()`, `getDeadline()`, `getTenantId()`, `getPhysicalTenantId()`, `toJson()` | `getProbeId()` (instead of `getType()`), `getSecretPointers()` (D73), `getInput()`, `getInputAsMap()`, `getInputAsType(Class<T>)` (instead of the `getVariables…` family) | Process fields, `getRetries()`, `getPriority()`, `getCustomHeaders()`, `getUserTask()`, `getKind()`, `getListenerEventType()`, `getDocumentReferences()`, `getTags()`, `getJobLeaseToken()` |

The input arrives with the secret values already injected by the engine (D12).

Divergence in `toString()`: `ActivatedProbe.toString()` prints everything except the injected
values, with the same removal as D30, at the leaves that `getSecretPointers()` names (D73). `toJson()` stays complete.

#### Errors and answers

If the handler throws an exception, the SDK answers `outcome: ERROR` with a `message` (D30):

- Default: the exception's message, with the secret values injected into this probe removed (the
  leaves named by `secretPointers`, D73), cut to
  500 characters, as the connector runtime does for jobs (`OutboundConnectorExceptionHandler`).
- Configurable, as `errorMessageProvider` for a job worker.
- Not the plain job worker's default (the stack trace): a probe's message goes into a user's dialog,
  and a probe always carries injected secrets.

The worker:

1. Polls `POST /v2/probes/activation` for all its `probeId`s in one request (D38, D50, section 4.2).
2. Runs the handler of the probe's `probeId` for each probe it receives.
3. Sends each answer with `POST /v2/probes/{probeKey}/answer` (D31, section 4.1). On `503` or a
   network error it retries until the deadline (D55); on `413` it sends a short `ERROR` (D53).

```java
ProbeAnswer.ok(data);          // outcome OK
ProbeAnswer.unsupported();     // outcome UNSUPPORTED (D5)
ProbeAnswer.error(message);    // outcome ERROR, at most 500 characters (D30)
```

### 5.2 Connector runtime

AGREED with Connectors on 2026-11-12. Not in the first delivery (D58).

The connector SDK already has the validation contract (`io.camunda.connector.api.validation`):

```java
public interface ConfigurationValidator<T> {
  ConfigurationValidationResult validate(T configuration);
}
```

`ConfigurationValidationResult` is `SUCCESS`, `FAILURE` or `UNSUPPORTED`, with `code` and `message`.

The runtime opens one probe worker for all its `ConfigurationValidator`s, with one `probeId` per
validator, `validate-configuration/<configuration ID>`, for example
`validate-configuration/io.camunda:aws-credential:1`:

```java
var builder = client.newProbeWorker();
for (var validator : registry) {
  builder.probeId("validate-configuration/" + validator.configurationId(), probe -> {
      T configuration = bind(probe.getInputAsMap(), validator.type());  // secrets already injected (D12)
      return toAnswer(validator.validate(configuration));                // D30
  });
}
builder.name(workerName).open();
```

A credential type is used by many connectors, but it has one validator, so it has one `probeId`.
The client needs no job type.

The handler binds the input to the validator's type with the runtime's object mapper and reuses
phase 1's mapping (D30). `ConfigurationValidationService` catches every failure and turns it into
a result with a fixed message:

|                       In the runtime                        |                               SDK result                               |            Envelope             |
|-------------------------------------------------------------|------------------------------------------------------------------------|---------------------------------|
| The validator returns `SUCCESS`, `FAILURE` or `UNSUPPORTED` | As returned                                                            | `outcome: OK`, result in `data` |
| The input cannot be bound or is invalid                     | `FAILURE`, `INVALID_INPUT`, "The resolved configuration is not valid." | `outcome: OK`                   |
| The validator throws                                        | `FAILURE`, `ERROR`, "Validation could not be completed."               | `outcome: OK`                   |

Connector authors keep writing `ConfigurationValidator<T>`. They never see probes.

Wiring in the runtime:

- A `ProbeWorkerManager` implements `CamundaClientLifecycleAware`, as `OutboundConnectorManager`
  does, and opens one probe worker with one `probeId` per entry of `ConfigurationValidationRegistry`, closing it on
  `onStop`.
- A hybrid runtime that must answer alone registers its own `probeId` (D47).
- Resolution (FEEL evaluation, `SecretReferenceResolver`) moves to the engine. The handler of a
  hybrid runtime still replaces the legacy local syntax (`{{secrets.X}}`) from its own configuration (D61).
- On a cluster without probes, the probe worker retries with backoff (D43).

## 6. Engine behavior

AGREED (D23, D39, D40, D41).

States, kept between two commands: `ACTIVATABLE` (waiting for a poll), `WAITING_FOR_SECRET_RESOLUTION`
(collected by a poll, a secret not cached) and `ACTIVATED` (handed to a worker, waiting for its
answer). `ANSWERED`, `TIMED_OUT` and `CANCELED` end the probe: their appliers remove it from the
state.

|                  State before                  |                      Trigger                      |                                Events written                                 |           State after           |             Client gets             |
|------------------------------------------------|---------------------------------------------------|-------------------------------------------------------------------------------|---------------------------------|-------------------------------------|
| (no probe)                                     | `CREATE`: invalid input or forbidden              | None (rejected)                                                               | (no probe)                      | 400 `INVALID_ARGUMENT` or 403       |
| (no probe)                                     | `CREATE`                                          | `CREATED`, notification for the `probeId`                                     | `ACTIVATABLE`                   |                                     |
| (no probe)                                     | `CREATE` processed after its deadline (D49)       | `CREATED`, then `TIMED_OUT` (reason `NO_WORKER`)                              | (removed)                       | 404 `NO_WORKER`                     |
| `ACTIVATABLE`                                  | A poll for its `probeId`, all secrets cached      | `PROBE_BATCH:ACTIVATED` (values in the response only)                         | `ACTIVATED`                     |                                     |
| `ACTIVATABLE`                                  | A poll for its `probeId`, a secret not cached     | `SECRET_REFERENCE:RESOLUTION_REQUESTED` (section 9); the poll skips the probe | `WAITING_FOR_SECRET_RESOLUTION` |                                     |
| `ACTIVATABLE`                                  | A poll for its `probeId`, injecting a value fails | `CANCELED` (reason `SECRET_RESOLUTION_ERROR`)                                 | (removed)                       | 400 `SECRET_RESOLUTION_ERROR` (D13) |
| `ACTIVATABLE`                                  | `TIME_OUT` (deadline)                             | `TIMED_OUT` (reason `NO_WORKER`)                                              | (removed)                       | 404 `NO_WORKER` (D40)               |
| `WAITING_FOR_SECRET_RESOLUTION`                | All secrets resolved                              | Notification for the `probeId`                                                | `ACTIVATABLE`                   |                                     |
| `WAITING_FOR_SECRET_RESOLUTION`                | A secret fails to resolve                         | `CANCELED` (reason `SECRET_RESOLUTION_ERROR`)                                 | (removed)                       | 400 `SECRET_RESOLUTION_ERROR` (D13) |
| `WAITING_FOR_SECRET_RESOLUTION`                | `TIME_OUT` (deadline)                             | `TIMED_OUT` (reason `SECRET_RESOLUTION_TIMEOUT`)                              | (removed)                       | 504 `SECRET_RESOLUTION_TIMEOUT`     |
| `ACTIVATED`                                    | Valid `ANSWER`                                    | `ANSWERED`                                                                    | (removed)                       | 200                                 |
| `ACTIVATED`                                    | `ANSWER` that is not valid                        | None (rejected)                                                               | `ACTIVATED`                     | Worker gets 400                     |
| `ACTIVATED`                                    | `YIELD` (the gateway could not deliver it)        | `YIELDED`, notification for the `probeId`                                     | `ACTIVATABLE`                   |                                     |
| `ACTIVATED`                                    | `TIME_OUT` (deadline)                             | `TIMED_OUT` (reason `WORKER_DID_NOT_ANSWER`)                                  | (removed)                       | 504 `WORKER_DID_NOT_ANSWER`         |
| `ACTIVATABLE`, `WAITING_FOR_SECRET_RESOLUTION` | `ANSWER`                                          | None (rejected with `INVALID_STATE`)                                          | Unchanged                       | Worker gets 409 (D31)               |
| (removed)                                      | `ANSWER` or `YIELD` arrives late                  | None (rejected with `NOT_FOUND`)                                              | (removed)                       | Worker gets 404 (F4)                |

Notes:

1. `TIMED_OUT` carries one of three reasons, depending on the state at the deadline: never
   collected (`NO_WORKER`), parked (`SECRET_RESOLUTION_TIMEOUT`), collected (`WORKER_DID_NOT_ANSWER`).
2. Secrets are checked only when a poll collects the probe, as for jobs. A probe that no worker
   polls for never triggers a secret resolution.
3. Who may send `ANSWER` (D24): any holder of `PROBE:ANSWER` on the probe's `probeId` and tenant,
   for an `ACTIVATED` probe, with its key. As for jobs, there is no check that the sender is the
   worker that collected it.
4. The `worker` in the answer (D20) is the worker name of the poll that collected the probe,
   recorded in `PROBE_BATCH:ACTIVATED`.

- The engine keeps the client request with the probe and answers it when the probe ends
  (`AsyncRequestBehavior`).
- The deadline uses a dedicated time-out scheduler per partition. While a probe is pending, it
  checks at most 400 ms ahead (half of `scheduledTaskCheckInterval` minus a margin); it stops when
  no probe is pending (D49).

### 6.1 Delivery failure and worker loss

AGREED (D40, D41).

| #  |                                  Case                                   |                                                  Behavior                                                  |
|----|-------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| F1 | No worker polls for the `probeId`                                       | The probe stays `ACTIVATABLE`. At the deadline: 404 `NO_WORKER` (D40).                                     |
| F2 | The gateway could not send the activation response to the worker        | `YIELD`: the probe is `ACTIVATABLE` again, and the next poll collects it (D41).                            |
| F3 | Collected, the worker never answers (crash, network cut, handler hangs) | At the deadline: 504 `WORKER_DID_NOT_ANSWER`.                                                              |
| F4 | The worker answers after the deadline                                   | `ANSWER` rejected with `NOT_FOUND`. The SDK logs it at debug level.                                        |
| F5 | Leader change while a probe is in flight                                | The client gets `DEADLINE_EXCEEDED` from the gateway. The probe expires at its deadline on the new leader. |

Divergence from jobs: a job waits in `ACTIVATABLE` without limit; a probe has a user waiting, so its
deadline also ends it when nobody collects it.

Cost of D40: when no worker exists, the client learns it only at the deadline. Hub can send a short
`requestTimeout` (for example 10 s).

### 6.2 Which worker answers

AGREED (D20, D47).

The first poll that reaches the probe's partition collects it, as for jobs. Replicas of one runtime
poll the same `probeId` and are equivalent.

A SaaS runtime and a hybrid runtime that register the same `probeId` compete: the answer can depend
on which one polled first. Secrets are the same in both, because the engine injects them (D12); only
network reach differs, for example a JDBC credential for an on-premises database. Documented
limitation (D47). A hybrid runtime that must answer alone registers its own `probeId`. A worker
selector comes later.

### 6.3 Ending reasons

`TIMED_OUT`: `NO_WORKER`, `SECRET_RESOLUTION_TIMEOUT` or `WORKER_DID_NOT_ANSWER`.
`CANCELED`: `SECRET_RESOLUTION_ERROR`.

### 6.4 Partitions

AGREED (D22).

1. `CREATE` has no key, so it goes round-robin to the next partition of the physical tenant, as
   expression evaluation. If the request is not accepted, the gateway tries another partition.
2. A poll goes to every partition, as job activation (section 4.2).
3. `ANSWER` and `YIELD` go to the partition that owns the probe, found from the probe key.
4. `TIME_OUT` comes from the partition's own scheduler.
5. No command distribution: a probe lives on one partition.

Side effect: the secret cache is per node (secret ADR D7). Round-robin spreads probes over nodes,
so a secret can miss the cache once per node. The cold path is accepted (D12).

### 6.5 Input evaluation

AGREED (D28). A job gets its references in two steps; a probe does both at `CREATE`.

How jobs do it:

1. At deploy, `VariableMappingTransformer` finds the references on the parsed FEEL tree, each with
   the JSON pointer of the leaf it sits in: `SecretReference.parse` for `camunda.secrets.<name>`,
   `ClusterVariableReference.parse` for `camunda.vars.<scope>.<name>`.
   `SecretReferenceLeafPrecisionValidator` rejects the deployment when
   `SecretReference.hasImpreciseReference` is true.
2. At job creation, `BpmnJobBehavior.mergedSecretReferences` uses
   `ClusterVariableJobSecretResolver.resolveInto` on the current cluster variable state: a
   `SECRET_REFERENCE` variable adds its stored references at the right pointer; a `JSON` variable or
   a missing one adds nothing.
3. During evaluation, `camunda.secrets.<name>` evaluates to its own placeholder text (secret ADR
   D3).

At `CREATE`, after `PROBE:CREATE` and the tenant are checked (403, D66), the engine:

1. Parses `input` as a FEEL expression. Otherwise 400.
2. Rejects imprecise references with 400, with the deploy rule (`hasImpreciseReference`).
3. Detects direct secrets with `SecretReference.parse`. Pointers are relative to the input context.
4. Detects secrets reached through cluster variables with `ClusterVariableReference.parse`, then
   `ClusterVariableJobSecretResolver.resolveInto`, with the same scope rules.
5. Checks `SECRET:READ` on every secret found in steps 3 and 4 (D14). Otherwise 403.
6. Evaluates the input with the input-mapping evaluation contexts: secrets become placeholders,
   cluster variables are available, there are no process variables. If the result is not a
   context, 400.

What "static" means:

|                                               Case                                                |                                       Result                                        |
|---------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `camunda.secrets.<name>` or `camunda.vars.<scope>.<name>` written directly, found at its own leaf | A reference. Through a cluster variable, only a `SECRET_REFERENCE` variable counts. |
| A reference inside a list literal or a context produced by a branch                               | Imprecise: rejected with 400, as at deploy.                                         |
| Text in a string literal, a name built at runtime, a `JSON` cluster variable spelling a reference | Not a reference: plain text, never resolved. Jobs do the same.                      |
| A context produced by a function call or a `for … return`                                         | Not detected, as for jobs. Its references stay placeholders, so nothing leaks.      |

## 7. Records and export

AGREED (D16, D32, D42).

Two value types, as `JOB` and `JOB_BATCH`:

|  Value type   |                               Command                               |    Event    |
|---------------|---------------------------------------------------------------------|-------------|
| `PROBE`       | `CREATE`                                                            | `CREATED`   |
| `PROBE`       | `YIELD`                                                             | `YIELDED`   |
| `PROBE`       | `ANSWER`                                                            | `ANSWERED`  |
| `PROBE`       | `RESPOND` (internal: appended by `ANSWER`, answers the client, D74) | (none)      |
| `PROBE`       | `TIME_OUT`                                                          | `TIMED_OUT` |
| `PROBE`       | (none: the engine ends the probe without an answer)                 | `CANCELED`  |
| `PROBE_BATCH` | `ACTIVATE`                                                          | `ACTIVATED` |

|                     Record                      |                                                                    Contains                                                                    |
|-------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `PROBE:CREATE` (command)                        | `probeId`, tenant, input expression as text, `requestTimeout`, and the absolute `deadline` set by the gateway (D49).                           |
| `PROBE:CREATED` (event)                         | `probeId`, tenant, evaluated input with placeholders, references (secret names and JSON pointers), deadline.                                   |
| `PROBE_BATCH:ACTIVATE` (command)                | `probeIds`, worker, `maxProbesToActivate`, tenants.                                                                                            |
| `PROBE_BATCH:ACTIVATED` (event)                 | The collected probes with worker and input with placeholders, as `JOB_BATCH:ACTIVATED`. The values go into the response only.                  |
| `PROBE:YIELD`, `YIELDED`                        | The probe key.                                                                                                                                 |
| `PROBE:ANSWER` (command)                        | The worker's full answer, including `data`. Skipped by the exporters (D42).                                                                    |
| `PROBE:ANSWERED` (event)                        | Outcome, worker, `message` when the outcome is `ERROR`. Never `data` (D42).                                                                    |
| `PROBE:RESPOND` (internal command)              | Outcome, worker, `message` for `ERROR`, and `data`: the client's answer. Written by the engine after `ANSWER`; skipped by the exporters (D74). |
| `PROBE:TIMED_OUT` (event)                       | The reason (section 6.3).                                                                                                                      |
| `PROBE:CANCELED` (event)                        | The reason.                                                                                                                                    |
| `SECRET_REFERENCE:RESOLUTION_REQUESTED` (event) | An existing record: `probeKeys`, the probes parked on the reference, next to `jobKeys` (D75).                                                  |

Column families, all partition-local: `PROBES`, `PROBES_ACTIVATABLE` and `PROBE_DEADLINES`; for a probe
parked on secrets, `SECRET_REFERENCES_BY_PROBE` and `PROBES_BY_SECRET_REFERENCE` (D75).

No record ever contains a secret value that the cluster injected. A worker keeps injected values
out of its answer (D72).

Exporters:

|         Exporter          |                                                                                                                                                                                                                 Handling                                                                                                                                                                                                                  |
|---------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Elasticsearch, OpenSearch | A `PROBE` and a `PROBE_BATCH` template, both off by default (D56). The input is stored but not indexed (`"enabled": false`), as job variables.                                                                                                                                                                                                                                                                                            |
| Camunda, RDBMS            | No index and no view. Audit log only, through a `ProbeAuditLogTransformer` in `zeebe/exporter-common`: one entry at `CREATED`, one at the end (`ANSWERED`, `TIMED_OUT` or `CANCELED`), description = `probeId`, new audit log entity type `PROBE`, mapping and default actors in D70. This adds `PROBE` to the public audit log enums (`audit-logs.yaml`, the Java client's `AuditLogEntityTypeEnum`) and the audit views must render it. |
| Third-party               | The raw records, as for jobs.                                                                                                                                                                                                                                                                                                                                                                                                             |

Refused requests (D29): a refused `CREATE` gets no audit log entry. The rejection record stays in
the record stream.

Remaining risk: a user types a raw secret into a literal field. The value is then in `CREATED`;
the engine cannot detect it. Mitigation (Phase 2 doc): sensitive fields in Hub and the Modelers
accept only the secret picker.

## 8. Authorization

AGREED (D14, D46, D67 to D69). Open point: OQ30.

New resource type `PROBE`:

| Permission |  Who   |       Resource ID       |
|------------|--------|-------------------------|
| `CREATE`   | Client | `probeId` (D46), or `*` |
| `ANSWER`   | Worker | Same                    |

At `CREATE`, the engine checks, in the order of D66 (section 6.5):

1. `PROBE:CREATE` on the `probeId`, and the tenant.
2. `SECRET:READ` on each referenced secret, direct or reached through a cluster variable. This is
   the guard for secrets, because the worker gets values without a secret permission (D12). Kept
   as agreed at the kickoff ("READ will be enough") and confirmed with Security; no reveal permission
   (D67).
3. No check per cluster variable, as the expression endpoint (`ExpressionEvaluateProcessor` checks
   only `EXPRESSION:EVALUATE`): `PROBE:CREATE` covers them (D68).

At poll and at `ANSWER`, the engine checks with the poller's claims, as for jobs
(`BpmnJobActivationBehavior.isAuthorized`):

1. `PROBE:ANSWER` on the `probeId`, and the tenant. At poll, a probe that the poller may not answer
   is not collected. At `ANSWER`, a missing permission gets 403 and another tenant gets 404.
2. No secret permission (D12).

`ANSWER` means "receive the secrets used with probes of this `probeId`". Grant it only to the
runtimes that serve that `probeId`. Because `probeId` is a free string (D46), any worker with
`ANSWER` on `*` receives every probe's secrets.

Default roles: Connectors `PROBE:ANSWER` on `*` (D69). Which roles get `PROBE:CREATE` is OQ30; the
proposal is users who manage connections `PROBE:CREATE` on `*` and `SECRET:READ`, and Admin all.

No permission per operation: the engine does not interpret the `probeId` (D46). Wildcards are only `*`, so there is no grant per operation prefix. A `probeId` can never be `*` (D54).

The `PROBE` resource type comes from `camunda-security-library`, which must be released first
(section 12). The Identity UI lists `PROBE` with `CREATE` and `ANSWER` in its authorization screens (D58).

## 9. Secrets

AGREED (D12, D39). The engine follows the central secret resolution ADR
(`docs/adr/secrets/001-central-secret-resolution-architecture.md`), as for jobs:

1. At `CREATE`, the engine detects the references and evaluates the input (6.5, D28). The probe
   record carries the references and their JSON pointers, never a value.
2. When a poll collects the probe, the engine reads each reference from the store's cache only, as
   `JobBatchCollector` does for jobs.
   - All references cached: the engine injects the values into the activation response only. The
     log and the state keep the placeholders.
   - A reference missing: the poll skips the probe, the engine parks it and requests its resolution
     (`SECRET_REFERENCE:RESOLUTION_REQUESTED`). The store answers with the command
     `RESOLUTION_COMPLETE` or `RESOLUTION_FAIL`; when the event `RESOLUTION_COMPLETED` is processed,
     the probe becomes `ACTIVATABLE` again and the engine announces it, as for waiting jobs.
3. A resolution failure (event `RESOLUTION_FAILED`) creates no incident, because there is no process. The engine ends the probe
   and answers `400 SECRET_RESOLUTION_ERROR` with the job incident's message without the incident
   part (D13). It names the placeholder only, never the value.
4. The deadline covers the time spent parked. A probe still parked at its deadline gets
   `504 SECRET_RESOLUTION_TIMEOUT`.

Consequences:

- A worker receives the values without any secret permission. The `CREATE` check on the client is
  the guard (section 8).
- A cold cache costs a few hundred milliseconds: the resolution, then a new poll after the
  notification.
- Staleness: a secret changed in the store is used with its old value for up to the cache TTL
  (default 20 minutes). Clients should say so in their error message.

## 10. Limits

- Answer size: D7. Too large: `413` (D53). For metadata, see D63.
- Timeout: AGREED (D18), see 10.1.
- Backpressure applies to `CREATE`, activation and `ANSWER`.
- Clients cache answers and send probes on a user action, not on each key press.

### 10.1 Timeout

AGREED (D18).

`requestTimeout`, in milliseconds, has the same name and meaning as in create-with-result:

- 0 or absent: the cluster's request timeout (default 15 s), clamped to the cap.
- Cap: 30 s. A negative value or a value above the cap gets 400. A probe stays in engine state until
  its deadline, so without a cap a client could fill the state.
- The deadline covers everything: waiting for a poll (D40), parked for secrets (D12), yields (D41),
  and the worker's work.
- The worker sees the deadline (`ActivatedProbe`, 5.1), so a handler can bound its own calls.

Each layer waits longer than the layer below, so that the client gets the engine's answer and not
`DEADLINE_EXCEEDED`:

|                                      Layer                                       |                       Waits                       |
|----------------------------------------------------------------------------------|---------------------------------------------------|
| Engine deadline                                                                  | `T`                                               |
| Gateway, for the broker's response                                               | `T + 500 ms`                                      |
| Every client of `POST /v2/probes` (Java client, Hub, Modelers), its HTTP request | At least `T + 1 s`; the API documentation says so |

The engine deadline `T` is absolute: the gateway computes it when it receives the request (D49), so
queueing and input evaluation cannot push the engine past the gateway.

### 10.2 Metrics

PROPOSED, to review in the implementation:

- Counter of probes ended, by outcome (`OK`, `UNSUPPORTED`, `ERROR`) and end reason (section 6.3).
- Histogram of the time from `CREATED` to the end.
- Gauge of pending probes per partition.
- Gauge of polls held by the gateway.
- Java client: the job worker's metrics, renamed for probes.

## 11. Future uses

Requested at the kickoff: examples that show the API carries later epics without change. Not phase
2. The `probeId` values follow the `<operation>/<template>` convention (D46).

### 11.1 Metadata retrieval: dropdown values

A Modeler user opens the "Channel" dropdown of a Slack task. The Modeler sends a probe for the
element template of that task:

```json
{
  "probeId": "fetch-field-options/io.camunda.connectors.Slack.v1",
  "input": "={field: \"channel\", search: \"eng\", values: {authentication: camunda.vars.env.slackProd, workspace: \"acme\"}}"
}
```

Answer: `{"outcome": "OK", "worker": "connectors-saas", "data": {"options": [{"value": "C0123", "label": "#eng-backend"}], "nextPageToken": "abc"}}`.

The same shape serves a schema lookup, for example the columns of a database table
(`probeId` `fetch-schema/<template>`, input `{table: "orders", values: {...}}`). The answer stays off exported records
(D42). Size limits: D53 and D63.

### 11.2 Task testing

A Modeler user tests one task before deploying: the runtime runs the connector once with test
values and returns its result, without a process instance.

```json
{
  "probeId": "test-task/io.camunda.connectors.HttpJson.v2",
  "input": "={values: {method: \"GET\", url: \"https://api.example.com/health\", authentication: camunda.vars.env.apiAuth}}"
}
```

Answer: `{"outcome": "OK", "worker": "connectors-saas", "data": {"result": {"status": 200, "body": {"ok": true}}}}`.

Unlike validation and metadata, a test can have side effects (it sends a message, writes a row).
Phase 2 probes are read-only (D57); task testing must decide again what a test needs.

## 12. Delivery

Order, because of dependencies:

1. `camunda-security-library`: the `PROBE` resource type with `CREATE` and `ANSWER`, released.
   Every step that checks a permission waits for it.
2. Protocol: `PROBE` and `PROBE_BATCH` value types, records, intents, column families, golden files.
3. Engine: processors, state, time-out scheduler, secret handling, authorization.
4. Gateway and REST: the three endpoints, `LongPollingActivateProbesHandler`, OpenAPI (section 4.3).
5. Java client: `newProbeWorker()`, `ActivatedProbe`, `ProbeAnswer`, with every option of 5.1.
6. Exporters and audit log: templates (D56), `ProbeAuditLogTransformer`, the audit enums.
7. Identity UI: the `PROBE` resource in the authorization screens.
8. Later, in `camunda/connectors`: the connector runtime (5.2, D58).

The POC's tests (`ProbeTest`, `ProbeBatchActivateAuthorizationTest`, `ProbeSecretTest`,
`ProbeSecretTimeoutTest`, `LongPollingActivateProbesHandlerTest`, `ProbeWorkerTest`, `ProbeIT`) are
the starting point for the acceptance tests. The acceptance tests (AT1 to AT24) and the success metrics are in
section 13.

## 13. Acceptance tests

Copied from the Probe specification tab of the Phase 2 doc on 2026-11-12, so that `ATn` references
resolve here.

|  ID  |                                                     Test                                                      |                                                                               Expected result                                                                                |
|------|---------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| AT1  | Validate a saved connection with a valid credential.                                                          | HTTP 200, outcome OK, data.status SUCCESS.                                                                                                                                   |
| AT2  | Validate a draft with a credential that is not valid.                                                         | HTTP 200, outcome OK, data.status FAILURE, with a code.                                                                                                                      |
| AT3  | The validator throws an exception.                                                                            | HTTP 200, outcome OK, data.status FAILURE, code ERROR.                                                                                                                       |
| AT4  | No worker polls for the probe ID.                                                                             | HTTP 404 NO_WORKER at the deadline.                                                                                                                                          |
| AT5  | The input has a secret reference in a list.                                                                   | HTTP 400 INVALID_ARGUMENT. No probe is created.                                                                                                                              |
| AT6  | The client has no SECRET:READ on a referenced secret.                                                         | HTTP 403. No probe is created.                                                                                                                                               |
| AT7  | A referenced secret does not exist in the store.                                                              | HTTP 400 SECRET_RESOLUTION_ERROR, with the name of the secret.                                                                                                               |
| AT8  | The worker collects the probe and never answers.                                                              | HTTP 504 WORKER_DID_NOT_ANSWER at the deadline. A late answer gets 404.                                                                                                      |
| AT9  | The only runtime has no PROBE:ANSWER.                                                                         | The runtime collects nothing. HTTP 404 NO_WORKER at the deadline.                                                                                                            |
| AT10 | The worker closes its connection before the activation response arrives.                                      | The gateway yields the probe. The next poll collects it.                                                                                                                     |
| AT11 | A connection with secrets.                                                                                    | No secret value in the log, the exported records or the response.                                                                                                            |
| AT12 | Audit and export.                                                                                             | One audit entry at creation and one at the end. PROBE records in every exporter whose probe index is on (Elasticsearch and OpenSearch: off by default). No data in ANSWERED. |
| AT13 | Operate, Tasklist and the search API.                                                                         | No validation shows.                                                                                                                                                         |
| AT14 | Two physical tenants.                                                                                         | A probe goes only to the workers of its physical tenant.                                                                                                                     |
| AT15 | A job worker and a probe worker in the same runtime.                                                          | A job worker never gets a probe. A probe never starts a job handler. A probe does not wake the job pollers.                                                                  |
| AT16 | A hybrid runtime with its own probe ID.                                                                       | A probe with this probe ID goes to the hybrid runtime. A result in less than 2 seconds for 95% of validations.                                                               |
| AT17 | A worker on a cluster without probes.                                                                         | The worker retries with exponential backoff. It does not stop.                                                                                                               |
| AT18 | One worker polls two probe IDs in one request.                                                                | It collects the probes of both. Each probe goes to the handler of its probe ID.                                                                                              |
| AT19 | A probe ID that is blank, `*`, longer than 256 characters or with whitespace. A poll with 0 or 101 probe IDs. | HTTP 400 INVALID_ARGUMENT.                                                                                                                                                   |
| AT20 | The answer does not fit in one log batch.                                                                     | The worker gets 413. The client gets HTTP 200 with outcome ERROR at once.                                                                                                    |
| AT21 | Sending the answer fails once with 503.                                                                       | The worker retries. The client gets the answer.                                                                                                                              |
| AT22 | The engine processes CREATE after its deadline.                                                               | CREATED, then TIMED_OUT. HTTP 404 NO_WORKER. Two audit entries.                                                                                                              |
| AT23 | An answer with outcome ERROR and data, or OK without data.                                                    | The worker gets 400 INVALID_ARGUMENT.                                                                                                                                        |
| AT24 | A CREATE without PROBE:CREATE and with an input that is not valid.                                            | HTTP 403. The engine does not evaluate the input.                                                                                                                            |

Success metrics:

- 95% of validations give a result in less than 2 seconds, on SaaS, Self-Managed and hybrid.
- The security test finds 0 secret values in the log, the exported records and the client responses.
- Each accepted validation has an audit entry.
- We have less connectors in failure due to misconfiguration

## Open questions

Each is discussed before it gets a decision number.

IDs in the Probe specification tab of the Phase 2 doc: Q2 = OQ30. The other
questions of the tab are decided (see Questions discussed).

|  ID  |                  Question                  |                                                 What we found                                                  |                    Recommendation                    |            Owner             | Status |
|------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------|------------------------------|--------|
| OQ30 | Which roles get `PROBE:CREATE` by default? | Kickoff Q2. Security, 2026-11-04: Connectors gets `PROBE:ANSWER` (D69); Security comes back on `PROBE:CREATE`. | Users who manage connections, and Admin (section 8). | Security, Product management | OPEN   |

## Questions discussed

|  ID  |                                      Question                                       |                                                                                                            Answer and reasoning                                                                                                             |      Status       |
|------|-------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| OQ1  | Is `operation` a closed enum or an open string?                                     | Closed enum. Reversed at the kickoff: there is no operation at all (D37).                                                                                                                                                                   | SUPERSEDED by D37 |
| OQ2  | What is the answer shape?                                                           | Two levels: `outcome` and `data`. For validation, `data` is `ConfigurationValidationResult`.                                                                                                                                                | AGREED (D9)       |
| OQ3  | Is `template` required for every operation?                                         | Required. Replaced at the kickoff by the job type (D36), then by `probeId` (D46).                                                                                                                                                           | SUPERSEDED by D46 |
| OQ4  | Push only, or also polling? Which transport?                                        | Push over gRPC, then also SSE (D35). Reversed at the kickoff: no new gRPC API, no SSE; long polling over REST, as jobs (D38).                                                                                                               | SUPERSEDED by D38 |
| OQ5  | What happens when the push fails?                                                   | `YIELD` and one re-dispatch. Replaced by the job model: the gateway yields what it could not deliver, and the next poll collects it (D41).                                                                                                  | SUPERSEDED by D41 |
| OQ6  | Does the engine inject secret values, or does the worker resolve them?              | Engine injects, cache-first, as for jobs; now at collection (D39).                                                                                                                                                                          | AGREED (D12)      |
| OQ7  | Fixed timeout or set by the client?                                                 | Set by the client with `requestTimeout`, 0 = cluster default, cap 30 s.                                                                                                                                                                     | AGREED (D18)      |
| OQ8  | Do exported records contain the evaluated input and the answer body?                | Input: yes, with placeholders. Answer `data`: never (D42).                                                                                                                                                                                  | AGREED (D16, D42) |
| OQ9  | Authorization granularity, and which secret permission for the client?              | Per `probeId` (D46). `SECRET:READ` per secret (D67).                                                                                                                                                                                        | AGREED (D14)      |
| OQ10 | Several workers match. Which one answers?                                           | The first poll, as for jobs; the answer names the worker.                                                                                                                                                                                   | AGREED (D20)      |
| OQ11 | Which partition processes a probe?                                                  | `CREATE` round-robin with retry; polls to every partition; the rest by key.                                                                                                                                                                 | AGREED (D22)      |
| OQ12 | How does the engine answer when it ends the probe for a secret?                     | `400 SECRET_RESOLUTION_ERROR`, `504 SECRET_RESOLUTION_TIMEOUT`.                                                                                                                                                                             | AGREED (D13)      |
| OQ16 | Is the timeout chain safe when the input is heavy?                                  | No under load: the gateway answered 504 before the engine. Fixed by an absolute deadline computed by the gateway.                                                                                                                           | AGREED (D49)      |
| OQ34 | Is one long poll per `probeId` acceptable for a connector runtime?                  | No: probes are rare, so almost every poll is empty, and that empty traffic grows with the number of IDs. One poll carries a list of `probeIds`.                                                                                             | AGREED (D50)      |
| OQ14 | What do the client and the worker see when a probe is too large?                    | `413 PAYLOAD_TOO_LARGE`; on an answer, the Java client falls back to a short `ERROR`.                                                                                                                                                       | AGREED (D53)      |
| OQ36 | Is a free `probeId` acceptable as a product choice?                                 | Yes, accepted on 2026-11-03. Revisit if a use case needs engine-side rules, for example task testing (OQ33).                                                                                                                                | AGREED (D46)      |
| OQ13 | How large may a metadata answer be?                                                 | Metadata answers are capped in the runtime and paged.                                                                                                                                                                                       | AGREED (D63)      |
| OQ17 | Must the client hold `SECRET:READ` on the secrets inside a saved credential?        | For metadata, read on the `SECRET_REFERENCE` cluster variable covers its secrets; validation keeps `SECRET:READ`.                                                                                                                           | AGREED (D64)      |
| OQ20 | How does a client put user-entered values into the input?                           | One helper in the client writes user values as FEEL string literals.                                                                                                                                                                        | AGREED (D65)      |
| OQ23 | Is the answer envelope a polymorphic union?                                         | Yes: a union of three shapes with a discriminator on `outcome`.                                                                                                                                                                             | AGREED (D59)      |
| OQ24 | Is 504 acceptable?                                                                  | Keep 504 and document it.                                                                                                                                                                                                                   | AGREED (D62)      |
| OQ32 | Should the input list its references explicitly?                                    | No explicit lists; FEEL detection, as input mappings.                                                                                                                                                                                       | AGREED (D60)      |
| OQ33 | Can a probe have side effects (task testing, 11.2)?                                 | Phase 2: probes are read-only and may run more than once. Decide again before task testing.                                                                                                                                                 | AGREED (D57)      |
| OQ35 | Does a hybrid runtime resolve its local secrets (`{{secrets.X}}`) in a probe?       | Yes, the hybrid runtime's handler replaces `{{secrets.X}}` from its own configuration.                                                                                                                                                      | AGREED (D61)      |
| OQ15 | Are metadata results exported?                                                      | No: `ANSWERED` never carries `data`, for any probe (D42).                                                                                                                                                                                   | AGREED (D42)      |
| OQ18 | What is the `template` of a metadata probe?                                         | The element template ID, inside the `probeId` (`fetch-field-options/<template>`). The field is in the input.                                                                                                                                | SUPERSEDED by D46 |
| OQ19 | Which runtime answers when several declare the template?                            | The first poll, as for jobs. A hybrid runtime that must answer alone registers its own `probeId`. Worker selector later.                                                                                                                    | AGREED (D47)      |
| OQ21 | What does a probe worker do on a cluster without probes?                            | Kickoff Q5: exponential backoff, as the job worker, not a stop.                                                                                                                                                                             | AGREED (D43)      |
| OQ22 | Should `template` be named `templateId`?                                            | Neither: one `probeId` (D46).                                                                                                                                                                                                               | SUPERSEDED by D46 |
| OQ25 | Is 404 right for `NO_WORKER`?                                                       | Kickoff Q7: "404 is what we would expect". Decided at the deadline (D40).                                                                                                                                                                   | AGREED (D19, D40) |
| OQ26 | Is `/answer` the right action name?                                                 | Kickoff Q8: "answer makes more sense".                                                                                                                                                                                                      | AGREED (D45)      |
| OQ27 | How is the SSE stream described in the OpenAPI?                                     | No SSE (D38).                                                                                                                                                                                                                               | SUPERSEDED by D38 |
| —    | How is `NO_WORKER` detected with long polling?                                      | Discussed on 2026-11-03: a pickup timeout (fast answer, one more timer, false 404 when every worker is busy) or the deadline (one timer, the client waits). Chosen: the deadline.                                                           | AGREED (D40)      |
| —    | Which job type does a probe carry?                                                  | Discussed on 2026-11-03: the type of the worker. Then dropped: a credential is used by many connectors, so a client validating a credential on its own has no job type.                                                                     | SUPERSEDED by D46 |
| OQ28 | Which job type does Hub send to validate a credential that is not tied to one task? | No job type: the credential has its own `probeId`, `validate-configuration/<configuration ID>` (D46).                                                                                                                                       | AGREED (D46)      |
| —    | `probeId`, or a closed `kind` with a routing key per kind?                          | Discussed on 2026-11-03. `kind` keeps engine checks per use case (data shape, permissions, audit wording) but needs a new enum value or endpoint per use case. The Zeebe team proposed one opaque `probeId`; chosen. The trade-off is OQ36. | AGREED (D46)      |
| —    | One FEEL expression or a JSON object with `=` values as input?                      | Discussed on 2026-11-03: one FEEL expression, since a probe carries one payload (D11 unchanged).                                                                                                                                            | AGREED (D11)      |
| OQ29 | Does `SECRET:READ` for the client agree with jobs?                                  | Kickoff Q1: "READ will be enough". Security, 2026-11-04: `SECRET:READ` is enough, no reveal permission.                                                                                                                                     | AGREED (D67)      |
| OQ31 | Must `CREATE` check `CLUSTER_VARIABLE:READ` on each referenced variable?            | Security, 2026-11-04: no, `PROBE:CREATE` covers it, as the expression endpoint.                                                                                                                                                             | AGREED (D68)      |

