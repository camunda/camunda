# Suspend/Resume SaaS load test

Plan for stress-testing the process-instance suspend/resume feature
([PR #61602](https://github.com/camunda/camunda/pull/61602),
[issue #59933](https://github.com/camunda/camunda/issues/59933)) on a real cluster.

## Why this exists (what JMH did not cover)

PR #61602 added a JMH microbenchmark suite for suspend/resume. Those benchmarks run a
**single-node, in-memory engine** — no gateway, no network, no exporters, no
Elasticsearch/RDBMS, no cluster, no sustained concurrent traffic. They characterized the
per-operation engine cost (and found e.g. `SuspendJobs` is O(n²) in job fan-out, and the
SUSPEND record hits a 4 MB / ~4 000-job hard cap).

This load test proves the **cluster-level** properties JMH structurally cannot:

1. **No throughput/latency regression** — adding suspend/resume traffic (and the new
   `Job.SUSPENDED/RESUMED`, `BufferedCommandRecord`, and suspension-state records) must not
   degrade normal process-instance throughput or latency.
2. **Resume drain under real load** — buffered commands accumulate while an instance is
   suspended and real traffic keeps arriving; resume must drain them correctly and quickly.
   JMH drained on an otherwise-idle engine.
3. **Exporter / storage impact** — the new record stream and the growth of
   `DbSuspensionState` (RocksDB) must not overload exporters/ES/RDBMS or bloat state over
   time.
4. **Batch-operation suspend at scale** — `processInstanceSuspend` batch operations over
   thousands of running instances must behave (throughput, backpressure, graceful
   batch-limit rejection) on a real cluster.

## Chosen test: blast-radius / interference

The concrete scenario we run measures **how much suspending/resuming one heavy process
definition impacts unrelated running processes** on the same cluster. Two workloads run
concurrently:

- **Victim — `typical_process` @ ~50 PI/s (the `typical` scenario), never suspended.** Workers
  complete its jobs normally. This is the workload we *measure*: its throughput and latency
  must not dip while the target is suspended/resumed.
- **Heavy target — a purpose-built process definition** with a large fan-out: **~500 active
  jobs + ~500 open message subscriptions per instance**, held concurrently. A small number of
  instances (default **1**) is created and then **suspended and resumed on a cadence** (default
  **30 s suspend interval / 30 s hold**). Only this definition is toggled.

**Signal:** compare the victim's throughput/latency with target suspension **on vs off** (A/B).
Any dip that lines up with the target's suspend/resume events is the blast radius.

### The heavy target process

- **~500 active jobs:** a parallel multi-instance service task (500 elements) whose job type
  **no worker services**, so the jobs stay activatable (never completed) and are all "running"
  at suspend time.
- **~500 message subscriptions:** a parallel multi-instance receive task (500 elements) with
  distinct correlation keys that are **never correlated**, so 500 subscriptions stay open.
- **~200 timers:** a parallel multi-instance sub-process (200 elements), each holding a `PT1H`
  timer catch that never fires during the run, so 200 timers stay scheduled (timers are suspended
  and resumed too, so this exercises that path).
- A parallel gateway activates all three multi-instance branches at once, so a single instance
  holds ~500 jobs + ~500 subscriptions + ~200 timers simultaneously.
- The instance is created once and **reused** across suspend/resume cycles: resume un-parks the
  jobs (still unhandled → they stay) and reopens the subscriptions.

### Why these numbers / what to watch

- **500 jobs + 500 subs + 200 timers = ~1200 combined** stays under the ~2000-combined 4 MB
  batch-record limit (`SuspensionBatchLimitTest`), so suspends never get rejected — we get a clean
  interference signal rather than probing the limit.
- Suspending 500 jobs is **O(n²)** (a few hundred ms), and it lands on the **single partition**
  that owns the target instance. Victim instances spread across all 3 partitions, so the
  interference should appear as a **per-partition latency spike** on the target's partition,
  not cluster-wide. Watch per-partition processing latency, not just the aggregate.
- With 1 target instance only one partition is stressed (cleanest isolation). Raising the
  target count spreads the hit across more partitions (placement is not guaranteed).

### Design change required (to be built after this plan is approved)

The current single-starter design runs one process definition. This test needs **two
concurrent workloads**, so `SuspensionMeter` is extended with a **target mode** (implemented):
it deploys the heavy target BPMN (`bpmn/suspend_target.bpmn`), creates `target-instances` heavy
PIs at startup, then suspends/resumes them by key each cycle — all while the starter runs the
untouched `typical` victim load. The first cycle starts after `batch-interval` (warm-up for the
fan-out to materialise); `hold-duration` is the hold and `batch-interval` the idle gap. New
config knobs (`load-tester.suspender.*`): `target-enabled`, `target-bpmn-path`,
`target-process-id`, `target-instances` (default 1), `job-count` (500), `subscription-count`
(500), `timer-count` (200), plus the existing `hold-duration`/`batch-interval`.

## Scope decisions

| Decision | Choice | Rationale |
|---|---|---|
| Environment | GKE benchmark cluster (`camunda-benchmark-prod`) | Full Grafana/Prometheus/GCS tooling + `SuspensionMetrics` already wired; prod-representative topology (3-node OC, 3 partitions, RF 3). Literal SaaS DEV/INT is an optional later smoke run, not the measurement vehicle. |
| Driver API | Both single-instance and batch-operation, as **two scenarios** | They stress different subsystems: single-instance = hot-path + drain interplay; batch = batch executor + backpressure. Both are real SaaS usage. |
| Injection | `SuspensionMeter` running **inside the starter** (like the read-benchmark meter), gated by `load-tester.suspender.enabled` | A separate `@Profile` deployment would need a new image, a new Helm template, and an external `camunda-load-tests-helm` chart release before it could run in SaaS. Folding it into the starter needs none of that: the starter image is already built from the PR branch, and `load-tester.suspender.*` config already flows to the starter via the chart's `global.extraConfig.load-tester.*` passthrough. Tradeoff: suspend/resume shares the starter pod and cannot be scaled independently — acceptable for a moderate A/B run, and it is client-side command issuance so it does not taint the broker-side signal. |
| PI shape | Typical low-fan-out (existing BPMN) | Realistic steady state; measures regression + drain without hitting the O(n²)/4 MB edge. **The O(n²)/4 MB-cap edge is deliberately left to JMH** — a coverage split, not an oversight. |
| Hold + drain | Long hold under active traffic | Suspend for tens of seconds–minutes while traffic keeps targeting the instance, so buffered commands accumulate, then resume and measure drain. This is the JMH blind spot. |
| Magnitude | ~50 PI/s base (`typical`), 10–20 % of live PIs suspended on a rolling basis | Realistic customer-like ratio; enough drain volume to measure without saturating the cluster and masking the regression signal. Tune from the first run. |
| Baseline | A/B: identical workload **with vs without** the suspender | The only way to attribute a throughput/latency/exporter delta to suspend/resume rather than to baseline drift. |
| Cadence | Ad-hoc multi-hour A/B pairs now | This is a **functional** extension (README taxonomy); a permanent daily-stress variant is only warranted if suspend/resume becomes a heavily-used / critical path. |

### Note on buffered-command source for low-fan-out PIs

A suspended instance has its jobs **parked**, so workers do not pull those jobs — job
completions are therefore *not* the main source of buffered commands for a low-fan-out PI.
The pileup instead comes from **message correlations and timers** firing against the
suspended instance. The drain scenario must therefore use a BPMN model with message/timer
catch events (a `typical_process` variant with a message intermediate catch), not a pure
job chain, or there will be nothing to buffer.

## The suspension meter

`SuspensionMeter` is a scheduled meter started inside the **starter** when
`load-tester.suspender.enabled=true` (mirroring `DataReadMeter` — see
`Starter.setupSuspensionMeter`). No new image, Helm template, or chart release is required;
the starter deployment already exists and already receives `load-tester.*` config. It reuses
the v2 process-instance search machinery already used by the data-availability meter
(`Starter.setupDataAvailabilityMeter`) and read benchmark. It targets the same process the
starter creates (`load-tester.starter.process-id`).

**Client API used** (`io.camunda.client.CamundaClient`):

- Find candidates: `newProcessInstanceSearchRequest().filter(f -> f.processDefinitionId(id).state(ACTIVE).suspendedDate(d -> d.exists(false)))`
- Single mode: `newSuspendProcessInstanceCommand(key)` / `newResumeProcessInstanceCommand(key)`
- Batch mode: `newCreateBatchOperationCommand().processInstanceSuspend().filter(...)` and `.processInstanceResume()`

### Modes

- **`single`** — on a schedule (rate-controlled like the starter), query up to *N* active,
  not-yet-suspended benchmark instances; suspend each; record the key + a resume deadline;
  a second scheduled task resumes instances whose hold has elapsed. An in-flight set guards
  against double-suspending the same instance.
- **`batch`** — on an interval, issue one `processInstanceSuspend` batch operation over a
  filter matching a bounded page of active instances; after the hold, issue the matching
  `processInstanceResume` batch operation (or resume by the recorded batch-operation key's
  item set). Exercises the batch executor and its batch-record-size rejection path.

### Config knobs (`load-tester.suspender.*`, env `LOAD_TESTER_SUSPENDER_*`)

| Property | Default | Meaning |
|---|---|---|
| `enabled` | `false` | Master switch (off = the A/B baseline arm). |
| `mode` | `SINGLE` | `SINGLE` or `BATCH`. |
| `rate` / `rate-duration` | `10` / `1s` | Suspend attempts per interval (single mode). |
| `batch-interval` | `10s` | Interval between batch operations (batch mode). |
| `batch-page-size` | `1000` | Max instances per batch operation. |
| `hold-duration` | `30s` | How long an instance stays suspended before resume. |
| `sample-size` | `100` | Candidates fetched per suspend cycle (single mode). |

The target process id is taken from `load-tester.starter.process-id` (the instances the
starter creates), not configured separately.

### Metrics

Load-tester side (client-observed): counters for suspend/resume requests issued and errors,
and request-latency timers, exposed on `/metrics` like the starter/worker meters.

Broker side (already exists — graph these): `SuspensionMetrics` —
`suspended`, `resumed`, `jobSuspended`, `jobResumed`, `commandBuffered`, `commandDrained`,
`commandDropped`, and the per-PI **resume-duration** timer
(`zeebe/engine/.../metrics/SuspensionMetrics.java`).

## Pass/fail (A/B)

Run the identical workload twice — arm A `suspender.enabled=false`, arm B `enabled=true` —
and compare:

- **Throughput / latency**: arm B process-instance completion throughput and p99 latency
  within noise of arm A (no regression).
- **Drain correctness**: `commandDropped == 0`; `commandBuffered` ≈ `commandDrained` over
  the run (nothing stuck).
- **Resume latency**: per-PI resume-duration p99 within an agreed bound; does not grow
  unbounded with hold time.
- **Exporter / storage**: exporter lag and ES/RDBMS write rate in arm B not materially
  worse than arm A; `DbSuspensionState` / RocksDB size returns to baseline after resume
  (no leak).
- **Backpressure**: gateway backpressure (RESOURCE_EXHAUSTED) rate in arm B not materially
  worse than arm A.

## How to run (SaaS, no chart release, off the PR branch)

The meter rides the existing starter, so the whole test runs from the PR branch with no
merge and no `camunda-load-tests-helm` release. `load-tester.suspender.*` is delivered
through the chart's `global.extraConfig.load-tester.*` passthrough.

```bash
# Arm A — baseline (suspender off; enabled=false is the default)
gh workflow run camunda-load-test.yml \
  --ref 59933-suspend-resume-load-testing \
  -f name=susp-a-baseline \
  -f ref=59933-suspend-resume-load-testing \
  -f scenario=typical

# Arm B — suspend/resume enabled (SINGLE driver)
gh workflow run camunda-load-test.yml \
  --ref 59933-suspend-resume-load-testing \
  -f name=susp-b-single \
  -f ref=59933-suspend-resume-load-testing \
  -f scenario=typical \
  -f load-test-load="--set global.extraConfig.load-tester.suspender.enabled=true --set global.extraConfig.load-tester.suspender.mode=SINGLE --set global.extraConfig.load-tester.suspender.hold-duration=30s"
```

BATCH driver: `--set global.extraConfig.load-tester.suspender.mode=BATCH --set global.extraConfig.load-tester.suspender.batch-interval=10s`.

Run both arms for the same (multi-hour) duration on comparable cluster state, then compare on
the Camunda Performance + Zeebe Grafana dashboards and the `SuspensionMetrics` panels; archive
results.

### Blast-radius / interference run (the chosen test)

Victim = the `typical` scenario (50 PI/s, untouched). Arm B additionally enables **target mode**,
which deploys the heavy target, seeds it, and suspends/resumes only it (30 s hold, 30 s gap):

```bash
# Arm A — baseline: typical load only, no target suspension
gh workflow run camunda-load-test.yml --ref 59933-suspend-resume-load-testing \
  -f name=blast-a-baseline -f ref=59933-suspend-resume-load-testing -f scenario=typical

# Arm B — same typical load + heavy target suspended/resumed
gh workflow run camunda-load-test.yml --ref 59933-suspend-resume-load-testing \
  -f name=blast-b-target -f ref=59933-suspend-resume-load-testing -f scenario=typical \
  -f load-test-load="--set global.extraConfig.load-tester.suspender.enabled=true --set global.extraConfig.load-tester.suspender.target-enabled=true --set global.extraConfig.load-tester.suspender.hold-duration=30s --set global.extraConfig.load-tester.suspender.batch-interval=30s"
```

Compare the **victim** throughput/latency (starter metrics) between arms, focused on the
**partition that owns the target instance** — that is where any interference shows.

## Deliverables / follow-ups

- [x] `SuspensionMeter` + `SuspenderProperties` + starter wiring + `application.yaml` (this PR).
- [x] Target mode + heavy target BPMN (`bpmn/suspend_target.bpmn`) for the blast-radius test.
- [ ] Smoke-deploy `bpmn/suspend_target.bpmn` on a cluster (or locally) to confirm it deploys and
      reaches the ~500 job + ~500 subscription fan-out — the model is hand-authored and has only
      been build-checked, not deploy-validated against a live engine.
- [ ] Optional: promote to a recurring daily-stress variant if suspend/resume becomes a
      relied-upon path.

The separate-deployment path (dedicated image + external `camunda-load-tests-helm` template +
chart release) was intentionally dropped in favour of the in-starter meter so the test runs in
SaaS with no cross-repo release. It could be revisited only if suspend/resume load must scale
independently of PI creation.
