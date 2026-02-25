# CI Runbooks

This page collects information on CI incident runbooks and alerts that affect
the [C8 monorepo CI](https://github.com/camunda/camunda/actions), including runbooks on how to respond to them. For general information see the
[CI & Automation page](./ci.md).

## Incident Runbooks

This section collects useful information and links for people dealing with
incidents affecting the monorepo CI.

### Checking Important Status Pages

**When:** If you suspect a problem comes from another (external) service.

**What:** Check the following:

- [Camunda CI Platform status](https://status.camunda.cloud/) (Infra team)
- [GitHub status](https://www.githubstatus.com/) (Actions, PRs, API, Git, etc.)
- [DockerHub status](https://www.dockerstatus.com/) (Docker image push/pull)
- [Maven Central status](https://status.maven.org/) (Maven artifact up-/downloads)

### Temporarily Disable Tests To Lessen Impact

**When:** There is an incident caused by a flaky or failing test, that causes blockers
for many other contributors by e.g. always failing in the merge queue. Make sure
that test is safe to disable temporarily.

**What:** Remember these goals during an incident:

1. Stop the bleeding.
2. Find a proper cure/fix.

In context of CI, many people are waiting for their work to be merged. If there
is a workaround for instability in tests or flakiness, apply the workaround
first. Afterwards, you can invest more time into finding a well-designed solution for
the problem. This will lower stress and unblock others.

One workaround can be temporarily skipping/disabling tests:

- For Java one option is the `@Disabled` JUnit annotation. Locate the source code of the test, add
  the annotation and raise a Pull Request with your change. You always have to specify the reason
  and link to an incident/ticket for the proper solution.

### Bypassing GitHub Merge Queue

**When:** If the [merge queue](./ci.md#github-merge-queue) to `main` or other `stable/*` branches is
not working (reliably) and you need to merge a change to fix an incident. This could e.g. be the
case for fixes to the release process.

**What:**

Bypassing the merge queue (for one PR) does not mean disabling it entirely, as that would allow
other PRs e.g. from Renovate to be automatically merged even though they fail CI.

1. Create a [Pull Request](https://github.com/camunda/camunda/pulls) with your fix, explain why it
   is needed and why the merge queue needs to be bypassed.
2. Ask a repository admin to temporarily change the
   [unified-ci-merges-*-branch ruleset for the desired branch](https://github.com/camunda/camunda/settings/rules)
   to add your group (`Monorepo DevOps Team`) to the `Bypass list` and save.
3. Merge your Pull Request with admin override.
4. Ask a repository admin to temporarily change the
   [unified-ci-merges-*-branch ruleset for the desired branch](https://github.com/camunda/camunda/settings/rules)
   to remove you from the `Bypass list` and save.

## Alert Runbooks

> **Note:** Don't change the heading names below — they are required as stable links!

### Merge Queue High Failure Rate

A high rate of unsuccessful runs for a GHA job in the [merge queue](./ci.md#github-merge-queue) to
[main](https://github.com/camunda/camunda/queue/main) or `stable/8.x` branches means that PRs
cannot get merged, and engineers are blocked.

We use L2 incidents to coordinate work in mitigating and resolving the blocker. They start in
triage. Please initially verify via the Job Trends dashboard whether the job has a consistently
high failure rate. If so, confirm the incident.

If the failure rate is above 50%, please increase severity to L1.

The initial Incident Commander attribution is never 100% accurate so it is fine to re-assign the
Incident Commander role as new information becomes available.

#### Troubleshooting

First analyze the symptoms of the merge queue failures of this particular job, e.g. whether the
job runs into timeouts or a certain step/command fails.

When multiple alerts are firing simultaneously, check if they are all affected by a similar issue —
some examples could be DockerHub availability, Nexus issues, or other.

Always consider mitigation options first in order to "stop the bleeding" and unblock other
engineers quickly with temporary fixes, before starting a deep root cause analysis.

E.g. increase the timeout, use a bigger runner, disable certain unreliable steps/commands or test
cases in order to ensure CI stability again.

#### Solutions

Depends on the kind of failure.

---

### Push `main` High Failure Rate

Unsuccessful [Unified CI](./ci.md#unified-ci) GHA jobs on push to `main` branch mean that artifacts
might not get built nor uploaded. Those same jobs being green is a precondition for the
[merge queue](./ci.md#github-merge-queue) — thus a high failure rate over the last hours can indicate
a general CI instability e.g. with infrastructure or remote network services.

This can prevent artifact uploads and needs to be investigated.

#### Troubleshooting

Verify the high rate of unsuccessful GHA jobs (for push to `main`) over the last hours in the CI
Health dashboard. Drill down into the list of recent unsuccessful jobs and check their GHA logs for
common symptoms and correlate [known issues](./ci.md#issue-tracking).

Check for [GitHub Actions outages](https://www.githubstatus.com/).

#### Solutions

Depends on the kind of failure.

---

### Selfhosted Runner High Disconnect Rate

Disconnected [self-hosted runners](https://confluence.camunda.com/display/HAN/Github+Actions+Self-Hosted+Runners)
mean that GHA jobs could not run until success, producing failed builds. A high disconnect rate
over the last hours can indicate a general CI instability e.g. with infrastructure or a specific
job.

This can block developers and needs to be investigated quickly.

#### Troubleshooting

Verify the high rate of disconnected self-hosted runners over the last hours in the
[CI Health dashboard](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo).

Drill down into the list of recent jobs aborted due to self-hosted runner disconnects. For those
jobs check the following:

- The jobs' GHA logs for errors/problems/last command before the disconnect.
- Do the jobs have anything in common? Always same branch or same job etc?
  - If always same job, likely related to the job and not the infrastructure.
- Correlate the job with potential Kubernetes pod or node (`Out of memory: killed ...`) problems,
  e.g. via GKE logs or K8s events.
  - If a node is getting shut down, likely related to infrastructure.
  - If a node has Out Of Memory (OOM) kills, likely related to the job needing more resources,
    e.g. [INC-2230](https://app.incident.io/camunda/incidents/2230).

Useful GKE log explorer links (time frame/scale set/runner name should be adjusted):

- [Last 3h of all workload logs for the camunda-gcp-perf-core-8-default scale set](https://console.cloud.google.com/logs/query;query=resource.labels.cluster_name%3D%22camunda-ci%22%0Aresource.labels.namespace_name%3D%22actions-runner-controller%22%0Alabels.%22k8s-pod%2Factions_github_com%2Fscale-set-name%22%3D%22camunda-gcp-perf-core-8-default%22;cursorTimestamp=2025-02-05T11:26:22.209274192Z;duration=PT3H?project=ci-30-162810&inv=1&invt=Abovxw)
  (runner name can be added in quotes as well)
- [Last 3h of all control plane logs affecting the camunda-gcp-perf-core-8-default-t6mr7-runner-c5qwf runner](https://console.cloud.google.com/logs/query;query=resource.type%3D%22k8s_cluster%22%0Aresource.labels.project_id%3D%22ci-30-162810%22%0Aresource.labels.location%3D%22europe-west1%22%0Aresource.labels.cluster_name%3D%22camunda-ci%22%0A%22camunda-gcp-perf-core-8-default-t6mr7-runner-c5qwf%22%0A;cursorTimestamp=2025-02-04T20:45:26.437021Z;duration=PT3H?project=ci-30-162810&inv=1&invt=Abovvw)

The `/ci-problems` [ChatOps command on PRs](./ci.md#chatops) can be helpful to get links to
resources.

#### Solutions

Depends on the kind of failure. One option is to use
[self-hosted runners with `-longrunning` suffix](./ci.md#ci-self-hosted-runners) that are more stable
but also more expensive.

---

### Merge Queue High Length

A high number of PRs in the [merge queue](./ci.md#github-merge-queue) for the
[main branch](https://github.com/camunda/camunda/queue/main) means that developers must wait more
time to have their PR merged. This increases the chance of the PR becoming outdated, leading to a
poor developer experience. It may also indicate congestion and/or CI problems. Such a situation can
block progress in the monorepo and should be investigated.

#### Troubleshooting

Verify the high number of queued PRs over the last hours in the
[CI Health dashboard](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo).
Drill down into the list of PRs and check their GHA logs for common symptoms and correlate
[known issues](./ci.md#issue-tracking).

Check for [GitHub Actions outages](https://www.githubstatus.com/).

#### Solutions

Depends on the kind of failure. In general, quick mitigations/workarounds are preferred to unblock
developers before going into root cause detection.

---

### Merge Queue Eviction Rate

A high number of PR evictions from the [merge queue](./ci.md#github-merge-queue) (while waiting to be
merged into the [main branch](https://github.com/camunda/camunda/queue/main)) typically occurs due
to CI failures during the merge queue process. This results in longer wait times for developers,
increases the risk of PRs becoming outdated, and contributes to developer frustration. It can slow
down development in the monorepo and should be addressed promptly.

#### Troubleshooting

Verify the [CI Health dashboard](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo)
for a high eviction rate over the last few hours. Identify affected PRs and check their GHA logs
for common symptoms and correlate [known issues](./ci.md#issue-tracking).

Check for [GitHub Actions outages](https://www.githubstatus.com/).

#### Solutions

Depends on the kind of failure. In general, quick mitigations/workarounds are preferred to unblock
developers before going into root cause detection.

---

### Snapshot Artifacts Stale

Snapshot artifacts in DockerHub and Artifactory have not been updated for more than 3 days,
indicating potential CI pipeline issues that could block development teams from accessing the
latest development versions.

#### Troubleshooting

Check which specific artifacts are stale and investigate any recent CI failures.

#### Solutions

Fix and rerun failing artifact publishing workflows.

---

### Snapshot Artifacts Missing

Expected snapshot artifacts are completely missing from registries, or the
artifact-metadata-exporter cannot retrieve metrics, indicating CI infrastructure failures or
monitoring issues.

#### Troubleshooting

Check if this is a publishing issue by reviewing recent CI workflow failures, or a monitoring issue
by verifying artifact-metadata-exporter service status.

#### Solutions

Fix any CI pipeline publishing issues. You may need to contact the Infra team for monitoring
issues.

---

### Camunda Helm Chart Integration Test Failure

You may observe one or more of the following:

- Helm install timeout.
- GitHub Actions step failure at: `Helm - Install - Install Camunda chart` (exit code 201 =
  installation failed).
- Warnings about ignored or overridden values in Helm output.
- Pods in `CrashLoopBackOff`, failing readiness or liveness probes.
- Pods "Running" but the service is non-functional.
- Repeated restarts.
- Supplied values not compliant with required defaults (`values.yaml`).

In essence: the deployment did not complete successfully, at least one core component failed to
start, or the CI job timed out before readiness.

#### Troubleshooting

1. Retry the workflow (fast feedback) to confirm if the issue is transient.
2. Inspect the `Helm - Install - Install Camunda chart` step logs:
   - Look for hard errors (e.g. validation failures, timeouts).
   - Warnings like the following one should not be considered blocking unless they directly relate
     to a failed service startup. Example:

     ```
     warning: destination for camunda-platform.connectors.security.authentication.oidc.existingSecret is a table. Ignoring non-table value ()
     ```
3. Check the `Get failed Pods info` step:
   - Identify pods in `CrashLoopBackOff`.
   - Note restart counts.
   - Search for readiness / liveness / startup probe failures.
4. If unclear, look at container logs for the failing pod(s).

#### Solution

Possible remediation actions (select based on observed symptom):

- **If Helm install failed (exit code 201):** Re-run the job to confirm reproducibility and capture
  full failure logs.
- **If some component is in CrashLoopBackOff:** Inspect logs and redirect the issue to the medic
  responsible for that component.
- **If components are running but non-functional:** Validate operational behavior (not just status);
  look for configuration alignment with `values.yaml`.

---

### Check Licenses Workflow High Failure Rate

A high failure rate in `check-licenses.yml` may indicate instability in license check jobs
(`analyze/single-app`, `analyze/optimize`, or other `analyze/*` jobs). This does not block merges,
but it may prevent license issues from being detected/addressed and should be investigated promptly.

> While some failures can be expected for pull requests introducing new licensing issues, `main`
> and `stable` branches should stay free of errors.

#### Troubleshooting

1. Verify failures in the [CI Health dashboard](https://dashboard.int.camunda.com/d/bdmo5l8puugaoc/ci-health-camunda-camunda-monorepo?orgId=1).
2. Review logs of failed jobs in GitHub Actions:
   - License validation or dependency scan errors
   - Network issues
   - Runner timeouts or OOM errors
3. Check for recent merges modifying configs (e.g. `check-licenses.yml` and `fossa.yml`).
4. Confirm GitHub Actions or FOSSA services are operational.

#### Solutions

- Retry failed jobs to rule out transient issues.
- Fix any broken dependencies or config errors.
- Escalate to Infra if failures persist.

---

### Preview Environment Smoke Test Failure

The [Preview Environment Smoke Test](https://github.com/camunda/camunda/actions/workflows/preview-env-smoke-test.yml)
runs weekly on Mondays to verify that preview environment deployments are working correctly. A
failure indicates a potential issue with the preview environment infrastructure that could affect
developers using the `deploy-preview` label on their PRs.

#### Troubleshooting

1. Check the failed workflow run linked in the Slack notification:
   - Identify which job failed: `Build Camunda`, `Build Optimize`, or `Deploy Preview Environment`
2. For deployment failures:
   - Check [ArgoCD](https://argocd.int.camunda.com/) for the smoke test app (named
     `camunda-smoke8x-<run_number>`).
     - Direct URL can be found in the workflow run summary.
   - Look for Kubernetes resource issues (pods not starting, image pull errors, etc.)
   - Verify the preview environment Helm chart in `.ci/preview-environments/charts/c8sm/`
3. Check for transient issues:
   - Re-run the workflow manually via `workflow_dispatch` to confirm whether the issue persists.

#### Solutions

- **Component build/startup failure:** Investigate recent changes and eventually delegate to the
  corresponding [Medic DRI](https://confluence.camunda.com/spaces/HAN/pages/245403757/C8+Monorepo+CI+Processes#C8MonorepoCIProcesses-Driveresolutionofincidents).
- **Infrastructure issue (ArgoCD / K8s cluster):** Contact the Infra Team (`@infra-medic`).
- **Helm chart issue:** Review recent changes to `.ci/preview-environments/` and the
  [camunda-platform Helm chart](https://github.com/camunda/camunda-platform-helm).
  - It may be resolved by upgrading the Helm chart or by fixing `values.yml` and/or other
    configuration files.
  - For issues with the camunda-platform Helm chart, help can be sought from the Distribution team
    (`@distro-medic`).
- **Playwright Smoke Tests issue:** Contact the QA team via the `#ask-qa` Slack channel and tag
  the `@test-automation-team`.

#### Cleanup

If the smoke test environment was not torn down (due to a failure or `skip-teardown`), it will be
automatically cleaned up by the
[preview-env-clean](https://github.com/camunda/camunda/actions/workflows/preview-env-clean.yml)
workflow after 12 hours.

To manually clean up:

1. Go to [ArgoCD](https://argocd.int.camunda.com/applications?labels=preview%3Dsmoke-test).
2. Delete the corresponding application with labels `preview=smoke-test` and
   `repo=camunda_camunda` (app name: `camunda-smoke8x-<run-number>`), which can be found in the
   workflow run logs.

---

### Unified CI High Job Runtime

A successful but slow Unified CI GHA job on push, PRs and merge queue to `main` or `stable/8.x`
branches makes all engineers wait longer and indicates a
[Runtime SLO breach](https://dashboard.int.camunda.com/d/5cd87b35-d2f2-4686-b3ab-2ef4de504364/ci-health-c8-monorepo?orgId=1).

We use L3 incidents to coordinate timely improvement work to speed up the CI. They start in triage.
Please initially verify via the Job Trends dashboard whether the job is consistently breaching the
SLO and/or trending upwards. If so, confirm the incident.

The initial Incident Commander attribution is never 100% accurate so it is fine to re-assign the
Incident Commander role as new information becomes available.

#### Solutions

As Incident Commander please analyze the slow Unified CI job and figure out how to make it faster,
e.g. by eliminating bottlenecks, caching, or splitting it into multiple parallel jobs.
