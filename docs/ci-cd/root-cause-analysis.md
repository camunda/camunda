# Root Cause Analysis

> Back to [CI/CD Improvement Plan](./README.md)

## Root Causes of Slowness

| # | Root Cause | Evidence | Impact |
|---|-----------|----------|--------|
| S1 | No shared build artifacts — every test job independently runs full `mvn install -DskipTests` | `.github/actions/build-zeebe` called ~75 times across 22 workflow files | ~200-600 runner-minutes wasted per PR |
| S2 | Legacy workflows run in parallel with unified CI | `operate-ci.yml`, `optimize-ci-core-features.yml`, etc. | Doubles compute for Operate/Optimize PRs |
| S3 | Frontend builds repeated — `build-platform-frontend` output never shared | `deploy-snapshots` and `deploy-docker` rebuild all 3 frontends | 18-48 min wasted in deploy jobs |
| S4 | No Docker layer caching | `docker/build-push-action` without `cache-from`/`cache-to` | Slow Docker builds across all workflows |
| S5 | Maven build cache disabled | `.mvn/maven-build-cache-config.xml:10` — `<enabled>false</enabled>` | No incremental build benefit |
| S6 | No default parallel module builds | No `.mvn/maven.config` with `-T1C` | Modules build serially by default |
| S7 | `parallel-tests` profile not activated by default | `parent/pom.xml:3114` requires explicit `-Pparallel-tests` | Tests run serially by default |
| S8 | `useIncrementalCompilation=false` | `parent/pom.xml:2379` — stale workaround for bug fixed in compiler plugin 3.13+ | Slower recompilation |
| S9 | Database integration tests run full matrix on every PR | 7 DB variants on every PR | Each variant takes ~20 min on gcp-perf-core-16 |
| S10 | Node.js downloaded 4 times per build | Each frontend module independently installs Node via `frontend-maven-plugin` | ~2-4 min wasted |

## Root Causes of Flakiness

| # | Root Cause | Evidence | Impact |
|---|-----------|----------|--------|
| F1 | `${env.LIMITS_CPU}` forkCount resolves to null when unset | `tasklist/pom.xml:61,74` (operate already uses `testForkCount` property but defaults to `${env.LIMITS_CPU}`) | forkCount=0 -> no test isolation -> shared JVM state pollution |
| F2 | Retry-as-a-strategy masks flakiness | `rerunFailingTestsCount=3` on every job | Tests failing 66% of the time appear "green" |
| F3 | `Thread.sleep()` in 52 test files | Hardcoded timing waits instead of polling | Timing-dependent failures on varying CI load |
| F4 | Playwright `actionTimeout: 0` | `operate/client/playwright.config.ts`, `tasklist/client/playwright.config.ts` | Stuck selectors hang indefinitely |
| F5 | 15 hardcoded `waitForTimeout()` in Playwright | All in Operate client E2E tests | Fragile timing on varying CI load |
| F6 | `continue-on-error` in CI workflows | `ci.yml:989` is on the Hadolint SARIF upload step (not a test step); however, 26 `continue-on-error` instances in `ci.yml` and 90+ across all workflows need systematic audit | Some may mask real failures in observability/reporting steps |
| F7 | 97 tests blanket-disabled on AWS OpenSearch | `@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")` | Entire DB backend has degraded test coverage |
| F8 | Database cleanup ordering sensitivity | `RdbmsTableNames.java:20-23` — FK ordering is a known flakiness source | Adding a table in wrong order breaks `RdbmsPurgerIT` |
| F9 | Race conditions in test helpers | `ElasticsearchSetupHelperTest.java:169` — explicit "race condition" comment | Non-deterministic test behavior |
| F10 | `skip.docker` double definition | `optimize/pom.xml:86,88` — second definition always wins | Docker containers start even when `skipTests=true` |

## Root Causes of Developer CI Dependency

| # | Root Cause | Evidence | Impact |
|---|-----------|----------|--------|
| D1 | Build cache disabled | `.mvn/maven-build-cache-config.xml:10` | Full rebuild every time locally |
| D2 | No default `-T1C` parallel builds | No `.mvn/maven.config` | Serial module builds by default |
| D3 | No JVM heap configuration | `.mvn/jvm.config` exists but only has `--add-exports`/`--add-opens` flags, no `-Xmx` | OOM during parallel builds |
| D4 | No "PR-ready" local test profile | Only `quickly` (skips ALL tests) exists | No middle ground between "skip all" and "run all" |
| D5 | 804 JUnit 4 test files | Can't use JUnit 5 parallel execution | Slower test execution locally |
