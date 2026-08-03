# Job state for jobs waiting for secret resolution

**DRI**: Berkay Can

**Status**: Proposed (8.10)

**Purpose**: Defines the persisted job state that marks a job as parked while its secret references
are resolved in the background: which transitions write it, which commands it accepts, and what it
means for state compatibility.

**Audience**: Zeebe engineers working on job activation, the secret reference resolution flow, or
job state, and AI agents reasoning about why a job is not handed out to a worker.

## Context

A job whose secret references are not in the broker's secret cache is not handed out on activation.
The engine emits `SecretReference.RESOLUTION_REQUESTED`, resolves the references in the background,
and hands the job out once they are resolved (see [#57846](https://github.com/camunda/camunda/issues/57846)
and [#57852](https://github.com/camunda/camunda/issues/57852)). Until then the job is parked.

Parking was implemented by removing the job from the activatable index (`JOB_ACTIVATABLE_BY_PRIORITY`)
while leaving its job state at `ACTIVATABLE`. The waiting condition therefore existed only as the
absence of an index entry, which has three consequences:

- `DbJobState.updateJobPriority` re-inserts a job into the activatable index when its state is
  `ACTIVATABLE`, so an `UpdateJob` command that changes the priority of a parked job un-parks it.
  The job is then handed out with unresolved placeholders in its variables.
- The reactivation path (`MutableJobState.makeActivatableAfterSecretResolution`) can only document
  its precondition ("call this on a parked job") in Javadoc. Nothing in the state can be checked, so
  a caller that reactivates an already-reactivated job silently upserts an index entry instead of
  being recognised as a no-op.
- An operator debugging an incident cannot tell a parked job from an activatable one, because both
  report `ACTIVATABLE`.

The secret reference state already keeps a waiting-jobs index (`(storeId, secretReference) -> jobKey`),
but it does not answer "is this job parked". Its entry is removed when a permanent resolution failure
raises an incident for the job, while the job stays parked until the incident is resolved.

## Decision

Add a constant to the persisted `JobState.State` enum:

```java
WAITING_FOR_SECRET_RESOLUTION((byte) 5)
```

### Transitions

|              From               |               To                |                                       Written by                                       |
|---------------------------------|---------------------------------|----------------------------------------------------------------------------------------|
| `ACTIVATABLE`                   | `WAITING_FOR_SECRET_RESOLUTION` | `SecretReferenceResolutionRequestedApplier` (parking, together with the index removal) |
| `WAITING_FOR_SECRET_RESOLUTION` | `ACTIVATABLE`                   | `SecretReferenceBatchJobsReactivatedApplier` (all references resolved)                 |
| `WAITING_FOR_SECRET_RESOLUTION` | `ACTIVATABLE`                   | `IncidentResolvedV4Applier` (a `SECRET_RESOLUTION_ERROR` incident is resolved)         |
| `WAITING_FOR_SECRET_RESOLUTION` | (job deleted)                   | `JobCanceledV4Applier`, when the element instance or process instance is terminated    |

Parking is the only writer of the new state, and reactivation is its only exit besides deletion. A
job is never activated, failed, or completed out of it.

### The reactivation precondition is enforced by the state, not documented

`makeActivatableAfterSecretResolution` acts only on a job in `WAITING_FOR_SECRET_RESOLUTION` and
returns silently otherwise. This replaces both the Javadoc precondition and the caller-side state
check in `IncidentResolvedV4Applier`.

The guard is a silent skip rather than an exception because a job can legitimately be reactivated
twice. A job waiting on two references A and B, both of which resolve, is reactivated by the
`BATCH_JOBS_REACTIVATED` event of A (B is no longer pending at that point, its own reactivation
command is only queued), and the event of B then calls the method again on a job that is already
`ACTIVATABLE`. An exception thrown from an event applier is unrecoverable, it fails the partition,
so a state that the guard rejects must be a no-op.

The eligibility check that keeps an incident-parked job parked stays in the reactivation applier: a
job with a `SECRET_RESOLUTION_ERROR` incident is in `WAITING_FOR_SECRET_RESOLUTION` like any other
parked job, so the state alone cannot express it.

### Command surface

|                     Command                     |                           On a parked job                           |
|-------------------------------------------------|---------------------------------------------------------------------|
| `CompleteJob`, `FailJob`, `ThrowError`, `Yield` | rejected, `INVALID_STATE`, naming the state in the rejection reason |
| `UpdateJob` (retries, priority)                 | accepted                                                            |
| `UpdateJob` (timeout)                           | rejected, the job has no deadline while parked (unchanged)          |
| `CancelJob`, process instance termination       | accepted, the job is deleted                                        |

Rejecting the worker lifecycle commands follows from leaving the new state out of the valid-state
lists of their processors. A parked job was withheld from every worker on purpose, so a command that
claims to act on an activation of it cannot be honoured.

This is a visible behaviour change for one case: a worker whose activation timed out could complete
the job late while it was `ACTIVATABLE`, and now gets `INVALID_STATE` if the job was parked again in
the meantime (its secret value expired from the cache before the next activation). The work of that
activation is lost and the job is handed out again once its references resolve, which is the same
outcome as any other lost race between a timed-out worker and a new activation.

A priority update is accepted and updates the job record only. The re-insert into the activatable
index is skipped because it is conditional on the `ACTIVATABLE` state, so the job stays parked and is
inserted with its new priority when it is reactivated.

### Persistence and compatibility

The enum is persisted in the `JOB_STATES` column family, encoded by name (`EnumValue` writes
`Enum.toString`). It never reaches the log, the protocol, exported records, or secondary storage, so
adding a constant is not a record schema change and needs no protocol version handling.

An older broker reading state written by a newer one would fail on `Enum.valueOf`, so the constant
follows the general no-downgrade rule for 8.10 state. Reading older state is unaffected: no existing
entry carries the new name.

The two pieces of applier logic that change (`SecretReferenceResolutionRequestedApplier` and the
`SECRET_RESOLUTION_ERROR` branch of `IncidentResolvedV4Applier`) are updated in place, with their
golden files regenerated, instead of being registered as new versions. Both were merged after the
`8.10.0-alpha4` candidates were branched: the parking applier is contained in no release tag at all,
and the incident branch was added to `IncidentResolvedV4Applier` (whose other branches did ship in
those candidates) after that branch point. No released build ever applied either of them, so no
cluster can have replayed them.

## Alternatives considered

- **Keep the absence of an index entry as the waiting marker.** Rejected. Absence is not specific to
  parking: every job that is not activatable (activated, failed, backing off, error thrown) is absent
  from the index too, so a caller that finds no entry learns nothing about why. Reconstructing the
  composite index key from the job record to probe it is possible but answers the wrong question,
  which is what makes the priority-update bug and the unenforceable reactivation precondition
  possible in the first place.
- **Treat the waiting-jobs index in the secret reference state as the marker.** Rejected. It answers
  "which jobs wait on this reference", not "is this job parked": the entry is removed when a
  permanent failure raises an incident, while the job stays parked. Consulting it from job state
  would also couple two states on the hot activation and update paths.
- **Reuse `FAILED`.** Rejected. `FAILED` carries retry and backoff semantics, participates in the
  backoff index and in incident resolution, and would make a parked job indistinguishable from a job
  that a worker failed.
- **Throw when the reactivation path sees an unexpected state.** Rejected, see above: the
  double-reactivation path is legitimate and an exception in an applier fails the partition.
- **Reject `UpdateJob` on a parked job as well.** Rejected. It would fix the un-parking bug by
  refusing the command instead of by state, and an operator re-prioritising queued work has no reason
  to be blocked by a pending secret resolution.

## Consequences

- `JobState.State` is no longer a pure lifecycle enum, it also carries a parked-ness that only the
  secret resolution flow produces. A second reason to park a job would either reuse this constant
  with the wrong name or add another one.
- Every future `switch` over `JobState.State` has to answer what a parked job means. The two existing
  ones (`JobTimeOutProcessor`, `JobRecurAfterBackoffProcessor`) reject with a reason that names the
  wait.
- A parked job is distinguishable from an activatable one in the engine's own state, which is what
  makes it possible to explain a job that is not handed out. Parking is still invisible in exported
  records: nothing about it reaches secondary storage.
- Downgrading a broker that has parked at least one job is not possible, in line with the general
  8.10 state compatibility rule.

## Source

- [Introduce a dedicated job state for jobs waiting for secret resolution (camunda/camunda#58993)](https://github.com/camunda/camunda/issues/58993)
- [Park jobs and request resolution on activation (camunda/camunda#57846)](https://github.com/camunda/camunda/issues/57846)
- [Reactivate jobs after secret resolution (camunda/camunda#57852)](https://github.com/camunda/camunda/issues/57852)
- [Phase 1: resolve secrets for job activation (epic camunda/camunda#56556)](https://github.com/camunda/camunda/issues/56556)
- [Review discussion that raised it (camunda/camunda#58587)](https://github.com/camunda/camunda/pull/58587#discussion_r3665621244)

