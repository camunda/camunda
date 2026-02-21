# Testing Strategy Manifesto — Consolidated Document

> This is a consolidated version of the Testing Strategy Manifesto for easier reading and import. For the structured version, see the [`docs/testing-strategy/`](./testing-strategy/) folder.

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
| Unit Tests | Rules, gold standard examples |
| Integration Tests | TestContainers, Spring slicing, patterns |
| Contract Tests | Consumer-driven contract testing with Pact |
| E2E Tests | Playwright rules, Page Object Model |
| Architecture Tests | ArchUnit rules and coverage |
| Performance & Reliability Tests | JMH, chaos engineering |
| Prohibited Patterns | 8 anti-patterns with examples |
| Required Patterns | 6 required patterns with examples |
| Naming Conventions | File suffixes, method naming, locations |
| Flaky Test Policy | Budget, quarantine, runbook, root causes |
| Test Data Management | Isolation, builders, Instancio |

## Setup & Configuration

| Guide | Description |
|-------|-------------|
| Frameworks and Tools | Mandatory/prohibited frameworks, versions |
| Enforcement Rules | ArchUnit, ESLint, Checkstyle config |
| Pact Setup | Dependencies, broker, CI YAML |

## Reference

| Guide | Description |
|-------|-------------|
| Examples | Gold standard file paths, utilities |

---

# Unit Tests

## Definition

A unit test verifies a single class or function in complete isolation. All collaborators are mocked or stubbed. No I/O, no network, no database, no file system, no Docker containers.

## Rules

1. **One assertion focus per test** — test a single behavior, not multiple scenarios
2. **Given/When/Then structure** — every test must use `// given`, `// when`, `// then` comments
3. **No shared mutable state** — use `@BeforeEach` for per-test setup, never static mutable fields
4. **Mock external dependencies** — use `@ExtendWith(MockitoExtension.class)` and `@Mock` / `@InjectMocks`
5. **Fast** — individual unit tests should execute in < 100ms. If a "unit test" needs > 1 second, it's an integration test
6. **No Spring context** — unit tests must never use `@SpringBootTest`, `@WebMvcTest`, or any Spring test annotation that loads an application context

## Gold Standard Example

From `zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/batchoperation/scheduler/BatchOperationPageProcessorTest.java`:

```java
class BatchOperationPageProcessorTest {

  private static final long BATCH_OPERATION_KEY = 123L;
  private static final int CHUNK_SIZE = 2;

  private BatchOperationPageProcessor processor;
  private TaskResultBuilder mockTaskResultBuilder;

  @BeforeEach
  void setUp() {
    processor = new BatchOperationPageProcessor(CHUNK_SIZE);
    mockTaskResultBuilder = mock(TaskResultBuilder.class);
  }

  @Test
  void shouldProcessPageWithSingleChunk() {
    // given
    final var item1 = new Item(100L, 200L, null);
    final var item2 = new Item(101L, 201L, null);
    final var page = new ItemPage(List.of(item1, item2), "cursor123", 2L, false);
    when(mockTaskResultBuilder.canAppendRecords(any(), any())).thenReturn(true);

    // when
    final var result = processor.processPage(BATCH_OPERATION_KEY, page, mockTaskResultBuilder);

    // then
    assertThat(result.chunksAppended()).isTrue();
    assertThat(result.endCursor()).isEqualTo("cursor123");
    assertThat(result.itemsProcessed()).isEqualTo(2);
    assertThat(result.isLastPage()).isFalse();
  }
}
```

**Why this is the standard:**
- Clear naming: `shouldProcessPageWithSingleChunk`
- Constants are named: `BATCH_OPERATION_KEY`, `CHUNK_SIZE`
- `@BeforeEach` creates fresh instances — no shared state
- Single behavior per test
- Given/when/then structure
- AssertJ assertions
- No Spring context, no I/O

---

# Integration Tests

## Definition

An integration test verifies the interaction between two or more real components — typically your code with a real database, search engine, or message broker. External dependencies run in Docker via TestContainers.

## Rules

1. **Use TestContainers** — never depend on pre-installed infrastructure. Tests must be self-contained
2. **Use the `TestSearchContainers` factory** (`zeebe/test-util`) for all Elasticsearch/OpenSearch containers — this is the single source of truth for container image versions
3. **Prefer singleton containers** — use `static @Container` with `@Testcontainers` to share a container across all tests in a class. Starting a new container per test is wasteful (~5-10 seconds per start)
4. **Clean up between tests** — truncate data, reset indexes, or use unique prefixes. Never rely on test execution order
5. **File naming** — integration tests MUST end with `IT.java` (e.g., `UserServiceIT.java`). This ensures Failsafe (not Surefire) executes them
6. **Timeout** — individual integration tests should complete in < 30 seconds. If a test needs more, it likely tests too much
7. **Use `Awaitility`** for eventual consistency — never `Thread.sleep()`

## Gold Standard Example

From `zeebe/exporters/elasticsearch-exporter/src/test/java/io/camunda/zeebe/exporter/ElasticsearchExporterIT.java`:

```java
@Testcontainers
final class ElasticsearchExporterIT {

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer()
          .withEnv("action.destructive_requires_name", "false");

  @BeforeEach
  public void beforeEach(final TestInfo testInfo) {
    // Only recreate indexes when the test method changes (performance optimization)
    final String currentTestMethod = getTestMethod(testInfo);
    if (!currentTestMethod.equals(previousTestMethod)) {
      if (previousTestMethod != null) {
        deletedIndices();
      }
      setup();
      previousTestMethod = currentTestMethod;
    }
  }
}
```

