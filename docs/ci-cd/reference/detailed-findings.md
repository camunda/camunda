# Detailed Findings by Area

> Back to [CI/CD Improvement Plan](../README.md)

## B.1 Maven Build Configuration

| Finding | Severity | File:Line | Description |
|---------|----------|-----------|-------------|
| Build cache disabled | CRITICAL | `.mvn/maven-build-cache-config.xml:10` | Extension installed but `<enabled>false</enabled>` |
| `${env.LIMITS_CPU}` forkCount | CRITICAL | `operate/pom.xml:66`, `tasklist/pom.xml:61,74` | Resolves to null when env var unset -> no test isolation |
| No `.mvn/maven.config` | WARNING | (missing file) | No default parallel builds |
| No JVM heap in jvm.config | WARNING | `.mvn/jvm.config` | Risk of OOM during parallel builds |
| `useIncrementalCompilation=false` | WARNING | `parent/pom.xml:2379` | Stale workaround for bug fixed in 3.13+ |
| `skip.docker` double definition | WARNING | `optimize/pom.xml:86,88` | Docker always starts regardless of skipTests |
| `parallel-tests` profile not default | IMPROVEMENT | `parent/pom.xml:3114` | Tests run serially unless explicitly opted in |
| Dead compiler config in optimize | IMPROVEMENT | `optimize/backend/pom.xml:760-768` | `fork=false` with meminitial/maxmem (only apply when fork=true) |
| Node.js downloaded 4 times | IMPROVEMENT | Frontend pom.xml files | Each frontend module installs Node independently |

## B.2 Flaky Test Indicators

| Pattern | Count | Key Examples |
|---------|-------|-------------|
| `@Disabled` annotations | 136 files | `GlobalJobStatisticsIT` (flaky), `HistoryCleanupIT` (flaky #35023) |
| `@Ignore` annotations | 9 files | `ModifyProcessInstanceOperationZeebeIT` ("Due to flaky CI tests") |
| Disabled on AWS OpenSearch | 97 files | `@DisabledIfSystemProperty(matches = "AWS_OS")` |
| `Thread.sleep()` in tests | 52 files | `GlobalJobStatisticsIT`, `RaftTest`, `SwimProtocolTest` |
| "flaky" in code comments | 19 locations | `RdbmsTableNames`, `MessageCorrelationTest`, `EmbeddedSubProcessConcurrencyTest` |
| `page.waitForTimeout()` | 15 occurrences | All in Operate Playwright tests |
| `continue-on-error` in workflows | 90+ instances | Some mask real failures in merge queue context |
| `rerunFailingTestsCount` | Every test job | 2-3 retries, masking real failure rates |

## B.3 Test Infrastructure Summary

| Category | Count | Frameworks |
|----------|-------|------------|
| Java test files with `@Test` | ~3,000+ | JUnit 5, JUnit 4, Mockito, AssertJ |
| Integration test files (`*IT.java`) | ~300+ | TestContainers, SpringBootTest, Failsafe |
| TestContainers usage | 89 files | Docker containers for ES/OS/S3/GCS/Azure/PG |
| WireMock usage | 100+ files | HTTP mocking |
| Playwright spec files | ~160+ | Chromium, Firefox, Edge |
| ArchUnit test files | 19 | Architecture validation |
| Awaitility usage | 200+ files | Async test patterns (should be higher) |
| JUnit 4 files remaining | ~804 | Migration to JUnit 5 needed |

## B.4 Runner Usage per PR

| Runner | Cores | Approx. Jobs Per PR |
|--------|-------|-------------------|
| `ubuntu-latest` | 2-4 | ~15 |
| `gcp-perf-core-8-default` | 8 | ~10 |
| `gcp-perf-core-16-default` | 16 | ~15 |
| `gcp-perf-core-16-longrunning` | 16 | ~4 |
| `gcp-core-4-default` | 4 | ~1 |
| `gcp-core-8-default` | 8 | ~2 |
| `gcp-core-32-default` | 32 | ~3 (legacy only) |
| `macos-latest` | - | ~1 |
| `windows-latest` | - | ~1 |
| `aws-arm-core-4-longrunning` | 4 | ~1 |

## B.5 CI Critical Path Analysis

```
detect-changes (~2 min)
  |
  +-- [Parallel Tier 2]
  |   +-- integration-tests matrix (30 min timeout, longest chain)
  |   +-- database-integration-tests x7 (20 min each)
  |   +-- zeebe-unit-tests x7 (10 min each)
  |   +-- general-unit-tests (10 min)
  |   +-- sub-workflows (operate/tasklist/optimize/zeebe CI)
  |
  check-results (~1 min)
  |
  +-- deploy-snapshots (~20 min)
  +-- deploy-camunda-docker-snapshot (~15 min, parallel)
```

**Current critical path: ~55 min (PR), ~75 min (main with deploys)**
**Target critical path: ~20 min (PR), ~30 min (main with deploys)**
