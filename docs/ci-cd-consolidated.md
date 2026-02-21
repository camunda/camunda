# CI/CD Improvement Plan: Path to Continuous Delivery — Consolidated Document

> This is a consolidated version of the CI/CD Improvement Plan for easier reading and import. For the structured version, see the [`docs/ci-cd/`](./ci-cd/) folder.

**Repository**: `camunda/camunda` (monorepo)
**Scope**: GitHub Actions pipelines, Maven build configuration, testing strategy, flaky test remediation, developer experience

> **Phase Ordering Rationale**: Flaky test remediation (Phase 1) runs concurrently with build
> optimization (Phase 2) — not sequentially. Quick wins (Phase 0) and the shared build artifact
> investigation start immediately, because reducing 75 redundant builds per PR also reduces resource
> contention that itself causes flakiness. However, reliability work (flaky test fixes, retry
> reduction) must reach measurable milestones before enforcing strict merge queue rules or
> restructuring the test strategy.

> **Implementation Tiers**: This plan is structured as three explicit tiers with off-ramps:
> - **Tier 1 (Must-Do, Weeks 1-8)**: Phase 0 + shared build artifacts + top flaky test fixes. Expected: ~25 min PR, -55% compute.
> - **Tier 2 (Should-Do, Weeks 8-16)**: Flaky budget enforcement, testing manifesto adoption, parallel-tests, runner right-sizing.
> - **Tier 3 (Aspirational, Weeks 16+)**: Contract testing, JUnit 4 migration, full CD pipeline, workflow consolidation. Only pursue with continued organizational commitment.

---

## Current vs. Target Metrics

| Metric | Current | Phase 0+1 | Phase 2+3 | CD Target |
|--------|---------|-----------|-----------|-----------|
| PR wall-clock time | ~55 min | ~45 min | ~25 min | **~20 min** |
| Compute cost per PR | Baseline | -30% | -55% | **-65%** |
| Flaky test rate | Unknown | <10% | <5% | **<2%** |
| CI retriggers per PR | Multiple | <1 | ~0 | **0** |
| Local `mvn -Dquickly` | ~5-10 min | ~3-5 min | ~2-3 min | **<2 min** |

## Phase Overview

| Phase | Timeline | Focus | Key Impact |
|-------|----------|-------|------------|
| Phase 0: Quick Wins | Week 1-2 | Build config, low-hanging fruit | -30% compute |
| Phase 1: Flaky Test Remediation | Week 2-6 | Pipeline trustworthiness | Reliable CI |
| Phase 2: Build Artifact Sharing | Week 2-8 | Eliminate redundant builds | -55% compute |
| Phase 3: Test Strategy | Week 8-16 | Testing pyramid, OpenAPI contract validation | Faster feedback |
| Phase 4: Developer Experience | Week 12-20 | Local dev, JUnit migration | <5 min local tests |
| Phase 5: Pipeline Architecture | Week 16-24 | CD-ready pipeline | ~20 min PR time |

## Target Pipeline Architecture

```
PR Push
  |
  +- [Tier 0: ~1 min] Lint + Format
  |
  +- [Tier 1: ~5 min] Build Artifacts (single job, shared via artifacts)
  |    |
  |    +- [Tier 2: ~8 min] Unit Tests (parallel, affected modules only)
  |    +- [Tier 2: ~8 min] Contract Tests (API compatibility)
  |    +- [Tier 2: ~3 min] ArchUnit + Static Analysis
  |    |
  |    +- [Tier 3: ~15 min] Integration Tests (primary DB only)
  |    |
  |    +- [Gate] check-results <- All green = mergeable
  |
  Merge to main -> Deploy snapshots + Docker (~10 min)
  |
  Nightly -> Full DB matrix, cross-browser E2E, load tests
```

## Detailed Documentation

| Document | Description |
|----------|-------------|
| Current State Assessment | Key numbers, architecture, what's done well |
| Root Cause Analysis | Slowness, flakiness, developer CI dependency |
| Success Metrics | Full metrics table across all phases |
| Priority Summary | All 32 action items ranked |
| Workflow Inventory | All GitHub Actions workflow tables |
| Detailed Findings | Findings tables by area |

## Related Documentation

- Testing Strategy Manifesto — Testing practices, patterns, and policies
- Flaky Test Policy — Budget, quarantine, runbook
- Contract Tests — Pact consumer-driven contract testing
- Enforcement Rules — ArchUnit, ESLint, Checkstyle

---

# Current State Assessment

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

---

# Root Cause Analysis

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

---

# Phase 0: Quick Wins (Week 1-2)

**Goal**: Immediate cost and time reduction with minimal risk.

> **Rollback protocol**: Every Phase 0 change must have a revert trigger. If any change increases the
> CI flaky rate by >5% within 1 week of deployment, revert immediately and investigate before re-applying.

---

## 3.1 Disable Legacy Duplicate Workflows

**Impact**: ~30-40% compute reduction for Operate/Optimize PRs
**Risk**: Low
**Effort**: Low

These workflows duplicate work already covered by the unified CI:

| File | Name | Overlap |
|------|------|---------|
| `.github/workflows/operate-ci.yml` | [Legacy] Operate | `ci.yml -> ci-operate.yml` |
| `.github/workflows/operate-docker-tests.yml` | [Legacy] Operate / Docker | `ci.yml -> docker-checks` |
| `.github/workflows/optimize-ci-core-features.yml` | [Legacy] Optimize Core Features | `ci.yml -> ci-optimize.yml` (uses 120min timeout on 32-core runners) |
| `.github/workflows/optimize-ci-data-layer.yml` | [Legacy] Optimize Data Layer | `ci.yml -> database-integration-tests` |
| `.github/workflows/optimize-e2e-tests-sm.yml` | [Legacy] Optimize E2E SM | 120min timeout on ubuntu-latest |