**Why this is the standard:**
- Singleton container (`static @Container`) — started once for the class
- `TestSearchContainers` factory for consistent versioning
- Smart cleanup: only resets state when the test method changes
- Self-contained: no dependency on external infrastructure

## TestContainers Patterns

**Singleton container (preferred for most cases):**
```java
@Testcontainers
class MyServiceIT {
  @Container
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefeaultElasticsearchContainer();
}
```

**Network-aware multi-container (for cross-service tests):**
```java
class CrossServiceIT {
  private Network network;
  private final List<GenericContainer<?>> containers = new ArrayList<>();

  @BeforeEach
  void setUp() {
    network = Network.newNetwork();
  }

  @AfterEach
  void tearDown() {
    containers.forEach(GenericContainer::stop);
    network.close();
  }
}
```

Reference: `dist/src/test/java/io/camunda/application/AbstractCamundaDockerIT.java`

## Spring Integration Tests

When Spring context is required, use the narrowest possible slice:

| Annotation | Use When | Context Size |
|-----------|----------|-------------|
| `@WebMvcTest(Controller.class)` | Testing a single REST controller | Minimal (web layer only) |
| `@DataJpaTest` | Testing repository/DAO layer | JPA layer only |
| `@SpringBootTest(classes = {SpecificConfig.class})` | Testing with specific beans only | Specified classes only |
| `@SpringBootTest` (full) | **Last resort** — only for acceptance tests | Full application context |

---

# Contract Tests

## Definition

A contract test verifies the **agreement between a consumer and a provider** at their API boundary, without starting the full system. Unlike integration tests that verify internal wiring, contract tests verify that:

- **Consumers** can rely on the API behaving as they expect (consumer-driven)
- **Providers** don't accidentally break consumers when they change the API
- **Services can be deployed independently** with confidence that they remain compatible

Contract tests are **not** controller slice tests (`@WebMvcTest`). Controller slice tests verify internal wiring between a controller and its service layer — that is an integration test concern. Contract tests verify the **external API contract** between independently deployable components.

## Camunda Service Communication Architecture

Understanding the architecture is essential for defining contract boundaries:

```
                    +----------------------------------+
                    |         v2 REST API              |
                    |   (Orchestration Cluster API)    |
                    |   rest-api.yaml (OpenAPI 3.0.3)  |
                    +---------------+------------------+
                                    |
               +--------------------+--------------------+
               |                    |                    |
      Operate Frontend      Tasklist Frontend      Java Client
      (fetch + Zod)         (fetch + Zod)          (HTTP + gRPC)
               |                    |                    |
               |   +----------------+                    |
               |   |                                     |
      Operate Backend     Tasklist Backend               |
      (gRPC writes)       (gRPC writes)                  |
               |                    |                    |
               +--------+---------+                     |
                        |                                |
               Zeebe Gateway / Broker <-----------------+
                        |
                        | [Record Stream]
                        v
                 Camunda Exporter
                        |
                        | [Bulk Write]
                        v
                Elasticsearch / OpenSearch
                        |
            +-----------+-----------+
            |           |           |
         Operate    Tasklist    Optimize
         (reads)    (reads)     (reads)
```

**Key insight**: Operate, Tasklist, and Optimize do **not** call each other. They share data only through Elasticsearch/OpenSearch indices written by the Camunda Exporter. The v2 REST API is the single unified provider for all consumers.

## Contract Boundary Definitions

| Consumer | Provider | Protocol | Contract Type | Tool |
|----------|----------|----------|--------------|------|
| **Java Client** | v2 REST API | HTTP/JSON | REST consumer contract | **Pact** |
| **Operate Frontend** | v2 REST API | HTTP/JSON | REST consumer contract | **Pact** |
| **Tasklist Frontend** | v2 REST API | HTTP/JSON | REST consumer contract | **Pact** |
| **Operate Frontend** | Operate Internal API (`/api/`) | HTTP/JSON | REST consumer contract (legacy) | **Pact** |
| **Optimize Frontend** | Optimize Backend REST API | HTTP/JSON | REST consumer contract | **Pact** |
| **Operate/Tasklist Backend** | Zeebe gRPC | gRPC/Protobuf | Schema compatibility | **Buf** (already in CI) |
| **Camunda Exporter** | ES/OS index schema | Data/Message | Message contract | **Pact message** or schema assertions |
| **Any consumer** | v2 REST API spec | OpenAPI 3.0.3 | Spec validity | **Spectral** (already in CI) |

## Java Client Consumer Tests (Priority 1)

The Java client (`clients/java/`) is the highest-priority Pact consumer because it is the **public API contract** used by all external integrators.

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "camunda-orchestration-api", port = "8080")
class JobApiContractTest {

  @Pact(consumer = "camunda-java-client")
  V4Pact failJobContract(PactDslWithProvider builder) {
    return builder
        .given("a job with key 123 exists")
        .uponReceiving("a request to fail job 123")
          .path("/v2/jobs/123/failure")
          .method("POST")
          .headers("Content-Type", "application/json")
          .body(new PactDslJsonBody()
              .integerType("retries", 1)
              .stringType("errorMessage", "task failed")
              .integerType("retryBackOff", 1000))
        .willRespondWith()
          .status(204)
        .toPact(V4Pact.class);
  }

