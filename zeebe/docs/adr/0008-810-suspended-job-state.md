# Suspended job state: withhold jobs of a suspended process instance from hand-out

**DRI**: Ambrose Tan

**Status**: Accepted (8.10)

**Purpose**: Defines the persisted `SUSPENDED` job state — why it exists, what writes and clears
it, and what it does and does not cover.

**Audience**: Zeebe engineers working on job processing, process instance suspension, or the
activatable index, and AI agents reasoning about job state transitions.

## Context

Suspending a process instance does not remove its jobs from the activatable index
(`JOB_ACTIVATABLE_BY_PRIORITY`). A worker can still activate such a job, execute it, and then have
its `CompleteJob` or `FailJob` rejected by the suspension gate. This wastes worker capacity,
repeats external side effects in the job handler, and forms an activate-execute-reject-time-out-retry
cycle.

Skipping suspended jobs while collecting an activation batch (the gate used for banned instances)
was measured on a backlog of 1000 suspended jobs of the polled job type:

|               Benchmark                |  backlog 0  | backlog 1000 | retained |
|----------------------------------------|-------------|--------------|----------|
| `measureEmptyActivation` run 1         | 25181 ops/s | 294 ops/s    | 1.2%     |
| `measureEmptyActivation` run 2         | 8575 ops/s  | 325 ops/s    | 3.8%     |
| `measureActivationBehindBacklog` run 1 | 522 ops/s   | 228 ops/s    | 43.7%    |
| `measureActivationBehindBacklog` run 2 | 587 ops/s   | 247 ops/s    | 42.1%    |

That is about 3 microseconds per suspended job, paid on every poll even when the poll returns
nothing, and linear in backlog size. A gate could not be used: it would cost too much for a large
backlog and would run on every poll, whether or not the poll finds work.

## Decision

**D1. `SUSPENDED` is a persisted `JobState.State`, written only by `Job.SUSPENDED` and left only
by `Job.RESUMED` or deletion.** A job in this state is removed from the activatable index, so no
scan visits it and hand-out cost returns to zero.

Transition table:

|  From state   |      Event      |            Guard            |                                    Effect                                     |
|---------------|-----------------|-----------------------------|-------------------------------------------------------------------------------|
| `ACTIVATABLE` | `Job.SUSPENDED` | job is in `ACTIVATABLE`     | state set to `SUSPENDED`, job removed from activatable index                  |
| `SUSPENDED`   | `Job.SUSPENDED` | job is in `SUSPENDED`       | no-op (idempotent)                                                            |
| other state   | `Job.SUSPENDED` | job is not in `ACTIVATABLE` | no-op                                                                         |
| `SUSPENDED`   | `Job.RESUMED`   | job is in `SUSPENDED`       | state set to `ACTIVATABLE`, job re-inserted by its type, tenant, and priority |
| other state   | `Job.RESUMED`   | job is not in `SUSPENDED`   | no-op                                                                         |
| any state     | job deletion    | —                           | job record and state removed, same as any other job                           |

Each guard returns silently instead of throwing, because an exception in an event applier bans the
process instance, or fails the partition on replay. The precondition is enforced by the guard, not
documented as a caller obligation.

**D2. The suspend and resume processors walk the element instance tree of the process instance
and write one job event per affected job.** `ProcessInstanceSuspendProcessor` appends
`Job.SUSPENDED` for every job in `ACTIVATABLE`; `ProcessInstanceResumeProcessor` appends
`Job.RESUMED` for every job in `SUSPENDED`. Both use `ProcessInstanceSuspensionJobBehavior`, an
`ArrayDeque` walk of `ElementInstanceState` (the pattern of
`ProcessInstanceMigrationMigrateProcessor`), so the cost is paid once per suspend or resume rather
than on every poll.

The walk never crosses into a called child instance: `ElementInstanceState.getChildren` is driven
by an element instance's `parentKey`, and a called child instance's root element has no such
parent link, so there is no tree edge to follow into it. This matters because the suspension
marker is keyed by the suspended instance alone — a called child instance's own commands are not
gated by it, so parking the child's jobs too would let the child un-park them on its own while the
parent is still suspended. The `processInstanceKey` filter in the behavior is a defensive check of
that invariant, not the mechanism that enforces it.

On resume, after each `Job.RESUMED` the processor calls `BpmnJobActivationBehavior.publishWork`,
the same call `JobRecurAfterBackoffProcessor` makes after its own reactivation event. A job stream
is push-only: without this call a stream worker would never learn the job is available again. A
poll-only worker gets the job-available notification from the same call.

Jobs in `ACTIVATED`, `FAILED`, and `ERROR_THROWN` are left alone. They are already absent from the
activatable index, and every path that would re-publish them — fail, recur after backoff, incident
resolution, activation time-out — is already rejected or buffered by the suspension gate.