**Action**: Validate unified CI covers all scenarios, then rename to `.yml.disabled` or delete. Start by adding a condition that skips them (e.g., `if: false`) so they can be quickly re-enabled if needed.

---

## 3.2 Create `.mvn/maven.config`

**Impact**: ~30-50% faster full builds (local and CI)
**Risk**: Low
**Effort**: Trivial

Create `.mvn/maven.config` with:
```
-T1C
--fail-at-end
```

This enables parallel module builds by default. Currently only the Tasklist Makefile uses `-T1C`.

---

## 3.3 Enable Maven Build Cache

**Impact**: 50-80% faster incremental local builds
**Risk**: Medium (test with a small module set first)
**Effort**: Low

At `.mvn/maven-build-cache-config.xml:10`, change:
```xml
<!-- Before -->
<enabled>false</enabled>

<!-- After -->
<enabled>true</enabled>
```

The configuration (xxHash, glob patterns, `maxBuildsCached=5`) is already well-designed. Start with local development only, expand to CI incrementally.

---

## 3.4 Fix `${env.LIMITS_CPU}` forkCount

**Impact**: Eliminates a class of flaky tests caused by missing test isolation
**Risk**: Low
**Effort**: Low

**Note**: `operate/pom.xml` already uses the `testForkCount` property indirection, but the default value still resolves to `${env.LIMITS_CPU}`. In `tasklist/pom.xml:61,74`, the forkCount is set directly in the plugin config.

**Fix for both modules**: Ensure the `testForkCount` property defaults to `1` (not `${env.LIMITS_CPU}`):
```xml
<!-- In <properties> -->
<testForkCount>1</testForkCount>

<!-- In surefire/failsafe config -->
<forkCount>${testForkCount}</forkCount>
```

CI overrides via `-DtestForkCount=${LIMITS_CPU}`.

**Rollback**: Revert the property default if test isolation causes unexpected failures.

---

## 3.5 Fix Playwright `actionTimeout: 0`

**Impact**: Prevents indefinite hangs in E2E tests
**Risk**: Low
**Effort**: Trivial

In `operate/client/playwright.config.ts` and `tasklist/client/playwright.config.ts`, change:
```typescript
// Before — unlimited, hangs forever on stuck selectors
actionTimeout: 0,

// After — 10 seconds, matching the C8 orchestration suite
actionTimeout: 10_000,
```

---

## 3.6 Fix `skip.docker` Double Definition

**Impact**: Prevents unnecessary Docker operations when tests are skipped
**Risk**: Low
**Effort**: Trivial

In `optimize/pom.xml`, remove the duplicate at line 88:
```xml
<!-- Remove line 88, keep line 86 -->
<skip.docker>${skipTests}</skip.docker>    <!-- line 86: correct -->
<!-- <skip.docker>false</skip.docker> -->  <!-- line 88: overwrites, always false — REMOVE -->
```

---

## 3.7 Add JVM Heap Configuration

**Impact**: Prevents OOM during parallel builds
**Risk**: Low
**Effort**: Trivial

The file `.mvn/jvm.config` already exists with `--add-exports`/`--add-opens` flags for Java module access, but has no heap settings. Append:
```
-Xmx4g -Xms1g
```

**Rollback**: Remove the heap flags if they cause issues on machines with <8 GB RAM.

---

## 3.8 Re-enable Incremental Compilation

**Impact**: Faster recompilation during local development
**Risk**: Low (bug was fixed in maven-compiler-plugin 3.13+, project uses 3.15.0)
**Effort**: Trivial

In `parent/pom.xml:2379`, change:
```xml
<!-- Before -->
<useIncrementalCompilation>false</useIncrementalCompilation>

<!-- After: remove the line entirely, or set to true -->
<useIncrementalCompilation>true</useIncrementalCompilation>
```

---

# Phase 1: Flaky Test Remediation (Week 2-6)

**Goal**: Make the pipeline trustworthy. Stop masking flakiness, start fixing it. Engineers should never need to retrigger a passing build.

> **Why this is Phase 1**: The pipeline currently fails due to flaky tests and engineers retrigger
> until it passes. This is the #1 source of developer frustration and wasted compute. No amount of
> build optimization matters if developers don't trust the results. A reliable pipeline is the
> prerequisite for everything that follows.

---

## 4.1 Establish a Flaky Test Budget

**Impact**: Cultural shift from "tolerate" to "fix"
**Risk**: Medium (requires team buy-in)
**Effort**: Low (policy change)

For the full flaky test policy including budget, quarantine process, runbook, and root cause table, see Flaky Test Policy.

Summary:
- **Tiered targets**: <10% by end of Phase 1, <5% by end of Phase 3, <2% as CD stretch goal
- **Metric definition**: Flaky rate = (test methods that flaked at least once in a week) / (total distinct test methods executed that week). Secondary metric: CI pipeline flake rate = (pipeline runs failed due to flakes) / (total pipeline runs)
- **Quarantine threshold**: Flake rate >5% for tests run 10+ times/week, OR 3+ flakes for tests run <10 times/week
- **Quarantine mechanism**: Use JUnit 5 `@Tag("quarantine")` with Surefire `<excludedGroups>quarantine</excludedGroups>` to exclude from PR runs; create GitHub issue with `kind/flake` label; escalate to team medic
- **Fix SLA**: Quarantined tests must be fixed or deleted within 2 sprints
- **Enforcement**: Block PR merge if flaky test count exceeds budget

---

## 4.2 Replace `Thread.sleep()` with Awaitility

**Impact**: Eliminates timing-dependent flakiness in 52 files
**Risk**: Low
**Effort**: Medium

Awaitility is already a dependency (200+ files use it). Expand adoption to the remaining 52 files. For the rule and examples, see Prohibited Patterns.

