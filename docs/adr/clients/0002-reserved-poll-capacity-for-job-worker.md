# Reserve job-worker poll capacity while pushed jobs starve the poll

**DRI**: Alexandre Janoni

**Status**: Accepted (8.11)

**Deciders**
- Alexandre Janoni
- Meggle (megglos)

**Purpose**: Defines how a streaming job worker in the Java client keeps capacity for its poll path when pushed jobs take all its slots.

**Audience**: Engineers who work on the Java client job worker (`clients/java/.../impl/worker/`). Engineers who analyze job push and job poll under load.

## Context

A streaming job worker gets jobs on two paths. The push path gets jobs from the job stream. The poll path gets jobs with `ActivateJobs` requests. Both paths use one capacity count of `maxJobsActive` slots (#59632). The poll path is the recovery path. Only the poll path gets the jobs that push does not deliver: jobs created while no stream had capacity, jobs that the gateway yields back after a blocked push, and jobs that time out.

The two paths do not compete equally. A pushed job waits on a thread in the worker, and this thread takes a free slot in microseconds. A poll must first send a request and wait for the response. During this round-trip, pushed jobs take the free slots. When the response arrives, the worker has no slot for the polled jobs. The worker refuses these jobs and fails them back to the broker. Issue #59734 asked for numbers that show if this starves the poll path.

A cluster A/B test measured the effect. One streaming worker had `maxJobsActive=30` and a handler that took 1 s. The load was 150 jobs/s, and the worker completed approximately 29 jobs/s. Thus the worker stayed at full capacity, with an open stream and a backlog. Without the lane, the worker refused almost all polled jobs, and approximately 0 polled jobs/s ran. With the lane, approximately 7 polled jobs/s ran. The poll share of delivery increased from 5.7% to 13.3%. Timed-out jobs decreased from 29.9/s to 22.7/s. Throughput did not change measurably. Thus the problem is the liveness of the poll path, not throughput.

## Decision

**D1. A streaming worker can reserve a poll lane. The lane limits only the push path.**
While the lane is reserved, push can use only `maxJobsActive − reserved` slots. The poll can use all slots, also the reserved slots. The single capacity count from #59632 does not change. Poll request size, `freeCapacity()` and `hasNoJobsInFlight()` continue to use it. A change of the lane does not stop jobs that run. If push has more jobs than its new limit, new pushed jobs wait until enough pushed jobs complete.

**D2. Refused polled jobs reserve the lane. Three polls in a row without a refusal, an empty poll, or a failed poll release it.**
A refusal shows that pushed jobs took the slots of the poll during the round-trip. This is the starvation from #59734. A poll that returns jobs is not a signal by itself. While the lane is reserved, the gateway cannot push some jobs, and the broker yields them back. The next poll gets these jobs. Thus this signal can keep the lane reserved also when push can do the work alone. A failed poll releases the lane, because no poll can use the reserved slots while polls fail.

**D3. The lane size is `floor(maxJobsActive × 0.25)`. The fraction is an internal constant.**
`floor` reserves no slot until the worker can spare a full slot: 0 slots below 4, 1 slot at 4, and 7 slots at 30. Users cannot change the fraction. #64977 tracks a user setting.

**D4. Only streaming workers get a lane.**
A poll-only worker has no push path that can starve its poll. It reserves 0 slots, and its behavior does not change.

## Alternatives considered

- **Fair semaphore (`new Semaphore(n, true)`).** #59734 first proposed this. A fair semaphore gives permits to waiting threads in order. But the poll does not wait on the semaphore. It tries to get a permit only after the round-trip. At that time, the waiting push thread already has the permit. A microbenchmark that models the round-trip showed no improvement.
- **A lane that is always reserved.** This was the first version of this ADR. It limits push also when the poll does not need the slots. Thus it decreases the capacity of streaming workers that are not saturated.
- **Take capacity when the poll request is sent.** The poll takes its slots when it sends the request, and releases the unused slots when the response arrives. This removes the race directly. But the poll keeps the slots for the full long poll, up to the request timeout. When push delivers most jobs, most polls return empty. Thus push loses slots that no job uses. Also, `freeCapacity()` and `hasNoJobsInFlight()` would count reservations, not jobs in flight. This breaks the single count from #59632.
- **Won't-fix.** #59734 accepted this option. Push delivers most jobs, and throughput does not change. But the lane gives the recovery path its slots back at no measurable throughput cost.

## Consequences

- **The poll path runs jobs under push load.** Polled jobs that run increase from approximately 0/s to 7/s. Timed-out jobs decrease by approximately 24%.
- **Throughput does not change measurably.** In one 20-minute window for each arm, throughput was 28.9/s without the lane and 28.0/s with the lane. An earlier A/B with a lane that was always reserved showed the opposite difference (28.5/s and 28.9/s). Thus this ADR does not read either difference as real.
- **Refusals increase.** Each reservation costs the refused jobs of one poll. The worker fails each refused job back to the broker with its retries unchanged. This costs one more command and one WARN log for each job. The broker activates the job again later. In the A/B, refusals increased from 8.1/s to 13.0/s.
- **A worker below saturation rarely gets refusals.** Near saturation, push can take the free slots during a poll round-trip, and the worker refuses the jobs of that poll. Then the lane is reserved for a short time, and then released. The lane cannot keep itself reserved, because a reserved lane stops the refusals. Jobs that the broker gives only to pollers (timed-out jobs and yielded jobs) do not reserve the lane when they get a free slot.
- **The lane starts one poll late.** The lane is reserved only after the worker refuses the jobs of a poll. Thus the first poll with contention loses its jobs to push. Also, refusals do not show all starvation. When push takes all slots before the next poll is due, the worker sends no poll, and no refusal occurs. The lane then waits for the next poll that the worker sends with free capacity.
- **The lane can stay reserved for up to one request timeout after the contention stops.** A poll that finds jobs returns quickly. A long poll that finds no jobs returns empty only after the request timeout (`requestTimeout`, 10 s by default). During this time, push can use only `maxJobsActive − reserved` slots.
- **Users cannot turn off the lane.** The only way to remove the lane is to turn off streaming. #64977 tracks a setting for the fraction, where 0 turns off the lane.

## Source

- [camunda/camunda#59734](https://github.com/camunda/camunda/issues/59734): non-fair shared capacity semaphore
- [camunda/camunda#59635](https://github.com/camunda/camunda/issues/59635): umbrella issue for the job worker intake defects
- [camunda/camunda#59632](https://github.com/camunda/camunda/issues/59632) and [PR #63915](https://github.com/camunda/camunda/pull/63915): unified worker capacity count
- [PR #64095](https://github.com/camunda/camunda/pull/64095): implementation and cluster A/B
- [camunda/camunda#64977](https://github.com/camunda/camunda/issues/64977): user setting for the lane fraction
- Implementation: `BlockingExecutor` (push limit), `JobWorkerImpl` (lane control), `JobWorkerBuilderImpl` (lane size)
- Microbenchmarks: `CapacityLaneReservationBenchmark` (shared capacity against a reserved lane, with the poll round-trip modeled) and `CapacitySemaphoreFairnessBenchmark` (fair against non-fair semaphore)

