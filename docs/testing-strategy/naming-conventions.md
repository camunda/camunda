# Naming Conventions

> Back to [Testing Strategy](./README.md)

## File Naming

| Test Type | Suffix | Example | Executed By |
|-----------|--------|---------|------------|
| Unit test | `*Test.java` | `BatchOperationPageProcessorTest.java` | Surefire |
| Integration test | `*IT.java` | `ElasticsearchExporterIT.java` | Failsafe |
| Architecture test | `*ArchTest.java` | `VisibleForTestingArchTest.java` | Surefire (in `qa/archunit-tests`) |
| Property-based test | `*PropertyTest.java` | `RandomizedPropertyTest.java` | Surefire (dedicated profile) |
| Playwright spec | `*.spec.ts` | `login.spec.ts` | Playwright Test |

**Critical**: Using the wrong suffix means the test runs with the wrong plugin (or not at all).
- `*Test.java` -> Surefire (unit tests, no Docker, fast)
- `*IT.java` -> Failsafe (integration tests, Docker allowed, lifecycle hooks)

## Method Naming

**Java test methods** must use the `should<Verb><Object>` pattern:

```java
// CORRECT
void shouldRejectInvalidCredentials()
void shouldReturnEmptyListWhenNoProcessesExist()
void shouldCreateProcessInstanceWithVariables()
void shouldFailWhenDatabaseIsUnavailable()

// INCORRECT
void test1()
void testCreateProcess()
void createProcessWorks()
void invalidCredentials()
```

**Playwright test descriptions** should describe the user action or scenario:

```typescript
// CORRECT
test('Log in with valid user account', ...)
test('Resolve an incident @roundtrip', ...)
test('should display error when process not found', ...)

// INCORRECT
test('test login', ...)
test('TC-001', ...)
```

## Test Class Location

| Test Type | Location | Example |
|-----------|----------|---------|
| Unit tests | Same module: `src/test/java/` | `zeebe/engine/src/test/java/.../MyTest.java` |
| Integration tests for a module | Same module or module's `qa/` | `operate/qa/integration-tests/` |
| Cross-module acceptance tests | `qa/acceptance-tests/` | `qa/acceptance-tests/src/test/java/` |
| Architecture tests | `qa/archunit-tests/` | `qa/archunit-tests/src/test/java/` |
| Playwright E2E | Module's `e2e-playwright/` or `qa/c8-orchestration-cluster-e2e-test-suite/` | `operate/client/e2e-playwright/` |
