# Phase 4: Developer Experience (Week 8-16)

> Back to [CI/CD Improvement Plan](./README.md)

**Goal**: Enable developers to run relevant tests locally in <5 minutes, reducing CI dependency.

---

## 7.1 Optimize Local Build Time

Summary of all local build improvements (many from [Phase 0](./phase-0-quick-wins.md)):

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

> **Note**: For the prohibition of JUnit 4 in new code, see [Prohibited Patterns](../testing-strategy/prohibited-patterns.md).

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
