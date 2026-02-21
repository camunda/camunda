# Phase 1: Flaky Test Remediation (Week 2-6)

> Back to [CI/CD Improvement Plan](./README.md)

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

For the full flaky test policy including budget, quarantine process, runbook, and root cause table, see [Flaky Test Policy](../testing-strategy/flaky-test-policy.md).

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

Awaitility is already a dependency (200+ files use it). Expand adoption to the remaining 52 files. For the rule and examples, see [Prohibited Patterns](../testing-strategy/prohibited-patterns.md).

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

For the rule, see [Prohibited Patterns](../testing-strategy/prohibited-patterns.md).

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

For the full enforcement rule definitions and configuration, see [Enforcement Rules](../testing-strategy/setup/enforcement-rules.md).

Summary of proposed rules:
- **ArchUnit `FreezingArchRule`** to prevent `Thread.sleep()` in test code — uses ArchUnit's baseline mechanism to capture existing violations and only fail on NEW violations, enabling gradual migration
- **ESLint rule** to prevent `waitForTimeout()` in Playwright tests — add `/* eslint-disable */` comments on the 15 existing files as a temporary baseline, then remove as files are fixed
- **ArchUnit rule** to prevent new JUnit 4 usage — ban `@RunWith`, `@Rule`, `org.junit.Test` imports in new files
- **Metric ratchets** — CI should track Thread.sleep count, disabled test count, and `waitForTimeout` count; fail any PR that increases the count beyond the current baseline
