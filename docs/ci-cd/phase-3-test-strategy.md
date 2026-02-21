# Phase 3: Test Strategy Restructuring (Week 8-16)

> Back to [CI/CD Improvement Plan](./README.md)

**Goal**: Shift left in the testing pyramid — faster feedback, more reliable tests. Adopt the [Testing Strategy Manifesto](../testing-strategy/README.md) as the binding standard for all new work.

> **Prerequisite**: Phase 1 (flaky test remediation) must be substantially complete. The pipeline
> must be reliable before restructuring what tests run where.
>
> **Important**: Activate enforcement rules (ArchUnit, ESLint) *before* socializing the manifesto.
> Automated enforcement is more effective than cultural change alone.

---

## 6.1 Adopt the Testing Strategy Manifesto

The [Testing Strategy Manifesto](../testing-strategy/README.md) defines:
- What tests are required for every change type (feature, bugfix, incident, refactoring)
- [Prohibited patterns](../testing-strategy/prohibited-patterns.md) (Thread.sleep, waitForTimeout, JUnit 4 in new code, etc.)
- [Required patterns](../testing-strategy/required-patterns.md) (given/when/then, Awaitility, TestSearchContainers, Page Object Model)
- [Naming conventions](../testing-strategy/naming-conventions.md), [framework choices](../testing-strategy/setup/frameworks-and-tools.md), and [enforcement mechanisms](../testing-strategy/setup/enforcement-rules.md)
- The PR checklist that every code change must satisfy

**Action**: Socialize the manifesto with all teams, integrate it into `CONTRIBUTING.md`, and create a PR template with the testing checklist.

---

## 6.2 Establish the Testing Pyramid

For the full testing pyramid definition and layer details, see the [Testing Strategy README](../testing-strategy/README.md).

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

If Pact is pursued, start with **PactFlow bidirectional testing** — compare consumer pacts against the existing OpenAPI spec without provider-side test code. This is the lightest-weight approach for a monorepo. See [Contract Tests](../testing-strategy/contract-tests.md) for the full guide.

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