**Tier the 52 files before starting:**

| Tier | Count | Description | Timeline |
|------|-------|-------------|----------|
| **(a) Trivial** | ~30 | Sleep precedes a simple assertion — direct 1:1 replacement | Week 3-4 |
| **(b) Moderate** | ~15 | Requires custom polling logic or condition identification | Week 5-6 |
| **(c) Hard** | ~7 | Test architecture needs restructuring (e.g., Raft, SWIM protocol) | Defer to Phase 4 |

Priority files (highest flakiness risk):
- `qa/acceptance-tests/src/test/java/io/camunda/it/client/GlobalJobStatisticsIT.java` (3 occurrences)
- `qa/acceptance-tests/src/test/java/io/camunda/it/tenancy/GlobalJobStatisticsTenancyIT.java` (2 occurrences)
- `dist/src/test/java/io/camunda/zeebe/shared/management/ControlledActorClockEndpointTest.java` (explicit "can be flaky" comment)
- `zeebe/atomix/cluster/src/test/java/io/atomix/raft/RaftTest.java`
- `zeebe/atomix/cluster/src/test/java/io/atomix/cluster/protocol/SwimProtocolTest.java`

---

## 4.3 Replace Playwright `waitForTimeout` with Proper Assertions

**Impact**: Eliminates 15 timing-dependent E2E flakiness sources
**Risk**: Low
**Effort**: Low

For the rule, see Prohibited Patterns.

All 15 occurrences in the Operate client E2E tests:

| File | Occurrences | Current Wait |
|------|------------|-------------|
| `operate/client/e2e-playwright/visual/processInstance.spec.ts` | 6 | `waitForTimeout(500)` |
| `operate/client/e2e-playwright/visual/decisionInstance.spec.ts` | 1 | `waitForTimeout(500)` |
| `operate/client/e2e-playwright/tests/processInstanceMigration.spec.ts` | 3 | `waitForTimeout(500)` |
| `operate/client/e2e-playwright/tests/processInstanceListeners.spec.ts` | 1 | `waitForTimeout(1000)` |
| `operate/client/e2e-playwright/docs-screenshots/get-familiar-with-operate.spec.ts` | 1 | `waitForTimeout(2000)` |
| `operate/client/e2e-playwright/docs-screenshots/process-instance-modification.spec.ts` | 3 | `waitForTimeout(1000)` |

---

## 4.4 Reduce `rerunFailingTestsCount` Progressively

**Impact**: Surfaces real flakiness instead of masking it
**Risk**: Medium (will initially surface more "failures")
**Effort**: Low

| Step | Surefire retries | Failsafe retries | Go/No-Go Criteria |
|------|-----------------|-------------------|-------------------|
| Current | 3 | 2-3 | — |
| Step 1 | 2 | 2 | Flaky rate <8% for 2 consecutive weeks AND Thread.sleep tier (a) fixes landed |
| Step 2 | 1 | 1 | Flaky rate <4% for 2 consecutive weeks AND Thread.sleep tier (a)+(b) fixes landed |
| Target | 0 | **1** | Flaky rate <2% for 4 consecutive weeks (Surefire only) |

> **Note**: Keep `failsafe.rerunFailingTestsCount=1` permanently. Integration tests are inherently
> more variable due to Docker, networking, and eventual consistency. Zero retries for Failsafe is
> unrealistic without architectural changes to the test harness.

Each reduction will surface previously-masked flaky tests. These should be triaged using the flaky test budget process.

**Communication**: Before each reduction step, post to the engineering Slack channel explaining what to expect (more red builds initially) and linking to the flaky test response runbook.

---

## 4.5 Audit and Fix `continue-on-error` Usage

**Impact**: Prevents failures from being silently swallowed
**Risk**: Medium (must ensure flakiness is under control first)
**Effort**: Medium

> **Correction**: `ci.yml:989` is on the Hadolint SARIF upload step (an observability step), not on
> a test execution step. The actual test pass/fail is determined by test steps and aggregated in
> `check-results`. A systematic audit of ALL `continue-on-error` usage is needed.

There are 26 `continue-on-error` instances in `ci.yml` alone and 90+ across all workflows. Classify each:

| Category | Action |
|----------|--------|
| `continue-on-error` on **test execution** steps | **Remove** — tests must fail the pipeline |
| `continue-on-error` on **observability/reporting** steps (SARIF upload, stats, Slack) | **Keep** — these protect against GitHub API transient failures |
| `continue-on-error` on **artifact upload** steps | **Evaluate** — may be protecting against legitimate transient failures |

**Prerequisite**: Complete steps 4.1-4.4 first. The enforcement point for strict merging should be in the `check-results` job, not in individual step-level flags.

---

## 4.6 Triage the 97 AWS OpenSearch Disabled Tests

**Impact**: Restores test coverage for an entire database backend
**Risk**: Low
**Effort**: High

97 test files contain:
```java
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
```

**Triage approach**:
1. **Categorize** the disabled tests by failure mode (timeout, assertion, connection, behavior difference)
2. **Root cause analysis** — is this a test environment issue, a product behavior difference, or a genuine incompatibility?
3. **Fix or formally document** — either fix the root cause or document why AWS OpenSearch behaves differently and adjust expectations
4. **Dedicated CI environment** — if AWS OpenSearch requires special configuration, create a dedicated CI job with proper setup

---

## 4.7 Triage the Disabled/Ignored Test Backlog

**Impact**: Restores lost test coverage
**Risk**: Low
**Effort**: Medium

139 files have `@Disabled` / `@Ignore`. **Start with automated analysis** — script the "linked to closed issue" and "disabled >6 months" checks to resolve 40-50% of the backlog immediately. Then manually triage the remainder.

