# Reserve a minimum share of job-worker capacity for the polling path

**DRI**: Alexandre Janoni

**Status**: Proposed

**Deciders**
- Alexandre Janoni
- Zeebe job-worker / client domain owner (to confirm the direction)

**Purpose**: Decide whether the Java client's job worker should reserve a
minimum slice of its shared activation capacity for the polling (ActivateJobs)
path so it cannot be starved by the streaming (push) path, and record the
numbers that back the decision as issue #59734 demanded.

**Audience**: Engineers working on the Java client job worker
(`clients/java/.../worker/`), and anyone reasoning about streaming vs polling
job delivery under load.

## Context

A streaming job worker delivers jobs through two paths that share a single
capacity budget:

- **Push (streaming).** Jobs are pushed to the client as they become
  activatable. In `BlockingExecutor.execute` a caller thread parks in a blocking
  `tryAcquire(timeout)` and is unparked in microseconds the instant a permit is
  released.
- **Poll (ActivateJobs).** The worker issues an `ActivateJobs` request only when
  it observes free capacity (`onCapacityAvailable`), pays a network round-trip,
  and then takes an untimed `tryAcquire()`. Polling is the recovery path: it is
  the only mechanism that drains jobs the push path missed (created while no
  stream had capacity, yielded back after a blocked push, or recovered after a
  lease timeout).

Both paths draw from one `Semaphore(maxJobsActive)` (unified under #59632).
Issue #59734 observed the semaphore is non-fair and asked — **with numbers, and
sanctioning a won't-fix** — whether that starves one path.

Under sustained load the two paths are asymmetric: when a permit frees, the
locally-parked push thread wins it in microseconds, while the poll must first
pay a network round-trip before it can even attempt the acquire. So push wins
nearly every contested slot and poll — the recovery path — is starved.

We validated this on a cluster A/B: a single streaming worker
(`maxJobsActive=30`), oversubscribed (~150 jobs/s created vs ~29/s drained) so
the worker is pinned at capacity with a live push stream and a standing
activatable backlog. Two arms differed **only** by the reserved-lane code.
Broker `zeebe_job_events_total` gives the delivery split (`pushed` vs
`activated`=poll) at matched offered load (~365 s window):

|     metric (per s)     | SHARED (baseline) | RESERVED (this ADR) |
|------------------------|------------------:|--------------------:|
| created                |             150.1 |               149.7 |
| completed (throughput) |              28.5 |                28.9 |
| push-delivered         |             140.7 |               118.7 |
| poll-delivered         |              11.8 |                13.1 |
| **poll share**         |          **7.7%** |           **10.0%** |
| timed out              |              30.1 |                22.3 |

A second, load-asymmetric window corroborated the direction (poll share 5.9% vs
11.0%). The `BLOCKED`/`YIELD` counters were large throughout, confirming push is
frequently unable to deliver and the poll path genuinely matters.

**Reading:** poll starvation is real and measurable — on the shared semaphore
the poll path wins only ~6–8% of delivered jobs under push contention. It is
*not* a throughput cliff: push carries ~90% of delivery and total throughput is
identical in both arms. The harm is to poll-path liveness — the fairness of the
recovery/backlog-drain mechanism — not to throughput.

## Decision

Add an **optional reserved poll lane** to `BlockingExecutor`, wired on the
streaming path only.

- `total = Semaphore(maxJobsActive)` remains the single capacity authority.
  `freeCapacity()`, poll request sizing, and `hasNoJobsInFlight()` are unchanged,
  so the unification from #59632 is preserved — the reserved lane is a sub-limit
  on one path, not a second tally.
- The push path additionally acquires a `pushBudget = Semaphore(maxJobsActive −
  reserved)` permit before `total`; both are released together in the same
  `finally`. The poll path acquires only `total`, so it can still reach every
  slot including the reserved ones.
- Reservation size = `floor(maxJobsActive × 0.25)`. `floor` reserves nothing
  until a whole slot can be spared (`N<4→0, N=4→1, N=30→7`), which avoids halving
  push at small `maxJobsActive` (a `round`-with-minimum-1 policy broke
  `shouldNotAskForJobsWhileItCannotRunAnyMore` at N=2). The 0.25 fraction is a
  tunable knob, not a load-bearing constant.
- The non-streaming (poll-only) path passes `reserved = 0` and is byte-for-byte
  unchanged.

## Consequences

- **Poll-path liveness is protected.** Poll's share of delivery rises from
  ~6–8% to ~10–11%, guaranteeing the recovery path reaches its reserved slots
  even under sustained push contention.
- **No throughput cost.** At matched load, completed/s is identical (28.5 vs
  28.9) and timeouts are no worse (fewer, in the matched-load window). Reserving
  slots for poll does not reduce total delivery in the oversubscribed regime.
- **Streaming-only, opt-in by construction.** Only the streaming builder path
  sets a non-zero reservation; existing poll-only workers are unaffected.
- **A purely-pushed streaming worker keeps polling for its reserved slots.** It
  no longer fills entirely from push — this is the backlog-drain mechanism doing
  its job, at the cost of a few more `ActivateJobs` requests.
- **Moderate severity, honest framing.** Because push masks most of the impact,
  a won't-fix remains defensible; this ADR lands the fix as a low-risk liveness
  improvement rather than a throughput fix, and leaves the final call to the
  domain owner — now backed by numbers.

## Alternatives considered

- **Fair semaphore (`new Semaphore(n, true)`), as the issue first proposed.**
  Rejected: fairness orders *waiters*, but the poll path does not wait on the
  semaphore — it only attempts an untimed acquire after a network round-trip, by
  which time the parked push thread has already taken the freed permit. A fair
  semaphore therefore cannot arbitrate poll-vs-push here, and a microbenchmark
  confirmed it does not help (and can hurt) once the poll's round-trip is
  modelled.
- **Won't-fix.** Defensible on severity (push carries throughput; no cliff).
  Not chosen because the reserved lane is throughput-neutral and cheaply
  restores recovery-path liveness, but recorded here as the reasonable
  alternative the issue explicitly sanctioned.

## References

- Issue: camunda/camunda#59734 (non-fair shared capacity semaphore)
- Prerequisite: camunda/camunda#59632 / PR #63915 (unified worker capacity
  accounting)
- Implementation: `clients/java/src/main/java/io/camunda/client/impl/worker/BlockingExecutor.java`,
  `JobWorkerBuilderImpl.java`
- Validation benchmarks:
  `microbenchmarks/.../worker/CapacityLaneReservationBenchmark.java` (RTT-aware
  A/B) and `CapacitySemaphoreFairnessBenchmark.java` (fair-semaphore control)

