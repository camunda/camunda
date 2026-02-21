# GitHub Actions Workflow Inventory

> Back to [CI/CD Improvement Plan](../README.md)

## Primary CI Workflows

| # | File | Name | Trigger |
|---|------|------|---------|
| 1 | `ci.yml` | Camunda CI | push (main/stable), PR, merge_group, schedule (daily 06:00 UTC) |
| 2 | `ci-zeebe.yml` | Zeebe CI | workflow_call from ci.yml |
| 3 | `ci-operate.yml` | Operate CI | workflow_call from ci.yml |
| 4 | `ci-tasklist.yml` | Tasklist CI | workflow_call from ci.yml |
| 5 | `ci-optimize.yml` | Optimize CI | workflow_call from ci.yml |
| 6 | `ci-client-components.yml` | Client Components CI | workflow_call from ci.yml |

## Reusable Workflows

| # | File | Called By | Times Called |
|---|------|----------|-------------|
| 7 | `ci-database-integration-tests-reusable.yml` | ci.yml, scheduled workflows | 7+ |
| 8 | `ci-webapp-run-ut-reuseable.yml` | Operate/Tasklist/Optimize CI | 6 |
| 9 | `operate-ci-build-reusable.yml` | Legacy Operate CI | 1 |
| 10 | `operate-ci-test-reusable.yml` | Legacy Operate CI | 1 |
| 11 | `tasklist-ci-build-reusable.yml` | Tasklist workflows | 1 |
| 12 | `tasklist-ci-test-reusable.yml` | Tasklist workflows | 1 |
| 13 | `optimize-ci-build-reusable.yml` | Legacy Optimize workflows | 1 |
| 14 | `generate-snapshot-docker-tag-and-concurrency-group.yml` | ci.yml, deploy workflows | Multiple |

## Legacy/Duplicate Workflows (Candidates for Removal)

| # | File | Name | Overlap |
|---|------|------|---------|
| 15 | `operate-ci.yml` | [Legacy] Operate | ci.yml -> ci-operate.yml |
| 16 | `operate-docker-tests.yml` | [Legacy] Operate / Docker | ci.yml -> docker-checks |
| 17 | `optimize-ci-core-features.yml` | [Legacy] Optimize Core Features | ci.yml -> ci-optimize.yml |
| 18 | `optimize-ci-data-layer.yml` | [Legacy] Optimize Data Layer | ci.yml -> database ITs |
| 19 | `optimize-e2e-tests-sm.yml` | [Legacy] Optimize E2E SM | Nightly E2E |

## Scheduled/Nightly Workflows

| # | File | Schedule | Purpose |
|---|------|----------|---------|
| 20 | `zeebe-daily-qa.yml` | Weekdays 01:00 UTC | QA testbench across stable branches |
| 21 | `camunda-daily-load-tests.yml` | Daily 04:00 UTC | Max load test |
| 22 | `camunda-weekly-load-tests.yml` | Weekly | Extended load tests |
| 23 | `zeebe-weekly-e2e.yml` | Weekly | E2E tests |
| 24 | `zeebe-search-integration-tests.yml` | Weekdays 05:00 UTC | Multi-version ES/OS tests (6 matrix) |
| 25 | `zeebe-rdbms-integration-tests.yml` | Weekdays 05:00 UTC | Multi-vendor RDBMS tests (13 matrix) |
| 26 | `zeebe-version-compatibility.yml` | Daily 06:00 UTC | Rolling update compatibility |
| 27 | `statistics-daily.yml` | Daily | Flaky test stats + Slack |
| 28 | `statistics-weekly.yml` | Weekly | Weekly stats + Slack |
