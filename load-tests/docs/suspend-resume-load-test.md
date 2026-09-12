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

## Scope decisions

| Decision | Choice | Rationale |
|---|---|---|
| Environment | GKE benchmark cluster (`camunda-benchmark-prod`) | Full Grafana/Prometheus/GCS tooling + `SuspensionMetrics` already wired; prod-representative topology (3-node OC, 3 partitions, RF 3). Literal SaaS DEV/INT is an optional later smoke run, not the measurement vehicle. |
| Driver API | Both single-instance and batch-operation, as **two scenarios** | They stress different subsystems: single-instance = hot-path + drain interplay; batch = batch executor + backpressure. Both are real SaaS usage. |
| Injection | New Spring `@Profile("suspender")` component | Suspend rate must be tunable independently of PI-creation rate, and it needs a two-phase timer (suspend → hold → resume). A dedicated deployment mirrors the existing starter/worker pattern and isolates rate/replica control. |
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

## The `suspender` component

A third role on the existing single-jar / Spring-profile design
(`LoadTesterApplication` + `--spring.profiles.active=suspender`), alongside `starter` and
`worker`. It reuses the v2 process-instance search machinery already used by the
data-availability meter (`Starter.setupDataAvailabilityMeter`) and read benchmark.

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
| `mode` | `single` | `single` or `batch`. |
| `process-id` | `benchmark` | Process definition to target. |
| `rate` / `rate-duration` | `10` / `1s` | Suspend attempts per interval (single mode). |
| `batch-interval` | `10s` | Interval between batch operations (batch mode). |
| `batch-page-size` | `1000` | Max instances per batch operation. |
| `hold-duration` | `30s` | How long an instance stays suspended before resume. |
| `sample-size` | `100` | Candidates fetched per suspend cycle (single mode). |

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

## How to run

1. Build + push images: `make docker` in `load-tests/load-tester` (adds the `suspender`
   image once its Jib profile is wired).
2. Deploy a load test via `newLoadTest.sh` / `make install` with a scenario values file that
   enables the suspender deployment and sets the base rate to `typical` (~50 PI/s).
3. Run arm A (`suspender.enabled=false`) and arm B (`enabled=true`) for the same duration
   (multi-hour), on comparable cluster state.
4. Compare on the Camunda Performance + Zeebe Grafana dashboards and the `SuspensionMetrics`
   panels; archive results.

## Deliverables / follow-ups

- [ ] `suspender` component + `SuspenderProperties` + `application.yaml` wiring (this PR).
- [ ] Jib `suspender` profile in `load-tester/pom.xml` + `docker-suspender` Makefile target.
- [ ] BPMN model with a message/timer catch event for the drain scenario, + scenario values
      file enabling the suspender.
- [ ] Helm chart (`camunda-load-tests-helm`, external repo) support for a `suspender`
      deployment — tracked separately, chart lives outside this monorepo.
- [ ] Optional: promote to a recurring daily-stress variant if suspend/resume becomes a
      relied-upon path.