Assign explicit team ownership: each product team (Operate, Tasklist, Optimize, Zeebe) triages their own disabled tests.

For each:

| Condition | Action |
|-----------|--------|
| Linked to an open issue | Verify issue is prioritized |
| Linked to a closed issue | Re-enable the test |
| No issue linked | Create an issue or delete the test |
| Comment says "flaky" | Apply flaky test budget process |
| Disabled for >6 months | Delete (dead disabled tests provide zero value) |

---

## 4.8 Create Missing Flaky Test Issue Template

**Impact**: Standardizes flaky test reporting
**Risk**: None
**Effort**: Trivial

The documented template at `unstable_test.md` doesn't exist in `.github/ISSUE_TEMPLATE/`. Create it with fields for:
- Test class and method name
- Flake frequency (from Grafana)
- Error message / stack trace
- Suspected root cause
- Link to Grafana dashboard
- Owning team

---

## 4.9 Add Automated Enforcement Rules

**Impact**: Prevents new flaky patterns from entering the codebase
**Risk**: Low
**Effort**: Medium

For the full enforcement rule definitions and configuration, see Enforcement Rules.

Summary of proposed rules:
- **ArchUnit `FreezingArchRule`** to prevent `Thread.sleep()` in test code — uses ArchUnit's baseline mechanism to capture existing violations and only fail on NEW violations, enabling gradual migration
- **ESLint rule** to prevent `waitForTimeout()` in Playwright tests — add `/* eslint-disable */` comments on the 15 existing files as a temporary baseline, then remove as files are fixed
- **ArchUnit rule** to prevent new JUnit 4 usage — ban `@RunWith`, `@Rule`, `org.junit.Test` imports in new files
- **Metric ratchets** — CI should track Thread.sleep count, disabled test count, and `waitForTimeout` count; fail any PR that increases the count beyond the current baseline

---

# Phase 2: Build Artifact Sharing (Week 2-8)

**Goal**: Eliminate redundant compilation across CI jobs.

> **Note**: Phase 2 starts in parallel with Phase 1 (Week 2), not after it. Reducing the ~75
> redundant builds per PR also reduces runner resource contention, which itself contributes to
> flakiness. The shared build investigation should begin immediately after Phase 0 quick wins land.

---

## 5.1 Implement a Shared Build Job

**Impact**: Eliminate ~75 redundant Maven builds across 22 workflow files (~200-600 runner-minutes saved per PR)
**Risk**: Medium-High (introduces single point of failure — see risks below)
**Effort**: High

This is the **single highest-impact change**. Currently every test job independently runs `./.github/actions/build-zeebe` which does a full `./mvnw -B -T1C -DskipTests -DskipChecks install`.

**Target architecture**:
```
build-artifacts (single job, gcp-perf-core-16)
  |  ./mvnw install -DskipTests -DskipChecks -T1C
  |  share via cache (primary) or upload-artifact (fallback)
  |
  +-- general-unit-tests (restore cache -> run tests only)
  +-- zeebe-unit-tests[7] (restore cache -> run tests only)
  +-- integration-tests[12] (restore cache -> run tests only)
  +-- database-integration-tests[7] (restore cache -> run tests only)
  +-- operate-ci (restore cache -> run tests only)
  +-- tasklist-ci (restore cache -> run tests only)
  +-- optimize-ci (restore cache -> run tests only)
```

### Approach Comparison

| Approach | Pros | Cons | Recommendation |
|----------|------|------|----------------|
| **`actions/cache@v5`** (SHA-keyed) | Handles concurrent access well, no storage limits per run | 10 GB per-repo limit, LRU eviction | **Primary approach** |
| **Remote Maven Build Cache** (S3/GCS-backed) | Content-addressable, cross-job reuse, incremental | Requires infrastructure (S3 bucket), cache extension already installed | **Evaluate for Phase 2.5** |
| **`actions/upload-artifact@v4`** | Simple, per-run isolation | 10 GB per-run limit, thundering-herd downloads, single point of failure | **Fallback only** for non-Maven outputs |
| **Develocity (Gradle Enterprise for Maven)** | Full build cache + build scan analytics | Commercial license, infrastructure | **Future evaluation** |

### Scaling Risks

- **Single point of failure**: The build-artifacts job becomes a critical path bottleneck. If it fails, everything waits. Today's redundant builds are embarrassingly parallel.
- **Thundering herd**: 40+ downstream jobs simultaneously downloading a multi-GB artifact can cause download failures — a new source of CI flakiness.
- **Storage limits**: `~/.m2/repository/io/camunda` for 144 modules may be several hundred MB to multiple GB. Measure the actual size before committing.

### Implementation Steps

1. **Measure first**: Run a full build and measure the size of `~/.m2/repository/io/camunda`
2. **Primary**: Use `actions/cache@v5` keyed by `hashFiles('**/pom.xml')-${{ github.sha }}` — better concurrent access handling than artifacts
3. **Selective sharing**: Share only `~/.m2/repository/io/camunda`, not the entire workspace
4. **Graceful fallback**: If cache restore fails, the job should fall back to building locally (like today) rather than failing the entire pipeline
5. **Cleanup**: Set `retention-days: 1` on any artifacts to prevent storage bloat
6. Phase in: start with unit test jobs, then integration tests
7. **Evaluate remote cache**: Investigate S3/GCS-backed Maven Build Cache (the extension is already installed at `.mvn/maven-build-cache-config.xml`) as a more robust long-term solution

---

## 5.2 Share Frontend Build Outputs

**Impact**: Save ~18-48 min in deploy jobs
**Risk**: Low
**Effort**: Medium

The `build-platform-frontend` job already builds all 4 frontends. Upload the output:

```yaml
build-platform-frontend:
  steps:
    # ... existing build steps ...
    - uses: actions/upload-artifact@v4
      with:
        name: frontend-builds
        path: |
          operate/client/build/
          tasklist/client/build/
          identity/client/build/

deploy-snapshots:
  needs: [check-results, build-platform-frontend]
  steps:
    - uses: actions/download-artifact@v4
      with:
        name: frontend-builds
    # Skip frontend rebuild, go straight to Maven deploy
```

---

## 5.3 Add Docker Layer Caching

**Impact**: Faster Docker builds across all workflows
**Risk**: Low
**Effort**: Medium

For all `docker/build-push-action` steps, add GHA cache backend:
```yaml
- uses: docker/build-push-action@v6
  with:
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

Applies to:
- `ci.yml` -> `docker-checks` job
- `ci-zeebe.yml` -> `docker-checks` job
- `deploy-camunda-docker-snapshot` job
- All integration test jobs that build Docker images

---

## 5.4 Consolidate Maven Cache Modifiers

**Impact**: Better cache reuse across jobs
**Risk**: Low
**Effort**: Low

Reduce from ~20+ unique cache key modifiers to 4:

| Modifier | Used By |
|----------|---------|
| `build` | Build artifact job |
| `test-ut` | All unit test jobs |
| `test-it` | All integration test jobs |
| `deploy` | Deploy jobs |

---

# Phase 3: Test Strategy Restructuring (Week 8-16)

**Goal**: Shift left in the testing pyramid — faster feedback, more reliable tests. Adopt the Testing Strategy Manifesto as the binding standard for all new work.

> **Prerequisite**: Phase 1 (flaky test remediation) must be substantially complete. The pipeline
> must be reliable before restructuring what tests run where.
>
> **Important**: Activate enforcement rules (ArchUnit, ESLint) *before* socializing the manifesto.
> Automated enforcement is more effective than cultural change alone.

---

## 6.1 Adopt the Testing Strategy Manifesto

The Testing Strategy Manifesto defines:
- What tests are required for every change type (feature, bugfix, incident, refactoring)
- Prohibited patterns (Thread.sleep, waitForTimeout, JUnit 4 in new code, etc.)
- Required patterns (given/when/then, Awaitility, TestSearchContainers, Page Object Model)
- Naming conventions, framework choices, and enforcement mechanisms
- The PR checklist that every code change must satisfy

**Action**: Socialize the manifesto with all teams, integrate it into `CONTRIBUTING.md`, and create a PR template with the testing checklist.

---

## 6.2 Establish the Testing Pyramid

For the full testing pyramid definition and layer details, see the Testing Strategy README.

**Current state** (estimated):
```
       /    E2E + ITs    \     ~50%+ of test time — slow, external-service-dependent
      /    Unit Tests      \   ~50% — includes 804 JUnit 4 files
```

**Target state**:
```
        /  E2E  \          < 5% of test time, nightly only
       /  Contract \        ~10% — API contract verification
      / Integration  \      ~20% — focused, TestContainers-based
     /    Unit Tests    \   ~65% — fast, isolated, no external deps
```

---

## 6.3 Strengthen API Contract Validation

**Impact**: Catch API breaking changes fast without full E2E
**Risk**: Low
**Effort**: Medium (Tier 2) to High (Tier 3)

### Tier 2: Strengthen What Exists (Do Now)

Currently the only contract-like testing is:
- OpenAPI linting (Spectral) — validates the spec is well-formed
- Protobuf backward-compat (Buf) — validates gRPC schema compatibility
- `validateResponse()` in E2E API tests — validates responses match OpenAPI spec (but only runs nightly)

**Immediate improvements** (no new frameworks needed):
1. **Add OpenAPI backward compatibility checking**: Use `openapi-diff` or `spectral diff` to detect breaking changes between PR and main. This catches field removals, type changes, and endpoint deletions automatically.
2. **Validate frontend Zod schemas stay in sync with OpenAPI spec**: Add a CI check that compares `@camunda/camunda-api-zod-schemas` against `rest-api.yaml`. This is already partially covered but should be a hard gate.
3. **Keep Buf for gRPC** (already enforced).

These get ~80% of contract testing value at ~10% of the implementation and maintenance cost.

### Tier 3: Consumer-Driven Contract Testing with Pact (Aspirational)

> **Recommendation**: Defer full Pact adoption until after the team demonstrates it can maintain
> the basics (zero new `Thread.sleep`, all new tests on JUnit 5, build cache enabled, flaky rate
> under control). Introducing a new testing framework to a team already struggling with test
> reliability adds complexity at the wrong time.

If Pact is pursued, start with **PactFlow bidirectional testing** — compare consumer pacts against the existing OpenAPI spec without provider-side test code. This is the lightest-weight approach for a monorepo. See Contract Tests for the full guide.

**Priority contract boundaries** (for when Pact is adopted):

| # | Consumer | Provider | Priority |
|---|----------|----------|----------|
| 1 | Java Client (`clients/java/`) | v2 REST API (Zeebe gateway) | **Highest** — public API |
| 2 | Tasklist Frontend | v2 REST API | High — already typed via `@camunda/camunda-api-zod-schemas` |
| 3 | Operate Frontend | v2 REST API + legacy `/api/` | High |
| 4 | Camunda Exporter | ES/OS index schema (Pact message contracts) | Medium |
| 5 | Optimize Frontend | Optimize Backend REST API | Medium |

---

## 6.4 Classify and Tier Tests for PR vs. Nightly

**Impact**: Faster PR feedback while maintaining comprehensive coverage
**Risk**: Medium (requires validation that nightly catches regressions)
**Effort**: Medium

| Tier | When to Run | What | Est. Time |
|------|-------------|------|-----------|
| **Tier 1 (PR-blocking)** | Every PR | Unit tests, ArchUnit, linting, contract tests, affected-component ITs | ~15 min |
| **Tier 2 (PR-non-blocking)** | Every PR | Primary DB ITs (ES8 + H2), component integration tests | ~20 min |
| **Tier 3 (Nightly)** | Daily schedule | Multi-DB matrix (ES9, OpenSearch, RDBMS variants), full E2E suites, smoke tests on macOS/Windows/ARM | ~60 min |
| **Tier 4 (Weekly)** | Weekly schedule | Load tests, version compatibility, full cross-browser E2E, AWS OpenSearch | ~120 min |

**Key change**: Move the 7-way database integration test matrix from PR-blocking to nightly. On PRs, run against 2 DB variants (Elasticsearch 8 + one RDBMS) to catch the most common cross-engine compatibility issues while still cutting the matrix by 5x. If nightly tests fail, auto-create issues.

**Nightly failure SLA**: Regressions detected in nightly must have a fix PR opened within 1 business day and merged within 3 business days. This prevents nightly from becoming a dumping ground for ignored failures.

---

## 6.5 Activate `parallel-tests` Profile by Default in CI

**Impact**: Faster test execution across all CI jobs
**Risk**: Low
**Effort**: Low

The `parallel-tests` profile (`parent/pom.xml:3114-3165`) enables `forkCount=0.5C` and JUnit 5 parallel execution. It's already used in some CI jobs but not consistently. Add it to all test-running Maven commands.

---

# Phase 4: Developer Experience (Week 8-16)

**Goal**: Enable developers to run relevant tests locally in <5 minutes, reducing CI dependency.

---

## 7.1 Optimize Local Build Time

Summary of all local build improvements (many from Phase 0):

| Change | File | Impact |
|--------|------|--------|
| Enable build cache | `.mvn/maven-build-cache-config.xml:10` | 50-80% faster incremental builds |
| Add `-T1C` default | `.mvn/maven.config` (new) | 30-50% faster full builds |
| Re-enable incremental compilation | `parent/pom.xml:2379` | Faster recompilation |
| Add JVM heap config | `.mvn/jvm.config` | Prevents OOM during parallel builds |

**Target**: `mvn install -Dquickly` in <2 minutes.

---

## 7.2 Create a "PR-Ready" Local Test Profile

**Impact**: Gives developers a fast local check before pushing
**Risk**: Low
**Effort**: Medium

Add a new Maven profile that runs exactly what CI Tier 1 would run:

```xml
<profile>
  <id>pr-ready</id>
  <properties>
    <skipITs>true</skipITs>
    <skipChecks>false</skipChecks>
  </properties>
  <!-- Runs: unit tests + archunit checks -->
  <!-- Skips: integration tests, E2E, Docker -->
