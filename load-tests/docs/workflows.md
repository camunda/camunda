# Load Test Workflows

Detailed reference for all load test GitHub Actions workflows. For a visual overview and schedule, see the [load-tests README](../README.md).

## Makefile Layer

All workflows ultimately deploy through Makefiles in `load-tests/setup/`. This is also the interface for manual ad-hoc deployments.

### How it works

1. `newLoadTest.sh` creates a K8s namespace, copies the Makefile template from `load-tests/setup/default/`, and configures it for the namespace
2. `make install` (or `make install-stable` for non-spot VMs) deploys everything via Helm

### Key Make targets

|                      Target                      |                          Description                           |
|--------------------------------------------------|----------------------------------------------------------------|
| `make install`                                   | Full deployment: credentials, platform, load test, ES exporter |
| `make install-stable`                            | Same as `install` but with non-preemptible VMs                 |
| `make install-platform`                          | Deploy Camunda Platform Helm chart only                        |
| `make install-load-test`                         | Deploy load test Helm chart (starters + workers) only          |
| `make clean`                                     | Remove all Helm releases and PVCs                              |
| `make template`                                  | Dry-run Helm template for validation                           |
| `make realistic` / `typical` / `latency` / `max` | Scenario shortcuts with predefined configs                     |

### Parameters

```bash
# secondary_storage: elasticsearch, opensearch, postgresql, none
# enable_optimize: deploy Optimize
# additional_platform_configuration: extra --set flags for platform chart
# additional_load_test_configuration: extra --set flags for load test chart
make install \
  secondary_storage=elasticsearch \
  enable_optimize=true \
  additional_platform_configuration="" \
  additional_load_test_configuration=""
```

### Manual ad-hoc usage

The same Makefile is used for manual deployments outside of GHA:

```bash
cd load-tests/setup
./newLoadTest.sh my-test elasticsearch 7 true
cd my-test
make install
# ... later ...
make clean
```

A cloud variant exists at `load-tests/setup/cloud-default/Makefile` for SaaS load tests, which only manages load test pods (not the platform itself).

## Workflow Reference

### camunda-load-test.yml

The central reusable workflow. All load test creation routes through this workflow.

**Triggers:** `workflow_dispatch`, `workflow_call`

**Inputs:**

|            Input             |  Type   |     Default     |                             Description                              |
|------------------------------|---------|-----------------|----------------------------------------------------------------------|
| `ref`                        | string  | `main`          | Git reference for Docker image build                                 |
| `name`                       | string  | *required*      | Load test name (becomes K8s namespace with `c8-` prefix)             |
| `ttl`                        | number  | `1`             | Days until namespace is auto-deleted                                 |
| `reuse-tag`                  | string  | `""`            | Reuse existing Docker image tag (skips build)                        |
| `use-official-docker-images` | boolean | `false`         | Use Docker Hub images instead of building                            |
| `scenario`                   | choice  | `custom`        | Workload variant: `custom`, `latency`, `realistic`, `typical`, `max` |
| `load-test-load`             | string  |                 | Helm arguments for load test components                              |
| `platform-helm-values`       | string  | `""`            | Additional Helm chart values                                         |
| `perform-read-benchmarks`    | boolean | `false`         | Enable read benchmarks                                               |
| `stable-vms`                 | boolean | `false`         | Deploy to non-spot VMs                                               |
| `enable-optimize`            | boolean | `true`          | Enable Optimize                                                      |
| `build-frontend`             | boolean | `false`         | Build frontend                                                       |
| `secondary-storage-type`     | choice  | `elasticsearch` | `elasticsearch`, `opensearch`, `postgresql`, `none`                  |
| `publish`                    | string  |                 | Where to publish results: `slack`, `comment` (workflow_call only)    |

**Key jobs:** `calculate-image-tag` -> `build-camunda-image` -> `build-load-test-images` -> `calculate-scenario-config` -> `deploy-cluster-under-test`

The deploy job runs `newLoadTest.sh` to create the namespace and then `make install` to deploy via Helm.

---

