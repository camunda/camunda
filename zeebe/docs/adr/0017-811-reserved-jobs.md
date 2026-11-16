# Reserved jobs: a caller-chosen token hides the listed job types from workers

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the reserved-jobs switch — which jobs of an instance it hides, what reservation
guarantees, and why its token is separate from the job lease token.

**Audience**: Zeebe engine engineers working on job processing, and AI agents reasoning about job
activation or the CPT recording path.

## Context

A recorder must author `completeJob` itself, but a subscribed worker completes a service task in
milliseconds and the step is lost. Reservation is the
[recording switch](0016-811-per-instance-recording-switches.md) that hides an instance's jobs of chosen
types from ordinary workers while its other jobs run for real. The shared create-time,
root-inheritance and REST-only mechanics are in ADR 0016.

## Decision

**D1. `RESERVE_JOBS` lists the job types to reserve and carries a `reservationToken`.** When the
instance tree creates a job whose evaluated type is listed, the engine stamps the token on it. A
reserved job stays in the ordinary `ACTIVATABLE` state but is pushed to no stream and served to no poll
— lease-aware ones included — except one presenting the matching token, such as CPT's mock worker
replaying the recording. Every command on it (complete, fail, throw error, update) must carry the
token. Jobs of unlisted types go to workers as usual, so to let a job run for real, leave its type off
the list; there is no release step. A FEEL-typed job is reserved only when its resolved type is listed.

**D2. The reservation token is its own field, separate from the job lease token.** The
[job lease token](0005-810-job-lease.md) is engine-minted, fresh per activation, and fences a stale
hand-off; a reservation token is caller-chosen (1–128 characters) and lives as long as the instance.
Sharing one field would break the lease's "client never mints one" contract and let a lease-aware
worker replace the reservation. A reserved job is skipped before any lease is minted, so a job is never
leased and reserved at once.

**D3. A token supplied for a job that is not reserved is rejected, not ignored.** A caller sends a
token only when it believes the job is its own; a token on an unreserved job means it targeted a
foreign or production job, or a type it never listed, so the command fails loudly.

**D4. A stub job is never reserved.** A [stubbed call activity](0018-811-stubbed-call-activities.md)'s
job is withheld by its own kind, so it carries no token even when its instance reserves other job
types or lists the stub job type.

## Alternatives considered

- **Reserve every job of the instance (`reserveJobs: true`) with a per-job release command.** The first
  design. Rejected: a recording driving one task had to release every other job after the instance was
  running. An up-front list states the intent once and needs no release endpoint, command, or state
  transition.
- **An engine-minted token returned at creation.** Matches "only the engine mints job tokens", but
  turns one token per recording run into one captured token per instance. Deferred.
- **Key the list on element id instead of job type.** Would target FEEL-typed jobs, but element ids
  collide across nested definitions; job type is what a worker subscribes to. May be added later for
  the FEEL case.
- **A new withheld job state plus a marker column family.** Visible in Operate, but every path that
  makes a job activatable again (timeout, retry backoff, incident resolution) must re-check the marker.
  The token on the job record reuses the job lifecycle for free.

## Consequences

- Every job of a listed type in the instance tree is reserved; there is no per-occurrence split.
- A reserved job's retries or timeout cannot be updated without the token, unlike a leased job, and
  batch job-update carries no token, so it is blocked for reserved jobs.
- Outbound connectors are covered (their job type is the connector id); inbound connectors create the
  instance from outside, so there is nothing to reserve.
- The token is an isolation label, not a secret. Its length cap bounds what is copied onto every
  reserved job record.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0005 — Job lease](0005-810-job-lease.md)
- [ADR 0016 — Per-instance recording switches](0016-811-per-instance-recording-switches.md)

