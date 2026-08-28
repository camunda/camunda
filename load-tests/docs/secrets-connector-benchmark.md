# Secrets connector e2e benchmark

Benchmarks secret resolution (`POST /v2/secrets/resolve`) through the code path a customer
actually exercises — an outbound connector job resolving a legacy secret before its HTTP call —
instead of a standalone driver hammering the gateway API directly.

## Background

An earlier iteration of this benchmark (`zeebe-secrets` scenario, superseded by this one) added a
standalone HTTP driver to the load-tester that called `/v2/secrets/resolve` and `/v2/secrets/list`
directly, impersonating the `orchestration` client. Review feedback on that approach
([PR #60742](https://github.com/camunda/camunda/pull/60742)) raised two concerns this scenario
addresses:

- **Architectural**: the load-tester Helm chart (`camunda-load-tests-helm`) already provides a
  Starter/Worker deployment model; adding a bespoke driver deployment duplicates that
  infrastructure instead of reusing it. This scenario adds no new Deployment templates —
  it only adds a BPMN process and reuses the vendored Starter, plus the `connectors` component
  every load-test cluster already deploys.
- **Product relevance**: recurring standalone API benchmarks in isolation aren't very actionable.
  The customer-relevant path is secret resolution for **connectors** (the actual caller of
  `/v2/secrets/resolve` in production), so measuring it there — not via an API-shaped proxy for it
  — gives baseline numbers that are directly meaningful to the Connectors team and to customers.

## How it works

The [`secrets-connector` scenario](../setup/common.mk) configures the Starter to create instances
of the [`connectorSecretResolution`](../load-tester/src/main/resources/bpmn/secrets/connectorSecretResolution.bpmn)
process instead of its default `one_task` process, and deploys no workers — the process's only
task is a single `io.camunda:http-json:1` service task, activated and completed by the
`connectors` component (already part of every load-test cluster; see
[`camunda-platform-values-defaults.yaml`](../setup/main/values/camunda-platform-values-defaults.yaml)).

The task's `headers` input is built from `batchSize` placeholders of the form
`{{secrets.<keyPrefix><index>}}`, where `index` is `modulo(businessKey + modulo(i, uniquePerBatch),
poolSize)` and `businessKey` is the Starter's own per-instance monotonic counter. Before issuing the
HTTP call, the connector runtime resolves every one of them via `CentralStoreSecretProvider`
(legacy `FALLBACK` mode, configured on the `connectors` component by
[`camunda-platform-values-secrets-connector.yaml`](../setup/main/values/camunda-platform-values-secrets-connector.yaml)),
which calls the same gateway `/v2/secrets/resolve` endpoint the old driver benchmarked directly —
but through the client that actually calls it in production.

`secret-filter.mode` is `DISABLED`, not `STRICT`/`LAX`: both of those derive their per-element
allow-list by statically regex-scanning the BPMN XML's `zeebe:input source` text for literal
`{{secrets.<name>}}` occurrences (`ProcessDefinitionSecretKeyCache`/`SecretUtil` in connectors).
This scenario's placeholder is built at runtime by a FEEL expression
(`"{{secrets." + keyPrefix + string(...) + "}}"`), so no literal name ever appears in the BPMN XML
— the scan finds none, both modes cache that empty allow-list, and every reference is filtered out
from then on (`LazyLoadingSecretFilter` only falls back to allow-all on an exception during the
scan, not on a legitimately-empty result). This was confirmed empirically: with `STRICT`, a full
run produced zero `/v2/secrets/resolve` calls and zero `camunda_secret_cache_result_total`
increments on any orchestration pod, even though jobs kept completing — the target
(`connectors:8080/actuator/health`) never validates headers, so a filtered-out, unresolved
placeholder still "succeeds". `DISABLED` is the only mode that skips the filter
(`SecretFilter.allowAll()`), so it is the only one compatible with a dynamically-parameterized
secret name.

`batchSize`, `uniquePerBatch`, `poolSize` and `keyPrefix` are process variables read from the
Starter's `payloadPath` (see [Test scenarios](#test-scenarios) below), so tuning them requires no
BPMN or code change — only a different payload file and, when `poolSize` grows past the seeded
secret count, a matching `secretsConnectorBenchmark.count` override.

The task calls the `connectors` pod's own `/actuator/health` endpoint rather than an external
service: the goal is to measure secret-resolution overhead added to a job activation, not to
benchmark an arbitrary downstream HTTP target, and this avoids depending on external network access
from the load-test cluster.

The orchestration cluster is configured with a **file-based secret store** via the chart's
first-class [`orchestration.secretStore.file`](../setup/main/values/camunda-platform-values-secrets-connector.yaml)
([camunda-platform-helm#6721](https://github.com/camunda/camunda-platform-helm/pull/6721)): a
Kubernetes `Secret` (`benchmark-secrets`, seeded by the `load-test-setup` chart's
`secretsConnectorBenchmark` values) is mounted one-file-per-secret at `/etc/camunda/secrets`, and
the chart writes `camunda.secrets.stores.file.default.path` for it. The connector resolves
`secrets.BENCHMARK_TOKEN0`, which the gateway maps to the mounted `BENCHMARK_TOKEN0` file.

Because `orchestration.secretStore` is not in the pinned released chart yet, this scenario needs
the platform chart sourced from git `main`. This uses a **generic mechanism** for benchmarking any
chart feature that is not released yet: `newLoadTest.sh` sources the `camunda-platform` chart from
the `camunda-platform-helm` git ref in `LOAD_TEST_PLATFORM_CHART_GIT_REF` (cloning it and building
its dependencies) instead of pulling the released artifact, and falls back to the pinned released
chart when the variable is unset. The
[load test workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml)
exposes this as the **`platform-chart-from-main` checkbox**, which sets the ref to `main` — enable
it for any run that needs an unreleased chart feature.

## Test scenarios

The default payload
([`connectorSecretResolutionPayload.json`](../load-tester/src/main/resources/bpmn/secrets/connectorSecretResolutionPayload.json),
`batchSize=1, uniquePerBatch=1, poolSize=1`) reproduces the original single-secret behavior: every
job resolves the same one secret, which stays cache-warm for the whole run (cache-first). Two
presets cover the other cache/store behaviors from
[issue #56590](https://github.com/camunda/camunda/issues/56590)'s benchmark plan, selected by
overriding `load-tester.starter.payloadPath` (and, where noted, the seeded secret count):

| Scenario | Payload | `secretsConnectorBenchmark.count` | What it measures |
| --- | --- | --- | --- |
| Cache-first (default) | `connectorSecretResolutionPayload.json` | `1` (default) | Baseline resolve latency/throughput once the single secret is cached. |
| Batch-dedup | `connectorSecretResolutionPayload-dedup.json` (`batchSize=20, uniquePerBatch=4, poolSize=20`) | `20` | Each job resolves 20 secret placeholders drawn from only 4 distinct names — the connector re-resolves the same 4 names 5x per job, so `camunda_secret_cache_result_total{result="HIT"}` should dominate within a job even before any cross-job cache warm-up, isolating the in-request dedup effect from cross-job caching. |
| Store-miss | `connectorSecretResolutionPayload-store-miss.json` (`batchSize=1, uniquePerBatch=1, poolSize=5000`) | `5000` | `modulo(businessKey, 5000)` cycles through 5000 distinct secrets, so — unless the cache holds all 5000 entries — most resolves are cache misses that fall through to a real store read. Compare `camunda_secret_cache_result_total{result="MISS"}` and `camunda_connector_outbound_execution_time_seconds_*` against the cache-first run to quantify the store-read cost.

Example, running the batch-dedup preset:

```sh
make secrets-connector additional_load_test_configuration="--set load-tester.starter.payloadPath=bpmn/secrets/connectorSecretResolutionPayload-dedup.json" additional_load_test_setup_configuration="--set secretsConnectorBenchmark.count=20"
```

`poolSize` and `secretsConnectorBenchmark.count` must match: a `poolSize` larger than the seeded
count sends resolve requests for secrets that don't exist, which fail rather than exercising a
real store-miss read.

### Concurrency ramp

To observe how resolve latency/throughput scale with concurrency (the issue's `1 → 5 → 10 → 25 →
50 → 100` ramp),
[`secrets-connector-rate-ramp.sh`](../setup/scripts/secrets-connector-rate-ramp.sh) re-runs the
same scenario at increasing `load-tester.starter.rate` values against an already-deployed
namespace, each step a separate `helm upgrade`, so results between steps stay isolated and
comparable:

```sh
cd load-tests/setup/<name>
../scripts/secrets-connector-rate-ramp.sh <namespace> 300  # 300s dwell (warm-up+measure+cool-down) per step
# or with custom rates:
../scripts/secrets-connector-rate-ramp.sh <namespace> 300 1 10 50
```

Use the dashboard's time range to bound each step and compare RPS/p50/p95/p99/error-rate/cache-hit
ratio panels across steps.

### Coverage vs. issue #56590

This scenario, plus the presets above, benchmarks `POST /v2/secrets/resolve` across cache-first,
store-miss, batch-dedup, and concurrency-ramp scenarios — through the real connector code path.
It does **not** exercise `POST /v2/secrets/list`: the connector resolves secrets in-process via
`CentralStoreSecretProvider`/`SecretProviderAggregator.getSecret()`, never calling the gateway's
list REST endpoint. If `/v2/secrets/list` coverage is needed, it requires either reviving the
driver-based approach from [PR #60742](https://github.com/camunda/camunda/pull/60742) for that one
call, or a dedicated connector/task that calls it directly.

## Metrics

No custom driver metrics are introduced. Observe the connector's own Micrometer metrics (already
scraped by the existing Prometheus/Grafana pipeline):

- `camunda_connector_outbound_execution_time_seconds_*` — end-to-end job execution time
  (includes secret resolution + the HTTP call), labeled by connector type.
- `camunda_connector_outbound_invocations_total` — invocation count/outcome, to compute throughput
  and error rate.
- `camunda_connector_secret_legacy_resolutions_total` (see `ConnectorMetrics.Secrets` in the
  `connectors` repo) — legacy secret references successfully resolved, labeled by
  `physicalTenantId`.
- The engine-side `camunda_secret_cache_*` and `camunda_secret_resolution_duration_seconds_*`
  metrics, which measure the gateway-side resolve cost independent of which client called it, and
  remain valid baselines from the earlier driver-based benchmark.

A ready-made dashboard,
[`monitor/grafana/dashboards/secrets-connector-benchmark.json`](../../monitor/grafana/dashboards/secrets-connector-benchmark.json),
plots all of the above. It is auto-provisioned by the existing Grafana file-provider (see
`monitor/grafana/provisioning/dashboards/dashboard.yml`), so it appears in Grafana without any
extra setup — pick the `cluster`/`namespace` template variables for the benchmark's namespace. It
adapts the `zeebe-secrets-benchmark` dashboard from the earlier driver-based benchmark: the
connector-invocation and resource-usage panels are new, while the secret-cache and engine
resolution panels are reused unchanged, since those measure the gateway-side store/cache
regardless of which client (HTTP driver or, here, the outbound connector) triggered the lookup.

### Log volume

`camunda-platform-values-secrets-connector.yaml` drops the connectors component's
`SpringConnectorJobHandler` logger to `WARN` (it logs "Received job"/"Completing job" at INFO for
every job by default). No other scenario drives `connectors` under sustained load, so nothing else
hits this: at `starter.rate=100/s` those two per-job INFO lines alone are enough to trip GKE's
"excessive logging" alert (>256 kB/s) within minutes. All the throughput/latency/error signal this
logger would have shown is already on `camunda_connector_outbound_invocations_total` and
`_execution_time_seconds`, so silencing it costs no benchmark data. If you add other scenario
values that drive `connectors` at similarly high rates, check its logs for the same pattern before
running at scale.

This has to be set via `SPRING_APPLICATION_JSON`, not a `LOGGING_LEVEL_*` env var: Spring Boot's
environment-variable relaxed binding lowercases the whole property name before matching it against
the `logging.level` map, so `LOGGING_LEVEL_..._SPRINGCONNECTORJOBHANDLER` resolves to the map key
`...springconnectorjobhandler` (all lowercase). SLF4J/Logback logger names are case-sensitive and
equal the FQCN (with its capitalised class-name segment), so the lowercase key never matches the
real logger and the level silently has no effect — this was confirmed against a live run, whose
`connectors` pod logs kept emitting the per-job INFO lines despite the env var being present in the
pod spec. `SPRING_APPLICATION_JSON` is parsed as JSON, which preserves case, so it is the reliable
way to set per-class log levels here.

## Running

Via the [Camunda load test workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml):
select the branch, name the test, choose the `secrets-connector` scenario, and **enable the
`platform-chart-from-main` checkbox** so the unreleased `orchestration.secretStore.file` is
available.

Also set the **`connectors-tag`** input to a build that includes the legacy secret-resolution
switch (`CAMUNDA_CONNECTOR_SECRET_RESOLVER_LEGACY_MODE` / `CentralStoreSecretProvider` /
`camunda_connector_secret_legacy_resolutions_total`) — e.g. `SNAPSHOT` (built from `connectors@main`)
or `8.10.0-alpha5-rc1`+. The Helm chart's default pinned Connectors version may predate this
feature: an older image silently ignores the env vars (the leftover `{{secrets.BENCHMARK_TOKEN0}}`
placeholder is sent as-is in the header, which the connectors pod's own `/actuator/health` doesn't
validate), so the job still completes and throughput/latency panels look healthy while no secret
is ever actually resolved — the giveaway is "Legacy secret resolutions" and the "Secret cache"
panels staying at zero/no-data even though "Outbound invocation throughput" is nonzero.

Manually (see the [setup README](../setup/README.md) for prerequisites):

```sh
cd load-tests/setup
# Source the platform chart from git main so orchestration.secretStore.file is available.
LOAD_TEST_PLATFORM_CHART_GIT_REF=main ./newLoadTest.sh <name> elasticsearch <ttl-days> false
cd <name>
make secrets-connector
```

This is intended to be run **ad hoc** to establish or refresh a baseline, not as a recurring
scheduled job — recurring standalone benchmarks of this endpoint in isolation don't provide much
additional signal today. Re-run it after a change to the secret-resolution path (e.g. adding
caching to `/v2/secrets/list`) to compare before/after numbers.
