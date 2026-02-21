# Phase 5: Pipeline Architecture for CD (Week 12-20)

> Back to [CI/CD Improvement Plan](./README.md)

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
