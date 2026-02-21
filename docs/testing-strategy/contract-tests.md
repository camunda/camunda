# Contract Tests

> Back to [Testing Strategy](./README.md) | Setup: [Pact Setup](./setup/pact-setup.md)

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
