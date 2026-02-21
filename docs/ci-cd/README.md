# CI/CD Improvement Plan: Path to Continuous Delivery

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
| [Phase 0: Quick Wins](./phase-0-quick-wins.md) | Week 1-2 | Build config, low-hanging fruit | -30% compute |
| [Phase 1: Flaky Test Remediation](./phase-1-flaky-test-remediation.md) | Week 2-6 | Pipeline trustworthiness | Reliable CI |
| [Phase 2: Build Artifact Sharing](./phase-2-build-artifact-sharing.md) | Week 2-8 | Eliminate redundant builds | -55% compute |
| [Phase 3: Test Strategy](./phase-3-test-strategy.md) | Week 8-16 | Testing pyramid, OpenAPI contract validation | Faster feedback |
| [Phase 4: Developer Experience](./phase-4-developer-experience.md) | Week 12-20 | Local dev, JUnit migration | <5 min local tests |
| [Phase 5: Pipeline Architecture](./phase-5-pipeline-architecture.md) | Week 16-24 | CD-ready pipeline | ~20 min PR time |

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
| [Current State Assessment](./current-state.md) | Key numbers, architecture, what's done well |
| [Root Cause Analysis](./root-cause-analysis.md) | Slowness, flakiness, developer CI dependency |
| [Success Metrics](./success-metrics.md) | Full metrics table across all phases |
| [Priority Summary](./priority-summary.md) | All 32 action items ranked |
| [Workflow Inventory](./reference/workflow-inventory.md) | All GitHub Actions workflow tables |
| [Detailed Findings](./reference/detailed-findings.md) | Findings tables by area |

## Related Documentation

- [Testing Strategy Manifesto](../testing-strategy/README.md) — Testing practices, patterns, and policies
- [Flaky Test Policy](../testing-strategy/flaky-test-policy.md) — Budget, quarantine, runbook
- [Contract Tests](../testing-strategy/contract-tests.md) — Pact consumer-driven contract testing
- [Enforcement Rules](../testing-strategy/setup/enforcement-rules.md) — ArchUnit, ESLint, Checkstyle