### camunda-scheduled-release-load-tests.yml

Orchestrates daily release load tests across all active stable branches and main. Each branch calls `camunda-release-load-test.yaml` at the respective branch ref, then verifies the deployment and cleans up.

**Triggers:** `schedule` (daily 04:00 UTC), `workflow_dispatch`

**Inputs:** None (release tags are hardcoded per branch in the workflow file).

**Job chain:**
1. One `release-load-test-<version>` job per branch — calls `camunda-release-load-test.yaml@stable/<version>` with hardcoded name and tag
2. One `verify-and-cleanup-<version>` job per branch — calls `camunda-verify-and-cleanup-load-test.yml` to wait for pods and then delete namespace
3. `notify-on-success` / `notify-on-failure` — Slack notification with per-branch results

**Maintenance:** When a new minor version is released or a branch is deprecated, this workflow must be updated (add/remove jobs and update the notify conditions).

---

### camunda-release-load-test.yaml

Creates a load test for official release images. Called by `camunda-scheduled-release-load-tests.yml` and can be triggered manually for ad-hoc release testing.

**Triggers:** `workflow_dispatch`, `workflow_call`

**Inputs:**

| Input  |  Type  |  Default   |      Description      |
|--------|--------|------------|-----------------------|
| `name` | string | *required* | Load test name        |
| `tag`  | string | *required* | Release tag to deploy |

**Job chain:**
1. `sanitize-inputs` — validates and sanitizes name/tag
2. `create-load-test` — calls `camunda-load-test.yml` with `use-official-docker-images: true`, scenario: `realistic`
3. `notify-on-failure` — sends a Slack notification if the workflow fails

Automated verification of the deployment and namespace cleanup are handled by `camunda-scheduled-release-load-tests.yml` via `camunda-verify-and-cleanup-load-test.yml`.

---

### camunda-verify-and-cleanup-load-test.yml

Verifies a load test deployment is healthy and deletes the namespace. Used by `camunda-scheduled-release-load-tests.yml`.

**Triggers:** `workflow_call`

**Inputs:**

|    Input    |  Type  |  Default   |            Description             |
|-------------|--------|------------|------------------------------------|
| `namespace` | string | *required* | K8s namespace to verify and delete |

**Steps:** Waits for all pods to be ready and checks gateway connectivity, then deletes the namespace regardless of outcome.

---

### camunda-daily-load-tests.yml

Runs a daily max-load stress test against main.

**Triggers:** `schedule` (daily 04:00 UTC), `workflow_dispatch`

**Inputs:**

|    Input    |  Type  | Default |           Description           |
|-------------|--------|---------|---------------------------------|
| `reuse-tag` | string | `""`    | Reuse existing Docker image tag |

**Job chain:**
1. `benchmark-data` — generates name: `medic-daily-YYYY-MM-DD-<sha>`
2. `setup-max-load-test` — calls `camunda-load-test.yml` with scenario `max`
3. `notify` — Slack notification on failure

---

### camunda-weekly-load-tests.yml

Runs four parallel endurance tests weekly against main.

**Triggers:** `schedule` (Monday 01:00 UTC), `workflow_dispatch`

**Inputs:**

|    Input    |  Type  | Default |           Description           |
|-------------|--------|---------|---------------------------------|
| `reuse-tag` | string | `""`    | Reuse existing Docker image tag |
| `ttl`       | number | `28`    | Days until namespace deletion   |

**Job chain:**
1. `test-data` — generates image tag: `medic-y-YYYY-cw-WW-<sha>`
2. `build-camunda-image` + `build-load-test-images` — conditional, skipped if `reuse-tag` provided
3. Four parallel load tests, each calling `camunda-load-test.yml`:
- `setup-typical-load-test` — scenario: `typical`
- `setup-realistic-test` — scenario: `realistic`
- `setup-rdbms-realistic-load-test` — scenario: `realistic`, secondary-storage: `postgresql`
- `setup-latency-test` — scenario: `latency`
4. `notify` — Slack notification on failure

---

### camunda-pr-load-test.yaml

