# Priority Summary

> Back to [CI/CD Improvement Plan](./README.md)

| # | Action | Phase | Effort | Impact | Risk |
|---|--------|-------|--------|--------|------|
| | **[Phase 0 — Quick Wins (Week 1-2)](./phase-0-quick-wins.md)** | | | | |
| 1 | Disable legacy duplicate workflows | 0 | Low | High | Low |
| 2 | Create `.mvn/maven.config` with `-T1C` | 0 | Trivial | Medium | Low |
| 3 | Enable Maven build cache | 0 | Low | High | Medium |
| 4 | Fix `${env.LIMITS_CPU}` forkCount | 0 | Low | High | Low |
| 5 | Fix Playwright `actionTimeout: 0` | 0 | Trivial | Medium | Low |
| 6 | Fix `skip.docker` double definition | 0 | Trivial | Low | Low |
| 7 | Add JVM heap to `.mvn/jvm.config` | 0 | Trivial | Low | Low |
| 8 | Re-enable incremental compilation | 0 | Trivial | Low | Low |
| | **[Phase 1 — Flaky Test Remediation (Week 2-6)](./phase-1-flaky-test-remediation.md)** | | | | |
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
| | **[Phase 2 — Build Artifact Sharing (Week 2-8)](./phase-2-build-artifact-sharing.md)** | | | | |
| 19 | **Shared build artifact job** | 2 | **High** | **Highest** | Medium |
| 20 | Share frontend build outputs | 2 | Medium | Medium | Low |
| 21 | Docker layer caching | 2 | Medium | Medium | Low |
| 22 | Consolidate Maven cache modifiers | 2 | Low | Low | Low |
| | **[Phase 3 — Test Strategy Restructuring (Week 8-16)](./phase-3-test-strategy.md)** | | | | |
| 23 | Adopt Testing Strategy Manifesto | 3 | Medium | High | Low |
| 24 | Strengthen API contract validation (OpenAPI diff, Zod sync) | 3 | Medium | High | Low |
| 25 | Move DB matrix to nightly (PR runs 2 DB variants) | 3 | Medium | High | Medium |
| 26 | Activate `parallel-tests` profile by default in CI | 3 | Low | Medium | Low |
| | **[Phase 4 — Developer Experience (Week 12-20)](./phase-4-developer-experience.md)** | | | | |
| 27 | JUnit 4 to Jupiter migration (804 files) | 4 | High | Medium | **Medium-High** |
| 28 | Create PR-ready local test profile | 4 | Medium | High | Low |
| | **[Phase 5 — Pipeline Architecture for CD (Week 16-24)](./phase-5-pipeline-architecture.md)** | | | | |
| 29 | Leverage Statsig feature flags for safe deployment | 5 | Low | High | None |
| 30 | Strict merge queue (remove `continue-on-error`) | 5 | Medium | High | Medium |
| 31 | Right-size runners | 5 | Low | Medium | Low |
| 32 | Consolidate workflow count (121 -> ~60-70) | 5 | High | Medium | Medium |
