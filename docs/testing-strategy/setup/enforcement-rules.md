# Enforcement Rules

> Back to [Testing Strategy](../README.md)

## Automated Enforcement (CI)

| Rule | Enforcement Mechanism | Status |
|------|----------------------|--------|
| AssertJ-only assertions | Checkstyle `IllegalImport` rule | Active |
| ArchUnit structural rules | `archunit-tests` CI job | Active |
| Code formatting | Spotless CI job | Active |
| OpenAPI spec validity | Spectral linting CI job | Active |
| Protobuf backward compat | Buf CI job | Active |
| No `Thread.sleep()` in tests | **Proposed**: Checkstyle or ArchUnit rule | Not yet active |
| No `waitForTimeout()` in Playwright | **Proposed**: ESLint rule | Not yet active |
| Test naming conventions (`should*`) | **Proposed**: ArchUnit rule | Not yet active |
| Coverage thresholds | **Proposed**: JaCoCo minimum limits | Not yet active |

## Code Review Enforcement (Human)

Reviewers must verify:
1. Appropriate test level (unit > integration > E2E)
2. Test quality (single behavior, clear naming, no anti-patterns)
3. No test-less production code changes (unless pure refactoring with unchanged behavior)
4. PR checklist is complete

## Proposed New Automated Rules

### Priority 1: Three Hard CI Rules (Enforce Immediately)

These three rules should be the first to go live. Start with exactly these three — more rules can be added later once the team demonstrates it can maintain compliance.

**1. ArchUnit `FreezingArchRule` — No NEW `Thread.sleep()` in test code:**

> Use `FreezingArchRule` to capture existing violations as a baseline. Only new violations fail the build. This enables gradual migration without breaking existing code.

```java
@ArchTest
static final ArchRule noThreadSleepInTests = FreezingArchRule.freeze(
    noClasses()
        .that().resideInAPackage("..test..")
        .should().callMethod(Thread.class, "sleep", long.class)
        .because("Use Awaitility instead of Thread.sleep() in tests"));
```

**2. ESLint rule — No `waitForTimeout()` in Playwright:**

Add `/* eslint-disable */` comments on the 15 existing files as a temporary baseline, then remove as files are fixed.

```json
{
  "rules": {
    "no-restricted-syntax": ["error", {
      "selector": "CallExpression[callee.property.name='waitForTimeout']",
      "message": "Use assertion-based waiting instead of waitForTimeout()"
    }]
  }
}
```

**3. ArchUnit rule — No new JUnit 4 usage:**

```java
@ArchTest
static final ArchRule noNewJUnit4 = noClasses()
    .should().dependOnClassesThat().resideInAPackage("org.junit")
    .andShould().not().dependOnClassesThat().resideInAPackage("org.junit.jupiter..")
    .because("New tests must use JUnit Jupiter, not JUnit 4");
```

### Priority 2: Additional Rules (After Priority 1 is Stable)

**ArchUnit rule — Test naming conventions (`should*`):**
```java
@ArchTest
static final ArchRule testMethodNaming = methods()
    .that().areAnnotatedWith(Test.class)
    .should().haveNameStartingWith("should")
    .because("Test methods must follow the should<Verb><Object> pattern");
```

**ArchUnit rule — No `@Disabled` without issue link:**
```java
// Custom rule that checks @Disabled annotation value contains "github.com/camunda/camunda/issues/"
```

**ArchUnit rule — TestContainers use `TestSearchContainers` factory:**
```java
// Custom rule banning direct ElasticsearchContainer/OpensearchContainer instantiation
```

### Metric Ratchets

CI should track these counts and fail any PR that increases them beyond the current baseline:

| Metric | Current Baseline | Tracked By |
|--------|-----------------|------------|
| `Thread.sleep()` in test files | 52 | FreezingArchRule violation store |
| `waitForTimeout()` in Playwright | 15 | ESLint baseline comments |
| `@Disabled` without issue link | TBD (measure) | Custom ArchUnit rule |
| JUnit 4 test files | 804 | ArchUnit / grep |
