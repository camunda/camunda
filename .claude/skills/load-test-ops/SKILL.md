---
name: load-test-ops
description: Trigger, monitor, update, profile, and stop Camunda load tests using gh CLI and kubectl directly — no MCP server required
---

# Camunda Load Test Operations

## Prerequisites check

`gh` CLI must always be authenticated (`gh auth status`).

**Starting a load test always goes through GHA** — it needs to build a Docker image from the
branch before deploying. There is no local shortcut for this step.

For **updates to a running cluster**, check kubectl access first:

```bash
kubectl get nodes 2>/dev/null && echo "kubectl available" || echo "use GHA fallback"
```

**If kubectl is available:** use `newLoadTest.sh` + Makefile for direct Helm upgrades — faster, no GHA queue, no image rebuild.
**If not:** fall back to `gh workflow run` with `reuse-tag` to skip the Docker build.

---

## Namespace conventions

- Format: `c8-<initials>-<slug>-<YYYYMMDD>` — always prefixed with `c8-`
- Example: `c8-ck-my-feature-20260427` (ck = ChrisKujawa)
- The `name` field passed to GHA/scripts is the part **without** `c8-` prefix

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
  --field name=ck-<slug>-<YYYYMMDD>
```

For all available inputs see the `inputs:` block in `.github/workflows/camunda-load-test.yml`.

After dispatching, find the run:

```bash
gh run list --workflow=camunda-load-test.yml --repo camunda/camunda --actor $(gh api /user --jq .login) --limit 5
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

```bash
# Read default Helm values (know what keys to override)
cat load-tests/camunda-platform-values.yaml
cat load-tests/load-test-values.yaml

# Read what's actually deployed (requires kubectl)
helm get values <namespace> -n <namespace>
helm get values <namespace>-test -n <namespace>
```

---

## Update / redeploy

**Check kubectl first** (see Prerequisites). Then choose:

### With kubectl (direct — preferred for iterative changes)

```bash
cd load-tests/setup

# Create namespace if it doesn't exist yet
./newLoadTest.sh <namespace> <secondary-storage> <ttl-days> <enable-optimize>

# Upgrade platform Helm chart
cd <namespace>
make install-platform additional_platform_configuration="--set orchestration.resources.limits.memory=4Gi"

# Upgrade load test Helm chart
make install-load-test additional_load_test_configuration="--set starter.rate=200"
```

### Without kubectl (via GHA)

Rebuild image from branch:

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=ck-<slug>-<YYYYMMDD>
```

Reuse existing image (skip Docker build):

```bash
# Find current image tag
kubectl get pods -n <namespace> -o jsonpath='{.items[0].spec.containers[0].image}'

gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field ref=<branch> \
  --field name=<name-without-c8-prefix> \
  --field reuse-tag=<image-tag> \
  --field platform-helm-values="--set orchestration.resources.limits.memory=4Gi"
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

## Stop / clean up

**With kubectl (preferred):**

```bash
cd load-tests/setup
./deleteLoadTest.sh <full-namespace-with-c8-prefix>
```

This deletes the namespace and removes the local setup directory for it.

**Without kubectl — GHA TTL cleanup:**

```bash
# WARNING: deletes ALL namespaces whose deadline-date label is ≤ today
gh workflow run camunda-load-test-clean-up.yml --repo camunda/camunda \
  --field date=$(date -u +%Y-%m-%d)
```

---

## Logs

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

## Monitoring dashboards

Replace `<namespace>` with the full namespace name (e.g. `c8-ck-my-feature-20260427`).

**Performance dashboard** — high-level overview: throughput, latency, and all key metrics (start here):
```
https://dashboard.benchmark.camunda.cloud/d/camunda-performance/camunda-performance?orgId=1&from=now-24h&to=now&timezone=browser&var-DS_PROMETHEUS=prometheus&var-namespace=<namespace>&var-pod=$__all&var-partition=$__all
```

**Zeebe dashboard** — deep dive into Zeebe internals (backpressure, partitions, exporters):
```
https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe?var-namespace=<namespace>
```
