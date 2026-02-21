# Frameworks and Tools

> Back to [Testing Strategy](../README.md)

## Mandatory Frameworks

| Purpose | Framework | Version Source |
|---------|-----------|----------------|
| Test runner | JUnit 5 (Jupiter) | `parent/pom.xml` |
| Assertions | AssertJ | `parent/pom.xml` |
| Mocking | Mockito | `parent/pom.xml` |
| Async assertions | Awaitility | `parent/pom.xml` |
| Containers | TestContainers | `parent/pom.xml` |
| Contract testing (Java) | Pact JVM (`au.com.dius.pact`) | `parent/pom.xml` (to be added) |
| Contract testing (JS/TS) | Pact JS (`@pact-foundation/pact`) | `package.json` (to be added) |
| Browser E2E | Playwright Test | `package.json` |
| Architecture | ArchUnit | `parent/pom.xml` |
| Test data generation | Instancio | `parent/pom.xml` |

## Prohibited Frameworks

| Framework | Reason | Alternative |
|-----------|--------|-------------|
| JUnit 4 (in new code) | Legacy, no parallel execution support | JUnit 5 |
| Hamcrest | Inconsistent with AssertJ conventions | AssertJ |
| PowerMock | Fragile, encourages bad design | Refactor to use interfaces + Mockito |
| Selenium | Superseded | Playwright |

## Spring Test Slicing (Use the Narrowest Slice)

| Annotation | When to Use | Startup Time |
|-----------|-------------|-------------|
| No Spring annotations | Unit tests — preferred | 0ms |
| `@WebMvcTest(Controller.class)` | REST controller slice tests | ~2-3s |
| `@DataJpaTest` | Repository/DAO tests | ~2-3s |
| `@SpringBootTest(classes = {...})` | Targeted integration tests | ~5-10s |
| `@SpringBootTest` (full) | Acceptance tests only | ~15-30s |