  @Test
  @PactTestFor(pactMethod = "failJobContract")
  void shouldFailJob(MockServer mockServer) {
    // given
    var client = CamundaClient.newClientBuilder()
        .restAddress(URI.create(mockServer.getUrl()))
        .preferRestOverGrpc(true)
        .build();

    // when + then — no exception means contract is satisfied
    client.newFailCommand(123L)
        .retries(1)
        .errorMessage("task failed")
        .retryBackoff(Duration.ofSeconds(1))
        .send()
        .join();
  }
}
```

**What this catches that `@WebMvcTest` doesn't:**
- If the Java client sends a field the provider doesn't expect -> contract fails
- If the provider removes a field the client depends on -> contract fails
- If the client assumes a 200 but the provider returns 204 -> contract fails
- These failures are detected **without running the provider at all**

## Frontend Consumer Tests (Priority 2)

The Operate and Tasklist frontends already use `@camunda/camunda-api-zod-schemas` for typed API calls. Pact JS can generate contracts from these.

```typescript
import {PactV4} from '@pact-foundation/pact';

const provider = new PactV4({
  consumer: 'tasklist-frontend',
  provider: 'camunda-orchestration-api',
});

describe('User Tasks API Contract', () => {
  it('should search user tasks', async () => {
    await provider
      .addInteraction()
      .given('user tasks exist')
      .uponReceiving('a search request for user tasks')
      .withRequest('POST', '/v2/user-tasks/search', (builder) => {
        builder.headers({'Content-Type': 'application/json'});
        builder.jsonBody({
          filter: {state: 'CREATED', assigned: true},
          page: {limit: 50},
        });
      })
      .willRespondWith(200, (builder) => {
        builder.headers({'Content-Type': 'application/json'});
        builder.jsonBody({
          items: eachLike({
            userTaskKey: string('12345'),
            name: string('Review document'),
            taskState: string('CREATED'),
            assignee: string('demo'),
          }),
          page: {totalItems: integer(1)},
        });
      })
      .executeTest(async (mockServer) => {
        const response = await fetch(`${mockServer.url}/v2/user-tasks/search`, {
          method: 'POST',
          headers: {'Content-Type': 'application/json'},
          body: JSON.stringify({
            filter: {state: 'CREATED', assigned: true},
            page: {limit: 50},
          }),
        });

        expect(response.status).toBe(200);
        const body = await response.json();
        expect(body.items).toBeDefined();
        expect(body.items[0].userTaskKey).toBeDefined();
      });
  });
});
```

## Provider Verification (Zeebe Gateway)

The Zeebe gateway (the v2 REST API provider) verifies all consumer contracts:

```java
@Provider("camunda-orchestration-api")
@PactBroker(url = "${PACT_BROKER_URL}")  // or @PactFolder("pacts") for local dev
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrchestrationApiProviderVerificationIT {

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void verifyPact(PactVerificationContext context) {
    context.verifyInteraction();
  }

  @State("a job with key 123 exists")
  void setupJobExists() {
    // Create test data in the provider's state
  }

  @State("user tasks exist")
  void setupUserTasksExist() {
    // Create test data for user task search
  }
}
```

This runs on every PR that changes the gateway, catching any provider-side breaks before merge.

## Exporter Data Contract (Priority 3)

The Elasticsearch index schema is an implicit data contract between the Camunda Exporter (producer) and Operate/Tasklist/Optimize (consumers). Use **Pact message contracts** for this:

```java
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "camunda-exporter", providerType = ProviderType.ASYNCH)
class OperateExporterContractTest {

  @Pact(consumer = "operate")
  MessagePact processInstanceRecord(MessagePactBuilder builder) {
    return builder
        .expectsToReceive("a process instance CREATED record")
        .withContent(new PactDslJsonBody()
            .integerType("key")
            .stringType("intent", "CREATED")
            .object("value")
              .stringType("bpmnProcessId")
              .integerType("processDefinitionKey")
              .integerType("version")
            .closeObject())
        .toPact();
  }

