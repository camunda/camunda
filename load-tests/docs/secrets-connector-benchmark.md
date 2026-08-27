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

The task's `headers` input contains a `{{secrets.BENCHMARK_TOKEN0}}` placeholder. Before issuing
the HTTP call, the connector runtime resolves it via `CentralStoreSecretProvider` (legacy
`FALLBACK` mode, configured on the `connectors` component by
[`camunda-platform-values-secrets-connector.yaml`](../setup/main/values/camunda-platform-values-secrets-connector.yaml)),
which calls the same gateway `/v2/secrets/resolve` endpoint the old driver benchmarked directly —
but through the client that actually calls it in production. `secret-filter.mode=STRICT` (required
whenever `legacy.mode=FALLBACK`) derives its allow-list from the task's own input mappings, so no
separate allow-list config is needed.

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