</profile>
```

Usage: `mvn verify -Ppr-ready -pl <changed-module>` — should complete in <5 minutes for a single module.

---

## 7.3 Add Pre-Push Git Hook (Optional, Opt-in)

**Impact**: Catches basic failures before pushing to CI
**Risk**: Low (opt-in)
**Effort**: Low

Offer an opt-in pre-push hook that runs affected-module unit tests:
```bash
#!/bin/bash
# .githooks/pre-push (opt-in via: git config core.hooksPath .githooks)
CHANGED_MODULES=$(git diff --name-only origin/main | scripts/affected-modules.sh)
if [ -n "$CHANGED_MODULES" ]; then
  ./mvnw test -pl "$CHANGED_MODULES" -am -Dquickly=false -DskipITs
fi
```

---

## 7.4 Complete JUnit 4 to JUnit Jupiter Migration

**Impact**: Unlocks parallel execution for all tests
**Risk**: **Medium-High** (automated migration handles syntax but not custom rule semantics)
**Effort**: High (804 files, expect 8-12 weeks)

> **Note**: The project already uses JUnit 6.0.3 (`parent/pom.xml` `<version.junit>6.0.3</version.junit>`),
> which is backward-compatible with JUnit 5 Jupiter APIs. The migration target is the Jupiter API,
> not a specific JUnit version.

Jupiter's `junit.jupiter.execution.parallel.enabled=true` only works with Jupiter tests. The 804 remaining JUnit 4 files run serially even when the `parallel-tests` profile is active.

> **Note**: For the prohibition of JUnit 4 in new code, see Prohibited Patterns.

### Prerequisites (Before Running OpenRewrite)

1. **Audit all custom JUnit 4 rules** — grep for classes extending `ExternalResource`, `TestRule`, `MethodRule`, and `@RunWith`. Create a mapping table to their JUnit 5 equivalents. Estimate: 2-3 days.
2. **Write custom OpenRewrite recipes** for the most common custom rules (e.g., `EngineRule` -> `EngineExtension`). OpenRewrite does NOT know about these custom mappings.
3. **Run in dry-run mode first** and categorize the 804 files by migration complexity.

### Known Gotchas

- **Custom `@Rule` lifecycle semantics**: `EngineRule` has specific `before()/after()` ordering that may differ from the `@RegisterExtension` lifecycle.
- **`@RuleChain`**: Tests using `RuleChain` for deterministic ordering require manual conversion to extension ordering.
- **`@ClassRule` / `@Rule` interaction**: Static vs. instance `@RegisterExtension` fields have subtle lifecycle differences.
- **`@RunWith(Parameterized.class)`**: Migrates to `@ParameterizedTest` but the data provider mechanism changes completely.
- **Expect 30-40% manual intervention**: OpenRewrite handles annotation/import changes but produces semantically incorrect tests for complex lifecycle cases.

### Approach

Use OpenRewrite recipe for the mechanical parts:
```bash
./mvnw rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.testing.junit5.JUnit4to5Migration
```

### Phased Rollout (with validation gaps)

| Phase | Scope | Files | Validation |
|-------|-------|-------|------------|
| 1 | Protocol, protocol-impl, msgpack | ~30 | Run full suite 20x, compare flake rates |
| 2 | Gateway-grpc, exporters, clients | ~50 | Wait 2+ weeks before proceeding |
| 3 | Zeebe engine | ~200 | Largest batch — review by owning team |
| 4 | Operate/Tasklist/Optimize | ~300 | Each team reviews their own modules |
| 5 | Remaining modules | ~200 | Cleanup |

**Migration canary**: For each phase, migrate 5% of the module first, run the full test suite 20 times, and compare flake rates before and after. Only proceed if flake rate is stable.

---

## 7.5 Fix Dead Compiler Configuration in Optimize

**Impact**: Removes confusion in build configuration
**Risk**: None
**Effort**: Trivial

In `optimize/backend/pom.xml:760-768`:
```xml
<!-- Current — contradictory: fork=false but meminitial/maxmem only apply when fork=true -->
<configuration>
  <fork>false</fork>
  <meminitial>128m</meminitial>
  <maxmem>256m</maxmem>
