# Flaky Test Policy

> Back to [Testing Strategy](./README.md)

## Definition

A flaky test is a test that produces different results (pass/fail) on the same code without any code change. A test that passes only on retry is flaky.

## Metric Definitions

Precise definitions are critical for enforceability:

- **Test-level flaky rate** = (number of test methods that flaked at least once in a week) / (total distinct test methods executed that week). This is the primary metric. Benchmark: Google targets 1.5%, Netflix <2%, Uber <3%.
- **Pipeline flake rate** = (pipeline runs that failed due to flakes) / (total pipeline runs). This is the developer-facing metric that drives trust.

## Tiered Targets

| Phase | Test-Level Target | Pipeline Target | Timeline |
|-------|-------------------|-----------------|----------|
| End of Phase 1 | <10% | Measured | Week 6 |
| End of Phase 3 | <5% | <10% | Week 16 |
| CD Readiness | **<2%** | **<2%** | Stretch goal |

## Policy

1. **Detection**: The existing `flaky-test-extractor-maven-plugin` and BigQuery/Grafana pipeline will track flaky tests
2. **Quarantine threshold**: Flake rate >5% for tests run 10+ times/week, OR 3+ flakes for tests run <10 times/week. The threshold is rate-based (not count-based) to account for execution frequency
3. **Quarantine mechanism**: Apply JUnit 5 `@Tag("quarantine")` to the test class/method. Configure Surefire with `<excludedGroups>quarantine</excludedGroups>` for PR runs. Quarantined tests still run nightly
4. **Fix SLA**: Quarantined tests must be fixed or deleted within 2 sprints
5. **No `@Disabled` without an issue**: Every disabled test must link to a tracking issue
6. **Progressive retry reduction**: `rerunFailingTestsCount` will be reduced from 3 -> 2 -> 1 -> 0 (Surefire) with explicit go/no-go gates. Keep Failsafe at 1 permanently — see [Phase 1](../ci-cd/phase-1-flaky-test-remediation.md)

## Flakiness Classification

Not all flakiness is the same. Classify by root cause before setting fix expectations:

| Category | Examples | Fixable? | Approach |
|----------|----------|----------|----------|
| **Timing-dependent** | `Thread.sleep()`, hardcoded timeouts | Yes | Awaitility / `expect.poll()` |
| **Resource contention** | Tests fail under CI load but pass locally | Partially | Reduce CI waste (Phase 2), singleton containers |
| **Architecturally eventual-consistent** | Raft consensus, ES write-to-read delay | Harder | May legitimately need 1 Failsafe retry |
| **Shared mutable state** | Static fields, containers without cleanup | Yes | Per-test isolation |
| **Environment-specific** | Docker pull failures, port conflicts | Partially | Registry mirrors, OS-assigned ports |

## Flaky Test Response Runbook

When a test flakes:

1. Check the Grafana dashboard: `https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo`
2. Search for an existing issue: `gh issue list --label kind/flake --search "<test class name>"`
3. If no issue exists, create one using the flaky test issue template
4. Reproduce locally using IntelliJ "Repeat Until Failure" (see `docs/zeebe/failing-tests.md`)
5. Fix the root cause, don't add retry logic

## Common Root Causes and Fixes

| Root Cause | Pattern | Fix |
|-----------|---------|-----|
| Timing dependency | `Thread.sleep()`, hardcoded timeouts | Awaitility / `expect.poll()` |
| Shared mutable state | Static fields, shared containers without cleanup | Per-test isolation, `@BeforeEach` cleanup |
| Port conflicts | Random ports without checking availability | Let the OS assign ports (`0`), TestContainers handles this |
| Test order dependency | Test B depends on data created by Test A | Each test creates its own data |
| Resource exhaustion | Too many concurrent containers, file handle leaks | Singleton containers, proper `@AfterEach` cleanup |
| Eventual consistency | Assert immediately after write to async system | Awaitility polling |
