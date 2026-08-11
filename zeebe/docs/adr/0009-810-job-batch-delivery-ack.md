# JobBatch delivery ACK: reclaim activations the gateway never received

**DRI**: Ambrose Tan

**Status**: Accepted (8.10)

**Purpose**: Recover jobs that the broker activated but whose JobBatch response never
reached the gateway, without waiting for the full worker job timeout.

**Audience**: Zeebe engineers working on job activation, the gateway, or delivery
guarantees between broker and gateway.

## Context

The gateway activates jobs with a `JobBatch ACTIVATE` command. The broker commits
`ACTIVATED`, sets each job's worker deadline, and sends the batch back. If that
response is lost (connection closed, request timeout, short leader disconnect), the
gateway has no job keys. It cannot run the existing `reactivateJobs` / `FAIL` path,
so the jobs stay `ACTIVATED` until the worker timeout (often hours). The client gets
an empty activate response.

The stream (push) path already recovers: `YieldingJobStreamErrorHandler` appends
`JobIntent.YIELD` when a push fails. The poll path has no equivalent for a lost
response. Retrying `ACTIVATE` is wrong: it would activate a second batch.

Production evidence and full analysis: [camunda/camunda#59354](https://github.com/camunda/camunda/issues/59354).

## Decision

**D1. Broker-side delivery handshake for poll activation.**
When the gateway includes a non-zero `deliveryAttemptKey` on `JobBatch ACTIVATE`, the
broker registers a short-lived **pending delivery** for the activated job keys after
`ACTIVATED`. The gateway must **ACK** that attempt when it receives the broker
response. If it does not, the broker **YIELDs** the pending jobs.

**D2. Gateway generates `deliveryAttemptKey`; transport request id is not enough.**
The attempt key is set on the command before send. After connection loss the transport
request id is gone; the attempt key remains known to the gateway so it can REJECT.

**D3. ACK means "gateway received the broker response", not "client received jobs".**
Client-send failure already uses `reactivateJobs` → `FAIL` with preserved retries.
ACK runs on broker-response success, before the client send, so it does not race the
delivery timer while the gateway is writing to the client.

**D4. Fast path REJECT + safety-net delivery deadline.**
On ambiguous response errors (`ConnectionClosed`, timeout, and similar — not
`RESOURCE_EXHAUSTED` or command rejections), the gateway sends `JobBatch REJECT` for
the attempt. Independently, a broker scheduler yields pending deliveries past
`jobDeliveryAckTimeout` (default 30s). REJECT covers the confirmed production case;
the timer covers gateway crash or lost REJECT.

**D5. Do not overload the worker job deadline.**
`JobRecord.deadline` stays the full worker timeout. Pending delivery uses separate
state and a separate checker. Delivery expiry becomes `YIELD`, never `TIME_OUT`.

**D6. Recovery primitive is `JobIntent.YIELD`.**
Same as the stream path. Does not burn retries. Job lease fencing (ADR 0005) is
unchanged: yield is engine-internal and unfenced; a worker that still holds a yielded
job is an at-least-once case the lease already covers when enabled.

**D7. Rolling upgrade: attempt key optional.**
- Missing / zero `deliveryAttemptKey` → no pending delivery (legacy gateways).
- New gateway + old broker: ACK/REJECT ignored until brokers upgrade; orphans remain
until then.
- Old gateway + new broker: no spurious yields.

**D8. Handshake is gateway↔broker only.**
No public client API change. gRPC and REST both use `ActivateJobsHandler`, so one
gateway change covers both.

## Alternatives considered

- **Recoverable JobBatch response by attempt id.** Gateway re-fetches the committed
  result. Better for same-poll recovery, but needs response cache, leader-change
  handling, and secret re-injection (secrets live only on the response copy). Deferred
  as a follow-up.
- **Short provisional worker deadline, then UpdateTimeout on ACK.** Overloads worker
  timeout semantics and races long-running workers if ACK is delayed.
- **Gateway-only REJECT without broker timer.** Fixes the logged production path but
  not gateway crash between commit and response.
- **Logging job keys only on gateway error.** Impossible without keys on the lost
  response path; broker-side logs on REJECT/timeout address operator visibility.

## Consequences

- One ACK command per successful partition activation; REJECT only on error paths.
- Worst-case stall after an orphaned activation is bounded by
  `jobDeliveryAckTimeout` (plus checker polling), not the worker job timeout.
- Lost ACK after a successful client delivery can yield a job the worker already has
  (at-least-once; lease when enabled).
- Protocol and engine state change; support backports need explicit assessment.

## Source

- [Jobs stay activated until the job timeout when a JobBatch activation response does not reach the gateway (#59354)](https://github.com/camunda/camunda/issues/59354)
- [Job lease ADR (0005)](0005-810-job-lease.md)
- Original client-send reactivation (#3631)