</configuration>

<!-- Fix — remove dead configuration -->
<configuration>
  <fork>false</fork>
</configuration>
```

---

# Phase 5: Pipeline Architecture for CD (Week 12-20)

**Goal**: Achieve a CI pipeline that supports continuous delivery — fast, reliable, and trustworthy.

---

## 8.1 Target Pipeline Architecture

```
PR Push
  |
  +- [Tier 0: ~1 min] Lint + Format
  |   actionlint, spotless, eslint, commitlint, openapi, protobuf
  |
  +- [Tier 1: ~5 min] Build Artifacts (single job, shared via artifacts)
  |    |
  |    +- [Tier 2: ~8 min] Unit Tests (parallel, affected modules only)
  |    +- [Tier 2: ~8 min] Contract Tests (API compatibility)
  |    +- [Tier 2: ~3 min] ArchUnit + Static Analysis
  |    |
  |    +- [Tier 3: ~15 min] Integration Tests (primary DB only, affected components)
  |    |
  |    +- [Gate] check-results <- All green = mergeable
  |
  Merge to main (via merge queue)
  |
  +- [Deploy: ~10 min] Snapshot deploy + Docker image push
  |
  Nightly
  |
  +- Full DB matrix (ES8, ES9, OpenSearch, H2, PostgreSQL, AWS OS)
  +- Cross-browser E2E (Chromium, Firefox, Edge)
  +- Smoke tests (Linux, macOS, Windows, ARM)
  +- Load tests
  +- Version compatibility tests
