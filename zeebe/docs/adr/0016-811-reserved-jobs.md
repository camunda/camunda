# Reserved jobs: a caller-chosen token hides the listed job types from workers

**DRI**: <TBD>

**Status**: Proposed (8.11)

**Purpose**: Defines the reserved-jobs switch — which jobs of an instance it hides, what reservation
guarantees, and why its token is separate from the job lease token.

**Audience**: Zeebe engine engineers working on job processing, and AI agents reasoning about job
activation or the CPT recording path.

## Context

A recorder must author `completeJob` itself, but a subscribed worker completes a service task in
milliseconds and the step is lost. Reservation is the [recording switch](0015-811-per-instance-recording-switches.md)
that hides an instance's jobs of chosen types from every worker so the caller drives them by hand,
while the instance's other jobs run for real. This ADR covers the reservation semantics specific to
jobs; the create-time, root-inheritance, poll-index, and REST-only mechanics are shared and defined in
[ADR 0015](0015-811-per-instance-recording-switches.md).

## Decision

**D1. A `RESERVE_JOBS` instruction lists the job types to reserve and carries a
`jobReservationToken`.** A job the instance creates is reserved when its type is in `jobTypes`. A
reserved job is handed to no worker — not by poll, not by push, not even to a worker that opts into
leases — and pollers are not notified of it. It stays in the ordinary `ACTIVATABLE` state and can be
completed, failed, error-thrown, or updated only by a caller that supplies the matching token. Jobs of
any other type are not touched and go to workers as usual. Selection is by job type alone, so every
job of a listed type is reserved, in the root instance and in every instance a call activity starts.
The list and the token are stored on the root instance and inherited by the whole tree (see ADR 0015
D3); the engine compares each job's evaluated type against the root's list when it creates the job and
stamps the token on the job only when it matches. A job whose type is a FEEL expression is reserved
only when its resolved value is listed, so a type the recording cannot predict cannot be targeted.

**D2. The reservation token is its own field, separate from the job lease token.** The
[job lease token](0005-810-job-lease.md) is engine-minted, fresh per activation, and exists to fence a
stale hand-off; a reservation token is caller-chosen and lives as long as the instance. Reusing one
field for both would falsify the lease's "client never mints one" contract and let a lease-aware worker
replace the reservation and take the job. A reserved job is skipped before either lease-minting site is
reached, so a job is never leased and reserved at once. The token is required (1–128 characters) on
every `RESERVE_JOBS` instruction.

**D3. A token supplied for a job that is not reserved is rejected, not ignored.** A caller sends a
token only when it believes the job is exclusively its own; a token on an unreserved job means that
belief is already wrong — the lookup landed on a foreign or production job, or on a job whose type was
never listed — so the command must fail loud rather than silently succeed.

**D4. There is no release step; to let a job run for real, leave its type off the list.** A job is
reserved from creation or not at all, so there is nothing to hand back. The list is the recorder's
single, declarative statement of what it drives. This replaces an earlier one-way
`POST /jobs/{jobKey}/release` command that cleared a job's token after the fact.

**D5. A stub job is never reserved.** The job a [stubbed call activity](0017-811-stubbed-call-activities.md)
waits on is withheld from workers by its own kind, independently of reservation, so it carries no
reservation token and is completed without one even when its instance reserves other job types.

## Alternatives considered

- **Reserve every job the instance creates (`reserveJobs: true`), with a per-job release.** The first
  design. Rejected: a recording that drives one service task had to hide, and then release, every
  other job of the instance; the release was the only way to run a job for real and had to be authored
  per job after the instance was already running. An explicit list states the intent once, up front,
  and removes the release endpoint, client command, state transition, and CPT instruction.
- **An engine-minted token (the engine returns it).** Matches the rule that only the engine mints a job
  token, but it changes the consumer from one token per recording run to capturing a minted token per
  instance — a larger change deferred to a later review, not assumed here.
- **Key the list on element id instead of job type.** Element ids are static in the model, and would
  target a FEEL-typed job, but they collide across nested definitions (a child's task id is
  indistinguishable from the root's), so a tree-wide list keyed on element id is ambiguous. Job type is
  what a worker subscribes to, so it is the natural unit to withhold. Element-id keying is left as a
  possible later addition for the FEEL case.
- **A new withheld job state plus a marker column family.** Airtight and visible in Operate, but every
  path that makes a job activatable again (timeout, retry backoff, incident resolution) must re-check
  the marker — roughly 3–5× the work. Keeping the token on the existing job record reuses the job
  lifecycle for free.

## Consequences

- Selection is per job type, so two jobs of one type in one instance tree are both reserved; there is
  no per-occurrence split (drive the job of type `X` at one task, let a worker run it at another).
  Accepted, as for stubbed call activities.
- A reserved job cannot have its retries or timeout updated without the token, unlike a leased job
  (which an operator may update untokened); batch job-update carries no token field, so it is blocked
  for reserved jobs. Acceptable for a recording session.
- Outbound connectors are covered for free — a connector is a job whose type is the connector id, so
  listing that id reserves it like any other job. Inbound connectors cannot be scoped: they create the
  instance from outside the engine, so there is nothing to reserve.
- A recording that lists a type only a later, not-yet-reached task uses still works: the job is
  reserved when it is created, whenever that is.
- The token is an isolation label, not a secret; a reserved job is never handed to a worker. A chosen
  length cap bounds what is copied onto every reserved job record of the instance.

## Source

- Deterministic process tests — internal design doc (claude.ai artifact) (internal)
- [ADR 0005 — Job lease](0005-810-job-lease.md)
- [ADR 0015 — Per-instance recording switches](0015-811-per-instance-recording-switches.md)