**D3. Worker lifecycle commands need no new handling; only the exhaustive `State` switches gain a
branch.**

Command surface table:

|                           Command                            |                                                                 Behavior while a job is `SUSPENDED`                                                                 |
|--------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ActivateJobs` (poll or stream)                              | job is absent from the index; not handed out                                                                                                                        |
| `CompleteJob`, `FailJob`, `ThrowError`, `UpdateJob`, `Yield` | not reached — the suspension gate on the process instance rejects or buffers the command before it dispatches to a job processor                                    |
| `JobFail` (immediate retry)                                  | rejected by the gate                                                                                                                                                |
| `JobRecurAfterBackoff`                                       | buffered by the gate; `JobRecurAfterBackoffProcessor`'s exhaustive switch gains a `SUSPENDED` branch naming the suspension, unreachable while the marker is present |
| `JobTimeOut`                                                 | rejected by the gate; `JobTimeOutProcessor`'s exhaustive switch gains the same kind of branch                                                                       |
| `CancelJob`                                                  | deletes the job, same as any other state                                                                                                                            |
| process instance termination                                 | deletes the job, same as any other state                                                                                                                            |

`BpmnJobBehavior.CANCELABLE_STATES` gains `SUSPENDED`, so terminating a suspended instance still
deletes its parked jobs instead of leaving orphaned records. The two switch branches exist only
because the switches are exhaustive; both processors are already gated before they would run, so
the branch is a correctness net for if the gate order ever changes, not a live path today.

**D4. `Job.SUSPENDED` and `Job.RESUMED` are exported but not consumed.** Both exporters filter by
an allow-list (`JobHandler.JOB_EVENTS`, `JobExportHandler.EXPORTABLE_INTENTS`) that does not
include these intents, so they reach no secondary storage. Suspension of a job stays visible at
process instance level only.

## Alternatives considered

- **Gate at hand-out time.** Skip suspended jobs while collecting an activation batch, the
  behavior already used for banned instances. Rejected on the measured cost above: paid on every
  poll, even an empty one, and linear in backlog size — 10k suspended jobs would cost about 30 ms
  per poll on the single-threaded stream processor, blocking every other command on the partition.
- **Parked column family (eager).** Move a job's record to a dedicated column family at suspend
  time and back at resume time. Removes the job from the activatable index like this design does,
  but adds a new column family and a second location for job records to be kept consistent with,
  for no benefit over reusing the existing `State` enum.
- **Parked column family (lazy).** Same column family, but move the record only when a poll
  actually encounters it. Keeps the hand-out-time cost this design was built to avoid, since the
  move happens during activation rather than at suspend time.
- **Group the activatable index by process instance.** Would let a suspend or resume find its jobs
  by index prefix instead of walking the element instance tree. Rejected because it changes the
  activatable index layout and activation order for a benefit — a different lookup path for a
  rare operation — that does not offset the risk to the hot activation path.
- **Lazy eviction.** Leave the job in the index and reject or discard it at hand-out time if its
  instance is suspended. Still pays a per-job cost on every poll, the same problem as gating at
  hand-out time.

## Consequences

- Suspend writes one event per parked job in the same command's batch as `ProcessInstance.SUSPENDED`.
  An instance with very many activatable jobs can exceed the maximum record batch size and have the
  command rejected. This is the same known limitation as process instance migration, which also
  writes one event per migrated entity. Chunking the suspend batch is a follow-up if this is ever
  hit in practice.
- There is no downgrade path once a job has been parked. A broker that has written `SUSPENDED` for
  at least one job cannot be downgraded to a version that does not know the state: the state is
  encoded by name, and an older broker fails on `Enum.valueOf`. This follows the general 8.10 state
  rule.
- `JobState.State` now carries parked-ness alongside the worker lifecycle it used to represent
  alone. Every future exhaustive switch over it has to decide what `SUSPENDED` means for that
  switch, as the two branches in D3 already had to.
- Extending suspension to called child instances, exporting the new state to secondary storage or
  Operate, and chunking a very large suspend batch are all out of scope for this change.

## Source

- [#58088](https://github.com/camunda/camunda/issues/58088) — suppress job handout for jobs of
  suspended process instances. Parent: [#57507](https://github.com/camunda/camunda/issues/57507).
- [Suspend and Resume – suspended job behaviour](https://docs.google.com/document/d/1MmNZ9zd0xMVeGShu_6F4imgjvb7Ys3VhFaTXLiSOJVQ/edit#heading=h.rrfjz4st7vvf),
  point 5 (selected option).
- `docs/superpowers/specs/2026-08-06-suspended-job-state-design.md` — the design document this
  ADR is drawn from.
- [#59338](https://github.com/camunda/camunda/pull/59338) — `WAITING_FOR_SECRET_RESOLUTION`, the
  pattern reference for a new persisted job state plus the appliers that write it.

