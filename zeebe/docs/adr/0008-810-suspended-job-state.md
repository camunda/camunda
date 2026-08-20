# Suspended job state: withhold jobs of a suspended process instance from hand-out

**DRI**: Ambrose Tan

**Status**: Accepted (8.10)

**Purpose**: Defines the persisted `SUSPENDED` job state — why it exists, what writes and clears it,
and what it does and does not cover.

**Audience**: Zeebe engineers working on job processing, process instance suspension, or the
activatable index, and AI agents reasoning about job state transitions.

## Context

Suspending a process instance does not remove its jobs from the activatable index
(`JOB_ACTIVATABLE_BY_PRIORITY`). A worker can still activate such a job, execute it, and then have
its `CompleteJob` or `FailJob` rejected by the suspension gate. This wastes worker capacity, repeats
external side effects in the job handler, and forms an activate-execute-reject-time-out-retry cycle.

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

**D1. `SUSPENDED` is a persisted `JobState.State`, written only by `Job.SUSPENDED` and left only by
`Job.RESUMED` or deletion.** A job in this state is removed from the activatable index, so no scan
visits it and hand-out cost returns to zero.

Transition table:

|           From state            |      Event      |                   Guard                   |                                    Effect                                     |
|---------------------------------|-----------------|-------------------------------------------|-------------------------------------------------------------------------------|
| `ACTIVATABLE`                   | `Job.SUSPENDED` | job is in `ACTIVATABLE`                   | state set to `SUSPENDED`, job removed from activatable index                  |
| `WAITING_FOR_SECRET_RESOLUTION` | `Job.SUSPENDED` | job is in `WAITING_FOR_SECRET_RESOLUTION` | state set to `SUSPENDED`; suspension overrides secret-waiting                 |
| `SUSPENDED`                     | `Job.SUSPENDED` | job is in `SUSPENDED`                     | no-op (idempotent)                                                            |
| other state                     | `Job.SUSPENDED` | job is not suspendable                    | no-op                                                                         |
| `SUSPENDED`                     | `Job.RESUMED`   | job is in `SUSPENDED`                     | state set to `ACTIVATABLE`, job re-inserted by its type, tenant, and priority |
| other state                     | `Job.RESUMED`   | job is not in `SUSPENDED`                 | no-op                                                                         |
| any state                       | job deletion    | —                                         | job record and state removed, same as any other job                           |

Each guard returns silently instead of throwing, because an exception in an event applier bans the
process instance, or fails the partition on replay. The precondition is enforced by the guard, not
documented as a caller obligation.

**D2. Suspend and resume walk the element instance tree and write one job event per affected job.**
Both use `ProcessInstanceSuspensionJobBehavior` (same `ArrayDeque` walk pattern as migration). Cost
is paid once per suspend, and once per resume cycle, not on every poll.

- **Suspend:** append `Job.SUSPENDED` for every `ACTIVATABLE` or `WAITING_FOR_SECRET_RESOLUTION` job,
  then append `ProcessInstance.SUSPENDED`. Suspending every job finishes before the instance marker
  is set, in one record batch: `Job.SUSPENDED` carries the job's own record, including its variables,
  so it is not fixed-size, but it is the only record suspend writes per job — no activation record
  alongside it. The batch's size scales with the aggregate serialized size of every job the walk
  suspends, since suspend does not chunk (see Consequences).
- **Resume:** a `RESUME_JOBS` command walks the tree, finds the first still-`SUSPENDED` job, appends
  `Job.RESUMED` for it, and calls `BpmnJobActivationBehavior.publishWork` so stream and poll workers
  see the job again, before appending the next `RESUME_JOBS` for what it did not reach. One job per
  cycle bounds each cycle's batch to that job's own hand-out cost, the same limit any other hand-out
  already has: publishing a job a stream is waiting for appends a `JobBatch.ACTIVATED` on top of
  `Job.RESUMED`, carrying the job's fetched variables a second time, so a resume cycle's size depends
  on the one job it reaches rather than on every job the instance suspended.
- **Child instances:** left untouched. `ElementInstanceState.getChildren` does not return a child
  instance root, so the walk never reaches those jobs.
- **Other job states at suspend time:** `ACTIVATED`, `FAILED`, and `ERROR_THROWN` stay as they are
  (already off the activatable index). An `ACTIVATED` job that times out while the instance is still
  `SUSPENDED` is parked by `JobTimeOutProcessor` (see D3) so it does not loop on rejected timeouts.
- **Secret-waiting:** overridden to `SUSPENDED` so a later secret resolution cannot put the job back
  into the hand-out index while the instance is suspended.

**D3. Most worker lifecycle commands need no new handling; `JobTimeOut` is the exception that parks
an already-activated job.**

Command surface table:

|                           Command                            |                                                                 Behavior while a job is `SUSPENDED` / instance is suspended                                                                  |
|--------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ActivateJobs` (poll or stream)                              | job is absent from the index; not handed out                                                                                                                                                 |
| `CompleteJob`, `FailJob`, `ThrowError`, `UpdateJob`, `Yield` | not reached — the suspension gate on the process instance rejects or buffers the command before it dispatches to a job processor                                                             |
| `JobFail` (immediate retry)                                  | rejected by the gate                                                                                                                                                                         |
| `JobRecurAfterBackoff`                                       | buffered by the gate; `JobRecurAfterBackoffProcessor`'s exhaustive switch gains a `SUSPENDED` branch naming the suspension, unreachable while the marker is present                          |
| `JobTimeOut`                                                 | processed while the instance marker is present (`SuspensionBehavior.PROCESS`); after `TIMED_OUT`, if `getSuspensionState == SUSPENDED`, append `Job.SUSPENDED` instead of notifying hand-out |
| `CancelJob`                                                  | deletes the job, same as any other state                                                                                                                                                     |
| process instance termination                                 | deletes the job, same as any other state                                                                                                                                                     |

Notes:

- `JobTimeOut` checks `getSuspensionState == SUSPENDED`, not `isSuspended`, so a job is not re-parked
  during `RESUMING`. `Job.SUSPENDED` is a follow-up event (same batch, bypasses the gate). Exporters
  still see `TIMED_OUT` only; that matches D4.
- `CANCELABLE_STATES` includes `SUSPENDED` so termination deletes parked jobs.
- The `JobRecurAfterBackoff` `SUSPENDED` switch branch is a safety net only; the gate already blocks
  that processor while suspended.

**D4. `Job.SUSPENDED` and `Job.RESUMED` are exported but not consumed.** Both exporters filter by an
allow-list (`JobHandler.JOB_EVENTS`, `JobExportHandler.EXPORTABLE_INTENTS`) that does not include
these intents, so they reach no secondary storage. Suspension of a job stays visible at process
instance level only.

## Alternatives considered

- **Gate at hand-out time.** Skip suspended jobs while collecting an activation batch, the behavior
  already used for banned instances. Rejected on the measured cost above: paid on every poll, even
  an empty one, and linear in backlog size — 10k suspended jobs would cost about 30 ms per poll on
  the single-threaded stream processor, blocking every other command on the partition.
- **Parked column family (eager).** Move a job's record to a dedicated column family at suspend time
  and back at resume time. Removes the job from the activatable index like this design does, but
  adds a new column family and a second location for job records to be kept consistent with, for no
  benefit over reusing the existing `State` enum.
- **Parked column family (lazy).** Same column family, but move the record only when a poll actually
  encounters it. Keeps the hand-out-time cost this design was built to avoid, since the move happens
  during activation rather than at suspend time.
- **Group the activatable index by process instance.** Would let a suspend or resume find its jobs
  by index prefix instead of walking the element instance tree. Rejected because it changes the
  activatable index layout and activation order for a benefit — a different lookup path for a rare
  operation — that does not offset the risk to the hot activation path.
- **A dedicated index of suspended jobs by process instance.** A new column family keyed
  `(processInstanceKey, jobKey)`, populated by `Job.SUSPENDED` and cleared by `Job.RESUMED` or job
  deletion, would let resume prefix-seek its jobs instead of re-walking the element instance tree once
  per `RESUME_JOBS` cycle. Deferred rather than rejected: it touches only a new, previously-empty
  index (no migration, since no 8.9 job can be `SUSPENDED`), not the activatable index the point above
  protects, so the risk this ADR weighs against does not transfer. Not built for the initial 8.10
  change so the walk-based design could ship without it; tracked as a concrete follow-up in
  [#60323](https://github.com/camunda/camunda/issues/60323).
- **Lazy eviction.** Leave the job in the index and reject or discard it at hand-out time if its
  instance is suspended. Still pays a per-job cost on every poll, the same problem as gating at
  hand-out time.

## Consequences

- One `Job.SUSPENDED` event per affected job in the same batch as the instance marker. A very large
  instance can still hit the max record batch size on suspend (same limit as migration); suspend does
  not chunk. Resume does not share this limit: one job per `RESUME_JOBS` cycle keeps each cycle's
  batch bounded to that job's own activation cost.
- Each `RESUME_JOBS` cycle re-walks the element instance tree from the root to find its one job,
  visiting every active element the previous cycles already resumed along the way, so a large
  instance's resume cost is quadratic in its suspended job count. Accepted for the same reason as the
  walk itself: resume is rare. Tracked for removal by
  [#60323](https://github.com/camunda/camunda/issues/60323), which replaces the re-walk with the
  dedicated index of suspended jobs by process instance (see Alternatives).
- The walk visits every active element of the instance, not only job-backed ones, and blocks the
  partition while it runs. Accepted because suspend/resume are rare; the same cost on activation
  would not be. We run on the same path as process instance migration which is fine so far.
- No downgrade once a job is parked: older brokers fail on the unknown `SUSPENDED` enum name.
- Every new exhaustive `JobState.State` switch must handle `SUSPENDED`.
- Out of scope: child-instance suspension, export to secondary storage/Operate, batch chunking for
  suspend.

## Open questions

- D4 leaves suspended jobs in their last exported state (for example `CREATED`) in secondary storage
  and `search-jobs`. Whether job-level suspension should be visible there is undecided.

## Source

- [#58088](https://github.com/camunda/camunda/issues/58088) — suppress job handout for jobs of
  suspended process instances. Parent: [#57507](https://github.com/camunda/camunda/issues/57507).
- [Suspend and Resume – suspended job behaviour](https://docs.google.com/document/d/1MmNZ9zd0xMVeGShu_6F4imgjvb7Ys3VhFaTXLiSOJVQ/edit#heading=h.rrfjz4st7vvf),
  point 5 (selected option).
- `docs/superpowers/specs/2026-08-06-suspended-job-state-design.md` — the design document this ADR
  is drawn from.
- [#59338](https://github.com/camunda/camunda/pull/59338) — `WAITING_FOR_SECRET_RESOLUTION`, the
  pattern reference for a new persisted job state plus the appliers that write it.
- [PR #59617 review thread](https://camunda.slack.com/archives/C08CKAP10DQ/p1786428784267579) —
  discussion of the element instance walk cost, log stream blocking, and API visibility that this
  ADR's Consequences and Open questions capture.

