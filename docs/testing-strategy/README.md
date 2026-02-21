# Testing Strategy Manifesto

**Camunda 8 Monorepo** | **Status**: DRAFT — Pending team review and adoption

> Every line of production code that reaches `main` must be backed by automated tests
> that are fast, deterministic, and maintainable. If you can't run it locally in minutes,
> it belongs in nightly — not in your PR.

---

## Guiding Principles

1. **Tests are a first-class deliverable** — A feature without tests is not done. A bugfix without a regression test is incomplete.
2. **Optimize for developer feedback speed** — A test that takes 20 minutes and fails intermittently is worse than no test.
3. **Determinism over coverage** — A deterministic suite with 70% coverage beats a flaky suite with 90%.
4. **Test at the lowest possible level** — Unit > Integration > E2E. Higher-level tests are slower, flakier, and harder to debug.
5. **Tests must be runnable locally** — If it requires infrastructure that can't be replicated locally, it belongs in nightly.

---

## The Testing Pyramid

```
                    /    E2E    \                 Nightly only. < 5% of test time.
                   /  Contract   \              PR-blocking. Fast. ~10% of test time.
                  /  Integration  \            PR-blocking (primary DB). ~20% of test time.
                 /    Unit Tests   \         PR-blocking. Fast. ~65% of test time.
                /___________________\
```

| Layer | Scope | External Deps | Target Time | When |
|-------|-------|---------------|-------------|------|
| **Unit** | Single class/function | None (mocked) | < 100ms/test | Every PR, locally |
| **Integration** | Multiple components | TestContainers | < 30s/test | PR (primary DB), nightly (matrix) |
| **Contract** | API boundary | None (mock consumer/provider) | < 1s/test | Every PR |
| **E2E** | Full system | Full running system | < 2 min/test | Nightly, release |
| **Architecture** | Code structure | None | < 10s total | Every PR |
| **Performance** | Throughput/latency | Full running system | Minutes-hours | Weekly, release |

---

## Test Requirements by Change Type

| Change Type | Required Tests |
|-------------|---------------|
| **New feature** | Unit tests for all public classes/methods; integration test if DB/search/external; contract test if REST/gRPC endpoint; update affected tests |
| **Bug fix** | Regression test (`@RegressionTest`) at lowest possible level |
| **Incident** | Regression test; integration test if data corruption; contract test if cross-service; post-mortem identifies correct layer |
| **Refactoring** | All existing tests pass without modification (or PR explains why) |
| **Dependency update** | All existing tests pass; contract tests if public API affected |

---

## PR Checklist

Every PR that modifies production code must satisfy:

- [ ] **Tests added or updated** — Every public change is verified by an automated test
- [ ] **Bug fixes include regression test** — annotated with `@RegressionTest`
- [ ] **No `Thread.sleep()` in test code** — use Awaitility or `expect.poll()`
- [ ] **No `waitForTimeout()` in Playwright tests** — use assertion-based waiting
- [ ] **Test naming follows conventions** — `should<Verb><Object>` for Java, descriptive text for Playwright
- [ ] **Given/When/Then structure** — all test methods use the structured comment pattern
- [ ] **Integration tests use `*IT.java` suffix** — executed by Failsafe
- [ ] **TestContainers use `TestSearchContainers` factory** — not hardcoded image tags
- [ ] **No `@Disabled` without issue link** — every disabled test tracks a resolution
- [ ] **New/modified REST/gRPC endpoints have contract tests** — Pact consumer test per consumer, Buf for gRPC

---

## Detailed Guides

| Guide | Description |
|-------|-------------|
| [Unit Tests](./unit-tests.md) | Rules, gold standard examples |
| [Integration Tests](./integration-tests.md) | TestContainers, Spring slicing, patterns |
| [Contract Tests](./contract-tests.md) | Consumer-driven contract testing with Pact |
| [E2E Tests](./e2e-tests.md) | Playwright rules, Page Object Model |
| [Architecture Tests](./architecture-tests.md) | ArchUnit rules and coverage |
| [Performance & Reliability Tests](./performance-reliability-tests.md) | JMH, chaos engineering |
| [Prohibited Patterns](./prohibited-patterns.md) | 8 anti-patterns with examples |
| [Required Patterns](./required-patterns.md) | 6 required patterns with examples |
| [Naming Conventions](./naming-conventions.md) | File suffixes, method naming, locations |
| [Flaky Test Policy](./flaky-test-policy.md) | Budget, quarantine, runbook, root causes |
| [Test Data Management](./test-data-management.md) | Isolation, builders, Instancio |

## Setup & Configuration

| Guide | Description |
|-------|-------------|
| [Frameworks and Tools](./setup/frameworks-and-tools.md) | Mandatory/prohibited frameworks, versions |
| [Enforcement Rules](./setup/enforcement-rules.md) | ArchUnit, ESLint, Checkstyle config |
| [Pact Setup](./setup/pact-setup.md) | Dependencies, broker, CI YAML |

## Reference

| Guide | Description |
|-------|-------------|
| [Examples](./reference/examples.md) | Gold standard file paths, utilities |
