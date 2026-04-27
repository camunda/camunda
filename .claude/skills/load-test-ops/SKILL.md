---
name: load-test-ops
description: Trigger, monitor, update, profile, and stop Camunda load tests using gh CLI and kubectl directly — no MCP server required
---

# Camunda Load Test Operations

## Prerequisites

- `gh` CLI authenticated (`gh auth status`)
- Optional: active kubectl/Teleport session for cluster-direct operations

When kubectl is available, prefer it for status and config inspection — it's faster
and more accurate than polling GHA. When kubectl is not available, fall back to
`gh run view` and GHA logs.

---

## Namespace conventions

- Format: `c8-<name>` — always prefixed with `c8-`
- Recommended `<name>`: `<branch-slug>-<YYYYMMDD>`, e.g. `my-feature-20260424`
- Grafana: `https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace=<namespace>`

Namespaces carry these labels (set by `newLoadTest.sh`):

| Label | Value |
|---|---|
| `camunda.io/purpose` | `load-test` |
| `created-by` | GitHub actor (sanitized) |
| `deadline-date` | `YYYY-MM-DD` (TTL expiry) |

---

## Start a load test

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=<name-without-c8-prefix> \
  --field ttl=1 \
  --field scenario=typical \
  --field secondary-storage-type=elasticsearch \
  --field enable-optimize=false
```

**All `--field` options:**

| Field | Default | Notes |
|---|---|---|
| `ref` | `main` | Branch, tag, or commit SHA to test |
| `name` | required | Namespace suffix — workflow prepends `c8-` |
| `ttl` | `1` | Days before namespace auto-deleted |
| `scenario` | `max` | `typical`, `realistic`, `latency`, `max`, `archiver`, `custom` |
| `secondary-storage-type` | `elasticsearch` | `elasticsearch`, `opensearch`, `postgresql`, `mysql`, `mariadb`, `mssql`, `oracle`, `none` |
| `enable-optimize` | `false` | `true` / `false` |
| `platform-helm-values` | — | Arbitrary `--set` flags for the platform chart |
| `load-test-load` | — | Helm args for the load test chart (only for `scenario=custom`) |
| `stable-vms` | `false` | Deploy to non-spot VMs |
| `perform-read-benchmarks` | `false` | Enable read load on secondary storage |
| `build-frontend` | `false` | Build frontend from `ref` before deploying |
| `orchestration-tag` | — | Pin orchestration to a Docker Hub tag (incompatible with `reuse-tag`) |
| `optimize-tag` | — | Pin Optimize image tag |
| `identity-tag` | — | Pin Identity image tag |
| `connectors-tag` | — | Pin Connectors image tag |
| `reuse-tag` | — | Skip Docker build, reuse an existing internal registry tag |

After dispatching, find the run:

```bash
gh run list --workflow=camunda-load-test.yml --repo camunda/camunda --limit 5
```

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
# Via kubectl labels
kubectl get namespaces -l camunda.io/purpose=load-test,created-by=$(gh api /user --jq .login)

# Via GHA
gh run list --workflow=camunda-load-test.yml --repo camunda/camunda \
  --actor $(gh api /user --jq .login) --limit 20
```

---

## Inspect configuration

**Read default Helm values (to know what keys to override):**

```bash
# Platform chart defaults
cat load-tests/camunda-platform-values.yaml

# Load test chart defaults
cat load-tests/load-test-values.yaml
```

**Read what's actually deployed (requires kubectl):**

```bash
helm get values <namespace> -n <namespace>
helm get values <namespace>-test -n <namespace>
```

**Common platform overrides for `platform-helm-values`:**

```
--set orchestration.resources.limits.memory=4Gi
--set orchestration.clusterSize=1
--set orchestration.partitionCount=1
--set orchestration.javaOpts='-Xmx3g -XX:+UseZGC'
--set elasticsearch.master.replicaCount=1
```

---

## Update / redeploy

**Via GHA — rebuild image from branch:**

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=<name-without-c8-prefix> \
  --field ttl=<original-ttl> \
  --field scenario=<original-scenario>
```

**Via GHA — reuse existing image (skip Docker build):**

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=<name-without-c8-prefix> \
  --field reuse-tag=<image-tag> \
  --field platform-helm-values="--set orchestration.resources.limits.memory=4Gi"
```

Find the current image tag:

```bash
kubectl get pods -n <namespace> -o jsonpath='{.items[0].spec.containers[0].image}'
```

**Via Makefile — direct Helm upgrade (requires kubectl):**

```bash
cd load-tests/setup/<namespace>
make install-platform additional_platform_configuration="--set orchestration.resources.limits.memory=4Gi"
make install-load-test additional_load_test_configuration="--set starter.rate=200"
```

The Makefile approach is faster for iterative config changes — no Docker build, no GHA queue.

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

After ~5 min, download flamegraph artifacts from the GHA run:

```bash
gh run download <run-id> --repo camunda/camunda
```

Artifact names: `flamegraph-cpu-camunda-0`, `flamegraph-wall-camunda-1`, `flamegraph-alloc-camunda-2`

---

## Stop / clean up

```bash
# Trigger TTL-based cleanup (deletes ALL namespaces expiring on or before <date>)
gh workflow run camunda-load-test-clean-up.yml --repo camunda/camunda \
  --field date=$(date -u +%Y-%m-%d)
```

**WARNING:** This deletes every namespace whose `deadline-date` label is ≤ today, not only the one you have in mind.

Direct deletion (requires kubectl):

```bash
kubectl delete namespace <namespace>
```

---

## Logs and debugging

```bash
# Broker logs
kubectl logs -n <namespace> camunda-0 --tail=100 -f

# All broker logs
for pod in camunda-0 camunda-1 camunda-2; do
  echo "=== $pod ===" && kubectl logs -n <namespace> $pod --tail=50
done

# Load tester logs
kubectl logs -n <namespace> -l app=starter --tail=100

# Describe a pod (resource issues, scheduling)
kubectl describe pod -n <namespace> camunda-0
```
