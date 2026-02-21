# Pact Setup

> Back to [Testing Strategy](../README.md) | Practices: [Contract Tests](../contract-tests.md)

This document covers the installation, configuration, and CI integration for Pact contract testing. For when and how to write contract tests, see [Contract Tests](../contract-tests.md).

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
