# Fair-share flow control across Physical Tenants

> **Superseded.** The controller-based design below was simplified: the next step is a spike of a
> dedicated actor thread pool per Physical Tenant, relying on the OS scheduler for fairness. See
> [spikes/physical-tenant-actor-pools.md](spikes/physical-tenant-actor-pools.md). This document is
> kept as background for the escalation options.
>
> Status: **Draft requirements**, input for the Define phase of
> [product-hub#3778](https://github.com/camunda/product-hub/issues/3778) (Reduce Noisy Neighbour
> Risks for Physical Tenants). Nothing here is implemented yet.

## Context

Physical Tenants (PTs) share one broker JVM. Each PT owns its own partition group, so every
partition of every PT has its own, fully independent `FlowControl` instance
(`zeebe/logstreams/.../flowcontrol/FlowControl.java`):

- a **request limiter** (Netflix concurrency-limits, `StabilizingAIMDLimit` by default) bounding
  user commands that are appended but not yet processed, adapting on processing latency;
- an optional **write-rate limiter** (Guava token bucket, records/s), optionally throttled by the
  exporter backlog.

All PTs on a broker run on one `ActorScheduler` (2 CPU + 2 IO threads by default, work-stealing
across all actors). The resources PTs actually contend on (actor threads, RocksDB, disk, heap/GC)
are **broker-local**.

### Problem

1. **No isolation.** One PT's spike consumes the shared actor threads; a slow task (e.g. a FEEL
   evaluation) blocks a thread for every PT.
2. **The adaptive limiter punishes the victim.** A neighbour's spike raises *my* processing
   latency, so *my* AIMD backs off. The noisy PT is not singled out.
3. **Static per-PT write-rate limits are not work-conserving.** If PT A is idle, PT B stays capped
   at its static limit while cores sit unused. Spare capacity, which is the common case, is wasted.
4. **Records/s is the wrong currency.** The maximum records/s of a node is unknown and depends on
   record type, payload size and hardware, so no static records/s limit can express "a fair share
   of the node".

## Goals

- **G1 Containment:** under contention, a spiking PT degrades its own latency/throughput, not its
  neighbours'.
- **G2 Work conservation:** capacity unused by idle PTs is available to busy PTs, without manual
  re-tuning.
- **G3 Weighted shares:** operators can give PTs different shares of a node under contention.
- **G4 No capacity tuning:** the mechanism must not require knowing the maximum records/s of a
  node; it adapts to record mix, payload size and hardware.
- **G5 Attribution:** per-PT resource consumption is observable, as a prerequisite for operating
  quotas.

## Non-goals

- **Hard isolation / SLA.** Heap, GC, RocksDB and disk remain shared inside one JVM. A hard bound
  needs a process boundary (dedicated brokers per PT); that is out of scope here.
- **Cluster-wide shares.** Shares are enforced per broker node. Uneven leader placement across
  nodes is addressed separately (e.g. load-aware leader balancing).
- **Reservations (guaranteed minimum throughput).** Only weights and optional ceilings.
- **Fair sharing of disk I/O and RocksDB** in the first iteration (see open questions).

## Terminology

|     Term     |                                          Meaning                                           |
|--------------|--------------------------------------------------------------------------------------------|
| Weight `wᵢ`  | Operator-configured, unitless share of a node's CPU that PT `i` gets **under contention**. |
| Ceiling      | Optional operator-configured hard cap on a PT's write rate (records/s), as today.          |
| Limit `Lᵢ`   | The **computed** write-rate limit (records/s) applied to PT `i`, recalculated every tick.  |
| Capacity `C` | CPU (cores) available to PT pools on the node.                                             |
| Cost `costᵢ` | Measured CPU time per written record for PT `i` (smoothed).                                |
| Saturated    | The node cannot serve all demand; PTs are competing for CPU.                               |

Weights and limits are deliberately separate: the operator sets weights, the controller derives
limits from them.

## Approach overview

Two layers, each handling what it is good at:

1. **Per-PT actor thread pools** (isolation, CPU sharing). Each PT gets its own small actor thread
   pool (target: 2 threads per PT, i.e. one stream processor + one exporter per led partition in
   the common case). Pools are **oversubscribed** (Σ threads > cores): idle pools park, and the OS
   scheduler hands the cores to runnable threads. This is work-conserving fair sharing for free,
   and it makes each PT's existing AIMD limiter observe only its own latency.
2. **A broker-level fair-share controller** (weights). Because actors are single-threaded, pool
   size cannot express a weight (a PT can never use more threads than it has runnable actors). The
   controller enforces weights by computing per-PT write-rate limits from **measured CPU time**,
   reusing the existing `FlowControl.setWriteRateLimit` runtime lever.

Using **CPU time as the currency** resolves G4: the capacity of a node in records/s is unknown, but
its capacity in CPU-seconds per second is known (≈ number of cores). Converting a CPU share to a
record rate uses each PT's own measured cost per record, which automatically reflects its record
type mix and payload sizes.

## Requirements

### Thread isolation

- **R1** Each PT's partition actors (stream processor, exporter director, and other per-partition
  actors to be determined) run on a thread pool dedicated to that PT.
- **R2** The pool size per PT is configurable, with a default of 2 CPU threads.
- **R3** Idle PT pools must not consume meaningful CPU (park after a bounded spin/yield).
- **R4** Actors shared across PTs (broker-wide services) keep running on the shared scheduler.
- **R5** Per-PT thread CPU time is measurable with low overhead (≤ once per second sampling of
  `ThreadMXBean#getThreadCpuTime` or equivalent).

### Fair-share controller

- **R6** One controller per broker evaluates all PTs whose partitions the broker leads, on a fixed
  tick (default 1 s, configurable).
- **R7** Per tick and per PT it observes: CPU used `cpuᵢ` (cores), records written `rateᵢ`
  (already measured by `FlowControl`), and write-rate rejections `rejectedᵢ` (already exposed via
  `zeebe.flow.control{outcome}`), and derives `costᵢ = cpuᵢ / rateᵢ` as a moving average.
- **R8** Capacity `C` is the CPU available to the process (respecting cgroup quotas) minus the CPU
  used by non-PT threads.
- **R9** Saturation is detected when either `Σ cpuᵢ ≥ threshold · C` (default 0.9) or the actor
  scheduling latency (`zeebe.actor.job.scheduling.latency`) of any PT pool exceeds a configured
  threshold.
- **R10 Saturated:** compute entitlements `eᵢ` by weighted water-filling (weighted max-min
  fairness):
  - initial share `eᵢ = wᵢ / Σw · C`;
  - PTs using less than their share keep their usage; the remainder is redistributed among the
    others by weight, until stable.

  For each PT with `cpuᵢ > eᵢ`, set `Lᵢ = eᵢ / costᵢ`.

- **R11 Not saturated:** distribute headroom `H = threshold · C − Σ cpuᵢ` among **limited** PTs
  (`rejectedᵢ > 0`) by weight: `Lᵢ += H · wᵢ / Σw_limited / costᵢ`, with a bounded step per tick
  (e.g. ≤ +25%).

- **R12** A PT with no rejections for N consecutive ticks returns to its ceiling (or unlimited).

- **R13** `Lᵢ` never exceeds the PT's configured ceiling.

- **R14** A PT's limit is split across its partitions on the node in proportion to each
  partition's recent write rate.

- **R15** CPU used by a PT's exporters counts against that PT.

- **R16** Whitelisted commands (`WhiteListedCommands`) and `Internal` writes stay exempt, as today.
  Processing follow-ups remain delayed (retried), never dropped.

- **R17** Stability: smoothing of `costᵢ` and `cpuᵢ`, bounded step changes, and hysteresis on the
  saturated/not-saturated transition, so limits do not oscillate.

- **R18** The controller can be disabled; when disabled, behaviour is identical to today.

### Configuration and operability

- **R19** Weight and ceiling are configurable per PT via the existing
  `camunda.physical-tenants.<id>.processing.flow-control.*` override mechanism (exact keys to be
  defined; proposal: `...flow-control.fair-share.weight`, default 1).
- **R20** Weights are updatable at runtime through the existing `flowControl` actuator
  (`?physicalTenant=`) without restart.