  @Test
  @PactTestFor(pactMethod = "processInstanceRecord")
  void shouldConsumeProcessInstanceRecord(List<Message> messages) {
    // Verify Operate can deserialize the exporter record
    var record = objectMapper.readValue(
        messages.get(0).contentsAsString(), ProcessInstanceRecord.class);
    assertThat(record.getKey()).isNotNull();
    assertThat(record.getValue().getBpmnProcessId()).isNotNull();
  }
}
```

## What Existing Tests Become

| Current Test | Current Classification | Correct Classification |
|-------------|----------------------|----------------------|
| `JobControllerTest` (`@WebMvcTest`) | Was labeled "contract test" | **API slice test** (integration test layer) — verifies controller wiring |
| Spectral OpenAPI linting | Spec validation | **Spec validation** (keep as-is, complementary to Pact) |
| Buf protobuf check | gRPC schema compat | **Schema contract test** (keep as-is) |
| `validateResponse()` in E2E API tests | API response validation | **E2E validation** (keep, but too slow for PR feedback) |
| Zod schema validation (frontend) | Runtime type checking | **Client-side validation** (complementary to Pact) |

The existing `@WebMvcTest` tests (like `JobControllerTest`) are still valuable — they verify that the controller correctly parses requests and delegates to services. They just aren't contract tests. They should be classified as **API slice tests** within the integration test layer.

## When to Write Contract Tests

**Rule: Every new or modified REST/gRPC endpoint must have a Pact consumer contract test.** This applies to all API surfaces — v2, legacy internal, Optimize backend, or any other endpoint consumed by another application or frontend.

| Change | Contract Test Required? |
|--------|----------------------|
| **New REST endpoint** (any API surface) | **Yes** — Pact consumer test for each consuming application |
| **Modified REST endpoint** (request/response change) | **Yes** — update existing Pact consumer tests |
| **Removed or renamed API field** | **Yes** — Pact will catch if any consumer depends on it |
| **New field in exporter record** | **Yes** — Pact message contract for consuming applications |
| **New or modified gRPC service method** | **Yes** — Buf backward compat (already enforced) + Pact consumer test if consumed by Java client |
| **New query parameter, header, or status code** | **Yes** — if any consumer relies on it |
| Internal refactoring (no API change) | No |

**No endpoint ships without a contract.** If the endpoint has no consumer test, either:
1. There is no consumer yet (add the contract when the first consumer is built), or
2. The consumers are untested — fix that first

## Rollout Strategy

| Phase | Scope | Timeline |
|-------|-------|----------|
| **Phase A** | Java Client -> v2 REST API (file-based pacts in repo) | Week 1-3 |
| **Phase B** | Tasklist Frontend -> v2 REST API (leveraging Zod schemas) | Week 2-4 |
| **Phase C** | Operate Frontend -> v2 REST API + legacy `/api/` | Week 3-5 |
| **Phase D** | Exporter -> ES/OS index schema (Pact message contracts) | Week 4-6 |
| **Phase E** | Optimize Frontend -> Optimize Backend REST API | Week 5-7 |
| **Phase F** | Set up Pact Broker + `can-i-deploy` in CI | Week 6-8 |

---

# End-to-End Tests

## Definition

An E2E test exercises the full system through its external interfaces (UI or API) with all real dependencies running. These are the most expensive tests and should be used sparingly.

## Rules

1. **E2E tests do NOT block PRs** — they run nightly and on release branches only
2. **Use Playwright** for all browser-based E2E tests
3. **Use the Page Object Model** — all page interactions go through page objects, never raw selectors in test files
4. **Never use `waitForTimeout()`** — use assertion-based waiting (`expect(locator).toBeVisible()`, `expect.poll()`)
5. **Never use `actionTimeout: 0`** — always set a bounded action timeout (10 seconds)
6. **Use accessible selectors** — `getByRole()`, `getByLabel()`, `getByText()` over `getByTestId()` over CSS selectors
7. **Retries are not a substitute for reliability** — `retries: 2` in Playwright config is acceptable for CI resilience, but a test that needs retries to pass should be investigated
8. **Use `test.step()`** for multi-step tests — provides clear failure attribution

## Gold Standard Example

From `operate/client/e2e-playwright/tests/login.spec.ts`:

```typescript
test.describe('login page', () => {
  test('Log in with invalid user account', async ({loginPage, page}) => {
    await loginPage.login({username: 'demo', password: 'wrong-password'});
    await expect(
      page.getByRole('alert').getByText('Username and password do not match'),
    ).toBeVisible();
    await expect(page).toHaveURL('/operate/login');
  });

  test('Log in with valid user account', async ({loginPage, page}) => {
    await loginPage.login({username: 'demo', password: 'demo'});
    await expect(page).toHaveURL('../operate');
  });
});
```

**Why this is the standard:**
- No `waitForTimeout` — all waits are assertion-based
- Page Object Model via fixtures (`loginPage`)
- Accessible selectors (`getByRole('alert')`)
- Short, focused — one scenario per test
- No hardcoded URLs or magic strings

## Page Object Model

Every page or component that tests interact with must have a corresponding page object class:

```typescript
// pages/Login.ts
export class Login {
  private page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel(/^username$/i);
    this.passwordInput = page.getByLabel(/^password$/i);
    this.loginButton = page.getByRole('button', {name: 'Login'});
  }

  async login(credentials: {username: string; password: string}) {
    await this.fillUsername(credentials.username);
    await this.fillPassword(credentials.password);
    await this.clickLoginButton();
  }
}
```

Reference: `operate/client/e2e-playwright/pages/Login.ts`

## Eventual Consistency in E2E Tests

Use `expect.poll()` or `expect().toPass()` for assertions that depend on asynchronous data:

```typescript
// CORRECT: polling with timeout
await expect.poll(async () => {
  const response = await request.get(`${baseUrl}/v1/process-instances/${key}`);
  return response.status();
}, {timeout: 30_000}).toBe(200);

// CORRECT: retry assertion block
await expect(async () => {
  const res = await request.get(buildUrl(`/process-instances/${key}`));
  await assertStatusCode(res, 200);
  await validateResponse({path: '/process-instances/{key}', method: 'GET', status: '200'}, res);
}).toPass(defaultAssertionOptions);

// WRONG: hardcoded wait
await page.waitForTimeout(5000);  // NEVER DO THIS
```

Reference: `operate/client/e2e-playwright/tests/processInstance.spec.ts`

---

# Architecture Tests

## Definition

Architecture tests (ArchUnit) verify structural properties of the codebase: dependency directions, naming conventions, annotation usage, and module boundaries.

## Rules

1. All ArchUnit tests live in `qa/archunit-tests/` — not scattered across modules
2. All ArchUnit test classes must be named `*ArchTest.java`
3. ArchUnit tests run on every PR as part of the `archunit-tests` CI job
4. New architectural constraints should be proposed via PR and reviewed by the team

## Current Coverage

The following architectural properties are enforced (see `qa/archunit-tests/`):

- Processor naming conventions
- `@VisibleForTesting` usage (only accessed from test code)
- Client dependency isolation (no protocol dependency)
- Controller authorization patterns
- Engine class dependency rules
- Protocol immutability
- REST controller annotations
- State modification rules
- Migration task registration

---

# Performance and Reliability Tests

## Performance Tests

- Run **weekly** and before releases — never on PRs
- Use JMH for microbenchmarks (`microbenchmarks/` module)
- Use the load tester (`load-tests/load-tester/`) for system-level throughput tests
- Throughput SLOs: 50 PI/s (typical), 300 PI/s (stress) — see `docs/testing/reliability-testing.md`

## Reliability Tests

- Chaos engineering via `zbchaos` CLI
- Automated chaos experiments via Camunda BPMN processes
- Run weekly on dedicated GKE infrastructure

---

# Prohibited Patterns

These patterns are **banned** from all new code and must be removed from existing code during maintenance.

---

## 1. `Thread.sleep()` in test code

```java
// PROHIBITED
Thread.sleep(5000);
assertThat(service.getStatus()).isEqualTo("READY");

