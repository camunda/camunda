# Camunda Load Tests

Load tests validate the reliability and performance of Camunda 8 across releases and development branches. They can be created via automated GitHub Actions workflows or manually (via Makefiles) on a GKE cluster (`camunda-benchmark-prod`), deploying the [Camunda Platform Helm Chart](https://github.com/camunda/camunda-platform-helm) and a custom [load test Helm chart](https://github.com/camunda/camunda-load-tests-helm).

For background on goals, test variants, and observability, see the [reliability testing documentation](../docs/testing/reliability-testing.md).

## Directory Layout

|   Directory    |                                                                            Description                                                                            |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `setup/`       | Makefiles, shell scripts, and Helm values for deploying load tests ([README](setup/README.md))                                                                    |
| `load-tester/` | Java load test applications (starters and workers) ([README](load-tester/README.md))                                                                              |
| `docs/`        | Additional documentation: [directory structure history](docs/directory-structure.md), [scripts](docs/scripts/README.md), [past failures](docs/failures/README.md) |

## Quick Start

Prerequisites: access to the GKE benchmark cluster via [Teleport](https://camunda.teleport.sh).

### Via GitHub Actions (recommended)

Trigger the [Camunda load test workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml) via the UI. Select a branch, name your test, and choose a scenario.

### Via Makefile (manual)

```bash
cd load-tests/setup
./newLoadTest.sh <name> <storage-type> <ttl-days> <enable-optimize>
cd <name>
make install
```

See the [setup README](setup/README.md) for full details.

## Workflow Overview

All automated load tests flow through `camunda-load-test.yml`, which builds images and deploys via the same Makefiles used for manual deployments.

```mermaid
graph TD
    subgraph "Scheduled Triggers"
        SCHEDULED["camunda-scheduled-release-<br/>load-tests.yml<br/><i>Weekdays 02:00 UTC</i>"]
        DAILY["camunda-daily-load-tests.yml<br/><i>Weekdays 02:00 UTC</i>"]
        WEEKLY["camunda-weekly-load-tests.yml<br/><i>Monday 01:00 UTC</i>"]
        ROLLING["zeebe-update-long-running-<br/>migrating-benchmark.yaml<br/><i>Monday 00:00 UTC</i>"]
        CLEANUP["camunda-load-test-clean-up.yml<br/><i>Daily 04:00 UTC</i>"]
    end

    subgraph "Event Triggers"
        PR["camunda-pr-load-test.yaml<br/><i>PR label: benchmark</i>"]
        ADHOC["Manual workflow_dispatch"]
    end

    subgraph "Reusable Workflows"
        RELEASE["camunda-release-load-test.yaml<br/><i>workflow_call</i>"]
        CORE["camunda-load-test.yml<br/><i>workflow_call + workflow_dispatch</i>"]
        VERIFY["camunda-verify-and-cleanup-<br/>load-test.yml<br/><i>workflow_call</i>"]
        PROFILE["profile-load-test.yml<br/><i>workflow_call + workflow_dispatch</i>"]
    end

    subgraph "Deployment Layer"
        MAKEFILE["load-tests/setup/<br/><b>Makefile</b><br/><i>make install / make clean</i>"]
    end

    subgraph "Infrastructure"
        GKE["GKE Cluster<br/>camunda-benchmark-prod"]
    end

    SCHEDULED -- "one job per stable branch<br/>+ main, official images" --> RELEASE
    SCHEDULED -- "verify + delete namespace" --> VERIFY
    DAILY -- "scenario: max" --> CORE
    WEEKLY -- "4 parallel calls:<br/>typical, realistic,<br/>rdbms-realistic, latency" --> CORE
    ROLLING -- "latest release tag<br/>custom helm values" --> CORE
    RELEASE -- "scenario: realistic<br/>orchestration-tag" --> CORE
    PR -- "scenario: max" --> CORE
    PR -- "after 15min wait" --> PROFILE
    ADHOC --> CORE
    ADHOC --> RELEASE

    CORE -- "newLoadTest.sh + make install" --> MAKEFILE
    MAKEFILE -- "Helm install" --> GKE
    PROFILE -- "async-profiler" --> GKE
    VERIFY -- "kubectl wait + delete" --> GKE
    CLEANUP -- "kubectl delete expired ns" --> GKE
```

### Schedule

|       Time       |                       Workflow                       | Frequency |
|------------------|------------------------------------------------------|-----------|
| 00:00 UTC Monday | `zeebe-update-long-running-migrating-benchmark.yaml` | Weekly    |
| 01:00 UTC Monday | `camunda-weekly-load-tests.yml`                      | Weekly    |
| 02:00 UTC Mon-Fri| `camunda-scheduled-release-load-tests.yml`           | Weekdays  |
| 04:00 UTC        | `camunda-daily-load-tests.yml`                       | Daily     |
| 04:00 UTC        | `camunda-load-test-clean-up.yml`                     | Daily     |

For detailed inputs, triggers, and job definitions, see each workflow's header comments in [`.github/workflows/`](../.github/workflows/). For branch-specific path differences, see [directory structure history](docs/directory-structure.md).
