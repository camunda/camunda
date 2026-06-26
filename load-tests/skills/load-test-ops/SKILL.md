---
name: load-test-ops
description: Trigger, monitor, update, profile, and stop Camunda load tests using gh CLI and kubectl directly — no MCP server required
---

# Camunda Load Test Operations

For the full operational reference (architecture, scenarios, scheduling, secondary storage,
Helm value typing pitfalls), see [`load-tests/README.md`](https://github.com/camunda/camunda/blob/main/load-tests/README.md). For metrics
definitions, SLO targets, and Prometheus queries, see
[`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md). For the canonical schema of every
available chart value (beyond what `load-tests/setup/main/values/camunda-platform-values-defaults.yaml` overrides), see
the upstream chart at [`camunda/camunda-platform-helm`](https://github.com/camunda/camunda-platform-helm/tree/main/charts/camunda-platform).

## Prerequisites check

`gh` CLI must always be authenticated (`gh auth status`).

> **Note for Claude:**
>
> - Run `gh` and `kubectl` commands directly via the Bash tool. The user's `gh` CLI is
>   authenticated on the host. If a command isn't pre-approved, you'll get a one-time
>   permission prompt — accept it and proceed.
> - **Don't leave the user waiting.** Whenever you start anything that takes time (workflow
>   dispatch, helm install, kubectl rollout, log capture, build), follow it through to
>   completion automatically: watch progress, pull the final output, and render it inline.
>   Stream interim status updates so the user can interrupt if something looks wrong. Don't
>   ask "should I wait?" or "want me to fetch the result?" — that's friction; chaining is
>   the default.
> - For GHA dispatches specifically: capture the run ID via
>   `gh run list --workflow=<file> --repo camunda/camunda --limit 1 --json databaseId --jq '.[0].databaseId'`,
>   watch it with `gh run watch <id> --repo camunda/camunda --exit-status`, then pull the
>   result with `gh run view <id> --repo camunda/camunda` (job summary) or `gh run download <id>`
>   (artifacts).

### Default path: GHA workflow

Use GHA for everything by default. It is the only path that builds a Docker image from a branch
(initial test of a feature branch, or after pushing new commits) and it works for any user with
`gh` access — no kubectl, no Helm, no Teleport login required. Prefer it unless the user explicitly
wants the kubectl path or the situation below applies.

### Optional: direct kubectl/Makefile

Only fall back to kubectl when **all** of the following are true:

1. The user has kubectl access to the benchmark cluster (Teleport login active)
2. There is already a usable Docker image — either a pre-built branch image (`reuse-tag`) or a
   released image (`orchestration-tag`)
3. The change is config-only (Helm values, replica counts, resource limits, starter rate) — no
   code changes that would require a new image build

**Caveat:** the kubectl path cannot build Docker images. If the image you need does not yet exist,
dispatch a GHA build first, then switch to kubectl for further config iteration on the same image.

Check kubectl access before taking the direct path:

```bash
kubectl get nodes 2>/dev/null && echo "kubectl available" || echo "use GHA"
```

---

## Typical workflow

Most runs follow the same arc — each step links to its own section for full options:

1. **[Start a load test](#start-a-load-test)** — dispatch the GHA workflow with a branch ref.
   Cluster is up in ~5–10 min.
2. **[Check status](#check-status)** — verify pods and starter reach steady state (~5 min
   warm-up).
3. **Wait for the run window** — 10–60 min, depending on what you're measuring. Most signals
   stabilise within 20 min.
4. *(optional)* **[Profile a running cluster](#profile-a-running-cluster)** — flamegraph capture
   (~5 min per pod).
5. **[Analyze metrics](#analyze-metrics)** — snapshot the run window into a JSON / job-summary
   report.
6. **[Compare against a baseline](#compare-against-a-baseline)** — diff against daily tests or a
   parallel `main` run; absolute numbers are rarely conclusive on their own.
7. **[Stop / clean up](#stop--clean-up)** — delete the namespace to free benchmark resources
   (also auto-cleaned at `deadline-date`).

For automation, the metrics workflow's `results-json` output can feed directly into the delete
workflow.

---

## Namespace conventions

- Format: `c8-<initials>-<slug>-<YYYYMMDD>` — always prefixed with `c8-`
- Example: `c8-ck-my-feature-20260427` (ck = ChrisKujawa)
- `camunda-load-test.yml` input `name` may be passed with or without `c8-` (it will add the prefix if missing)
- Metrics/profile/delete workflows take the full namespace and require it to start with `c8-`
**Use a unique name for each new run.** Reusing a namespace mixes the new run's metrics with the
prior run's data and makes comparisons ambiguous; for intentional in-place iteration (config
tweak, image bump on the same cluster), use [Update / redeploy](#update--redeploy) instead. The
date alone is not enough — it collides within a single day or across parallel investigations
from different sessions. Add a disambiguator:

- short Git SHA — `ck-feature-abc1234` — best when validating a specific commit
- HHMM time — `ck-feature-20260508-1430` — best for ad-hoc iteration loops
- role suffix — `ck-feature-20260508-baseline` / `-branch` — best for paired comparison runs
  (see [Compare against a baseline](#compare-against-a-baseline))

Namespaces carry these labels (set by `newLoadTest.sh`):

|        Label         |           Value           |
|----------------------|---------------------------|
| `camunda.io/purpose` | `load-test`               |
| `camunda.io/created-by` | GitHub actor (sanitized)  |
| `deadline-date`      | `YYYY-MM-DD` (TTL expiry) |

---

## Start a load test

### Via GHA (builds image from branch)

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=ck-<slug>-<YYYYMMDD>
```

Most useful inputs:

|          Input           |                              Use                              |
|--------------------------|---------------------------------------------------------------|
| `name` (required)        | Namespace suffix; full namespace becomes `c8-<name>`          |
| `ref`                    | Git ref to build the image from (defaults to `main`)          |
| `scenario`               | `latency` / `realistic` / `typical` / `max` / `archiver`      |
| `secondary-storage-type` | `elasticsearch` (default) / `opensearch` / `postgresql` / ... |
| `reuse-tag`              | Skip the build and reuse a previously built tag               |
| `orchestration-tag`      | Use a released Docker Hub image instead of building           |
| `platform-helm-values`   | Extra `--set` flags for the Camunda platform chart            |
| `load-test-load`         | Extra `--set` flags for the workload chart                    |
| `enable-optimize`        | Toggle Optimize (defaults to `true`)                          |
| `ttl`                    | Days before auto-cleanup (defaults to `1`)                    |

The full input list lives in the `inputs:` block of `.github/workflows/camunda-load-test.yml`.

> **Pick the right tag input — `reuse-tag` and `orchestration-tag` hit different registries:**
> - `reuse-tag` pulls from the **internal Camunda registry**. Only works for tags built by a
>   prior GHA load-test run on this repo. Passing a public tag (`8.4.3`, `SNAPSHOT`, `latest`)
>   fails because the internal registry can't resolve them.
> - `orchestration-tag` pulls from the **public Docker Hub registry**. Required for released
>   versions (e.g. `8.4.3`), nightly `SNAPSHOT`, or any tag you didn't build via GHA yourself.

> **Helm value typing — common gotchas:**
> - Use `--set-string` for chart fields whose schema declares them as strings (e.g.
>   `orchestration.cpuThreadCount`, `orchestration.ioThreadCount`). Plain `--set` will pass an
>   integer and the chart's JSON-schema validation will reject the install.
> - Quote boolean-looking string env values: `--set-string 'orchestration.env[0].value=false'`,
>   not `--set 'orchestration.env[0].value=false'`.
> - Quote any value that contains `,` `[` `]` `=` so Helm doesn't parse it as a list/key syntax.
>
> Full guidance with copy-paste examples in the
> [Trigger Camunda Load Test GitHub Workflow](https://github.com/camunda/camunda/blob/main/load-tests/README.md#trigger-camunda-load-test-github-workflow-recommended)
> section of `load-tests/README.md`.

### Via manual setup (kubectl, existing image only)

Only when GHA is not viable for this iteration and you have kubectl access. The kubectl path does
not build images — provide an already-built tag (`reuse-tag` from a previous GHA run, or a
released `orchestration-tag`):

The following will create a new folder under `load-tests/setup/<namespace>` with copying the Helm values for the platform and load test charts.

```bash
cd load-tests/setup
./newLoadTest.sh <namespace> [secondaryStorage] [ttl_days] [enable_optimize]
```

Configuration is driven by the values files at the setup folder which is created by the `newLoadTest.sh` script which are copied from the defaults.

For example:

- `load-tests/setup/main/values/camunda-platform-values-defaults.yaml` — platform chart overrides (image tag, resources,
  exporters, secondary storage)
- `load-tests/setup/main/values/camunda-platform-values-${storage}.yaml` — platform chart overrides for secondary storage-specific configuration (e.g. Elasticsearch JVM settings, OpenSearch auth)
- `load-tests/setup/main/values/load-test-values.yaml` — load tester chart overrides (rate, scenario, worker)

Override individual keys by passing extra `--set` flags to the `make` calls (see Update / redeploy below). Full reference in
`load-tests/setup/README.md`.

Depending on the use case (clarify with the user) different scenarios can be started, e.g realistic, max. 

```bash
make realistic # Installs the camunda platform and load tester with the realistic scenario configuration, which is defined in the values file.
```

After running the script, check the deployment via kubectl (this path doesn't dispatch a GHA workflow, so `gh run view` won't show anything). Use the kubectl commands in the
[Check status](#check-status) section below.

---

## Check status

**Via GHA (always works):**

```bash
gh run view <run-id> --repo camunda/camunda
```

**Via kubectl (if available — more accurate):**

```bash
kubectl get pods -n <namespace>
kubectl get events -n <namespace> --sort-by='.lastTimestamp' | tail -20
```

**List all your running load tests:**

```bash
# Via GHA (default — works without kubectl)
gh run list --workflow=camunda-load-test.yml --repo camunda/camunda \
  --user $(gh api /user --jq .login) --limit 20

# Via kubectl labels (if you have cluster access)
kubectl get namespaces -l camunda.io/purpose=load-test,camunda.io/created-by=$(gh api /user --jq .login)
```

---

## Inspect configuration

```bash
# Read default Helm values (know what keys to override)
cat load-tests/setup/main/values/camunda-platform-values-defaults.yaml
cat load-tests/setup/main/values/load-test-values.yaml

# What inputs were dispatched into a GHA run? The build/install jobs print
# the resolved values and parameters into the GitHub Actions step summary.
gh run view <run-id> --repo camunda/camunda -w

# Read what's actually deployed (requires kubectl and Helm)
helm get values <namespace> -n <namespace>
helm get values <namespace>-test -n <namespace>
```

---

## Update / redeploy

Default to GHA. Use kubectl only for config-only iteration on an existing image (see Prerequisites).

### Via GHA (default — handles builds and config changes)

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=ck-<slug>-<YYYYMMDD>
```

Reuse an existing image to skip the Docker build:

```bash
# Read the previous run's image tag from its GHA step summary
gh run view <run-id> --repo camunda/camunda

gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=<name-without-c8-prefix> \
  --field reuse-tag=<image-tag> \
  --field platform-helm-values="--set orchestration.resources.limits.memory=4Gi"
```

### Via kubectl (config-only iteration on an existing image)

Faster than GHA for tweaking Helm values against an already-running deployments. See `load-tests/setup/README.md` for full manual setup documentation.

**Always pin the image** — `load-tests/setup/main/values/camunda-platform-values-defaults.yaml` defaults to `tag: SNAPSHOT`,
which floats. For an iterative loop you usually want a specific tag (the one from the previous
GHA build, or a released `orchestration-tag`):

```bash
cd load-tests/setup

# Create namespace if it doesn't exist yet
./newLoadTest.sh <namespace> <secondary-storage> <ttl-days> <enable-optimize>

# Upgrade platform Helm chart — pin the image tag explicitly
cd <namespace>
make max additional_platform_configuration="\
  --set-string global.image.tag=<image-tag> \
  --set orchestration.resources.limits.memory=4Gi" \
  additional_load_test_configuration="--set starter.rate=200"
```

---

## Profile a running cluster

Profiles all 3 broker pods in parallel (cpu / wall / alloc):

```bash
gh workflow run profile-load-test.yml --repo camunda/camunda \
  --field name=<full-namespace-with-c8-prefix>
```

Profile a single pod (cpu only):

```bash
gh workflow run profile-load-test.yml --repo camunda/camunda \
  --field name=<full-namespace-with-c8-prefix> \
  --field pod=camunda-1
```

After ~5 min, download flamegraph artifacts:

```bash
gh run download <run-id> --repo camunda/camunda
```

Artifact names: `flamegraph-cpu-camunda-0`, `flamegraph-wall-camunda-1`, `flamegraph-alloc-camunda-2`

---

## Analyze metrics

Snapshot run metrics into a JSON / job-summary report — useful for post-run analysis before
tear-down, so you can capture results and then delete the namespace to free resources.

The workflow and `loadTestMetrics.sh` script both run the **headline queries only** — the fixed
set defined in [`load-tests/docs/scripts/queries.yaml`](https://github.com/camunda/camunda/blob/main/load-tests/docs/scripts/queries.yaml):
throughput (PI/s), completion ratio, backpressure, data-availability p99, and
request-response latency p99. This is enough to answer "did the run meet its SLO?".

For deeper investigation (FNI/s, processing/exporting latency, CPU throttling, JVM heap trend,
processing and exporting backlogs, write IOPS, disk usage, …), the full metric catalogue with
PromQL queries is in [`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md). The Prometheus
instance at [`monitor.benchmark.camunda.cloud`](https://monitor.benchmark.camunda.cloud)
(Okta login) and the [Camunda Performance dashboard](https://dashboard.benchmark.camunda.cloud/d/camunda-performance/camunda-performance)
expose all of them — start there when the headline numbers don't explain what you're seeing.

### Via GHA (default — no kubectl needed)

```bash
gh workflow run camunda-load-test-metrics.yaml --repo camunda/camunda \
  --field namespace=<full-namespace-with-c8-prefix> \
  --field duration-seconds=1200

# Chain dispatch → watch → render: capture the run, wait for it,
# and pull the rendered job summary inline (no "should I wait?" prompt).
sleep 3   # let GitHub register the dispatched run
RUN_ID=$(gh run list --workflow=camunda-load-test-metrics.yaml --repo camunda/camunda \
  --limit 1 --json databaseId --jq '.[0].databaseId')
gh run watch "$RUN_ID" --repo camunda/camunda --exit-status
gh run view "$RUN_ID" --repo camunda/camunda
```

`duration-seconds` is the PromQL range each query covers (default `600` = 10 min). Pass `1200`
for a ~20-min window. Results render in the job summary; the workflow is also reusable via
`workflow_call` and exposes `results-json` for downstream jobs (e.g. analyze → delete).

### Via Grafana MCP (in Claude Code sessions — no port-forward needed)

Use `mcp__grafana__*` tools directly when running inside Claude Code. Confirm tools are active
with `mcp__grafana__check_datasources_health` at session start.

**Rule: always run `list_prometheus_metric_names` before writing any PromQL query.** Guessed
metric names return empty results silently — one discovery call eliminates all name-guess failures.

**Session startup:**
```
mcp__grafana__check_datasources_health()
  → datasourceUid: "prometheus" should show status: OK

mcp__grafana__list_prometheus_label_values(
  datasourceUid="prometheus", labelName="namespace",
  matches=[{filters: [{name: "__name__", value: "zeebe_.*", type: "=~"}]}],
  startRfc3339="now-24h")
  → lists all namespaces with recent Zeebe data
```

**Metric name discovery:**
```
mcp__grafana__list_prometheus_metric_names(datasourceUid="prometheus", regex="optimize.*", limit=50)
mcp__grafana__list_prometheus_metric_names(datasourceUid="prometheus", regex="zeebe.*process.*", limit=20)
```

For metric names and PromQL queries, see [load-tests/docs/metrics.md](../../docs/metrics.md).

**Known benchmark cluster dashboards:**
- `camunda-performance` — general throughput/latency/resource overview (namespace variable)
- `zeebe-dashboard` — deep dive into Zeebe internals (backpressure, partitions, exporters)

See `load-tests/README.md` → **Accessing metrics via Claude Code (Grafana MCP)** for setup instructions.

### Via local script (kubectl + port-forward)

Faster when you have cluster access. First port-forward the monitoring Prometheus pod, then run
the script directly against `http://localhost:9090`:

```bash
# Port-forward the monitoring Prometheus service (leave running in a separate terminal)
kubectl port-forward svc/kube-prometheus-stack-prometheus -n monitoring 9090:9090

# Then run the metrics script
cd load-tests/docs/scripts
./loadTestMetrics.sh <full-namespace-with-c8-prefix> 1200 > /tmp/results.json
```

Args: `<namespace> [duration_seconds] [endpoint] [extra_curl_opts]`. 

### Additional metrics via Prometheus (kubectl required)

When the headline metrics aren't enough, query the full set from
[`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md) directly against Prometheus. Open a
port-forward in one terminal, then run ad-hoc PromQL in another:

```bash
# Open port-forward (keep this terminal open)
kubectl port-forward -n monitoring svc/prometheus-operated 9090:9090

# Ad-hoc query — substitute <namespace> and the PromQL from metrics.md
curl -sG 'http://localhost:9090/api/v1/query_range' \
  --data-urlencode 'query=<promql>' \
  --data-urlencode 'start=<unix-timestamp>' \
  --data-urlencode 'end=<unix-timestamp>' \
  --data-urlencode 'step=15s' \
  | jq '.data.result'
```

---

## Compare against a baseline

Absolute numbers depend on cluster state, neighbour noise, and time of day — a single
feature-branch run is rarely conclusive on its own. Always compare against a reference under
matching conditions:

- **Continuous reference runs** — release, weekly, and daily load tests already cover `main` and
  stable branches with a standard scenario set. See
  [`load-tests/README.md` → Test Scenarios](https://github.com/camunda/camunda/blob/main/load-tests/README.md#test-scenarios) for what each
  variant covers (workload, secondary storage, naming pattern, validation dashboard); pick the
  closest match to your branch's scenario / secondary-storage / rate.
- **Custom baseline run** — if your branch diverges from any continuous reference (different
  scenario, exporter, replica count), dispatch a parallel run on `main` (or the merge-base) with
  identical inputs and the same `duration-seconds`.

Capture both via [Analyze metrics](#analyze-metrics) over the same window, then diff the JSON
outputs. The metrics workflow's `results-json` makes this scriptable.

---

## Stop / clean up

### Via GHA (default — no kubectl needed)

```bash
gh workflow run camunda-delete-load-test.yml --repo camunda/camunda \
  --field namespace=<full-namespace-with-c8-prefix>
```

The workflow validates the `c8-` prefix and `camunda.io/purpose=load-test` label before deleting,
and is idempotent if the namespace is already gone.


## Logs

### Default: Stackdriver (works without kubectl)

Stackdriver has the full structured logs from every pod in the namespace. Open the
**GCP Logs Explorer**, pre-scoped to the load test namespace:

Replace `<namespace>` with the full namespace name (e.g. `c8-ck-my-feature-20260427`).
```
https://console.cloud.google.com/logs/query;query=resource.labels.namespace_name%3D%22<namespace>%22?project=camunda-benchmark
```

For a GHA-dispatched run, the workflow run logs themselves are also available via:

```bash
gh run view <run-id> --repo camunda/camunda --log
```

### Optional: kubectl (when you have cluster access and want a live tail)

```bash
# Broker logs (follow)
kubectl logs -n <namespace> camunda-0 -f

# All brokers — last 50 lines each
for pod in camunda-0 camunda-1 camunda-2; do
  echo "=== $pod ===" && kubectl logs -n <namespace> $pod --tail=50
done

# Load tester logs
kubectl logs -n <namespace> -l app=starter --tail=100

# Describe a pod (scheduling / resource issues)
kubectl describe pod -n <namespace> camunda-0
```

---

## Metrics & dashboards

Replace `<namespace>` with the full namespace name (e.g. `c8-ck-my-feature-20260427`).

**Performance dashboard** — high-level overview: throughput, latency, and all key metrics (start here):

```
https://dashboard.benchmark.camunda.cloud/d/camunda-performance/camunda-performance?orgId=1&from=now-24h&to=now&timezone=browser&var-DS_PROMETHEUS=prometheus&var-namespace=<namespace>&var-pod=$__all&var-partition=$__all
```

**Zeebe dashboard** — deep dive into Zeebe internals (backpressure, partitions, exporters):

```
https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace=<namespace>
```

**Metrics reference** — Prometheus queries, SLO targets per variant, and threshold definitions: [`load-tests/docs/metrics.md`](https://github.com/camunda/camunda/blob/main/load-tests/docs/metrics.md).