// REQUIRED: use Awaitility
Awaitility.await()
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(() ->
        assertThat(service.getStatus()).isEqualTo("READY"));
```

**Exception**: `Thread.sleep()` is acceptable only in production code for deliberate backoff/throttling, never in tests.

---

## 2. `page.waitForTimeout()` in Playwright tests

```typescript
// PROHIBITED
await page.waitForTimeout(500);

// REQUIRED: use assertion-based waiting
await expect(page.locator('.modal')).toBeVisible();
```

**No exceptions.**

---

## 3. `actionTimeout: 0` in Playwright configuration

```typescript
// PROHIBITED
actionTimeout: 0,  // unlimited — will hang forever

// REQUIRED
actionTimeout: 10_000,  // bounded timeout
```

---

## 4. JUnit / Hamcrest assertions

```java
// PROHIBITED (enforced by Checkstyle)
assertEquals(expected, actual);        // JUnit
assertThat(actual, is(expected));      // Hamcrest

// REQUIRED
assertThat(actual).isEqualTo(expected);  // AssertJ
```

This is already enforced by Checkstyle (`IllegalImport` rule in `build-tools/src/main/resources/check/.checkstyle.xml`).

---

## 5. JUnit 4 in new code

```java
// PROHIBITED in new code
@RunWith(SpringRunner.class)
@Rule public final EngineRule engine = EngineRule.singlePartition();

// REQUIRED
@ExtendWith(SpringExtension.class)
@RegisterExtension final EngineExtension engine = EngineExtension.create();
```

> **Note**: For the JUnit 4 -> 5 migration plan for existing code, see CI/CD Phase 4: Developer Experience.

---

## 6. Raw `Thread` management in concurrency tests

```java
// PROHIBITED
Thread[] threads = new Thread[5];
for (int i = 0; i < threads.length; i++) {
  threads[i] = new Thread(task);
  threads[i].start();
  Thread.sleep(10);
}

// REQUIRED
ExecutorService executor = Executors.newFixedThreadPool(5);
CountDownLatch startLatch = new CountDownLatch(1);
for (int i = 0; i < 5; i++) {
  executor.submit(() -> {
    startLatch.await();  // all threads start simultaneously
    task.run();
  });
}
startLatch.countDown();
executor.shutdown();
executor.awaitTermination(10, TimeUnit.SECONDS);
```

---

## 7. Non-deterministic test data without seed

```java
// PROHIBITED — different data each run, unreproducible failures
var randomName = UUID.randomUUID().toString();

// REQUIRED — seeded random or deterministic
var testName = "test-process-" + testInfo.getDisplayName().hashCode();

// OR use Instancio with seed
var data = Instancio.of(MyModel.class)
    .withSeed(12345L)
    .create();
```

**Exception**: UUIDs are acceptable when used for isolation (unique index names, unique process IDs) and the specific value doesn't matter for the assertion.

---

## 8. `@Disabled` without an issue link

```java
// PROHIBITED
@Disabled("it's broken")

// REQUIRED
@Disabled("Flaky, tracked in https://github.com/camunda/camunda/issues/42928")
```

---

# Required Patterns

---

## 1. Given/When/Then structure

Every test method must contain `// given`, `// when`, `// then` comments:

```java
@Test
void shouldRejectInvalidInput() {
    // given
    final var request = new CreateRequest(null, -1);

    // when
    final var result = assertThrows(ValidationException.class,
        () -> service.create(request));

    // then
    assertThat(result.getMessage()).contains("name must not be null");
}
```

---

## 2. Regression test annotation for bug fixes

```java
@Test
@RegressionTest("https://github.com/camunda/camunda/issues/12345")
void shouldNotCrashWhenInputIsEmpty() {
    // given — the exact scenario that caused the bug
    // when — the action that triggered the bug
    // then — the correct behavior
}
```

---

## 3. Awaitility for all asynchronous assertions

```java
Awaitility.await("process instance should be active")
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(200))
    .untilAsserted(() ->
        assertThat(getProcessInstance(key).getState()).isEqualTo(ACTIVE));
```

---

## 4. `expect.poll()` or `expect().toPass()` in Playwright

```typescript
await expect.poll(async () => {
  const response = await request.get(url);
  return response.status();
}, {timeout: 30_000, intervals: [500, 1000, 2000]}).toBe(200);
```

---

## 5. TestContainers via `TestSearchContainers` factory

```java
// REQUIRED: use the central factory
@Container
private static final ElasticsearchContainer CONTAINER =
    TestSearchContainers.createDefeaultElasticsearchContainer();

// PROHIBITED: hardcoded image tags
@Container
private static final ElasticsearchContainer CONTAINER =
    new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.18.4");
```

---

## 6. Page Object Model in Playwright

```typescript
// REQUIRED: interact through page objects
await loginPage.login({username: 'demo', password: 'demo'});

// PROHIBITED: raw selectors in test files
await page.locator('#username-input').fill('demo');
await page.locator('#password-input').fill('demo');
await page.locator('button[type="submit"]').click();
```

---

# Naming Conventions

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

---

# Flaky Test Policy

## Definition

A flaky test is a test that produces different results (pass/fail) on the same code without any code change. A test that passes only on retry is flaky.

## Metric Definitions

Precise definitions are critical for enforceability:

- **Test-level flaky rate** = (number of test methods that flaked at least once in a week) / (total distinct test methods executed that week). This is the primary metric. Benchmark: Google targets 1.5%, Netflix <2%, Uber <3%.
- **Pipeline flake rate** = (pipeline runs that failed due to flakes) / (total pipeline runs). This is the developer-facing metric that drives trust.

## Tiered Targets

| Phase | Test-Level Target | Pipeline Target | Timeline |
|-------|-------------------|-----------------|----------|
| End of Phase 1 | <10% | Measured | Week 6 |
| End of Phase 3 | <5% | <10% | Week 16 |
| CD Readiness | **<2%** | **<2%** | Stretch goal |

## Policy

1. **Detection**: The existing `flaky-test-extractor-maven-plugin` and BigQuery/Grafana pipeline will track flaky tests
2. **Quarantine threshold**: Flake rate >5% for tests run 10+ times/week, OR 3+ flakes for tests run <10 times/week. The threshold is rate-based (not count-based) to account for execution frequency
3. **Quarantine mechanism**: Apply JUnit 5 `@Tag("quarantine")` to the test class/method. Configure Surefire with `<excludedGroups>quarantine</excludedGroups>` for PR runs. Quarantined tests still run nightly
4. **Fix SLA**: Quarantined tests must be fixed or deleted within 2 sprints
5. **No `@Disabled` without an issue**: Every disabled test must link to a tracking issue
6. **Progressive retry reduction**: `rerunFailingTestsCount` will be reduced from 3 -> 2 -> 1 -> 0 (Surefire) with explicit go/no-go gates. Keep Failsafe at 1 permanently — see Phase 1

## Flakiness Classification

Not all flakiness is the same. Classify by root cause before setting fix expectations:

| Category | Examples | Fixable? | Approach |
|----------|----------|----------|----------|
| **Timing-dependent** | `Thread.sleep()`, hardcoded timeouts | Yes | Awaitility / `expect.poll()` |
| **Resource contention** | Tests fail under CI load but pass locally | Partially | Reduce CI waste (Phase 2), singleton containers |
| **Architecturally eventual-consistent** | Raft consensus, ES write-to-read delay | Harder | May legitimately need 1 Failsafe retry |
| **Shared mutable state** | Static fields, containers without cleanup | Yes | Per-test isolation |
| **Environment-specific** | Docker pull failures, port conflicts | Partially | Registry mirrors, OS-assigned ports |

## Flaky Test Response Runbook

When a test flakes:

1. Check the Grafana dashboard: `https://dashboard.int.camunda.com/d/ae2j69npxh3b4f/flaky-tests-camunda-camunda-monorepo`
2. Search for an existing issue: `gh issue list --label kind/flake --search "<test class name>"`
3. If no issue exists, create one using the flaky test issue template
4. Reproduce locally using IntelliJ "Repeat Until Failure" (see `docs/zeebe/failing-tests.md`)
5. Fix the root cause, don't add retry logic

## Common Root Causes and Fixes

| Root Cause | Pattern | Fix |
|-----------|---------|-----|
| Timing dependency | `Thread.sleep()`, hardcoded timeouts | Awaitility / `expect.poll()` |
| Shared mutable state | Static fields, shared containers without cleanup | Per-test isolation, `@BeforeEach` cleanup |
| Port conflicts | Random ports without checking availability | Let the OS assign ports (`0`), TestContainers handles this |
| Test order dependency | Test B depends on data created by Test A | Each test creates its own data |
| Resource exhaustion | Too many concurrent containers, file handle leaks | Singleton containers, proper `@AfterEach` cleanup |
| Eventual consistency | Assert immediately after write to async system | Awaitility polling |

---

# Test Data Management

## Principles

1. **Each test creates its own data** — never depend on data from another test
2. **Use unique identifiers** — prefix test data with test method name or unique key to prevent collisions in parallel execution
3. **Clean up after yourself** — integration tests must clean up created data in `@AfterEach` or use unique prefixes that don't collide

## Patterns

**Builder/factory pattern for test data:**
```java
// CORRECT
var process = Bpmn.createExecutableProcess("test-" + testInfo.getDisplayName())
    .startEvent()
    .userTask("task-1")
    .endEvent()
    .done();

// INCORRECT — shared, mutable, reused across tests
static final BpmnModelInstance PROCESS = Bpmn.createExecutableProcess("shared-process")...;
```

**Instancio for random but reproducible data:**
```java
var user = Instancio.of(UserEntity.class)
    .set(field(UserEntity::getEmail), "test@example.com")
    .create();
```

---

# Frameworks and Tools

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

---

# Enforcement Rules

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

---

# Pact Setup

This document covers the installation, configuration, and CI integration for Pact contract testing. For when and how to write contract tests, see the Contract Tests section above.

## Dependencies

**Java consumer (add to `clients/java/pom.xml` or relevant consumer module):**
```xml
<dependency>
  <groupId>au.com.dius.pact.consumer</groupId>
  <artifactId>junit5</artifactId>
  <version>4.6.x</version>
  <scope>test</scope>
</dependency>
```

**JavaScript/TypeScript consumer (add to `operate/client/package.json` or relevant frontend):**
```json
{
  "devDependencies": {
    "@pact-foundation/pact": "^13.x"
  }
}
```

## How Pact Works: The Full Lifecycle

> **References**:
> - Pact documentation: <https://docs.pact.io>
> - Pact JVM (Java): <https://docs.pact.io/implementation_guides/jvm>
> - Pact JS (TypeScript/JavaScript): <https://docs.pact.io/implementation_guides/javascript>
> - Consumer-Driven Contract Testing concepts: <https://docs.pact.io/getting_started/how_pact_works>
> - Pact Broker (self-hosted): <https://github.com/pact-foundation/pact_broker>
> - PactFlow (managed SaaS): <https://pactflow.io>
> - Pact + OpenAPI (bidirectional contract testing): <https://docs.pactflow.io/docs/bi-directional-contract-testing>