```

**Target PR wall-clock time: ~20 minutes** (down from ~55 minutes)

---

## 8.2 Leverage Statsig Feature Flags for Safe Deployment

**Impact**: Enables true continuous delivery — deploy != release
**Risk**: None (infrastructure already exists)
**Effort**: Low (process adoption)

Statsig is already in place as the feature flag platform. This is a critical CD enabler:

- **Incomplete features merge to `main` safely** — wrap in-progress work behind Statsig gates so it can be deployed without being released to users
- **Progressive rollouts** — new features roll out to 1% -> 10% -> 50% -> 100% with automatic rollback if error rates spike
- **Testing in production** — enable features for internal users or specific tenants before general availability
- **Decoupled deploy/release** — deployments happen continuously; feature releases are a business decision, not an engineering event

**Policy for CD readiness**: Any feature that takes more than one PR to complete must be gated behind a Statsig feature flag. This eliminates the need for long-lived feature branches and allows `main` to always be deployable.

---

## 8.3 Right-Size Runner Specifications

| Current | Proposed | Jobs Affected |
|---------|----------|---------------|
| `gcp-core-32-default` | `gcp-perf-core-16-default` | Legacy Operate/Optimize ITs |
| `gcp-perf-core-16-default` for small UT modules | `gcp-perf-core-8-default` | Protocol, Gateway, Client unit tests |
| 120-min timeouts | 30-min max | All jobs |

---

## 8.4 Implement Strict Merge Queue

**Impact**: Ensures only green code merges to `main`
**Risk**: Medium (requires flakiness to be under control)
**Effort**: Medium

Replace current `continue-on-error` merge queue with strict enforcement:
- All Tier 1-3 tests must pass (no `continue-on-error`)
- Merge queue batches up to 5 PRs for combined testing
- Failed batches bisect to find the offending PR
- Flaky test budget enforced at merge time

---

## 8.5 Reduce Total Workflow Count

**Impact**: Simpler maintenance, fewer duplicate jobs
**Risk**: Medium
**Effort**: High

Target: reduce from 121 workflows to ~60-70 by:
1. Removing all legacy duplicate workflows (Phase 0)
2. Consolidating scheduled workflows (combine `zeebe-daily-qa`, `zeebe-search-integration-tests`, `zeebe-rdbms-integration-tests` into a single nightly matrix)
3. Unifying release workflows into a single parameterized workflow

---

## 8.6 Future: Deployment Safety (Monitoring, Canary, Rollback)

> **Status**: Future improvement — out of scope for the initial CD pipeline work, but essential for
> full continuous deployment maturity.

Camunda has two distinct distribution models, each with different deployment safety requirements:

| Concern | SaaS | Self-Hosted / Self-Managed |
|---------|------|---------------------------|
| **Canary deployments** | Roll out to a subset of SaaS clusters before full fleet | N/A — customers control their own upgrade schedule |
| **Automated rollback** | Auto-revert if error rates or latency SLOs breach thresholds post-deploy | Provide rollback documentation and tested downgrade paths |
| **Health monitoring gates** | Post-deploy health checks (error rate, p99 latency, Zeebe throughput) must pass before promoting to next cluster ring | Ship health check endpoints and upgrade verification scripts customers can run |
| **Deploy/release separation** | Statsig gates control feature visibility independently of deployment | Statsig gates + version-gated feature enablement |
| **Upgrade compatibility testing** | Tested in CI (rolling update, version compatibility) | Must also test customer-facing upgrade paths (Helm chart, Docker Compose, manual) |
| **Rollback scope** | Platform team controls full rollback | Customers need clear rollback procedures per distribution (Helm rollback, Docker tag pinning, etc.) |

**Key principle**: CD for Camunda means `main` is always deployable to SaaS *and* releasable as a Self-Managed artifact. The CI pipeline (Phases 0-5) ensures code quality; deployment safety mechanisms ensure the *release process* is safe for both distribution models.

These topics should be scoped as a follow-up initiative once the pipeline achieves the Phase 5 targets (~20 min PR, <2% flaky rate, strict merge queue).

---

# Success Metrics

| Metric | Current | Phase 0+1 (Wk 1-6) | Phase 2+3 (Wk 8-16) | Phase 4+5 (CD) |
|--------|---------|---------------------|----------------------|----------------|
| **PR wall-clock time** | ~55 min | ~45 min | ~25 min | ~20 min |
| **Compute cost per PR** | Baseline | -30% | -55% | -65% |
| **Flaky test rate** | Unknown (masked by 3x retries) | <10% (measured) | <5% | <2% |
| **CI retriggers per PR** | Multiple (daily pain) | <1 per PR | ~0 | 0 |
| **`rerunFailingTestsCount`** | 3 | 2 | 1 | 0-1 |
| **Local `mvn install -Dquickly`** | ~5-10 min | ~3-5 min | ~2-3 min | <2 min |
| **Tests disabled for flakiness** | 139+ files | <80 (triaged) | <30 | <10 |
| **Devs running tests locally** | Rarely | Some | Most | Standard |
| **Time to deploy after merge** | ~20 min | ~15 min | ~10 min | <10 min |
| **Main branch red rate** | Unknown | Measured | <5% | <1% |

---

# Priority Summary

| # | Action | Phase | Effort | Impact | Risk |
|---|--------|-------|--------|--------|------|
| | **Phase 0 — Quick Wins (Week 1-2)** | | | | |
| 1 | Disable legacy duplicate workflows | 0 | Low | High | Low |
| 2 | Create `.mvn/maven.config` with `-T1C` | 0 | Trivial | Medium | Low |
| 3 | Enable Maven build cache | 0 | Low | High | Medium |
| 4 | Fix `${env.LIMITS_CPU}` forkCount | 0 | Low | High | Low |
| 5 | Fix Playwright `actionTimeout: 0` | 0 | Trivial | Medium | Low |
| 6 | Fix `skip.docker` double definition | 0 | Trivial | Low | Low |
| 7 | Add JVM heap to `.mvn/jvm.config` | 0 | Trivial | Low | Low |
| 8 | Re-enable incremental compilation | 0 | Trivial | Low | Low |
| | **Phase 1 — Flaky Test Remediation (Week 2-6)** | | | | |
| 9 | Establish flaky test budget + quarantine policy | 1 | Low | **Highest** | Medium |
| 10 | Replace `Thread.sleep` in 52 test files | 1 | Medium | High | Low |
| 11 | Replace Playwright `waitForTimeout` (15 occurrences) | 1 | Low | High | Low |
| 12 | Reduce `rerunFailingTestsCount` progressively (3 -> 2 -> 1) | 1 | Low | High | Medium |
| 13 | Create flaky test issue template | 1 | Trivial | Medium | None |
| 14 | Triage disabled tests backlog (139 files) | 1 | High | Medium | Low |
| 15 | Triage 97 AWS OpenSearch disabled tests | 1 | High | Medium | Low |
| 16 | Add ArchUnit rule to ban `Thread.sleep` in tests | 1 | Medium | Medium | Low |
| 17 | Add ESLint rule to ban `waitForTimeout` | 1 | Low | Medium | Low |
| 18 | Fix merge queue `continue-on-error` | 1 | Trivial | High | Medium |
| | **Phase 2 — Build Artifact Sharing (Week 2-8)** | | | | |
| 19 | **Shared build artifact job** | 2 | **High** | **Highest** | Medium |
| 20 | Share frontend build outputs | 2 | Medium | Medium | Low |
| 21 | Docker layer caching | 2 | Medium | Medium | Low |
| 22 | Consolidate Maven cache modifiers | 2 | Low | Low | Low |
| | **Phase 3 — Test Strategy Restructuring (Week 8-16)** | | | | |
| 23 | Adopt Testing Strategy Manifesto | 3 | Medium | High | Low |
| 24 | Strengthen API contract validation (OpenAPI diff, Zod sync) | 3 | Medium | High | Low |
| 25 | Move DB matrix to nightly (PR runs 2 DB variants) | 3 | Medium | High | Medium |
| 26 | Activate `parallel-tests` profile by default in CI | 3 | Low | Medium | Low |
| | **Phase 4 — Developer Experience (Week 12-20)** | | | | |
| 27 | JUnit 4 to Jupiter migration (804 files) | 4 | High | Medium | **Medium-High** |
| 28 | Create PR-ready local test profile | 4 | Medium | High | Low |
| | **Phase 5 — Pipeline Architecture for CD (Week 16-24)** | | | | |
| 29 | Leverage Statsig feature flags for safe deployment | 5 | Low | High | None |
| 30 | Strict merge queue (remove `continue-on-error`) | 5 | Medium | High | Medium |
| 31 | Right-size runners | 5 | Low | Medium | Low |
| 32 | Consolidate workflow count (121 -> ~60-70) | 5 | High | Medium | Medium |

---

# GitHub Actions Workflow Inventory

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

---

# Detailed Findings by Area

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