Creates a load test when a PR is labeled with `benchmark`. Profiles the cluster and comments results on the PR.

**Triggers:** `pull_request` (events: `labeled`, `unlabeled`, `synchronize`, `closed`)

**Job chain (on label add):**
1. `sanitize-branch-name` — sanitizes PR branch name
2. `create-benchmark` — calls `camunda-load-test.yml` with scenario `max`
3. `await-benchmark` — waits 15 minutes
4. `run-profiling` — calls `profile-load-test.yml`
5. `comment-pr` — posts flamegraph results as PR comment

**Job chain (on label remove or PR close):**
1. `sanitize-branch-name` — sanitizes PR branch name
2. `delete-benchmark` — deletes the namespace

---

### profile-load-test.yml

Profiles a running load test cluster using async-profiler. Produces flamegraph artifacts.

**Triggers:** `workflow_dispatch`, `workflow_call`

**Inputs:**

|       Input        |  Type  |  Default   |             Description             |
|--------------------|--------|------------|-------------------------------------|
| `name`             | string | *required* | Load test namespace name            |
| `pod`              | string | `""`       | Pod to profile (empty = all 3 pods) |
| `profiler_options` | string | `""`       | Additional async-profiler flags     |

**Jobs (mutually exclusive):**
- `profile-all-pods` — profiles 3 pods in parallel (cpu, wall, alloc events)
- `profile-single-pod` — profiles one pod with cpu event

**Artifacts:** Uploads `flamegraph-{event}-{pod}` artifacts.

---

### camunda-load-test-clean-up.yml

Deletes expired load test namespaces based on TTL labels.

**Triggers:** `schedule` (daily 04:00 UTC), `workflow_dispatch`

**Inputs:**

| Input  |  Type  | Default |                      Description                       |
|--------|--------|---------|--------------------------------------------------------|
| `date` | string | *today* | Delete namespaces with deadline on or before this date |

**Jobs (parallel):**
- `delete-load-tests-legacy` — cleans up legacy GKE namespaces (Google Cloud auth)
- `delete-load-tests` — cleans up new infrastructure namespaces (Teleport auth)

Both post Slack notifications listing deleted namespaces.

---

### zeebe-update-long-running-migrating-benchmark.yaml

Updates the rolling release benchmark weekly with the latest release tag.

**Triggers:** `schedule` (Monday 00:00 UTC), `workflow_dispatch`

**Job chain:**
1. `fetch-release` — fetches latest release tag from GitHub API
2. `update-long-running-migrating-benchmark` — calls `camunda-load-test.yml` with name `release-rolling` and custom Helm values for realistic load

## Branch Availability

For how workflow file names differ across stable branches, see [directory-structure.md](directory-structure.md).

|                       Workflow                       |           stable/8.6-8.7            |             stable/8.8              |     stable/8.9+ / main      |
|------------------------------------------------------|-------------------------------------|-------------------------------------|-----------------------------|
| `camunda-load-test.yml`                              | `zeebe-benchmark.yml` (renamed)     | `zeebe-benchmark.yml` (renamed)     | `camunda-load-test.yml`     |
| `camunda-pr-load-test.yaml`                          | `zeebe-pr-benchmark.yaml` (renamed) | `zeebe-pr-benchmark.yaml` (renamed) | `camunda-pr-load-test.yaml` |
| `camunda-scheduled-release-load-tests.yml`           | absent                              | absent                              | present                     |
| `camunda-verify-and-cleanup-load-test.yml`           | absent                              | absent                              | present                     |
| `camunda-daily-load-tests.yml`                       | present                             | present                             | present                     |
| `camunda-weekly-load-tests.yml`                      | present                             | present                             | present                     |
| `camunda-release-load-test.yaml`                     | present                             | present                             | present                     |
| `camunda-load-test-clean-up.yml`                     | present                             | present                             | present                     |
| `profile-load-test.yml`                              | present                             | present                             | present                     |
| `zeebe-update-long-running-migrating-benchmark.yaml` | present                             | present                             | present                     |