### Step 1: Consumer writes tests (generates contracts)

The consumer team writes Pact tests as part of their regular unit/integration test suite. When tests run, Pact generates a **contract file** (JSON) in the `target/pacts/` (Java) or `pacts/` (JS) directory.

```
# Java consumer test execution
./mvnw test -pl clients/java -Dtest=JobApiContractTest

# Output:
# target/pacts/camunda-java-client-camunda-orchestration-api.json
```

```
# JS/TS consumer test execution
cd tasklist/client && npm test -- --grep "contract"

# Output:
# pacts/tasklist-frontend-camunda-orchestration-api.json
```

The generated pact file contains every interaction the consumer expects:

```json
{
  "consumer": { "name": "camunda-java-client" },
  "provider": { "name": "camunda-orchestration-api" },
  "interactions": [
    {
      "description": "a request to fail job 123",
      "providerState": "a job with key 123 exists",
      "request": {
        "method": "POST",
        "path": "/v2/jobs/123/failure",
        "headers": { "Content-Type": "application/json" },
        "body": { "retries": 1, "errorMessage": "task failed" }
      },
      "response": {
        "status": 204
      }
    }
  ]
}
```

### Step 2: Contracts are stored and shared

Contracts must be accessible to both consumers and providers. Three options, from simplest to most mature:

**Option A: File-based (start here)**

Store pact files in the repository under a shared directory:

```
contracts/
  pacts/
    camunda-java-client-camunda-orchestration-api.json
    tasklist-frontend-camunda-orchestration-api.json
    operate-frontend-camunda-orchestration-api.json
```

Consumer CI publishes pacts by committing to this directory. Provider CI reads from it. This works well for a monorepo where consumers and providers are in the same repository.

**Pros**: Zero infrastructure, works immediately
**Cons**: No versioning, no `can-i-deploy`, no branch-aware verification

**Option B: Self-hosted Pact Broker**

