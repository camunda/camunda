# Secrets API benchmark

Benchmarks the secret-resolution gateway API — `POST /v2/secrets/resolve` and
`POST /v2/secrets/list` — for latency, throughput, batch-deduplication effect, cache-first vs
store-miss cost, and concurrency / parallel-I/O scaling.

Inbound connector latency depends on this API, so this benchmark establishes its baseline
performance and the regression thresholds that guard it.

## How it works

The [`zeebe-secrets` scenario](../setup/common.mk) deploys the load-tester's zeebe-secrets driver
(`SPRING_PROFILES_ACTIVE=zeebe-secrets`) instead of the process starter/workers. The driver issues
resolve and list requests through the same `CamundaClient` (and therefore the same gateway address
and OAuth credentials) the other load-tester components use, and records latency, throughput and
outcome as Micrometer metrics scraped by the existing Prometheus/Grafana pipeline.

The orchestration cluster is configured with a **file-based secret store** via the chart's
first-class [`orchestration.secretStore.file`](../setup/main/values/camunda-platform-values-zeebe-secrets.yaml)
([camunda-platform-helm#6721](https://github.com/camunda/camunda-platform-helm/pull/6721)): a
Kubernetes `Secret` (`benchmark-secrets`, seeded by the `load-test-setup` chart) is mounted
one-file-per-secret at `/etc/camunda/secrets`, and the chart writes
`camunda.secrets.stores.file.default.path` for it. The driver resolves `camunda.secrets.bench_<i>`
references, which the gateway maps to the mounted `bench_<i>` files.

Because `orchestration.secretStore` is not in the pinned released chart yet, the `zeebe-secrets`
scenario needs the platform chart sourced from git `main`. This uses a **generic mechanism** for
benchmarking any chart feature that is not released yet: `newLoadTest.sh` sources the
`camunda-platform` chart from the `camunda-platform-helm` git ref in
`LOAD_TEST_PLATFORM_CHART_GIT_REF` (cloning it and building its dependencies) instead of pulling the
released artifact, and falls back to the pinned released chart when the variable is unset. The
[load test workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml)
exposes this as the **`platform-chart-from-main` checkbox**, which sets the ref to `main` — enable it
for any run (including `zeebe-secrets`) that needs an unreleased chart feature. The mechanism is
scenario-agnostic: the workflow and scaffold script hard-code no scenario name.

A local cache (`CachingSecretStore`) sits in front of the file store, so:

- **cache-first** load repeatedly resolves a small reference pool that fits the cache — the file
  store is only read on first touch (or after cache expiry).
- **store-miss** load resolves a reference pool larger than the cache, or runs with `warmup=false`,
  so requests reach the file store.

## Running

Via the [Camunda load test workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml):
select the branch, name the test, choose the `zeebe-secrets` scenario (which drives
mixed resolve + list traffic by default), and **enable the `platform-chart-from-main` checkbox** so
the unreleased `orchestration.secretStore.file` is available. To instead run the
[resolve-only preset](#resolve-only-traffic-preset) through the workflow,
set the `load-test-setup-helm-values` input to `-f zeebe-secrets-values-resolve-only.yaml` — the
scaffold copies the preset into the run directory, so the file resolves there.

Manually (see the [setup README](../setup/README.md) for prerequisites):

```sh
cd load-tests/setup
# Source the platform chart from git main so orchestration.secretStore.file is available.
LOAD_TEST_PLATFORM_CHART_GIT_REF=main ./newLoadTest.sh <name> elasticsearch <ttl-days> false
cd <name>
make zeebe-secrets
```

> **Note:** `make zeebe-secrets` deploys the traffic generator itself — the `zeebe-secrets`
> Deployment from the load-test-setup chart
> ([`benchmark-zeebe-secrets-driver.yaml`](../setup/charts/load-test-setup/templates/benchmark-zeebe-secrets-driver.yaml)),
> gated by `zeebeSecretsBenchmark.enabled` — alongside the file store, seeded secrets
> and authorization. The starter/workers are scaled to 0 for this scenario. The
> driver runs the dedicated `zeebe-secrets` load-tester image (see the `zeebe-secrets` jib
> profile in [`load-tester/pom.xml`](../load-tester/pom.xml)) and reuses the
> `load-test-credentials` Secret for OAuth, so the existing `clients`
> ServiceMonitor scrapes its metrics with no extra wiring.

### Resolve-only traffic (preset)

The default scenario drives **mixed traffic**: an even resolve/list split
(`resolveRatio=0.5`) at 200 req/s — ~100 req/s to each of `/v2/secrets/resolve` and
`/v2/secrets/list` — with in-batch duplicates (`duplicateRatio=0.3`) so the gateway's
server-side de-duplication path is exercised on every run. To instead drive
resolve-only traffic with no in-batch duplicates (isolating `/v2/secrets/resolve`
against distinct references at a single 100 req/s budget, so the `zeebe_secrets_*`
list series stay at 0), layer the
[`zeebe-secrets-values-resolve-only.yaml`](../setup/scenarios/zeebe-secrets-values-resolve-only.yaml)
preset (`rate=100`, `resolveRatio=1.0`, `duplicateRatio=0.0`) on top of the scenario:

```sh
make install scenario=zeebe-secrets \
  additional_load_test_setup_configuration="-f zeebe-secrets-values-resolve-only.yaml"
```

The preset is layered after the scenario's own flags, so it overrides only the rate
and the two ratios and leaves `zeebeSecretsBenchmark.enabled` and the scaled-to-0
starter/workers untouched.

## Scenarios

Each scenario is shaped by `zeebeSecretsBenchmark.driver.*` values (see
[`ZeebeSecretsDriverProperties`](../load-tester/src/main/java/io/camunda/zeebe/config/ZeebeSecretsDriverProperties.java)).
The **default** is the `resolve + list mixed` row below; the resolve-only rows require the
[resolve-only preset](#resolve-only-traffic-preset) (or the equivalent `--set` overrides).
Keep the reference pool, batch shape and rate fixed across compared runs so results are comparable.

|              Scenario              | `resolveRatio` | `batchSize` | `duplicateRatio` | `referencePoolSize` | `warmup` |                                                                 Purpose                                                                  |
|------------------------------------|----------------|-------------|------------------|---------------------|----------|------------------------------------------------------------------------------------------------------------------------------------------|
| resolve cache-first                | 1.0            | 20          | 0.0              | 20                  | true     | Best-case latency/throughput, served from cache                                                                                          |
| resolve store-miss                 | 1.0            | 20          | 0.0              | 5000 †              | false    | Worst-case backend (file store) read cost                                                                                                |
| resolve batch dedup                | 1.0            | 20          | 0.0 / 0.5 / 0.9  | 100                 | true     | Server-side deduplication effect                                                                                                         |
| resolve concurrency                | 1.0            | 20          | 0.0              | 100                 | true     | Saturation point under a concurrency ramp                                                                                                |
| list cache-first                   | 0.0            | —           | —                | —                   | true     | Steady-state catalog access                                                                                                              |
| list store-miss                    | 0.0            | —           | —                | —                   | false    | Backend-bound listing (`list` bypasses cache)                                                                                            |
| list concurrency                   | 0.0            | —           | —                | —                   | true     | Throughput and tail latency under load                                                                                                   |
| resolve + list mixed **(default)** | 0.5            | 20          | 0.3              | 100                 | false    | Parallel-I/O scaling: concurrent resolve+list backend reads, exercising server-side de-duplication (`rate=200`, ~100 req/s per endpoint) |

For the **concurrency ramp**, step the offered load (`rate`) upward across runs — e.g. 200 → 500 →
1000 → 2500 req/s — and record where latency rises sharply, throughput plateaus, or error rate
climbs. `threads` only distributes request submission across the driver's scheduler pool; because
each request is issued asynchronously it does not bound in-flight requests or change the offered
rate, so vary `rate` (not `threads`) to increase load.

**† Store-miss configuration.** A pool of 100 references fits inside the default ~1000-entry secret
cache, so after first touch the workload becomes cache-first and stops measuring the store. To keep
the working set larger than the cache, seed and address a pool that exceeds it: set
`zeebeSecretsBenchmark.count=5000` and `referencePoolSize=5000` (the [resolve-only
preset](#resolve-only-traffic-preset) with `--set` overrides), which is what the baseline results
below use. `referencePoolSize` must never exceed `zeebeSecretsBenchmark.count` (the number of seeded
secret files). Alternatively, lower `camunda.secrets.cache.max-size` below `referencePoolSize`.

## Metrics

A ready-made Grafana dashboard for these metrics lives at
[`monitor/grafana/dashboards/zeebe-secrets-benchmark.json`](../../monitor/grafana/dashboards/zeebe-secrets-benchmark.json)
(UID `zeebe-secrets-benchmark`, title "Zeebe Secrets Benchmark"): throughput, latency percentiles, error
ratio, the driver configuration, a **Server resource usage & saturation** row (CPU, memory, CPU
throttling, and offered-vs-served queueing), a **Secret cache** row (hit rate, lookups, evictions,
size, and distinct lookups per resolve — [PR #59861](https://github.com/camunda/camunda/pull/59861)), and an **Engine secret
resolution** row (duration, outcome, cycle errors — [PR #59468](https://github.com/camunda/camunda/pull/59468),
job-activation path), filtered by `cluster` / `namespace` / `pod`. Like the other
benchmark dashboards it is version-controlled here and deployed to the benchmark Grafana by
referencing its raw GitHub URL (see [`monitor/README.md`](../../monitor/README.md)); adding it to
that instance is a one-line registration in the deployment repo.

Micrometer names are exported to Prometheus with `.` → `_`; timers gain a `_seconds` suffix with
`_bucket` / `_count` / `_sum` series, and counters gain `_total`. Substitute `$namespace` with the
test namespace and `$__rate_interval` with a duration ≥ 4× the scrape interval.

### Request throughput (RPS)

Requests completed per second, split by endpoint. Uses the latency timer's completion count (as the
dashboard does) rather than the submitted counter, so it reports achieved throughput rather than
offered load — the two diverge under saturation or queueing.

- **Unit:** req/s
- **Measurement:** rate

```promql
sum(rate(zeebe_secrets_request_latency_seconds_count{namespace=~"$namespace"}[$__rate_interval])) by (endpoint)
```

### Request latency p50 / p95 / p99

End-to-end latency of a single request, per endpoint. Change `0.99` to `0.95` / `0.50` for the other
percentiles.

- **Unit:** seconds
- **Measurement:** histogram quantile

```promql
histogram_quantile(0.99, sum(rate(zeebe_secrets_request_latency_seconds_bucket{namespace=~"$namespace"}[$__rate_interval])) by (le, endpoint))
```

### Error rate

Fraction of requests that completed with an error, per endpoint.

- **Unit:** ratio (0–1)
- **Measurement:** rate ratio

```promql
sum(rate(zeebe_secrets_request_latency_seconds_count{namespace=~"$namespace", outcome="error"}[$__rate_interval])) by (endpoint)
/
sum(rate(zeebe_secrets_request_latency_seconds_count{namespace=~"$namespace"}[$__rate_interval])) by (endpoint)
```

### Resource usage (CPU / memory)

CPU cores and working-set memory per pod in the run namespace. These are **not** filtered by `$pod`,
since the Camunda gateway that serves the secrets API is a different pod than the driver — break the
series down `by (namespace, pod)` and read the gateway pod for the server-side cost. The dashboard's
`cluster` and `namespace` variables are **single-select**: a benchmark runs in exactly one namespace,
and grouping cluster-wide (`namespace=~".*"`) would sum same-named pods (`camunda-0`, `zeebe-0`, …)
across every concurrent run and report cores/bytes an order of magnitude too high. Keep `container!=""`
to drop the pod-level aggregate.

- **Unit:** cores / bytes
- **Measurement:** rate (CPU) / gauge (memory)

```promql
sum by (namespace, pod) (rate(container_cpu_usage_seconds_total{cluster=~"$cluster", namespace=~"$namespace", container!="", image!=""}[$__rate_interval]))
sum by (namespace, pod) (container_memory_working_set_bytes{cluster=~"$cluster", namespace=~"$namespace", container!="", image!=""})
```

### Saturation / queueing

Two complementary saturation signals, both from existing metrics:

- **CPU throttling ratio** — fraction of CFS scheduler periods a pod was throttled in. Sustained
  non-zero throttling on the gateway means it is CPU-bound and latency will rise regardless of
  offered load.
- **Offered vs served** — the driver's submitted request rate against its completed rate per
  endpoint. A sustained gap (offered above served) means requests queue faster than they drain.
- **Unit:** ratio (0–1) / req/s
- **Measurement:** rate ratio / rate

```promql
sum by (namespace, pod) (rate(container_cpu_cfs_throttled_periods_total{cluster=~"$cluster", namespace=~"$namespace", container!=""}[$__rate_interval]))
/ clamp_min(sum by (namespace, pod) (rate(container_cpu_cfs_periods_total{cluster=~"$cluster", namespace=~"$namespace", container!=""}[$__rate_interval])), 1)

sum by (endpoint) (rate(zeebe_secrets_requests_submitted_total{namespace=~"$namespace"}[$__rate_interval]))   # offered
sum by (endpoint) (rate(zeebe_secrets_request_latency_seconds_count{namespace=~"$namespace"}[$__rate_interval]))  # served
```

### Secret cache (server-side)

The file store is wrapped in a caching store, so the HTTP resolve/list path emits the secret-cache
meters added in [PR #59861](https://github.com/camunda/camunda/pull/59861). These are published by
the **Camunda gateway pod**, not the driver, so query them by `cluster` / `namespace` and **not** by
`$pod`; the `store` tag is `default` in this benchmark.

- **Cache hit rate** — the direct cache-first signal, `HIT / (HIT + MISS)`:

```promql
sum by (store) (rate(camunda_secret_cache_result_total{namespace=~"$namespace", result="HIT"}[$__rate_interval]))
/ clamp_min(sum by (store) (rate(camunda_secret_cache_result_total{namespace=~"$namespace"}[$__rate_interval])), 1)
```

- **Evictions by cause** (`camunda_secret_cache_evictions_total`, tag `cause=SIZE|EXPIRED|EXPLICIT|COLLECTED`)
  and **cache size** (`camunda_secret_cache_size` gauge) show whether the fixed cache bound fits the
  reference pool: sustained `SIZE` evictions while the size sits at its maximum means the pool is
  larger than the cache — i.e. a genuine store-miss run.

### Seeing the `duplicateRatio` effect (server-side de-duplication)

`duplicateRatio` does **not** show up as extra cache hits. The gateway collapses the repeated
references within each resolve batch into a distinct set (`LinkedHashSet` in `SecretServices`)
**before** the cache or store is consulted, so a deduplicated reference never reaches the cache and
never counts as a `HIT`. Raising `duplicateRatio` therefore *lowers* the number of lookups per
request rather than adding hits.

The observable signal is the **Distinct store lookups per resolve** panel — the distinct names the
gateway actually looks up, per resolve request:

```promql
sum(rate(camunda_secret_cache_result_total{namespace=~"$namespace"}[$__rate_interval]))
/ clamp_min(sum(rate(zeebe_secrets_requests_submitted_total{namespace=~"$namespace", endpoint="resolve"}[$__rate_interval])), 0.001)
```

The numerator is one cache lookup per distinct name (the `list` endpoint polls the store directly
and does not touch this counter), the denominator is the resolve request rate. The ratio reads the
number of distinct names per batch, so it sits below the driver's `batchSize` by the collapsed
duplicates: `batchSize=20` with `duplicateRatio=0.3` collapses `round(20 × 0.3) = 6` references and
reads **≈14**; set `duplicateRatio=0` and it climbs back to the full `batchSize` of 20. The gap
between this line and `batchSize` (from the **Driver configuration** panel) is the de-duplication
the gateway performed.

### Cache-first vs store-miss

Read the cache effect two ways: directly from the **cache hit rate** above (a cache-first run climbs
toward 1, a store-miss run stays low), and as the **latency delta** between the cache-first and
store-miss runs of the same request shape (compare their p95/p99). The gateway's CPU/memory and the
file-store read activity corroborate the store-miss cost.

Note the REST `/v2/secrets/resolve` path resolves **synchronously in the gateway** (`SecretServices`
reads the store in-process), not through the engine's record stream. The engine's
`camunda_secret_resolution_*` meters from [PR #59468](https://github.com/camunda/camunda/pull/59468)
are recorded by the job-activation resolution scheduler, so they only populate when BPMN traffic
resolves secrets — they stay empty under the HTTP-only driver. The dashboard still carries them (in
a separate row) so a mixed run that also drives process instances shows resolution duration/outcome
alongside the cache and HTTP series.

### Run configuration and completion

`zeebe_secrets_driver_info` (gauge = 1, tagged `resolve_ratio` / `batch_size` / `duplicate_ratio` /
`nb_threads`) surfaces how a run was configured; `zeebe_secrets_run_finished` flips to 1 when a finite run
completes.

## Targets and regression thresholds

Concrete target values are **established from the first baseline run** on `main` against the
file store, then committed here. Until a baseline exists, treat the table below as the shape to fill
in, not as pass/fail gates.

First baseline established from run [`32404249670`](https://github.com/camunda/camunda/actions/runs/32404249670)
on `main` (mixed default: `rate=200`, `resolveRatio=0.5`, `batchSize=20`, `duplicateRatio=0.3`,
`threads=2`, file store, Elasticsearch secondary storage), sampled after ~45 min of steady state.

|       Metric        |     Endpoint     |          Target (baseline)          |           Regression threshold           |
|---------------------|------------------|-------------------------------------|------------------------------------------|
| p99 latency         | resolve (cached) | ~24 ms (p50 ~8 ms, p95 ~17 ms)      | > 20% above rolling baseline, or > 250ms |
| p99 latency         | resolve (miss)   | ~25 ms (p50 ~17 ms, p95 ~24 ms)     | > 20% above rolling baseline             |
| p99 latency         | list             | ~25 ms (p50 ~9 ms, p95 ~23 ms)      | > 20% above rolling baseline             |
| throughput          | resolve          | ~100 req/s (200 total, 50/50 split) | > 10% below rolling baseline             |
| error rate          | both             | 0%                                  | > 0.1% sustained                         |
| dedup latency delta | resolve          | unique ≥ heavy-dup p95              | heavy-dup p95 above unique p95           |

The `resolve (miss)` row was measured with a separate resolve-only run
([`32410732515`](https://github.com/camunda/camunda/actions/runs/32410732515)) that forces store
reads by making the working set larger than the cache: seed 5000 secrets and set
`referencePoolSize=5000` against the default 1000-entry cache (`camunda.secrets.cache.max-size`), so
LRU sustains ~80% misses (measured 1352 MISS/s vs 338 HIT/s). This needs no platform override — it is
all `--set zeebeSecretsBenchmark.count=5000 --set zeebeSecretsBenchmark.driver.referencePoolSize=5000`
layered on the resolve-only preset. The store-miss p50 (~17 ms) is roughly double the cache-first p50
(~8 ms), but the p99 stays ~25 ms: file-store reads are cheap, so the tail is not backend-bound. (The
mixed default instead warms the cache to ~100% HIT — measured HIT:MISS ≈ 1400:0.4 /s — so it only
characterises the cache-first path.)

Rationale:

- **Latency** is the primary signal (this API gates inbound connector latency). The cache-first p99
  is the tightest gate; the store-miss p99 bounds worst-case backend cost.
- **Throughput** and **error rate** together detect saturation regressions.
- **Dedup latency delta** validates that server-side deduplication actually collapses duplicate
  references: a heavily-duplicated batch (`duplicateRatio=0.9`) should not cost more than an
  all-unique batch of the same size — if it does, deduplication regressed.

## Results report

Record one row per run so results stay comparable and reviewable:

| Endpoint |   Scenario    | Payload (batchSize / dupRatio) | Concurrency (rate × threads) |             Cache state             | Duration |  RPS   |  p50   |  p95   |  p99   | Errors |                                                                  Notes                                                                  |
|----------|---------------|--------------------------------|------------------------------|-------------------------------------|----------|--------|--------|--------|--------|--------|-----------------------------------------------------------------------------------------------------------------------------------------|
| resolve  | mixed default | 20 / 0.3                       | 200 × 2                      | warm (~100% HIT)                    | ~45 min  | ~100.8 | ~8 ms  | ~17 ms | ~24 ms | 0%     | run [32404249670](https://github.com/camunda/camunda/actions/runs/32404249670); ~14 distinct lookups/resolve (dedup 20→14)              |
| list     | mixed default | — / —                          | 200 × 2                      | n/a (list polls store)              | ~45 min  | ~99.2  | ~9 ms  | ~23 ms | ~25 ms | 0%     | same run; `list` bypasses the cache                                                                                                     |
| resolve  | store-miss    | 20 / 0.0                       | 100 × 2                      | ~80% miss (5000 pool vs 1000 cache) | ~13 min  | ~100.0 | ~17 ms | ~24 ms | ~25 ms | 0%     | run [32410732515](https://github.com/camunda/camunda/actions/runs/32410732515); `count=5000`, `referencePoolSize=5000` force LRU misses |
|          |               |                                |                              |                                     |          |        |        |        |        |        |                                                                                                                                         |