- **R21** Rejections caused by the controller surface as today (`RESOURCE_EXHAUSTED` / HTTP 503),
  so the gateway retry behaviour and client contracts are unchanged.

### Observability

- **R22** Metrics per PT (tagged with the PT id): CPU used, entitlement, computed limit, cost per
  record, rejections caused by the controller.
- **R23** Metrics per broker: capacity `C`, saturation state, controller tick duration.
- **R24** Broker actor metrics are tagged with the PT, so partitions with the same number in
  different PTs are not merged (prerequisite for attribution).

## Worked example

4 cores, weights A=1, B=1, C=2. A uses 0.5 cores; B and C want everything.

- Initial shares: 1 / 1 / 2 cores. A only uses 0.5, so the spare 0.5 is split 1:2 between B and C.
- Entitlements: **A 0.5, B 1.17, C 2.33 cores.**
- With `cost_B = 0.1 ms/record`, `L_B ≈ 11.7k records/s`; with `cost_C = 0.5 ms/record`,
  `L_C ≈ 4.7k records/s`. Same CPU share, very different record rates.
- When A goes idle, the next non-saturated ticks hand its 0.5 cores to B and C (1:2).

## Validation

Validated with the Phase 1 interference benchmark of product-hub#3778:

- **Arms:** (A) shared scheduler, today's behaviour; (B) per-PT pools only; (C) per-PT pools +
  fair-share controller.
- **Scenarios:** one saturating PT + steady neighbours; idle neighbours (work conservation); mixed
  record costs (large payloads, heavy FEEL); weights 1:2.
- **Acceptance:**
  - Neighbour p99 latency under a saturating PT stays within an agreed bound of its baseline
    (bound defined by Phase 1).
  - Under contention, measured CPU split matches weights within ±10%.
  - With neighbours idle, a busy PT reaches ≥ 90% of single-tenant throughput on the same node.
  - No limit oscillation beyond an agreed amplitude in steady state.

## Open questions

1. **Shared actors.** Which per-partition actors besides the stream processor and exporter must
   move to the PT pool (Raft, journal flush, snapshot, job streaming)? Which remain on shared IO
   threads, and does that leave a significant unattributed CPU share?
2. **Disk and RocksDB.** CPU fairness does not bound disk I/O, compaction or memory. Is a second
   currency needed (e.g. bytes appended, dominant resource fairness), or is the exporter-backlog
   throttle plus a PT-scoped disk pause enough?
3. **Thread count at scale.** 2 threads × 50 PTs adds ~100 threads (on top of today's ~100–150).
   Validate context-switch and wake-up latency overhead, aligned with the scalability epic
   (product-hub#3797).
4. **Lever choice.** The write-rate limit also delays processing follow-ups. Is limiting at the
   request limiter (user commands only) a better lever for some workloads?
5. **Leader imbalance.** A PT leading more partitions on a node has more runnable actors and thus
   more natural OS share before the controller kicks in. Acceptable, or should the controller cap
   it from the first tick?

## References

- A. Gulati, A. Merchant, P. Varman. *mClock: Handling Throughput Variability for Hypervisor IO
  Scheduling.* OSDI 2010. Reservation/limit/shares under time-varying capacity.
- J. Mace et al. *2DFQ: Two-Dimensional Fair Queuing for Multi-Tenant Cloud Services.* SIGCOMM
  2016. Fair queueing with unknown, highly variable request costs.
- A. Demers, S. Keshav, S. Shenker. *Analysis and Simulation of a Fair Queueing Algorithm.*
  SIGCOMM 1989. Work-conserving weighted fair queueing.
- D. Chiu, R. Jain. *Analysis of the Increase and Decrease Algorithms for Congestion Avoidance.*
  1989. AIMD convergence to fair shares without capacity knowledge.
- A. Ghodsi et al. *Dominant Resource Fairness.* NSDI 2011. Multi-resource fairness.
- Netflix concurrency-limits, `AbstractPartitionedLimiter`: a global adaptive limit split by
  percentage, with bursting into unused share.
- [Backpressure](backpressure.md): current request limiter behaviour.