Deploy the open-source Pact Broker (<https://github.com/pact-foundation/pact_broker>):

```yaml
# docker-compose.pact-broker.yml
services:
  pact-broker:
    image: pactfoundation/pact-broker:latest
    ports:
      - "9292:9292"
    environment:
      PACT_BROKER_DATABASE_URL: "sqlite:///pact_broker.sqlite3"
```

Consumers publish pacts after tests pass:
```bash
# Java (via pact-jvm plugin)
./mvnw pact:publish -Dpact.broker.url=https://pact-broker.internal.camunda.com

# JS (via @pact-foundation/pact-cli)
npx pact-broker publish ./pacts \
  --broker-base-url=https://pact-broker.internal.camunda.com \
  --consumer-app-version=$(git rev-parse HEAD) \
  --tag=$(git branch --show-current)
```

Providers fetch and verify:
```java
@Provider("camunda-orchestration-api")
@PactBroker(url = "https://pact-broker.internal.camunda.com")
class OrchestrationApiProviderVerificationIT { ... }
```

The broker provides:
- **Contract versioning**: each consumer version has its own pact
- **`can-i-deploy`**: query whether a version is safe to deploy
- **Webhooks**: trigger provider verification when a new pact is published
- **Network diagram**: visual map of all consumer-provider relationships
- **Tags/branches**: branch-aware verification (verify `main` pacts vs. feature branch pacts)

```bash
# Before deploying: check if this version is compatible with all consumers/providers
pact-broker can-i-deploy \
  --pacticipant camunda-orchestration-api \
  --version $(git rev-parse HEAD) \
  --to-environment production
```

**Pros**: Full Pact workflow, free, self-hosted
**Cons**: Requires infrastructure to host and maintain

**Option C: PactFlow (managed SaaS)**

PactFlow (<https://pactflow.io>) is the managed Pact Broker with additional features:

- **Bidirectional contract testing**: verify consumer pacts against the OpenAPI spec (`rest-api.yaml`) without running the provider
- **Teams and permissions**: control who can publish/verify
- **CI integration**: native GitHub Actions support
- **Support and SLAs**

**Bidirectional contract testing** is especially powerful here because:
1. Consumer pacts are generated from consumer tests (as normal)
2. Instead of running provider verification tests, PactFlow compares the consumer pact against the published OpenAPI spec
3. If the consumer's expectations are a subset of what the OpenAPI spec promises -> compatible
4. This means **no provider-side test code is needed** for the initial rollout

```
Consumer Pact ---+
                 +--- PactFlow comparison ---> Compatible? Y/N
OpenAPI Spec ----+
```

Reference: <https://docs.pactflow.io/docs/bi-directional-contract-testing>

**Pros**: Zero infrastructure, bidirectional testing with OpenAPI, managed
**Cons**: Paid service (free tier available for small teams)

### Step 3: Provider verifies contracts

When the provider (Zeebe gateway) changes, it verifies all consumer contracts:

```java
@Provider("camunda-orchestration-api")
@PactBroker(url = "${PACT_BROKER_URL}")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrchestrationApiProviderVerificationIT {

  @BeforeEach
  void setupTarget(PactVerificationContext context) {
    context.setTarget(new HttpTestTarget("localhost", port));
  }

  @TestTemplate
  @ExtendWith(PactVerificationInvocationContextProvider.class)
  void verifyPact(PactVerificationContext context) {
    context.verifyInteraction();
  }

  // Provider state handlers — set up test data for each consumer scenario
  @State("a job with key 123 exists")
  void setupJobExists() {
    testHelper.createJob(123L);
  }

  @State("user tasks exist")
  void setupUserTasksExist() {
    testHelper.createUserTask("Review document", "demo");
  }
}
```

### Step 4: CI Integration

```yaml
# .github/workflows/ci.yml (additions)

# Consumer side — runs as part of unit tests
consumer-contract-tests:
  name: "Contract / Consumer Tests"
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Run Java client consumer tests
      run: ./mvnw test -pl clients/java -Dgroups=contract
    - name: Run Tasklist frontend consumer tests
      run: cd tasklist/client && npm test -- --grep "contract"
    - name: Publish pacts to broker
      run: |
        ./mvnw pact:publish \
          -Dpact.broker.url=${{ secrets.PACT_BROKER_URL }} \
          -Dpact.consumer.version=${{ github.sha }} \
          -Dpact.consumer.tags=${{ github.ref_name }}

# Provider side — runs when gateway code changes
provider-contract-verification:
  name: "Contract / Provider Verification"
  needs: [consumer-contract-tests]
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - name: Verify provider against all consumer pacts
      run: |
        ./mvnw verify -pl zeebe/gateway-rest \
          -Dpact.broker.url=${{ secrets.PACT_BROKER_URL }} \
          -Dpact.provider.version=${{ github.sha }} \
          -Dpact.provider.tags=${{ github.ref_name }} \
          -Dgroups=contract

# Deployment gate
can-i-deploy:
  name: "Contract / Can I Deploy?"
  needs: [provider-contract-verification]
  runs-on: ubuntu-latest
  steps:
    - name: Check deployment compatibility
      run: |
        pact-broker can-i-deploy \
          --pacticipant camunda-orchestration-api \
          --version ${{ github.sha }} \
          --to-environment production \
          --broker-base-url ${{ secrets.PACT_BROKER_URL }}
```

## Recommended Progression

| Stage | Approach | When |
|-------|----------|------|
| **Start** | **File-based** (`contracts/pacts/` in repo) | Immediately — zero setup |
| **Scale** | **Self-hosted Pact Broker** or **PactFlow free tier** | When contract count exceeds ~10 or need `can-i-deploy` |
| **Mature** | **PactFlow** with bidirectional testing against OpenAPI spec | When ready for independent deployability |

The monorepo actually simplifies the initial rollout because consumer and provider code live in the same repo — pact files can be generated and verified in the same CI run without a broker.

---

# Reference Examples

## Unit Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `zeebe/engine/.../BatchOperationPageProcessorTest.java` | Given/when/then, single behavior, clean mocking |
| Gold | `operate/webapp/.../ResolveIncidentHandlerTest.java` | `@ExtendWith(MockitoExtension.class)`, `InOrder` verification |
| Gold | `tasklist/webapp/.../TaskServiceTest.java` | `@ParameterizedTest`, error path testing |

## Integration Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `zeebe/exporters/.../ElasticsearchExporterIT.java` | Singleton container, `TestSearchContainers`, smart cleanup |
| Gold | `dist/.../AbstractCamundaDockerIT.java` | Network-aware multi-container, deterministic cleanup |
| Gold | `operate/qa/.../OperateSearchAbstractIT.java` | Lifecycle hooks, cache clearing, mock auth |

## Contract Test References

| Quality | File / Concept | Why |
|---------|---------------|-----|
| Target | Java Client Pact consumer tests (to be created) | Consumer-driven, verifies client assumptions against v2 REST API |
| Target | Tasklist Frontend Pact consumer tests (to be created) | Leverages existing Zod schemas, verifies FE expectations |
| Target | Exporter message Pact (to be created) | Verifies ES/OS index schema contract between exporter and consumers |
| Keep | Spectral OpenAPI linting (existing) | Spec validity — complementary to Pact |
| Keep | Buf protobuf backward compat (existing) | gRPC schema contract — already enforced |
| Reclassify | `zeebe/gateway-rest/.../JobControllerTest.java` | **API slice test** (integration), not a contract test |
| Reclassify | `qa/c8-orchestration-cluster-e2e-test-suite/.../process-instance-get-api.spec.ts` | **E2E API validation** — too slow for PR feedback, keep as nightly |

## E2E Test References

| Quality | File | Why |
|---------|------|-----|
| Gold | `operate/client/e2e-playwright/tests/login.spec.ts` | No `waitForTimeout`, Page Object Model, accessible selectors |
| Gold | `operate/client/e2e-playwright/tests/processInstance.spec.ts` | `expect.poll()`, `test.slow()`, fixture architecture |
| Avoid | `operate/client/e2e-playwright/visual/processInstance.spec.ts` | 6x `waitForTimeout(500)` — anti-pattern |

## Test Utility References

| Utility | File | Purpose |
|---------|------|---------|
| Container factory | `zeebe/test-util/.../TestSearchContainers.java` | Single source of truth for container images |
| Engine test rule | `zeebe/engine/.../EngineRule.java` | Full engine bootstrap for engine tests |
| State extension | `zeebe/engine/.../ProcessingStateExtension.java` | Inject ZeebeDb state per test |
| Recording exporter | `zeebe/test-util/.../RecordingExporterTestWatcher.java` | Record/replay events, dump on failure |
| REST test base | `zeebe/gateway-rest/.../RestControllerTest.java` | `WebTestClient` + auth stubs |
| Operate IT base | `operate/qa/.../OperateSearchAbstractIT.java` | Container lifecycle + auth mocking |
| Playwright fixtures | `operate/client/e2e-playwright/e2e-fixtures.ts` | Page objects + auth session reuse |
