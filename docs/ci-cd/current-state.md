# Current State Assessment

> Back to [CI/CD Improvement Plan](./README.md)

## Key Numbers

| Metric | Value |
|--------|-------|
| GitHub Actions workflow files | 121 |
| Maven modules (total pom.xml) | 144 (22 aggregators + 122 buildable) |
| Top-level modules in root POM | 36 |
| PR wall-clock CI time | ~50-55 min |
| Main branch CI time (with deploys) | ~70-75 min |
| Redundant full Maven builds per PR | ~75 (across 22 workflow files) |
| Legacy duplicate workflows | 5+ (running in parallel with unified CI) |
| Test files with `@Disabled` / `@Ignore` | 139 |
| Tests blanket-disabled on AWS OpenSearch | 97 files |
| `Thread.sleep()` in test code | 52 files |
| Hardcoded Playwright `waitForTimeout` | 15 occurrences |
| `rerunFailingTestsCount` | 2-3 retries on every test job |
| JUnit 4 test files remaining | ~804 |
| Maven build cache | Installed but **disabled** |
| `.mvn/maven.config` | Does not exist |

## Pipeline Architecture Overview

The repository has a unified CI entry point (`ci.yml`) that fans out into component sub-workflows:

```
ci.yml (main entry point)
  +-- detect-changes (path-based filtering)
  +-- [Tier 0] Linting: actionlint, commitlint, spotless, openapi, renovate, protobuf
  +-- [Tier 1] Build: build-platform-frontend
  +-- [Tier 2] Unit Tests: general-unit-tests, zeebe-unit-tests (7x matrix)
  +-- [Tier 2] Integration Tests: integration-tests (12x matrix), database ITs (7 variants)
  +-- [Tier 2] Sub-workflows:
  |   +-- ci-operate.yml (10+ jobs)
  |   +-- ci-tasklist.yml (10+ jobs)
  |   +-- ci-optimize.yml (4+ jobs)
  |   +-- ci-zeebe.yml (8+ jobs)
  |   +-- ci-client-components.yml (1 job)
  +-- [Gate] check-results (requires ALL jobs)
  +-- [Deploy] deploy-snapshots, deploy-docker (main/stable only)
```

**Total effective job count per full PR build: 60+ parallel/sequential jobs.**

## What's Already Done Well

- **Smart path-based change detection** — only affected component tests run on PRs
- **Concurrency control** — cancels superseded PR builds, preserves main builds
- **Flaky test observability pipeline** — BigQuery + Grafana + daily/weekly Slack + PR comments + team medic escalation (industry-leading)
- **Maven cache** design is solid — split local repo, modifier-based keys, restore-only on PRs
- **Playwright container images** — no browser installation overhead
- **`-Dquickly` cascading skip system** — well-designed for local dev inner loop
- **ChatOps `/ci-problems`** command for self-service CI failure analysis
- **Maven Wrapper** (`mvnw`) ensures reproducible Maven versions
