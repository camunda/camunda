# Reserved jobs: a caller-chosen token hides an instance's jobs from workers

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the reserved-jobs switch — what reservation guarantees, why its token is separate
from the job lease token, and how a reserved job is released back to the workers.

**Audience**: Zeebe engine engineers working on job processing, and AI agents reasoning about job
activation or the CPT recording path.

## Context

A recorder must author `completeJob` itself, but a subscribed worker completes a service task in
milliseconds and the step is lost. Reservation is the [recording switch](0015-811-per-instance-recording-switches.md)
that hides an instance's jobs from every worker so the caller drives them by hand. This ADR covers the
reservation semantics specific to jobs; the create-time, root-inheritance, poll-index, and REST-only
mechanics are shared and defined in [ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. `reserveJobs` plus a `jobReservationToken` reserves every job the instance creates.** A reserved
job is handed to no worker — not by poll, not by push, not even to a worker that opts into leases — and
pollers are not notified of it. It stays in the ordinary `ACTIVATABLE` state and can be completed,
failed, or error-thrown only by a caller that supplies the matching token.

**D2. The reservation token is its own field, separate from the job lease token.** The
[job lease token](0005-810-job-lease.md) is engine-minted, fresh per activation, and exists to fence a
stale hand-off; a reservation token is caller-chosen and lives as long as the instance. Reusing one
field for both would falsify the lease's "client never mints one" contract and let a lease-aware worker
replace the reservation and take the job. A reserved job is skipped before either lease-minting site is
reached, so a job is never leased and reserved at once.

**D3. A token supplied for a job that is not reserved is rejected, not ignored.** A caller sends a
token only when it believes the job is exclusively its own; a token on an unreserved job means that
belief is already wrong — the lookup landed on a foreign or production job — so the command must fail
loud rather than silently succeed.

**D4. Release is a one-way command that hands the job back to the workers.** `POST /jobs/{jobKey}/release`
clears the token and makes the job available to the worker that would have served it; it is not a
completion — the job stays `ACTIVATABLE`. It is fenced by the same token. Afterwards the token is gone,
so a timeout or retry backoff keeps the job released and the caller must stop sending the token for it.

## Alternatives considered

- **An engine-minted token (`reserveJobs: true`, engine returns the token).** Matches the rule that
  only the engine mints a job token, but it changes the consumer from one token per recording run to
  capturing a minted token per instance — a larger change deferred to a later review, not assumed here.
- **A new withheld job state plus a marker column family.** Airtight and visible in Operate, but every
  path that makes a job activatable again (timeout, retry backoff, incident resolution) must re-check
  the marker — roughly 3–5× the work. Keeping the token on the existing job record reuses the job
  lifecycle for free.

## Consequences

- A reserved job cannot have its retries or timeout updated without the token, unlike a leased job
  (which an operator may update untokened); batch job-update carries no token field, so it is blocked
  for reserved jobs. Acceptable for a recording session.
- Outbound connectors are covered for free — a connector is a job whose type is the connector id, so
  reservation hides it like any other job. Inbound connectors cannot be scoped: they create the
  instance from outside the engine, so there is nothing to reserve.
- The token is an isolation label, not a secret; a reserved job is never handed to a worker. A chosen
  length cap bounds what is copied onto every job record of a reserved instance.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0005 — Job lease](0005-810-job-lease.md)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)
