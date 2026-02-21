# Phase 0: Quick Wins (Week 1-2)

> Back to [CI/CD Improvement Plan](./README.md)

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
